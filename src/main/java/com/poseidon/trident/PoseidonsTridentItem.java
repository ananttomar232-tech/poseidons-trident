package com.poseidon.trident;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * The item itself is just a shiny staff. All of its behaviour (passive buffs and
 * the four active powers) lives in {@link PowerManager}, which checks whether the
 * player is holding this item in either hand.
 */
public class PoseidonsTridentItem extends Item {

    public PoseidonsTridentItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.poseidonstrident.hold").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.poseidonstrident.passives").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.poseidonstrident.keys").withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.translatable("tooltip.poseidonstrident.timers").withStyle(ChatFormatting.DARK_AQUA));
    }
}
