package org.foodcraft.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.RenderType;
import org.foodcraft.FoodCraft;
import org.foodcraft.client.MachineScreen;
import org.foodcraft.agriculture.FoodCropBlock;
import org.foodcraft.agriculture.FoodSaplingBlock;
import org.foodcraft.agriculture.FruitBlock;

public final class FabricClient implements ClientModInitializer {
    private int ticks;
    @Override public void onInitializeClient(){
        net.fabricmc.fabric.api.client.networking.v1.ClientLoginNetworking.registerGlobalReceiver(FoodCraft.id("handshake"),(client,handler,buffer,listeners)->{
            int protocol=buffer.readVarInt();
            var response=net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();response.writeVarInt(protocol==2?2:-1);
            return java.util.concurrent.CompletableFuture.completedFuture(response);
        });
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(FabricPlatform.SETTINGS,(client,handler,buffer,sender)->{
            boolean wrench=buffer.readBoolean();client.execute(()->{FabricPlatform.setRemoteWrench(wrench);if(Boolean.getBoolean("foodcraft.qa.details"))System.out.println("FOODCRAFT DETAIL CONFIG SYNC CLIENT wrench="+wrench);});
        });
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->FabricPlatform.setRemoteWrench(null));
        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.GAME.register((message,overlay)->{org.foodcraft.test.UiDetailVerification.onSystemMessage(message.getString());org.foodcraft.test.NetworkStressVerification.message(message.getString());org.foodcraft.test.AuditClientVerification.message(message.getString());org.foodcraft.test.NetworkNegativeVerification.message(message.getString());org.foodcraft.test.ItemEdgeVerification.message(message.getString());org.foodcraft.test.UseLifecycleVerification.message(message.getString());});
        org.foodcraft.machine.MachineTransfers.setClientSender(request->{
            var buffer=net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();request.write(buffer);
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(org.foodcraft.machine.MachineTransfers.CHANNEL,buffer);
        });
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client->org.foodcraft.test.ClientVerification.tick());
        if(net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("jei"))net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client->{if(++ticks%20==0)org.foodcraft.compat.FoodCraftJei.checkReload();});
        FoodCraft.MENUS.values().forEach(type->MenuScreens.register(type,MachineScreen::new));
        FoodCraft.BLOCKS.values().forEach(block->{if(block instanceof FoodCropBlock||block instanceof org.foodcraft.agriculture.FoodOnionBlock||block instanceof FoodSaplingBlock||block instanceof FruitBlock)BlockRenderLayerMap.INSTANCE.putBlock(block,RenderType.cutoutMipped());});
    }
}
