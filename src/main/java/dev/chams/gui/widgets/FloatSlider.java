package dev.chams.gui.widgets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.function.Consumer;

/**
 * Slider fuer Float-Werte mit Min/Max + Label.
 *
 *   [ Label: 42.0   ████████░░░░░░░░░ ]
 */
public final class FloatSlider extends AbstractSliderButton {

    private final float min;
    private final float max;
    private final String label;
    private final String unit;
    private final boolean integerOnly;
    private final Consumer<Float> onChange;

    public FloatSlider(int x, int y, int w, int h, String label, String unit,
                       float val, float min, float max, boolean integerOnly,
                       Consumer<Float> onChange) {
        super(x, y, w, h, Component.empty(), normalize(val, min, max));
        this.label = label;
        this.unit = unit == null ? "" : unit;
        this.min = min;
        this.max = max;
        this.integerOnly = integerOnly;
        this.onChange = onChange;
        this.updateMessage();
    }

    private static double normalize(float v, float min, float max) {
        if (max <= min) return 0.0;
        double t = (v - min) / (max - min);
        return Math.max(0, Math.min(1, t));
    }

    private float currentValue() {
        float v = (float) (min + this.value * (max - min));
        if (integerOnly) v = Math.round(v);
        return v;
    }

    @Override
    protected void updateMessage() {
        float v = currentValue();
        String s = integerOnly
                ? String.format(Locale.ROOT, "%d", (int) v)
                : (v == Math.floor(v) ? String.format(Locale.ROOT, "%d", (int) v)
                                      : String.format(Locale.ROOT, "%.2f", v));
        this.setMessage(Component.literal(label + ": " + s + unit));
    }

    @Override
    protected void applyValue() {
        onChange.accept(currentValue());
    }

    @Override
    public void renderWidget(GuiGraphics ctx, int mx, int my, float dt) {
        boolean hover = isHovered() || isFocused();

        // Track
        ctx.fill(getX(), getY(), getX() + width, getY() + height, Theme.SLIDER_TRACK);
        Theme.drawBorder(ctx, getX(), getY(), width, height,
                hover ? Theme.INPUT_BORDER_FOCUS : Theme.BTN_BORDER);

        // Fill bis zum Knob
        int fillW = (int) (this.value * (width - 2));
        if (fillW > 0) {
            ctx.fill(getX() + 1, getY() + 1, getX() + 1 + fillW, getY() + height - 1,
                    (Theme.SLIDER_FILL & 0x00FFFFFF) | 0x60000000);
        }

        // Knob (vertikaler Strich)
        int knobX = getX() + 1 + fillW - 2;
        knobX = Math.max(getX() + 1, Math.min(getX() + width - 4, knobX));
        ctx.fill(knobX, getY() + 1, knobX + 3, getY() + height - 1, Theme.SLIDER_KNOB);

        // Label + Wert (zentriert)
        var font = Minecraft.getInstance().font;
        int textY = getY() + (height - font.lineHeight) / 2 + 1;
        int textW = font.width(getMessage());
        int textX = getX() + (width - textW) / 2;
        // Schwarzer Schatten unterhalb
        ctx.drawString(font, getMessage(), textX + 1, textY + 1, 0xFF000000, false);
        ctx.drawString(font, getMessage(), textX,     textY,     Theme.TEXT_PRIMARY, false);
    }
}
