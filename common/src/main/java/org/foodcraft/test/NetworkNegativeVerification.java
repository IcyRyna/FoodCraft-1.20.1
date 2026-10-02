package org.foodcraft.test;

import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import org.foodcraft.FoodCraft;
import org.foodcraft.machine.MachineTransfers;

/** Real malformed C2S payloads, with server inventory checks and reconnects. */
public final class NetworkNegativeVerification {
    private static int scenario,phase,ticks;
    private static String reply="";
    private static int preparedMenu;
    private static ResourceLocation preparedRecipe;
    public static boolean started;
    private NetworkNegativeVerification(){}
    public static void message(String text){if(text.startsWith("FOODCRAFT AUDIT "))reply=text.substring(16);}
    public static boolean tick(Minecraft mc){
        started=true;ticks++;
        if(reply.startsWith("FAIL"))throw new IllegalStateException(reply);
        if(ticks>1000)throw new IllegalStateException("Negative network timeout case="+scenario+" phase="+phase+" reply="+reply);
        if(scenario==3){
            if(mc.player==null||mc.level==null||mc.gameMode==null)return false;
            if(phase==0){reply="";mc.player.connection.sendCommand("foodcraftaudit negative_assert");phase=5;return false;}
            if(reply.equals("NEGATIVE_PASS stock=16")){System.out.println("FOODCRAFT AUDIT NEGATIVE NETWORK PASS cases=3 stock=16 server_survived=true");return true;}
            return false;
        }
        if(mc.screen instanceof DisconnectedScreen){
            if(phase!=2)throw new IllegalStateException("Unexpected negative-test disconnect");
            Screenshot.grab(mc.gameDirectory,"negative-packet-"+scenario+".png",mc.getMainRenderTarget(),m->{});
            System.out.println("FOODCRAFT AUDIT BAD PACKET REJECTED case="+scenario+" action=disconnect");
            scenario++;phase=0;ticks=0;reply="";
            String address=System.getProperty("foodcraft.qa.address");
            net.minecraft.client.gui.screens.ConnectScreen.startConnecting(mc.screen,mc,net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(address),new net.minecraft.client.multiplayer.ServerData("FoodCraft QA",address,false),false);
            return false;
        }
        if(mc.player==null||mc.level==null||mc.gameMode==null)return false;
        if(phase==0){reply="";mc.player.connection.sendCommand("foodcraftaudit negative_prepare");phase=1;ticks=0;return false;}
        if(phase==1&&reply.startsWith("NEGATIVE_READY ")){
            String[] fields=reply.split(" ");preparedMenu=Integer.parseInt(fields[1]);preparedRecipe=new ResourceLocation(fields[2]);
            MachineTransfers.send(new MachineTransfers.Request(preparedMenu,preparedRecipe,false));
            mc.player.connection.sendCommand("foodcraftaudit negative_control");phase=4;ticks=0;reply="";return false;
        }
        if(phase==4&&reply.equals("NEGATIVE_CONTROL_PASS stock=16")){
            int menu=preparedMenu;var recipe=preparedRecipe;
            var buf=new FriendlyByteBuf(Unpooled.buffer());
            if(FoodCraft.platform.modLoaded("forge"))buf.writeVarInt(0);
            if(scenario==0){buf.writeVarInt(menu);buf.writeResourceLocation(recipe);buf.writeBoolean(false);buf.writeByte(1);}
            else if(scenario==2){buf.writeVarInt(101);buf.writeResourceLocation(recipe);buf.writeBoolean(false);}
            // Scenario one intentionally leaves only the Forge discriminator, or an empty Fabric payload.
            var packet=new ServerboundCustomPayloadPacket(MachineTransfers.CHANNEL,buf);
            mc.player.connection.getConnection().send(packet,PacketSendListener.thenRun(buf::release));
            phase=2;ticks=0;reply="";return false;
        }
        if(phase==2&&ticks==40)mc.player.connection.sendCommand("foodcraftaudit negative_assert");
        if(phase==2&&reply.equals("NEGATIVE_PASS stock=16")){
            System.out.println("FOODCRAFT AUDIT BAD PACKET REJECTED case="+scenario+" action=ignored_without_mutation");
            scenario++;phase=0;ticks=0;reply="";
        }
        return false;
    }
}
