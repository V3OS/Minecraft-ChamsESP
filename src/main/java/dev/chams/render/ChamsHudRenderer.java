package dev.chams.render;

import dev.chams.config.ChamsConfig;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
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
        HudElementRegistry.addLast(HUD_LAYER_ID, ChamsHudRenderer::drawHud);
    }

    // ==================================================================
    //  1) Projection capture (World-Phase)
    // ==================================================================

    private static void captureProjections(WorldRenderContext ctx) {
        projected.clear();

        ChamsConfig cfg = ChamsConfig.get();
        if (!cfg.masterEnabled) return;
        boolean anyHud = cfg.box2dEnabled || cfg.nameTagEnabled
                || cfg.distanceTagEnabled || cfg.healthBar2dEnabled;
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
        int screenW = mc.getWindow().getScreenWidth();
        int screenH = mc.getWindow().getScreenHeight();
        double sx = screenW > 0 ? (double) guiW / screenW : 1.0;
        double sy = screenH > 0 ? (double) guiH / screenH : 1.0;

        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);

        List<AbstractClientPlayer> players = new ArrayList<>(mc.level.players());
        players.remove(mc.player);
        players.removeIf(p -> !p.isAlive());

        if (cfg.rangeEnabled) {
            double maxSq = (double) cfg.rangeMaxBlocks * (double) cfg.rangeMaxBlocks;
            players.removeIf(p -> p.distanceToSqr(mc.player) > maxSq);
        }
        if (cfg.fovLimitEnabled) {
            applyFovFilter(players, camPos, fx, fy, fz, cfg.fovLimitDegrees);
        }

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

            ProjectedBox box = projectAabb(gr, camPos, fx, fy, fz, bb, sx, sy);
            if (box == null) continue;
            if (box.maxX <= 0 || box.minX >= guiW) continue;
            if (box.maxY <= 0 || box.minY >= guiH) continue;

            double dist = mc.player.distanceTo(p);
            projected.add(new HudPlayer(p, box, dist));
        }
    }

    private static void applyFovFilter(List<AbstractClientPlayer> players,
                                       Vec3 camPos,
                                       double lx, double ly, double lz,
                                       float fovDegrees) {
        double half = Math.max(1.0, Math.min(180.0, fovDegrees)) * 0.5;
        double cosMax = Math.cos(Math.toRadians(half));
        players.removeIf(p -> {
            double dx = p.getX() - camPos.x;
            double dy = p.getY() + p.getBbHeight() * 0.5 - camPos.y;
            double dz = p.getZ() - camPos.z;
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (len < 0.01) return false;
            double cosA = (dx * lx + dy * ly + dz * lz) / len;
            return cosA < cosMax;
        });
    }

    /**
     * Projiziert die 8 Eckpunkte einer AABB und liefert das umschliessende
     * Rechteck in GUI-skalierten Pixeln zurueck. Liefert null wenn ALLE
     * Ecken hinter der Kamera liegen.
     */
    private static ProjectedBox projectAabb(GameRenderer gr, Vec3 camPos,
                                            double fx, double fy, double fz,
                                            AABB bb, double sx, double sy) {
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

            Vec3 screen = gr.projectPointToScreen(new Vec3(c[0], c[1], c[2]));
            // projectPointToScreen liefert Pixel-Koordinaten im physischen
            // Framebuffer; auf GUI-Skala umrechnen.
            float px = (float) (screen.x * sx);
            float py = (float) (screen.y * sy);
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

        for (HudPlayer hp : projected) {
            ProjectedBox box = hp.box;

            if (cfg.box2dEnabled) {
                int col = ColorResolver.resolve(ColorResolver.Feature.BOX_2D,
                        hp.player, self, cfg);
                drawBoxOutline(ctx, box, col);
            }

            if (cfg.healthBar2dEnabled) {
                drawHealthBar(ctx, box, hp.player);
            }

            float textCenterX = (box.minX + box.maxX) * 0.5f;
            float textTopY = box.minY - 2f;

            if (cfg.nameTagEnabled) {
                String name = hp.player.getPlainTextName();
                drawCenteredShadowed(ctx, name, textCenterX, textTopY - 10,
                        cfg.nameTagColor);
                textTopY -= 10;
            }
            if (cfg.distanceTagEnabled) {
                String s = String.format(Locale.ROOT, "%.1fm", hp.distance);
                drawCenteredShadowed(ctx, s, textCenterX, textTopY - 10,
                        cfg.distanceTagColor);
            }
        }
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
