package com.poseidon.trident;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, PoseidonsTridentMod.MOD_ID);

    public static final RegistryObject<Item> POSEIDONS_TRIDENT = ITEMS.register("poseidons_trident",
            () -> new PoseidonsTridentItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.EPIC)
                    .fireResistant()));

    private ModItems() {}
}
