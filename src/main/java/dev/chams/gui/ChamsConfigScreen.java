package dev.chams.gui;

import dev.chams.config.ChamsConfig;
import dev.chams.gui.widgets.FloatSlider;
import dev.chams.gui.widgets.ModeCycle;
import dev.chams.gui.widgets.PrimaryButton;
import dev.chams.gui.widgets.TabButton;
import dev.chams.gui.widgets.Theme;
import dev.chams.gui.widgets.ToggleSwitch;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Modernes Tab-basiertes Settings-Menue fuer Chams ESP.
 *
 * <p>5 Tabs: 3D ESP, 2D HUD, Farben, Filter, Info.
 * Custom-Widgets (ToggleSwitch, FloatSlider, TabButton) statt Vanilla
 * CycleButton fuer einheitliche, kompaktere Optik.
 */
public final class ChamsConfigScreen extends Screen {

    // ------------------------------------------------------------------
    //  Tabs
    // ------------------------------------------------------------------
    private enum Tab {
        ESP_3D("3D ESP"),
        HUD_2D("2D HUD"),
        COLORING("Farben"),
        FILTERS("Filter"),
        INFO("Info");

        final String label;
        Tab(String label) { this.label = label; }
    }

    private static Tab activeTab = Tab.ESP_3D; // ueber Screen-Reopen hinweg merken

    // ------------------------------------------------------------------
    //  State
    // ------------------------------------------------------------------
    private final Screen parent;
    private final List<DrawCallback> deferred = new ArrayList<>();
    private final List<ColorSwatch> swatches = new ArrayList<>();

    // Layout-Konstanten
    private static final int CONTENT_W = 520;
    private static final int CARD_PAD = 10;
    private static final int ROW_H = 22;
    private static final int ROW_GAP = 4;
    private static final int SECTION_GAP = 12;

    public ChamsConfigScreen(Screen parent) {
        super(Component.literal("Chams ESP"));
        this.parent = parent;
    }

    // ==================================================================
    //  Init
    // ==================================================================

    @Override
    protected void init() {
        deferred.clear();
        swatches.clear();

        // ---- Tab-Bar ----
        int tabY = 36;
        int tabH = 24;
        Tab[] tabs = Tab.values();
        int tabW = 92;
        int gap = 4;
        int totalW = tabs.length * tabW + (tabs.length - 1) * gap;
        int tabX = (this.width - totalW) / 2;
        for (Tab t : tabs) {
            final Tab finalT = t;
            addRenderableWidget(new TabButton(
                    tabX, tabY, tabW, tabH, t.label,
                    activeTab == t,
                    () -> { activeTab = finalT; this.rebuildWidgets(); }));
            tabX += tabW + gap;
        }

        // ---- Content ----
        int contentX = (this.width - CONTENT_W) / 2;
        int contentY = tabY + tabH + 14;

        switch (activeTab) {
            case ESP_3D   -> initEsp3D(contentX, contentY);
            case HUD_2D   -> initHud2D(contentX, contentY);
            case COLORING -> initColoring(contentX, contentY);
            case FILTERS  -> initFilters(contentX, contentY);
            case INFO     -> initInfo(contentX, contentY);
        }

        // ---- Footer ----
        int footerY = this.height - 32;
        int btnW = 200, btnH = 22;
        addRenderableWidget(new PrimaryButton(
                this.width / 2 - btnW - 4, footerY, btnW, btnH,
                "Abbrechen", false, this::onClose));
        addRenderableWidget(new PrimaryButton(
                this.width / 2 + 4, footerY, btnW, btnH,
                "Speichern & Schliessen", true, () -> {
                    ChamsConfig.get().save();
                    this.onClose();
                }));
    }

    // ==================================================================
    //  Tab-Inhalte
    // ==================================================================

