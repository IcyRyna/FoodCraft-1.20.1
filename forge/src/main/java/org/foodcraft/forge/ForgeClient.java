package org.foodcraft.forge;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import org.foodcraft.FoodCraft;
import org.foodcraft.client.MachineScreen;
import org.foodcraft.agriculture.FoodCropBlock;
import org.foodcraft.agriculture.FoodSaplingBlock;
import org.foodcraft.agriculture.FruitBlock;

@Mod.EventBusSubscriber(modid=FoodCraft.MOD_ID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class ForgeClient {
    private static int ticks;
    private static final boolean JEI=net.minecraftforge.fml.ModList.get().isLoaded("jei");
    private ForgeClient(){}
    @SubscribeEvent public static void setup(FMLClientSetupEvent event){
        event.enqueueWork(()->{
            org.foodcraft.machine.MachineTransfers.setClientSender(ForgeNetworking::send);
            FoodCraft.MENUS.values().forEach(type->MenuScreens.register(type,MachineScreen::new));
            FoodCraft.BLOCKS.values().forEach(block->{if(block instanceof FoodCropBlock||block instanceof org.foodcraft.agriculture.FoodOnionBlock||block instanceof FoodSaplingBlock||block instanceof FruitBlock)ItemBlockRenderTypes.setRenderLayer(block,RenderType.cutoutMipped());});
        });
    }
    @Mod.EventBusSubscriber(modid=FoodCraft.MOD_ID,value=Dist.CLIENT)
    public static final class TickHandler {
        @SubscribeEvent public static void message(net.minecraftforge.client.event.ClientChatReceivedEvent event){org.foodcraft.test.UiDetailVerification.onSystemMessage(event.getMessage().getString());org.foodcraft.test.NetworkStressVerification.message(event.getMessage().getString());org.foodcraft.test.AuditClientVerification.message(event.getMessage().getString());org.foodcraft.test.NetworkNegativeVerification.message(event.getMessage().getString());org.foodcraft.test.ItemEdgeVerification.message(event.getMessage().getString());org.foodcraft.test.UseLifecycleVerification.message(event.getMessage().getString());}
        @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.ClientTickEvent event){
            if(event.phase==net.minecraftforge.event.TickEvent.Phase.END)org.foodcraft.test.ClientVerification.tick();
            if(event.phase==net.minecraftforge.event.TickEvent.Phase.END&&JEI&&++ticks%20==0)org.foodcraft.compat.FoodCraftJei.checkReload();
        }
    }
}
