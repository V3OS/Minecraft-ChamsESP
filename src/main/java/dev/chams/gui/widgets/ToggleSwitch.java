package dev.chams.gui.widgets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * Modernes Toggle in Pill-Style:
 *
 *   [ Label                          ●  ]   <- gruene Pille rechts wenn AN
 *   [ Label                          ○  ]   <- rote Pille  rechts wenn AUS
 *
 * Klick auf die ganze Zeile flippt den Wert.
 */
public final class ToggleSwitch extends AbstractButton {

    private boolean value;
    private final Consumer<Boolean> onChange;
    private final String label;

    public ToggleSwitch(int x, int y, int w, int h, String label,
                        boolean initial, Consumer<Boolean> onChange) {
        super(x, y, w, h, Component.literal(label));
        this.label = label;
        this.value = initial;
        this.onChange = onChange;
    }

    public boolean getValue() { return value; }
    public void setValue(boolean v) { this.value = v; }

    @Override
    public void onPress(InputWithModifiers input) {
        value = !value;
        onChange.accept(value);
    }

    @Override
    protected void renderContents(GuiGraphics ctx, int mx, int my, float dt) {
        boolean hover = isHovered();

        // Hintergrund-Card
        int bg = hover ? Theme.BTN_BG_HOVER : Theme.BTN_BG;
        ctx.fill(getX(), getY(), getX() + width, getY() + height, bg);
        Theme.drawBorder(ctx, getX(), getY(), width, height,
                hover ? Theme.INPUT_BORDER_FOCUS : Theme.BTN_BORDER);

        // Label links, vertikal zentriert
        var font = Minecraft.getInstance().font;
        int textY = getY() + (height - font.lineHeight) / 2 + 1;
        ctx.drawString(font, label, getX() + 8, textY, Theme.TEXT_PRIMARY, false);

        // Pille rechts
        int pillW = 26;
        int pillH = Math.min(12, height - 6);
        int pillX = getX() + width - pillW - 8;
        int pillY = getY() + (height - pillH) / 2;

        int pillBg = value ? Theme.TOGGLE_ON_BG : Theme.TOGGLE_OFF_BG;
        ctx.fill(pillX, pillY, pillX + pillW, pillY + pillH, pillBg);
        // Innen-Highlight (1px oben fuer subtile 3D-Wirkung)
        ctx.fill(pillX, pillY, pillX + pillW, pillY + 1, 0x40FFFFFF);

        // Knob
        int knobW = pillH - 2;
        int knobX = value ? (pillX + pillW - knobW - 1) : (pillX + 1);
        int knobY = pillY + 1;
        int knobColor = value ? Theme.TOGGLE_KNOB : Theme.TOGGLE_KNOB_OFF;
        ctx.fill(knobX, knobY, knobX + knobW, knobY + knobW, knobColor);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {
        this.defaultButtonNarrationText(out);
    }
}
