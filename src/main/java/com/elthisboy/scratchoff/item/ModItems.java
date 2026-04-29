package com.elthisboy.scratchoff.item;

import com.elthisboy.scratchoff.Scratchoff;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModItems {

    public static final Item SCRATCH_TICKET_COMMON =
            new ScratchTicketItem(new Item.Settings().maxCount(16), TicketTier.COMMON);

    public static final Item SCRATCH_TICKET_RARE =
            new ScratchTicketItem(new Item.Settings().maxCount(16), TicketTier.RARE);

    public static final Item SCRATCH_TICKET_EPIC =
            new ScratchTicketItem(new Item.Settings().maxCount(16), TicketTier.EPIC);

    public static void registerItems() {
        Registry.register(Registries.ITEM,
            Identifier.of(Scratchoff.MOD_ID, "scratch_ticket_common"), SCRATCH_TICKET_COMMON);
        Registry.register(Registries.ITEM,
            Identifier.of(Scratchoff.MOD_ID, "scratch_ticket_rare"),   SCRATCH_TICKET_RARE);
        Registry.register(Registries.ITEM,
            Identifier.of(Scratchoff.MOD_ID, "scratch_ticket_epic"),   SCRATCH_TICKET_EPIC);

        Scratchoff.LOGGER.info("[Scratch-Off] Items registrados (common, rare, epic).");
    }
}