    private void initEsp3D(int x0, int y0) {
        ChamsConfig cfg = ChamsConfig.get();
        int colW = (CONTENT_W - 12) / 2;
        int leftX = x0;
        int rightX = x0 + colW + 12;

        // ----- Linke Spalte -----
        int y = y0;
        y = card(leftX, y, colW, "ESP Master & Features", 6);
        y = toggle(leftX, y, colW, "ESP Master (kill switch)",
                cfg.masterEnabled, v -> { cfg.masterEnabled = v; cfg.save(); });
        y = toggle(leftX, y, colW, "Skin-Chams (durch Waende)",
                cfg.skinEnabled, v -> { cfg.skinEnabled = v; cfg.save(); });
        y = toggle(leftX, y, colW, "Skelett",
                cfg.skeletonEnabled, v -> { cfg.skeletonEnabled = v; cfg.save(); });
        y = toggle(leftX, y, colW, "Hitbox",
                cfg.hitboxEnabled, v -> { cfg.hitboxEnabled = v; cfg.save(); });
        y = toggle(leftX, y, colW, "Glow",
                cfg.glowEnabled, v -> { cfg.glowEnabled = v; cfg.save(); });
        y = toggle(leftX, y, colW, "Tracers",
                cfg.tracersEnabled, v -> { cfg.tracersEnabled = v; cfg.save(); });
        y = endCard(y);

        y = card(leftX, y, colW, "Skin-Chams Optionen", 2);
        y = toggle(leftX, y, colW, "Ruestung anzeigen",
                cfg.chamsShowArmor, v -> { cfg.chamsShowArmor = v; cfg.save(); });
        y = toggle(leftX, y, colW, "Capes anzeigen",
                cfg.chamsShowCapes, v -> { cfg.chamsShowCapes = v; cfg.save(); });
        endCard(y);

        // ----- Rechte Spalte -----
        y = y0;
        y = card(rightX, y, colW, "Skelett-Modus", 1);
        y = modeCycle(rightX, y, colW, "Farb-Modus",
                List.of("health", "fixed"), cfg.skeletonColorMode,
                v -> "health".equals(v) ? "Health (Rot->Gruen)" : "Fest (Hex)",
                v -> { cfg.skeletonColorMode = v; cfg.save(); });
        y = endCard(y);

        y = card(rightX, y, colW, "Farben (Hex RRGGBB)", 4);
        y = colorRow(rightX, y, colW, "Skelett",
                cfg.skeletonColor, v -> { cfg.skeletonColor = v; cfg.save(); });
        y = colorRow(rightX, y, colW, "Glow",
                cfg.glowColor, v -> { cfg.glowColor = v; cfg.save(); });
        y = colorRow(rightX, y, colW, "Hitbox",
                cfg.hitboxColor, v -> { cfg.hitboxColor = v; cfg.save(); });
        y = colorRow(rightX, y, colW, "Tracer",
                cfg.tracerColor, v -> { cfg.tracerColor = v; cfg.save(); });
        endCard(y);
    }

    private void initHud2D(int x0, int y0) {
        ChamsConfig cfg = ChamsConfig.get();
        int colW = (CONTENT_W - 12) / 2;
        int leftX = x0;
        int rightX = x0 + colW + 12;

        int y = y0;
        y = card(leftX, y, colW, "Features", 4);
        y = toggle(leftX, y, colW, "2D Box",
                cfg.box2dEnabled, v -> { cfg.box2dEnabled = v; cfg.save(); });
        y = toggle(leftX, y, colW, "Name-Tag",
                cfg.nameTagEnabled, v -> { cfg.nameTagEnabled = v; cfg.save(); });
        y = toggle(leftX, y, colW, "Distanz-Tag",
                cfg.distanceTagEnabled, v -> { cfg.distanceTagEnabled = v; cfg.save(); });
        y = toggle(leftX, y, colW, "Health-Bar (2D)",
                cfg.healthBar2dEnabled, v -> { cfg.healthBar2dEnabled = v; cfg.save(); });
        endCard(y);

        y = y0;
        y = card(rightX, y, colW, "Farben (Hex RRGGBB)", 3);
        y = colorRow(rightX, y, colW, "2D Box",
                cfg.box2dColor, v -> { cfg.box2dColor = v; cfg.save(); });
        y = colorRow(rightX, y, colW, "Name-Tag",
                cfg.nameTagColor, v -> { cfg.nameTagColor = v; cfg.save(); });
        y = colorRow(rightX, y, colW, "Distanz-Tag",
                cfg.distanceTagColor, v -> { cfg.distanceTagColor = v; cfg.save(); });
        y = endCard(y);

        y = card(rightX, y, colW, "Chroma", 1);
        y = toggle(rightX, y, colW, "2D Box Chroma",
                cfg.chromaBox2d, v -> { cfg.chromaBox2d = v; cfg.save(); });
        endCard(y);
    }

