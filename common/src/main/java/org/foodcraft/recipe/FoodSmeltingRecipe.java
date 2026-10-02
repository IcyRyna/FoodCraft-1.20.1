package org.foodcraft.recipe;

import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import org.foodcraft.FoodCraft;

/** Supports the legacy purified-water -> two salts result in a vanilla furnace. */
public final class FoodSmeltingRecipe extends SmeltingRecipe {
    public FoodSmeltingRecipe(ResourceLocation id,String group,Ingredient input,ItemStack result,float experience,int time){super(id,group,CookingBookCategory.MISC,input,result,experience,time);}
    @Override public RecipeSerializer<?> getSerializer(){return FoodCraft.SMELTING_SERIALIZER;}
    public static final class Serializer implements RecipeSerializer<FoodSmeltingRecipe> {
        public FoodSmeltingRecipe fromJson(ResourceLocation id,JsonObject json){
            ItemStack result=ShapedRecipe.itemStackFromJson(GsonHelper.getAsJsonObject(json,"result"));
            int time=GsonHelper.getAsInt(json,"cookingtime",200);float xp=GsonHelper.getAsFloat(json,"experience",0);
            if(result.isEmpty()||result.getCount()>result.getMaxStackSize()||time<1||!Float.isFinite(xp)||xp<0)throw new IllegalArgumentException("Invalid FoodCraft furnace recipe "+id);
            return new FoodSmeltingRecipe(id,GsonHelper.getAsString(json,"group",""),Ingredient.fromJson(json.get("ingredient")),result,xp,time);
        }
        public FoodSmeltingRecipe fromNetwork(ResourceLocation id,FriendlyByteBuf buffer){return new FoodSmeltingRecipe(id,buffer.readUtf(),Ingredient.fromNetwork(buffer),buffer.readItem(),buffer.readFloat(),buffer.readVarInt());}
        public void toNetwork(FriendlyByteBuf buffer,FoodSmeltingRecipe recipe){buffer.writeUtf(recipe.group);recipe.ingredient.toNetwork(buffer);buffer.writeItem(recipe.result);buffer.writeFloat(recipe.experience);buffer.writeVarInt(recipe.cookingTime);}
    }
}
