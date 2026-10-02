package org.foodcraft.test;

import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.world.item.ItemStack;
import org.foodcraft.machine.*;
import java.util.Random;

/** Native client prediction plus native server packets, including stale container IDs. */
public final class NetworkStressVerification {
    private static final Random random=new Random(0x4e4554574f524bL+System.getProperty("foodcraft.qa.actor","1").hashCode());
    private static int phase,ticks,index,round;
    private static boolean ready,ledger,cursorProbed,cursorAck;
    private static boolean recovering;
    public static void reconnect(){ready=false;ledger=false;recovering=true;phase=0;ticks=0;System.out.println("FOODCRAFT NETWORK STRESS RECONNECTING cursor_inventory_preserved_by_server=true");}
    private NetworkStressVerification(){}
    public static void message(String value){
        if(value.equals("FOODCRAFT_STRESS_READY"))ready=true;if(value.equals("FOODCRAFT_STRESS_LEDGER_OK"))ledger=true;if(value.equals("FOODCRAFT_STRESS_CURSOR_OK"))cursorAck=true;
        if(value.equals("FOODCRAFT_STRESS_RESET")){phase=0;ticks=0;index=0;round=0;ready=false;ledger=false;cursorProbed=false;cursorAck=false;recovering=false;}
    }
    public static boolean tick(Minecraft mc){
        ticks++;
        if(phase==0){
            if(recovering){mc.player.connection.sendCommand("foodcraftqa stress_resynchronize");phase=7;ticks=0;return false;}
            if(org.foodcraft.FoodCraft.platform.modLoaded("jei")&&!org.foodcraft.compat.FoodCraftJei.verifyRuntime())return false;
            if(org.foodcraft.FoodCraft.platform.modLoaded("crafttweaker"))mc.player.connection.sendCommand("foodcraftqa compat_verify");
            mc.player.connection.sendCommand("foodcraftqa stress_setup");phase=1;ticks=0;return false;
        }
        if(phase==1){if(!ready){if(ticks>1200)throw new IllegalStateException("Stress setup timed out");return false;}phase=2;ticks=0;return false;}
        if(phase==7){if(ticks>1200)throw new IllegalStateException("Paired recovery timed out");return false;}
        if(round>=4){System.out.println("FOODCRAFT NETWORK STRESS CLIENT PASS rounds=4 menus=36 packets_per_menu=80 full_inventory=true survival_creative=true stale_ids=true");return true;}
        var kind=MachineKind.values()[index];
        if(phase==2){
            mc.player.connection.sendCommand("foodcraftqa stress_open "+kind.id+" "+(round%2==1)+" "+(round>=2));phase=3;ticks=0;return false;
        }
        if(phase==3){
            if(!(mc.player.containerMenu instanceof MachineMenu menu)||menu.kind!=kind){if(ticks>1200)throw new IllegalStateException("Stress menu failed to open");return false;}
            if(ticks<20)return false;phase=4;ticks=0;return false;
        }
        if(phase==4){
            if(!(mc.player.containerMenu instanceof MachineMenu menu)){phase=5;ticks=0;return false;}
            int size=menu.slots.size(),base=kind.size;
            if(!cursorProbed){
                if(ticks==1){cursorAck=false;mc.gameMode.handleInventoryMouseClick(menu.containerId,base,0,ClickType.PICKUP,mc.player);}
                if(ticks==30)mc.player.connection.sendCommand("foodcraftqa stress_cursor_assert");
                if(ticks<140)return false;
                if(!cursorAck)throw new IllegalStateException("Server did not acknowledge the native carried-item proof");
                mc.gameMode.handleInventoryMouseClick(menu.containerId,base,0,ClickType.PICKUP,mc.player);cursorProbed=true;ticks=0;return false;
            }
            if(ticks<=80){
                int slot=random.nextInt(size);ClickType click=switch(ticks%6){case 0->ClickType.QUICK_MOVE;case 1->ClickType.SWAP;case 2->ClickType.THROW;case 3->ClickType.PICKUP_ALL;default->ClickType.PICKUP;};
                int button=click==ClickType.SWAP?random.nextInt(9):random.nextInt(2);
                mc.gameMode.handleInventoryMouseClick(menu.containerId,slot,button,click,mc.player);
                if(ticks%10==0){
                    mc.gameMode.handleInventoryMouseClick(menu.containerId,-999,AbstractContainerMenu.getQuickcraftMask(0,0),ClickType.QUICK_CRAFT,mc.player);
                    for(int i=0;i<3;i++)mc.gameMode.handleInventoryMouseClick(menu.containerId,base+random.nextInt(36),AbstractContainerMenu.getQuickcraftMask(1,0),ClickType.QUICK_CRAFT,mc.player);
                    mc.gameMode.handleInventoryMouseClick(menu.containerId,-999,AbstractContainerMenu.getQuickcraftMask(2,0),ClickType.QUICK_CRAFT,mc.player);
                    int stale=menu.containerId%100+1;
                    mc.player.connection.send(new ServerboundContainerClickPacket(stale,0,base,0,ClickType.PICKUP,ItemStack.EMPTY,new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<>()));
                    mc.player.connection.send(new ServerboundContainerClosePacket(stale));
                }
                return false;
            }
            // Destruction races genuine in-flight clicks from the other connection.
            if(index==7&&System.getProperty("foodcraft.qa.actor","1").equals("1"))mc.player.connection.sendCommand("foodcraftqa stress_destroy "+kind.id);
            mc.player.closeContainer();phase=5;ticks=0;ledger=false;return false;
        }
        if(phase==5){if(ticks<30)return false;ledger=false;mc.player.connection.sendCommand("foodcraftqa stress_assert");phase=6;ticks=0;return false;}
        if(!ledger){if(ticks>1200)throw new IllegalStateException("Stress conservation acknowledgment missing");return false;}
        System.out.println("FOODCRAFT NETWORK STRESS MENU PASS round="+round+" kind="+kind);index++;
        if(index>=9){index=0;round++;}phase=2;ticks=0;return false;
    }
}
