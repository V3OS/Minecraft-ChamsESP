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
 * Einstellungs-Screen fuer alle HUD- / 2D-ESP-Features.
 * Wird aus dem Haupt-{@link ChamsConfigScreen} per Button geoeffnet.
 */
public final class ChamsHudConfigScreen extends Screen {

    private final Screen parent;
    private final List<FloatingLabel> labels = new ArrayList<>();

    public ChamsHudConfigScreen(Screen parent) {
        super(Component.literal("Chams ESP - HUD / 2D Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        ChamsConfig cfg = ChamsConfig.get();
        labels.clear();

        int cx = this.width / 2;
        int colW = 260;
        int h = 20;
        int rowH = 22;
        int sectionGap = 8;

        int x = cx - colW / 2;
        int y = 30;

        // ===== Feature Toggles =====
        addSection("Features", x, y); y += 10;
        y = addToggle(y, rowH, x, colW, h, "2D-Box",
                cfg.box2dEnabled, v -> { cfg.box2dEnabled = v; cfg.save(); });
        y = addToggle(y, rowH, x, colW, h, "Name-Tag",
                cfg.nameTagEnabled, v -> { cfg.nameTagEnabled = v; cfg.save(); });
        y = addToggle(y, rowH, x, colW, h, "Distance-Tag",
                cfg.distanceTagEnabled, v -> { cfg.distanceTagEnabled = v; cfg.save(); });
        y = addToggle(y, rowH, x, colW, h, "Healthbar (2D)",
                cfg.healthBar2dEnabled, v -> { cfg.healthBar2dEnabled = v; cfg.save(); });
        y += sectionGap;

        // ===== Farben =====
        addSection("Farben (Hex RRGGBB)", x, y); y += 10;
        int boxW = 80;
        int boxX = x + colW - boxW;
        y = addColorRow("2D-Box", boxX, y, boxW, h, x, cfg.box2dColor,
                v -> { cfg.box2dColor = v; cfg.save(); }, rowH);
        y = addColorRow("Name-Tag", boxX, y, boxW, h, x, cfg.nameTagColor,
                v -> { cfg.nameTagColor = v; cfg.save(); }, rowH);
        y = addColorRow("Distance-Tag", boxX, y, boxW, h, x, cfg.distanceTagColor,
                v -> { cfg.distanceTagColor = v; cfg.save(); }, rowH);
        y += sectionGap;

        // ===== Chroma =====
        addSection("Chroma", x, y); y += 10;
        y = addToggle(y, rowH, x, colW, h, "2D-Box Chroma",
                cfg.chromaBox2d, v -> { cfg.chromaBox2d = v; cfg.save(); });
        y += sectionGap;

        // ===== Zurueck =====
        addRenderableWidget(Button.builder(
                Component.literal("Zurueck"),
                b -> this.onClose()
        ).bounds(cx - 120, this.height - 36, 240, h).build());
    }

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

    private void addSection(String text, int x, int y) {
        labels.add(new FloatingLabel("\u00A7e" + text, x, y, 0xFFFF55));
    }

    @Override
    public void render(GuiGraphics ctx, int mx, int my, float dt) {
        super.render(ctx, mx, my, dt);
        ctx.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFF);
        for (FloatingLabel l : labels) {
            ctx.drawString(this.font, l.text, l.x, l.y, l.color, false);
        }
        ctx.drawCenteredString(this.font,
                Component.literal("\u00A77HUD wird ueber Spieler durch Waende gezeichnet"),
                this.width / 2, this.height - 14, 0xAAAAAA);
    }

    @Override
    public void onClose() {
        ChamsConfig.get().save();
        if (this.minecraft != null) this.minecraft.setScreen(this.parent);
    }

    private record FloatingLabel(String text, int x, int y, int color) {}
}
