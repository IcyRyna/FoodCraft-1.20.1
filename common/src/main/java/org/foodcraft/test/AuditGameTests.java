package org.foodcraft.test;

import com.google.gson.JsonParser;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.foodcraft.Catalog;
import org.foodcraft.FoodCraft;
import org.foodcraft.machine.*;
import org.foodcraft.recipe.MachineRecipe;
import java.util.ArrayList;
import java.util.List;

/** Explicit regression coverage for the gaps identified by the post-release audit. */
public final class AuditGameTests {
    private AuditGameTests() {}
    private static MachineBlockEntity place(GameTestHelper h,MachineKind kind,BlockPos local){
        var level=h.getLevel();var pos=h.absolutePos(local);
        if(level.getBlockEntity(pos) instanceof MachineBlockEntity previous)previous.clearContent();
        level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
        level.setBlock(pos,FoodCraft.BLOCKS.get(kind.id).defaultBlockState(),3);
        return (MachineBlockEntity)level.getBlockEntity(pos);
    }
    private static void tick(MachineBlockEntity machine,int count){
        for(int i=0;i<count;i++)MachineBlockEntity.tick(machine.getLevel(),machine.getBlockPos(),machine.getBlockState(),machine);
    }
    private static void prepareFailure(MachineBlockEntity machine,MachineRecipe recipe,boolean over){
        machine.clearContent();
        for(var input:recipe.inputs)machine.setItem(input.slot(),input.ingredient().getItems()[0].copyWithCount(input.count()));
        var tag=machine.saveWithoutMetadata();
        tag.putInt("Progress",over?16:recipe.time-1);tag.putInt("TotalTime",recipe.time);
        tag.putInt("AccumulatedHeat",over?recipe.maxHeat+1:Math.max(0,recipe.minHeat-1));
        tag.putInt("FirePower",0);tag.putString("ActiveRecipe",recipe.getId().toString());
        tag.putString("ActiveRecipeFingerprint",recipe.fingerprint);machine.load(tag);
    }
    public static void heatFailures(GameTestHelper h){
        int cases=0;var level=h.getLevel();var local=new BlockPos(3,3,3);
        for(var kind:List.of(MachineKind.POT,MachineKind.FRYING_PAN)){
            var machine=place(h,kind,local);var stove=place(h,MachineKind.STOVE,local.below());stove.burnTime=1000000;
            for(var recipe:level.getRecipeManager().getAllRecipesFor(FoodCraft.RECIPE_TYPES.get(kind))){
                for(boolean over:new boolean[]{false,true}){
                    if(!over&&recipe.minHeat==0||over&&recipe.maxHeat==Integer.MAX_VALUE)continue;
                    for(int mode=0;mode<5;mode++){
                        prepareFailure(machine,recipe,over);
                        ItemStack expected=over?new ItemStack(Items.COAL):machine.getItem(kind.inputs[0]).copyWithCount(1);
                        h.assertTrue(!expected.isEmpty(),"Undercooked recipe has no first ingredient "+recipe.getId());
                        ItemStack occupied=mode==0?ItemStack.EMPTY:mode==3?new ItemStack(Items.BARRIER,1):expected.copyWithCount(mode==1?63:mode==2?64:1);
                        if(mode==4)occupied.getOrCreateTag().putString("AuditDifferent","failure-output");
                        machine.setItem(kind.outputs[1],occupied);
                        var before=machine.saveWithoutMetadata();tick(machine,1);
                        boolean success=mode==0||mode==1;
                        if(success){
                            h.assertTrue(machine.isEmpty()==false&&machine.getItem(kind.outputs[1]).getCount()==(mode==0?1:64),"Failure output count "+recipe.getId()+"/"+over+"/"+mode);
                            for(var input:recipe.inputs)h.assertTrue(machine.getItem(input.slot()).isEmpty(),"Failure did not consume exactly the batch");
                            h.assertTrue(machine.proficiency==0&&machine.progress==0,"Failed cooking increased skill or kept completed progress");
                        }else{
                            h.assertTrue(ItemStack.matches(occupied,machine.getItem(kind.outputs[1])),"Blocked failure changed output");
                            h.assertTrue(before.getList("Items",10).equals(machine.saveWithoutMetadata().getList("Items",10)),"Blocked failure swallowed ingredients");
                            tick(machine,10);machine.load(machine.saveWithoutMetadata());machine.setItem(kind.outputs[1],ItemStack.EMPTY);tick(machine,1);
                            h.assertTrue(ItemStack.isSameItemSameTags(expected,machine.getItem(kind.outputs[1]))&&machine.getItem(kind.outputs[1]).getCount()==1,"Failure save/resume changed the output");
                            tick(machine,10);h.assertTrue(machine.getItem(kind.outputs[1]).getCount()==1,"Failure was committed twice");
                        }
                        cases++;
                    }
                }
            }
            var recipe=level.getRecipeManager().getAllRecipesFor(FoodCraft.RECIPE_TYPES.get(kind)).get(0);
            prepareFailure(machine,recipe,false);machine.progress=0;machine.accumulatedHeat=0;machine.adjustFirePower(50);
            tick(machine,17);h.assertTrue(machine.accumulatedHeat==25,"Initial heat power changed");
            machine.adjustFirePower(100);tick(machine,17);h.assertTrue(machine.accumulatedHeat==75,"Mid-batch heat adjustment changed");
            level.removeBlock(stove.getBlockPos(),false);var before=machine.saveWithoutMetadata();tick(machine,1);
            h.assertTrue(machine.progress==0&&machine.accumulatedHeat==0&&before.getList("Items",10).equals(machine.saveWithoutMetadata().getList("Items",10)),"Removing stove consumed or retained stale heat");
        }
        var original=List.copyOf(level.getRecipeManager().getRecipes());List<String> containerFailures=new ArrayList<>();
        try{
            for(int inputSlot:new int[]{0,1}){
                var machine=place(h,MachineKind.POT,new BlockPos(3,2,3));var stove=place(h,MachineKind.STOVE,new BlockPos(3,1,3));stove.burnTime=1000;
                var item=inputSlot==0?Items.MILK_BUCKET:Items.STICK;
                var custom=new MachineRecipe(FoodCraft.id("audit_failure_refund"),MachineKind.POT,List.of(new MachineRecipe.Input(inputSlot,Ingredient.of(item),1)),new int[]{},new ItemStack(Items.BREAD),1,0,1,10,false,false,0);
                level.getRecipeManager().replaceRecipes(List.of(custom));machine.setItem(inputSlot,new ItemStack(item));machine.firePower=0;
                var unchanged=machine.saveWithoutMetadata();
                var foreign=new MachineRecipe(FoodCraft.id("audit_foreign"),MachineKind.MILLING_MACHINE,List.of(new MachineRecipe.Input(0,Ingredient.of(item),1)),new int[]{},new ItemStack(Items.BREAD),1,0,0,0,false,false,0);
                h.assertTrue(!machine.commit(custom,ItemStack.EMPTY,13)&&!machine.commit(custom,new ItemStack(Items.BREAD),inputSlot)&&!machine.commit(foreign,new ItemStack(Items.BREAD),13),"Invalid commit was accepted");
                h.assertTrue(unchanged.equals(machine.saveWithoutMetadata()),"Invalid commit mutated state/inventory");
                try{tick(machine,1);}catch(RuntimeException error){containerFailures.add("Failed-output processing threw for valid input slot "+inputSlot+": "+error.getMessage());continue;}
                if(!machine.getItem(13).is(item)||machine.getItem(13).getCount()!=1)containerFailures.add("Undercooked recipe lost its first actual ingredient slot="+inputSlot);
                if(inputSlot==0){int containers=0;for(int slot=0;slot<machine.getContainerSize();slot++)if(machine.getItem(slot).is(Items.BUCKET)||machine.getItem(slot).is(Items.MILK_BUCKET))containers+=machine.getItem(slot).getCount();
                    if(containers!=1)containerFailures.add("Undercooked refund duplicated a container: "+containers);}
            }
            var honey=place(h,MachineKind.POT,new BlockPos(3,2,3));var stove=place(h,MachineKind.STOVE,new BlockPos(3,1,3));stove.burnTime=1000;
            var counted=new MachineRecipe(FoodCraft.id("audit_counted_refund"),MachineKind.POT,List.of(new MachineRecipe.Input(0,Ingredient.of(Items.HONEY_BOTTLE),3)),new int[]{},new ItemStack(Items.BREAD),1,0,1,10,false,false,0);
            level.getRecipeManager().replaceRecipes(List.of(counted));honey.setItem(0,new ItemStack(Items.HONEY_BOTTLE,3));honey.firePower=0;tick(honey,1);
            h.assertTrue(honey.getItem(13).is(Items.HONEY_BOTTLE)&&honey.getItem(13).getCount()==1&&honey.getItem(0).is(Items.GLASS_BOTTLE)&&honey.getItem(0).getCount()==2,"Counted refund did not preserve three containers");
        }finally{level.getRecipeManager().replaceRecipes(original);}
        h.assertTrue(containerFailures.isEmpty(),String.join("; ",containerFailures));
        System.out.println("FOODCRAFT AUDIT FAILURE REFUND PASS cases=3 invalid_commits=6 container_conservation=true nonzero_first_slot=true");
        System.out.println("FOODCRAFT AUDIT HEAT FAILURES PASS cases="+cases+" save_resume=true stove_removal=true power_change=true");h.succeed();
    }
    public static void specialTools(GameTestHelper h){
        var level=h.getLevel();var player=h.makeMockPlayer();var pos=h.absolutePos(new BlockPos(3,2,3));
        var hit=new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false);int cases=0;
        for(var entry:Catalog.ENTRIES)if(entry.kind().equals("multitool")){
            var item=FoodCraft.item(entry.id());boolean heal=entry.path().contains("anbi");
            for(var hand:InteractionHand.values())for(boolean creative:new boolean[]{false,true})for(int enchant:new int[]{0,3})
                for(int damage:new int[]{0,1994,1995,1999,2000,4994,4995,4999}){
                    level.setBlock(pos,Blocks.STONE.defaultBlockState(),3);level.setBlock(pos.above(),Blocks.AIR.defaultBlockState(),3);
                    player.getAbilities().instabuild=creative;player.getAbilities().mayBuild=true;player.setHealth(7);
                    ItemStack stack=new ItemStack(item);stack.setDamageValue(damage);if(enchant>0)stack.enchant(Enchantments.UNBREAKING,enchant);
                    player.setItemInHand(hand,stack);
                    var result=item.useOn(new UseOnContext(player,hand,hit));
                    h.assertTrue(result.consumesAction(),"Special tool refused a valid target");
                    h.assertTrue(heal?player.getHealth()==20:level.getBlockState(pos.above()).is(Blocks.TORCH),"Special tool effect missing "+entry.id());
                    h.assertTrue(creative?stack.getDamageValue()==damage&&!stack.isEmpty():damage+5>=2000?stack.isEmpty():stack.getDamageValue()==damage+5,
                        "Legacy right-click wear/break mismatch "+entry.id()+" hand="+hand+" creative="+creative+" enchant="+enchant+" damage="+damage+" actual="+stack.getDamageValue());
                    cases++;
                }
            player.getAbilities().instabuild=false;player.getAbilities().mayBuild=false;
            ItemStack denied=new ItemStack(item);player.setItemInHand(InteractionHand.MAIN_HAND,denied);
            h.assertTrue(!item.useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,hit)).consumesAction()&&denied.getDamageValue()==0,"Adventure restriction consumed tool");
            player.getAbilities().mayBuild=true;
            for(var block:List.of(Blocks.STONE,Blocks.OAK_LOG,Blocks.DIRT,Blocks.DIAMOND_ORE,Blocks.OBSIDIAN))
                h.assertTrue(item.getDestroySpeed(new ItemStack(item),block.defaultBlockState())==8&&item.isCorrectToolForDrops(block.defaultBlockState()),"Multitool mining role "+block);
            var modifiers=item.getDefaultAttributeModifiers(net.minecraft.world.entity.EquipmentSlot.MAINHAND);
            h.assertTrue(modifiers.get(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE).stream().anyMatch(m->m.getAmount()==12),"Multitool attack modifier changed");
            ItemStack mining=new ItemStack(item);item.mineBlock(mining,level,Blocks.STONE.defaultBlockState(),pos,player);
            h.assertTrue(mining.getDamageValue()==1,"Native mining wear changed");
            item.hurtEnemy(mining,player,player);h.assertTrue(mining.getDamageValue()==3,"Native attack wear changed");
            if(heal){
                player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(40);player.setHealth(30);
                player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(item));
                item.useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,hit));
                h.assertTrue(player.getHealth()==30,"Healing tool reduced an enhanced-health player");
                player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(20);player.setHealth(20);
            }
        }
        System.out.println("FOODCRAFT AUDIT SPECIAL TOOLS PASS cases="+cases+" hands=2 unbreaking=true adventure=true mining_combat=true");h.succeed();
    }
    private static LootParams blockLoot(GameTestHelper h,net.minecraft.world.level.block.Block block,ItemStack tool){
        return new LootParams.Builder(h.getLevel()).withParameter(LootContextParams.ORIGIN,Vec3.atCenterOf(h.absolutePos(new BlockPos(3,2,3))))
            .withParameter(LootContextParams.BLOCK_STATE,block.defaultBlockState()).withParameter(LootContextParams.TOOL,tool).create(LootContextParamSets.BLOCK);
    }
    public static void acquisitionLoot(GameTestHelper h){
        var level=h.getLevel();var seen=new java.util.HashSet<String>();int leafSamples=0,chestSamples=0;
        var silk=new ItemStack(Items.DIAMOND_AXE);silk.enchant(Enchantments.SILK_TOUCH,1);
        var fortune=new ItemStack(Items.DIAMOND_AXE);fortune.enchant(Enchantments.BLOCK_FORTUNE,3);
        for(String name:List.of("oak","spruce","birch","jungle","acacia","dark_oak","mangrove","cherry"))
            for(ItemStack tool:List.of(ItemStack.EMPTY,new ItemStack(Items.SHEARS),silk,fortune)){
                var block=BuiltInRegistries.BLOCK.get(new ResourceLocation("minecraft",name+"_leaves"));
                var table=level.getServer().getLootData().getLootTable(new ResourceLocation("minecraft","blocks/"+name+"_leaves"));
                int total=0;
                for(int sample=0;sample<8000;sample++)for(var drop:table.getRandomItems(blockLoot(h,block,tool),0xA170000L+sample)){
                    var id=BuiltInRegistries.ITEM.getKey(drop.getItem());if(id.getNamespace().equals("foodcraft")){
                        h.assertTrue(Catalog.ENTRIES.stream().anyMatch(e->e.kind().equals("sapling")&&e.id().equals(id.toString()))&&drop.getCount()==1,"Invalid leaf acquisition");
                        total++;seen.add(id.toString());
                    }
                }
                // Legacy leaf additions are independent of the native tool-specific loot pool.
                h.assertTrue(total>40&&total<125,"Leaf chance or duplicate injection changed "+name+" tool="+tool+" total="+total);leafSamples+=8000;
            }
        h.assertTrue(seen.size()==17,"Leaf pool omitted a fruit sapling "+seen.size());
        var expected=java.util.Set.of("zongye","douban","galikuai","hetaosu","xiangchang","laweixunliao","kafei");
        for(String name:List.of("simple_dungeon","abandoned_mineshaft","stronghold_corridor","stronghold_crossing","stronghold_library","jungle_temple")){
            seen.clear();var table=level.getServer().getLootData().getLootTable(new ResourceLocation("minecraft","chests/"+name));
            var params=new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN,Vec3.atCenterOf(h.absolutePos(new BlockPos(3,2,3)))).create(LootContextParamSets.CHEST);
            for(int sample=0;sample<2000;sample++){
                int rolls=0;
                for(var drop:table.getRandomItems(params,0xC170000L+sample)){
                    var id=BuiltInRegistries.ITEM.getKey(drop.getItem());if(!id.getNamespace().equals("foodcraft"))continue;
                    h.assertTrue(expected.contains(id.getPath())&&drop.getCount()>=1&&drop.getCount()<=10,"Invalid chest item/count "+id);
                    if(id.getPath().equals("douban")||id.getPath().equals("kafei"))h.assertTrue(drop.getCount()==1,"Fixed chest count changed "+id);
                    rolls++;seen.add(id.getPath());
                }
                h.assertTrue(rolls==1,"Chest injected zero or duplicate FoodCraft pools "+name+" rolls="+rolls);chestSamples++;
            }
            h.assertTrue(seen.equals(expected),"Chest omitted content "+name);
        }
        var pos=h.absolutePos(new BlockPos(3,2,3));var squid=EntityType.SQUID.create(level);
        squid.setPos(pos.getX()+0.5,pos.getY(),pos.getZ()+0.5);level.addFreshEntity(squid);squid.hurt(level.damageSources().generic(),Float.MAX_VALUE);
        final int leaves=leafSamples,chests=chestSamples;
        h.runAfterDelay(3,()->{
            int meat=level.getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(3)).stream().filter(e->e.getItem().is(FoodCraft.item("youyurou"))).mapToInt(e->e.getItem().getCount()).sum();
            h.assertTrue(meat==3,"Native squid death lost/duplicated meat "+meat);
            System.out.println("FOODCRAFT AUDIT ACQUISITION PASS leaf_samples="+leaves+" chest_samples="+chests+" squid_meat=3");h.succeed();
        });
    }
    private static void rejects(GameTestHelper h,Runnable run,String label){
        boolean rejected=false;try{run.run();}catch(RuntimeException expected){rejected=true;}
        h.assertTrue(rejected,"Invalid data was accepted: "+label);
    }
    public static void malformedRecipes(GameTestHelper h){
        var serializer=new MachineRecipe.Serializer(MachineKind.MILLING_MACHINE);var id=FoodCraft.id("audit_recipe");
        String base="{\"inputs\":[{\"slot\":0,\"ingredient\":{\"item\":\"minecraft:stick\"}}],\"result\":{\"item\":\"minecraft:bread\"},\"time\":20";
        for(int length:new int[]{2,13,24}){
            String slots=java.util.stream.IntStream.range(0,length).mapToObj(i->"0").collect(java.util.stream.Collectors.joining(","));
            rejects(h,()->serializer.fromJson(id,JsonParser.parseString(base+",\"exclusive_slots\":["+slots+"]}").getAsJsonObject()),"repeated exclusive slots "+length);
        }
        for(String suffix:List.of(",\"water\":9}",",\"water\":-1}",",\"min_heat\":-1}",",\"max_heat\":-1}",",\"experience\":-1}"))
            rejects(h,()->serializer.fromJson(id,JsonParser.parseString(base+suffix).getAsJsonObject()),suffix);
        for(int size:new int[]{0,1,2,3,5}){
            var buf=new FriendlyByteBuf(Unpooled.buffer());try{buf.writeBytes(new byte[size]);rejects(h,()->serializer.fromNetwork(id,buf),"truncated recipe "+size);}finally{buf.release();}
        }
        var complete=serializer.fromJson(id,JsonParser.parseString(base+"}").getAsJsonObject());
        byte[] encoded;var full=new FriendlyByteBuf(Unpooled.buffer());
        try{serializer.toNetwork(full,complete);encoded=new byte[full.readableBytes()];full.getBytes(0,encoded);}finally{full.release();}
        for(int length=0;length<encoded.length;length++){
            var prefix=new FriendlyByteBuf(Unpooled.wrappedBuffer(java.util.Arrays.copyOf(encoded,length)));
            try{rejects(h,()->serializer.fromNetwork(id,prefix),"valid recipe prefix "+length);}finally{prefix.release();}
        }
        var buf=new FriendlyByteBuf(Unpooled.buffer());
        try{new MachineTransfers.Request(1,id,false).write(buf);buf.writeByte(1);rejects(h,()->MachineTransfers.Request.read(buf),"trailing request bytes");}finally{buf.release();}
        conventionalTags(h);
        System.out.println("FOODCRAFT AUDIT MALFORMED RECIPES PASS duplicates=true values=true truncation=true request_trailing=true");h.succeed();
    }
    private static void conventionalTags(GameTestHelper h){
        var registry=BuiltInRegistries.ITEM;
        var original=new java.util.HashMap<net.minecraft.tags.TagKey<net.minecraft.world.item.Item>,List<net.minecraft.core.Holder<net.minecraft.world.item.Item>>>();
        registry.getTags().forEach(pair->original.put(pair.getFirst(),pair.getSecond().stream().toList()));
        String[][] cases={{"forge:ingots/iron","c:iron_ingots","000_tiepian"},{"forge:ingots/gold","c:gold_ingots","002_caidao_hj"},{"forge:gems/diamond","c:diamonds","003_caidao_zs"},{"forge:gems/emerald","c:emeralds","004_caidao_lbs"}};
        List<String> failures=new ArrayList<>();
        try{
            for(var entry:cases){
                var tags=new java.util.HashMap<>(original);
                var key=net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM,new ResourceLocation(entry[1]));
                tags.put(key,List.of(registry.wrapAsHolder(Items.STICK)));registry.bindTags(tags);
                try(var reader=h.getLevel().getServer().getResourceManager().getResource(FoodCraft.id("recipes/crafting/"+entry[2]+".json")).orElseThrow().openAsReader()){
                    var json=JsonParser.parseReader(reader).getAsJsonObject();
                    var value=json.getAsJsonObject("key").entrySet().stream().map(java.util.Map.Entry::getValue).filter(v->v.toString().contains(entry[0])).findFirst().orElseThrow();
                    if(!Ingredient.fromJson(value).test(new ItemStack(Items.STICK)))failures.add("Standard Fabric-only ingredient was rejected: "+entry[1]);
                }catch(java.io.IOException error){throw new java.io.UncheckedIOException(error);}
            }
        }finally{registry.bindTags(original);}
        h.assertTrue(failures.isEmpty(),String.join("; ",failures));
        System.out.println("FOODCRAFT AUDIT CONVENTIONAL TAGS PASS cases=4 exclusively_c_tagged_material=true native_recipe_json=true");
    }
    public static void complexTransfers(GameTestHelper h){
        var machine=place(h,MachineKind.POT,new BlockPos(3,2,3));var player=h.makeMockPlayer();
        var menu=new MachineMenu(MachineKind.POT,1,player.getInventory(),machine,machine.data());player.getInventory().clearContent();
        List<MachineRecipe.Input> inputs=new ArrayList<>();
        for(int i=0;i<12;i++)inputs.add(new MachineRecipe.Input(i,Ingredient.of(Items.STICK),1));
        var recipe=new MachineRecipe(FoodCraft.id("audit_twelve"),MachineKind.POT,inputs,new int[]{},new ItemStack(Items.BREAD),100,0,0,1000,false,false,0);
        for(int group=0;group<12;group++){
            ItemStack stack=new ItemStack(Items.STICK,group==11?4:5);stack.getOrCreateTag().putInt("AuditGroup",group);player.getInventory().setItem(group,stack);
        }
        var plan=MachineTransfers.prepare(menu,recipe,true);
        h.assertTrue(plan.successful()&&plan.plan().batches==4,"Feasible maximum transfer rejected by overlapping/NBT search budget");
        h.assertTrue(plan.plan().commit(menu),"Complex transfer did not commit");
        int remainder=0;for(int i=0;i<36;i++)remainder+=player.getInventory().getItem(i).getCount();
        for(int slot:machine.kind.inputs)h.assertTrue(machine.getItem(slot).getCount()==4,"Complex transfer quantity changed");
        h.assertTrue(remainder==11,"Complex transfer conservation changed "+remainder);
        for(boolean mixed:new boolean[]{false,true}){
            machine.clearContent();player.getInventory().clearContent();inputs=new ArrayList<>();
            for(int i=0;i<12;i++)inputs.add(new MachineRecipe.Input(i,Ingredient.of(Items.STICK),mixed&&i==0?1:6));
            var fragmented=new MachineRecipe(FoodCraft.id("audit_fragmented"),MachineKind.POT,inputs,new int[]{},new ItemStack(Items.BREAD),100,0,0,1000,false,false,0);
            for(int group=0;group<10;group++){
                var stack=new ItemStack(Items.STICK,17);stack.getOrCreateTag().putInt("AuditGroup",group);player.getInventory().setItem(group,stack);
            }
            var fragmentedPlan=MachineTransfers.prepare(menu,fragmented,true);
            h.assertTrue(fragmentedPlan.successful()&&fragmentedPlan.plan().batches==1,"Fragmented stock exhausted search budget mixed="+mixed);
            h.assertTrue(fragmentedPlan.plan().commit(menu),"Fragmented allocation failed to commit");
            int total=0;for(int i=0;i<36;i++)total+=player.getInventory().getItem(i).getCount();
            for(var input:fragmented.inputs){h.assertTrue(machine.getItem(input.slot()).getCount()==input.count(),"Fragmented slot count changed");total+=machine.getItem(input.slot()).getCount();}
            h.assertTrue(total==170,"Fragmented transfer violated conservation");
        }
        System.out.println("FOODCRAFT AUDIT FRAGMENTED TRANSFER PASS cases=2 stock=170 nbt_groups=10 slots=12 maximum_batches=1");
        System.out.println("FOODCRAFT AUDIT COMPLEX TRANSFER PASS slots=12 nbt_groups=12 stock=59 maximum_batches=4 remainder=11");h.succeed();
    }
    public static void destruction(GameTestHelper h){
        var level=h.getLevel();var player=h.makeMockPlayer();var local=new BlockPos(3,2,3);var pos=h.absolutePos(local);var box=new AABB(pos).inflate(2);int cases=0;
        for(var kind:MachineKind.values())for(boolean creative:new boolean[]{false,true}){
            for(var entity:level.getEntitiesOfClass(ItemEntity.class,box))entity.discard();
            var machine=place(h,kind,local);machine.setItem(0,new ItemStack(Items.STICK,7));player.getAbilities().instabuild=creative;
            player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(FoodCraft.item("wrench")));
            var result=player.getMainHandItem().getItem().useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false)));
            h.assertTrue(result.consumesAction()&&level.getBlockState(pos).isAir(),"Wrench did not remove "+kind);
            var drops=level.getEntitiesOfClass(ItemEntity.class,box);int sticks=0,machines=0,saved=0;
            for(var entity:drops){var item=entity.getItem();if(item.is(Items.STICK))sticks+=item.getCount();if(item.is(FoodCraft.item(kind.id))){machines+=item.getCount();saved+=item.getTag().getCompound("BlockEntityTag").getList("Items",10).getCompound(0).getByte("Count");}}
            h.assertTrue(creative?sticks==7&&machines==0:sticks==0&&machines==1&&saved==7,"Wrench inventory duplicated/lost "+kind+" creative="+creative);cases++;
        }
        for(var kind:MachineKind.values()){
            for(var entity:level.getEntitiesOfClass(ItemEntity.class,box))entity.discard();
            var machine=place(h,kind,local);machine.setItem(0,new ItemStack(Items.STICK,7));
            for(Direction direction:Direction.values())h.assertTrue(!PistonBaseBlock.isPushable(machine.getBlockState(),level,pos,direction,false,direction),"Machine can be moved by piston "+kind);
            // Use vanilla explosion finalization with a controlled affected-block list.
            var explosion=new Explosion(level,null,pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+0.5,4,false,Explosion.BlockInteraction.DESTROY,List.of(pos));explosion.finalizeExplosion(false);
            h.assertTrue(level.getBlockState(pos).isAir(),"Explosion did not remove "+kind);
            int sticks=level.getEntitiesOfClass(ItemEntity.class,box).stream().filter(e->e.getItem().is(Items.STICK)).mapToInt(e->e.getItem().getCount()).sum();
            h.assertTrue(sticks==7,"Explosion inventory duplicated/lost "+kind+" actual="+sticks);cases++;
        }
        System.out.println("FOODCRAFT AUDIT DESTRUCTION PASS cases="+cases+" filled_creative_wrench=true explosion_finalize=true piston_pushability=true");h.succeed();
    }
    public static void nativePistons(GameTestHelper h){pistonStep(h,0);}
    private static void pistonStep(GameTestHelper h,int step){
        if(step==18){System.out.println("FOODCRAFT AUDIT NATIVE PISTON PASS push=9 pull=9 inventory=7");h.succeed();return;}
        var level=h.getLevel();var kind=MachineKind.values()[step%9];var local=new BlockPos(3,2,3);var pos=h.absolutePos(local);
        for(int x=1;x<=3;x++)level.setBlock(pos.west(x),Blocks.AIR.defaultBlockState(),3);
        var machine=place(h,kind,local);machine.setItem(0,new ItemStack(Items.BARRIER,7));
        for(var entity:level.getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(2)))entity.discard();
        if(step<9){
            level.setBlock(pos.west(),Blocks.PISTON.defaultBlockState().setValue(net.minecraft.world.level.block.piston.PistonBaseBlock.FACING,Direction.EAST),3);
            level.setBlock(pos.west(2),Blocks.REDSTONE_BLOCK.defaultBlockState(),3);
        }else{
            level.setBlock(pos.west(3),Blocks.REDSTONE_BLOCK.defaultBlockState(),3);
            level.setBlock(pos.west(2),Blocks.STICKY_PISTON.defaultBlockState().setValue(net.minecraft.world.level.block.piston.PistonBaseBlock.FACING,Direction.EAST).setValue(net.minecraft.world.level.block.piston.PistonBaseBlock.EXTENDED,true),3);
            level.setBlock(pos.west(),Blocks.PISTON_HEAD.defaultBlockState().setValue(net.minecraft.world.level.block.piston.PistonHeadBlock.FACING,Direction.EAST).setValue(net.minecraft.world.level.block.piston.PistonHeadBlock.TYPE,net.minecraft.world.level.block.state.properties.PistonType.STICKY),3);
            level.setBlock(pos.west(3),Blocks.AIR.defaultBlockState(),3);
        }
        h.runAfterDelay(5,()->{
            h.assertTrue(level.getBlockEntity(pos)==machine&&machine.getItem(0).getCount()==7,"Actual piston moved/damaged "+kind+" step="+step);
            h.assertTrue(level.getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(2)).isEmpty(),"Actual piston released machine inventory");
            var base=level.getBlockState(pos.west(step<9?1:2));
            h.assertTrue(base.is(step<9?Blocks.PISTON:Blocks.STICKY_PISTON)&&!base.getValue(net.minecraft.world.level.block.piston.PistonBaseBlock.EXTENDED),"Piston attempt did not end in the expected state");
            if(step<9)h.assertTrue(level.hasNeighborSignal(pos.west()),"Push attempt was not actually powered");
            pistonStep(h,step+1);
        });
    }
    public static void cakeEdges(GameTestHelper h){
        var level=h.getLevel();var a=h.makeMockPlayer();var b=h.makeMockPlayer();var pos=h.absolutePos(new BlockPos(3,2,3));int cases=0;
        level.setBlock(pos.below(),Blocks.STONE.defaultBlockState(),3);
        for(var entry:Catalog.ENTRIES)if(entry.kind().equals("cake_item")){
            var block=FoodCraft.BLOCKS.get(new ResourceLocation(entry.crop()).getPath());
            var hit=new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false);
            level.setBlock(pos,block.defaultBlockState(),3);a.getFoodData().setFoodLevel(20);
            block.use(level.getBlockState(pos),level,pos,a,InteractionHand.MAIN_HAND,hit);
            h.assertTrue(level.getBlockState(pos).getValue(net.minecraft.world.level.block.CakeBlock.BITES)==0&&a.getFoodData().getFoodLevel()==20,"Full player ate cake "+entry.id());
            for(int bite=0;bite<7;bite++){
                a.getFoodData().setFoodLevel(10);var state=level.getBlockState(pos);
                state.getBlock().use(state,level,pos,a,InteractionHand.OFF_HAND,hit);
                h.assertTrue(a.getFoodData().getFoodLevel()==12,"Cake bite nutrition changed");
                h.assertTrue(bite==6?level.getBlockState(pos).isAir():level.getBlockState(pos).getValue(net.minecraft.world.level.block.CakeBlock.BITES)==bite+1,"Cake final bite/count changed "+entry.id());
                cases++;
            }
            var last=block.defaultBlockState().setValue(net.minecraft.world.level.block.CakeBlock.BITES,6);level.setBlock(pos,last,3);
            a.getFoodData().setFoodLevel(10);b.getFoodData().setFoodLevel(10);
            for(var player:List.of(a,b)){var state=level.getBlockState(pos);state.getBlock().use(state,level,pos,player,InteractionHand.MAIN_HAND,hit);}
            block.randomTick(last,level,pos,level.random);
            h.assertTrue(a.getFoodData().getFoodLevel()==12&&b.getFoodData().getFoodLevel()==10&&level.getBlockState(pos).isAir(),"Last bite was duplicated/regenerated "+entry.id());
            cases++;
        }
        System.out.println("FOODCRAFT AUDIT CAKE EDGES PASS cases="+cases+" full_hunger=true final_bite=true same_tick_two_players=true no_resurrection=true");h.succeed();
    }
    public static void malformedPersistence(GameTestHelper h){
        var random=new java.util.Random(0xFC4A04L);int cases=0;var local=new BlockPos(3,2,3);
        for(var kind:MachineKind.values()){
            var machine=place(h,kind,local);
            for(int trial=0;trial<500;trial++){
                var tag=new net.minecraft.nbt.CompoundTag();
                for(String key:List.of("Progress","TotalTime","BurnTime","InitialBurnTime","ColdTime","Liquid","LiquidFraction","Proficiency","FirePower","AccumulatedHeat"))
                    tag.putInt(key,trial<4?new int[]{Integer.MIN_VALUE,-1,0,Integer.MAX_VALUE}[trial]:random.nextInt());
                tag.putInt("DataVersion",trial%5);tag.putString("ActiveRecipe",trial%2==0?"bad uppercase !!!":"foodcraft:missing");tag.putBoolean("Milk",true);
                machine.load(tag);
                h.assertTrue(machine.progress>=0&&machine.totalTime>=0&&machine.burnTime>=0&&machine.coldTime>=0&&machine.accumulatedHeat>=0,"Negative persistent state");
                h.assertTrue(machine.proficiency>=0&&machine.proficiency<=3000&&machine.firePower>=0&&machine.firePower<=100&&machine.liquidVolume()<=648000,"Persistent range exceeded");
                var saved=machine.saveWithoutMetadata();machine.load(saved);
                h.assertTrue(saved.equals(machine.saveWithoutMetadata()),"Sanitized persistence is not stable kind="+kind+" trial="+trial+" first="+saved+" second="+machine.saveWithoutMetadata());
                tick(machine,1);h.assertTrue(machine.progress>=0&&machine.accumulatedHeat>=0,"Invalid persistent state overflowed after tick");cases++;
            }
        }
        var board=place(h,MachineKind.CUTTING_BOARD,local);
        board.setItem(0,new ItemStack(FoodCraft.item("caidao")));board.getItem(0).setDamageValue(Integer.MAX_VALUE);
        board.setItem(1,new ItemStack(Items.COOKED_CHICKEN));tick(board,1);
        h.assertTrue(board.getItem(0).isEmpty(),"Corrupt knife damage overflow renewed the tool");
        System.out.println("FOODCRAFT AUDIT NBT PASS cases="+cases+" unknown_versions=true stable_sanitize=true corrupt_knife=true");h.succeed();
    }
    public static void numericProcessing(GameTestHelper h){
        var level=h.getLevel();var manager=level.getRecipeManager();var original=List.copyOf(manager.getRecipes());
        var local=new BlockPos(3,3,3);var machine=place(h,MachineKind.FRYING_PAN,local);var stove=place(h,MachineKind.STOVE,local.below());stove.burnTime=1000000;
        var recipe=new MachineRecipe(FoodCraft.id("audit_numeric"),MachineKind.FRYING_PAN,List.of(new MachineRecipe.Input(0,Ingredient.of(Items.STICK),1)),new int[]{},new ItemStack(Items.BREAD),100,0,10,20,false,false,0);
        List<String> failures=new ArrayList<>();
        try{
            manager.replaceRecipes(List.of(recipe));machine.setItem(0,new ItemStack(Items.STICK));
            var tag=machine.saveWithoutMetadata();tag.putInt("Progress",16);tag.putInt("TotalTime",100);tag.putInt("AccumulatedHeat",Integer.MAX_VALUE);tag.putInt("FirePower",100);
            tag.putString("ActiveRecipe",recipe.getId().toString());tag.putString("ActiveRecipeFingerprint",recipe.fingerprint);machine.load(tag);tick(machine,1);
            if(!machine.getItem(2).is(Items.COAL)||!machine.getItem(0).isEmpty())failures.add("Extreme saved heat overflowed instead of producing burnt output");
            machine.clearContent();machine.setItem(0,new ItemStack(Items.STICK));machine.setItem(2,new ItemStack(Items.COAL,64));
            tag=machine.saveWithoutMetadata();tag.putInt("Progress",Integer.MAX_VALUE);tag.putInt("AccumulatedHeat",30);tag.putInt("FirePower",0);tag.putString("ActiveRecipe",recipe.getId().toString());tag.putString("ActiveRecipeFingerprint",recipe.fingerprint);machine.load(tag);tick(machine,20);
            if(machine.progress<0||machine.getItem(0).getCount()!=1)failures.add("Blocked failure overflowed progress or consumed ingredients");
            machine.accumulatedHeat=100000;
            var packet=new net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket(1,8,machine.data().get(8));
            var buf=new FriendlyByteBuf(Unpooled.buffer());try{packet.write(buf);var decoded=new net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket(buf);if(decoded.getValue()<0)failures.add("Heat gauge wrapped through signed short");}finally{buf.release();}
            var drink=place(h,MachineKind.DRINK_MAKER,new BlockPos(6,3,3));drink.coldTime=100000;
            packet=new net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket(1,9,drink.data().get(9));
            buf=new FriendlyByteBuf(Unpooled.buffer());try{packet.write(buf);var decoded=new net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket(buf);if(decoded.getValue()!=2500)failures.add("Extreme saved cooldown wrapped the cold gauge instead of saturating its visual scale");}finally{buf.release();}
        }finally{manager.replaceRecipes(original);}
        h.assertTrue(failures.isEmpty(),String.join("; ",failures));
        System.out.println("FOODCRAFT AUDIT NUMERIC PROCESSING PASS extreme_heat=true progress_saturation=true gauge_short=true cold_gauge=true");h.succeed();
    }
}
