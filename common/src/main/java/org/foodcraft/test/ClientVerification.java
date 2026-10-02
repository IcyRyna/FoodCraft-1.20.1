package org.foodcraft.test;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.server.level.ServerPlayer;
import org.foodcraft.machine.*;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;

/** Runs genuine client/container packets; screenshots come from Minecraft's framebuffer. */
public final class ClientVerification {
    private static final String MODE=System.getProperty("foodcraft.verify.client","");
    private static int ticks,index,phase;
    private static boolean setupRequested,started,optionsSet,launchRequested,destroyRequested,jeiVerified,compatRequested,detailsDone;
    private static final AtomicBoolean ready=new AtomicBoolean();
    private ClientVerification(){}
    public static void tick(){
        if(MODE.isEmpty())return;
        Minecraft mc=Minecraft.getInstance();ticks++;
        if(!optionsSet){mc.options.pauseOnLostFocus=false;mc.options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MASTER).set(0.0D);optionsSet=true;}
        if(Boolean.getBoolean("foodcraft.qa.useLifecycle")&&(UseLifecycleVerification.started||setupRequested&&mc.level!=null&&mc.player!=null&&mc.gameMode!=null)){
            try{if(UseLifecycleVerification.tick(mc)){marker("client-complete.txt","Native active food death/disconnect audit complete.\n");mc.stop();}}catch(RuntimeException failure){fail(mc,failure.toString());}
            return;
        }
        if(Boolean.getBoolean("foodcraft.qa.negative")&&(NetworkNegativeVerification.started||mc.level!=null&&mc.player!=null&&mc.gameMode!=null)){
            try{if(NetworkNegativeVerification.tick(mc)){marker("client-complete.txt","Native malformed packet rejection complete.\n");mc.stop();}}catch(RuntimeException failure){fail(mc,failure.toString());}
            return;
        }
        if(mc.level==null||mc.player==null||mc.gameMode==null){
            if(Boolean.getBoolean("foodcraft.qa.stress")&&mc.screen instanceof net.minecraft.client.gui.screens.DisconnectedScreen){
                NetworkStressVerification.reconnect();String address=System.getProperty("foodcraft.qa.address");
                net.minecraft.client.gui.screens.ConnectScreen.startConnecting(mc.screen,mc,net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(address),new net.minecraft.client.multiplayer.ServerData("FoodCraft QA",address,false),false);ticks=0;return;
            }
            if(ticks==200){System.out.println("FOODCRAFT QA WAITING SCREEN="+(mc.screen==null?"null":mc.screen.getClass().getName()));capture(mc,"waiting.png");}
            if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen||mc.screen instanceof net.minecraft.client.gui.screens.ConfirmScreen){
                for(var child:mc.screen.children())if(child instanceof net.minecraft.client.gui.components.Button button&&java.util.Set.of("Continue","Done","Yes","Proceed").contains(button.getMessage().getString())){button.onPress();break;}
            }
            if(!launchRequested&&mc.screen instanceof net.minecraft.client.gui.screens.TitleScreen&&mc.getOverlay()==null){
                launchRequested=true;
                if(MODE.equals("single"))mc.createWorldOpenFlows().loadLevel(mc.screen,"FoodCraftQA");
                else{
                    String address=System.getProperty("foodcraft.qa.address","127.0.0.1:25575");
                    net.minecraft.client.gui.screens.ConnectScreen.startConnecting(mc.screen,mc,net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(address),new net.minecraft.client.multiplayer.ServerData("FoodCraft QA",address,false),false);
                }
            }
            if(ticks>4000)fail(mc,"World failed to load");return;
        }
        if(!setupRequested){
            setupRequested=true;
            if(mc.getSingleplayerServer()!=null){
                var server=mc.getSingleplayerServer();server.execute(()->{var player=server.getPlayerList().getPlayer(mc.player.getUUID());ClientTestFixture.setup(player);ready.set(true);});
            }else{mc.player.connection.sendCommand("foodcraftqa setup");ready.set(true);}
            ticks=0;return;
        }
        if(!ready.get()||!started&&ticks<40)return;
        if(Boolean.getBoolean("foodcraft.qa.visual")){
            try{if(VisualMatrixVerification.tick(mc)){marker("client-complete.txt","Native GPU visual matrix complete.\n");mc.stop();}}catch(RuntimeException failure){fail(mc,failure.toString());}
            return;
        }
        if(Boolean.getBoolean("foodcraft.qa.audit")){
            try{if(AuditClientVerification.tick(mc)){marker("client-complete.txt","Native detailed audit complete.\n");mc.stop();}}catch(RuntimeException failure){fail(mc,failure.toString());}
            return;
        }
        if(Boolean.getBoolean("foodcraft.qa.edges")){
            try{if(ItemEdgeVerification.tick(mc)){marker("client-complete.txt","Native item edge checks complete.\n");mc.stop();}}catch(RuntimeException failure){fail(mc,failure.toString());}
            return;
        }
        if(Boolean.getBoolean("foodcraft.qa.stress")){
            try{if(NetworkStressVerification.tick(mc)){marker("client-complete.txt","Native multiplayer stress matrix complete.\n");mc.stop();}}catch(RuntimeException failure){fail(mc,failure.toString());}
            return;
        }
        if(!compatRequested&&org.foodcraft.FoodCraft.platform.modLoaded("crafttweaker")){
            compatRequested=true;
            if(mc.getSingleplayerServer()!=null){var server=mc.getSingleplayerServer();server.execute(()->ClientTestFixture.compatVerify(server.overworld()));}
            else mc.player.connection.sendCommand("foodcraftqa compat_verify");
        }
        if(!jeiVerified&&org.foodcraft.FoodCraft.platform.modLoaded("jei")){
            if(!org.foodcraft.compat.FoodCraftJei.verifyRuntime()){if(ticks>600)fail(mc,"JEI runtime did not initialize");return;}
            jeiVerified=true;
        }
        if(Boolean.getBoolean("foodcraft.qa.details")&&!detailsDone){
            try{detailsDone=UiDetailVerification.tick(mc);}catch(RuntimeException exception){fail(mc,exception.toString());return;}
            if(detailsDone)ticks=0;
            return;
        }
        if(!started){started=true;capture(mc,"world.png");ticks=0;}
        if(index>=MachineKind.values().length){
            if(!destroyRequested){
                destroyRequested=true;ticks=0;
                if(mc.getSingleplayerServer()!=null){var server=mc.getSingleplayerServer();server.execute(()->ClientTestFixture.destroyCheck(server.getPlayerList().getPlayer(mc.player.getUUID())));}
                else mc.player.connection.sendCommand("foodcraftqa destroy_check");
                return;
            }
            if(ticks<30)return;
            marker("client-complete.txt","FoodCraft client completed all nine menus and native click modes.\n");
            System.out.println("FOODCRAFT CLIENT COMPLETE "+MODE);mc.stop();return;
        }
        MachineKind kind=MachineKind.values()[index];
        if(phase==0){
            if(mc.getSingleplayerServer()!=null){var server=mc.getSingleplayerServer();server.execute(()->ClientTestFixture.open(server.getPlayerList().getPlayer(mc.player.getUUID()),kind));}
            else mc.player.connection.sendCommand("foodcraftqa open "+kind.id);
            phase=1;ticks=0;return;
        }
        if(!(mc.player.containerMenu instanceof MachineMenu menu)||menu.kind!=kind){if(ticks>400)fail(mc,"Menu failed to open "+kind.id);return;}
        int base=kind.size;
        if(ticks==20)capture(mc,kind.id+".png");
        if(ticks==25)click(mc,menu,base,0,ClickType.QUICK_MOVE);
        if(ticks==35)click(mc,menu,base+27,0,ClickType.PICKUP);
        if(ticks==40){
            click(mc,menu,-999,AbstractContainerMenu.getQuickcraftMask(0,0),ClickType.QUICK_CRAFT);
            for(int i=1;i<=3;i++)click(mc,menu,base+i,AbstractContainerMenu.getQuickcraftMask(1,0),ClickType.QUICK_CRAFT);
            click(mc,menu,-999,AbstractContainerMenu.getQuickcraftMask(2,0),ClickType.QUICK_CRAFT);
        }
        if(ticks==45)click(mc,menu,base+27,0,ClickType.PICKUP);
        if(ticks==50)click(mc,menu,base+1,1,ClickType.SWAP);
        if(ticks==55)click(mc,menu,base+2,0,ClickType.THROW);
        if(ticks==60)click(mc,menu,base+28,0,ClickType.PICKUP);
        if(ticks==65)click(mc,menu,base+2,0,ClickType.PICKUP_ALL);
        if(ticks==70)click(mc,menu,base+27,0,ClickType.PICKUP);
        if(ticks==85){
            if(mc.getSingleplayerServer()!=null){var server=mc.getSingleplayerServer();server.execute(()->ClientTestFixture.assertLedger(server.getPlayerList().getPlayer(mc.player.getUUID())));}
            else mc.player.connection.sendCommand("foodcraftqa assert");
        }
        if(ticks>=100){mc.player.closeContainer();index++;phase=0;ticks=0;}
    }
    private static void click(Minecraft mc,MachineMenu menu,int slot,int button,ClickType kind){mc.gameMode.handleInventoryMouseClick(menu.containerId,slot,button,kind,mc.player);}
    private static void capture(Minecraft mc,String name){
        Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->System.out.println("FOODCRAFT SCREENSHOT "+name));
    }
    private static void marker(String name,String value){
        try{var path=Path.of(System.getProperty("foodcraft.evidence.dir",mcDirectory())).resolve(name);Files.createDirectories(path.getParent());Files.writeString(path,value);}
        catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}
    }
    private static String mcDirectory(){return Minecraft.getInstance().gameDirectory.getAbsolutePath();}
    private static void fail(Minecraft mc,String reason){marker("client-failed.txt",reason);System.err.println("FOODCRAFT CLIENT FAILED "+reason);mc.stop();}
}
