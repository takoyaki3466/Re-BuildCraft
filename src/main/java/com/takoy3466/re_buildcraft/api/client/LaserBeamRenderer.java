package com.takoy3466.re_buildcraft.api.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.takoy3466.re_buildcraft.ReBuildcraft;
import com.takoy3466.re_buildcraft.api.laser.LaserBlockEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * レーザーのビーム描画。元実装: {@code buildcraft.core.RenderLaser} / {@code RenderEnergyLaser}。
 * 照射量に応じて laser_1 - laser_4 のテクスチャを使い分ける。
 */
public class LaserBeamRenderer<T extends LaserBlockEntity> implements BlockEntityRenderer<T> {
    /** 標準ビームの断面の半分の幅 (旧モデルは 1 ピクセル角)。 */
    private static final float BEAM_HALF_WIDTH = 0.5F / 16.0F;
    private static final ResourceLocation[] BEAM_TEXTURES = new ResourceLocation[LaserBlockEntity.BEAM_STAGE_COUNT];

    static {
        for (int stage = 0; stage < BEAM_TEXTURES.length; stage++) {
            BEAM_TEXTURES[stage] = ReBuildcraft.id("textures/entity/laser_" + (stage + 1) + ".png");
        }
    }

    public LaserBeamRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(T laser, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        Vec3 end = laser.getBeamEnd();
        if (end == null) {
            return;
        }
        Vec3 origin = Vec3.atLowerCornerOf(laser.getBlockPos());
        float halfWidth = BEAM_HALF_WIDTH * laser.getSpec().beamWidth();
        BoxRenderer.renderBeam(poseStack, buffers.getBuffer(RenderType.entityCutout(BEAM_TEXTURES[laser.getBeamStage()])),
                LightTexture.FULL_BRIGHT, laser.getBeamStart().subtract(origin), end.subtract(origin), halfWidth);
    }

    @Override
    public boolean shouldRenderOffScreen(T laser) {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(T laser) {
        return new AABB(laser.getBlockPos()).inflate(laser.getSpec().searchRange() + 1);
    }
}
