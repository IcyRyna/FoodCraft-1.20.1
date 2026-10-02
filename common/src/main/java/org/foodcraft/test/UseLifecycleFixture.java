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
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.foodcraft.Catalog;
import org.foodcraft.FoodCraft;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Opt-in QA only: interrupt a genuinely active native food use, then count its persisted/drop ledger. */
public final class UseLifecycleFixture {
    public static final List<Catalog.Entry> FOODS;
    static {
        var groups=new LinkedHashSet<String>();var foods=new ArrayList<Catalog.Entry>();
        for(var food:AuditServerFixture.FOODS)if(groups.add(food.effectName()))foods.add(food);
        FOODS=List.copyOf(foods);
    }
    private record State(int number,String owner,boolean keepBefore,boolean active){}
    private static final Map<UUID,State> states=new java.util.HashMap<>();
    private UseLifecycleFixture(){}
    public static void commands(CommandDispatcher<CommandSourceStack> dispatcher){
        if(!Boolean.getBoolean("foodcraft.qa.server"))return;
        dispatcher.register(Commands.literal("foodcraftuseaudit").requires(s->s.hasPermission(2))
            .then(Commands.literal("prepare").then(Commands.argument("case",IntegerArgumentType.integer(0,FOODS.size()*6-1)).executes(c->{prepare(c.getSource().getPlayerOrException(),IntegerArgumentType.getInteger(c,"case"));return 1;})))
            .then(Commands.literal("interrupt").then(Commands.argument("case",IntegerArgumentType.integer(0,FOODS.size()*6-1)).executes(c->{interrupt(c.getSource().getPlayerOrException(),IntegerArgumentType.getInteger(c,"case"));return 1;})))
            .then(Commands.literal("check").then(Commands.argument("case",IntegerArgumentType.integer(0,FOODS.size()*6-1)).executes(c->{check(c.getSource().getPlayerOrException(),IntegerArgumentType.getInteger(c,"case"));return 1;}))));
    }
    private static InteractionHand hand(int number){return number/3%2==0?InteractionHand.MAIN_HAND:InteractionHand.OFF_HAND;}
    private static void ack(ServerPlayer p,String message){String line="FOODCRAFT USE AUDIT "+message;System.out.println(line+" player="+p.getScoreboardName());p.sendSystemMessage(Component.literal(line));}
    private static void require(ServerPlayer p,boolean ok,String reason){if(!ok){ack(p,"FAIL "+reason);throw new IllegalStateException(reason);}}
    private static void prepare(ServerPlayer p,int number){
        p.closeContainer();p.stopUsingItem();p.setGameMode(GameType.SURVIVAL);p.getInventory().clearContent();p.getInventory().selected=0;
        p.clearFire();p.removeAllEffects();p.setHealth(20);p.getFoodData().setFoodLevel(0);p.getFoodData().setSaturation(0);p.getFoodData().setExhaustion(0);
        var level=p.getServer().overworld();var pos=new BlockPos(14000,70,14000);
        for(int x=-12;x<=12;x++)for(int z=-12;z<=12;z++)level.setBlock(pos.offset(x,0,z),Blocks.STONE.defaultBlockState(),3);
        level.setChunkForced(pos.getX()>>4,pos.getZ()>>4,true);
        p.setRespawnPosition(Level.OVERWORLD,pos.above(),0,true,false);p.teleportTo(level,pos.getX()+0.5,71,pos.getZ()+0.5,0,0);
        var entry=FOODS.get(number/6);String owner=p.getUUID()+":food-use:"+number;
        var stack=new ItemStack(FoodCraft.item(entry.id()),2);stack.getOrCreateTag().putString("FoodCraftUseOwner",owner);p.setItemInHand(hand(number),stack);
        if(entry.effectName().equals("milk")){
            p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.POISON,1000,1));
            p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED,1000,1));
        }
        states.put(p.getUUID(),new State(number,owner,level.getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY),false));
        p.inventoryMenu.broadcastChanges();ack(p,"READY "+number);
    }
    private static void interrupt(ServerPlayer p,int number){
        var state=states.get(p.getUUID());require(p,state!=null&&state.number==number&&!state.active,"Wrong use interrupt state");
        require(p,p.isUsingItem()&&p.getUsedItemHand()==hand(number)&&p.getUseItemRemainingTicks()>=12,"Food was not actively using before interruption");
        require(p,p.getItemInHand(hand(number)).getCount()==2,"Food was consumed before intended interruption");
        states.put(p.getUUID(),new State(number,state.owner,state.keepBefore,true));
        System.out.println("FOODCRAFT USE AUDIT ACTIVE case="+number+" remaining_ticks="+p.getUseItemRemainingTicks()+" count=2");
        int kind=number%3;
        if(kind==2){p.connection.disconnect(Component.literal("FoodCraft QA active food interruption "+number));return;}
        p.getServer().overworld().getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(kind==1,p.getServer());
        p.hurt(p.damageSources().genericKill(),Float.MAX_VALUE);require(p,p.isDeadOrDying(),"Native food-use death did not occur");
    }
    private static int count(ItemStack stack,String owner){return stack.hasTag()&&owner.equals(stack.getTag().getString("FoodCraftUseOwner"))?stack.getCount():0;}
    private static void check(ServerPlayer p,int number){
        var state=states.get(p.getUUID());require(p,state!=null&&state.number==number&&state.active&&p.isAlive(),"Food-use interruption did not resume");
        int total=0;for(int slot=0;slot<p.getInventory().getContainerSize();slot++)total+=count(p.getInventory().getItem(slot),state.owner);
        total+=count(p.containerMenu.getCarried(),state.owner);
        for(var dimension:p.getServer().getAllLevels())for(var entity:dimension.getAllEntities())if(entity instanceof ItemEntity item)total+=count(item.getItem(),state.owner);
        require(p,total==2&&!p.isUsingItem(),"Interrupted food changed its ledger/use flag case="+number+" count="+total);
        int effects=number%3==2&&FOODS.get(number/6).effectName().equals("milk")?2:0;
        require(p,p.getActiveEffects().size()==effects&&!p.isOnFire(),"Interrupted food applied/cured effects case="+number);
        if(number%3==2)require(p,p.getFoodData().getFoodLevel()==0,"Disconnected food changed nutrition");
        p.getServer().overworld().getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(state.keepBefore,p.getServer());
        states.remove(p.getUUID());ack(p,"PASS "+number+" count=2 effects="+effects);
    }
}
