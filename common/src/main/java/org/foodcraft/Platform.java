package org.foodcraft;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;

/** The only loader-facing seam; shared gameplay never imports loader classes. */
public interface Platform {
    void item(String id, java.util.function.Supplier<Item> item);
    void block(String id, java.util.function.Supplier<Block> block);
    void blockEntity(String id, java.util.function.Supplier<BlockEntityType<?>> type);
    void menu(String id, java.util.function.Supplier<MenuType<?>> type);
    void recipeType(String id, java.util.function.Supplier<RecipeType<?>> type);
    void recipeSerializer(String id, java.util.function.Supplier<RecipeSerializer<?>> serializer);
    void creativeTab(String id, java.util.function.Supplier<CreativeModeTab> tab);
    int fuelTime(net.minecraft.world.item.ItemStack stack);
    boolean wrenchEnabled();
    boolean modLoaded(String id);
    void cureMilk(net.minecraft.world.entity.LivingEntity entity);
}
