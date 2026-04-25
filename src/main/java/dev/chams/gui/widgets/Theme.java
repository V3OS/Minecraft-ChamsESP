package dev.chams.gui.widgets;

/**
 * Zentrale Farb-Konstanten fuer das Custom-GUI.
 * ARGB-Werte (0xAARRGGBB).
 */
public final class Theme {
    private Theme() {}

    // ---- Background ----
    public static final int BG_OVERLAY      = 0xD0101015; // Vollflaechiger Modal-BG
    public static final int CARD_BG         = 0xC0181820;
    public static final int CARD_BORDER     = 0xFF2A2A35;
    public static final int CARD_HEADER_BG  = 0xFF22222C;

    // ---- Text ----
    public static final int TEXT_PRIMARY    = 0xFFE6E6F0;
    public static final int TEXT_SECONDARY  = 0xFFA0A0B0;
    public static final int TEXT_DIM        = 0xFF707080;
    public static final int TEXT_HEADING    = 0xFFFFD453;
    public static final int TEXT_ACCENT     = 0xFF66D9EF;

    // ---- Toggle / Slider ----
    public static final int TOGGLE_ON_BG    = 0xFF2E8E40;
    public static final int TOGGLE_OFF_BG   = 0xFF552B2B;
    public static final int TOGGLE_KNOB     = 0xFFEFEFF5;
    public static final int TOGGLE_KNOB_OFF = 0xFFC0C0CA;

    // ---- Buttons ----
    public static final int BTN_BG          = 0xFF2A2A33;
    public static final int BTN_BG_HOVER    = 0xFF3A3A4A;
    public static final int BTN_BORDER      = 0xFF454555;

    // ---- Tabs ----
    public static final int TAB_BG          = 0xFF1A1A22;
    public static final int TAB_BG_HOVER    = 0xFF26262F;
    public static final int TAB_BG_ACTIVE   = 0xFF333344;
    public static final int TAB_INDICATOR   = 0xFFFFD453;

    // ---- Sliders ----
    public static final int SLIDER_TRACK    = 0xFF2A2A33;
    public static final int SLIDER_FILL     = 0xFF66D9EF;
    public static final int SLIDER_KNOB     = 0xFFEFEFF5;

    // ---- Inputs ----
    public static final int INPUT_BG        = 0xFF12121A;
    public static final int INPUT_BORDER    = 0xFF2A2A35;
    public static final int INPUT_BORDER_FOCUS = 0xFF66D9EF;

    /** Zeichnet einen 1px-Rahmen via 4 fill-Calls. */
    public static void drawBorder(net.minecraft.client.gui.GuiGraphics ctx,
                                  int x, int y, int w, int h, int color) {
        ctx.fill(x,         y,         x + w,     y + 1,     color); // top
        ctx.fill(x,         y + h - 1, x + w,     y + h,     color); // bottom
        ctx.fill(x,         y,         x + 1,     y + h,     color); // left
        ctx.fill(x + w - 1, y,         x + w,     y + h,     color); // right
    }
}
