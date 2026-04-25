package dev.chams.render;

import dev.chams.config.ChamsConfig;
import dev.chams.config.ChamsConfig.FriendEntry;
import dev.chams.config.ChamsConfig.FriendMode;
import dev.chams.config.FriendRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.scores.PlayerTeam;

import java.awt.Color;

/**
 * Zentrale Farb-Aufloesung fuer Skelett, Hitbox, Glow und Tracer.
 *
 * Prioritaeten (hoch -&gt; niedrig):
 *   0. Friend-HIGHLIGHT (per-Spieler Override; HIDE wird in {@link PlayerFilter} behandelt)
 *   1. Chroma (pro Feature toggelbar)
 *   2. Team-Farbe aus dem Scoreboard (global toggelbar)
 *   3. Distance-Color (global toggelbar)
 *   4. Feature-spezifischer Modus (z.B. Skelett Health-Farbe)
 *   5. Feste Farbe aus der Config
 */
public final class ColorResolver {

    public enum Feature { SKELETON, HITBOX, GLOW, TRACER, BOX_2D }

    private ColorResolver() {}

    public static int resolve(Feature feature,
                              AbstractClientPlayer target,
                              AbstractClientPlayer self,
                              ChamsConfig cfg) {
        // 0. Friend-HIGHLIGHT: per-Spieler Override schlaegt alles.
        // HIDE wird hier nicht behandelt - das macht der Filter, der den Spieler
        // gar nicht erst in die Render-Liste laesst.
        if (cfg.friendsEnabled && target != null) {
            FriendEntry fe = FriendRegistry.find(target.getGameProfile().name());
            if (fe != null && fe.mode == FriendMode.HIGHLIGHT) {
                return fe.highlightColor;
            }
        }

        // 1. Chroma
        if (isChroma(feature, cfg)) {
            return chroma(cfg.chromaSpeed);
        }

        // 2. Team-Farbe
        if (cfg.teamColorEnabled) {
            Integer teamRgb = teamColor(target);
            if (teamRgb != null) return teamRgb;
        }

        // 3. Distance-Color
        if (cfg.distanceColorEnabled && self != null) {
            double dsq = target.distanceToSqr(self);
            double d = Math.sqrt(dsq);
            float t = (float) Mth.clamp(d / Math.max(cfg.distanceColorMax, 0.01), 0.0, 1.0);
            return lerpRgb(cfg.distanceColorClose, cfg.distanceColorFar, t);
        }

        // 4 + 5. Feature-Default
        return defaultColor(feature, target, cfg);
    }

    /** Holt die Team-Farbe als 0xRRGGBB, oder null wenn kein Team / keine Farbe. */
    private static Integer teamColor(AbstractClientPlayer player) {
        PlayerTeam team = player.getTeam();
        if (team == null) return null;
        ChatFormatting fmt = team.getColor();
        if (fmt == null) return null;
        Integer rgb = fmt.getColor();
        return rgb; // kann null sein (z.B. RESET)
    }

    // ------------------------------------------------------------------
    //  Hilfsmethoden
    // ------------------------------------------------------------------

    private static boolean isChroma(Feature f, ChamsConfig cfg) {
        return switch (f) {
            case SKELETON -> cfg.chromaSkeleton;
            case HITBOX   -> cfg.chromaHitbox;
            case GLOW     -> cfg.chromaGlow;
            case TRACER   -> cfg.chromaTracer;
            case BOX_2D   -> cfg.chromaBox2d;
        };
    }

    private static int defaultColor(Feature f, AbstractClientPlayer target, ChamsConfig cfg) {
        return switch (f) {
            case SKELETON -> skeletonBase(target, cfg);
            case HITBOX   -> cfg.hitboxColor;
            case GLOW     -> cfg.glowColor;
            case TRACER   -> cfg.tracerColor;
            case BOX_2D   -> cfg.box2dColor;
        };
    }

    private static int skeletonBase(AbstractClientPlayer target, ChamsConfig cfg) {
        if ("fixed".equalsIgnoreCase(cfg.skeletonColorMode)) {
            return cfg.skeletonColor;
        }
        // Health-Mode (Default): rot -> gruen je nach HP
        float pct = Mth.clamp(target.getHealth() / target.getMaxHealth(), 0f, 1f);
        int r = (int) ((1f - pct) * 255f);
        int g = (int) (pct * 255f);
        int b = 25;
        return (r << 16) | (g << 8) | b;
    }

    /** HSV-Rainbow abhaengig von der Systemzeit. {@code speed} in Zyklen/Sekunde. */
    private static int chroma(float speed) {
        float t = (System.nanoTime() / 1_000_000_000f) * Math.max(speed, 0.01f);
        float hue = t - (float) Math.floor(t);
        return Color.HSBtoRGB(hue, 1f, 1f) & 0x00FFFFFF;
    }

    /** Lineare RGB-Interpolation zwischen zwei 24-bit Farben. */
    private static int lerpRgb(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = (int) (ar + (br - ar) * t);
        int g = (int) (ag + (bg - ag) * t);
        int bl= (int) (ab + (bb - ab) * t);
        return (r << 16) | (g << 8) | bl;
    }

    /** Splittet 0xRRGGBB in float[3] rgb [0..1]. */
    public static float[] toFloats(int rgb) {
        return new float[] {
                ((rgb >> 16) & 0xFF) / 255f,
                ((rgb >>  8) & 0xFF) / 255f,
                ( rgb        & 0xFF) / 255f
        };
    }
}
