package org.foodcraft.test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.foodcraft.Catalog;
import org.foodcraft.FoodCraft;
import org.foodcraft.agriculture.FoodSaplingBlock;
import org.foodcraft.machine.MachineBlockEntity;
import org.foodcraft.machine.MachineKind;

/** Native interactions that were not covered by the first release's value checks. */
public final class DetailGameTests {
    private DetailGameTests() {}

    private static MachineBlockEntity machine(GameTestHelper helper, MachineKind kind, BlockPos local) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(local);
        if (level.getBlockEntity(pos) instanceof MachineBlockEntity previous) previous.clearContent();
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(pos, FoodCraft.BLOCKS.get(kind.id).defaultBlockState(), 3);
        return (MachineBlockEntity) level.getBlockEntity(pos);
    }

    private static void tick(MachineBlockEntity machine) {
        MachineBlockEntity.tick(machine.getLevel(), machine.getBlockPos(), machine.getBlockState(), machine);
    }

    private static net.minecraft.server.level.ServerPlayer nativePlayer(GameTestHelper helper) {
        return new net.minecraft.server.level.ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "FoodCraftDetail"));
    }

    public static void seedBuildHeight(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = helper.makeMockPlayer();
        player.getAbilities().instabuild = false;
        var horizontal = helper.absolutePos(new BlockPos(3, 2, 3));
        var ground = new BlockPos(horizontal.getX(), level.getMaxBuildHeight() - 1, horizontal.getZ());
        level.setBlock(ground, Blocks.FARMLAND.defaultBlockState(), 3);
        int count = 0;
        for (var entry : Catalog.ENTRIES) {
            if (!entry.kind().equals("seed")) continue;
            var seeds = new ItemStack(FoodCraft.item(entry.path()), 2);
            player.setItemInHand(InteractionHand.MAIN_HAND, seeds);
            var hit = new BlockHitResult(Vec3.atCenterOf(ground), Direction.UP, ground, false);
            var result = seeds.getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
            helper.assertTrue(!result.consumesAction() && seeds.getCount() == 2,
                    "Seed was consumed outside build height: " + entry.id());
            count++;
        }
        System.out.println("FOODCRAFT DETAIL SEED HEIGHT PASS varieties=" + count);
        helper.succeed();
    }

    public static void agriculture(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = nativePlayer(helper);
        player.getAbilities().instabuild = false;
        var root = helper.absolutePos(new BlockPos(6, 2, 6));
        int crops = 0;
        for (var entry : Catalog.ENTRIES) {
            if (!entry.kind().equals("crop")) continue;
            var crop = (CropBlock) FoodCraft.BLOCKS.get(entry.path());
            level.setBlock(root, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(root.below(), Blocks.FARMLAND.defaultBlockState(), 3);
            level.setBlock(root, crop.defaultBlockState(), 3);
            var boneMeal = new ItemStack(Items.BONE_MEAL, 16);
            int uses = 0;
            while (!crop.isMaxAge(level.getBlockState(root))) {
                helper.assertTrue(BoneMealItem.growCrop(boneMeal, level, root), "Bone meal refused " + entry.id());
                helper.assertTrue(++uses <= 7, "Bone meal failed to mature " + entry.id());
            }
            int remaining = boneMeal.getCount();
            helper.assertTrue(!BoneMealItem.growCrop(boneMeal, level, root) && boneMeal.getCount() == remaining,
                    "Mature crop consumed extra bone meal: " + entry.id());
            level.setBlock(root, crop.defaultBlockState(), 3);
            Blocks.FARMLAND.fallOn(level, level.getBlockState(root.below()), root.below(), player, 100);
            helper.assertTrue(level.getBlockState(root.below()).is(Blocks.DIRT) && level.getBlockState(root).isAir(),
                    "Trampling failed to remove unsupported crop: " + entry.id() + " ground=" + level.getBlockState(root.below()) + " crop=" + level.getBlockState(root));
            crops++;
        }
        for (var drop : level.getEntitiesOfClass(ItemEntity.class, new AABB(root).inflate(7))) drop.discard();
        int trees = 0;
        for (var entry : Catalog.ENTRIES) {
            if (!entry.kind().equals("sapling")) continue;
            for (var voxel : Catalog.TREES.get(entry.legacy_class())) {
                var offset = voxel.offset();
                level.setBlock(root.offset(offset[0], offset[1], offset[2]), Blocks.AIR.defaultBlockState(), 3);
            }
            level.setBlock(root.below(), Blocks.DIRT.defaultBlockState(), 3);
            var sapling = (FoodSaplingBlock) FoodCraft.BLOCKS.get(entry.path());
            level.setBlock(root, sapling.defaultBlockState(), 3);
            var obstacle = root.above();
            level.setBlock(obstacle, Blocks.STONE.defaultBlockState(), 3);
            sapling.advanceTree(level, root, level.getBlockState(root), level.random);
            helper.assertTrue(level.getBlockState(root).is(sapling) && level.getBlockState(obstacle).is(Blocks.STONE),
                    "Tree overwrote an obstacle: " + entry.id());
            level.setBlock(obstacle, Blocks.AIR.defaultBlockState(), 3);
            var fertilizer = new ItemStack(FoodCraft.item("jinkela"), 2);
            player.setItemInHand(InteractionHand.MAIN_HAND, fertilizer);
            var hit = new BlockHitResult(Vec3.atCenterOf(root), Direction.UP, root, false);
            var result = fertilizer.getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
            helper.assertTrue(result.consumesAction() && fertilizer.getCount() == 1 && !level.getBlockState(root).is(sapling),
                    "Fertilizer did not grow the tree exactly once: " + entry.id());
            trees++;
        }
        for (int x = -4; x <= 4; x++) for (int y = 0; y <= 9; y++) for (int z = -4; z <= 4; z++)
            level.setBlock(root.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3);
        var onionEntry = Catalog.ENTRIES.stream().filter(entry -> entry.kind().equals("onion")).findFirst().orElseThrow();
        var onion = (SugarCaneBlock) FoodCraft.BLOCKS.get(onionEntry.path());
        level.setBlock(root.below(), Blocks.FARMLAND.defaultBlockState(), 3);
        level.setBlock(root, onion.defaultBlockState(), 3);
        for (int step = 0; step < 60; step++) {
            var top = level.getBlockState(root.above(2)).is(onion) ? root.above(2)
                    : level.getBlockState(root.above()).is(onion) ? root.above() : root;
            onion.randomTick(level.getBlockState(top), level, top, level.random);
        }
        helper.assertTrue(level.getBlockState(root.above(2)).is(onion) && !level.getBlockState(root.above(3)).is(onion),
                "Onion must grow to three segments and stop");
        for (var drop : level.getEntitiesOfClass(ItemEntity.class, new AABB(root).inflate(7))) drop.discard();
        level.setBlock(root.below(), Blocks.AIR.defaultBlockState(), 3);
        int verifiedCrops = crops, verifiedTrees = trees;
        helper.runAfterDelay(8, () -> {
            for (int y = 0; y < 3; y++) helper.assertTrue(level.getBlockState(root.above(y)).isAir(), "Unsupported onion segment survived");
            var seed = Catalog.ENTRIES.stream().filter(entry -> entry.kind().equals("seed") && onionEntry.id().equals(entry.crop())).findFirst().orElseThrow();
            int drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(root).inflate(7)).stream()
                    .filter(entity -> entity.getItem().is(FoodCraft.item(seed.path()))).mapToInt(entity -> entity.getItem().getCount()).sum();
            helper.assertTrue(drops == 3, "Unsupported onion must drop exactly three items, got " + drops);
            System.out.println("FOODCRAFT DETAIL AGRICULTURE PASS crops=" + verifiedCrops + " trees=" + verifiedTrees + " onion_segments=3 drops=3");
            helper.succeed();
        });
    }

    public static void liquidContainers(GameTestHelper helper) {
        int tanks = 0;
        for (var kind : MachineKind.values()) {
            if (kind.liquidSlot < 0) continue;
            var machine = machine(helper, kind, new BlockPos(3, 2, 3));
            machine.liquid = 8;
            var source = new ItemStack(kind == MachineKind.DEEP_FRYER ? FoodCraft.item("huashenyou") : Items.WATER_BUCKET);
            machine.setItem(kind.liquidSlot, source.copy());
            tick(machine);
            helper.assertTrue(machine.liquid == 8 && ItemStack.matches(machine.getItem(kind.liquidSlot), source), "Full tank consumed its container: " + kind);
            machine.liquid = 0;
            var wrongPotion = PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.HEALING);
            machine.setItem(kind.liquidSlot, wrongPotion.copy());
            tick(machine);
            helper.assertTrue(machine.liquid == 0 && ItemStack.matches(machine.getItem(kind.liquidSlot), wrongPotion), "Wrong potion was consumed: " + kind);
            if (kind != MachineKind.DEEP_FRYER) {
                machine.setItem(kind.liquidSlot, PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.WATER));
                tick(machine);
                helper.assertTrue(machine.liquid == 1 && machine.getItem(kind.liquidSlot).is(Items.GLASS_BOTTLE)
                        && machine.getItem(kind.liquidSlot).getCount() == 1, "Water potion returned the wrong bottle: " + kind);
            }
            tanks++;
        }
        var drink = machine(helper, MachineKind.DRINK_MAKER, new BlockPos(3, 2, 3));
        drink.liquid = 4;
        drink.milk = true;
        drink.setItem(0, new ItemStack(Items.WATER_BUCKET));
        tick(drink);
        helper.assertTrue(drink.liquid == 4 && drink.milk && drink.getItem(0).is(Items.WATER_BUCKET), "Milk and water were mixed");
        var level = helper.getLevel();
        var player = nativePlayer(helper);
        var water = helper.absolutePos(new BlockPos(6, 2, 6));
        player.setPos(water.getX() + 0.5, water.getY(), water.getZ() - 2.0);
        player.setYRot(0); player.setXRot(24.2F); player.getAbilities().instabuild = false;
        for (int slot = 0; slot < 36; slot++) player.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(FoodCraft.item("jinghuashuitong")));
        for (int use = 0; use < 16; use++) {
            level.setBlock(water, Blocks.WATER.defaultBlockState(), 3);
            var result = player.getMainHandItem().getItem().use(level, player, InteractionHand.MAIN_HAND);
            helper.assertTrue(result.getResult().consumesAction(), "Full inventory prevented purifier use " + use);
            player.setItemInHand(InteractionHand.MAIN_HAND, result.getObject());
        }
        int waters = level.getEntitiesOfClass(ItemEntity.class, new AABB(water).inflate(7)).stream()
                .filter(entity -> entity.getItem().is(FoodCraft.item("water"))).mapToInt(entity -> entity.getItem().getCount()).sum();
        helper.assertTrue(waters == 16 && player.getMainHandItem().is(Items.BUCKET) && player.getMainHandItem().getCount() == 1,
                "Full inventory lost purifier products or duplicated the final bucket: " + waters);
        System.out.println("FOODCRAFT DETAIL CONTAINERS PASS tanks=" + tanks + " full_inventory_water=16 final_buckets=1");
        helper.succeed();
    }

    public static void edibleInteractions(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = helper.makeMockPlayer();
        player.getAbilities().instabuild = false;
        int tested = 0;
        for (var entry : Catalog.ENTRIES) {
            if (entry.id().startsWith("minecraft:") || entry.kind().equals("debug") || entry.nutrition() == 0) continue;
            var item = FoodCraft.item(entry.path());
            player.removeAllEffects(); player.clearFire();
            player.getFoodData().setFoodLevel(20);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item, 2));
            var full = item.use(level, player, InteractionHand.MAIN_HAND);
            helper.assertTrue(full.getResult().consumesAction() == entry.always_edible(), "Wrong full-hunger use result: " + entry.id());
            player.stopUsingItem();
            player.getFoodData().setFoodLevel(0); player.getFoodData().setSaturation(0);
            var stack = new ItemStack(item, 2);
            if(entry.effectName().equals("milk")){
                player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.POISON,400,2));
                player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED,400,2));
            }
            var eaten = item.finishUsingItem(stack, level, player);
            helper.assertTrue(eaten.getCount() == 1 && player.getFoodData().getFoodLevel() == Math.min(20, entry.nutrition()),
                    "Native consumption count or nutrition changed: " + entry.id());
            helper.assertTrue(Math.abs(player.getFoodData().getSaturationLevel() - Math.min(entry.nutrition(), entry.nutrition() * entry.saturation() * 2)) < 0.0001,
                    "Native consumption saturation changed: " + entry.id());
            if (entry.effectName().equals("none")) helper.assertTrue(player.getActiveEffects().isEmpty(), "Ordinary food has an unexpected effect: " + entry.id());
            verifyFoodEffects(helper,player,entry);
            tested++;
        }
        helper.assertTrue(tested == 189, "Food interaction coverage is incomplete: " + tested);
        var staple=Catalog.ENTRIES.stream().filter(entry->entry.effectName().equals("staple")).findFirst().orElseThrow();
        var wine=Catalog.ENTRIES.stream().filter(entry->entry.effectName().equals("wine")).findFirst().orElseThrow();
        var stapleEffects=new java.util.HashSet<net.minecraft.world.effect.MobEffect>();var wineGroups=new java.util.HashSet<Boolean>();
        for(int seed=0;seed<256;seed++){
            level.random.setSeed(seed*0x9E3779B97F4A7C15L);player.removeAllEffects();FoodCraft.item(staple.path()).finishUsingItem(new ItemStack(FoodCraft.item(staple.path())),level,player);
            stapleEffects.addAll(player.getActiveEffects().stream().map(net.minecraft.world.effect.MobEffectInstance::getEffect).toList());verifyFoodEffects(helper,player,staple);
            level.random.setSeed((seed+1000L)*0x9E3779B97F4A7C15L);player.removeAllEffects();FoodCraft.item(wine.path()).finishUsingItem(new ItemStack(FoodCraft.item(wine.path())),level,player);
            wineGroups.add(player.hasEffect(net.minecraft.world.effect.MobEffects.JUMP));verifyFoodEffects(helper,player,wine);
        }
        helper.assertTrue(stapleEffects.size()==7&&wineGroups.size()==2,"Random food effect branches were not all exercised: staple="+stapleEffects.size()+" wine="+wineGroups.size());
        System.out.println("FOODCRAFT DETAIL FOOD EFFECTS PASS staple_branches=7 wine_groups=2 seeded_trials=256 exact_durations_and_amplifiers=true");
        System.out.println("FOODCRAFT DETAIL FOOD INTERACTIONS PASS items=" + tested + " hungry_and_full=true native_consumption=true");
        helper.succeed();
    }

    private static void verifyFoodEffects(GameTestHelper helper,net.minecraft.world.entity.player.Player player,Catalog.Entry entry){
        var effects=player.getActiveEffects();
        if(entry.effectName().equals("milk")){helper.assertTrue(effects.isEmpty(),"Milk did not cure both positive and negative effects");return;}
        if(entry.effectName().equals("chili")){helper.assertTrue(player.getRemainingFireTicks()==60&&effects.isEmpty(),"Chili fire duration/effects changed");return;}
        if(entry.effectName().equals("none"))return;
        int duration=switch(entry.effectName()){case "gold_cookie"->1200;case "gold_grape","gold_apple"->36000;case "gold_grape_wine","gold_apple_wine"->3600;default->600;};
        int amplifier=switch(entry.effectName()){case "staple","gold_cookie"->1;case "wine"->3;default->4;};
        int count=switch(entry.effectName()){case "staple"->1;case "wine"->4;case "gold_grape_wine","gold_apple_wine"->6;default->3;};
        helper.assertTrue(effects.size()==count,"Wrong food effect count "+entry.id()+": "+effects.size());
        for(var effect:effects)helper.assertTrue(effect.getDuration()==duration&&effect.getAmplifier()==amplifier,"Food effect duration/amplifier changed "+entry.id());
    }

    public static void hopperAutomation(GameTestHelper helper) {
        var level = helper.getLevel();
        var kind = MachineKind.PRESSURE_COOKER;
        var pos = helper.absolutePos(new BlockPos(5, 4, 5));
        var recipe = level.getRecipeManager().getAllRecipesFor(FoodCraft.RECIPE_TYPES.get(kind)).stream()
                .filter(value -> value.result.is(FoodCraft.item("pidanshourouzhou"))).findFirst().orElseThrow();
        var machine = machine(helper, kind, new BlockPos(5, 4, 5));
        machine.liquid = 8;
        machine.setItem(kind.fuelSlot, new ItemStack(Items.COAL, 8));
        level.setBlock(pos.above(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN), 3);
        level.setBlock(pos.above(2), Blocks.CHEST.defaultBlockState(), 3);
        level.setBlock(pos.below(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN), 3);
        level.setBlock(pos.below(2), Blocks.CHEST.defaultBlockState(), 3);
        var inputChest = (Container) level.getBlockEntity(pos.above(2));
        for(int index=0;index<recipe.inputs.size();index++){
            var input=recipe.inputs.get(recipe.inputs.size()-1-index);
            inputChest.setItem(index,input.ingredient().getItems()[0].copyWithCount(8));
        }
        helper.runAfterDelay(1300,()->helper.assertTrue(count((Container)level.getBlockEntity(pos.below(2)),recipe.result.getItem())>0,
                "Native hopper never completed the recipe: "+java.util.Arrays.stream(kind.inputs).mapToObj(slot->machine.getItem(slot).toString()).toList()));
        helper.onEachTick(() -> {
            var chest = (Container) level.getBlockEntity(pos.below(2));
            int delivered = count(chest, recipe.result.getItem());
            if (delivered == 0) return;
            for(var input:recipe.inputs){
                var ingredient=input.ingredient().getItems()[0].getItem();
                int remaining=count(inputChest,ingredient)+count((Container)level.getBlockEntity(pos.above()),ingredient)+count(machine,ingredient);
                helper.assertTrue(remaining==8-input.count(),"Native hoppers duplicated or lost an ingredient: "+ingredient+" count="+remaining);
            }
            int products = delivered + count((Container) level.getBlockEntity(pos.below()), recipe.result.getItem())
                    + count(machine, recipe.result.getItem());
            helper.assertTrue(products == recipe.result.getCount(), "Native hoppers duplicated or lost products");
            helper.assertTrue(machine.liquid == 8 - recipe.water, "Native hopper batch changed liquid consumption");
            System.out.println("FOODCRAFT DETAIL HOPPERS PASS recipe=" + recipe.getId() + " reverse_input_order=true products=" + products);
            helper.succeed();
        });
    }

    public static void heatButtonProtocol(GameTestHelper helper){
        var machine=machine(helper,MachineKind.POT,new BlockPos(3,2,3));
        var player=helper.makeMockPlayer();player.setPos(Vec3.atCenterOf(machine.getBlockPos()));
        var menu=new org.foodcraft.machine.MachineMenu(MachineKind.POT,7,player.getInventory(),machine,machine.data());
        for(int power=0;power<=100;power++){
            var packet=new net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket(7,2+power);
            var buffer=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            try{
                packet.write(buffer);var decoded=new net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket(buffer);
                helper.assertTrue(menu.clickMenuButton(player,decoded.getButtonId())&&machine.firePower==power,"Heat button failed protocol roundtrip at power="+power+" decoded="+decoded.getButtonId());
            }finally{buffer.release();}
        }
        System.out.println("FOODCRAFT DETAIL HEAT BUTTON PASS values=101 native_packet_roundtrip=true");helper.succeed();
    }

    private static int count(Container inventory, net.minecraft.world.item.Item item) {
        int count = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) if (inventory.getItem(slot).is(item)) count += inventory.getItem(slot).getCount();
        return count;
    }

    public static void recipeTransfers(GameTestHelper helper){
        var level=helper.getLevel();var player=helper.makeMockPlayer();int checked=0;
        for(var kind:MachineKind.values()){
            if(kind==MachineKind.STOVE)continue;
            for(var recipe:level.getRecipeManager().getAllRecipesFor(FoodCraft.RECIPE_TYPES.get(kind))){
                for(boolean maximum:new boolean[]{false,true}){
                    var machine=machine(helper,kind,new BlockPos(3,2,3));player.getInventory().clearContent();
                    var menu=new org.foodcraft.machine.MachineMenu(kind,7,player.getInventory(),machine,machine.data());
                    for(int index=0;index<recipe.inputs.size();index++){
                        var input=recipe.inputs.get(index);player.getInventory().setItem(9+index,input.ingredient().getItems()[0].copyWithCount(input.count()*3));
                    }
                    for(int slot:kind.inputs)if(recipe.inputs.stream().noneMatch(input->input.slot()==slot)){machine.setItem(slot,new ItemStack(Items.DIRT,2));break;}
                    var before=transferLedger(menu);
                    var prepared=org.foodcraft.machine.MachineTransfers.prepare(menu,recipe,maximum);
                    helper.assertTrue(prepared.successful()&&prepared.plan().batches==(maximum?3:1),"Recipe transfer plan failed "+recipe.getId());
                    helper.assertTrue(before.equals(transferLedger(menu)),"Recipe transfer simulation changed inventory");
                    helper.assertTrue(prepared.plan().commit(menu),"Recipe transfer commit failed "+recipe.getId());
                    helper.assertTrue(before.equals(transferLedger(menu)),"Recipe transfer lost or duplicated an item "+recipe.getId());
                    for(var input:recipe.inputs)helper.assertTrue(machine.getItem(input.slot()).getCount()==input.count()*(maximum?3:1),"Wrong transferred ingredient quantity "+recipe.getId());
                    helper.assertTrue(recipe.matches(machine,level),"Transferred ingredients did not match the recipe "+recipe.getId());
                    checked++;
                }
            }
        }
        var machine=machine(helper,MachineKind.FERMENTING_BARREL,new BlockPos(3,2,3));player.getInventory().clearContent();
        var menu=new org.foodcraft.machine.MachineMenu(machine.kind,7,player.getInventory(),machine,machine.data());
        var counted=new org.foodcraft.recipe.MachineRecipe(new ResourceLocation("foodcraft_details:counted"),machine.kind,
                java.util.List.of(new org.foodcraft.recipe.MachineRecipe.Input(0,net.minecraft.world.item.crafting.Ingredient.of(Items.OAK_PLANKS,Items.BIRCH_PLANKS),2),
                                 new org.foodcraft.recipe.MachineRecipe.Input(1,net.minecraft.world.item.crafting.Ingredient.of(Items.OAK_PLANKS),1)),
                new int[]{0,1,2},new ItemStack(Items.STICK),5,0,0,Integer.MAX_VALUE,false,false,0);
        player.getInventory().setItem(9,new ItemStack(Items.OAK_PLANKS));player.getInventory().setItem(10,new ItemStack(Items.BIRCH_PLANKS,2));
        var plan=org.foodcraft.machine.MachineTransfers.prepare(menu,counted,false);
        helper.assertTrue(plan.successful()&&plan.plan().commit(menu)&&machine.getItem(0).is(Items.BIRCH_PLANKS)&&machine.getItem(0).getCount()==2&&machine.getItem(1).is(Items.OAK_PLANKS),"Overlapping ingredients were allocated greedily or counts were ignored");
        machine=machine(helper,MachineKind.MILLING_MACHINE,new BlockPos(3,2,3));player.getInventory().clearContent();
        menu=new org.foodcraft.machine.MachineMenu(machine.kind,7,player.getInventory(),machine,machine.data());
        var recipe=level.getRecipeManager().getAllRecipesFor(FoodCraft.RECIPE_TYPES.get(machine.kind)).get(0);
        machine.setItem(0,recipe.inputs.get(0).ingredient().getItems()[0].copyWithCount(3));
        for(int slot=0;slot<36;slot++)player.getInventory().setItem(slot,new ItemStack(Items.COBBLESTONE,64));
        var before=transferLedger(menu);plan=org.foodcraft.machine.MachineTransfers.prepare(menu,recipe,false);
        helper.assertTrue(!plan.successful()&&plan.failure()==org.foodcraft.machine.MachineTransfers.Failure.INVENTORY_FULL&&before.equals(transferLedger(menu)),"Full inventory partially transferred or discarded old ingredients");
        player.getInventory().clearContent();plan=org.foodcraft.machine.MachineTransfers.prepare(menu,recipe,false);
        helper.assertTrue(plan.successful(),"Stale transfer setup failed");machine.setItem(0,ItemStack.EMPTY);
        helper.assertTrue(!plan.plan().commit(menu),"Stale transfer snapshot was committed");
        System.out.println("FOODCRAFT DETAIL RECIPE TRANSFERS PASS legacy_cases="+checked+" counted_overlap=true full_inventory=true stale_snapshot=true");helper.succeed();
    }

    private static java.util.Map<String,Integer> transferLedger(org.foodcraft.machine.MachineMenu menu){
        var ledger=new java.util.TreeMap<String,Integer>();
        for(var slot:menu.slots){var stack=slot.getItem();if(stack.isEmpty())continue;
            String key=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem())+"|"+(stack.hasTag()?stack.getTag().toString():"");
            ledger.merge(key,stack.getCount(),Integer::sum);
        }
        return ledger;
    }

    public static void changedRecipeProgress(GameTestHelper helper){
        var level=helper.getLevel();var manager=level.getRecipeManager();var original=java.util.List.copyOf(manager.getRecipes());
        var id=new ResourceLocation("foodcraft_details:progress");
        var input=new org.foodcraft.recipe.MachineRecipe.Input(0,net.minecraft.world.item.crafting.Ingredient.of(Items.STICK),2);
        var first=new org.foodcraft.recipe.MachineRecipe(id,MachineKind.MILLING_MACHINE,java.util.List.of(input),new int[]{0},new ItemStack(Items.BREAD),20,0,0,Integer.MAX_VALUE,false,false,0);
        var replacement=new org.foodcraft.recipe.MachineRecipe(id,MachineKind.MILLING_MACHINE,java.util.List.of(input),new int[]{0},new ItemStack(Items.SUGAR,2),30,0,0,Integer.MAX_VALUE,false,false,0);
        try{
            var recipes=new java.util.ArrayList<net.minecraft.world.item.crafting.Recipe<?>>(original);recipes.add(first);manager.replaceRecipes(recipes);
            var machine=machine(helper,MachineKind.MILLING_MACHINE,new BlockPos(3,2,3));machine.setItem(0,new ItemStack(Items.STICK,64));machine.burnTime=1000;
            for(int step=0;step<5;step++)tick(machine);
            helper.assertTrue(machine.progress==5,"Progress reload setup failed");
            machine.setItem(1,new ItemStack(Items.COBBLESTONE,64));recipes.remove(first);recipes.add(replacement);manager.replaceRecipes(recipes);tick(machine);
            helper.assertTrue(machine.progress==0&&machine.totalTime==30,"Same-ID recipe reload retained old progress while output was blocked: "+machine.progress+" / "+machine.totalTime);
            machine.setItem(1,ItemStack.EMPTY);for(int step=0;step<9;step++)tick(machine);
            var saved=machine.saveWithoutMetadata();machine.load(saved);tick(machine);
            helper.assertTrue(machine.progress==10,"Unchanged recipe progress did not survive its NBT reload");
            for(int step=10;step<30;step++)tick(machine);
            helper.assertTrue(machine.getItem(1).is(Items.SUGAR)&&machine.getItem(1).getCount()==2&&machine.getItem(0).getCount()==62,"Reloaded recipe produced the wrong output or consumed the wrong quantity");
            System.out.println("FOODCRAFT DETAIL RECIPE CHANGE PASS same_id_reload=true blocked_output=true nbt_resume=true");helper.succeed();
        }finally{manager.replaceRecipes(original);}
    }
}
