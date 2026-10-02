package org.foodcraft.test;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import org.foodcraft.Catalog;
import org.foodcraft.FoodCraft;
import org.foodcraft.machine.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Only registered with the explicit isolated-QA flag, never in normal installations. */
public final class AuditServerFixture {
    public static final List<Catalog.Entry> FOODS=Catalog.ENTRIES.stream().filter(e->e.nutrition()>0&&e.id().startsWith("foodcraft:")).toList();
    private record Meal(int number,InteractionHand hand,boolean cancel){}
    private record Life(int number,String owner,BlockPos pos,boolean previousKeep){}
    private static final Map<UUID,Meal> meals=new java.util.HashMap<>();
    private static final Map<UUID,Life> lives=new java.util.HashMap<>();
    private static final java.util.Set<UUID> negativePrepared=new java.util.HashSet<>();
    private AuditServerFixture(){}
    public static void commands(CommandDispatcher<CommandSourceStack> dispatcher){
        if(!Boolean.getBoolean("foodcraft.qa.server"))return;
        UseLifecycleFixture.commands(dispatcher);
        FailurePersistenceFixture.commands(dispatcher);
        dispatcher.register(Commands.literal("foodcraftaudit").requires(s->s.hasPermission(2))
            .then(Commands.literal("load_prepare").executes(c->{LoadStressFixture.prepare(c.getSource().getLevel());return 1;}))
            .then(Commands.literal("load_checkpoint").executes(c->{LoadStressFixture.checkpoint(c.getSource().getLevel());return 1;}))
            .then(Commands.literal("load_resume").executes(c->{LoadStressFixture.resume(c.getSource().getLevel());return 1;}))
            .then(Commands.literal("negative_prepare").executes(c->{negativePrepare(c.getSource().getPlayerOrException());return 1;}))
            .then(Commands.literal("negative_assert").executes(c->{negativeAssert(c.getSource().getPlayerOrException());return 1;}))
            .then(Commands.literal("negative_control").executes(c->{negativeControl(c.getSource().getPlayerOrException());return 1;}))
            .then(Commands.literal("tool_prepare").then(Commands.argument("case",IntegerArgumentType.integer(0,15)).executes(c->{toolPrepare(c.getSource().getPlayerOrException(),IntegerArgumentType.getInteger(c,"case"));return 1;})))
            .then(Commands.literal("tool_assert").then(Commands.argument("case",IntegerArgumentType.integer(0,15)).executes(c->{toolAssert(c.getSource().getPlayerOrException(),IntegerArgumentType.getInteger(c,"case"));return 1;})))
            .then(Commands.literal("food_prepare").then(Commands.argument("case",IntegerArgumentType.integer(0,FOODS.size()*4-1)).executes(c->{mealPrepare(c.getSource().getPlayerOrException(),IntegerArgumentType.getInteger(c,"case"));return 1;})))
            .then(Commands.literal("food_assert").then(Commands.argument("case",IntegerArgumentType.integer(0,FOODS.size()*4-1)).executes(c->{mealAssert(c.getSource().getPlayerOrException(),IntegerArgumentType.getInteger(c,"case"));return 1;})))
            .then(Commands.literal("life_prepare").then(Commands.argument("case",IntegerArgumentType.integer(0,5)).executes(c->{lifePrepare(c.getSource().getPlayerOrException(),IntegerArgumentType.getInteger(c,"case"));return 1;})))
            .then(Commands.literal("life_carried").then(Commands.argument("case",IntegerArgumentType.integer(0,5)).executes(c->{lifeCarried(c.getSource().getPlayerOrException(),IntegerArgumentType.getInteger(c,"case"));return 1;})))
            .then(Commands.literal("life_act").then(Commands.argument("case",IntegerArgumentType.integer(0,5)).executes(c->{lifeAct(c.getSource().getPlayerOrException(),IntegerArgumentType.getInteger(c,"case"));return 1;})))
            .then(Commands.literal("life_check").then(Commands.argument("case",IntegerArgumentType.integer(0,5)).executes(c->{lifeCheck(c.getSource().getPlayerOrException(),IntegerArgumentType.getInteger(c,"case"));return 1;})))
            .then(Commands.literal("gui").then(Commands.argument("case",IntegerArgumentType.integer(0,8)).executes(c->{gui(c.getSource().getPlayerOrException(),IntegerArgumentType.getInteger(c,"case"));return 1;}))));
    }
    private static void ack(ServerPlayer p,String text){String line="FOODCRAFT AUDIT "+text;System.out.println(line+" player="+p.getScoreboardName());p.sendSystemMessage(Component.literal(line));}
    private static void require(ServerPlayer p,boolean condition,String text){if(!condition){ack(p,"FAIL "+text);throw new IllegalStateException(text);}}
    private static void mealPrepare(ServerPlayer p,int number){
        p.closeContainer();p.stopUsingItem();p.setGameMode(GameType.SURVIVAL);p.getInventory().clearContent();
        p.clearFire();p.removeAllEffects();p.setHealth(20);p.getFoodData().setFoodLevel(0);p.getFoodData().setSaturation(0);p.getFoodData().setExhaustion(0);
        var hand=number%4<2?InteractionHand.MAIN_HAND:InteractionHand.OFF_HAND;
        if(FOODS.get(number/4).effectName().equals("milk")){
            p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.POISON,400,1));
            p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED,400,1));
        }
        p.setItemInHand(hand,new ItemStack(FoodCraft.item(FOODS.get(number/4).id()),2));meals.put(p.getUUID(),new Meal(number,hand,number%2==1));
        p.inventoryMenu.broadcastChanges();ack(p,"FOOD_READY "+number);
    }
    private static void mealAssert(ServerPlayer p,int number){
        var meal=meals.get(p.getUUID());require(p,meal!=null&&meal.number==number,"Wrong meal assertion");var entry=FOODS.get(number/4);
        require(p,p.getItemInHand(meal.hand).getCount()==(meal.cancel?2:1),"Food consumed wrong count "+number+" actual="+p.getItemInHand(meal.hand).getCount()+" using="+p.isUsingItem()+" remaining="+p.getUseItemRemainingTicks());
        require(p,p.getFoodData().getFoodLevel()==(meal.cancel?0:Math.min(20,entry.nutrition())),"Food nutrition or cancellation changed "+number);
        if(meal.cancel){
            require(p,p.getActiveEffects().size()==(entry.effectName().equals("milk")?2:0)&&!p.isOnFire(),"Cancelled food changed effects "+number);
        }else{
            int expected=switch(entry.effectName()){case "milk","chili","none"->0;case "staple"->1;case "wine"->4;case "gold_grape_wine","gold_apple_wine"->6;default->3;};
            require(p,p.getActiveEffects().size()==expected,"Native food effect count changed "+number);
            int duration=switch(entry.effectName()){case "gold_cookie"->1200;case "gold_grape","gold_apple"->36000;case "gold_grape_wine","gold_apple_wine"->3600;default->600;};
            int amplifier=switch(entry.effectName()){case "staple","gold_cookie"->1;case "wine"->3;default->4;};
            for(var effect:p.getActiveEffects())require(p,effect.getAmplifier()==amplifier&&effect.getDuration()>duration-200&&effect.getDuration()<=duration,"Native food effect duration/amplifier changed "+number);
        }
        require(p,!p.isUsingItem(),"Food use did not finish/stop "+number);meals.remove(p.getUUID());ack(p,"FOOD_PASS "+number);
    }
    private static ItemStack owned(net.minecraft.world.item.Item item,int count,String owner){var stack=new ItemStack(item,count);stack.getOrCreateTag().putString("FoodCraftAuditOwner",owner);return stack;}
    private static int count(ItemStack stack,String owner){return stack.hasTag()&&owner.equals(stack.getTag().getString("FoodCraftAuditOwner"))?stack.getCount():0;}
    private static void lifePrepare(ServerPlayer p,int number){
        p.closeContainer();p.setGameMode(GameType.SURVIVAL);p.clearFire();p.removeAllEffects();p.setHealth(20);p.getInventory().clearContent();
        var level=p.getServer().overworld();if(p.serverLevel()!=level)p.teleportTo(level,16.5,71,16.5,0,0);
        var pos=new BlockPos(8000+number*4,70,8000);level.setChunkForced(pos.getX()>>4,pos.getZ()>>4,true);
        level.setBlock(pos.below(),Blocks.STONE.defaultBlockState(),3);
        for(int x=-12;x<=12;x++)for(int z=-12;z<=12;z++)level.setBlock(pos.offset(x,0,z),Blocks.STONE.defaultBlockState(),3);
        var kind=MachineKind.values()[number];level.setBlock(pos,FoodCraft.BLOCKS.get(kind.id).defaultBlockState(),3);
        String owner=p.getUUID()+":"+number;var machine=(MachineBlockEntity)level.getBlockEntity(pos);machine.setItem(0,owned(Items.BARRIER,7,owner));
        p.teleportTo(level,pos.getX()+0.5,71,pos.getZ()+1.5,0,0);
        p.getInventory().setItem(9,owned(Items.COBBLESTONE,48,owner));
        lives.put(p.getUUID(),new Life(number,owner,pos,level.getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)));
        p.openMenu(machine);p.containerMenu.broadcastChanges();ack(p,"LIFE_READY "+number+" "+p.containerMenu.containerId);
    }
    private static void lifeCarried(ServerPlayer p,int number){
        var life=lives.get(p.getUUID());require(p,life!=null&&life.number==number,"Wrong life scenario");
        require(p,p.containerMenu instanceof MachineMenu&&count(p.containerMenu.getCarried(),life.owner)==48,"Native carried stack missing");
        if(number%2==1)for(int slot=0;slot<36;slot++)p.getInventory().setItem(slot,new ItemStack(Items.STONE,64));
        p.containerMenu.broadcastChanges();ack(p,"LIFE_CARRIED "+number+" 48");
    }
    private static void lifeAct(ServerPlayer p,int number){
        var life=lives.get(p.getUUID());require(p,life!=null&&life.number==number,"Wrong life transition");
        if(number<4){
            p.serverLevel().getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(number>=2,p.getServer());
            require(p,p.hurt(p.damageSources().genericKill(),Float.MAX_VALUE)&&!p.isAlive(),"Native death did not occur");
        }else{
            var destination=p.getServer().getLevel(number==4?Level.NETHER:Level.END);
            destination.setBlock(new BlockPos(16,80,16),Blocks.STONE.defaultBlockState(),3);
            p.teleportTo(destination,16.5,81,16.5,0,0);ack(p,"LIFE_MOVED "+number);
        }
    }
    private static void lifeCheck(ServerPlayer p,int number){
        var life=lives.get(p.getUUID());require(p,life!=null&&life.number==number&&p.isAlive(),"Life did not resume");
        int total=count(p.containerMenu.getCarried(),life.owner);
        for(int i=0;i<p.getInventory().getContainerSize();i++)total+=count(p.getInventory().getItem(i),life.owner);
        var world=p.getServer().overworld();var machine=(MachineBlockEntity)world.getBlockEntity(life.pos);
        for(int i=0;i<machine.getContainerSize();i++)total+=count(machine.getItem(i),life.owner);
        for(var dimension:p.getServer().getAllLevels())for(var entity:dimension.getAllEntities())if(entity instanceof ItemEntity item)total+=count(item.getItem(),life.owner);
        require(p,total==55,"Death/dimension conservation failed "+number+" actual="+total);
        require(p,number<4?p.serverLevel()==world:p.serverLevel()==p.getServer().getLevel(number==4?Level.NETHER:Level.END),"Wrong native dimension");
        world.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(life.previousKeep,p.getServer());
        p.teleportTo(world,16.5,71,16.5,0,0);lives.remove(p.getUUID());ack(p,"LIFE_PASS "+number+" total=55");
    }
    private static void gui(ServerPlayer p,int number){
        p.closeContainer();var kind=MachineKind.values()[number];var level=p.serverLevel();var pos=ClientTestFixture.machinePos(kind);
        level.setBlock(pos,FoodCraft.BLOCKS.get(kind.id).defaultBlockState(),3);
        p.teleportTo(level,pos.getX()+0.5,pos.getY()+1,pos.getZ()+1.5,0,0);p.openMenu((MachineBlockEntity)level.getBlockEntity(pos));
        ack(p,"GUI_READY "+number+" "+p.containerMenu.containerId);
    }
    private static void negativePrepare(ServerPlayer p){
        p.closeContainer();var level=p.serverLevel();var pos=new BlockPos(24,70,24);
        var recipe=level.getRecipeManager().getAllRecipesFor(FoodCraft.RECIPE_TYPES.get(MachineKind.MILLING_MACHINE)).stream().sorted(java.util.Comparator.comparing(r->r.getId().toString())).findFirst().orElseThrow();
        if(negativePrepared.add(p.getUUID())){
            p.getInventory().clearContent();p.getInventory().setItem(9,owned(recipe.inputs.get(0).ingredient().getItems()[0].getItem(),16,p.getUUID()+":negative"));
            level.setBlock(pos,FoodCraft.BLOCKS.get("milling_machine").defaultBlockState(),3);
            level.setBlock(pos.south(),Blocks.STONE.defaultBlockState(),3);
        }
        negativeAssert(p);p.teleportTo(level,24.5,71,25.5,0,0);p.openMenu((MachineBlockEntity)level.getBlockEntity(pos));p.containerMenu.broadcastChanges();
        ack(p,"NEGATIVE_READY "+p.containerMenu.containerId+" "+recipe.getId());
    }
    private static void negativeAssert(ServerPlayer p){
        int total=0;for(int i=0;i<p.getInventory().getContainerSize();i++)total+=count(p.getInventory().getItem(i),p.getUUID()+":negative");
        var m=(MachineBlockEntity)p.serverLevel().getBlockEntity(new BlockPos(24,70,24));
        require(p,total==16&&m!=null&&m.isEmpty(),"Bad packet mutated source inventory or machine");
        ack(p,"NEGATIVE_PASS stock=16");
    }
    private static void negativeControl(ServerPlayer p){
        var m=(MachineBlockEntity)p.serverLevel().getBlockEntity(new BlockPos(24,70,24));int total=0;
        for(int i=0;i<p.getInventory().getContainerSize();i++)total+=count(p.getInventory().getItem(i),p.getUUID()+":negative");
        require(p,total==15&&count(m.getItem(0),p.getUUID()+":negative")==1,"Valid network transfer control failed");
        var returned=m.removeItem(0,1);require(p,p.getInventory().add(returned),"Control ingredient could not be returned");
        p.containerMenu.broadcastChanges();ack(p,"NEGATIVE_CONTROL_PASS stock=16");
    }
    private static void toolPrepare(ServerPlayer p,int number){
        p.closeContainer();p.stopUsingItem();p.removeAllEffects();p.clearFire();p.setGameMode(number/4%2==1?GameType.CREATIVE:GameType.SURVIVAL);p.getInventory().clearContent();
        p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(40);p.setHealth(30);
        p.getFoodData().setFoodLevel(10);p.getFoodData().setSaturation(0);p.getFoodData().setExhaustion(0);
        var pos=new BlockPos(26,70,26);var level=p.serverLevel();level.setBlock(pos,Blocks.STONE.defaultBlockState(),3);level.setBlock(pos.south(),Blocks.STONE.defaultBlockState(),3);level.setBlock(pos.above(),Blocks.AIR.defaultBlockState(),3);
        p.teleportTo(level,26.5,71,27.5,0,24);
        var entries=Catalog.ENTRIES.stream().filter(e->e.kind().equals("multitool")).toList();var stack=new ItemStack(FoodCraft.item(entries.get(number%2).id()));
        stack.setDamageValue(number/8==0?0:1995);stack.enchant(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING,3);
        p.setItemInHand(number/2%2==0?InteractionHand.MAIN_HAND:InteractionHand.OFF_HAND,stack);p.inventoryMenu.broadcastChanges();ack(p,"TOOL_READY "+number);
    }
    private static void toolAssert(ServerPlayer p,int number){
        var entries=Catalog.ENTRIES.stream().filter(e->e.kind().equals("multitool")).toList();boolean heal=entries.get(number%2).path().contains("anbi"),creative=number/4%2==1;
        var stack=p.getItemInHand(number/2%2==0?InteractionHand.MAIN_HAND:InteractionHand.OFF_HAND);int before=number/8==0?0:1995;
        require(p,creative?!stack.isEmpty()&&stack.getDamageValue()==before:before==1995?stack.isEmpty():!stack.isEmpty()&&stack.getDamageValue()==5,"Native tool wear or last-use break changed "+number);
        require(p,p.getHealth()==30,"Native healing reduced enhanced health "+number);
        if(!heal)require(p,p.serverLevel().getBlockState(new BlockPos(26,71,26)).is(Blocks.TORCH),"Native torch placement failed "+number);
        ack(p,"TOOL_PASS "+number);
    }
}
