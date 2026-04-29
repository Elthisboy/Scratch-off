package com.elthisboy.scratchoff.screen;

import com.elthisboy.scratchoff.item.TicketTier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Environment(EnvType.CLIENT)
public class ScratchTicketScreen extends Screen {

    // ── Claves NBT ────────────────────────────────────────────────────────────
    private static final String NBT_PRIZES = "ScratchPrizes";
    private static final String NBT_MASK   = "ScratchMask";
    private static final String NBT_DONE   = "ScratchDone";
    private static final String NBT_TIER   = "ScratchTier";

    // ── Layout ────────────────────────────────────────────────────────────────
    private static final int GUI_W      = 280;
    private static final int GUI_H      = 220;
    private static final int NUM_PANELS = 3;
    private static final int PANEL_SIZE = 68;
    private static final int PANEL_GAP  = 14;
    private static final int PANEL_TOP  = 68;
    private static final int CELL            = 4;
    private static final int COLS            = PANEL_SIZE / CELL;
    private static final int ROWS            = PANEL_SIZE / CELL;
    private static final float REVEAL_THRESHOLD = 0.72f;
    private static final int   BRUSH_RADIUS     = 3;

    // ── Tema visual por tier ──────────────────────────────────────────────────
    private record TierTheme(
        int bgBody,         // fondo principal del ticket
        int bgHeader,       // franja superior
        int bgFooter,       // franja inferior
        int borderOuter,    // borde exterior
        int borderInner,    // borde interior / filete dorado
        int titleColor,     // color del titulo
        int panelBorder,    // borde de cada panel sin revelar
        int panelBorderAlt, // borde interior panel sin revelar
        String titleKey     // clave de traduccion del titulo
    ) {}

    private static TierTheme themeFor(TicketTier tier) {
        return switch (tier) {
            case COMMON -> new TierTheme(
                0xFF2A3020,  // verde oscuro
                0xFF0D1A0A,  // header casi negro-verde
                0xFF0D1A0A,
                0xFF557733,  // borde verde
                0xFF88BB44,  // filete verde claro
                0xFF99DD44,  // titulo verde lima
                0xFF4A7730,
                0xFF88BB44,
                "screen.scratch-off.title.common"
            );
            case RARE -> new TierTheme(
                0xFF1A2035,  // azul noche
                0xFF0A0D20,
                0xFF0A0D20,
                0xFF335599,  // borde azul
                0xFF4488EE,  // filete azul claro
                0xFF66AAFF,  // titulo azul
                0xFF224488,
                0xFF4488EE,
                "screen.scratch-off.title.rare"
            );
            case EPIC -> new TierTheme(
                0xFF25102E,  // purpura oscuro
                0xFF120818,
                0xFF120818,
                0xFF882299,  // borde purpura
                0xFFCC44EE,  // filete magenta
                0xFFFF66FF,  // titulo magenta brillante
                0xFF771188,
                0xFFCC44EE,
                "screen.scratch-off.title.epic"
            );
        };
    }

    // ── Estado ────────────────────────────────────────────────────────────────
    private final Hand       hand;
    private final ItemStack  stack;
    private final TicketTier tier;
    private final TierTheme  theme;
    private int guiLeft, guiTop;

    private final LotteryPrize[] prizes        = new LotteryPrize[NUM_PANELS];
    private final boolean[][][]  mask           = new boolean[NUM_PANELS][ROWS][COLS];
    private final int[]          scratchedCells = new int[NUM_PANELS];
    private final float[]        scratchProgress= new float[NUM_PANELS];
    private final boolean[]      revealed       = new boolean[NUM_PANELS];

    private boolean wonTriple = false; // solo true en victoria real (triple match)
    private boolean rewardGiven = false;
    private boolean allRevealed = false;

    private Text resultText  = null;
    private int  resultColor = 0xFFFFFFFF;
    private int  resultTimer = 0;
    private int  winAnimTimer= 0;

    private double lastMx = -1, lastMy = -1;
    private float  scratchSpeed  = 0f;
    private int    soundCooldown = 0;
    private int    jitterTick    = 0;

    private static final class Particle {
        float x, y, vx, vy, life, decay, gravity;
        int color, size;
        Particle(float x, float y, float vx, float vy, int color) {
            this(x, y, vx, vy, color, 0.06f, 0.18f, 3);
        }
        Particle(float x, float y, float vx, float vy, int color, float decay, float gravity, int size) {
            this.x=x; this.y=y; this.vx=vx; this.vy=vy; this.life=1f;
            this.color=color; this.decay=decay; this.gravity=gravity; this.size=size;
        }
    }
    private final List<Particle> particles = new ArrayList<>();

    // ── Fuegos artificiales ───────────────────────────────────────────────────
    private int fireworkSchedule = 0;  // ticks hasta el siguiente burst
    private int fireworkBursts   = 0;  // bursts que aún quedan por disparar
    private final Random fwRng   = new Random();

