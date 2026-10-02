package com.poseidon.trident;

import net.minecraft.network.chat.Component;

public enum Power {
    THUNDER_DOMAIN("thunder_domain"),
    GOD_SPEED("god_speed"),
    WATER_CUBE("water_cube"),
    TSUNAMI("tsunami");

    public final String id;

    Power(String id) {
        this.id = id;
    }

    public Component displayName() {
        return Component.translatable("power." + PoseidonsTridentMod.MOD_ID + "." + id);
    }
}
