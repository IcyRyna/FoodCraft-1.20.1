package org.foodcraft.recipe;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;
import org.foodcraft.FoodCraft;
import org.foodcraft.machine.MachineKind;
import java.util.List;
import java.util.ArrayList;

public final class MachineRecipe implements Recipe<Container> {
    public record Input(int slot,Ingredient ingredient,int count) {
        public Input { if(slot<0||count<1||count>64||ingredient.isEmpty())throw new IllegalArgumentException("Invalid machine input"); }
    }
    private final ResourceLocation id;
    public final MachineKind kind;
    public final List<Input> inputs;
    public final int[] exclusive;
    public final ItemStack result;
    public final int time, water, minHeat, maxHeat;
    public final boolean milk,cold;
    public final float experience;
    public final String fingerprint;
    public MachineRecipe(ResourceLocation id,MachineKind kind,List<Input> inputs,int[] exclusive,ItemStack result,
                         int time,int water,int minHeat,int maxHeat,boolean milk,boolean cold,float experience){
        if(inputs.isEmpty()||result.isEmpty()||result.getCount()>result.getMaxStackSize()||time<1||water<0||water>8||minHeat<0||maxHeat<minHeat||!Float.isFinite(experience)||experience<0)
            throw new IllegalArgumentException("Invalid machine recipe "+id);
        var used=new java.util.HashSet<Integer>();
        for(Input input:inputs) if(input.slot()>=kind.size||!kind.input(input.slot())||!used.add(input.slot()))throw new IllegalArgumentException("Invalid or duplicated ingredient slot in "+id);
        var exclusiveUsed=new java.util.HashSet<Integer>();
        if(exclusive.length>kind.inputs.length)throw new IllegalArgumentException("Too many exclusive slots in "+id);
        for(int slot:exclusive) if(!kind.input(slot)||!exclusiveUsed.add(slot))throw new IllegalArgumentException("Invalid or duplicated exclusive slot in "+id);
        this.id=id;this.kind=kind;this.inputs=List.copyOf(inputs);this.exclusive=exclusive.clone();this.result=result.copy();
        this.time=time;this.water=water;this.minHeat=minHeat;this.maxHeat=maxHeat;this.milk=milk;this.cold=cold;this.experience=experience;
        StringBuilder signature=new StringBuilder(id.toString()).append('|').append(kind).append('|').append(time).append('|').append(water)
                .append('|').append(minHeat).append('|').append(maxHeat).append('|').append(milk).append('|').append(cold).append('|').append(experience)
                .append('|').append(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(result.getItem())).append('|').append(result.getCount()).append('|').append(result.getTag());
        for(var input:inputs)signature.append('|').append(input.slot()).append(':').append(input.count()).append(':').append(input.ingredient().toJson());
        signature.append('|').append(java.util.Arrays.toString(exclusive));
        try{fingerprint=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(signature.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException exception){throw new IllegalStateException("Required SHA-256 provider is unavailable",exception);}
    }
    @Override public boolean matches(Container inventory,Level level){
        for(Input input:inputs){ItemStack stack=inventory.getItem(input.slot());if(stack.getCount()<input.count()||!input.ingredient().test(stack))return false;}
        for(int slot:exclusive) if(inputs.stream().noneMatch(i->i.slot()==slot)&&!inventory.getItem(slot).isEmpty())return false;
        return true;
    }
    @Override public ItemStack assemble(Container c,RegistryAccess access){return result.copy();}
    @Override public boolean canCraftInDimensions(int w,int h){return true;}
    @Override public ItemStack getResultItem(RegistryAccess access){return result.copy();}
    @Override public ResourceLocation getId(){return id;}
    @Override public RecipeType<?> getType(){return FoodCraft.RECIPE_TYPES.get(kind);}
    @Override public RecipeSerializer<?> getSerializer(){return FoodCraft.SERIALIZERS.get(kind);}
    @Override public NonNullList<Ingredient> getIngredients(){var list=NonNullList.<Ingredient>create();inputs.forEach(i->list.add(i.ingredient()));return list;}
    @Override public boolean isSpecial(){return true;}

    public static final class Serializer implements RecipeSerializer<MachineRecipe>{
        private final MachineKind kind;
        public Serializer(MachineKind kind){this.kind=kind;}
        @Override public MachineRecipe fromJson(ResourceLocation id,JsonObject json){
            List<Input> inputs=new ArrayList<>();
            GsonHelper.getAsJsonArray(json,"inputs").forEach(value->{var input=value.getAsJsonObject();inputs.add(new Input(GsonHelper.getAsInt(input,"slot"),Ingredient.fromJson(input.get("ingredient")),GsonHelper.getAsInt(input,"count",1)));});
            JsonArray array=GsonHelper.getAsJsonArray(json,"exclusive_slots",new JsonArray());
            int[] exclusive=new int[array.size()];for(int i=0;i<array.size();i++)exclusive[i]=array.get(i).getAsInt();
            return new MachineRecipe(id,kind,inputs,exclusive,ShapedRecipe.itemStackFromJson(GsonHelper.getAsJsonObject(json,"result")),
                    GsonHelper.getAsInt(json,"time",kind.ticks),GsonHelper.getAsInt(json,"water",0),GsonHelper.getAsInt(json,"min_heat",0),GsonHelper.getAsInt(json,"max_heat",Integer.MAX_VALUE),
                    GsonHelper.getAsBoolean(json,"milk",false),GsonHelper.getAsBoolean(json,"cold",false),GsonHelper.getAsFloat(json,"experience",0));
        }
        @Override public MachineRecipe fromNetwork(ResourceLocation id,FriendlyByteBuf buf){
            int count=buf.readVarInt();if(count<1||count>12)throw new IllegalArgumentException("Invalid recipe input count");
            List<Input> inputs=new ArrayList<>();for(int i=0;i<count;i++)inputs.add(new Input(buf.readVarInt(),Ingredient.fromNetwork(buf),buf.readVarInt()));
            int[] exclusive=buf.readVarIntArray(12);
            return new MachineRecipe(id,kind,inputs,exclusive,buf.readItem(),buf.readVarInt(),buf.readVarInt(),buf.readVarInt(),buf.readVarInt(),buf.readBoolean(),buf.readBoolean(),buf.readFloat());
        }
        @Override public void toNetwork(FriendlyByteBuf buf,MachineRecipe recipe){
            buf.writeVarInt(recipe.inputs.size());for(Input i:recipe.inputs){buf.writeVarInt(i.slot());i.ingredient().toNetwork(buf);buf.writeVarInt(i.count());}
            buf.writeVarIntArray(recipe.exclusive);buf.writeItem(recipe.result);buf.writeVarInt(recipe.time);buf.writeVarInt(recipe.water);buf.writeVarInt(recipe.minHeat);buf.writeVarInt(recipe.maxHeat);
            buf.writeBoolean(recipe.milk);buf.writeBoolean(recipe.cold);buf.writeFloat(recipe.experience);
        }
    }
}
