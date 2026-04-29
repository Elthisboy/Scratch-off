package com.elthisboy.scratchoff.item;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

public class ScratchTicketItem extends Item {

    private final TicketTier tier;

    public ScratchTicketItem(Settings settings, TicketTier tier) {
        super(settings);
        this.tier = tier;
    }

    public TicketTier getTier() { return tier; }

    /** Color del nombre segun el tier (se ve en la mano y en el inventario). */
    @Override
    public Text getName(ItemStack stack) {
        Formatting color = switch (tier) {
            case COMMON -> Formatting.GREEN;
            case RARE   -> Formatting.AQUA;
            case EPIC   -> Formatting.LIGHT_PURPLE;
        };
        return Text.translatable(tier.nameKey).setStyle(Style.EMPTY.withColor(color).withItalic(false));
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient()) {
            openScreen(hand, stack, tier);
        }
        return TypedActionResult.success(stack);
    }

    @net.fabricmc.api.Environment(net.fabricmc.api.EnvType.CLIENT)
    private void openScreen(Hand hand, ItemStack stack, TicketTier tier) {
        try {
            Class<?> screenClass = Class.forName(
                "com.elthisboy.scratchoff.screen.ScratchTicketScreen");
            java.lang.reflect.Method openMethod =
                screenClass.getMethod("open", Hand.class, ItemStack.class, TicketTier.class);
            openMethod.invoke(null, hand, stack, tier);
        } catch (Exception e) {
            throw new RuntimeException("[Scratch-Off] No se pudo abrir ScratchTicketScreen", e);
        }
    }
}
