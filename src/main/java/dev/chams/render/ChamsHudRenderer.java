package dev.chams.render;

import dev.chams.config.ChamsConfig;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Zweiteiliger HUD-Renderer:
 *
 * <ol>
 *   <li>{@link WorldRenderEvents#END_MAIN} – nachdem die Welt fertig gerendert ist
 *       (aber vor Hand/GUI). Wir projizieren hier die Hitbox-Ecken aller sichtbaren
 *       Spieler in Screen-Space und legen sie in {@link #projected} ab.</li>
 *   <li>{@link HudElementRegistry} – in der 2D-Phase zeichnen wir dann anhand
 *       dieser projizierten Rechtecke Boxen, Namen, Distanzen und Healthbars.</li>
 * </ol>
 *
 * Projektion erfolgt ueber {@link GameRenderer#projectPointToScreen(Vec3)}, das die
 * gleiche Projection+Camera-Rotation benutzt, die auch Vanilla fuer Waypoints nutzt.
 */
public final class ChamsHudRenderer {

    private static final List<HudPlayer> projected = new ArrayList<>();

    private ChamsHudRenderer() {}

    private static final Identifier HUD_LAYER_ID =
            Identifier.fromNamespaceAndPath("chams_esp", "esp_overlay");

    public static void register() {
        WorldRenderEvents.END_MAIN.register(ChamsHudRenderer::captureProjections);
        // Vor Chat einhaengen: ESP-Overlay zeichnet zuerst, Chat liegt darueber
        // -> Chat / F3 / Bossbar bleiben lesbar.
        HudElementRegistry.attachElementBefore(
                VanillaHudElements.CHAT, HUD_LAYER_ID, ChamsHudRenderer::drawHud);
    }

    // ==================================================================
    //  1) Projection capture (World-Phase)
    // ==================================================================

    private static void captureProjections(WorldRenderContext ctx) {
        projected.clear();

        ChamsConfig cfg = ChamsConfig.get();
        if (!cfg.masterEnabled) return;
        boolean anyHud = cfg.box2dEnabled || cfg.nameTagEnabled
                || cfg.distanceTagEnabled || cfg.healthBar2dEnabled
                || cfg.heldItemEnabled || cfg.armorScoreEnabled;
        if (!anyHud) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        GameRenderer gr = mc.gameRenderer;
        Camera camera = gr.getMainCamera();
        Vec3 camPos = camera.position();
        Vector3fc fwd = camera.forwardVector();
        double fx = fwd.x(), fy = fwd.y(), fz = fwd.z();

        int guiW = mc.getWindow().getGuiScaledWidth();
        int guiH = mc.getWindow().getGuiScaledHeight();

        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);

        List<AbstractClientPlayer> players = PlayerFilter.collect(mc, cfg);
        if (players.isEmpty()) return;

        for (AbstractClientPlayer p : players) {
            // Interpolierte Position fuer smoothes Tracking
            double lx = Mth.lerp(partial, p.xo, p.getX());
            double ly = Mth.lerp(partial, p.yo, p.getY());
            double lz = Mth.lerp(partial, p.zo, p.getZ());
            AABB bb = p.getBoundingBox().move(lx - p.getX(), ly - p.getY(), lz - p.getZ());

            // Skip Spieler hinter der Kamera (Mittelpunkt-Test)
            double cx = (bb.minX + bb.maxX) * 0.5 - camPos.x;
            double cy = (bb.minY + bb.maxY) * 0.5 - camPos.y;
            double cz = (bb.minZ + bb.maxZ) * 0.5 - camPos.z;
            double dot = cx * fx + cy * fy + cz * fz;
            if (dot < 0.05) continue; // hinter / direkt auf der Kamera

            ProjectedBox box = projectAabb(gr, camPos, fx, fy, fz, bb, guiW, guiH);
            if (box == null) continue;
            if (box.maxX <= 0 || box.minX >= guiW) continue;
            if (box.maxY <= 0 || box.minY >= guiH) continue;

            double dist = mc.player.distanceTo(p);
            projected.add(new HudPlayer(p, box, dist));
        }
    }

    /**
     * Projiziert die 8 Eckpunkte einer AABB und liefert das umschliessende
     * Rechteck in GUI-skalierten Pixeln zurueck. Liefert null wenn ALLE
     * Ecken hinter der Kamera liegen.
     *
     * <p>{@link GameRenderer#projectPointToScreen(Vec3)} liefert
     * <b>NDC-Koordinaten</b> im Bereich [-1, +1] (Y nach oben). Wir rechnen
     * das hier auf GUI-Pixel um und flippen Y, damit (0,0) oben links liegt.
     */
    private static ProjectedBox projectAabb(GameRenderer gr, Vec3 camPos,
                                            double fx, double fy, double fz,
                                            AABB bb, int guiW, int guiH) {
        double[][] corners = {
                {bb.minX, bb.minY, bb.minZ},
                {bb.maxX, bb.minY, bb.minZ},
                {bb.minX, bb.maxY, bb.minZ},
                {bb.maxX, bb.maxY, bb.minZ},
                {bb.minX, bb.minY, bb.maxZ},
                {bb.maxX, bb.minY, bb.maxZ},
                {bb.minX, bb.maxY, bb.maxZ},
                {bb.maxX, bb.maxY, bb.maxZ},
        };
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
        boolean anyInFront = false;

        for (double[] c : corners) {
            double dx = c[0] - camPos.x;
            double dy = c[1] - camPos.y;
            double dz = c[2] - camPos.z;
            double dot = dx * fx + dy * fy + dz * fz;
            if (dot < 0.05) continue; // Ecke hinter Kamera -> ueberspringen

            Vec3 ndc = gr.projectPointToScreen(new Vec3(c[0], c[1], c[2]));
            // Bei w<=0 (Kamera-Ebene) liefert transformProject NaN/Inf.
            if (!Double.isFinite(ndc.x) || !Double.isFinite(ndc.y)) continue;

            // NDC ([-1,+1], Y nach oben) -> GUI-Pixel (0..guiW/guiH, Y nach unten)
            float px = (float) ((ndc.x + 1.0) * 0.5 * guiW);
            float py = (float) ((1.0 - ndc.y) * 0.5 * guiH);

            anyInFront = true;
            if (px < minX) minX = px;
            if (py < minY) minY = py;
            if (px > maxX) maxX = px;
            if (py > maxY) maxY = py;
        }
        if (!anyInFront) return null;
        return new ProjectedBox(minX, minY, maxX, maxY);
    }

    // ==================================================================
    //  2) HUD-Draw (2D-Phase)
    // ==================================================================

    private static void drawHud(GuiGraphics ctx, DeltaTracker dt) {
        if (projected.isEmpty()) return;
        ChamsConfig cfg = ChamsConfig.get();
        if (!cfg.masterEnabled) return;

        Minecraft mc = Minecraft.getInstance();
        AbstractClientPlayer self = mc.player;
        if (self == null) return;
        Font font = mc.font;

        for (HudPlayer hp : projected) {
            ProjectedBox box = hp.box;

            // ----- Box -----
            if (cfg.box2dEnabled) {
                int col = ColorResolver.resolve(ColorResolver.Feature.BOX_2D,
                        hp.player, self, cfg);
                drawBoxOutline(ctx, box, col);
            }

            // ----- Health-Bar -----
            if (cfg.healthBar2dEnabled) {
                drawHealthBar(ctx, box, hp.player);
            }

            // ----- Armor-Score (rechts neben Health-Bar) -----
            if (cfg.armorScoreEnabled) {
                int armor = hp.player.getArmorValue();
                String label = "A:" + armor;
                int x = (int) Math.ceil(box.maxX) + 2;
                int y = (int) Math.floor(box.minY);
                ctx.drawString(font, label, x, y, 0xFFB8B8B8, true);
            }

            // ----- Text-Stack ueber/unter der Box -----
            float textCenterX = (box.minX + box.maxX) * 0.5f;
            float textTopY = box.minY - 2f;       // Baseline fuer Texte UEBER der Box
            float textBottomY = box.maxY + 2f;    // Baseline fuer Texte UNTER der Box

            // Distance-Position bestimmen
            String distPos = cfg.distanceTagPosition == null
                    ? "BELOW_BOX" : cfg.distanceTagPosition;
            String distText = cfg.distanceTagEnabled
                    ? formatDistance(hp.distance) : null;
            int distColor = cfg.distanceTagColor;

            // Distance ABOVE_BOX (zuerst, dann Name darueber)
            if (distText != null && "ABOVE_BOX".equals(distPos)) {
                drawCenteredShadowed(ctx, distText, textCenterX, textTopY - 10, distColor);
                textTopY -= 10;
            }

            // ----- Name + Held-Item (oberhalb der Box) -----
            if (cfg.nameTagEnabled) {
                String name = hp.player.getPlainTextName();
                int nameY = (int) (textTopY - 10);

                if (cfg.heldItemEnabled) {
                    ItemStack mainHand = hp.player.getMainHandItem();
                    if (!mainHand.isEmpty()) {
                        int nameW = font.width(name);
                        int iconSize = 16;
                        int gap = 2;
                        int totalW = iconSize + gap + nameW;
                        int iconX = (int) (textCenterX - totalW * 0.5f);
                        int textX = iconX + iconSize + gap;
                        int iconY = nameY - 4; // Icon vertikal etwas hoeher als Text
                        drawHeldItem(ctx, font, mainHand, iconX, iconY);
                        ctx.drawString(font, name, textX, nameY, cfg.nameTagColor, true);
                    } else {
                        drawCenteredShadowed(ctx, name, textCenterX, nameY,
                                cfg.nameTagColor);
                    }
                } else {
                    drawCenteredShadowed(ctx, name, textCenterX, nameY,
                            cfg.nameTagColor);
                }
                textTopY -= 10;
            } else if (cfg.heldItemEnabled) {
                // Held-Item ohne Name: zentriertes Icon
                ItemStack mainHand = hp.player.getMainHandItem();
                if (!mainHand.isEmpty()) {
                    int iconSize = 16;
                    int iconX = (int) (textCenterX - iconSize * 0.5f);
                    int iconY = (int) (textTopY - iconSize - 2);
                    drawHeldItem(ctx, font, mainHand, iconX, iconY);
                    textTopY -= iconSize + 2;
                }
            }

            // Distance BELOW_BOX (unter der Box)
            if (distText != null && "BELOW_BOX".equals(distPos)) {
                drawCenteredShadowed(ctx, distText, textCenterX, textBottomY, distColor);
            }

            // Distance INSIDE_BOX_TOP (oben innen, halbtransparenter BG)
            if (distText != null && "INSIDE_BOX_TOP".equals(distPos)) {
                int textW = font.width(distText);
                int x = (int) (textCenterX - textW * 0.5f);
                int y = (int) box.minY + 2;
                ctx.fill(x - 2, y - 1, x + textW + 2, y + font.lineHeight + 1, 0x80000000);
                ctx.drawString(font, distText, x, y, distColor, true);
            }
        }
    }

    /** "12.4m" fuer < 10m, "42m" fuer >= 10m. */
    private static String formatDistance(double d) {
        if (d < 10.0) return String.format(Locale.ROOT, "%.1fm", d);
        return String.format(Locale.ROOT, "%.0fm", d);
    }

    /**
     * Zeichnet ein Item-Icon mit Decorations (Durability-Bar, Stack-Count).
     * {@code renderItem} kuemmert sich intern um den Z-Stack der Item-Geometry,
     * also muessen wir hier nicht selbst pushMatrix/popMatrix drumlegen.
     */
    private static void drawHeldItem(GuiGraphics ctx, Font font, ItemStack stack,
                                     int x, int y) {
        ctx.renderItem(stack, x, y);
        ctx.renderItemDecorations(font, stack, x, y);
    }

    /** Zeichnet 4 1px-Kanten um die Box. */
    private static void drawBoxOutline(GuiGraphics ctx, ProjectedBox box, int colorRgb) {
        int argb = 0xFF000000 | (colorRgb & 0x00FFFFFF);
        int x1 = (int) Math.floor(box.minX);
        int y1 = (int) Math.floor(box.minY);
        int x2 = (int) Math.ceil(box.maxX);
        int y2 = (int) Math.ceil(box.maxY);
        if (x2 - x1 < 1 || y2 - y1 < 1) return;
        ctx.fill(x1, y1,     x2,     y1 + 1, argb); // oben
        ctx.fill(x1, y2 - 1, x2,     y2,     argb); // unten
        ctx.fill(x1, y1,     x1 + 1, y2,     argb); // links
        ctx.fill(x2 - 1, y1, x2,     y2,     argb); // rechts
    }

    /** Vertikaler Balken an der linken Kante der Box, rot -&gt; gruen nach HP. */
    private static void drawHealthBar(GuiGraphics ctx, ProjectedBox box,
                                      AbstractClientPlayer p) {
        float pct = Mth.clamp(p.getHealth() / Math.max(p.getMaxHealth(), 1f), 0f, 1f);

        int x1 = (int) Math.floor(box.minX) - 4;
        int x2 = x1 + 3;
        int y1 = (int) Math.floor(box.minY);
        int y2 = (int) Math.ceil(box.maxY);
        int height = y2 - y1;
        if (height < 2) return;

        // Hintergrund halbtransparent
        ctx.fill(x1, y1, x2, y2, 0xAA000000);

        int r = (int) ((1f - pct) * 255f);
        int g = (int) (pct * 255f);
        int fillArgb = 0xFF000000 | (r << 16) | (g << 8) | 0x22;
        int fillH = Math.max(1, (int) (height * pct));
        ctx.fill(x1, y2 - fillH, x2, y2, fillArgb);
    }

    private static void drawCenteredShadowed(GuiGraphics ctx, String text,
                                             float centerX, float y, int colorRgb) {
        Minecraft mc = Minecraft.getInstance();
        int w = mc.font.width(text);
        int x = (int) (centerX - w * 0.5f);
        int argb = 0xFF000000 | (colorRgb & 0x00FFFFFF);
        ctx.drawString(mc.font, text, x, (int) y, argb, true);
    }

    // ==================================================================
    //  Records
    // ==================================================================

    private record ProjectedBox(float minX, float minY, float maxX, float maxY) {}
    private record HudPlayer(AbstractClientPlayer player, ProjectedBox box, double distance) {}
}
