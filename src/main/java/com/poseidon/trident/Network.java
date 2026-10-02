package com.poseidon.trident;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.SimpleChannel;

public final class Network {
    public static final SimpleChannel CHANNEL = ChannelBuilder
            .named(ResourceLocation.fromNamespaceAndPath(PoseidonsTridentMod.MOD_ID, "main"))
            .networkProtocolVersion(1)
            .simpleChannel()
            .messageBuilder(ActionPacket.class, NetworkDirection.PLAY_TO_SERVER)
            .encoder(ActionPacket::encode)
            .decoder(ActionPacket::decode)
            .consumerMainThread(ActionPacket::handle)
            .add();

    private Network() {}

    /** Forces the class (and therefore the channel) to initialise. */
    public static void init() {}
}
