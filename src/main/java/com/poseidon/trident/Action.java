package com.poseidon.trident;

/** Things the client can ask the server to do (sent as the ordinal in {@link ActionPacket}). */
public enum Action {
    THUNDER_DOMAIN,
    CHARGE,
    DASH,
    WATER_CUBE,
    TSUNAMI,
    // --- ability selector (added later, kept at the end so old ordinals don't change)
    SELECT_THUNDER_DOMAIN,
    SELECT_GOD_SPEED,
    SELECT_WATER_CUBE,
    SELECT_TSUNAMI,
    GOD_SPEED_SMART
}
