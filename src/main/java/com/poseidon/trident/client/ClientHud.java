package com.poseidon.trident.client;

import com.poseidon.trident.Power;
import com.poseidon.trident.PoseidonsTridentMod;
import com.poseidon.trident.PowerManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = PoseidonsTridentMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientHud {
    private static final long SHOW_MS = 2500L;
    private static final long FADE_MS = 600L;

    private ClientHud() {}

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !PowerManager.isHolding(mc.player)) return;

        GuiGraphics g = event.getGuiGraphics();
        Font font = mc.font;
        int w = event.getWindow().getGuiScaledWidth();
        int h = event.getWindow().getGuiScaledHeight();
        int sel = ClientState.selected;
        Power[] powers = Power.values();

        Component small = Component.literal("\u2726 ").append(powers[sel].displayName());
        g.drawString(font, small, w - font.width(small) - 8, h - 18, 0xFF000000 | ClientState.COLORS[sel], true);

        long age = System.currentTimeMillis() - ClientState.switchedAtMillis;
        if (age < 0 || age > SHOW_MS) return;
        float fade = age < SHOW_MS - FADE_MS ? 1.0F : 1.0F - (age - (SHOW_MS - FADE_MS)) / (float) FADE_MS;
        int alpha = (int) (255 * fade);
        if (alpha < 12) return;

        var pose = g.pose();
        pose.pushPose();
        pose.translate(w / 2.0F, h / 2.0F - 44.0F, 0.0F);
        pose.scale(2.0F, 2.0F, 1.0F);
        g.drawCenteredString(font, powers[sel].displayName(), 0, 0, (alpha << 24) | ClientState.COLORS[sel]);
        pose.popPose();

        int total = -12;
        for (Power p : powers) total += font.width(p.displayName()) + 12;
        int x = (w - total) / 2;
        int y = h / 2 - 44 + 24;
        for (int i = 0; i < powers.length; i++) {
            Component name = powers[i].displayName();
            int a = i == sel ? alpha : (int) (alpha * 0.45F);
            if (a >= 12) {
                g.drawString(font, name, x, y, (a << 24) | ClientState.COLORS[i], true);
            }
            x += font.width(name) + 12;
        }
    }
}
