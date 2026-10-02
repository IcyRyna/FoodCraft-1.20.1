package org.foodcraft.test;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;

/** Native use, actual death/respawn or disconnect/reconnect. No OS input or direct finishUsingItem calls. */
public final class UseLifecycleVerification {
    public static boolean started;
    private static int test,phase,ticks;
    private static String reply="";
    private UseLifecycleVerification(){}
    public static void message(String text){if(text.startsWith("FOODCRAFT USE AUDIT "))reply=text.substring(20);}
    private static void command(Minecraft mc,String text){reply="";mc.player.connection.sendCommand("foodcraftuseaudit "+text);ticks=0;}
    public static boolean tick(Minecraft mc){
        started=true;ticks++;if(reply.startsWith("FAIL "))throw new IllegalStateException(reply);
        if(ticks>800)throw new IllegalStateException("Food use lifecycle timeout case="+test+" phase="+phase+" message="+reply);
        if(test>=UseLifecycleFixture.FOODS.size()*6){System.out.println("FOODCRAFT USE LIFECYCLE CLIENT PASS cases="+test+" effect_groups="+UseLifecycleFixture.FOODS.size()+" hands=2 death_keep_false_true=true native_disconnect_reconnect=true");return true;}
        if(mc.screen instanceof net.minecraft.client.gui.screens.DisconnectedScreen){
            if(test%3!=2||(phase!=3&&phase!=6)){
                net.minecraft.client.Screenshot.grab(mc.gameDirectory,"use-reconnect-failed-"+test+".png",mc.getMainRenderTarget(),m->{});
                throw new IllegalStateException("Unexpected food audit disconnect screen="+mc.screen.getTitle().getString()+" case="+test+" phase="+phase);
            }
            mc.options.keyUse.setDown(false);
            if(phase==3){phase=6;ticks=0;System.out.println("FOODCRAFT USE AUDIT RECONNECT WAIT case="+test+" prior_connection="+(mc.getConnection()!=null));return false;}
            if(ticks<20||mc.getConnection()!=null)return false;
            String address=System.getProperty("foodcraft.qa.address");
            net.minecraft.client.gui.screens.ConnectScreen.startConnecting(mc.screen,mc,net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(address),new net.minecraft.client.multiplayer.ServerData("FoodCraft QA",address,false),false);
            phase=4;ticks=0;return false;
        }
        if(mc.player==null||mc.level==null||mc.gameMode==null)return false;
        if(phase==0){command(mc,"prepare "+test);phase=1;return false;}
        var hand=test/3%2==0?InteractionHand.MAIN_HAND:InteractionHand.OFF_HAND;
        if(phase==1&&reply.equals("READY "+test)){
            if(mc.player.getItemInHand(hand).getCount()!=2)return false;
            mc.options.keyUse.setDown(true);mc.gameMode.useItem(mc.player,hand);ticks=0;phase=2;return false;
        }
        if(phase==2&&ticks==8){command(mc,"interrupt "+test);phase=3;return false;}
        if(phase==3&&test%3!=2&&mc.player.isDeadOrDying()){
            mc.options.keyUse.setDown(false);mc.player.respawn();phase=4;ticks=0;return false;
        }
        if(phase==4&&mc.player.isAlive()&&ticks>=20){command(mc,"check "+test);phase=5;return false;}
        if(phase==5&&reply.startsWith("PASS "+test+" count=2 ")){test++;phase=0;ticks=0;}
        return false;
    }
}
