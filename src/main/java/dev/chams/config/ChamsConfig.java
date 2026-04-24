package dev.chams.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.chams.ChamsMod;
import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Persistente Konfiguration für Chams ESP.
 * Wird als JSON unter config/chams-esp.json gespeichert.
 *
 * Felder sind public, damit Gson sie direkt (de)serialisieren kann.
 * Default-Werte werden durch normale Feld-Initialisierung gesetzt –
 * fehlen Felder im gespeicherten JSON, bleiben die Defaults.
 */
public final class ChamsConfig {

    // ---- Master Toggle --------------------------------------------------
    /** Wenn false, wird absolut nichts gerendert – Notfall-Aus ueber Hotkey. */
    public boolean masterEnabled = true;

    // ---- Feature Toggles ------------------------------------------------
    public boolean skinEnabled     = false;
    public boolean skeletonEnabled = false;
    public boolean hitboxEnabled   = false;
    public boolean glowEnabled     = false;
    public boolean tracersEnabled  = false;

    // ---- Skin-Chams Sub-Optionen ---------------------------------------
    public boolean chamsShowArmor = true;
    public boolean chamsShowCapes = false; // Cape-Bug: aus bis proper depth-order funktioniert

    // ---- Skelett-Optionen ----------------------------------------------
    /** "health" = rot→grün je nach HP, "fixed" = {@link #skeletonColor}. */
    public String skeletonColorMode = "health";
    public int skeletonColor = 0x00FFFF; // cyan (nur bei mode=fixed)

    // ---- Glow / Hitbox / Tracer Farben ---------------------------------
    public int glowColor   = 0xFF44AA; // magenta
    public int hitboxColor = 0xFFFF00; // gelb
    public int tracerColor = 0xFFFFFF; // weiss

    // ---- Distance Color ------------------------------------------------
    /** Wenn true, wird die Farbe zwischen close/far je nach Distanz interpoliert. */
    public boolean distanceColorEnabled = false;
    public int   distanceColorClose = 0xFF2222; // rot fuer nahe Spieler
    public int   distanceColorFar   = 0x22FF22; // gruen fuer weit entfernte
    public float distanceColorMax   = 50f;      // ab dieser Distanz = farColor

    // ---- Chroma (animierter Rainbow-Cycle) -----------------------------
    public boolean chromaSkeleton = false;
    public boolean chromaHitbox   = false;
    public boolean chromaGlow     = false;
    public boolean chromaTracer   = false;
    /** Zyklen pro Sekunde (0.5 = ein Regenbogen alle 2s). */
    public float chromaSpeed = 0.3f;

    // ---- Team Color ----------------------------------------------------
    /** Uebernimmt die Scoreboard-Team-Farbe des Zielspielers, falls gesetzt. */
    public boolean teamColorEnabled = false;

    // ---- Range Filter --------------------------------------------------
    /** Wenn true, werden nur Spieler innerhalb rangeMaxBlocks gerendert. */
    public boolean rangeEnabled   = false;
    public float   rangeMaxBlocks = 64f;

    // ---- FOV Limit -----------------------------------------------------
    /** Wenn true, werden nur Spieler innerhalb des sichtbaren Kegels gerendert. */
    public boolean fovLimitEnabled = false;
    /** Oeffnungswinkel des Kegels in Grad (1..180). */
    public float   fovLimitDegrees = 90f;

    // ---- Hotkeys (GLFW Key-Codes) --------------------------------------
    public int hotkeySkin      = GLFW.GLFW_KEY_G;
    public int hotkeySkeleton  = GLFW.GLFW_KEY_H;
    public int hotkeyHitbox    = GLFW.GLFW_KEY_J;
    public int hotkeyGlow      = GLFW.GLFW_KEY_K;
    public int hotkeyTracers   = GLFW.GLFW_KEY_Y;
    public int hotkeyMaster    = GLFW.GLFW_KEY_RIGHT_CONTROL;
    public int hotkeyOpenMenu  = GLFW.GLFW_KEY_RIGHT_SHIFT;

    // ====================================================================
    //  Laden / Speichern
    // ====================================================================

    private static final Path PATH =
            FabricLoader.getInstance().getConfigDir().resolve("chams-esp.json");

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    private static ChamsConfig INSTANCE;

    public static ChamsConfig get() {
        if (INSTANCE == null) INSTANCE = load();
        return INSTANCE;
    }

    private static ChamsConfig load() {
        try {
            if (Files.exists(PATH)) {
                String json = Files.readString(PATH);
                ChamsConfig loaded = GSON.fromJson(json, ChamsConfig.class);
                if (loaded != null) return loaded;
            }
        } catch (Exception e) {
            ChamsMod.LOGGER.warn("Konnte Config nicht laden, nutze Defaults: {}", e.getMessage());
        }
        ChamsConfig fresh = new ChamsConfig();
        fresh.save();
        return fresh;
    }

    public void save() {
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(this));
        } catch (Exception e) {
            ChamsMod.LOGGER.error("Config-Speichern fehlgeschlagen", e);
        }
    }
}
