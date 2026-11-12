package com.takoy3466.re_buildcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.takoy3466.re_buildcraft.Config;
import com.takoy3466.re_buildcraft.ReBuildcraft;
import com.takoy3466.re_buildcraft.api.client.BoxRenderer;
import com.takoy3466.re_buildcraft.builders.MarkerBlockEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * マーカーのレーザー描画。元実装: {@code TileMarker.createLasers} (赤い範囲の枠) と {@code switchSignals} (目安線)。
 * 旧実装はレーザーをエンティティとして出していた。ここでは原点のマーカーの BER が枠を描き、
 * 信号を受けたマーカーがつながっていない軸に沿って、距離ごとに色の違う目安線 (青 15 / 緑 63 / 黄 127 / 赤 255) を描く。
 */
public class MarkerRenderer implements BlockEntityRenderer<MarkerBlockEntity> {
    private static final ResourceLocation RED = ReBuildcraft.id("textures/block/marker/laser_red.png");
    private static final ResourceLocation BLUE = ReBuildcraft.id("textures/block/marker/laser_blue.png");
    private static final ResourceLocation GREEN = ReBuildcraft.id("textures/block/marker/laser_green.png");
    private static final ResourceLocation YELLOW = ReBuildcraft.id("textures/block/marker/laser_yellow.png");
    /** 目安線の区切り (旧 blue / green / yellow / red) と色。 */
    private static final int[] STEPS = {15, 63, 127, 255};
    private static final ResourceLocation[] STEP_TEXTURES = {BLUE, GREEN, YELLOW, RED};
    /** 縦の目安線の長さ (旧 yMax = 32、下に半分)。 */
    private static final int VERTICAL_LENGTH = 32;
    /** Y 軸が未接続のときの縦線 (旧 createLaser(y - 64, 長さ 128))。 */
    private static final int Y_SIGNAL_HALF = 64;
    private static final float HALF_WIDTH = 0.05F;

    public MarkerRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(MarkerBlockEntity marker, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        BoundingBox box = marker.getBox();
        if (box != null) {
            BoxRenderer.renderLaserBox(poseStack, buffers.getBuffer(RenderType.entityCutout(RED)), LightTexture.FULL_BRIGHT,
                    Vec3.atLowerCornerOf(marker.getBlockPos()), box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ(),
                    HALF_WIDTH);
        }
        if (marker.showsSignals()) {
            renderSignals(marker, poseStack, buffers);
        }
    }

    private static void renderSignals(MarkerBlockEntity marker, PoseStack poseStack, MultiBufferSource buffers) {
        Vec3 base = Vec3.atLowerCornerOf(marker.getBlockPos());
        Vec3 center = Vec3.atCenterOf(marker.getBlockPos());
        if (!marker.isAxisLinked(1)) {
            beam(poseStack, buffers.getBuffer(RenderType.entityCutout(BLUE)), base,
                    center.add(0, -Y_SIGNAL_HALF, 0), center.add(0, Y_SIGNAL_HALF, 0));
        }
        int steps = Math.min(STEPS.length, Config.MARKER_SIGNAL_RANGE.get() + 1);
        boolean vertical = Config.MARKER_VERTICAL_LINE.get();
        for (int axis : new int[]{0, 2}) {
            if (marker.isAxisLinked(axis)) {
                continue;
            }
            Vec3 unit = axis == 0 ? new Vec3(1, 0, 0) : new Vec3(0, 0, 1);
            int previous = 0;
            for (int step = 0; step < steps; step++) {
                VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutout(STEP_TEXTURES[step]));
                for (int sign : new int[]{1, -1}) {
                    Vec3 from = center.add(unit.scale(sign * previous));
                    Vec3 to = center.add(unit.scale(sign * STEPS[step]));
                    beam(poseStack, consumer, base, from, to);
                    if (vertical) {
                        beam(poseStack, consumer, base, to.add(0, -VERTICAL_LENGTH / 2.0, 0), to.add(0, VERTICAL_LENGTH / 2.0, 0));
                    }
                }
                previous = STEPS[step];
            }
        }
    }

    private static void beam(PoseStack poseStack, VertexConsumer consumer, Vec3 base, Vec3 start, Vec3 end) {
        BoxRenderer.renderBeam(poseStack, consumer, LightTexture.FULL_BRIGHT, start.subtract(base), end.subtract(base), HALF_WIDTH);
    }

    @Override
    public boolean shouldRenderOffScreen(MarkerBlockEntity marker) {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(MarkerBlockEntity marker) {
        return AABB.INFINITE;
    }
}
