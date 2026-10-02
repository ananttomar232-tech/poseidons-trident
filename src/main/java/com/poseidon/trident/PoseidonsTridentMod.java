package com.poseidon.trident;

import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(PoseidonsTridentMod.MOD_ID)
public class PoseidonsTridentMod {
    public static final String MOD_ID = "poseidonstrident";

    public PoseidonsTridentMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModItems.ITEMS.register(modBus);
        modBus.addListener(this::addToCreativeTab);

        // Touch the class so the network channel is built and registered.
        Network.init();

        // Server-side power logic (static @SubscribeEvent handlers).
        MinecraftForge.EVENT_BUS.register(PowerManager.class);
    }

    private void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(ModItems.POSEIDONS_TRIDENT.get());
        }
    }
}
