package dev.chams.gui.widgets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/**
 * Schlichter Button im Theme-Style.
 */
public final class PrimaryButton extends AbstractButton {

    private final Runnable onClick;
    private final String label;
    private final boolean accent;

    public PrimaryButton(int x, int y, int w, int h, String label,
                         boolean accent, Runnable onClick) {
        super(x, y, w, h, Component.literal(label));
        this.label = label;
        this.onClick = onClick;
        this.accent = accent;
    }

    @Override
    public void onPress(InputWithModifiers input) { onClick.run(); }

    @Override
    protected void renderContents(GuiGraphics ctx, int mx, int my, float dt) {
        boolean hover = isHovered();
        int bg = accent
                ? (hover ? 0xFF4FA8C7 : 0xFF3D8AA8)
                : (hover ? Theme.BTN_BG_HOVER : Theme.BTN_BG);
        ctx.fill(getX(), getY(), getX() + width, getY() + height, bg);
        Theme.drawBorder(ctx, getX(), getY(), width, height,
                hover ? Theme.INPUT_BORDER_FOCUS : Theme.BTN_BORDER);

        var font = Minecraft.getInstance().font;
        int textW = font.width(label);
        int textX = getX() + (width - textW) / 2;
        int textY = getY() + (height - font.lineHeight) / 2 + 1;
        ctx.drawString(font, label, textX, textY, Theme.TEXT_PRIMARY, false);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {
        this.defaultButtonNarrationText(out);
    }
}