    private void initColoring(int x0, int y0) {
        ChamsConfig cfg = ChamsConfig.get();
        int colW = (CONTENT_W - 12) / 2;
        int leftX = x0;
        int rightX = x0 + colW + 12;

        int y = y0;
        y = card(leftX, y, colW, "Chroma (Rainbow)", 6);
        y = toggle(leftX, y, colW, "Skelett",
                cfg.chromaSkeleton, v -> { cfg.chromaSkeleton = v; cfg.save(); });
        y = toggle(leftX, y, colW, "Hitbox",
                cfg.chromaHitbox, v -> { cfg.chromaHitbox = v; cfg.save(); });
        y = toggle(leftX, y, colW, "Glow",
                cfg.chromaGlow, v -> { cfg.chromaGlow = v; cfg.save(); });
        y = toggle(leftX, y, colW, "Tracer",
                cfg.chromaTracer, v -> { cfg.chromaTracer = v; cfg.save(); });
        y = toggle(leftX, y, colW, "2D Box",
                cfg.chromaBox2d, v -> { cfg.chromaBox2d = v; cfg.save(); });
        y = slider(leftX, y, colW, "Speed", " Zykl/s",
                cfg.chromaSpeed, 0.05f, 5f, false,
                v -> { cfg.chromaSpeed = v; cfg.save(); });
        endCard(y);

        y = y0;
        y = card(rightX, y, colW, "Team-Color", 1);
        y = toggle(rightX, y, colW, "Scoreboard-Team-Farbe nutzen",
                cfg.teamColorEnabled, v -> { cfg.teamColorEnabled = v; cfg.save(); });
        y = endCard(y);

        y = card(rightX, y, colW, "Distance-Color", 4);
        y = toggle(rightX, y, colW, "Aktiv",
                cfg.distanceColorEnabled, v -> { cfg.distanceColorEnabled = v; cfg.save(); });
        y = colorRow(rightX, y, colW, "Nah",
                cfg.distanceColorClose, v -> { cfg.distanceColorClose = v; cfg.save(); });
        y = colorRow(rightX, y, colW, "Fern",
                cfg.distanceColorFar, v -> { cfg.distanceColorFar = v; cfg.save(); });
        y = slider(rightX, y, colW, "Max-Distanz", " Bl",
                cfg.distanceColorMax, 1f, 256f, true,
                v -> { cfg.distanceColorMax = v; cfg.save(); });
        endCard(y);
    }

    private void initFilters(int x0, int y0) {
        ChamsConfig cfg = ChamsConfig.get();
        int leftX = x0;

        int y = y0;
        y = card(leftX, y, CONTENT_W, "ESP Master", 1);
        y = toggle(leftX, y, CONTENT_W, "ESP Master (alle Features auf einmal)",
                cfg.masterEnabled, v -> { cfg.masterEnabled = v; cfg.save(); });
        y = endCard(y);

        y = card(leftX, y, CONTENT_W, "Range-Limit", 2);
        y = toggle(leftX, y, CONTENT_W, "Range-Limit aktiv",
                cfg.rangeEnabled, v -> { cfg.rangeEnabled = v; cfg.save(); });
        y = slider(leftX, y, CONTENT_W, "Max Range", " Bloecke",
                cfg.rangeMaxBlocks, 1f, 512f, true,
                v -> { cfg.rangeMaxBlocks = v; cfg.save(); });
        y = endCard(y);

        y = card(leftX, y, CONTENT_W, "FOV-Limit (Sichtkegel)", 2);
        y = toggle(leftX, y, CONTENT_W, "FOV-Limit aktiv",
                cfg.fovLimitEnabled, v -> { cfg.fovLimitEnabled = v; cfg.save(); });
        y = slider(leftX, y, CONTENT_W, "Oeffnungswinkel", " Grad",
                cfg.fovLimitDegrees, 5f, 180f, true,
                v -> { cfg.fovLimitDegrees = v; cfg.save(); });
        endCard(y);
    }

    private void initInfo(int x0, int y0) {
        // Card-Header rendert sich selbst, Inhalt rendern wir ueber deferred-Labels
        int colW = CONTENT_W;
        int leftX = x0;

        int y = y0;
        y = card(leftX, y, colW, "Hotkeys (im Controls-Menue rebindbar)", 8);
        y = infoRow(leftX, y, colW, "Skin-Chams",     "G");
        y = infoRow(leftX, y, colW, "Skelett",        "H");
        y = infoRow(leftX, y, colW, "Hitbox",         "J");
        y = infoRow(leftX, y, colW, "Glow",           "K");
        y = infoRow(leftX, y, colW, "Tracers",        "Y");
        y = infoRow(leftX, y, colW, "2D Box",         "B");
        y = infoRow(leftX, y, colW, "ESP Master",     "Right Ctrl");
        y = infoRow(leftX, y, colW, "Settings-Menue", "Right Shift");
        y = endCard(y);

        y = card(leftX, y, colW, "Tipps", 3);
        y = textRow(leftX, y, colW, "\u00A77- Hotkeys koennen unter Optionen -> Steuerung -> Chams ESP geaendert werden.");
        y = textRow(leftX, y, colW, "\u00A77- Config wird in .minecraft/config/chams-esp.json persistiert.");
        y = textRow(leftX, y, colW, "\u00A77- ESP Master schaltet ALLE Features mit einer Taste aus.");
        endCard(y);
    }

