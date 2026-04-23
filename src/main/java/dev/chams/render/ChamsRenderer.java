package dev.chams.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.chams.ChamsMod;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

public final class ChamsRenderer {

    // Dicke für die Skelett-Linien
    private static final float LINE_WIDTH = 2.0f;

    private ChamsRenderer() {}

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(ChamsRenderer::onRender);
    }

    private static void onRender(WorldRenderContext context) {
        boolean trueChams = ChamsMod.isSkinEnabled();
        boolean skeleton  = ChamsMod.isSkeletonEnabled();
        if (!trueChams && !skeleton) return;

        Minecraft mc = Minecraft.getInstance();
        ClientLevel world = mc.level;
        if (world == null || mc.player == null) return;

        PoseStack matrices = context.matrices();
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 camPos = camera.position();

        float tickDelta = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();

        List<AbstractClientPlayer> players = new ArrayList<>(world.players());
        players.remove(mc.player);
        players.removeIf(p -> !p.isAlive());
        // Hinterste zuerst zeichnen, damit nähere drüberliegen
        players.sort((p1, p2) -> Double.compare(
                p2.distanceToSqr(mc.player),
                p1.distanceToSqr(mc.player)));

        // ====================================================================
        //  1. TRUE CHAMS (Skin durch Wände)
        // ====================================================================
        if (trueChams) {
            renderSkinThroughWalls(mc, matrices, buffers, camPos, tickDelta, players, context);
        }

        // ====================================================================
        //  2. SKELETON ESP
        // ====================================================================
        if (skeleton) {
            renderSkeletonThroughWalls(matrices, buffers, camPos, tickDelta, players);
        }
    }

    // ------------------------------------------------------------------
    //  True Chams: kompletter Spieler durch Wände (Body + Armor + Items)
    //
    //  Statt das Modell selbst zu transformieren, fahren wir die ganze
    //  Entity-Render-Pipeline durch dispatcher.submit(...). Wir schieben
    //  ihr aber einen eigenen SubmitNodeCollector unter, der jeden
    //  eingehenden RenderType gegen unsere NO_DEPTH_TEST-Variante tauscht.
    //  So werden Body, Layer (Rüstung, Cape, Elytra, ...) und Hand-Items
    //  alle ohne Tiefentest gezeichnet - also durch Wände sichtbar.
    // ------------------------------------------------------------------
    private static void renderSkinThroughWalls(Minecraft mc, PoseStack matrices,
                                               MultiBufferSource.BufferSource buffers,
                                               Vec3 camPos, float tickDelta,
                                               List<AbstractClientPlayer> players,
                                               WorldRenderContext context) {
        if (players.isEmpty()) return;

        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        CameraRenderState cameraState = context.worldState().cameraRenderState;
        if (cameraState == null) return;

        ChamsSubmitNodeCollector chamsCollector = new ChamsSubmitNodeCollector(buffers);

        for (AbstractClientPlayer player : players) {
            double x = Mth.lerp(tickDelta, player.xo, player.getX()) - camPos.x;
            double y = Mth.lerp(tickDelta, player.yo, player.getY()) - camPos.y;
            double z = Mth.lerp(tickDelta, player.zo, player.getZ()) - camPos.z;

            AvatarRenderer<AbstractClientPlayer> renderer = dispatcher.getPlayerRenderer(player);

            // Frischen RenderState pro Spieler füllen
            AvatarRenderState state = renderer.createRenderState();
            renderer.extractRenderState(player, state, tickDelta);

            // Ganze Render-Pipeline anstoßen. dispatcher.submit() macht Push/
            // Translate/Layer-Iteration/etc. intern. Layer- und Body-Submits
            // landen in unserem ChamsSubmitNodeCollector, der die
            // NO_DEPTH_TEST-Pipeline anwendet.
            dispatcher.submit(state, cameraState, x, y, z, matrices, chamsCollector);
        }

        // Alles, was im Collector gepuffert wurde, an den Framebuffer spülen
        buffers.endBatch();
    }

    // ------------------------------------------------------------------
    //  Skeleton-ESP (bleibt wie vorher – funktionierte schon)
    // ------------------------------------------------------------------
    private static void renderSkeletonThroughWalls(PoseStack matrices,
                                                   MultiBufferSource.BufferSource buffers,
                                                   Vec3 camPos, float tickDelta,
                                                   List<AbstractClientPlayer> players) {
        for (AbstractClientPlayer player : players) {
            double x = Mth.lerp(tickDelta, player.xo, player.getX()) - camPos.x;
            double y = Mth.lerp(tickDelta, player.yo, player.getY()) - camPos.y;
            double z = Mth.lerp(tickDelta, player.zo, player.getZ()) - camPos.z;

            float healthPct = Mth.clamp(player.getHealth() / player.getMaxHealth(), 0f, 1f);
            float red = 1.0f - healthPct;
            float green = healthPct;
            float blue = 0.1f;

            matrices.pushPose();
            matrices.translate(x, y, z);

            VertexConsumer lineConsumer = buffers.getBuffer(ChamsRenderTypes.linesAlways());
            drawSkeleton(matrices, lineConsumer, player, tickDelta, red, green, blue);

            matrices.popPose();
        }
        buffers.endBatch(ChamsRenderTypes.linesAlways());
    }

    // ------------------------------------------------------------------
    //  Skelett-Logik (unverändert)
    // ------------------------------------------------------------------
    private static void drawSkeleton(PoseStack matrices, VertexConsumer c,
                                     AbstractClientPlayer player, float tickDelta,
                                     float red, float green, float blue) {
        matrices.pushPose();

        float bodyYaw = Mth.lerp(tickDelta, player.yBodyRotO, player.yBodyRot);
        matrices.mulPose(Axis.YN.rotationDegrees(bodyYaw));

        if (player.isCrouching()) {
            matrices.translate(0, -0.2f, 0);
        }

        float limbPos   = player.walkAnimation.position(tickDelta);
        float limbSpeed = Math.min(player.walkAnimation.speed(tickDelta), 1.0f);

        float rightLegPitch = Mth.cos(limbPos * 0.6662f) * 1.4f * limbSpeed;
        float leftLegPitch  = Mth.cos(limbPos * 0.6662f + (float)Math.PI) * 1.4f * limbSpeed;
        float rightArmPitch = Mth.cos(limbPos * 0.6662f + (float)Math.PI) * 1.2f * limbSpeed;
        float leftArmPitch  = Mth.cos(limbPos * 0.6662f) * 1.2f * limbSpeed;

        if (player.isVehicle()) {
            rightLegPitch = -1.4f; leftLegPitch = -1.4f;
            rightArmPitch = -0.2f; leftArmPitch = -0.2f;
        } else if (player.isSleeping()) {
            rightLegPitch = 0; leftLegPitch = 0; rightArmPitch = 0; leftArmPitch = 0;
        }

        float pelvisY = 0.75f, neckY = 1.4f;
        float hipX = 0.15f, shoulderX = 0.3f;
        float armLen = 0.65f, legLen = 0.75f;
        float alpha = 1.0f;

        Matrix4f m = matrices.last().pose();

        line(c, m, 0, pelvisY, 0,  0, neckY, 0, red, green, blue, alpha);
        line(c, m, hipX, pelvisY, 0,  -hipX, pelvisY, 0, red, green, blue, alpha);
        line(c, m, shoulderX, neckY, 0,  -shoulderX, neckY, 0, red, green, blue, alpha);

        float headYaw = Mth.lerp(tickDelta, player.yHeadRotO, player.yHeadRot) - bodyYaw;
        float headPitch = Mth.lerp(tickDelta, player.xRotO, player.getXRot());

        matrices.pushPose();
        matrices.translate(0, neckY, 0);
        matrices.mulPose(Axis.YN.rotationDegrees(headYaw));
        matrices.mulPose(Axis.XP.rotationDegrees(headPitch));

        Matrix4f headM = matrices.last().pose();
        line(c, headM, 0, 0, 0,  0, 0.3f, 0, red, green, blue, alpha);

        // kleine Blickrichtungslinie
        line(c, headM, 0, 0.2f, 0,  0, 0.2f, 0.6f, 0.0f, 1.0f, 1.0f, 0.9f);
        matrices.popPose();

        matrices.pushPose();
        matrices.translate(hipX, pelvisY, 0);
        matrices.mulPose(Axis.XP.rotation(leftLegPitch));
        line(c, matrices.last().pose(), 0, 0, 0,  0, -legLen, 0, red, green, blue, alpha);
        matrices.popPose();

        matrices.pushPose();
        matrices.translate(-hipX, pelvisY, 0);
        matrices.mulPose(Axis.XP.rotation(rightLegPitch));
        line(c, matrices.last().pose(), 0, 0, 0,  0, -legLen, 0, red, green, blue, alpha);
        matrices.popPose();

        matrices.pushPose();
        matrices.translate(shoulderX, neckY, 0);
        matrices.mulPose(Axis.XP.rotation(leftArmPitch));
        line(c, matrices.last().pose(), 0, 0, 0,  0, -armLen, 0, red, green, blue, alpha);
        matrices.popPose();

        matrices.pushPose();
        matrices.translate(-shoulderX, neckY, 0);
        matrices.mulPose(Axis.XP.rotation(rightArmPitch));
        line(c, matrices.last().pose(), 0, 0, 0,  0, -armLen, 0, red, green, blue, alpha);
        matrices.popPose();

        matrices.popPose();
    }

    private static void line(VertexConsumer c, Matrix4f m,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float red, float green, float blue, float alpha) {
        float dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-5f) return;
        dx /= len; dy /= len; dz /= len;

        c.addVertex(m, x1, y1, z1).setColor(red, green, blue, alpha).setNormal(dx, dy, dz).setLineWidth(LINE_WIDTH);
        c.addVertex(m, x2, y2, z2).setColor(red, green, blue, alpha).setNormal(dx, dy, dz).setLineWidth(LINE_WIDTH);
    }
}
