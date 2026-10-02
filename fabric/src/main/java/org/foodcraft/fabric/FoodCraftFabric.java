package org.foodcraft.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.loot.v2.LootTableEvents;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.InventoryStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import org.foodcraft.FoodCraft;

public final class FoodCraftFabric implements ModInitializer {
    @Override public void onInitialize(){
        FoodCraft.initialize(new FabricPlatform());
        net.fabricmc.fabric.api.networking.v1.ServerLoginConnectionEvents.QUERY_START.register((handler,server,sender,synchronizer)->{
            var query=net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();query.writeVarInt(2);
            sender.sendPacket(FoodCraft.id("handshake"),query);
        });
        net.fabricmc.fabric.api.networking.v1.ServerLoginNetworking.registerGlobalReceiver(FoodCraft.id("handshake"),(server,handler,understood,buffer,synchronizer,sender)->{
            boolean compatible=false;
            if(understood)try{compatible=buffer.readVarInt()==2&&!buffer.isReadable();}catch(IndexOutOfBoundsException|io.netty.handler.codec.DecoderException invalid){compatible=false;}
            if(!compatible)synchronizer.waitFor(server.submit(()->handler.disconnect(net.minecraft.network.chat.Component.literal("FoodCraft network protocol mismatch. Install the same FoodCraft build on client and server."))));
        });
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(org.foodcraft.machine.MachineTransfers.CHANNEL,(server,player,handler,buffer,sender)->{
            var request=org.foodcraft.machine.MachineTransfers.Request.read(buffer);
            server.execute(()->org.foodcraft.machine.MachineTransfers.handle(player,request));
        });
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((dispatcher,access,environment)->org.foodcraft.test.ClientTestFixture.commands(dispatcher));
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->{
            var settings=net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();settings.writeBoolean(((FabricPlatform)FoodCraft.platform).configuredWrench());
            sender.sendPacket(FabricPlatform.SETTINGS,settings);org.foodcraft.test.ClientTestFixture.onJoin(handler.player);
        });
        ItemStorage.SIDED.registerForBlockEntity(FabricItemStorage::new,FoodCraft.MACHINE_ENTITY);
        FluidStorage.SIDED.registerForBlockEntity((machine,side)->new FabricWaterStorage(machine,side),FoodCraft.MACHINE_ENTITY);
        if(Boolean.getBoolean("foodcraft.qa.server"))FluidStorage.SIDED.registerForBlockEntity((chest,side)->chest.getLevel() instanceof net.minecraft.server.level.ServerLevel?new FabricQaWater(chest):null,net.minecraft.world.level.block.entity.BlockEntityType.CHEST);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_WORLD_TICK.register(org.foodcraft.test.PipelineVerification::tick);
        LootTableEvents.MODIFY.register((resources,manager,id,builder,source)->{
            if(source.isBuiltin())org.foodcraft.loot.FoodCraftLoot.pools(id).forEach(builder::withPool);
        });
    }
}
