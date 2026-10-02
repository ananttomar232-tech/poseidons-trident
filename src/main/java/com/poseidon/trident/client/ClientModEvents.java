package com.poseidon.trident.client;

import com.poseidon.trident.PoseidonsTridentMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = PoseidonsTridentMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModEvents {
    private ClientModEvents() {}

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(ModKeys.THUNDER_DOMAIN);
        event.register(ModKeys.CHARGE);
        event.register(ModKeys.DASH);
        event.register(ModKeys.WATER_CUBE);
        event.register(ModKeys.TSUNAMI);
        event.register(ModKeys.NEXT_ABILITY);
        event.register(ModKeys.PREVIOUS_ABILITY);
        event.register(ModKeys.USE_SELECTED);
    }
}
