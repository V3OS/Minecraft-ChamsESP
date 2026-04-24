package dev.chams.gui;

import dev.chams.config.ChamsConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * Einstellungs-Screen für Chams ESP.
 * Aufruf via Hotkey (Default: Right Shift) oder aus dem Pause-Menü,
 * wenn wir den Knopf später einbauen.
 */
public final class ChamsConfigScreen extends Screen {

    private final Screen parent;

    // Y-Positionen der Color-Boxen für Label-Rendering
    private int skelColorY = -1;
    private int glowColorY = -1;
    private int hitboxColorY = -1;
    private int colorLabelX = -1;

    public ChamsConfigScreen(Screen parent) {
        super(Component.literal("Chams ESP - Einstellungen"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        ChamsConfig cfg = ChamsConfig.get();

        int cx = this.width / 2;
        int buttonW = 240;
        int h = 20;
        int rowH = 24;
        int sectionGap = 8;
        int y = 36;

        // ===== Feature Toggles =====
        y = addToggle(y, rowH, cx, buttonW, h, "Skin-Chams",
                cfg.skinEnabled, v -> { cfg.skinEnabled = v; cfg.save(); });
        y = addToggle(y, rowH, cx, buttonW, h, "Skelett",
                cfg.skeletonEnabled, v -> { cfg.skeletonEnabled = v; cfg.save(); });
        y = addToggle(y, rowH, cx, buttonW, h, "Hitbox",
                cfg.hitboxEnabled, v -> { cfg.hitboxEnabled = v; cfg.save(); });
        y = addToggle(y, rowH, cx, buttonW, h, "Glow",
                cfg.glowEnabled, v -> { cfg.glowEnabled = v; cfg.save(); });
        y += sectionGap;

        // ===== Skin-Chams Sub-Optionen =====
        y = addToggle(y, rowH, cx, buttonW, h, "Ruestung anzeigen",
                cfg.chamsShowArmor, v -> { cfg.chamsShowArmor = v; cfg.save(); });
        y = addToggle(y, rowH, cx, buttonW, h, "Capes anzeigen",
                cfg.chamsShowCapes, v -> { cfg.chamsShowCapes = v; cfg.save(); });
        y += sectionGap;

        // ===== Skelett-Farbmodus =====
        addRenderableWidget(CycleButton.<String>builder(
                        val -> Component.literal("health".equals(val) ? "Health (Rot->Gruen)" : "Fest (Hex)"),
                        cfg.skeletonColorMode)
                .withValues("health", "fixed")
                .create(cx - buttonW/2, y, buttonW, h,
                        Component.literal("Skelett-Modus"),
                        (btn, v) -> { cfg.skeletonColorMode = v; cfg.save(); }));
        y += rowH + sectionGap;

        // ===== Color EditBoxes =====
        // Labels: links, Boxen: rechts
        int boxW = 80;
        int boxX = cx + 4;
        colorLabelX = cx - 120;

        skelColorY   = y; addColorBox(boxX, y, boxW, h, cfg.skeletonColor,
                v -> { cfg.skeletonColor = v; cfg.save(); });  y += rowH;

        glowColorY   = y; addColorBox(boxX, y, boxW, h, cfg.glowColor,
                v -> { cfg.glowColor = v; cfg.save(); });      y += rowH;

        hitboxColorY = y; addColorBox(boxX, y, boxW, h, cfg.hitboxColor,
                v -> { cfg.hitboxColor = v; cfg.save(); });    y += rowH + sectionGap;

        // ===== Close Button =====
        addRenderableWidget(Button.builder(
                Component.literal("Speichern & Schliessen"),
                b -> this.onClose()
        ).bounds(cx - buttonW/2, y, buttonW, h).build());
    }

    private int addToggle(int y, int rowH, int cx, int buttonW, int h,
                          String label, boolean initial, Consumer<Boolean> onChange) {
        addRenderableWidget(CycleButton.booleanBuilder(
                        Component.literal("\u00A7aAN"),
                        Component.literal("\u00A7cAUS"),
                        initial)
                .create(cx - buttonW/2, y, buttonW, h,
                        Component.literal(label),
                        (btn, v) -> onChange.accept(v)));
        return y + rowH;
    }

    private void addColorBox(int x, int y, int w, int h, int initialColor,
                             Consumer<Integer> onChange) {
        EditBox box = new EditBox(this.font, x, y, w, h, Component.literal(""));
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
    }

    @Override
    public void render(GuiGraphics ctx, int mx, int my, float dt) {
        super.render(ctx, mx, my, dt);

        // Titel
        ctx.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFF);

        // Color-Labels links neben den EditBoxes
        if (colorLabelX >= 0) {
            int lblOff = 6;
            if (skelColorY   >= 0) ctx.drawString(this.font, "Skelett-Farbe (Hex):", colorLabelX, skelColorY + lblOff, 0xFFFFFF, false);
            if (glowColorY   >= 0) ctx.drawString(this.font, "Glow-Farbe (Hex):",    colorLabelX, glowColorY + lblOff, 0xFFFFFF, false);
            if (hitboxColorY >= 0) ctx.drawString(this.font, "Hitbox-Farbe (Hex):",  colorLabelX, hitboxColorY + lblOff, 0xFFFFFF, false);
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
}
