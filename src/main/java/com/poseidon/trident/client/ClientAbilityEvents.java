package com.poseidon.trident.client;

import com.poseidon.trident.Action;
import com.poseidon.trident.ActionPacket;
import com.poseidon.trident.Network;
import com.poseidon.trident.Power;
import com.poseidon.trident.PoseidonsTridentMod;
import com.poseidon.trident.PowerManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

@Mod.EventBusSubscriber(modid = PoseidonsTridentMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientAbilityEvents {
    private static final int COUNT = 4;

    private ClientAbilityEvents() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            ClientState.wasHolding = false;
            return;
        }
        boolean holding = PowerManager.isHolding(mc.player);

        if (holding && !ClientState.wasHolding) {
            send(Action.values()[Action.SELECT_THUNDER_DOMAIN.ordinal() + ClientState.selected]);
        }
        ClientState.wasHolding = holding;

        if (mc.screen != null) return;

        int next = 0, prev = 0, use = 0;
        while (ModKeys.NEXT_ABILITY.consumeClick()) next++;
        while (ModKeys.PREVIOUS_ABILITY.consumeClick()) prev++;
        while (ModKeys.USE_SELECTED.consumeClick()) use++;
        if (!holding) return;

        if (next > 0 || prev > 0) {
            int index = ((ClientState.selected + next - prev) % COUNT + COUNT) % COUNT;
            select(index);
        }
        if (use > 0) {
            Action action = switch (ClientState.selected) {
                case 0 -> Action.THUNDER_DOMAIN;
                case 1 -> Action.GOD_SPEED_SMART;
                case 2 -> Action.WATER_CUBE;
                default -> Action.TSUNAMI;
            };
            send(action);
        }
    }

    private static void select(int index) {
        ClientState.selected = index;
        ClientState.switchedAtMillis = System.currentTimeMillis();
        send(Action.values()[Action.SELECT_THUNDER_DOMAIN.ordinal() + index]);
        showTitle(index);
    }

    private static void showTitle(int index) {
        Minecraft mc = Minecraft.getInstance();
        Component name = Power.values()[index].displayName().copy()
                .withStyle(Style.EMPTY.withColor(ClientState.COLORS[index]).withBold(true));
        Component hint = Component.literal("V / B = switch    N = use").withStyle(ChatFormatting.GRAY);
        mc.gui.setTimes(2, 30, 10);
        mc.gui.setSubtitle(hint);
        mc.gui.setTitle(name);
    }

    private static void send(Action action) {
        Network.CHANNEL.send(new ActionPacket(action.ordinal()), PacketDistributor.SERVER.noArg());
    }
}
