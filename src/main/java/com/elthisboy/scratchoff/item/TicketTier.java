package com.elthisboy.scratchoff.item;

/**
 * Los tres tiers de ticket disponibles.
 * Cada tier apunta a su propio archivo de config JSON.
 */
public enum TicketTier {

    COMMON ("scratch-off-common.json",  "item.scratch-off.scratch_ticket_common"),
    RARE   ("scratch-off-rare.json",    "item.scratch-off.scratch_ticket_rare"),
    EPIC   ("scratch-off-epic.json",    "item.scratch-off.scratch_ticket_epic");

    /** Nombre del archivo JSON en la carpeta config/ */
    public final String configFile;
    /** Clave de traducción del nombre del item */
    public final String nameKey;

    TicketTier(String configFile, String nameKey) {
        this.configFile = configFile;
        this.nameKey    = nameKey;
    }
}
