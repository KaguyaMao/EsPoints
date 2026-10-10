package com.example.espoints.client;

import com.example.espoints.ESPointsMod;
import com.example.espoints.config.TacticalMapJsonConfig;
import com.example.espoints.tactical.ClientTacticalMarkerState;
import com.example.espoints.tactical.TacticalMarker;
import com.example.espoints.tactical.TacticalMarkerIcons;
import com.example.espoints.tactical.TacticalMarkerType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 世界空间 billboard 渲染战术标点（ESPoints 贴图）。
 * <ul>
 *   <li>按贴图类型批绘，减少 texture bind / tessellate 次数</li>
 *   <li>客户端距离裁剪 + 末段 alpha 淡出</li>
 *   <li>关闭深度测试以便隔墙可见（战术标点需求）</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public final class TacticalMarkerWorldRenderer {

    private static final float BASE_HALF_SIZE = 0.55F;
    private static final float MIN_SCALE = 0.4F;
    private static final float MAX_SCALE = 1.4F;
    /** 每帧最多绘制数量，防止极端情况下卡顿。 */
    private static final int MAX_DRAW = 48;

    private record DrawItem(double dx, double dy, double dz, float half, int argb) {}

    private TacticalMarkerWorldRenderer() {
    }

    /**
     * 保留旧实现供回归对照，但不再注册到 Forge 事件总线。
     * 世界标点统一交给 Ping Wheel 渲染，避免同一标点绘制两次。
     */
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) {
            return;
        }
        List<TacticalMarker> markers = ClientTacticalMarkerState.getMarkers();
        if (markers.isEmpty()) {
            return;
        }

        TacticalMapJsonConfig config = TacticalMapJsonConfig.getInstance();
        double maxDist = config.getTacticalMarkerMaxRenderDistance();
        double maxDistSq = maxDist * maxDist;
        long duration = config.getTacticalMarkerDurationMillis();
        long fadeMs = config.getTacticalMarkerFadeMillis();
        long now = System.currentTimeMillis();

        Vec3 cam = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();

        // 按类型分桶批绘
        EnumMap<TacticalMarkerType, List<DrawItem>> batches = new EnumMap<>(TacticalMarkerType.class);
        int drawn = 0;
        for (TacticalMarker marker : markers) {
            if (drawn >= MAX_DRAW) {
                break;
            }
            float opacity = opacityOf(marker, duration, fadeMs, now);
            if (opacity <= 0.02F) {
                continue;
            }
            double dx = marker.x() - cam.x;
            double dy = marker.y() - cam.y;
            double dz = marker.z() - cam.z;
            double distSq = dx * dx + dy * dy + dz * dz;
            if (distSq > maxDistSq || distSq < 1.0e-6) {
                continue;
            }
            double dist = Math.sqrt(distSq);
            float half = BASE_HALF_SIZE * scaleForDistance(dist, maxDist);
            int argb = colorFor(marker.type(), opacity);
            batches.computeIfAbsent(marker.type(), t -> new ArrayList<>(8))
                .add(new DrawItem(dx, dy, dz, half, argb));
            drawn++;
        }
        if (batches.isEmpty()) {
            return;
        }

        var cameraRot = event.getCamera().rotation();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest(); // 隔墙可见
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);

        for (Map.Entry<TacticalMarkerType, List<DrawItem>> entry : batches.entrySet()) {
            ResourceLocation tex = TacticalMarkerIcons.textureFor(entry.getKey());
            RenderSystem.setShaderTexture(0, tex);
            for (DrawItem item : entry.getValue()) {
                poseStack.pushPose();
                poseStack.translate(item.dx, item.dy, item.dz);
                poseStack.mulPose(cameraRot);
                poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
                drawQuad(poseStack, item.half, item.argb);
                poseStack.popPose();
            }
        }

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    private static float opacityOf(TacticalMarker marker, long duration, long fadeMs, long now) {
        if (marker.type().isPersistentUntilRemoved()) {
            return 1.0F;
        }
        long age = Math.max(0L, now - marker.createdAtMillis());
        long remaining = duration - age;
        if (remaining <= 0L) {
            return 0.0F;
        }
        // 仅在最后 fadeMs 内淡出；之前保持满不透明
        if (remaining >= fadeMs) {
            return 1.0F;
        }
        return Mth.clamp(remaining / (float) Math.max(1L, fadeMs), 0.0F, 1.0F);
    }

    private static float scaleForDistance(double dist, double maxDist) {
        double t = Mth.clamp(dist / Math.max(1.0D, maxDist), 0.0D, 1.0D);
        // 平滑近大远小
        t = t * t;
        return (float) (MAX_SCALE + (MIN_SCALE - MAX_SCALE) * t);
    }

    private static int colorFor(TacticalMarkerType type, float opacity) {
        // 敌方贴图本身已是红色：用白调制保留贴图色；指令标用类型色
        int base = switch (type) {
            case ATTACK_HERE -> TacticalMarkerType.ATTACK_HERE.getColor();
            case DEFEND_HERE -> TacticalMarkerType.DEFEND_HERE.getColor();
            case ENEMY_INFANTRY, ENEMY_TANK, ENEMY_IFV,
                 ENEMY_LIGHT_VEHICLE, ENEMY_HELICOPTER -> 0xFFFFFFFF;
            default -> TacticalMarkerIcons.isEnemyUnit(type) ? 0xFFFFFFFF : type.getColor();
        };
        int a = Mth.clamp(Math.round(255 * opacity), 0, 255);
        return (base & 0x00FFFFFF) | (a << 24);
    }

    private static void drawQuad(PoseStack pose, float half, int argb) {
        Matrix4f mat = pose.last().pose();
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        buf.vertex(mat, -half, -half, 0).uv(0f, 1f).color(r, g, b, a).endVertex();
        buf.vertex(mat, -half, half, 0).uv(0f, 0f).color(r, g, b, a).endVertex();
        buf.vertex(mat, half, half, 0).uv(1f, 0f).color(r, g, b, a).endVertex();
        buf.vertex(mat, half, -half, 0).uv(1f, 1f).color(r, g, b, a).endVertex();
        tess.end();
    }
}
