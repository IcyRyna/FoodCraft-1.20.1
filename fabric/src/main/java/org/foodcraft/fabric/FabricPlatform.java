package org.foodcraft.fabric;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.fabricmc.fabric.api.registry.FuelRegistry;
import net.fabricmc.loader.api.FabricLoader;
import org.foodcraft.Platform;
import org.foodcraft.FoodCraft;
import com.google.gson.Gson;
import java.nio.file.Files;
import java.util.Map;

final class FabricPlatform implements Platform {
    private final boolean wrench;
    static final net.minecraft.resources.ResourceLocation SETTINGS=FoodCraft.id("settings");
    private static volatile Boolean remoteWrench;
    static void setRemoteWrench(Boolean value){remoteWrench=value;}
    boolean configuredWrench(){return wrench;}
    FabricPlatform(){
        var path=FabricLoader.getInstance().getConfigDir().resolve("foodcraft.json");
        try{
            if(!Files.exists(path)){Files.createDirectories(path.getParent());Files.writeString(path,"{\"wrench\":true}\n");wrench=true;}
            else {try(var reader=Files.newBufferedReader(path)){
                var object=new Gson().fromJson(reader,com.google.gson.JsonObject.class);
                if(object==null||!object.has("wrench")||!object.get("wrench").isJsonPrimitive()||!object.getAsJsonPrimitive("wrench").isBoolean())throw new IllegalArgumentException("foodcraft.json requires a boolean wrench setting");
                wrench=object.get("wrench").getAsBoolean();
            }}
        }catch(java.io.IOException e){throw new java.io.UncheckedIOException("Unable to read FoodCraft config",e);}
    }
    public void item(String id,java.util.function.Supplier<Item> item){Registry.register(BuiltInRegistries.ITEM,FoodCraft.id(id),item.get());}
    public void block(String id,java.util.function.Supplier<Block> block){Registry.register(BuiltInRegistries.BLOCK,FoodCraft.id(id),block.get());}
    public void blockEntity(String id,java.util.function.Supplier<BlockEntityType<?>> type){Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,FoodCraft.id(id),type.get());}
    public void menu(String id,java.util.function.Supplier<MenuType<?>> type){Registry.register(BuiltInRegistries.MENU,FoodCraft.id(id),type.get());}
    public void recipeType(String id,java.util.function.Supplier<RecipeType<?>> type){Registry.register(BuiltInRegistries.RECIPE_TYPE,FoodCraft.id(id),type.get());}
    public void recipeSerializer(String id,java.util.function.Supplier<RecipeSerializer<?>> serializer){Registry.register(BuiltInRegistries.RECIPE_SERIALIZER,FoodCraft.id(id),serializer.get());}
    public void creativeTab(String id,java.util.function.Supplier<CreativeModeTab> tab){Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB,FoodCraft.id(id),tab.get());}
    public int fuelTime(ItemStack stack){Integer value=FuelRegistry.INSTANCE.get(stack.getItem());return value==null?0:value;}
    public boolean wrenchEnabled(){return FabricLoader.getInstance().getEnvironmentType()==net.fabricmc.api.EnvType.CLIENT&&remoteWrench!=null?remoteWrench:wrench;}
    public boolean modLoaded(String id){return net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded(id);}
    public void cureMilk(net.minecraft.world.entity.LivingEntity entity){entity.removeAllEffects();}
}
