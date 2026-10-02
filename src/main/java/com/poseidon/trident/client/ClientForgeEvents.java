package com.poseidon.trident.client;

import com.poseidon.trident.Action;
import com.poseidon.trident.ActionPacket;
import com.poseidon.trident.Network;
import com.poseidon.trident.PoseidonsTridentMod;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/** Forge-bus client events: turns key presses into packets for the server. */
@Mod.EventBusSubscriber(modid = PoseidonsTridentMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientForgeEvents {
    private ClientForgeEvents() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;

        poll(ModKeys.THUNDER_DOMAIN, Action.THUNDER_DOMAIN);
        poll(ModKeys.CHARGE, Action.CHARGE);
        poll(ModKeys.DASH, Action.DASH);
        poll(ModKeys.WATER_CUBE, Action.WATER_CUBE);
        poll(ModKeys.TSUNAMI, Action.TSUNAMI);
    }

    private static void poll(KeyMapping key, Action action) {
        while (key.consumeClick()) {
            Network.CHANNEL.send(new ActionPacket(action.ordinal()), PacketDistributor.SERVER.noArg());
        }
    }
}
