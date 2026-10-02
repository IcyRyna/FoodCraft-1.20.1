package org.foodcraft.fabric.mixin;

import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.foodcraft.recipe.FoodSmeltingRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Vanilla assumes one furnace product; the legacy salt recipe produces two. */
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class FurnaceCountMixin {
    @Inject(method="canBurn",at=@At("RETURN"),cancellable=true)
    private static void foodcraft$capacity(RegistryAccess access,Recipe<?> recipe,NonNullList<ItemStack> inventory,int limit,CallbackInfoReturnable<Boolean> result){
        if(Boolean.TRUE.equals(result.getReturnValue())&&recipe instanceof FoodSmeltingRecipe){
            ItemStack output=inventory.get(2),incoming=recipe.getResultItem(access);
            if(output.getCount()+incoming.getCount()>Math.min(limit,incoming.getMaxStackSize()))result.setReturnValue(false);
        }
    }
    @Redirect(method="burn",at=@At(value="INVOKE",target="Lnet/minecraft/world/item/ItemStack;grow(I)V"))
    private static void foodcraft$productCount(ItemStack output,int vanillaCount,RegistryAccess access,Recipe<?> recipe,NonNullList<ItemStack> inventory,int limit){
        output.grow(recipe instanceof FoodSmeltingRecipe?recipe.getResultItem(access).getCount():vanillaCount);
    }
}
