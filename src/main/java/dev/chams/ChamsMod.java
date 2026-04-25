package dev.chams;

import com.mojang.blaze3d.platform.InputConstants;
import dev.chams.config.ChamsConfig;
import dev.chams.gui.ChamsConfigScreen;
import dev.chams.render.ChamsHudRenderer;
import dev.chams.render.ChamsRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChamsMod implements ClientModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("chams-esp");

    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("chams_esp", "esp"));

    private static KeyMapping keySkin;
    private static KeyMapping keySkeleton;
    private static KeyMapping keyHitbox;
    private static KeyMapping keyGlow;
    private static KeyMapping keyTracers;
    private static KeyMapping keyBox2d;
    private static KeyMapping keyMaster;
    private static KeyMapping keyOpenMenu;

    @Override
    public void onInitializeClient() {
        LOGGER.info("Chams ESP geladen (MC 1.21.11)");

        ChamsConfig cfg = ChamsConfig.get();

        keySkin     = register("toggle_skin",     cfg.hotkeySkin);
        keySkeleton = register("toggle_skeleton", cfg.hotkeySkeleton);
        keyHitbox   = register("toggle_hitbox",   cfg.hotkeyHitbox);
        keyGlow     = register("toggle_glow",     cfg.hotkeyGlow);
        keyTracers  = register("toggle_tracers",  cfg.hotkeyTracers);
        keyBox2d    = register("toggle_box2d",    cfg.hotkeyBox2d);
        keyMaster   = register("toggle_master",   cfg.hotkeyMaster);
        keyOpenMenu = register("open_menu",       cfg.hotkeyOpenMenu);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ChamsConfig c = ChamsConfig.get();

            while (keySkin.consumeClick()) {
                c.skinEnabled = !c.skinEnabled;
                c.save();
                notify(client, "Skin-Chams", c.skinEnabled);
            }
            while (keySkeleton.consumeClick()) {
                c.skeletonEnabled = !c.skeletonEnabled;
                c.save();
                notify(client, "Skeleton", c.skeletonEnabled);
            }
            while (keyHitbox.consumeClick()) {
                c.hitboxEnabled = !c.hitboxEnabled;
                c.save();
                notify(client, "Hitbox", c.hitboxEnabled);
            }
            while (keyGlow.consumeClick()) {
                c.glowEnabled = !c.glowEnabled;
                c.save();
                notify(client, "Glow", c.glowEnabled);
            }
            while (keyTracers.consumeClick()) {
                c.tracersEnabled = !c.tracersEnabled;
                c.save();
                notify(client, "Tracers", c.tracersEnabled);
            }
            while (keyBox2d.consumeClick()) {
                c.box2dEnabled = !c.box2dEnabled;
                c.save();
                notify(client, "2D-Box", c.box2dEnabled);
            }
            while (keyMaster.consumeClick()) {
                c.masterEnabled = !c.masterEnabled;
                c.save();
                notify(client, "ESP Master", c.masterEnabled);
            }
            while (keyOpenMenu.consumeClick()) {
                if (client.screen == null) {
                    client.setScreen(new ChamsConfigScreen(null));
                }
            }
        });

        ChamsRenderer.register();
        ChamsHudRenderer.register();
    }

    private static KeyMapping register(String name, int defaultKey) {
        return KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.chams_esp." + name,
                InputConstants.Type.KEYSYM,
                defaultKey,
                CATEGORY
        ));
    }

    private static void notify(Minecraft client, String name, boolean on) {
        if (client.player != null) {
            client.player.displayClientMessage(
                    Component.literal("\u00A76[Chams ESP] \u00A7r" + name + ": " +
                            (on ? "\u00A7aAN" : "\u00A7cAUS")),
                    true
            );
        }
        LOGGER.info("{} -> {}", name, on ? "AN" : "AUS");
    }

    // Backwards-Compat für eventuell alte Aufrufer (nicht mehr benutzt)
    public static boolean isSkinEnabled()     { return ChamsConfig.get().skinEnabled; }
    public static boolean isSkeletonEnabled() { return ChamsConfig.get().skeletonEnabled; }
}
