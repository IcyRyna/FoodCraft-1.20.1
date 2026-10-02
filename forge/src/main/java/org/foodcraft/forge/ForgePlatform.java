package org.foodcraft.forge;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.core.registries.Registries;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.eventbus.api.IEventBus;
import org.foodcraft.Platform;
import org.foodcraft.FoodCraft;

final class ForgePlatform implements Platform {
    private final DeferredRegister<Item> items=DeferredRegister.create(ForgeRegistries.ITEMS,FoodCraft.MOD_ID);
    private final DeferredRegister<Block> blocks=DeferredRegister.create(ForgeRegistries.BLOCKS,FoodCraft.MOD_ID);
    private final DeferredRegister<BlockEntityType<?>> entities=DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES,FoodCraft.MOD_ID);
    private final DeferredRegister<MenuType<?>> menus=DeferredRegister.create(ForgeRegistries.MENU_TYPES,FoodCraft.MOD_ID);
    private final DeferredRegister<RecipeType<?>> types=DeferredRegister.create(ForgeRegistries.RECIPE_TYPES,FoodCraft.MOD_ID);
    private final DeferredRegister<RecipeSerializer<?>> serializers=DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS,FoodCraft.MOD_ID);
    private final DeferredRegister<CreativeModeTab> tabs=DeferredRegister.create(Registries.CREATIVE_MODE_TAB,FoodCraft.MOD_ID);
    ForgePlatform(IEventBus bus){items.register(bus);blocks.register(bus);entities.register(bus);menus.register(bus);types.register(bus);serializers.register(bus);tabs.register(bus);}
    public void item(String id,java.util.function.Supplier<Item> item){items.register(id,item);}
    public void block(String id,java.util.function.Supplier<Block> block){blocks.register(id,block);}
    public void blockEntity(String id,java.util.function.Supplier<BlockEntityType<?>> type){entities.register(id,type);}
    public void menu(String id,java.util.function.Supplier<MenuType<?>> type){menus.register(id,type);}
    public void recipeType(String id,java.util.function.Supplier<RecipeType<?>> type){types.register(id,type);}
    public void recipeSerializer(String id,java.util.function.Supplier<RecipeSerializer<?>> serializer){serializers.register(id,serializer);}
    public void creativeTab(String id,java.util.function.Supplier<CreativeModeTab> tab){tabs.register(id,tab);}
    public int fuelTime(ItemStack stack){return ForgeHooks.getBurnTime(stack,null);}
    public boolean wrenchEnabled(){return FoodCraftForge.WRENCH.get();}
    public boolean modLoaded(String id){return net.minecraftforge.fml.ModList.get().isLoaded(id);}
    public void cureMilk(net.minecraft.world.entity.LivingEntity entity){entity.curePotionEffects(new ItemStack(net.minecraft.world.item.Items.MILK_BUCKET));}
}
