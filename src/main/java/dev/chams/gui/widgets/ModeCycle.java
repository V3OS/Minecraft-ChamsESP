package dev.chams.gui.widgets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Cycle-Button fuer enumartige String-Werte ("health" -&gt; "fixed" -&gt; ...).
 * Klick wechselt zum naechsten Eintrag.
 */
public final class ModeCycle extends AbstractButton {

    private final List<String> values;
    private int idx;
    private final String label;
    private final Function<String, String> displayMapper;
    private final Consumer<String> onChange;

    public ModeCycle(int x, int y, int w, int h, String label,
                     List<String> values, String initial,
                     Function<String, String> displayMapper,
                     Consumer<String> onChange) {
        super(x, y, w, h, Component.literal(label));
        this.label = label;
        this.values = values;
        this.idx = Math.max(0, values.indexOf(initial));
        this.displayMapper = displayMapper;
        this.onChange = onChange;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        idx = (idx + 1) % values.size();
        onChange.accept(values.get(idx));
    }

    @Override
    protected void renderContents(GuiGraphics ctx, int mx, int my, float dt) {
        boolean hover = isHovered();
        ctx.fill(getX(), getY(), getX() + width, getY() + height,
                hover ? Theme.BTN_BG_HOVER : Theme.BTN_BG);
        Theme.drawBorder(ctx, getX(), getY(), width, height,
                hover ? Theme.INPUT_BORDER_FOCUS : Theme.BTN_BORDER);

        var font = Minecraft.getInstance().font;
        int textY = getY() + (height - font.lineHeight) / 2 + 1;

        // Label links
        ctx.drawString(font, label, getX() + 8, textY, Theme.TEXT_SECONDARY, false);

        // Aktueller Wert rechts
        String shown = displayMapper.apply(values.get(idx));
        int valW = font.width(shown);
        ctx.drawString(font, shown, getX() + width - valW - 8, textY,
                Theme.TEXT_ACCENT, false);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {
        this.defaultButtonNarrationText(out);
    }
}
