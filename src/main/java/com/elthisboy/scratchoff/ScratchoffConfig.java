package com.elthisboy.scratchoff;

import com.elthisboy.scratchoff.item.TicketTier;
import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Configuracion del mod Scratch-Off.
 *
 * Cada tier tiene su propio archivo JSON en config/:
 *   scratch-off-common.json
 *   scratch-off-rare.json
 *   scratch-off-epic.json
 */
public class ScratchoffConfig {

    // ── Cache por tier ────────────────────────────────────────────────────────
    private static final Map<String, ScratchoffConfig> CACHE = new HashMap<>();

    /** Obtiene (y cachea) la config del tier indicado. Pasa null para precargar todos. */
    public static ScratchoffConfig get(TicketTier tier) {
        if (tier == null) {
            for (TicketTier t : TicketTier.values()) get(t);
            return null;
        }
        return CACHE.computeIfAbsent(tier.configFile, f -> load(tier));
    }

    /** Recarga todos los configs desde disco. */
    public static void reload() {
        CACHE.clear();
        for (TicketTier t : TicketTier.values()) get(t);
    }

    // ── Campos ────────────────────────────────────────────────────────────────

    /** Premios disponibles para este tier. */
    public List<PrizeConfig> prizes;

    // ── Clase PrizeConfig ─────────────────────────────────────────────────────

    public static class PrizeConfig {
        public String  itemId;
        public String  nameKey;
        public String  color;          // AARRGGBB hex sin '#'
        public int     weight;
        public boolean isLose;
        public String  command;             // comando al ganar con triple
        public String  consolationCommand;  // comando al conseguir par

        public PrizeConfig() {}

        public PrizeConfig(String itemId, String nameKey, String color,
                           int weight, boolean isLose,
                           String command, String consolationCommand) {
            this.itemId             = itemId;
            this.nameKey            = nameKey;
            this.color              = color;
            this.weight             = weight;
            this.isLose             = isLose;
            this.command            = command;
            this.consolationCommand = consolationCommand;
        }

        public int colorInt() {
            try { return (int) Long.parseLong(color, 16); }
            catch (NumberFormatException e) { return 0xFFAAAAAA; }
        }

        public boolean hasCommand()            { return command            != null && !command.isBlank(); }
        public boolean hasConsolationCommand() { return consolationCommand != null && !consolationCommand.isBlank(); }
    }

    // ── Premios por defecto por tier ──────────────────────────────────────────

    private static List<PrizeConfig> defaultPrizesFor(TicketTier tier) {
        List<PrizeConfig> list = new ArrayList<>();
        switch (tier) {

            case COMMON -> {
                // Premios modestos, probabilidades generosas
                list.add(new PrizeConfig("minecraft:iron_ingot",  "prize.scratch-off.common.iron",
                    "FFDDDDDD", 1, false,
                    "give %player% minecraft:iron_ingot 8",
                    "give %player% minecraft:iron_ingot 2"));
                list.add(new PrizeConfig("minecraft:bread",       "prize.scratch-off.common.bread",
                    "FFCC9944", 2, false,
                    "give %player% minecraft:bread 12",
                    "give %player% minecraft:bread 4"));
                list.add(new PrizeConfig("minecraft:coal",        "prize.scratch-off.common.coal",
                    "FF555555", 3, false,
                    "give %player% minecraft:coal 16",
                    "give %player% minecraft:coal 4"));
                list.add(new PrizeConfig("minecraft:skeleton_skull", "prize.scratch-off.lose",
                    "FF884444", 4, true, null, null));
            }

            case RARE -> {
                // Premios medios, probabilidades moderadas
                list.add(new PrizeConfig("minecraft:diamond",      "prize.scratch-off.rare.diamond",
                    "FF55FFFF", 1, false,
                    "give %player% minecraft:diamond 3",
                    "give %player% minecraft:diamond 1"));
                list.add(new PrizeConfig("minecraft:gold_ingot",   "prize.scratch-off.rare.gold",
                    "FFFFAA00", 2, false,
                    "give %player% minecraft:gold_ingot 8",
                    "give %player% minecraft:gold_nugget 4"));
                list.add(new PrizeConfig("minecraft:lapis_lazuli", "prize.scratch-off.rare.lapis",
                    "FF3355FF", 3, false,
                    "give %player% minecraft:lapis_lazuli 12",
                    "give %player% minecraft:lapis_lazuli 3"));
                list.add(new PrizeConfig("minecraft:skeleton_skull", "prize.scratch-off.lose",
                    "FF884444", 4, true, null, null));
            }

            case EPIC -> {
                // Premios top, probabilidades difíciles
                list.add(new PrizeConfig("minecraft:netherite_ingot", "prize.scratch-off.epic.netherite",
                    "FFAA44AA", 1, false,
                    "give %player% minecraft:netherite_ingot 2",
                    "give %player% minecraft:netherite_scrap 1"));
                list.add(new PrizeConfig("minecraft:emerald",         "prize.scratch-off.epic.emerald",
                    "FF00FF77", 2, false,
                    "give %player% minecraft:emerald 10",
                    "give %player% minecraft:emerald 2"));
                list.add(new PrizeConfig("minecraft:elytra",          "prize.scratch-off.epic.elytra",
                    "FFFF8800", 3, false,
                    "give %player% minecraft:elytra 1",
                    "give %player% minecraft:phantom_membrane 4"));
                list.add(new PrizeConfig("minecraft:skeleton_skull",  "prize.scratch-off.lose",
                    "FF884444", 5, true, null, null));
            }
        }
        return list;
    }

    // ── Carga desde disco ─────────────────────────────────────────────────────

    private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir();

    private static ScratchoffConfig load(TicketTier tier) {
        Path path = CONFIG_DIR.resolve(tier.configFile);
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        if (Files.exists(path)) {
            try (Reader r = Files.newBufferedReader(path)) {
                ScratchoffConfig cfg = gson.fromJson(r, ScratchoffConfig.class);
                if (cfg != null && cfg.prizes != null && !cfg.prizes.isEmpty()) {
                    Scratchoff.LOGGER.info("[Scratch-Off] Config cargada: " + path.getFileName());
                    return cfg;
                }
            } catch (Exception e) {
                Scratchoff.LOGGER.warn("[Scratch-Off] Error al leer " + path.getFileName()
                    + ", usando defaults: " + e.getMessage());
            }
        }

        // Crear config por defecto
        ScratchoffConfig def = new ScratchoffConfig();
        def.prizes = defaultPrizesFor(tier);
        def.save(path, gson);
        return def;
    }

    private void save(Path path, Gson gson) {
        try (Writer w = Files.newBufferedWriter(path)) {
            gson.toJson(this, w);
            Scratchoff.LOGGER.info("[Scratch-Off] Config guardada: " + path.getFileName());
        } catch (Exception e) {
            Scratchoff.LOGGER.warn("[Scratch-Off] No se pudo guardar config: " + e.getMessage());
        }
    }

    // ── Selección aleatoria ponderada ─────────────────────────────────────────

    public PrizeConfig getRandom(java.util.Random rng) {
        int total = prizes.stream().mapToInt(p -> p.weight).sum();
        if (total <= 0) return prizes.get(prizes.size() - 1);
        int roll = rng.nextInt(total), acc = 0;
        for (PrizeConfig p : prizes) {
            acc += p.weight;
            if (roll < acc) return p;
        }
        return prizes.get(prizes.size() - 1);
    }
}
