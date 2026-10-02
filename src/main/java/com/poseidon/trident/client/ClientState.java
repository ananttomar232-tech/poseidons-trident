package com.poseidon.trident.client;

public final class ClientState {
    public static int selected = 0;
    public static long switchedAtMillis = 0L;
    public static boolean wasHolding = false;

    public static final int[] COLORS = {0xFFD83A, 0xFF7A1A, 0x3FD0FF, 0x2E6BFF};

    private ClientState() {}
}
