package dev.chams.gui.widgets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/**
 * Tab-Button mit gelben Active-Indikator unten.
 * Aktive Tabs heben sich durch andere BG-Farbe + Underline ab.
 */
public final class TabButton extends AbstractButton {

    private final boolean active;
    private final Runnable onClick;
    private final String label;

    public TabButton(int x, int y, int w, int h, String label,
                     boolean active, Runnable onClick) {
        super(x, y, w, h, Component.literal(label));
        this.label = label;
        this.active = active;
        this.onClick = onClick;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        if (!active) onClick.run();
    }

    @Override
    protected void renderContents(GuiGraphics ctx, int mx, int my, float dt) {
        boolean hover = isHovered();
        int bg;
        if (active) bg = Theme.TAB_BG_ACTIVE;
        else if (hover) bg = Theme.TAB_BG_HOVER;
        else bg = Theme.TAB_BG;

        ctx.fill(getX(), getY(), getX() + width, getY() + height, bg);

        // Active-Indikator: 2px gelbe Linie unten
        if (active) {
            ctx.fill(getX(), getY() + height - 2, getX() + width, getY() + height,
                    Theme.TAB_INDICATOR);
        }

        // Label zentriert
        var font = Minecraft.getInstance().font;
        int textW = font.width(label);
        int textX = getX() + (width - textW) / 2;
        int textY = getY() + (height - font.lineHeight) / 2 + 1;
        int color = active ? Theme.TAB_INDICATOR
                : (hover ? Theme.TEXT_PRIMARY : Theme.TEXT_SECONDARY);
        ctx.drawString(font, label, textX, textY, color, false);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {
        this.defaultButtonNarrationText(out);
    }
}
