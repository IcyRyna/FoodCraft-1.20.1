package org.foodcraft.forge;

import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import org.foodcraft.machine.MachineTransfers;

final class ForgeNetworking {
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(MachineTransfers.CHANNEL, () -> "2", "2"::equals, "2"::equals);
    private ForgeNetworking() {}
    static void initialize() {
        CHANNEL.messageBuilder(MachineTransfers.Request.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder(MachineTransfers.Request::write).decoder(MachineTransfers.Request::read)
                .consumerMainThread((request, context) -> MachineTransfers.handle(context.get().getSender(), request)).add();
    }
    static void send(MachineTransfers.Request request) { CHANNEL.sendToServer(request); }
}
