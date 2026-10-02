package org.foodcraft.test;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Native hotbar swaps during use, and native tool-use packets in both hands. */
public final class ItemEdgeVerification {
    private static final List<Integer> REPRESENTATIVES=new ArrayList<>();
    static{var seen=new LinkedHashSet<String>();for(int i=0;i<AuditServerFixture.FOODS.size();i++)if(seen.add(AuditServerFixture.FOODS.get(i).effectName()))REPRESENTATIVES.add(i);}
    private static int test,phase,ticks,tool;
    private static String reply="";
    private ItemEdgeVerification(){}
    public static void message(String text){if(text.startsWith("FOODCRAFT AUDIT "))reply=text.substring(16);}
    public static boolean tick(Minecraft mc){
        ticks++;if(reply.startsWith("FAIL"))throw new IllegalStateException(reply);
        if(ticks>600)throw new IllegalStateException("Item edge timeout test="+test+" tool="+tool+" phase="+phase+" reply="+reply);
        if(test<REPRESENTATIVES.size()*2){
            boolean off=test%2==1;var hand=off?InteractionHand.OFF_HAND:InteractionHand.MAIN_HAND;
            int number=REPRESENTATIVES.get(test/2)*4+(off?2:1);
            if(phase==0){reply="";mc.player.connection.sendCommand("foodcraftaudit food_prepare "+number);phase=1;ticks=0;return false;}
            if(phase==1&&reply.equals("FOOD_READY "+number)){
                if(mc.player.getItemInHand(hand).getCount()!=2)return false;
                mc.options.keyUse.setDown(true);mc.gameMode.useItem(mc.player,hand);phase=2;ticks=0;return false;
            }
            if(phase==2){
                if(ticks==8){mc.player.getInventory().selected=1;mc.player.connection.send(new ServerboundSetCarriedItemPacket(1));}
                if(off&&mc.player.getOffhandItem().getCount()<2){mc.options.keyUse.setDown(false);if(mc.player.isUsingItem())mc.gameMode.releaseUsingItem(mc.player);}
                if(ticks<48)return false;
                mc.options.keyUse.setDown(false);if(mc.player.isUsingItem())mc.gameMode.releaseUsingItem(mc.player);
                mc.player.getInventory().selected=0;mc.player.connection.send(new ServerboundSetCarriedItemPacket(0));
                reply="";mc.player.connection.sendCommand("foodcraftaudit food_assert "+number);phase=3;ticks=0;return false;
            }
            if(phase==3&&reply.equals("FOOD_PASS "+number)){System.out.println("FOODCRAFT AUDIT HOTBAR SWAP PASS food_case="+number+" offhand="+off);test++;phase=0;ticks=0;}
            return false;
        }
        if(tool<16){
            var hand=tool/2%2==0?InteractionHand.MAIN_HAND:InteractionHand.OFF_HAND;
            if(phase==0){reply="";mc.player.connection.sendCommand("foodcraftaudit tool_prepare "+tool);phase=1;ticks=0;return false;}
            if(phase==1&&reply.equals("TOOL_READY "+tool)){
                if(mc.player.getItemInHand(hand).isEmpty())return false;
                var pos=new BlockPos(26,70,26);mc.gameMode.useItemOn(mc.player,hand,new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false));phase=2;ticks=0;return false;
            }
            if(phase==2&&ticks>=10){reply="";mc.player.connection.sendCommand("foodcraftaudit tool_assert "+tool);phase=3;ticks=0;return false;}
            if(phase==3&&reply.equals("TOOL_PASS "+tool)){tool++;phase=0;ticks=0;}
            return false;
        }
        System.out.println("FOODCRAFT AUDIT ITEM EDGES PASS hotbar_swap_cases="+test+" native_tool_cases="+tool+" hands=2 creative_survival=true unbreaking=true enhanced_health=true");
        return true;
    }
}