    // ── Constructor ───────────────────────────────────────────────────────────

    public ScratchTicketScreen(Hand hand, ItemStack stack, TicketTier tier) {
        super(Text.translatable("screen.scratch-off.ticket"));
        this.hand  = hand;
        this.tier  = tier;
        this.theme = themeFor(tier);

        // Separar 1 ticket del stack para que su NBT sea independiente
        if (stack.getCount() > 1) {
            ItemStack single = stack.copyWithCount(1);
            NbtComponent existing = single.get(DataComponentTypes.CUSTOM_DATA);
            if (existing != null) {
                NbtCompound cleaned = existing.copyNbt();
                cleaned.remove(NBT_PRIZES);
                cleaned.remove(NBT_MASK);
                cleaned.remove(NBT_DONE);
                if (cleaned.isEmpty()) single.remove(DataComponentTypes.CUSTOM_DATA);
                else single.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(cleaned));
            }
            this.stack = single;
            stack.decrement(1);
        } else {
            this.stack = stack;
        }

        NbtCompound nbt = getScratchData();

        if (nbt.contains(NBT_PRIZES) && nbt.contains(NBT_MASK) && nbt.getByte(NBT_DONE) != 1) {
            loadFromNbt(nbt);
        } else {
            Random rng = new Random();
            for (int i = 0; i < NUM_PANELS; i++) {
                prizes[i] = LotteryPrize.getRandom(rng, tier);
                for (int r = 0; r < ROWS; r++)
                    for (int c = 0; c < COLS; c++)
                        mask[i][r][c] = false;
            }
            saveToNbt();
        }
    }

    public static void open(Hand hand, ItemStack stack, TicketTier tier) {
        MinecraftClient.getInstance().setScreen(new ScratchTicketScreen(hand, stack, tier));
    }

    // ── Persistencia NBT ──────────────────────────────────────────────────────

    private NbtCompound getScratchData() {
        NbtComponent c = stack.get(DataComponentTypes.CUSTOM_DATA);
        return c != null ? c.copyNbt() : new NbtCompound();
    }

    private void setScratchData(NbtCompound nbt) {
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
    }

    private void saveToNbt() {
        NbtCompound nbt = getScratchData();
        nbt.putString(NBT_TIER, tier.name());

        NbtList prizeList = new NbtList();
        for (LotteryPrize p : prizes) prizeList.add(NbtString.of(p.cfg.itemId));
        nbt.put(NBT_PRIZES, prizeList);

        NbtList maskList = new NbtList();
        for (int i = 0; i < NUM_PANELS; i++) maskList.add(NbtString.of(encodeMask(mask[i])));
        nbt.put(NBT_MASK, maskList);

        setScratchData(nbt);
    }

    private void markDoneInNbt() {
        NbtCompound nbt = getScratchData();
        nbt.putByte(NBT_DONE, (byte) 1);
        setScratchData(nbt);
    }

    private void consumeTicket() {
        assert this.client != null && this.client.player != null;
        if (this.client.player.isCreative()) return;
        this.stack.setCount(0);
    }

    private void loadFromNbt(NbtCompound nbt) {
        NbtList prizeList = nbt.getList(NBT_PRIZES, 8);
        NbtList maskList  = nbt.getList(NBT_MASK,   8);

        for (int i = 0; i < NUM_PANELS; i++) {
            String itemId = (i < prizeList.size()) ? prizeList.getString(i) : "";
            prizes[i] = LotteryPrize.findByItemId(itemId, tier);

            String encoded = (i < maskList.size()) ? maskList.getString(i) : "";
            mask[i] = decodeMask(encoded);

            scratchedCells[i] = 0;
            for (int r = 0; r < ROWS; r++)
                for (int c = 0; c < COLS; c++)
                    if (mask[i][r][c]) scratchedCells[i]++;
            scratchProgress[i] = (float) scratchedCells[i] / (ROWS * COLS);
            if (scratchProgress[i] >= REVEAL_THRESHOLD) revealed[i] = true;
        }

        boolean allDone = true;
        for (boolean r : revealed) if (!r) { allDone = false; break; }
        if (allDone) { allRevealed = true; rewardGiven = true; }
    }

    // ── Codificación de máscara ───────────────────────────────────────────────

    private static String encodeMask(boolean[][] m) {
        int bits = ROWS * COLS;
        byte[] bytes = new byte[(bits + 7) / 8];
        int idx = 0;
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < COLS; c++) {
                if (m[r][c]) bytes[idx / 8] |= (byte)(1 << (idx % 8));
                idx++;
            }
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b & 0xFF));
        return sb.toString();
    }

    private static boolean[][] decodeMask(String hex) {
        boolean[][] m = new boolean[ROWS][COLS];
        if (hex == null || hex.isEmpty()) return m;
        int byteCount = (ROWS * COLS + 7) / 8;
        byte[] bytes = new byte[byteCount];
        for (int i = 0; i < Math.min(hex.length() / 2, byteCount); i++)
            bytes[i] = (byte) Integer.parseInt(hex.substring(i*2, i*2+2), 16);
        int idx = 0;
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < COLS; c++) {
                m[r][c] = (bytes[idx / 8] & (1 << (idx % 8))) != 0;
                idx++;
            }
        return m;
    }

    // ── Ciclo de vida ─────────────────────────────────────────────────────────

    @Override protected void init() {
        super.init();
        guiLeft = (this.width  - GUI_W) / 2;
        guiTop  = (this.height - GUI_H) / 2;
    }

    private boolean isFireworksActive() {
        return wonTriple && (fireworkBursts > 0 || !particles.isEmpty());
    }

    @Override public boolean shouldPause()      { return false; }
    @Override public boolean shouldCloseOnEsc() { return !isFireworksActive(); }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, this.width, this.height, 0x60000000);
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (allRevealed && !isFireworksActive() && (btn == 1 || isOutsideTicket(mx, my))) { this.close(); return true; }
        if (btn == 0) { lastMx = mx; lastMy = my; handleScratch(mx, my); }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (btn == 0) handleScratch(mx, my);
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    public void mouseMoved(double mx, double my) {
        if (lastMx < 0) { lastMx = mx; lastMy = my; }
        super.mouseMoved(mx, my);
    }

    private boolean isOutsideTicket(double mx, double my) {
        return mx < guiLeft || mx > guiLeft + GUI_W || my < guiTop || my > guiTop + GUI_H;
    }

    private void handleScratch(double mx, double my) {
        if (allRevealed) return;
        double ddx = mx - lastMx, ddy = my - lastMy;
        float dist = (float) Math.sqrt(ddx*ddx + ddy*ddy);
        scratchSpeed = dist;
        lastMx = mx; lastMy = my;
        if (dist < 1.5f) return;
        jitterTick++;
        for (int i = 0; i < NUM_PANELS; i++) {
            if (!revealed[i] && isOverPanel(mx, my, i)) {
                scratchPanel(i, (float)mx, (float)my, dist);
                break;
            }
        }
    }

    private boolean isOverPanel(double mx, double my, int idx) {
        int px = panelX(idx), py = panelY();
        return mx >= px && mx < px + PANEL_SIZE && my >= py && my < py + PANEL_SIZE;
    }

    private void scratchPanel(int idx, float mx, float my, float speed) {
        int px = panelX(idx), py = panelY();
        int centerCol = (int)((mx - px) / CELL);
        int centerRow = (int)((my - py) / CELL);
        int radius = BRUSH_RADIUS + (speed > 8f ? 1 : 0) + (speed > 15f ? 1 : 0);

        boolean newCell = false;
        for (int dr = -radius; dr <= radius; dr++) {
            for (int dc = -radius; dc <= radius; dc++) {
                if (dr*dr + dc*dc > radius*radius) continue;
                int r = centerRow + dr, c = centerCol + dc;
                if (r < 0 || r >= ROWS || c < 0 || c >= COLS) continue;
                if (!mask[idx][r][c]) { mask[idx][r][c] = true; scratchedCells[idx]++; newCell = true; }
            }
        }

        if (newCell) {
            scratchProgress[idx] = (float) scratchedCells[idx] / (ROWS * COLS);
            spawnParticles(mx, my, prizes[idx].color(), Math.max(4, Math.min(20, (int)(speed * 1.5f))));

            if (soundCooldown <= 0) {
                assert this.client != null;
                float pitch = Math.min(1.8f, Math.max(0.6f, 0.8f + (speed / 30f) * 0.6f));
                this.client.getSoundManager().play(
                    PositionedSoundInstance.master(SoundEvents.BLOCK_GRAVEL_HIT, pitch, 0.55f));
                soundCooldown = Math.max(1, (int)(6 - speed / 5f));
            }

            saveToNbt();

            if (scratchProgress[idx] >= REVEAL_THRESHOLD && !revealed[idx]) {
                revealed[idx] = true;
                assert this.client != null;
                this.client.getSoundManager().play(
                    PositionedSoundInstance.master(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 0.9f));
                checkAllRevealed();
            }
        }
    }

    private void spawnParticles(float mx, float my, int baseColor, int count) {
        Random rng = new Random();
        for (int i = 0; i < count; i++)
            particles.add(new Particle(mx, my, (rng.nextFloat()-.5f)*5f, (rng.nextFloat()-.9f)*5f, baseColor));
        for (int i = 0; i < count/2; i++)
            particles.add(new Particle(mx, my, (rng.nextFloat()-.5f)*3f, (rng.nextFloat()-.6f)*2f, 0xFFCCCCCC));
    }

    // ── Fuegos artificiales ───────────────────────────────────────────────────

    /** Arranca la secuencia de fuegos artificiales según el tier ganador. */
    private boolean fireworkSmall = false;

    private void startFireworks(TicketTier t, boolean small) {
        fireworkSmall = small;
        fireworkBursts = small
            ? switch (t) { case COMMON -> 1; case RARE -> 2; case EPIC -> 3; }
            : switch (t) { case COMMON -> 2; case RARE -> 3; case EPIC -> 5; };
        fireworkSchedule = 1;
    }

    private void launchNextFireworkBurst() {
        if (fireworkBursts <= 0) return;
        fireworkBursts--;

        float cx = guiLeft + 20 + fwRng.nextFloat() * (GUI_W - 40);
        float cy = guiTop  + 10 + fwRng.nextFloat() * (GUI_H * 0.7f);

        switch (tier) {
            case COMMON -> spawnCommonBurst(cx, cy);
            case RARE   -> spawnRareBurst(cx, cy);
            case EPIC   -> spawnEpicBurst(cx, cy);
        }

        fireworkSchedule = 2 + fwRng.nextInt(3);
    }

    /** COMMON: explosión circular verde-lima con chispas blancas, sencilla. */
    private void spawnCommonBurst(float cx, float cy) {
        int[] palette = {0xFF66DD22, 0xFF99FF44, 0xFFBBFF77, 0xFFFFFFAA};
        int main = fireworkSmall ? 20 : 40;
        int spark = fireworkSmall ? 8 : 20;
        int sz = fireworkSmall ? 3 : 4;
        float spd = fireworkSmall ? 2.5f : 5f;
        for (int i = 0; i < main; i++) {
            float angle = (float)(fwRng.nextFloat() * Math.PI * 2);
            float speed = (fireworkSmall ? 1f : 1.5f) + fwRng.nextFloat() * spd;
            int col = palette[fwRng.nextInt(palette.length)];
            particles.add(new Particle(cx, cy, (float)Math.cos(angle)*speed, (float)Math.sin(angle)*speed, col, 0.022f, 0.06f, sz));
        }
        for (int i = 0; i < spark; i++) {
            float angle = (float)(fwRng.nextFloat() * Math.PI * 2);
            float speed = (fireworkSmall ? 2f : 3f) + fwRng.nextFloat() * (fireworkSmall ? 2f : 4f);
            particles.add(new Particle(cx, cy, (float)Math.cos(angle)*speed, (float)Math.sin(angle)*speed, 0xFFFFFFCC, 0.04f, 0.1f, 2));
        }
        assert client != null;
        client.getSoundManager().play(PositionedSoundInstance.master(
            SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST, 0.9f + fwRng.nextFloat()*0.3f, fireworkSmall ? 0.45f : 0.7f));
    }

    private void spawnRareBurst(float cx, float cy) {
        int[] inner = {0xFF2288FF, 0xFF44AAFF, 0xFF66CCFF};
        int[] outer = {0xFF99DDFF, 0xFFCCEEFF, 0xFFFFFFFF};
        int n1 = fireworkSmall ? 25 : 50;
        int n2 = fireworkSmall ? 15 : 35;
        int n3 = fireworkSmall ? 10 : 25;
        int n4 = fireworkSmall ? 6  : 15;
        int sz = fireworkSmall ? 3 : 4;
        for (int i = 0; i < n1; i++) {
            float angle = (float)(i / (double)n1 * Math.PI * 2);
            float speed = (fireworkSmall ? 1.2f : 2f) + fwRng.nextFloat() * (fireworkSmall ? 1f : 1.5f);
            particles.add(new Particle(cx, cy, (float)Math.cos(angle)*speed, (float)Math.sin(angle)*speed, inner[i % inner.length], 0.018f, 0.05f, sz));
        }
        for (int i = 0; i < n2; i++) {
            float angle = (float)(fwRng.nextFloat() * Math.PI * 2);
            float speed = (fireworkSmall ? 2f : 4f) + fwRng.nextFloat() * (fireworkSmall ? 1.5f : 3f);
            particles.add(new Particle(cx, cy, (float)Math.cos(angle)*speed, (float)Math.sin(angle)*speed, outer[fwRng.nextInt(outer.length)], 0.015f, 0.04f, 3));
        }
        for (int i = 0; i < n3; i++) {
            float vx = (fwRng.nextFloat() - 0.5f) * (fireworkSmall ? 1f : 2f);
            float vy = -fwRng.nextFloat() * (fireworkSmall ? 0.8f : 1.5f);
            particles.add(new Particle(cx, cy, vx, vy, 0xFFAADDFF, 0.012f, 0.09f, 2));
        }
        for (int i = 0; i < n4; i++) {
            float angle = (float)(fwRng.nextFloat() * Math.PI * 2);
            float speed = (fireworkSmall ? 3f : 6f) + fwRng.nextFloat() * (fireworkSmall ? 2f : 3f);
            particles.add(new Particle(cx, cy, (float)Math.cos(angle)*speed, (float)Math.sin(angle)*speed, 0xFFFFFFFF, 0.035f, 0.07f, 2));
        }
        assert client != null;
        client.getSoundManager().play(PositionedSoundInstance.master(
            SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST, 1.1f + fwRng.nextFloat()*0.3f, fireworkSmall ? 0.5f : 0.85f));
        client.getSoundManager().play(PositionedSoundInstance.master(
            SoundEvents.ENTITY_FIREWORK_ROCKET_TWINKLE, 1f, fireworkSmall ? 0.35f : 0.6f));
    }

    private void spawnEpicBurst(float cx, float cy) {
        int[] magenta = {0xFFFF00FF, 0xFFFF44FF, 0xFFFF88FF, 0xFFFFAAFF};
        int[] gold    = {0xFFFFDD00, 0xFFFFBB00, 0xFFFF8800, 0xFFFFEE88};
        int n1 = fireworkSmall ? 30 : 65;
        int n2 = fireworkSmall ? 25 : 55;
        int n3 = fireworkSmall ? 15 : 40;
        int n4 = fireworkSmall ? 12 : 35;
        int sz = fireworkSmall ? 3 : 5;
        for (int i = 0; i < n1; i++) {
            float angle = (float)(fwRng.nextFloat() * Math.PI * 2);
            float speed = (fireworkSmall ? 1.5f : 2.5f) + fwRng.nextFloat() * (fireworkSmall ? 2.5f : 4.5f);
            particles.add(new Particle(cx, cy, (float)Math.cos(angle)*speed, (float)Math.sin(angle)*speed, magenta[fwRng.nextInt(magenta.length)], 0.014f, 0.045f, sz));
        }
        float cx2 = cx + fwRng.nextFloat()*30 - 15;
        float cy2 = cy + fwRng.nextFloat()*20 - 10;
        for (int i = 0; i < n2; i++) {
            float angle = (float)(fwRng.nextFloat() * Math.PI * 2);
            float speed = (fireworkSmall ? 1.2f : 2f) + fwRng.nextFloat() * (fireworkSmall ? 3f : 5f);
            particles.add(new Particle(cx2, cy2, (float)Math.cos(angle)*speed, (float)Math.sin(angle)*speed, gold[fwRng.nextInt(gold.length)], 0.013f, 0.05f, sz));
        }
        for (int i = 0; i < n3; i++) {
            float angle = (float)(fwRng.nextFloat() * Math.PI * 2);
            float speed = (fireworkSmall ? 4f : 7f) + fwRng.nextFloat() * (fireworkSmall ? 3f : 5f);
            particles.add(new Particle(cx, cy, (float)Math.cos(angle)*speed, (float)Math.sin(angle)*speed, 0xFFFFFFFF, 0.03f, 0.06f, 2));
        }
        for (int i = 0; i < n4; i++) {
            float vx = (fwRng.nextFloat() - 0.5f) * (fireworkSmall ? 2f : 3f);
            float vy = -fwRng.nextFloat() * (fireworkSmall ? 1.2f : 2f) + 1f;
            int col = (fwRng.nextBoolean()) ? 0xFFFF88FF : 0xFFFFDD44;
            particles.add(new Particle(cx + fwRng.nextFloat()*40-20, cy + fwRng.nextFloat()*20-10, vx, vy, col, 0.010f, 0.1f, fireworkSmall ? 2 : 3));
        }
        assert client != null;
        client.getSoundManager().play(PositionedSoundInstance.master(
            SoundEvents.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 0.9f, fireworkSmall ? 0.5f : 1f));
        client.getSoundManager().play(PositionedSoundInstance.master(
            SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST, 1.3f, fireworkSmall ? 0.4f : 0.9f));
        if (!fireworkSmall) client.getSoundManager().play(PositionedSoundInstance.master(
            SoundEvents.ENTITY_FIREWORK_ROCKET_TWINKLE_FAR, 1f, 0.7f));
    }

    private void checkAllRevealed() {
        for (boolean r : revealed) if (!r) return;
        allRevealed = true;
        if (!rewardGiven) { rewardGiven = true; giveReward(); }
    }

    private void giveReward() {
        assert this.client != null && this.client.player != null;
        String playerName = this.client.player.getName().getString();

        boolean tripleMatch = prizes[0].equals(prizes[1]) && prizes[1].equals(prizes[2]);
        LotteryPrize pairPrize = null;
        if (!tripleMatch) {
            if      (prizes[0].equals(prizes[1]) && !prizes[0].isLose()) pairPrize = prizes[0];
            else if (prizes[0].equals(prizes[2]) && !prizes[0].isLose()) pairPrize = prizes[0];
            else if (prizes[1].equals(prizes[2]) && !prizes[1].isLose()) pairPrize = prizes[1];
        }

        if (tripleMatch && !prizes[0].isLose()) {
            LotteryPrize prize = prizes[0];
            if (prize.cfg.hasCommand())
                this.client.player.networkHandler.sendChatCommand(
                    prize.cfg.command.replace("%player%", playerName));
            resultText  = Text.translatable("result.scratch-off.win", Text.translatable(prize.nameKey()));
            resultColor = prize.color();
            winAnimTimer = 200;
            this.client.getSoundManager().play(
                PositionedSoundInstance.master(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f));
            wonTriple = true;
            startFireworks(tier, false);

        } else if (pairPrize != null && pairPrize.cfg.hasConsolationCommand()) {
            this.client.player.networkHandler.sendChatCommand(
                pairPrize.cfg.consolationCommand.replace("%player%", playerName));
            resultText  = Text.translatable("result.scratch-off.consolation",
                            Text.translatable(pairPrize.nameKey()));
            resultColor = pairPrize.color();
            winAnimTimer = 80;
            this.client.getSoundManager().play(
                PositionedSoundInstance.master(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f));
            startFireworks(tier, true);

        } else {
            resultText  = Text.translatable("result.scratch-off.lose");
            resultColor = 0xFFFF6666;
            this.client.getSoundManager().play(
                PositionedSoundInstance.master(SoundEvents.ENTITY_VILLAGER_NO, 1f, 0.9f));
        }

        resultTimer = 260;
        markDoneInNbt();
        consumeTicket();
    }

    // ── Tick ──────────────────────────────────────────────────────────────────

    @Override
    public void tick() {
        super.tick();
        particles.removeIf(p -> p.life <= 0f);
        for (Particle p : particles) { p.x+=p.vx; p.y+=p.vy; p.vy+=p.gravity; p.vx*=0.97f; p.life-=p.decay; }
        if (resultTimer   > 0) resultTimer--;
        if (winAnimTimer  > 0) winAnimTimer--;
        if (soundCooldown > 0) soundCooldown--;
        if (jitterTick    > 0) jitterTick = Math.max(0, jitterTick - 2);
        scratchSpeed *= 0.85f;

        // Fuegos artificiales
        if (fireworkBursts > 0) {
            fireworkSchedule--;
            if (fireworkSchedule <= 0) launchNextFireworkBurst();
        }
    }

    // ── Render ────────────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx, mouseX, mouseY, delta);
        drawTicket(ctx);
        drawHeader(ctx);
        drawPanels(ctx, mouseX, mouseY);
        drawResultMessage(ctx);
        drawParticles(ctx);
        drawCloseHint(ctx);
        super.render(ctx, mouseX, mouseY, delta);
    }

    private void drawTicket(DrawContext ctx) {
        int l=guiLeft, t=guiTop, w=GUI_W, h=GUI_H;

        // Sombra
        ctx.fill(l+5, t+5, l+w+5, t+h+5, 0x88000000);
        // Borde exterior e interior del tier
        ctx.fill(l-4, t-4, l+w+4, t+h+4, theme.borderOuter());
        ctx.fill(l-2, t-2, l+w+2, t+h+2, theme.borderInner());
        // Cuerpo
        ctx.fill(l, t, l+w, t+h, theme.bgBody());

        // Animacion de victoria
        if (winAnimTimer > 0) {
            float pulse = (float)Math.sin(winAnimTimer * 0.3f) * 0.5f + 0.5f;
            ctx.fill(l, t, l+w, t+h, ((int)(pulse * 80) << 24) | (theme.titleColor() & 0x00FFFFFF));
        }

        // Header y footer
        ctx.fill(l, t,      l+w, t+55,   theme.bgHeader());
        ctx.fill(l, t,      l+w, t+2,    theme.borderInner());
        ctx.fill(l, t+55,   l+w, t+57,   theme.borderInner());
        ctx.fill(l, t+h-35, l+w, t+h,    theme.bgFooter());
        ctx.fill(l, t+h-37, l+w, t+h-35, theme.borderInner());

        // Ornamentos de esquina
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("✦"), l+14,   t+8, theme.borderInner());
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("✦"), l+w-14, t+8, theme.borderInner());
    }

    private void drawHeader(DrawContext ctx) {
        int cx = guiLeft + GUI_W / 2;
        ctx.drawCenteredTextWithShadow(textRenderer,
            Text.translatable(theme.titleKey()), cx, guiTop+10, theme.titleColor());
        ctx.drawCenteredTextWithShadow(textRenderer,
            Text.translatable("screen.scratch-off.subtitle"), cx, guiTop+26, 0xFFCCDDFF);
        ctx.drawCenteredTextWithShadow(textRenderer,
            Text.translatable("screen.scratch-off.instruction"), cx, guiTop+39, 0xFF8899CC);
    }

    private void drawPanels(DrawContext ctx, int mouseX, int mouseY) {
        for (int i = 0; i < NUM_PANELS; i++) {
            int px = panelX(i), py = panelY();
            LotteryPrize prize = prizes[i];

            boolean panelScratching = jitterTick > 0
                && mouseX >= px && mouseX < px + PANEL_SIZE
                && mouseY >= py && mouseY < py + PANEL_SIZE;
            int bjx = (panelScratching && (jitterTick % 4 < 2))  ? (jitterTick % 2 == 0 ? 1 : -1) : 0;
            int bjy = (panelScratching && (jitterTick % 4 >= 2)) ? (jitterTick % 2 == 0 ? 1 : -1) : 0;

            int borderColor = revealed[i] ? (prize.color() | 0xFF000000) : theme.panelBorder();
            int innerBorder = revealed[i] ? lighten(prize.color(), 60)   : theme.panelBorderAlt();
            ctx.fill(px-4+bjx, py-4+bjy, px+PANEL_SIZE+4+bjx, py+PANEL_SIZE+4+bjy, borderColor);
            ctx.fill(px-2+bjx, py-2+bjy, px+PANEL_SIZE+2+bjx, py+PANEL_SIZE+2+bjy, innerBorder);

            if (revealed[i]) {
                ctx.fill(px, py, px+PANEL_SIZE, py+PANEL_SIZE, darken(prize.color(), 120));
                ctx.fill(px, py, px+PANEL_SIZE/2, py+PANEL_SIZE/2, 0x22FFFFFF);
                ctx.drawItem(new ItemStack(prize.item), px + PANEL_SIZE/2 - 8, py + PANEL_SIZE/2 - 16);
                ctx.drawItemInSlot(textRenderer, new ItemStack(prize.item), px + PANEL_SIZE/2 - 8, py + PANEL_SIZE/2 - 16);
                ctx.drawCenteredTextWithShadow(textRenderer,
                    Text.translatable(prize.nameKey()),
                    px + PANEL_SIZE/2, py + PANEL_SIZE/2 + 6,
                    prize.color() | 0xFF000000);
            } else {
                ctx.fill(px, py, px+PANEL_SIZE, py+PANEL_SIZE, 0xFF3A3A3A);

                if (scratchedCells[i] > 0)
                    drawItemInScratchedCells(ctx, i, px, py, prize);

                drawScratchLayerMask(ctx, i, px, py, prize.color(),
                    jitterTick > 0 && mouseX >= px && mouseX < px + PANEL_SIZE
                                   && mouseY >= py && mouseY < py + PANEL_SIZE);

                if (scratchProgress[i] > 0f) {
                    int barW = PANEL_SIZE - 8, barX = px+4, barY = py+PANEL_SIZE-6;
                    ctx.fill(barX, barY, barX+barW, barY+3, 0x88000000);
                    ctx.fill(barX, barY, barX+(int)(scratchProgress[i]*barW), barY+3, theme.titleColor());
                }
            }

            boolean hover = !revealed[i] && mouseX>=px && mouseX<px+PANEL_SIZE
                                         && mouseY>=py && mouseY<py+PANEL_SIZE;
            Text label; int labelColor;
            if (revealed[i]) {
                label = Text.translatable("panel.scratch-off.revealed"); labelColor = theme.titleColor();
            } else if (hover) {
                label = Text.translatable("panel.scratch-off.scratch");  labelColor = theme.borderInner();
            } else {
                label = Text.translatable("panel.scratch-off.touch");    labelColor = 0xFF997755;
            }
            ctx.drawCenteredTextWithShadow(textRenderer, label, px+PANEL_SIZE/2, py+PANEL_SIZE+6, labelColor);
        }
    }

    private void drawItemInScratchedCells(DrawContext ctx, int panelIdx, int px, int py, LotteryPrize prize) {
        int itemX = px + PANEL_SIZE/2 - 8;
        int itemY = py + PANEL_SIZE/2 - 16;
        int nameY = py + PANEL_SIZE/2 + 6;
        int nameCX = px + PANEL_SIZE/2;

        for (int r = 0; r < ROWS; r++) {
            int segStart = -1;
            for (int c = 0; c <= COLS; c++) {
                boolean scraped = (c < COLS) && mask[panelIdx][r][c];
                if (scraped && segStart < 0) {
                    segStart = c;
                } else if (!scraped && segStart >= 0) {
                    ctx.enableScissor(px + segStart*CELL, py + r*CELL, px + c*CELL, py + r*CELL + CELL);
                    ctx.drawItem(new ItemStack(prize.item), itemX, itemY);
                    ctx.drawCenteredTextWithShadow(textRenderer,
                        Text.translatable(prize.nameKey()), nameCX, nameY,
                        prize.color() | 0xFF000000);
                    ctx.disableScissor();
                    segStart = -1;
                }
            }
        }
    }

    private void drawScratchLayerMask(DrawContext ctx, int panelIdx,
                                      int px, int py, int prizeColor, boolean isScratching) {
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                if (mask[panelIdx][r][c]) continue;
                int cx2 = px + c*CELL, cy2 = py + r*CELL;
                int x2  = cx2 + CELL,   y2  = cy2 + CELL;
                int shade = 62 + ((r+c) % 4 == 0 ? -12 : 0) + (r % 3 == 0 ? 8 : 0) + (c % 5 == 0 ? -5 : 0);
                shade = Math.max(30, Math.min(100, shade));
                ctx.fill(cx2, cy2, x2, y2, 0xFF000000|(shade<<16)|(shade<<8)|shade);

                if (c+1 < COLS && mask[panelIdx][r][c+1])   ctx.fill(x2-1, cy2, x2, y2, 0x99FFFFFF);
                if (r+1 < ROWS && mask[panelIdx][r+1][c])   ctx.fill(cx2, y2-1, x2, y2, 0x99FFFFFF);
                ctx.fill(cx2, cy2, x2, cy2+1, 0x44FFFFFF);
                ctx.fill(cx2, cy2, cx2+1, y2, 0x33FFFFFF);
            }
        }

        if (scratchProgress[panelIdx] < 0.15f)
            ctx.drawCenteredTextWithShadow(textRenderer,
                Text.translatable("panel.scratch-off.label"),
                px + PANEL_SIZE/2, py + PANEL_SIZE/2 - 4, 0xFF999999);

        if (scratchProgress[panelIdx] > 0.05f && scratchProgress[panelIdx] < REVEAL_THRESHOLD) {
            int tintAlpha = (int)(scratchProgress[panelIdx] * 100);
            for (int r = 0; r < ROWS; r++) {
                for (int c = 0; c < COLS; c++) {
                    if (!mask[panelIdx][r][c]) continue;
                    boolean hasNeighbor =
                        (r > 0      && !mask[panelIdx][r-1][c]) ||
                        (r < ROWS-1 && !mask[panelIdx][r+1][c]) ||
                        (c > 0      && !mask[panelIdx][r][c-1]) ||
                        (c < COLS-1 && !mask[panelIdx][r][c+1]);
                    if (hasNeighbor) {
                        int ex = px + c*CELL, ey = py + r*CELL;
                        ctx.fill(ex, ey, ex+CELL, ey+CELL, (tintAlpha << 24) | (prizeColor & 0x00FFFFFF));
                    }
                }
            }
        }
    }

    private void drawResultMessage(DrawContext ctx) {
        int cx = guiLeft + GUI_W/2, ty = guiTop + GUI_H - 24;
        if (resultText != null && resultTimer > 0) {
            float fade = Math.min(1f, resultTimer / 30f);
            int color = (resultColor & 0x00FFFFFF) | ((int)(fade*255) << 24);
            ctx.drawCenteredTextWithShadow(textRenderer, resultText, cx, ty, color);
        } else if (allRevealed) {
            ctx.drawCenteredTextWithShadow(textRenderer,
                Text.translatable("screen.scratch-off.used"), cx, ty, 0xFF99AACC);
        } else {
            int done = 0; for (boolean r : revealed) if (r) done++;
            ctx.drawCenteredTextWithShadow(textRenderer,
                Text.translatable("screen.scratch-off.progress", done, NUM_PANELS), cx, ty, 0xFF997755);
        }
    }

    private void drawParticles(DrawContext ctx) {
        for (Particle p : particles) {
            int a = Math.max(0, (int)(p.life*220));
            int c = (a<<24)|(p.color&0x00FFFFFF);
            int x=(int)p.x, y=(int)p.y, s=p.size;
            ctx.fill(x, y, x+s, y+s, c);
            if (s >= 3) ctx.fill(x+1, y-1, x+2, y, (a/2)<<24|0xFFFFFF);
        }
    }

    private void drawCloseHint(DrawContext ctx) {
        String key   = allRevealed ? "screen.scratch-off.close_hint_done" : "screen.scratch-off.close_hint";
        int    color = allRevealed ? 0xFF99AACC : 0xFF446688;
        ctx.drawCenteredTextWithShadow(textRenderer,
            Text.translatable(key), guiLeft+GUI_W/2, guiTop+GUI_H+8, color);
    }

    // ── Utilidades ────────────────────────────────────────────────────────────

    private static int darken(int color, int amount) {
        int r=Math.max(0,((color>>16)&0xFF)-amount);
        int g=Math.max(0,((color>> 8)&0xFF)-amount);
        int b=Math.max(0,( color     &0xFF)-amount);
        return 0xFF000000|(r<<16)|(g<<8)|b;
    }

    private static int lighten(int color, int amount) {
        int r=Math.min(255,((color>>16)&0xFF)+amount);
        int g=Math.min(255,((color>> 8)&0xFF)+amount);
        int b=Math.min(255,( color     &0xFF)+amount);
        return 0xFF000000|(r<<16)|(g<<8)|b;
    }

    private int panelX(int idx) {
        int total = NUM_PANELS*PANEL_SIZE + (NUM_PANELS-1)*PANEL_GAP;
        return guiLeft + (GUI_W-total)/2 + idx*(PANEL_SIZE+PANEL_GAP);
    }
    private int panelY() { return guiTop + PANEL_TOP; }
}
