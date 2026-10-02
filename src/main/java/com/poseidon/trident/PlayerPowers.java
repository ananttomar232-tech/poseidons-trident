package com.poseidon.trident;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Server-side, per-player runtime state for the active powers. Not saved to disk. */
final class PlayerPowers {
    /** Game tick at which each power ends; 0 means "not active". */
    final long[] activeUntil = new long[Power.values().length];
    /** Game tick until which each power is on cooldown. */
    final long[] cooldownUntil = new long[Power.values().length];

    /** Game tick when R was pressed, or -1 when the trident is not charging/charged. */
    long chargeStart = -1;
    boolean chargeAnnounced;

    /** God Speed burst dash. */
    int dashTicks;
    Vec3 dashDir = Vec3.ZERO;
    AABB prevBox;
    final Map<UUID, Long> lastHit = new HashMap<>();

    /** Ability currently selected on the client (index into {@link Power}); used for the particle theme. */
    int selected;

    /** Active Great Tsunami flood, if any. */
    Flood flood;

    boolean isActive(Power power) {
        return activeUntil[power.ordinal()] > 0;
    }

    boolean anyActive() {
        for (long l : activeUntil) {
            if (l > 0) return true;
        }
        return false;
    }
}
