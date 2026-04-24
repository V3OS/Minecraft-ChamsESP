package dev.chams.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.chams.config.ChamsConfig;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.List;
import java.util.Optional;

/**
 * Ein {@link SubmitNodeCollector}, der Modell-/Modellteil-/Item-Submits
 * an eine NO_DEPTH_TEST-Variante des eingehenden RenderTypes umleitet.
 *
 * Filter (aus {@link ChamsConfig}):
 * <ul>
 *   <li><b>Glint</b> wird immer geskippt (statisches violettes Muster)</li>
 *   <li><b>Capes</b> werden geskippt wenn {@code chamsShowCapes} aus ist
 *       (Cape würde sonst in Render-Order vor dem Skin landen)</li>
 *   <li><b>Rüstung</b> wird geskippt wenn {@code chamsShowArmor} aus ist</li>
 * </ul>
 *
 * Bei aktivem <b>Glow</b> wird der Tint durch {@code glowColor} ersetzt,
 * damit der komplette Spieler in der Glow-Farbe leuchtet.
 */
public final class ChamsSubmitNodeCollector implements SubmitNodeCollector {

    private static final int FULL_BRIGHT = 15728880;

    private final MultiBufferSource.BufferSource buffers;
    private final ChamsConfig cfg;

    /**
     * Aktuell zu verwendende Glow-Farbe (0xRRGGBB).
     * Wird vom {@link ChamsRenderer} vor jedem Spieler-Submit via
     * {@link #setCurrentGlowColor(int)} gesetzt, damit Chroma und
     * Distance-Color auch bei Glow berücksichtigt werden.
     * Default: Wert aus Config (Rückwärtskompatibilität).
     */
    private int currentGlowColor;

    public ChamsSubmitNodeCollector(MultiBufferSource.BufferSource buffers) {
        this.buffers = buffers;
        this.cfg = ChamsConfig.get();
        this.currentGlowColor = cfg.glowColor;
    }

    /** Pro Spieler aufgerufen, um Chroma/Distance-Glow zu übergeben. */
    public void setCurrentGlowColor(int rgb) {
        this.currentGlowColor = rgb & 0x00FFFFFF;
    }

    @Override
    public OrderedSubmitNodeCollector order(int order) {
        return this;
    }

    // ==================================================================
    //  Texture-Path-Heuristiken
    // ==================================================================

    private static boolean isCapeTexture(Identifier id) {
        if (id == null) return false;
        String p = id.getPath();
        return p.contains("capes/") || p.contains("cape.png") || p.contains("textures/entity/cape");
    }

    private static boolean isArmorTexture(Identifier id) {
        if (id == null) return false;
        String p = id.getPath();
        // Vanilla Armor:        textures/models/armor/...
        // Neues Equipment-Sys:  textures/entity/equipment/humanoid/...
        return p.contains("models/armor/") || p.contains("/equipment/");
    }

    /** Liefert null wenn dieser Submit übersprungen werden soll. */
    private RenderType resolveChamsRt(RenderType rt, Optional<Identifier> tex) {
        if (tex.isPresent()) {
            Identifier id = tex.get();
            if (ChamsRenderTypeTransform.isGlintTexture(id)) return null;
            if (!cfg.chamsShowCapes  && isCapeTexture(id))   return null;
            if (!cfg.chamsShowArmor  && isArmorTexture(id))  return null;
        }
        RenderType cloned = ChamsRenderTypeTransform.cloneWithNoDepth(rt);
        if (cloned != null) return cloned;
        return tex.map(ChamsRenderTypes::entityAlways).orElse(null);
    }

    /** Liefert den Tint, der an model/part.render() geht. */
    private int applyGlow(int color) {
        if (cfg.glowEnabled) {
            // Alpha auf voll setzen, RGB aus aktueller Glow-Farbe (Chroma/Distance-aware)
            return 0xFF000000 | (currentGlowColor & 0x00FFFFFF);
        }
        return (color == 0) ? -1 : color;
    }

    // ==================================================================
    //  BODY / ARMOR
    // ==================================================================

    @Override
    public <S> void submitModel(Model<? super S> model,
                                S state,
                                PoseStack pose,
                                RenderType rt,
                                int light,
                                int overlay,
                                int color,
                                TextureAtlasSprite sprite,
                                int outline,
                                ModelFeatureRenderer.CrumblingOverlay crumbling) {
        Optional<Identifier> tex = ChamsRenderTypeTransform.extractTexture(rt);
        RenderType crt = resolveChamsRt(rt, tex);
        if (crt == null) return;

        applyAnim(model, state);
        int effColor = applyGlow(color);

        VertexConsumer vc = buffers.getBuffer(crt);
        model.root().render(pose, vc, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, effColor);
    }

