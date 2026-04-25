package dev.chams.render;

import dev.chams.config.ChamsConfig;
import dev.chams.config.FriendRegistry;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Gemeinsamer Filter fuer 3D- ({@link ChamsRenderer}) und 2D-Renderer
 * ({@link ChamsHudRenderer}).
 *
 * <p>Liefert die Liste sichtbarer Spieler nach den Schritten:
 * <ol>
 *   <li>Self entfernen</li>
 *   <li>Tote Spieler entfernen</li>
 *   <li>Friend-HIDE-Eintraege entfernen</li>
 *   <li>Range-Filter (falls aktiv)</li>
 *   <li>FOV-Filter (falls aktiv)</li>
 * </ol>
 */
public final class PlayerFilter {

    private PlayerFilter() {}

    /**
     * Sammelt alle relevanten Spieler nach Anwendung aller globalen Filter.
     * Gibt eine leere Liste zurueck, falls Welt/Player nicht verfuegbar.
     */
    public static List<AbstractClientPlayer> collect(Minecraft mc, ChamsConfig cfg) {
        if (mc.level == null || mc.player == null) return Collections.emptyList();

        List<AbstractClientPlayer> players = new ArrayList<>(mc.level.players());
        players.remove(mc.player);
        players.removeIf(p -> !p.isAlive());

        // Friend-HIDE: komplett aus 3D + 2D ausblenden
        if (cfg.friendsEnabled) {
            players.removeIf(p -> FriendRegistry.isHidden(p.getGameProfile().name()));
        }

        // Range-Filter
        if (cfg.rangeEnabled) {
            double maxSq = (double) cfg.rangeMaxBlocks * (double) cfg.rangeMaxBlocks;
            players.removeIf(p -> p.distanceToSqr(mc.player) > maxSq);
        }

        // FOV-Filter
        if (cfg.fovLimitEnabled && !players.isEmpty()) {
            Camera camera = mc.gameRenderer.getMainCamera();
            applyFovFilter(players, camera, camera.position(), cfg.fovLimitDegrees);
        }

        return players;
    }

    private static void applyFovFilter(List<AbstractClientPlayer> players,
                                       Camera camera, Vec3 camPos, float fovDegrees) {
        Vector3fc fwd = camera.forwardVector();
        double lx = fwd.x(), ly = fwd.y(), lz = fwd.z();
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
}
