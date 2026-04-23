package dev.chams;

import com.mojang.blaze3d.platform.InputConstants;
import dev.chams.render.ChamsRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChamsMod implements ClientModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("chams-esp");

    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("chams-esp", "esp"));

    private static boolean skinEnabled     = false;
    private static boolean skeletonEnabled = false;

    private static KeyMapping toggleSkinKey;
    private static KeyMapping toggleSkeletonKey;

    @Override
    public void onInitializeClient() {
        LOGGER.info("Chams ESP geladen (MC 1.21.11)");

        toggleSkinKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.chams_esp.toggle_skin",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_G,
                CATEGORY
        ));

        toggleSkeletonKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.chams_esp.toggle_skeleton",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_H,
                CATEGORY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleSkinKey.consumeClick()) {
                skinEnabled = !skinEnabled;
                notify(client, "Skin-Chams", skinEnabled);
            }
            while (toggleSkeletonKey.consumeClick()) {
                skeletonEnabled = !skeletonEnabled;
                notify(client, "Skeleton", skeletonEnabled);
            }
        });

        ChamsRenderer.register();
    }

    private static void notify(Minecraft client, String name, boolean on) {
        if (client.player != null) {
            client.player.displayClientMessage(
                    Component.literal("\u00A76[Chams ESP] \u00A7r" + name + ": " + (on ? "\u00A7aAN" : "\u00A7cAUS")),
                    true
            );
        }
        LOGGER.info("{} -> {}", name, on ? "AN" : "AUS");
    }

    public static boolean isSkinEnabled() {
        return skinEnabled;
    }

    public static boolean isSkeletonEnabled() {
        return skeletonEnabled;
    }
}
