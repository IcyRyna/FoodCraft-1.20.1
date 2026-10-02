package org.foodcraft.test;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import org.foodcraft.machine.*;
import java.util.concurrent.CompletableFuture;

/** Native packets, elapsed use durations, death/respawn and real GUI resize; no OS input. */
public final class AuditClientVerification {
    private static final int START=Integer.getInteger("foodcraft.qa.auditStart",0);
    private static int food=START,phase,ticks,life,lifeMenu=-1,gui,guiMenu=-1;
    private static String message="";
    private static CompletableFuture<Void> reload;
    private static int loadedLocale=-1;
    private static int scaledHeatControls;
    private static final String[] LOCALES={"en_us","zh_cn","zh_tw"};
    private static final int[][] WINDOWS={{960,540},{1280,960},{1920,1080}};
    private AuditClientVerification(){}
    public static void message(String text){if(text.startsWith("FOODCRAFT AUDIT "))message=text.substring(16);}
    private static boolean received(String expected){return message.equals(expected);}
    private static void command(Minecraft mc,String text){message="";mc.player.connection.sendCommand("foodcraftaudit "+text);ticks=0;}
    public static boolean tick(Minecraft mc){
        if(message.startsWith("FAIL "))throw new IllegalStateException(message);
        ticks++;if(ticks>600)throw new IllegalStateException("Audit client timeout food="+food+" life="+life+" gui="+gui+" phase="+phase+" message="+message);
        if(food<AuditServerFixture.FOODS.size()*4){
            var hand=food%4<2?InteractionHand.MAIN_HAND:InteractionHand.OFF_HAND;boolean cancel=food%2==1;
            if(phase==0){command(mc,"food_prepare "+food);phase=1;return false;}
            if(phase==1&&received("FOOD_READY "+food)){
                if(mc.player.getItemInHand(hand).getCount()!=2)return false;
                mc.options.keyUse.setDown(true);mc.gameMode.useItem(mc.player,hand);ticks=0;phase=2;return false;
            }
            if(phase==2){
                if(cancel&&ticks==8){mc.options.keyUse.setDown(false);mc.gameMode.releaseUsingItem(mc.player);}
                if(!cancel&&mc.player.getItemInHand(hand).getCount()<2){
                    mc.options.keyUse.setDown(false);
                    if(mc.player.isUsingItem())mc.gameMode.releaseUsingItem(mc.player);
                }
                if(ticks<48)return false;
                command(mc,"food_assert "+food);phase=3;return false;
            }
            if(phase==3&&received("FOOD_PASS "+food)){
                food++;phase=0;ticks=0;
                if(food%40==0)System.out.println("FOODCRAFT AUDIT NATIVE FOOD PROGRESS cases="+food);
            }
            return false;
        }
        if(life<6){
            if(phase==0){command(mc,"life_prepare "+life);phase=1;lifeMenu=-1;return false;}
            if(phase==1){
                if(message.startsWith("LIFE_READY "+life+" "))lifeMenu=Integer.parseInt(message.substring(message.lastIndexOf(' ')+1));
                if(!(mc.player.containerMenu instanceof MachineMenu menu)||menu.containerId!=lifeMenu)return false;
                mc.gameMode.handleInventoryMouseClick(menu.containerId,menu.kind.size,0,ClickType.PICKUP,mc.player);
                command(mc,"life_carried "+life);phase=2;return false;
            }
            if(phase==2&&received("LIFE_CARRIED "+life+" 48")){command(mc,"life_act "+life);phase=3;return false;}
            if(phase==3){
                if(life<4){if(!mc.player.isDeadOrDying())return false;mc.player.respawn();phase=4;ticks=0;}
                else if(received("LIFE_MOVED "+life)){phase=4;ticks=0;}
                return false;
            }
            if(phase==4){
                if(mc.player.isDeadOrDying()||ticks<20)return false;
                command(mc,"life_check "+life);phase=5;return false;
            }
            if(phase==5&&received("LIFE_PASS "+life+" total=55")){life++;phase=0;ticks=0;}
            return false;
        }
        if(gui<324){
            int kind=gui%9,scale=gui/9%4+1,window=gui/36%3,locale=gui/108;
            if(loadedLocale!=locale){
                if(reload==null){mc.getLanguageManager().setSelected(LOCALES[locale]);mc.options.languageCode=LOCALES[locale];reload=mc.reloadResourcePacks();}
                if(!reload.isDone()||mc.getOverlay()!=null)return false;
                reload.join();reload=null;loadedLocale=locale;ticks=0;
            }
            if(phase==0){
                mc.getWindow().setWindowed(WINDOWS[window][0],WINDOWS[window][1]);mc.options.guiScale().set(scale);mc.resizeDisplay();
                command(mc,"gui "+kind);guiMenu=-1;phase=1;return false;
            }
            if(phase==1){
                if(message.startsWith("GUI_READY "+kind+" "))guiMenu=Integer.parseInt(message.substring(message.lastIndexOf(' ')+1));
                if(!(mc.player.containerMenu instanceof MachineMenu menu)||menu.containerId!=guiMenu||mc.screen==null)return false;
                phase=2;ticks=0;return false;
            }
            if(phase==2&&ticks>=5){
                int width=mc.getWindow().getGuiScaledWidth(),height=mc.getWindow().getGuiScaledHeight(),left=(width-176)/2,top=(height-166)/2;
                if(left<0||top<0||mc.font.width(mc.screen.getTitle())>176)throw new IllegalStateException("GUI does not fit scale/window/locale "+gui);
                var menu=(MachineMenu)mc.player.containerMenu;
                for(var slot:menu.slots)if(left+slot.x<0||left+slot.x+16>width||top+slot.y<0||top+slot.y+16>height)throw new IllegalStateException("Slot is outside GUI viewport "+gui);
                if(menu.kind.heatedExternally()){
                    int y=menu.kind==MachineKind.POT?65:19;
                    if(!mc.screen.mouseClicked(left+87,top+y,0))throw new IllegalStateException("Scaled heat click was not accepted");
                    phase=3;ticks=0;return false;
                }
                finishGui(mc,menu,scale,window,locale,width,height);return false;
            }
            if(phase==3){
                var menu=(MachineMenu)mc.player.containerMenu;if(menu.data.get(6)!=100)return false;
                int left=(mc.getWindow().getGuiScaledWidth()-176)/2,top=(mc.getWindow().getGuiScaledHeight()-166)/2,y=menu.kind==MachineKind.POT?65:19;
                if(!mc.screen.mouseClicked(left+87,top+y+13,0))throw new IllegalStateException("Scaled zero-heat click was not accepted");
                phase=4;ticks=0;return false;
            }
            if(phase==4){
                var menu=(MachineMenu)mc.player.containerMenu;if(menu.data.get(6)!=0)return false;
                scaledHeatControls+=2;finishGui(mc,menu,scale,window,locale,mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight());
            }
            return false;
        }
        System.out.println("FOODCRAFT AUDIT CLIENT PASS food_cases="+(food-START)+" start="+START+" native_use_cancel_hands=true lifecycle_cases="+life+" gui_cases="+gui+" scaled_heat_controls="+scaledHeatControls);
        return true;
    }
    private static void finishGui(Minecraft mc,MachineMenu menu,int scale,int window,int locale,int width,int height){
                if(scale==4&&window==2)Screenshot.grab(mc.gameDirectory,"audit-"+LOCALES[locale]+"-"+menu.kind.id+".png",mc.getMainRenderTarget(),m->System.out.println("FOODCRAFT AUDIT SCREENSHOT "+m.getString()));
                System.out.println("FOODCRAFT AUDIT GUI PASS case="+gui+" locale="+LOCALES[locale]+" requested_scale="+scale+" actual_scale="+mc.getWindow().getGuiScale()+" framebuffer="+mc.getWindow().getWidth()+"x"+mc.getWindow().getHeight()+" viewport="+width+"x"+height);
                mc.player.closeContainer();gui++;phase=0;ticks=0;
    }
}
