package com.takoy3466.re_buildcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.takoy3466.re_buildcraft.ReBuildcraft;
import com.takoy3466.re_buildcraft.api.client.BoxRenderer;
import com.takoy3466.re_buildcraft.builders.PathMarkerBlockEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 緑マーカーの描画。青マーカーと同じ目安線に加え、つながった緑マーカー同士を青いレーザーで結ぶ
 * (旧 TilePathMarker.createLaserAndConnect の laser_1)。1 本の線は位置の小さい側だけが描く。
 */
public class PathMarkerRenderer implements BlockEntityRenderer<PathMarkerBlockEntity> {
    private static final ResourceLocation LASER = ReBuildcraft.id("textures/entity/laser_1.png");
    private static final float HALF_WIDTH = 0.5F / 16.0F;

    private final MarkerRenderer markerRenderer;

    public PathMarkerRenderer(BlockEntityRendererProvider.Context context) {
        this.markerRenderer = new MarkerRenderer(context);
    }

    @Override
    public void render(PathMarkerBlockEntity marker, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        markerRenderer.render(marker, partialTick, poseStack, buffers, packedLight, packedOverlay);
        BlockPos pos = marker.getBlockPos();
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutout(LASER));
        for (BlockPos link : marker.getLinks()) {
            if (pos.compareTo(link) < 0) {
                Vec3 base = Vec3.atLowerCornerOf(pos);
                BoxRenderer.renderBeam(poseStack, consumer, LightTexture.FULL_BRIGHT, Vec3.atCenterOf(pos).subtract(base),
                        Vec3.atCenterOf(link).subtract(base), HALF_WIDTH);
            }
        }
    }

    @Override
    public boolean shouldRenderOffScreen(PathMarkerBlockEntity marker) {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(PathMarkerBlockEntity marker) {
        return AABB.INFINITE;
    }
}