    // ==================================================================
    //  Card-Helpers
    // ==================================================================

    /** Reserviert eine Card mit Header. Liefert die Y-Position fuer den ersten Inhalt. */
    private int card(int x, int y, int w, String header, int rowCount) {
        int headerH = 18;
        int contentH = rowCount * (ROW_H + ROW_GAP) + CARD_PAD;
        int totalH = headerH + contentH + 4;
        final int finalX = x, finalY = y, finalW = w, finalTotalH = totalH, finalHeaderH = headerH;
        final String finalHeader = header;
        deferred.add((ctx, mx, my, dt) -> {
            // BG
            ctx.fill(finalX, finalY, finalX + finalW, finalY + finalTotalH, Theme.CARD_BG);
            Theme.drawBorder(ctx, finalX, finalY, finalW, finalTotalH, Theme.CARD_BORDER);
            // Header-Streifen
            ctx.fill(finalX, finalY, finalX + finalW, finalY + finalHeaderH, Theme.CARD_HEADER_BG);
            ctx.fill(finalX, finalY + finalHeaderH, finalX + finalW, finalY + finalHeaderH + 1,
                    Theme.CARD_BORDER);
            // Header-Text
            var font = this.font;
            ctx.drawString(font, finalHeader,
                    finalX + 8, finalY + (finalHeaderH - font.lineHeight) / 2 + 1,
                    Theme.TEXT_HEADING, false);
        });
        return y + headerH + CARD_PAD / 2 + 2;
    }

    private int endCard(int y) {
        return y + CARD_PAD / 2 + SECTION_GAP;
    }

    // ==================================================================
    //  Row-Helpers
    // ==================================================================

    private int toggle(int x, int y, int w, String label,
                       boolean initial, Consumer<Boolean> onChange) {
        int innerX = x + 6;
        int innerW = w - 12;
        addRenderableWidget(new ToggleSwitch(innerX, y, innerW, ROW_H,
                label, initial, onChange));
        return y + ROW_H + ROW_GAP;
    }

    private int slider(int x, int y, int w, String label, String unit,
                       float val, float min, float max, boolean integerOnly,
                       Consumer<Float> onChange) {
        int innerX = x + 6;
        int innerW = w - 12;
        addRenderableWidget(new FloatSlider(innerX, y, innerW, ROW_H,
                label, unit, val, min, max, integerOnly, onChange));
        return y + ROW_H + ROW_GAP;
    }

    private int modeCycle(int x, int y, int w, String label,
                          List<String> values, String initial,
                          java.util.function.Function<String, String> mapper,
                          Consumer<String> onChange) {
        int innerX = x + 6;
        int innerW = w - 12;
        addRenderableWidget(new ModeCycle(innerX, y, innerW, ROW_H,
                label, values, initial, mapper, onChange));
        return y + ROW_H + ROW_GAP;
    }

    /** Color-Row: Label links, Hex-EditBox rechts, Swatch ganz rechts. */
    private int colorRow(int x, int y, int w, String label,
                         int initialColor, Consumer<Integer> onChange) {
        int innerX = x + 6;
        int innerW = w - 12;
        int swatchSize = ROW_H;
        int boxW = 70;
        int gap = 6;
        int boxX = innerX + innerW - swatchSize - gap - boxW;
        int swatchX = innerX + innerW - swatchSize;

        EditBox box = new EditBox(this.font, boxX, y, boxW, ROW_H, Component.literal(""));
        box.setMaxLength(6);
        box.setValue(String.format("%06X", initialColor & 0xFFFFFF));
        // Live-Color fuer den Swatch ueber ein Array (mutable Closure)
        final int[] currentColor = { initialColor & 0xFFFFFF };
        box.setResponder(val -> {
            if (val == null || val.length() != 6) return;
            try {
                int parsed = Integer.parseInt(val, 16);
                currentColor[0] = parsed;
                onChange.accept(parsed);
            } catch (NumberFormatException ignored) {}
        });
        addRenderableWidget(box);

        // Label-Text
        final int finalLabelX = innerX;
        final int finalLabelY = y + (ROW_H - this.font.lineHeight) / 2 + 1;
        final String finalLabel = label;
        deferred.add((ctx, mx, my, dt) ->
                ctx.drawString(this.font, finalLabel,
                        finalLabelX, finalLabelY, Theme.TEXT_PRIMARY, false));

        // Swatch (live aus currentColor)
        swatches.add(new ColorSwatch(swatchX, y, swatchSize, currentColor));

        return y + ROW_H + ROW_GAP;
    }

