package dev.chams.config;

import dev.chams.config.ChamsConfig.FriendEntry;
import dev.chams.config.ChamsConfig.FriendMode;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Lookup-Helper fuer Freunde.
 *
 * <p>Spielernamen sind in Minecraft case-sensitiv (siehe {@code GameProfile#getName()}),
 * fuer User-Komfort akzeptieren wir aber jede Schreibweise: der Cache normalisiert
 * alle Eintraege auf Lowercase.
 *
 * <p>Der Cache wird beim ersten Lookup pro Frame lazy aufgebaut. Wenn die
 * {@link ChamsConfig#friends}-Liste mutiert wurde (Add/Remove/Mode-Switch), muss
 * {@link #invalidate()} aufgerufen werden, sonst zeigt der Cache veraltete Werte.
 */
public final class FriendRegistry {

    private FriendRegistry() {}

    private static Map<String, FriendEntry> CACHE = null;

    /** Erzwingt einen Rebuild beim naechsten {@link #find(String)}-Call. */
    public static void invalidate() {
        CACHE = null;
    }

    /**
     * Findet den Friend-Eintrag fuer einen Spielernamen. Gibt {@code null} zurueck,
     * wenn der Spieler kein Freund ist oder das Friend-System aus ist.
     */
    public static FriendEntry find(String username) {
        if (username == null || username.isEmpty()) return null;
        ChamsConfig cfg = ChamsConfig.get();
        if (!cfg.friendsEnabled) return null;

        Map<String, FriendEntry> cache = CACHE;
        if (cache == null) {
            cache = build(cfg);
            CACHE = cache;
        }
        return cache.get(username.toLowerCase(Locale.ROOT));
    }

    /** {@code true} wenn der Spieler als HIDE markiert ist. */
    public static boolean isHidden(String username) {
        FriendEntry fe = find(username);
        return fe != null && fe.mode == FriendMode.HIDE;
    }

    /** {@code true} wenn der Spieler als HIGHLIGHT markiert ist. */
    public static boolean isHighlighted(String username) {
        FriendEntry fe = find(username);
        return fe != null && fe.mode == FriendMode.HIGHLIGHT;
    }

    private static Map<String, FriendEntry> build(ChamsConfig cfg) {
        Map<String, FriendEntry> out = new HashMap<>();
        if (cfg.friends == null) return out;
        for (FriendEntry fe : cfg.friends) {
            if (fe == null || fe.name == null || fe.name.isBlank()) continue;
            out.put(fe.name.trim().toLowerCase(Locale.ROOT), fe);
        }
        return out;
    }
}
