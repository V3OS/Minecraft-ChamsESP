package dev.chams.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
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
 * Ein {@link SubmitNodeCollector}, der alle Modell-/Modellteil-/Item-Submits
 * an eine NO_DEPTH_TEST-Variante des jeweils eingehenden RenderTypes umleitet.
 *
 * Der Klon übernimmt Shader, VertexFormat, Cull, Blend und Textur-Bindings
 * 1:1 aus dem Original – nur Tiefentest und Depth-Write werden deaktiviert.
 * Dadurch werden Body, Rüstung (incl. Hose) und Hand-Items korrekt durch
 * Wände sichtbar, ohne Vertex-Format-Fehler oder falsche Cull-Richtungen.
 */
public final class ChamsSubmitNodeCollector implements SubmitNodeCollector {

    private static final int FULL_BRIGHT = 15728880;

    private final MultiBufferSource.BufferSource buffers;

    public ChamsSubmitNodeCollector(MultiBufferSource.BufferSource buffers) {
        this.buffers = buffers;
    }

    @Override
    public OrderedSubmitNodeCollector order(int order) {
        return this;
    }

    // ==================================================================
    //  Hilfsmethode: RenderType für Chams holen.
    //  Versucht zuerst den Pipeline-Klon (preserves shader/cull/blend/
    //  vertex-format), fällt sonst auf den generischen ENTITY_SNIPPET-
    //  basierten Typ zurück (nur wenn Textur bekannt ist).
    // ==================================================================

    private RenderType chamsRt(RenderType rt) {
        RenderType cloned = ChamsRenderTypeTransform.cloneWithNoDepth(rt);
        if (cloned != null) return cloned;

        // Fallback: generische Entity-Pipeline mit extrahierter Textur
        Optional<Identifier> tex = ChamsRenderTypeTransform.extractTexture(rt);
        return tex.map(ChamsRenderTypes::entityAlways).orElse(null);
    }

    // ==================================================================
    //  BODY / ARMOR: submitModel + submitModelPart
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
        // Enchantment-Glint überspringen (statisches violettes Muster)
        Optional<Identifier> tex = ChamsRenderTypeTransform.extractTexture(rt);
        if (tex.isPresent() && ChamsRenderTypeTransform.isGlintTexture(tex.get())) return;

        RenderType crt = chamsRt(rt);
        if (crt == null) return;

        // Pose sofort auftragen, da wir nicht auf Mojang's Batch-Schritt warten
        applyAnim(model, state);

        // color=0 = "kein Tint" im neuen Equipment-System; -1 = weiß (kein Tint)
        int effectiveColor = (color == 0) ? -1 : color;

        VertexConsumer vc = buffers.getBuffer(crt);
        model.root().render(pose, vc, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, effectiveColor);
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
        if (tex.isPresent() && ChamsRenderTypeTransform.isGlintTexture(tex.get())) return;

        RenderType crt = chamsRt(rt);
        if (crt == null) return;

        int effectiveColor = (color == 0) ? -1 : color;

        VertexConsumer vc = buffers.getBuffer(crt);
        part.render(pose, vc, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, effectiveColor);
    }

    /**
     * Trägt den aktuellen RenderState auf das Modell auf (Arme, Beine, Kopf,
     * Cape …). Wird von Mojang normalerweise im deferred Batch gemacht;
     * wir brauchen es sofort vor dem Rendern.
     */
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
    //  ITEMS in der Hand (BakedQuads – flache 2-D-Items wie Schwerter,
    //  Bögen, Leiter …)
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
        if (tex.isPresent() && ChamsRenderTypeTransform.isGlintTexture(tex.get())) return;

        RenderType crt = chamsRt(rt);
        if (crt == null) return;

        VertexConsumer vc = buffers.getBuffer(crt);
        PoseStack.Pose last = pose.last();

        for (int i = 0; i < quads.size(); i++) {
            BakedQuad quad = quads.get(i);
            int tint = (tintColors != null && i < tintColors.length) ? tintColors[i] : -1;
            float r, g, b, a;
            if (tint == -1 || tint == 0) {
                r = g = b = a = 1.0f;
            } else {
                r = ((tint >> 16) & 0xFF) / 255.0f;
                g = ((tint >>  8) & 0xFF) / 255.0f;
                b = ( tint        & 0xFF) / 255.0f;
                a = ((tint >> 24) & 0xFF) / 255.0f;
                if (a == 0f) a = 1f;
            }
            vc.putBulkData(last, quad, r, g, b, a, FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        }
    }

    // ==================================================================
    //  CUSTOM GEOMETRY – 3-D-Items (Schild, Dreizack, Block-Items …)
    // ==================================================================

    @Override
    public void submitCustomGeometry(PoseStack p, RenderType rt,
                                     SubmitNodeCollector.CustomGeometryRenderer renderer) {
        Optional<Identifier> tex = ChamsRenderTypeTransform.extractTexture(rt);
        if (tex.isPresent() && ChamsRenderTypeTransform.isGlintTexture(tex.get())) return;

        RenderType crt = chamsRt(rt);
        if (crt == null) return;

        VertexConsumer vc = buffers.getBuffer(crt);
        renderer.render(p.last(), vc);
    }

    // ==================================================================
    //  Alles andere ignorieren (Shadow, NameTag, Flame, Blöcke, Partikel)
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
