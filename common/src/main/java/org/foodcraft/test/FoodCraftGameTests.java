package org.foodcraft.test;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import io.netty.buffer.Unpooled;
import org.foodcraft.Catalog;
import org.foodcraft.FoodCraft;
import org.foodcraft.machine.*;
import org.foodcraft.recipe.MachineRecipe;
import java.util.ArrayList;
import java.util.List;

public final class FoodCraftGameTests {
    private FoodCraftGameTests(){}
    private static MachineBlockEntity place(GameTestHelper helper,MachineKind kind,BlockPos local){
        BlockPos pos=helper.absolutePos(local);
        if(helper.getLevel().getBlockEntity(pos) instanceof MachineBlockEntity previous)previous.clearContent();
        helper.getLevel().setBlock(pos,Blocks.AIR.defaultBlockState(),3);
        helper.getLevel().setBlock(pos,FoodCraft.BLOCKS.get(kind.id).defaultBlockState(),3);
        return (MachineBlockEntity)helper.getLevel().getBlockEntity(pos);
    }
    private static void tick(MachineBlockEntity machine,int ticks){
        for(int i=0;i<ticks;i++)MachineBlockEntity.tick(machine.getLevel(),machine.getBlockPos(),machine.getBlockState(),machine);
    }
    public static void registry(GameTestHelper helper){
        int counted=0;
        for(Catalog.Entry entry:Catalog.ENTRIES){
            if(entry.kind().equals("debug"))continue;
            ResourceLocation id=new ResourceLocation(entry.id());
            helper.assertTrue(BuiltInRegistries.ITEM.containsKey(id),"Unregistered item "+id);
            helper.assertTrue(FoodCraft.item(entry.id()).getMaxStackSize()==entry.stack_size(),"Legacy stack size changed "+entry.id());
            if(entry.durability()>0)helper.assertTrue(FoodCraft.item(entry.id()).getMaxDamage()==entry.durability(),"Legacy durability changed "+entry.id());
            if(entry.block()){
                helper.assertTrue(BuiltInRegistries.BLOCK.containsKey(id),"Unregistered block "+id);var state=FoodCraft.BLOCKS.get(entry.path()).defaultBlockState();
                helper.assertTrue(Math.abs(state.getDestroySpeed(helper.getLevel(),BlockPos.ZERO)-entry.hardness())<0.00001,"Legacy block hardness changed "+entry.id());
                String sound=entry.sound().equals("Cloth")?"wool":entry.sound().toLowerCase(java.util.Locale.ROOT);
                helper.assertTrue(state.getSoundType().getStepSound().getLocation().getPath().equals("block."+sound+".step"),"Legacy block sound changed "+entry.id());
                if(entry.collision()!=null){var b=entry.collision();var shape=state.getShape(helper.getLevel(),BlockPos.ZERO).bounds();double[] actual={shape.minX,shape.minY,shape.minZ,shape.maxX,shape.maxY,shape.maxZ};
                    for(int axis=0;axis<6;axis++)helper.assertTrue(Math.abs(actual[axis]-b[axis]/16.0)<0.00001,"Legacy selection bounds changed "+entry.id()+" axis="+axis);
                }
            }
            if(entry.kind().equals("jam")||entry.kind().equals("cookie")||entry.kind().equals("cake_item"))helper.assertTrue(FoodCraft.item(entry.id()).isFoil(new ItemStack(FoodCraft.item(entry.id())))==entry.path().contains("jinputao"),"Fruit variant glint changed "+entry.id());
            counted++;
        }
        helper.assertTrue(counted==358,"Unexpected catalog item count "+counted);
        try(var reader=new java.io.InputStreamReader(FoodCraftGameTests.class.getResourceAsStream("/assets/foodcraft/recipe-manifest.json"),java.nio.charset.StandardCharsets.UTF_8)){
            int expected=new com.google.gson.Gson().fromJson(reader,com.google.gson.JsonArray.class).size();
            long actual=helper.getLevel().getRecipeManager().getRecipes().stream().filter(recipe->recipe.getId().getNamespace().equals("foodcraft")).count();
            helper.assertTrue(actual==expected,"Recipes missing after data load: "+actual+" / "+expected);
            System.out.println("FOODCRAFT VERIFIED LOADED RECIPES="+actual);
        }catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}
        VerificationSnapshot.export(helper.getLevel());helper.succeed();
    }
    public static void waterPurifierAndEffects(GameTestHelper helper){
        var level=helper.getLevel();var player=helper.makeMockPlayer();var water=helper.absolutePos(new BlockPos(4,2,4));
        player.setPos(water.getX()+0.5,water.getY(),water.getZ()-2.0);player.setYRot(0);player.setXRot(24.2F);
        player.getAbilities().instabuild=false;player.getInventory().clearContent();
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(FoodCraft.item("jinghuashuitong")));
        for(int use=0;use<16;use++){
            level.setBlock(water,Blocks.WATER.defaultBlockState(),3);
            ItemStack held=player.getMainHandItem();var result=held.getItem().use(level,player,net.minecraft.world.InteractionHand.MAIN_HAND);
            helper.assertTrue(result.getResult().consumesAction(),"Purifier refused source water at use "+use);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,result.getObject());
            helper.assertTrue(!level.getFluidState(water).isSource(),"Purifier failed to remove source water");
        }
        int purified=0,buckets=0;for(int i=0;i<player.getInventory().getContainerSize();i++){
            var stack=player.getInventory().getItem(i);if(stack.is(FoodCraft.item("water")))purified+=stack.getCount();if(stack.is(Items.BUCKET))buckets+=stack.getCount();
        }
        helper.assertTrue(purified==16&&buckets==1,"Purifier must produce 16 waters and exactly one empty bucket: "+purified+" / "+buckets);
        player.removeAllEffects();player.getFoodData().setFoodLevel(0);
        FoodCraft.item("pingguozhi").finishUsingItem(new ItemStack(FoodCraft.item("pingguozhi")),level,player);
        helper.assertTrue(player.getActiveEffects().isEmpty(),"Ordinary drink added a potion effect");
        FoodCraft.item("jinpingguozhi").finishUsingItem(new ItemStack(FoodCraft.item("jinpingguozhi")),level,player);
        var resistance=player.getEffect(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE);
        helper.assertTrue(resistance!=null&&resistance.getDuration()==36000&&resistance.getAmplifier()==4,"Golden juice effect lost its legacy tick duration or amplifier");
        player.removeAllEffects();helper.succeed();
    }
    public static void everyMachineRecipe(GameTestHelper helper){
        int count=0;
        for(MachineKind kind:MachineKind.values()){
            if(kind==MachineKind.STOVE)continue;
            var recipes=helper.getLevel().getRecipeManager().getAllRecipesFor(FoodCraft.RECIPE_TYPES.get(kind));
            helper.assertTrue(!recipes.isEmpty(),"No recipes for "+kind.id);
            for(MachineRecipe recipe:recipes){
                BlockPos relative=new BlockPos(3,2,3);
                var machine=place(helper,kind,relative);
                for(var input:recipe.inputs){
                    ItemStack[] options=input.ingredient().getItems();
                    helper.assertTrue(options.length>0,"Empty ingredient tag in "+recipe.getId());
                    machine.setItem(input.slot(),options[0].copyWithCount(input.count()));
                }
                machine.liquid=recipe.water;machine.milk=recipe.milk;
                if(kind==MachineKind.CUTTING_BOARD)machine.setItem(0,new ItemStack(FoodCraft.item("caidao")));
                if(kind.fuelSlot>=0)machine.setItem(kind.fuelSlot,new ItemStack(Items.COAL,64));
                if(kind==MachineKind.DRINK_MAKER&&recipe.cold)machine.setItem(4,new ItemStack(Blocks.ICE,64));
                if(kind.heatedExternally()){
                    var stove=place(helper,MachineKind.STOVE,relative.below());
                    stove.setItem(0,new ItemStack(Items.COAL,64));tick(stove,1);
                    int steps=recipe.time/17;
                    int power=Math.max(1,(recipe.minHeat+recipe.maxHeat)/Math.max(1,steps));
                    machine.adjustFirePower(Math.min(100,power));
                }
                tick(machine,recipe.time);
                ItemStack actual=machine.getItem(kind.outputs[0]);
                helper.assertTrue(ItemStack.isSameItemSameTags(actual,recipe.result)&&actual.getCount()==recipe.result.getCount(),
                    "Wrong output for "+recipe.getId()+": "+actual);
                for(var input:recipe.inputs)helper.assertTrue(machine.getItem(input.slot()).isEmpty(),"Input not consumed for "+recipe.getId());
                helper.assertTrue(machine.liquid==0,"Wrong liquid consumption for "+recipe.getId());
                // Exercise the exact serializer used for client recipe synchronization.
                FriendlyByteBuf buf=new FriendlyByteBuf(Unpooled.buffer());
                try{
                    var serializer=FoodCraft.SERIALIZERS.get(kind);
                    serializer.toNetwork(buf,recipe);MachineRecipe decoded=serializer.fromNetwork(recipe.getId(),buf);
                    helper.assertTrue(decoded.kind==recipe.kind&&decoded.time==recipe.time&&decoded.water==recipe.water&&decoded.minHeat==recipe.minHeat&&decoded.maxHeat==recipe.maxHeat&&decoded.milk==recipe.milk&&decoded.cold==recipe.cold&&decoded.experience==recipe.experience&&java.util.Arrays.equals(decoded.exclusive,recipe.exclusive)&&decoded.inputs.size()==recipe.inputs.size()&&ItemStack.matches(decoded.result,recipe.result),"Recipe conditions/counts/slots/NBT network roundtrip failed "+recipe.getId());
                    // Vanilla ingredient synchronization expands tags to their resolved items.
                    for(int index=0;index<recipe.inputs.size();index++){
                        var a=recipe.inputs.get(index);var b=decoded.inputs.get(index);
                        helper.assertTrue(a.slot()==b.slot()&&a.count()==b.count(),"Input slot/count changed on network");
                        java.util.Set<String> expected=new java.util.HashSet<>(),actualItems=new java.util.HashSet<>();
                        for(var stack:a.ingredient().getItems())expected.add(BuiltInRegistries.ITEM.getKey(stack.getItem())+"|"+stack.getTag());
                        for(var stack:b.ingredient().getItems())actualItems.add(BuiltInRegistries.ITEM.getKey(stack.getItem())+"|"+stack.getTag());
                        helper.assertTrue(expected.equals(actualItems),"Resolved ingredient set changed on network "+recipe.getId());
                    }
                }finally{buf.release();}
                count++;
            }
        }
        helper.assertTrue(count>=100,"Too few machine recipes: "+count);
        System.out.println("FOODCRAFT VERIFIED MACHINE RECIPES="+count);
        helper.succeed();
    }
    public static void cuttingBoardBoundaries(GameTestHelper helper){
        var board=place(helper,MachineKind.CUTTING_BOARD,new BlockPos(2,2,2));
        board.setItem(0,new ItemStack(FoodCraft.item("caidao")));
        board.setItem(1,new ItemStack(Items.COOKED_CHICKEN));
        board.setItem(2,new ItemStack(Items.COOKED_CHICKEN));
        board.setItem(3,new ItemStack(Items.COOKED_CHICKEN));tick(board,10);
        helper.assertTrue(board.getItem(4).isEmpty()&&board.getItem(1).getCount()==1&&board.getItem(2).getCount()==1&&board.getItem(3).getCount()==1,"Three-slot chicken exploit");
        board.setItem(1,ItemStack.EMPTY);board.setItem(3,ItemStack.EMPTY);
        board.setItem(4,new ItemStack(FoodCraft.item("jichi"),63));tick(board,1);
        helper.assertTrue(board.getItem(2).getCount()==1&&board.getItem(4).getCount()==63,"Blocked output consumed chicken");
        board.setItem(4,ItemStack.EMPTY);
        ItemStack knife=board.getItem(0);knife.setDamageValue(knife.getMaxDamage()-1);tick(board,1);
        helper.assertTrue(board.getItem(4).is(FoodCraft.item("jichi"))&&board.getItem(4).getCount()==2,"Slot-two chicken must produce wings");
        helper.assertTrue(board.getItem(0).isEmpty()&&board.getItem(2).isEmpty(),"Final knife durability was not consumed once");
        board.setItem(0,new ItemStack(FoodCraft.item("caidao_hj")));board.setItem(1,new ItemStack(Items.COOKED_CHICKEN));board.setItem(4,ItemStack.EMPTY);tick(board,1);
        helper.assertTrue(board.getItem(4).getCount()==3&&board.getItem(4).is(FoodCraft.item("jitui")),"Golden knife must produce an extra chicken leg");
        helper.succeed();
    }
    public static void waterAndPersistence(GameTestHelper helper){
        var machine=place(helper,MachineKind.PRESSURE_COOKER,new BlockPos(2,2,2));
        machine.setItem(3,new ItemStack(Items.WATER_BUCKET));tick(machine,1);
        helper.assertTrue(machine.liquid==1&&machine.getItem(3).is(Items.BUCKET),"Water bucket not returned exactly once");
        helper.assertTrue(machine.fillWater(7,true)==7&&machine.liquid==1,"Simulated fill changed tank");
        machine.fillWater(7,false);helper.assertTrue(machine.liquid==8&&machine.fillWater(1,false)==0,"Tank overflow");
        machine.progress=97;machine.burnTime=400000;machine.initialBurnTime=400000;machine.proficiency=123;machine.firePower=33;
        CompoundTag tag=machine.saveWithoutMetadata();
        var restored=place(helper,MachineKind.PRESSURE_COOKER,new BlockPos(5,2,2));restored.load(tag);
        helper.assertTrue(restored.liquid==8&&restored.progress==97&&restored.burnTime==400000&&restored.initialBurnTime==400000&&restored.proficiency==123&&restored.firePower==33,"Machine NBT state did not roundtrip");
        helper.assertTrue(restored.getItem(3).is(Items.BUCKET),"Saved container changed");
        wrenchRoundTrips(helper);
        helper.succeed();
    }
    private static void wrenchRoundTrips(GameTestHelper helper){
        var level=helper.getLevel();var player=helper.makeMockPlayer();player.getAbilities().instabuild=false;
        var pos=helper.absolutePos(new BlockPos(7,2,7));var box=new net.minecraft.world.phys.AABB(pos).inflate(2);
        var wrench=new ItemStack(FoodCraft.item("wrench"));var face=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),net.minecraft.core.Direction.UP,pos,false);
        for(MachineKind kind:MachineKind.values()){
            for(var entity:level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,box))entity.discard();
            level.setBlock(pos.below(),Blocks.STONE.defaultBlockState(),3);level.setBlock(pos,FoodCraft.BLOCKS.get(kind.id).defaultBlockState(),3);
            var machine=(MachineBlockEntity)level.getBlockEntity(pos);machine.clearContent();machine.setItem(0,new ItemStack(Items.BREAD,7));
            machine.liquid=5;machine.progress=77;machine.proficiency=777;machine.firePower=13;machine.burnTime=400000;
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,wrench.copy());
            var picked=wrench.getItem().useOn(new net.minecraft.world.item.context.UseOnContext(player,net.minecraft.world.InteractionHand.MAIN_HAND,face));
            var drops=level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,box);
            helper.assertTrue(picked.consumesAction()&&level.getBlockState(pos).isAir()&&drops.size()==1,"Wrench produced duplicate machine/inventory drops "+kind.id);
            var saved=drops.get(0).getItem().copy();helper.assertTrue(saved.is(FoodCraft.item(kind.id))&&saved.getCount()==1&&saved.hasTag(),"Wrench did not save the machine item "+kind.id);drops.get(0).discard();
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,saved);
            var groundHit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos.below()),net.minecraft.core.Direction.UP,pos.below(),false);
            var placed=saved.getItem().useOn(new net.minecraft.world.item.context.UseOnContext(player,net.minecraft.world.InteractionHand.MAIN_HAND,groundHit));
            var replacement=(MachineBlockEntity)level.getBlockEntity(pos);
            helper.assertTrue(placed.consumesAction()&&saved.isEmpty()&&replacement!=null&&replacement.getItem(0).getCount()==7&&replacement.liquid==5&&replacement.progress==77&&replacement.proficiency==777&&replacement.firePower==13&&replacement.burnTime==400000,"Wrench placement lost saved state "+kind.id);
            level.destroyBlock(pos,true,player);drops=level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,box);
            int food=drops.stream().filter(entity->entity.getItem().is(Items.BREAD)).mapToInt(entity->entity.getItem().getCount()).sum();
            int housing=drops.stream().filter(entity->entity.getItem().is(FoodCraft.item("waike"))).mapToInt(entity->entity.getItem().getCount()).sum();
            helper.assertTrue(food==7&&housing==1&&drops.stream().noneMatch(entity->entity.getItem().is(FoodCraft.item(kind.id))),"Normal breaking duplicated or lost machine inventory "+kind.id);
            for(var entity:drops)entity.discard();
        }
        player.getAbilities().instabuild=true;level.setBlock(pos,FoodCraft.BLOCKS.get("cutting_board").defaultBlockState(),3);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,wrench.copy());wrench.getItem().useOn(new net.minecraft.world.item.context.UseOnContext(player,net.minecraft.world.InteractionHand.MAIN_HAND,face));
        helper.assertTrue(level.getBlockState(pos).isAir()&&level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,box).isEmpty(),"Creative wrench generated an extra machine");
        System.out.println("FOODCRAFT VERIFIED WRENCH PLACE/BREAK=9 creative=1");
    }
    public static void menuLayouts(GameTestHelper helper){
        PlayerInventoryCheck.verify(helper);helper.succeed();
    }
    public static void craftingRecipes(GameTestHelper helper){
        var player=helper.makeMockPlayer();
        var owner=new net.minecraft.world.inventory.CraftingMenu(0,player.getInventory());
        var grid=new net.minecraft.world.inventory.TransientCraftingContainer(owner,3,3);
        int count=0;
        for(var recipe:helper.getLevel().getRecipeManager().getAllRecipesFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING)){
            if(!recipe.getId().getNamespace().equals("foodcraft"))continue;
            grid.clearContent();
            var ingredients=recipe.getIngredients();
            int width=recipe instanceof net.minecraft.world.item.crafting.ShapedRecipe shaped?shaped.getWidth():3;
            for(int i=0;i<ingredients.size();i++){
                var ingredient=ingredients.get(i);if(ingredient.isEmpty())continue;
                var choices=ingredient.getItems();helper.assertTrue(choices.length>0,"Empty crafting ingredient "+recipe.getId());
                grid.setItem(i/width*3+i%width,choices[0].copyWithCount(1));
            }
            helper.assertTrue(recipe.matches(grid,helper.getLevel()),"Crafting recipe does not match its ingredients "+recipe.getId());
            var expected=recipe.getResultItem(helper.getLevel().registryAccess());var result=recipe.assemble(grid,helper.getLevel().registryAccess());
            helper.assertTrue(ItemStack.isSameItemSameTags(expected,result)&&result.getCount()==expected.getCount(),"Crafting result mismatch "+recipe.getId());
            count++;
        }
        helper.assertTrue(count==141,"Expected 141 crafting recipes, got "+count);
        for(String name:List.of("bread","cake","pumpkin_pie"))helper.assertTrue(helper.getLevel().getRecipeManager().byKey(new ResourceLocation("minecraft",name)).orElseThrow() instanceof org.foodcraft.recipe.DisabledRecipe,"Vanilla recipe bypasses legacy processing chain: "+name);
        var board=helper.getLevel().getRecipeManager().getAllRecipesFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING).stream().filter(recipe->recipe.getResultItem(helper.getLevel().registryAccess()).is(FoodCraft.item("cutting_board"))).findFirst().orElseThrow();
        var planks=new java.util.ArrayList<net.minecraft.world.item.Item>();
        for(var holder:net.minecraft.core.registries.BuiltInRegistries.ITEM.getTagOrEmpty(net.minecraft.tags.ItemTags.PLANKS))if(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(holder.value()).getNamespace().equals("minecraft"))planks.add(holder.value());
        helper.assertTrue(planks.size()==11,"Minecraft 1.20.1 must supply eleven plank variants");int plankCases=0;
        for(var first:planks)for(var second:planks){
            grid.clearContent();var ingredients=board.getIngredients();int width=board instanceof net.minecraft.world.item.crafting.ShapedRecipe shaped?shaped.getWidth():3,woodSlots=0;
            for(int i=0;i<ingredients.size();i++){
                var ingredient=ingredients.get(i);if(ingredient.isEmpty())continue;
                ItemStack stack;
                if(ingredient.test(new ItemStack(first))){stack=new ItemStack(woodSlots++%2==0?first:second);}else stack=ingredient.getItems()[0].copyWithCount(1);
                grid.setItem(i/width*3+i%width,stack);
            }
            helper.assertTrue(woodSlots>0&&board.matches(grid,helper.getLevel()),"Cutting board rejected a vanilla or mixed plank variant");
            var result=board.assemble(grid,helper.getLevel().registryAccess());helper.assertTrue(result.is(FoodCraft.item("cutting_board"))&&result.getCount()==1,"Wood variant crafted the wrong cutting board quantity");plankCases++;
        }
        System.out.println("FOODCRAFT ROUND3 WOOD PLANK CRAFTING PASS native_variants="+planks.size()+" combinations="+plankCases);
        System.out.println("FOODCRAFT VERIFIED CRAFTING RECIPES="+count);helper.succeed();
    }
    public static void cookingRecipes(GameTestHelper helper){
        var level=helper.getLevel();int count=0;BlockPos pos=helper.absolutePos(new BlockPos(3,2,3));
        for(var recipe:level.getRecipeManager().getAllRecipesFor(net.minecraft.world.item.crafting.RecipeType.SMELTING))if(recipe.getId().getNamespace().equals("foodcraft")){
            level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);level.setBlock(pos,Blocks.FURNACE.defaultBlockState(),3);
            var furnace=(net.minecraft.world.level.block.entity.FurnaceBlockEntity)level.getBlockEntity(pos);
            furnace.setItem(0,recipe.getIngredients().get(0).getItems()[0].copyWithCount(2));furnace.setItem(1,new ItemStack(Items.COAL));
            for(int tick=0;tick<400;tick++)net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity.serverTick(level,pos,furnace.getBlockState(),furnace);
            helper.assertTrue(ItemStack.isSameItemSameTags(furnace.getItem(2),recipe.getResultItem(level.registryAccess()))&&furnace.getItem(2).getCount()==2*recipe.getResultItem(level.registryAccess()).getCount()&&furnace.getItem(0).isEmpty(),"Vanilla furnace recipe failed "+recipe.getId());
            helper.assertTrue(recipe.getExperience()==0.5F&&recipe.getCookingTime()==200,"Legacy furnace XP/time changed");furnace.clearContent();count++;
            if(recipe.getResultItem(level.registryAccess()).getCount()>1){
                furnace.setItem(0,recipe.getIngredients().get(0).getItems()[0].copy());furnace.setItem(2,recipe.getResultItem(level.registryAccess()).copyWithCount(63));
                for(int tick=0;tick<200;tick++)net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity.serverTick(level,pos,furnace.getBlockState(),furnace);
                helper.assertTrue(furnace.getItem(0).getCount()==1&&furnace.getItem(2).getCount()==63,"Furnace output capacity swallowed purified water");furnace.clearContent();
            }
        }
        helper.assertTrue(count==4,"Expected all four unique legacy furnace recipes");System.out.println("FOODCRAFT VERIFIED FURNACE RECIPES="+count);helper.succeed();
    }
    public static void plantsAndTrees(GameTestHelper helper){
        int crops=0,trees=0;
        var level=helper.getLevel();BlockPos local=new BlockPos(4,2,4);BlockPos pos=helper.absolutePos(local);
        for(Catalog.Entry entry:Catalog.ENTRIES)if(entry.kind().equals("crop")){
            level.setBlock(pos.below(),Blocks.FARMLAND.defaultBlockState(),3);
            var block=(org.foodcraft.agriculture.FoodCropBlock)FoodCraft.BLOCKS.get(entry.path());
            for(int age=0;age<8;age++){
                var state=block.defaultBlockState().setValue(net.minecraft.world.level.block.CropBlock.AGE,age);level.setBlock(pos,state,3);
                // Loot functions can legally return a zero seed count; the world does not spawn it.
                var drops=net.minecraft.world.level.block.Block.getDrops(state,level,pos,null).stream().filter(drop->!drop.isEmpty()).toList();
                helper.assertTrue(!drops.isEmpty(),"Crop produces no drops "+entry.id()+" age="+age);
                for(var drop:drops)helper.assertTrue(!drop.isEmpty()&&drop.getCount()<=drop.getMaxStackSize(),"Invalid crop drop stack "+entry.id());
            }
            level.setBlock(pos.below(),Blocks.DIRT.defaultBlockState(),3);
            helper.assertTrue(!block.defaultBlockState().canSurvive(level,pos),"Crop survives trampled farmland "+entry.id());
            level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);crops++;
        }
        for(Catalog.Entry entry:Catalog.ENTRIES)if(entry.kind().equals("sapling")){
            var sapling=(org.foodcraft.agriculture.FoodSaplingBlock)FoodCraft.BLOCKS.get(entry.path());
            for(var voxel:Catalog.TREES.get(entry.legacy_class())){var offset=voxel.offset();level.setBlock(pos.offset(offset[0],offset[1],offset[2]),Blocks.AIR.defaultBlockState(),3);}
            level.setBlock(pos.below(),Blocks.DIRT.defaultBlockState(),3);level.setBlock(pos,sapling.defaultBlockState(),3);
            sapling.advanceTree(level,pos,sapling.defaultBlockState(),level.random);
            helper.assertTrue(level.getBlockState(pos).is(Blocks.OAK_LOG),"Sapling did not generate legacy tree "+entry.id());
            for(var voxel:Catalog.TREES.get(entry.legacy_class())){var offset=voxel.offset();helper.assertTrue(!level.getBlockState(pos.offset(offset[0],offset[1],offset[2])).isAir(),"Tree geometry missing "+entry.id());}
            trees++;
        }
        helper.assertTrue(crops==17&&trees==17,"Plant catalog counts changed: crops="+crops+" trees="+trees);
        var grass=level.getServer().getLootData().getLootTable(new ResourceLocation("minecraft:blocks/grass"));
        var params=new net.minecraft.world.level.storage.loot.LootParams.Builder(level)
                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN,net.minecraft.world.phys.Vec3.atCenterOf(helper.absolutePos(new BlockPos(2,2,2))))
                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_STATE,Blocks.GRASS.defaultBlockState())
                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.TOOL,ItemStack.EMPTY)
                .create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.BLOCK);
        java.util.Set<String> seen=new java.util.HashSet<>();int seeds=0;
        for(int sample=0;sample<10000;sample++)for(var drop:grass.getRandomItems(params,0xFC000L+sample)){
            var id=BuiltInRegistries.ITEM.getKey(drop.getItem());if(id.getNamespace().equals("foodcraft")){seen.add(id.toString());seeds+=drop.getCount();}
        }
        helper.assertTrue(seen.size()==18&&seeds>800&&seeds<1150,"Grass seed acquisition or legacy weighted chance changed: "+seeds+" / "+seen.size());
        var shears=new net.minecraft.world.level.storage.loot.LootParams.Builder(level)
                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN,net.minecraft.world.phys.Vec3.ZERO)
                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_STATE,Blocks.GRASS.defaultBlockState())
                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.TOOL,new ItemStack(Items.SHEARS))
                .create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.BLOCK);
        for(int sample=0;sample<1000;sample++)helper.assertTrue(grass.getRandomItems(shears,0xFC000L+sample).stream().noneMatch(drop->BuiltInRegistries.ITEM.getKey(drop.getItem()).getNamespace().equals("foodcraft")),"Shearing grass produced extra FoodCraft seeds");
        System.out.println("FOODCRAFT VERIFIED GRASS LOOT seeds="+seeds+" varieties="+seen.size()+" samples=10000 shears=1000");helper.succeed();
    }
    public static void foodProperties(GameTestHelper helper){
        helper.assertTrue(Items.EGG.getMaxStackSize()==64&&Items.SNOWBALL.getMaxStackSize()==64,"Legacy vanilla egg/snowball stack limit was lost");
        int count=0;
        for(Catalog.Entry entry:Catalog.ENTRIES){
            if(entry.id().startsWith("minecraft:")||entry.kind().equals("debug")||entry.nutrition()==0)continue;
            var item=FoodCraft.item(entry.path());var food=item.getFoodProperties();
            helper.assertTrue(food!=null&&food.getNutrition()==entry.nutrition()&&Math.abs(food.getSaturationModifier()-entry.saturation())<0.00001,"Food values changed "+entry.id());
            helper.assertTrue(food.canAlwaysEat()==entry.always_edible(),"Wrong always-edible flag "+entry.id());count++;
            if(entry.effectName().equals("wine")||entry.effectName().endsWith("_wine"))helper.assertTrue(item.getUseAnimation(new ItemStack(item))==net.minecraft.world.item.UseAnim.DRINK,"Wine uses eating animation "+entry.id());
        }
        helper.assertTrue(count>150,"Edible content missing");System.out.println("FOODCRAFT VERIFIED FOOD VALUES="+count);
        var level=helper.getLevel();var player=helper.makeMockPlayer();player.getAbilities().instabuild=false;
        var pos=helper.absolutePos(new BlockPos(4,2,4));level.setBlock(pos.below(),Blocks.STONE.defaultBlockState(),3);int cakes=0;
        var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos.below()),net.minecraft.core.Direction.UP,pos.below(),false);
        for(Catalog.Entry entry:Catalog.ENTRIES)if(entry.kind().equals("cake_item")){
            level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);var stack=new ItemStack(FoodCraft.item(entry.id()));player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,stack);
            var context=new net.minecraft.world.item.context.UseOnContext(player,net.minecraft.world.InteractionHand.MAIN_HAND,hit);
            var placed=stack.getItem().useOn(context);var block=FoodCraft.BLOCKS.get(new ResourceLocation(entry.crop()).getPath());
            helper.assertTrue(placed.consumesAction()&&level.getBlockState(pos).is(block)&&stack.isEmpty(),"Fruit cake placement failed or duplicated the item "+entry.id());
            player.getFoodData().setFoodLevel(10);player.removeAllEffects();
            var eaten=block.use(level.getBlockState(pos),level,pos,player,net.minecraft.world.InteractionHand.MAIN_HAND,hit);
            helper.assertTrue(eaten.consumesAction()&&player.getFoodData().getFoodLevel()==12&&level.getBlockState(pos).getValue(net.minecraft.world.level.block.CakeBlock.BITES)==1,"Cake bite state/food value changed "+entry.id());
            helper.assertTrue(player.getActiveEffects().isEmpty(),"Fruit cake incorrectly added cookie potion effects "+entry.id());
            block.randomTick(level.getBlockState(pos),level,pos,level.random);
            helper.assertTrue(level.getBlockState(pos).getValue(net.minecraft.world.level.block.CakeBlock.BITES)==(entry.path().contains("jinputao")?0:1),"Golden cake regeneration/ordinary cake state changed "+entry.id());
            cakes++;
        }
        helper.assertTrue(cakes==8,"Fruit cake variants missing");System.out.println("FOODCRAFT VERIFIED CAKE PLACEMENT/BITES="+cakes);helper.succeed();
    }
    public static void automationSoak(GameTestHelper helper){
        record Station(MachineKind kind,MachineRecipe recipe,BlockPos pos){}
        List<Station> stations=new ArrayList<>();
        int index=0;
        for(MachineKind kind:MachineKind.values()){
            if(kind==MachineKind.STOVE)continue;
            MachineRecipe recipe=helper.getLevel().getRecipeManager().getAllRecipesFor(FoodCraft.RECIPE_TYPES.get(kind)).get(0);
            BlockPos pos=new BlockPos(2+(index%3)*3,2,2+(index/3)*3);
            place(helper,kind,pos);stations.add(new Station(kind,recipe,pos));
            if(kind.heatedExternally()){var stove=place(helper,MachineKind.STOVE,pos.below());stove.setItem(0,new ItemStack(Items.COAL,64));}
            index++;
        }
        long started=System.nanoTime();long[] counts=new long[stations.size()];int[] elapsed={0};
        boolean realtime=Boolean.getBoolean("foodcraft.soak");int durationTicks=realtime?36000:4000;
        helper.onEachTick(()->{
            elapsed[0]++;
            // GameTestServer normally runs faster than real time. Pace only this opt-in soak.
            long target=realtime?started+elapsed[0]*50_000_000L:System.nanoTime();
            long remaining;
            while((remaining=target-System.nanoTime())>0)java.util.concurrent.locks.LockSupport.parkNanos(Math.min(remaining,50_000_000L));
            for(int i=0;i<stations.size();i++){
                Station station=stations.get(i);
                var machine=(MachineBlockEntity)helper.getLevel().getBlockEntity(helper.absolutePos(station.pos()));
                helper.assertTrue(machine!=null,"Machine disappeared during soak");
                for(var input:station.recipe().inputs)if(machine.getItem(input.slot()).isEmpty())machine.setItem(input.slot(),input.ingredient().getItems()[0].copyWithCount(64));
                if(station.kind()==MachineKind.CUTTING_BOARD&&machine.getItem(0).isEmpty())machine.setItem(0,new ItemStack(FoodCraft.item("caidao")));
                if(station.kind().fuelSlot>=0&&machine.getItem(station.kind().fuelSlot).isEmpty())machine.setItem(station.kind().fuelSlot,new ItemStack(Items.COAL,64));
                if(station.kind()==MachineKind.DRINK_MAKER&&station.recipe().cold&&machine.getItem(4).isEmpty())machine.setItem(4,new ItemStack(Blocks.ICE,64));
                if(station.recipe().water>0&&machine.liquid<station.recipe().water){machine.liquid=8;machine.milk=station.recipe().milk;}
                if(station.kind().heatedExternally()){
                    machine.adjustFirePower(Math.min(100,(station.recipe().minHeat+station.recipe().maxHeat)/Math.max(1,station.recipe().time/17)));
                    var stove=(MachineBlockEntity)helper.getLevel().getBlockEntity(helper.absolutePos(station.pos().below()));
                    if(stove.getItem(0).isEmpty())stove.setItem(0,new ItemStack(Items.COAL,64));
                }
                int output=station.kind().outputs[0];
                if(!machine.getItem(output).isEmpty()){
                    helper.assertTrue(ItemStack.isSameItemSameTags(machine.getItem(output),station.recipe().result),"Wrong soak product "+station.kind().id);
                    counts[i]+=machine.getItem(output).getCount();machine.removeItem(output,64);
                }
                if(station.kind().outputs.length>1)helper.assertTrue(machine.getItem(station.kind().outputs[1]).isEmpty(),"Unexpected burnt/undercooked soak batch "+station.kind().id);
                if(elapsed[0]%1200==0){var saved=machine.saveWithoutMetadata();machine.load(saved);}
            }
            if(elapsed[0]%1200==0)System.out.println("FOODCRAFT SOAK ticks="+elapsed[0]+" products="+java.util.Arrays.toString(counts));
            if(elapsed[0]>=durationTicks){
                if(realtime)helper.assertTrue(System.nanoTime()-started>=1_800_000_000_000L,"Soak did not run for 30 minutes of wall time");
                for(int i=0;i<counts.length;i++)helper.assertTrue(counts[i]>0,"No soak output from "+stations.get(i).kind().id);
                System.out.println("FOODCRAFT "+(realtime?"SOAK":"AUTOMATION")+" PASSED "+durationTicks+" ticks products="+java.util.Arrays.toString(counts));helper.succeed();
            }
        });
    }
    private static final class PlayerInventoryCheck {
        static void verify(GameTestHelper helper){
            var player=helper.makeMockPlayer();
            for(MachineKind kind:MachineKind.values()){
                var machine=place(helper,kind,new BlockPos(2,2,2));
                var menu=new MachineMenu(kind,0,player.getInventory(),machine,machine.data());
                helper.assertTrue(menu.slots.size()==kind.size+36,"Wrong slot count for "+kind.id);
                player.getInventory().setItem(9,new ItemStack(Items.COAL,64));
                menu.quickMoveStack(player,kind.size);
                int sum=0;for(int i=0;i<machine.getContainerSize();i++)if(machine.getItem(i).is(Items.COAL))sum+=machine.getItem(i).getCount();
                for(int i=0;i<player.getInventory().getContainerSize();i++)if(player.getInventory().getItem(i).is(Items.COAL))sum+=player.getInventory().getItem(i).getCount();
                helper.assertTrue(sum==64,"Shift-click duplicated or lost fuel for "+kind.id);
                player.getInventory().clearContent();
            }
        }
    }
}
