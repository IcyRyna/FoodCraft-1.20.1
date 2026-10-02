package org.foodcraft;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.RecipeSerializer;
import org.foodcraft.machine.*;
import org.foodcraft.recipe.MachineRecipe;
import org.foodcraft.item.*;
import org.foodcraft.agriculture.*;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.EnumMap;

public final class FoodCraft {
    public static final String MOD_ID="foodcraft";
    public static Platform platform;
    public static final Map<String,Item> ITEMS=new LinkedHashMap<>();
    public static final Map<String,Block> BLOCKS=new LinkedHashMap<>();
    public static final Map<MachineKind,MenuType<MachineMenu>> MENUS=new EnumMap<>(MachineKind.class);
    public static final Map<MachineKind,RecipeType<MachineRecipe>> RECIPE_TYPES=new EnumMap<>(MachineKind.class);
    public static final Map<MachineKind,RecipeSerializer<MachineRecipe>> SERIALIZERS=new EnumMap<>(MachineKind.class);
    public static BlockEntityType<MachineBlockEntity> MACHINE_ENTITY;
    public static final RecipeSerializer<org.foodcraft.recipe.DisabledRecipe> DISABLED_SERIALIZER=new org.foodcraft.recipe.DisabledRecipe.Serializer();
    public static final RecipeSerializer<org.foodcraft.recipe.FoodSmeltingRecipe> SMELTING_SERIALIZER=new org.foodcraft.recipe.FoodSmeltingRecipe.Serializer();
    private FoodCraft(){}
    public static ResourceLocation id(String path){return new ResourceLocation(MOD_ID,path);}
    public static Item item(String path){
        if(path.contains(":"))return BuiltInRegistries.ITEM.get(new ResourceLocation(path));
        Item result=ITEMS.get(path);if(result==null)throw new IllegalArgumentException("Unknown FoodCraft item "+path);return result;
    }
    public static void initialize(Platform implementation){
        if(platform!=null)throw new IllegalStateException("FoodCraft initialized twice");
        platform=implementation;
        for(Catalog.Entry e:Catalog.ENTRIES){
            if(!e.block())continue;
            platform.block(e.path(),()->{
            float hardness=e.hardness()==null?0.0F:e.hardness();SoundType sound=legacySound(e.sound());
            BlockBehaviour.Properties props=BlockBehaviour.Properties.of().strength(hardness).sound(sound);
            Block block=switch(e.kind()){
                case "machine" -> new MachineBlock(MachineKind.byId(e.path()),props,e.collision(),e.lit_light());
                case "crop" -> new FoodCropBlock(e,BlockBehaviour.Properties.copy(Blocks.WHEAT).strength(hardness).sound(sound));
                case "onion" -> new FoodOnionBlock(e,BlockBehaviour.Properties.copy(Blocks.SUGAR_CANE).strength(hardness).sound(sound));
                case "sapling" -> new FoodSaplingBlock(e,BlockBehaviour.Properties.copy(Blocks.OAK_SAPLING).strength(hardness).sound(sound));
                case "fruit" -> new FruitBlock(e,BlockBehaviour.Properties.copy(Blocks.OAK_LEAVES).strength(hardness).sound(sound).noOcclusion());
                case "cake" -> new FoodCakeBlock(e,BlockBehaviour.Properties.copy(Blocks.CAKE).strength(hardness).sound(sound));
                default -> new Block(props);
            };
            BLOCKS.put(e.path(),block);return block;
            });
        }
        for(Catalog.Entry e:Catalog.ENTRIES){
            if(e.id().startsWith("minecraft:")||e.kind().equals("debug"))continue;
            platform.item(e.path(),()->{
            Item.Properties props=new Item.Properties();
            if(e.stack_size()>0)props.stacksTo(e.stack_size());
            if(e.nutrition()>0){
                var builder=new net.minecraft.world.food.FoodProperties.Builder().nutrition(e.nutrition()).saturationMod(e.saturation());
                if(e.always_edible())builder.alwaysEat();props.food(builder.build());
            }
            if(e.durability()>0)props.durability(e.durability());
            Item item;
            if(e.block()){
                item=new BlockItem(BLOCKS.get(e.path()),props);
            }else item=switch(e.kind()){
                case "seed" -> new FoodSeedItem(e,props);
                case "knife" -> new KitchenKnifeItem(e.path().equals("caidao_hj"),props);
                case "purifier" -> new WaterPurifierItem(props);
                case "wrench" -> new WrenchItem(props.stacksTo(1));
                case "multitool" -> new MultiToolItem(e,props);
                case "cake_item" -> new FoodCakeItem(e,props);
                default -> new FoodItem(e,props);
            };
            ITEMS.put(e.path(),item);return item;
            });
        }
        platform.blockEntity("machine",()->{
            Block[] machines=BLOCKS.values().stream().filter(b->b instanceof MachineBlock).toArray(Block[]::new);
            MACHINE_ENTITY=BlockEntityType.Builder.of(MachineBlockEntity::new,machines).build(null);return MACHINE_ENTITY;
        });
        for(MachineKind kind:MachineKind.values()){
            platform.menu(kind.id,()->{
                MenuType<MachineMenu> menu=new MenuType<>((syncId,inventory)->MachineMenu.client(kind,syncId,inventory),FeatureFlags.DEFAULT_FLAGS);
                MENUS.put(kind,menu);return menu;
            });
            if(kind==MachineKind.STOVE)continue;
            RecipeType<MachineRecipe> type=new RecipeType<>(){public String toString(){return "foodcraft:"+kind.id;}};
            RecipeSerializer<MachineRecipe> serializer=new MachineRecipe.Serializer(kind);
            RECIPE_TYPES.put(kind,type);SERIALIZERS.put(kind,serializer);
            platform.recipeType(kind.id,()->type);platform.recipeSerializer(kind.id,()->serializer);
        }
        platform.recipeSerializer("disabled",()->DISABLED_SERIALIZER);
        platform.recipeSerializer("smelting",()->SMELTING_SERIALIZER);
        String[][] tabs={{"jiqi","milling_machine"},{"zhiwu","dami"},{"yingliao","pingguozhi"},{"zhushi","mantou"},{"shicai","mianfen"},{"xiaodian","putao_bg"},{"collection","wrench"}};
        for(String[] tab:tabs)platform.creativeTab(tab[0],()->CreativeModeTab.builder(CreativeModeTab.Row.TOP,0).title(Component.translatable("itemGroup.foodcraft."+tab[0]))
                .icon(()->new ItemStack(item(tab[1])))
                .displayItems((parameters,output)->Catalog.ENTRIES.stream().filter(e->tab[0].equals(e.creative_tab())&&ITEMS.containsKey(e.path())).forEach(e->output.accept(item(e.path())))).build());
    }
    private static SoundType legacySound(String name){
        if(name==null)return SoundType.STONE;
        return switch(name){case "Grass"->SoundType.GRASS;case "Wood"->SoundType.WOOD;case "Cloth"->SoundType.WOOL;case "Snow"->SoundType.SNOW;case "Sand"->SoundType.SAND;case "Gravel"->SoundType.GRAVEL;default->SoundType.STONE;};
    }
}
