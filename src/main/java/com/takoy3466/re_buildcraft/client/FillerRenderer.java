package com.takoy3466.re_buildcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.takoy3466.re_buildcraft.ReBuildcraft;
import com.takoy3466.re_buildcraft.api.client.BoxRenderer;
import com.takoy3466.re_buildcraft.builders.FillerBlockEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** フィラーの作業範囲をしま模様のレーザーで囲む。元実装: {@code TileFiller.updateEntity} の {@code box.createLasers(LaserKind.Stripes)}。 */
public class FillerRenderer implements BlockEntityRenderer<FillerBlockEntity> {
    private static final ResourceLocation STRIPES = ReBuildcraft.id("textures/block/quarry/stripes.png");
    private static final float HALF_WIDTH = 0.05F;

    public FillerRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(FillerBlockEntity filler, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        BoundingBox box = filler.getBox();
        if (box != null) {
            BoxRenderer.renderLaserBox(poseStack, buffers.getBuffer(RenderType.entityCutout(STRIPES)), LightTexture.FULL_BRIGHT,
                    Vec3.atLowerCornerOf(filler.getBlockPos()), box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ(),
                    HALF_WIDTH);
        }
    }

    @Override
    public boolean shouldRenderOffScreen(FillerBlockEntity filler) {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(FillerBlockEntity filler) {
        return AABB.INFINITE;
    }
}
