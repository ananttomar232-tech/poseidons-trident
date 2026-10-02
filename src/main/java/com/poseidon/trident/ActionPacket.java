package com.poseidon.trident;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;

/** Client -> server: "the player pressed the key for this action". */
public record ActionPacket(int action) {

    public static void encode(ActionPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.action);
    }

    public static ActionPacket decode(FriendlyByteBuf buf) {
        return new ActionPacket(buf.readVarInt());
    }

    public static void handle(ActionPacket packet, CustomPayloadEvent.Context context) {
        ServerPlayer sender = context.getSender();
        if (sender != null) {
            PowerManager.handleAction(sender, packet.action);
        }
    }
}
