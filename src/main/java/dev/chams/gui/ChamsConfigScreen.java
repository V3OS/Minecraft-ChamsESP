package dev.chams.gui;

import dev.chams.config.ChamsConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Einstellungs-Screen für Chams ESP.
 * 2-Spalten-Layout, aufgerufen via Hotkey (Default: Right Shift).
 *
 * Linke Spalte : Feature-Toggles, Skin-Optionen, Skelett-Farbe, Farben
 * Rechte Spalte: Chroma, Distance-Color, Range-Filter
 */
public final class ChamsConfigScreen extends Screen {

    private final Screen parent;

    // Labels die wir frei in render() zeichnen wollen
    private final List<FloatingLabel> labels = new ArrayList<>();

    public ChamsConfigScreen(Screen parent) {
        super(Component.literal("Chams ESP - Einstellungen"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        ChamsConfig cfg = ChamsConfig.get();
        labels.clear();

        int centerX = this.width / 2;
        int colWidth = 230;
        int gap = 20;
        int leftX = centerX - colWidth - gap / 2;
        int rightX = centerX + gap / 2;

        int h = 20;
        int rowH = 22;
        int sectionGap = 8;
        int startY = 30;

        // ============ LINKE SPALTE ============
        int y = startY;

        // Master Toggle (prominent oben)
        addSectionLabel("ESP Master", leftX, y); y += 10;
        y = addToggle(y, rowH, leftX, colWidth, h, "\u00A76ESP Master",
                cfg.masterEnabled, v -> { cfg.masterEnabled = v; cfg.save(); });
        y += sectionGap;

        // Feature Toggles
        addSectionLabel("Features", leftX, y); y += 10;
        y = addToggle(y, rowH, leftX, colWidth, h, "Skin-Chams",
                cfg.skinEnabled, v -> { cfg.skinEnabled = v; cfg.save(); });
        y = addToggle(y, rowH, leftX, colWidth, h, "Skelett",
                cfg.skeletonEnabled, v -> { cfg.skeletonEnabled = v; cfg.save(); });
        y = addToggle(y, rowH, leftX, colWidth, h, "Hitbox",
                cfg.hitboxEnabled, v -> { cfg.hitboxEnabled = v; cfg.save(); });
        y = addToggle(y, rowH, leftX, colWidth, h, "Glow",
                cfg.glowEnabled, v -> { cfg.glowEnabled = v; cfg.save(); });
        y = addToggle(y, rowH, leftX, colWidth, h, "Tracers",
                cfg.tracersEnabled, v -> { cfg.tracersEnabled = v; cfg.save(); });
        y += sectionGap;

        // Skin-Chams Sub-Optionen
        addSectionLabel("Skin-Chams Optionen", leftX, y); y += 10;
        y = addToggle(y, rowH, leftX, colWidth, h, "Ruestung anzeigen",
                cfg.chamsShowArmor, v -> { cfg.chamsShowArmor = v; cfg.save(); });
        y = addToggle(y, rowH, leftX, colWidth, h, "Capes anzeigen",
                cfg.chamsShowCapes, v -> { cfg.chamsShowCapes = v; cfg.save(); });
        y += sectionGap;

        // Skelett-Farbmodus
        addSectionLabel("Skelett-Modus", leftX, y); y += 10;
        addRenderableWidget(CycleButton.<String>builder(
                        val -> Component.literal("health".equals(val) ? "Health (Rot->Gruen)" : "Fest (Hex)"),
                        cfg.skeletonColorMode)
                .withValues("health", "fixed")
                .create(leftX, y, colWidth, h,
                        Component.literal("Skelett"),
                        (btn, v) -> { cfg.skeletonColorMode = v; cfg.save(); }));
        y += rowH + sectionGap;

        // Color EditBoxes
        addSectionLabel("Farben (Hex RRGGBB)", leftX, y); y += 10;
        int boxW = 80;
        int boxX = leftX + colWidth - boxW;
        y = addColorRow("Skelett", boxX, y, boxW, h, leftX,
                cfg.skeletonColor, v -> { cfg.skeletonColor = v; cfg.save(); }, rowH);
        y = addColorRow("Glow", boxX, y, boxW, h, leftX,
                cfg.glowColor, v -> { cfg.glowColor = v; cfg.save(); }, rowH);
        y = addColorRow("Hitbox", boxX, y, boxW, h, leftX,
                cfg.hitboxColor, v -> { cfg.hitboxColor = v; cfg.save(); }, rowH);
        y = addColorRow("Tracer", boxX, y, boxW, h, leftX,
                cfg.tracerColor, v -> { cfg.tracerColor = v; cfg.save(); }, rowH);

        // ============ RECHTE SPALTE ============
        y = startY;

        // Chroma
        addSectionLabel("Chroma (Rainbow)", rightX, y); y += 10;
        y = addToggle(y, rowH, rightX, colWidth, h, "Skelett Chroma",
                cfg.chromaSkeleton, v -> { cfg.chromaSkeleton = v; cfg.save(); });
        y = addToggle(y, rowH, rightX, colWidth, h, "Hitbox Chroma",
                cfg.chromaHitbox, v -> { cfg.chromaHitbox = v; cfg.save(); });
        y = addToggle(y, rowH, rightX, colWidth, h, "Glow Chroma",
                cfg.chromaGlow, v -> { cfg.chromaGlow = v; cfg.save(); });
        y = addToggle(y, rowH, rightX, colWidth, h, "Tracer Chroma",
                cfg.chromaTracer, v -> { cfg.chromaTracer = v; cfg.save(); });
        y = addFloatRow("Chroma-Speed (Zyklen/s)", rightX, y, colWidth, boxW, h,
                cfg.chromaSpeed, 0.01f, 5f, v -> { cfg.chromaSpeed = v; cfg.save(); }, rowH);
        y += sectionGap;

        // Team Color
        addSectionLabel("Team-Color", rightX, y); y += 10;
        y = addToggle(y, rowH, rightX, colWidth, h, "Team-Farbe aus Scoreboard",
                cfg.teamColorEnabled, v -> { cfg.teamColorEnabled = v; cfg.save(); });
        y += sectionGap;

        // Distance Color
        addSectionLabel("Distance-Color", rightX, y); y += 10;
        y = addToggle(y, rowH, rightX, colWidth, h, "Distance Color aktiv",
                cfg.distanceColorEnabled, v -> { cfg.distanceColorEnabled = v; cfg.save(); });
        y = addColorRow("Farbe nah", rightX + colWidth - boxW, y, boxW, h, rightX,
                cfg.distanceColorClose, v -> { cfg.distanceColorClose = v; cfg.save(); }, rowH);
        y = addColorRow("Farbe fern", rightX + colWidth - boxW, y, boxW, h, rightX,
                cfg.distanceColorFar, v -> { cfg.distanceColorFar = v; cfg.save(); }, rowH);
        y = addFloatRow("Max-Distanz (Bloecke)", rightX, y, colWidth, boxW, h,
                cfg.distanceColorMax, 1f, 256f, v -> { cfg.distanceColorMax = v; cfg.save(); }, rowH);
        y += sectionGap;

        // Filter (Range + FOV)
        addSectionLabel("Filter", rightX, y); y += 10;
        y = addToggle(y, rowH, rightX, colWidth, h, "Range-Limit aktiv",
                cfg.rangeEnabled, v -> { cfg.rangeEnabled = v; cfg.save(); });
        y = addFloatRow("Max Range (Bloecke)", rightX, y, colWidth, boxW, h,
                cfg.rangeMaxBlocks, 1f, 512f, v -> { cfg.rangeMaxBlocks = v; cfg.save(); }, rowH);
        y = addToggle(y, rowH, rightX, colWidth, h, "FOV-Limit aktiv",
                cfg.fovLimitEnabled, v -> { cfg.fovLimitEnabled = v; cfg.save(); });
        y = addFloatRow("FOV-Winkel (Grad)", rightX, y, colWidth, boxW, h,
                cfg.fovLimitDegrees, 1f, 180f, v -> { cfg.fovLimitDegrees = v; cfg.save(); }, rowH);

        // ============ FOOTER: Close Button mittig ============
        int closeY = this.height - 36;
        addRenderableWidget(Button.builder(
                Component.literal("Speichern & Schliessen"),
                b -> this.onClose()
        ).bounds(centerX - 120, closeY, 240, h).build());
    }

    // ------------------------------------------------------------------
    //  Hilfs-Adder
    // ------------------------------------------------------------------

    private int addToggle(int y, int rowH, int x, int w, int h,
                          String label, boolean initial, Consumer<Boolean> onChange) {
        addRenderableWidget(CycleButton.booleanBuilder(
                        Component.literal("\u00A7aAN"),
                        Component.literal("\u00A7cAUS"),
                        initial)
                .create(x, y, w, h,
                        Component.literal(label),
                        (btn, v) -> onChange.accept(v)));
        return y + rowH;
    }

    /** Farb-Feld: links Label, rechts 6-stellige Hex-Box. */
    private int addColorRow(String label, int boxX, int y, int boxW, int h,
                            int labelX, int initialColor,
                            Consumer<Integer> onChange, int rowH) {
        EditBox box = new EditBox(this.font, boxX, y, boxW, h, Component.literal(""));
        box.setMaxLength(6);
        box.setValue(String.format("%06X", initialColor & 0xFFFFFF));
        box.setResponder(val -> {
            if (val == null || val.length() != 6) return;
            try {
                int parsed = Integer.parseInt(val, 16);
                onChange.accept(parsed);
            } catch (NumberFormatException ignored) {}
        });
        addRenderableWidget(box);
        labels.add(new FloatingLabel(label + ":", labelX, y + 6, 0xFFFFFF));
        return y + rowH;
    }

    /** Float-Feld: links Label, rechts Zahlen-Box mit Clamp. */
    private int addFloatRow(String label, int x, int y, int rowW, int boxW, int h,
                            float initial, float min, float max,
                            Consumer<Float> onChange, int rowH) {
        int boxX = x + rowW - boxW;
        EditBox box = new EditBox(this.font, boxX, y, boxW, h, Component.literal(""));
        box.setMaxLength(8);
        box.setValue(formatFloat(initial));
        box.setResponder(val -> {
            if (val == null || val.isEmpty()) return;
            try {
                float parsed = Float.parseFloat(val);
                if (!Float.isFinite(parsed)) return;
                parsed = Math.max(min, Math.min(max, parsed));
                onChange.accept(parsed);
            } catch (NumberFormatException ignored) {}
        });
        addRenderableWidget(box);
        labels.add(new FloatingLabel(label + ":", x, y + 6, 0xFFFFFF));
        return y + rowH;
    }

    private void addSectionLabel(String text, int x, int y) {
        labels.add(new FloatingLabel("\u00A7e" + text, x, y, 0xFFFF55));
    }

    private static String formatFloat(float v) {
        if (v == (int) v) return String.valueOf((int) v);
        return String.format(java.util.Locale.ROOT, "%.2f", v);
    }

    // ------------------------------------------------------------------
    //  Render
    // ------------------------------------------------------------------

    @Override
    public void render(GuiGraphics ctx, int mx, int my, float dt) {
        super.render(ctx, mx, my, dt);

        // Titel
        ctx.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFF);

        // Freitext-Labels (Section-Header, Farb-/Float-Labels)
        for (FloatingLabel lbl : labels) {
            ctx.drawString(this.font, lbl.text, lbl.x, lbl.y, lbl.color, false);
        }

        // Footer-Hinweis
        ctx.drawCenteredString(this.font,
                Component.literal("\u00A77Hotkeys im Minecraft Controls-Menue unter 'Chams ESP'"),
                this.width / 2, this.height - 14, 0xAAAAAA);
    }

    @Override
    public void onClose() {
        ChamsConfig.get().save();
        if (this.minecraft != null) this.minecraft.setScreen(this.parent);
    }

    private record FloatingLabel(String text, int x, int y, int color) {}
}
