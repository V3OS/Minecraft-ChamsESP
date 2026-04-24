package dev.chams.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.chams.config.ChamsConfig;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.List;

public final class ChamsRenderer {

    private static final float LINE_WIDTH = 2.0f;

    private ChamsRenderer() {}

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(ChamsRenderer::onRender);
    }

    private static void onRender(WorldRenderContext context) {
        ChamsConfig cfg = ChamsConfig.get();

        // Master-Toggle: alles aus
        if (!cfg.masterEnabled) return;

        boolean trueChams = cfg.skinEnabled;
        boolean skeleton  = cfg.skeletonEnabled;
        boolean hitbox    = cfg.hitboxEnabled;
        boolean tracers   = cfg.tracersEnabled;
        if (!trueChams && !skeleton && !hitbox && !tracers) return;

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

        // Range-Filter
        if (cfg.rangeEnabled) {
            double maxSq = (double) cfg.rangeMaxBlocks * (double) cfg.rangeMaxBlocks;
            players.removeIf(p -> p.distanceToSqr(mc.player) > maxSq);
        }

        // FOV-Filter: nur Spieler im Blickkegel rendern
        if (cfg.fovLimitEnabled && !players.isEmpty()) {
            applyFovFilter(players, camera, camPos, cfg.fovLimitDegrees);
        }

        // Hinterste zuerst → nähere überdecken
        players.sort((p1, p2) -> Double.compare(
                p2.distanceToSqr(mc.player),
                p1.distanceToSqr(mc.player)));

        if (trueChams) {
            renderSkinThroughWalls(mc, matrices, buffers, camPos, tickDelta, players, context);
        }

        if (skeleton || hitbox || tracers) {
            renderLinesThroughWalls(matrices, buffers, camera, camPos, tickDelta, players,
                                    skeleton, hitbox, tracers, cfg);
        }
    }

    // ------------------------------------------------------------------
    //  FOV-Filter
    // ------------------------------------------------------------------
    private static void applyFovFilter(List<AbstractClientPlayer> players,
                                       Camera camera, Vec3 camPos, float fovDegrees) {
        Vector3fc fwd = camera.forwardVector();
        double lx = fwd.x(), ly = fwd.y(), lz = fwd.z();

        double halfFov = Math.max(1.0, Math.min(180.0, fovDegrees)) * 0.5;
        double cosMax  = Math.cos(Math.toRadians(halfFov));

        players.removeIf(p -> {
            double dx = p.getX() - camPos.x;
            double dy = p.getY() + p.getBbHeight() * 0.5 - camPos.y;
            double dz = p.getZ() - camPos.z;
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (len < 0.01) return false; // direkt auf/im Spieler → drin lassen
            double cosA = (dx * lx + dy * ly + dz * lz) / len;
            return cosA < cosMax;
        });
    }

    // ------------------------------------------------------------------
    //  True Chams: kompletter Spieler durch Wände
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
        ChamsConfig cfg = ChamsConfig.get();

        for (AbstractClientPlayer player : players) {
            double x = Mth.lerp(tickDelta, player.xo, player.getX()) - camPos.x;
            double y = Mth.lerp(tickDelta, player.yo, player.getY()) - camPos.y;
            double z = Mth.lerp(tickDelta, player.zo, player.getZ()) - camPos.z;

            // Per-Player Glow-Farbe (berücksichtigt Chroma + Distance)
            int glowCol = ColorResolver.resolve(ColorResolver.Feature.GLOW, player, mc.player, cfg);
            chamsCollector.setCurrentGlowColor(glowCol);

            AvatarRenderer<AbstractClientPlayer> renderer = dispatcher.getPlayerRenderer(player);

            AvatarRenderState state = renderer.createRenderState();
            renderer.extractRenderState(player, state, tickDelta);

            dispatcher.submit(state, cameraState, x, y, z, matrices, chamsCollector);
        }

        buffers.endBatch();
    }

    // ------------------------------------------------------------------
    //  Line-Pass: Skelett und/oder Hitbox (teilen sich den Line-Buffer)
    // ------------------------------------------------------------------
    private static void renderLinesThroughWalls(PoseStack matrices,
                                                MultiBufferSource.BufferSource buffers,
                                                Camera camera,
                                                Vec3 camPos, float tickDelta,
                                                List<AbstractClientPlayer> players,
                                                boolean skeleton, boolean hitbox,
                                                boolean tracers, ChamsConfig cfg) {
        VertexConsumer lineVc = buffers.getBuffer(ChamsRenderTypes.linesAlways());
        AbstractClientPlayer self = Minecraft.getInstance().player;

        // Kamera-Forward einmal pro Frame (fuer Tracer-Anker)
        Vector3fc fwd = camera.forwardVector();
        float fwdX = fwd.x();
        float fwdY = fwd.y();
        float fwdZ = fwd.z();

        for (AbstractClientPlayer player : players) {
            double x = Mth.lerp(tickDelta, player.xo, player.getX()) - camPos.x;
            double y = Mth.lerp(tickDelta, player.yo, player.getY()) - camPos.y;
            double z = Mth.lerp(tickDelta, player.zo, player.getZ()) - camPos.z;

            if (hitbox) {
                int hitCol = ColorResolver.resolve(ColorResolver.Feature.HITBOX, player, self, cfg);
                drawHitbox(matrices, lineVc, player, x, y, z, hitCol);
            }

            if (skeleton) {
                int skCol = ColorResolver.resolve(ColorResolver.Feature.SKELETON, player, self, cfg);
                float[] col = ColorResolver.toFloats(skCol);
                matrices.pushPose();
                matrices.translate(x, y, z);
                drawSkeleton(matrices, lineVc, player, tickDelta, col[0], col[1], col[2]);
                matrices.popPose();
            }

            if (tracers) {
                int trCol = ColorResolver.resolve(ColorResolver.Feature.TRACER, player, self, cfg);
                drawTracer(matrices, lineVc, player, x, y, z, fwdX, fwdY, fwdZ, trCol);
            }
        }
        buffers.endBatch(ChamsRenderTypes.linesAlways());
    }

    // ------------------------------------------------------------------
    //  Tracer: Linie vom unteren Bildschirmrand zum Spieler
    // ------------------------------------------------------------------
    private static void drawTracer(PoseStack matrices, VertexConsumer c,
                                   AbstractClientPlayer player,
                                   double camDx, double camDy, double camDz,
                                   float fwdX, float fwdY, float fwdZ,
                                   int colorRgb) {
        float r = ((colorRgb >> 16) & 0xFF) / 255f;
        float g = ((colorRgb >>  8) & 0xFF) / 255f;
        float b = ( colorRgb        & 0xFF) / 255f;

        // Startpunkt: 1 Block vor der Kamera, 0.35 unter der Horizontalen
        // -> Tracer startet optisch am unteren Bildschirmrand
        float sx = fwdX * 1.0f;
        float sy = fwdY * 1.0f - 0.35f;
        float sz = fwdZ * 1.0f;

        // Endpunkt: Mitte des Spieler-Modells
        float ex = (float) camDx;
        float ey = (float) (camDy + player.getBbHeight() * 0.5f);
        float ez = (float) camDz;

        line(c, matrices.last().pose(), sx, sy, sz, ex, ey, ez, r, g, b, 1f);
    }

    // ------------------------------------------------------------------
    //  Hitbox (AABB um den Spieler)
    // ------------------------------------------------------------------
    private static void drawHitbox(PoseStack matrices, VertexConsumer c,
                                   AbstractClientPlayer player,
                                   double camDx, double camDy, double camDz,
                                   int colorRgb) {
        AABB bb = player.getBoundingBox();
        // AABB aktuell in Welt-Koordinaten. Wir brauchen sie relativ zur Kamera.
        float x1 = (float)(bb.minX - player.getX() + camDx);
        float y1 = (float)(bb.minY - player.getY() + camDy);
        float z1 = (float)(bb.minZ - player.getZ() + camDz);
        float x2 = (float)(bb.maxX - player.getX() + camDx);
        float y2 = (float)(bb.maxY - player.getY() + camDy);
        float z2 = (float)(bb.maxZ - player.getZ() + camDz);

        float r = ((colorRgb >> 16) & 0xFF) / 255f;
        float g = ((colorRgb >>  8) & 0xFF) / 255f;
        float b = ( colorRgb        & 0xFF) / 255f;

        Matrix4f m = matrices.last().pose();

        // 12 Kanten eines Quaders
        // untere 4
        line(c, m, x1,y1,z1, x2,y1,z1, r,g,b,1);
        line(c, m, x2,y1,z1, x2,y1,z2, r,g,b,1);
        line(c, m, x2,y1,z2, x1,y1,z2, r,g,b,1);
        line(c, m, x1,y1,z2, x1,y1,z1, r,g,b,1);
        // obere 4
        line(c, m, x1,y2,z1, x2,y2,z1, r,g,b,1);
        line(c, m, x2,y2,z1, x2,y2,z2, r,g,b,1);
        line(c, m, x2,y2,z2, x1,y2,z2, r,g,b,1);
        line(c, m, x1,y2,z2, x1,y2,z1, r,g,b,1);
        // vertikale 4
        line(c, m, x1,y1,z1, x1,y2,z1, r,g,b,1);
        line(c, m, x2,y1,z1, x2,y2,z1, r,g,b,1);
        line(c, m, x2,y1,z2, x2,y2,z2, r,g,b,1);
        line(c, m, x1,y1,z2, x1,y2,z2, r,g,b,1);
    }

    // ------------------------------------------------------------------
    //  Skeleton-Stickfigur
    // ------------------------------------------------------------------
    private static void drawSkeleton(PoseStack matrices, VertexConsumer c,
                                     AbstractClientPlayer player, float tickDelta,
                                     float red, float green, float blue) {
        matrices.pushPose();

        float bodyYaw = Mth.lerp(tickDelta, player.yBodyRotO, player.yBodyRot);
        matrices.mulPose(Axis.YN.rotationDegrees(bodyYaw));

        if (player.isCrouching()) matrices.translate(0, -0.2f, 0);

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
        float a = 1.0f;

        Matrix4f m = matrices.last().pose();

        line(c, m, 0, pelvisY, 0,  0, neckY, 0, red, green, blue, a);
        line(c, m, hipX, pelvisY, 0,  -hipX, pelvisY, 0, red, green, blue, a);
        line(c, m, shoulderX, neckY, 0,  -shoulderX, neckY, 0, red, green, blue, a);

        float headYaw = Mth.lerp(tickDelta, player.yHeadRotO, player.yHeadRot) - bodyYaw;
        float headPitch = Mth.lerp(tickDelta, player.xRotO, player.getXRot());

        matrices.pushPose();
        matrices.translate(0, neckY, 0);
        matrices.mulPose(Axis.YN.rotationDegrees(headYaw));
        matrices.mulPose(Axis.XP.rotationDegrees(headPitch));

        Matrix4f headM = matrices.last().pose();
        line(c, headM, 0, 0, 0,  0, 0.3f, 0, red, green, blue, a);
        line(c, headM, 0, 0.2f, 0,  0, 0.2f, 0.6f, 0.0f, 1.0f, 1.0f, 0.9f);
        matrices.popPose();

        matrices.pushPose();
        matrices.translate(hipX, pelvisY, 0);
        matrices.mulPose(Axis.XP.rotation(leftLegPitch));
        line(c, matrices.last().pose(), 0, 0, 0,  0, -legLen, 0, red, green, blue, a);
        matrices.popPose();

        matrices.pushPose();
        matrices.translate(-hipX, pelvisY, 0);
        matrices.mulPose(Axis.XP.rotation(rightLegPitch));
        line(c, matrices.last().pose(), 0, 0, 0,  0, -legLen, 0, red, green, blue, a);
        matrices.popPose();

        matrices.pushPose();
        matrices.translate(shoulderX, neckY, 0);
        matrices.mulPose(Axis.XP.rotation(leftArmPitch));
        line(c, matrices.last().pose(), 0, 0, 0,  0, -armLen, 0, red, green, blue, a);
        matrices.popPose();

        matrices.pushPose();
        matrices.translate(-shoulderX, neckY, 0);
        matrices.mulPose(Axis.XP.rotation(rightArmPitch));
        line(c, matrices.last().pose(), 0, 0, 0,  0, -armLen, 0, red, green, blue, a);
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
