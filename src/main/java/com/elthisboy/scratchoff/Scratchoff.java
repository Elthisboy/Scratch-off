package com.elthisboy.scratchoff;

import com.elthisboy.scratchoff.item.ModItems;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.ItemGroups;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Scratchoff implements ModInitializer {
    public static final String MOD_ID = "scratch-off";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        // Cargar configs antes de registrar items
        ScratchoffConfig.get(null); // inicializa el cache

        ModItems.registerItems();

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> {
            entries.add(ModItems.SCRATCH_TICKET_COMMON);
            entries.add(ModItems.SCRATCH_TICKET_RARE);
            entries.add(ModItems.SCRATCH_TICKET_EPIC);
        });

        LOGGER.info("[Scratch-Off] Mod inicializado correctamente!");
    }
}
