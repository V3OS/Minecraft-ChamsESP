package dev.chams.gui.widgets;

import com.mojang.blaze3d.platform.InputConstants;
import dev.chams.ChamsMod;
import dev.chams.ChamsMod.HotkeyDef;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Capture-Widget zum Umbelegen von Hotkeys.
 *
 * <p>Verhalten:
 * <ul>
 *   <li>Ruhend: zeigt das Label des aktuell gebundenen Keys (z.B. {@code "G"}).</li>
 *   <li>Klick: wechselt in den Capture-Modus, Label wird zu {@code "> Press a key…"}.</li>
 *   <li>Im Capture-Modus:
 *     <ul>
 *       <li>{@code ESC}: bricht ab, behaelt alte Bindung.</li>
 *       <li>{@code BACKSPACE}: resettet auf Default.</li>
 *       <li>{@code DELETE}: unbindet (Hotkey deaktiviert).</li>
 *       <li>Jede andere Taste: bindet diese Taste.</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <p>Es darf nur ein Binder gleichzeitig im Capture-Modus sein. Der statische
 * {@link #activeBinder} traegt den aktuell aktiven; das Screen-Routing prueft
 * diesen, um Tasten an den Binder weiterzureichen, statt sie an den Fokus oder
 * an das Screen-Default ({@code ESC} = close) zu liefern.
 */
public final class HotkeyBinder extends AbstractButton {

    private static HotkeyBinder activeBinder = null;

    private final HotkeyDef def;
    private boolean capturing = false;

    public HotkeyBinder(int x, int y, int w, int h, HotkeyDef def) {
        super(x, y, w, h, Component.literal(def.label()));
        this.def = def;
    }

    public static HotkeyBinder getActiveBinder() { return activeBinder; }

    public boolean isCapturing() { return capturing; }

    public void cancelCapture() {
        capturing = false;
        if (activeBinder == this) activeBinder = null;
    }

    /**
     * Wird vom Screen aufgerufen, wenn dieser Binder im Capture-Modus ist.
     * Liefert {@code true}, wenn der Tastendruck verbraucht wurde.
     */
    public boolean handleKey(int keyCode) {
        if (!capturing) return false;
        switch (keyCode) {
            case GLFW.GLFW_KEY_ESCAPE -> {
                // Abbruch ohne Aenderung
                cancelCapture();
            }
            case GLFW.GLFW_KEY_BACKSPACE -> {
                // Auf Default zuruecksetzen
                ChamsMod.rebindHotkey(def, def.defaultKey());
                cancelCapture();
            }
            case GLFW.GLFW_KEY_DELETE -> {
                // Unbinden
                ChamsMod.rebindHotkey(def, InputConstants.UNKNOWN.getValue());
                cancelCapture();
            }
            default -> {
                ChamsMod.rebindHotkey(def, keyCode);
                cancelCapture();
            }
        }
        return true;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        if (capturing) {
            // Erneuter Klick = Abbruch
            cancelCapture();
            return;
        }
        // Anderen aktiven Binder abbrechen, falls einer existiert
        if (activeBinder != null && activeBinder != this) {
            activeBinder.cancelCapture();
        }
        capturing = true;
        activeBinder = this;
    }

    @Override
    protected void renderContents(GuiGraphics ctx, int mx, int my, float dt) {
        boolean hover = isHovered();
        int bg = capturing ? 0xFF3D8AA8 // Aktiv = Akzent
                : (hover ? Theme.BTN_BG_HOVER : Theme.BTN_BG);
        ctx.fill(getX(), getY(), getX() + width, getY() + height, bg);
        Theme.drawBorder(ctx, getX(), getY(), width, height,
                capturing ? Theme.INPUT_BORDER_FOCUS
                        : (hover ? Theme.INPUT_BORDER_FOCUS : Theme.BTN_BORDER));

        var font = Minecraft.getInstance().font;
        int textY = getY() + (height - font.lineHeight) / 2 + 1;

        // Label links
        ctx.drawString(font, def.label(), getX() + 8, textY,
                Theme.TEXT_SECONDARY, false);

        // Aktueller / Capture-Hint rechts
        String shown;
        int color;
        if (capturing) {
            shown = "> Press a key...";
            color = Theme.TEXT_HEADING;
        } else if (def.mapping().isUnbound()) {
            shown = "[NONE]";
            color = Theme.TEXT_DIM;
        } else {
            shown = def.mapping().getTranslatedKeyMessage().getString();
            color = Theme.TEXT_ACCENT;
        }
        int valW = font.width(shown);
        ctx.drawString(font, shown, getX() + width - valW - 8, textY, color, false);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {
        this.defaultButtonNarrationText(out);
    }
}
