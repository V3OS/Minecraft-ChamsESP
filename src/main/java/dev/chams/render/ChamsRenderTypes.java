package dev.chams.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

public final class ChamsRenderTypes {

    private ChamsRenderTypes() {}

    // ------------------------------------------------------------------
    //  Lines - Skeleton immer sichtbar (Ignoriert Wände)
    // ------------------------------------------------------------------
    private static final RenderPipeline LINES_PIPELINE =
            RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
                    .withLocation("chams_lines_always")
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST) // Keine Wände blockieren es
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withCull(false)
                    .withDepthWrite(false)
                    .build();

    private static final RenderType LINES_ALWAYS = RenderType.create(
            "chams_lines_always",
            RenderSetup.builder(LINES_PIPELINE).createRenderSetup()
    );

    public static RenderType linesAlways() {
        return LINES_ALWAYS;
    }

    // ------------------------------------------------------------------
    //  Entity-Textur immer sichtbar (True Chams / Skin + Armor durch Wände)
    //
    //  Eigene Pipeline auf Basis von ENTITY_SNIPPET, aber ohne Tiefentest.
    //  -> Das Modell wird einfach immer gezeichnet, Wände blockieren nichts.
    //
    //  WICHTIG:
    //   - .withBlend(TRANSLUCENT): Skin-Layer (Hat) sind semi-transparent und
    //     brauchen Alpha-Blending. Ohne Blend = schwarze Löcher statt Blend.
    //   - cull=false: Armor-Innenseiten sollen nicht culled werden.
    // ------------------------------------------------------------------
    private static final RenderPipeline ENTITY_NO_DEPTH_PIPELINE =
            RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
                    .withLocation("chams_entity_always")
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withCull(true)   // Rückseiten ausblenden: verhindert dass
                    .withDepthWrite(false)  // Cape/Helm-Innenseite durch die
                    .build();              // Vorderseite scheint

    // Cache pro Skin-Textur, damit wir nicht in jedem Frame einen neuen
    // RenderType bauen.
    private static final Map<Identifier, RenderType> ENTITY_TYPE_CACHE = new HashMap<>();

    public static RenderType entityAlways(Identifier texture) {
        RenderType cached = ENTITY_TYPE_CACHE.get(texture);
        if (cached != null) return cached;

        RenderSetup setup = RenderSetup.builder(ENTITY_NO_DEPTH_PIPELINE)
                .withTexture("Sampler0", texture)
                .useLightmap()
                .useOverlay()
                .createRenderSetup();

        RenderType type = RenderType.create("chams_entity_always", setup);
        ENTITY_TYPE_CACHE.put(texture, type);
        return type;
    }
}
