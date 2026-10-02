package org.foodcraft.recipe;

import com.google.gson.JsonObject;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import org.foodcraft.FoodCraft;

/** Explicitly disables the three vanilla recipes removed by the legacy mod. */
public final class DisabledRecipe implements CraftingRecipe {
    private final ResourceLocation id;
    public DisabledRecipe(ResourceLocation id){this.id=id;}
    public boolean matches(CraftingContainer grid,Level level){return false;}
    public ItemStack assemble(CraftingContainer grid,RegistryAccess registry){return ItemStack.EMPTY;}
    public boolean canCraftInDimensions(int width,int height){return false;}
    public ItemStack getResultItem(RegistryAccess registry){return ItemStack.EMPTY;}
    public ResourceLocation getId(){return id;}
    public RecipeType<?> getType(){return RecipeType.CRAFTING;}
    public RecipeSerializer<?> getSerializer(){return FoodCraft.DISABLED_SERIALIZER;}
    public boolean isSpecial(){return true;}
    public CraftingBookCategory category(){return CraftingBookCategory.MISC;}
    public static final class Serializer implements RecipeSerializer<DisabledRecipe> {
        public DisabledRecipe fromJson(ResourceLocation id,JsonObject json){return new DisabledRecipe(id);}
        public DisabledRecipe fromNetwork(ResourceLocation id,FriendlyByteBuf buffer){return new DisabledRecipe(id);}
        public void toNetwork(FriendlyByteBuf buffer,DisabledRecipe recipe){/* The recipe ID is already part of the vanilla packet. */}
    }
}
