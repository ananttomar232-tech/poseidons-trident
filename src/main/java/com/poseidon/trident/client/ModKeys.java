package com.poseidon.trident.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.poseidon.trident.PoseidonsTridentMod;

import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/** Key bindings. All of them can be changed in Options > Controls > Poseidon's Trident. */
public final class ModKeys {
    public static final KeyMapping THUNDER_DOMAIN = key("thunder_domain", GLFW.GLFW_KEY_G);
    public static final KeyMapping CHARGE         = key("charge",         GLFW.GLFW_KEY_R);
    public static final KeyMapping DASH           = key("dash",           GLFW.GLFW_KEY_K);
    public static final KeyMapping WATER_CUBE     = key("water_cube",     GLFW.GLFW_KEY_H);
    public static final KeyMapping TSUNAMI        = key("tsunami",        GLFW.GLFW_KEY_J);

    // Ability selector (switch with the first two, activate with the third)
    public static final KeyMapping NEXT_ABILITY     = key("next_ability",     GLFW.GLFW_KEY_V);
    public static final KeyMapping PREVIOUS_ABILITY = key("previous_ability", GLFW.GLFW_KEY_B);
    public static final KeyMapping USE_SELECTED     = key("use_selected",     GLFW.GLFW_KEY_N);

    private ModKeys() {}

    private static KeyMapping key(String name, int keyCode) {
        return new KeyMapping(
                "key." + PoseidonsTridentMod.MOD_ID + "." + name,
                KeyConflictContext.IN_GAME,
                InputConstants.Type.KEYSYM,
                keyCode,
                "key.categories." + PoseidonsTridentMod.MOD_ID);
    }
}
