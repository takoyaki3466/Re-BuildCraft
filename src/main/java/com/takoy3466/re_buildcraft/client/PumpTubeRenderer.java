package com.takoy3466.re_buildcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.takoy3466.re_buildcraft.ReBuildcraft;
import com.takoy3466.re_buildcraft.api.client.BoxRenderer;
import com.takoy3466.re_buildcraft.factory.PumpBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;

/**
 * ポンプから液面まで伸びるチューブの描画。旧実装では {@code EntityBlock} (テクスチャ 102) で表示していた。
 * テクスチャを引き伸ばさないよう、1 ブロック分ずつ区切って描く。
 */
public class PumpTubeRenderer implements BlockEntityRenderer<PumpBlockEntity> {
    private static final ResourceLocation TEXTURE = ReBuildcraft.id("textures/block/pump_tube.png");
    private static final float TUBE_MIN = 0.25F;
    private static final float TUBE_MAX = 0.75F;

    public PumpTubeRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(PumpBlockEntity pump, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        float tubeBottom = (float) (pump.getTubeY() - pump.getBlockPos().getY());
        if (tubeBottom >= 0.0F) {
            return;
        }
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutout(TEXTURE));
        for (float top = 0.0F; top > tubeBottom; top -= 1.0F) {
            float bottom = Math.max(top - 1.0F, tubeBottom);
            BoxRenderer.render(poseStack.last(), consumer, packedLight,
                    TUBE_MIN, bottom, TUBE_MIN, TUBE_MAX, top, TUBE_MAX);
        }
    }

    @Override
    public boolean shouldRenderOffScreen(PumpBlockEntity pump) {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(PumpBlockEntity pump) {
        AABB block = new AABB(pump.getBlockPos());
        return block.setMinY(Math.min(block.minY, pump.getTubeY()));
    }
}
