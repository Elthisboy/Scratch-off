package com.elthisboy.scratchoff.screen;

import com.elthisboy.scratchoff.ScratchoffConfig;
import com.elthisboy.scratchoff.item.TicketTier;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

public class LotteryPrize {

    public final ScratchoffConfig.PrizeConfig cfg;
    public final Item item;

    public LotteryPrize(ScratchoffConfig.PrizeConfig cfg) {
        this.cfg  = cfg;
        this.item = resolveItem(cfg.itemId);
    }

    public String  nameKey() { return cfg.nameKey; }
    public int     color()   { return cfg.colorInt(); }
    public boolean isLose()  { return cfg.isLose; }

    /** Selección aleatoria ponderada del pool del tier indicado. */
    public static LotteryPrize getRandom(java.util.Random rng, TicketTier tier) {
        ScratchoffConfig.PrizeConfig chosen = ScratchoffConfig.get(tier).getRandom(rng);
        return new LotteryPrize(chosen);
    }

    /** Busca un prize por itemId dentro del pool del tier. */
    public static LotteryPrize findByItemId(String itemId, TicketTier tier) {
        ScratchoffConfig cfg = ScratchoffConfig.get(tier);
        for (ScratchoffConfig.PrizeConfig p : cfg.prizes)
            if (p.itemId.equals(itemId))
                return new LotteryPrize(p);
        return new LotteryPrize(cfg.prizes.get(0));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LotteryPrize)) return false;
        return cfg.itemId.equals(((LotteryPrize) o).cfg.itemId);
    }

    @Override
    public int hashCode() { return cfg.itemId.hashCode(); }

    private static Item resolveItem(String itemId) {
        try {
            Identifier id = Identifier.of(itemId);
            if (Registries.ITEM.containsId(id)) return Registries.ITEM.get(id);
        } catch (Exception ignored) {}
        return Registries.ITEM.get(Identifier.of("minecraft:barrier"));
    }
}