    @Override
    public void submitModelPart(ModelPart part,
                                PoseStack pose,
                                RenderType rt,
                                int light,
                                int overlay,
                                TextureAtlasSprite sprite,
                                boolean b1,
                                boolean b2,
                                int color,
                                ModelFeatureRenderer.CrumblingOverlay crumbling,
                                int outline) {
        Optional<Identifier> tex = ChamsRenderTypeTransform.extractTexture(rt);
        RenderType crt = resolveChamsRt(rt, tex);
        if (crt == null) return;

        int effColor = applyGlow(color);

        VertexConsumer vc = buffers.getBuffer(crt);
        part.render(pose, vc, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, effColor);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void applyAnim(Model<?> model, Object state) {
        if (model instanceof EntityModel entityModel && state instanceof EntityRenderState ers) {
            try {
                entityModel.setupAnim(ers);
            } catch (ClassCastException ignored) {
                // Modell erwartet spezifischeren State-Typ → aktuelle Pose beibehalten
            }
        }
    }

    // ==================================================================
    //  ITEMS in der Hand (flache BakedQuads)
    // ==================================================================

    @Override
    public void submitItem(PoseStack pose,
                           ItemDisplayContext ctx,
                           int light,
                           int overlay,
                           int outline,
                           int[] tintColors,
                           List<BakedQuad> quads,
                           RenderType rt,
                           ItemStackRenderState.FoilType foilType) {
        if (quads == null || quads.isEmpty()) return;

        Optional<Identifier> tex = ChamsRenderTypeTransform.extractTexture(rt);
        // Glint auf Items auch skippen
        if (tex.isPresent() && ChamsRenderTypeTransform.isGlintTexture(tex.get())) return;

        RenderType crt = ChamsRenderTypeTransform.cloneWithNoDepth(rt);
        if (crt == null && tex.isPresent()) crt = ChamsRenderTypes.entityAlways(tex.get());
        if (crt == null) return;

        VertexConsumer vc = buffers.getBuffer(crt);
        PoseStack.Pose last = pose.last();

        // Glow tint überschreibt alle Quad-Tints
        boolean glow = cfg.glowEnabled;
        float gr = 1f, gg = 1f, gb = 1f;
        if (glow) {
            gr = ((currentGlowColor >> 16) & 0xFF) / 255f;
            gg = ((currentGlowColor >>  8) & 0xFF) / 255f;
            gb = ( currentGlowColor        & 0xFF) / 255f;
        }

        for (int i = 0; i < quads.size(); i++) {
            BakedQuad quad = quads.get(i);
            float r, g, b, a;
            if (glow) {
                r = gr; g = gg; b = gb; a = 1f;
            } else {
                int tint = (tintColors != null && i < tintColors.length) ? tintColors[i] : -1;
                if (tint == -1 || tint == 0) {
                    r = g = b = a = 1.0f;
                } else {
                    r = ((tint >> 16) & 0xFF) / 255.0f;
                    g = ((tint >>  8) & 0xFF) / 255.0f;
                    b = ( tint        & 0xFF) / 255.0f;
                    a = ((tint >> 24) & 0xFF) / 255.0f;
                    if (a == 0f) a = 1f;
                }
            }
            vc.putBulkData(last, quad, r, g, b, a, FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        }
    }

    // ==================================================================
    //  CUSTOM GEOMETRY (3-D Items – Shield, Trident, Block-Items)
    // ==================================================================

    @Override
    public void submitCustomGeometry(PoseStack p, RenderType rt,
                                     SubmitNodeCollector.CustomGeometryRenderer renderer) {
        Optional<Identifier> tex = ChamsRenderTypeTransform.extractTexture(rt);
        if (tex.isPresent() && ChamsRenderTypeTransform.isGlintTexture(tex.get())) return;

        RenderType crt = ChamsRenderTypeTransform.cloneWithNoDepth(rt);
        if (crt == null && tex.isPresent()) crt = ChamsRenderTypes.entityAlways(tex.get());
        if (crt == null) return;

        VertexConsumer vc = buffers.getBuffer(crt);
        renderer.render(p.last(), vc);
    }

    // ==================================================================
    //  Ignore-Liste
    // ==================================================================

    @Override public void submitShadow(PoseStack p, float r, List<EntityRenderState.ShadowPiece> l) {}
    @Override public void submitNameTag(PoseStack p, Vec3 v, int i, Component c, boolean b,
                                        int bg, double dist, CameraRenderState crs) {}
    @Override public void submitText(PoseStack p, float x, float y, FormattedCharSequence text,
                                     boolean dropShadow, Font.DisplayMode mode,
                                     int bgColor, int color, int packedLight, int outline) {}
    @Override public void submitFlame(PoseStack p, EntityRenderState s, Quaternionf q) {}
    @Override public void submitLeash(PoseStack p, EntityRenderState.LeashState ls) {}
    @Override public void submitBlock(PoseStack p, BlockState bs, int a, int b, int c) {}
    @Override public void submitMovingBlock(PoseStack p, MovingBlockRenderState s) {}
    @Override public void submitBlockModel(PoseStack p, RenderType rt, BlockStateModel m,
                                           float r, float g, float b,
                                           int l1, int l2, int l3) {}
    @Override public void submitParticleGroup(SubmitNodeCollector.ParticleGroupRenderer r) {}
}