    /** Info-Zeile mit Label links und Wert rechts (z.B. Hotkey-Anzeige). */
    private int infoRow(int x, int y, int w, String label, String value) {
        int innerX = x + 6;
        int innerW = w - 12;
        final int finalX = innerX;
        final int finalRightX = innerX + innerW;
        final int finalY = y + (ROW_H - this.font.lineHeight) / 2 + 1;
        final String finalLabel = label;
        final String finalValue = value;

        deferred.add((ctx, mx, my, dt) -> {
            // dezente Linie als Trenner
            ctx.fill(finalX, finalY + this.font.lineHeight + 4,
                    finalRightX, finalY + this.font.lineHeight + 5,
                    0xFF2A2A35);
            ctx.drawString(this.font, finalLabel, finalX, finalY,
                    Theme.TEXT_PRIMARY, false);
            int valW = this.font.width(finalValue);
            ctx.drawString(this.font, finalValue,
                    finalRightX - valW, finalY, Theme.TEXT_ACCENT, false);
        });
        return y + ROW_H + ROW_GAP;
    }

    private int textRow(int x, int y, int w, String text) {
        int innerX = x + 6;
        final int finalX = innerX;
        final int finalY = y + (ROW_H - this.font.lineHeight) / 2 + 1;
        final String finalText = text;
        deferred.add((ctx, mx, my, dt) ->
                ctx.drawString(this.font, finalText, finalX, finalY,
                        Theme.TEXT_SECONDARY, false));
        return y + ROW_H + ROW_GAP;
    }

    // ==================================================================
    //  Render
    // ==================================================================

    @Override
    public void renderBackground(GuiGraphics ctx, int mx, int my, float dt) {
        // Voller dunkler Modal-Background statt Vanilla-Dirt
        ctx.fill(0, 0, this.width, this.height, Theme.BG_OVERLAY);
    }

    @Override
    public void render(GuiGraphics ctx, int mx, int my, float dt) {
        this.renderBackground(ctx, mx, my, dt);

        // Cards / Backgrounds zuerst (unter den Widgets)
        for (DrawCallback d : deferred) d.draw(ctx, mx, my, dt);

        // Vanilla-Widgets (Buttons, Toggles, Slider, EditBoxes)
        super.render(ctx, mx, my, dt);

        // Color-Swatches obenauf (live aus dem Closure-Array)
        for (ColorSwatch s : swatches) s.draw(ctx);

        // Titel ganz oben
        ctx.drawString(this.font, "\u00A7lChams ESP",
                this.width / 2 - this.font.width("Chams ESP") / 2 - 2,
                14, Theme.TEXT_HEADING, false);
        // Untertitel
        String sub = "v" + getVersion();
        ctx.drawString(this.font, "\u00A77" + sub,
                this.width / 2 - this.font.width(sub) / 2,
                24, Theme.TEXT_DIM, false);
    }

    private static String getVersion() {
        try {
            return net.fabricmc.loader.api.FabricLoader.getInstance()
                    .getModContainer("chams_esp")
                    .map(c -> c.getMetadata().getVersion().getFriendlyString())
                    .orElse("?");
        } catch (Throwable t) { return "?"; }
    }

    @Override
    public void onClose() {
        ChamsConfig.get().save();
        if (this.minecraft != null) this.minecraft.setScreen(this.parent);
    }

    // ==================================================================
    //  Inner Types
    // ==================================================================

    @FunctionalInterface
    private interface DrawCallback {
        void draw(GuiGraphics ctx, int mx, int my, float dt);
    }

    /** Live-Swatch der die aktuelle Farbe aus einem int[]-Slot zeichnet. */
    private static final class ColorSwatch {
        final int x, y, size;
        final int[] colorRef;

        ColorSwatch(int x, int y, int size, int[] colorRef) {
            this.x = x; this.y = y; this.size = size; this.colorRef = colorRef;
        }

        void draw(GuiGraphics ctx) {
            int argb = 0xFF000000 | (colorRef[0] & 0x00FFFFFF);
            ctx.fill(x, y, x + size, y + size, argb);
            Theme.drawBorder(ctx, x, y, size, size, Theme.CARD_BORDER);
        }
    }
}
