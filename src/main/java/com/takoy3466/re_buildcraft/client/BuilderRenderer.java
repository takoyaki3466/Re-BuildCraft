package com.takoy3466.re_buildcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.takoy3466.re_buildcraft.api.client.QuarryRenderer;
import com.takoy3466.re_buildcraft.builders.BuilderBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.Vec3;

/** ビルダーの描画: 作業範囲のしま模様のレーザーと、建設ロボット (旧 TileBuilder の Box.createLasers と EntityRobot)。 */
public class BuilderRenderer extends AreaRenderer<BuilderBlockEntity> {
    public BuilderRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(BuilderBlockEntity builder, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        super.render(builder, partialTick, poseStack, buffers, packedLight, packedOverlay);
        Vec3 robot = builder.getRobotPosition();
        if (robot != null) {
            QuarryRenderer.renderRobot(robot, builder.getRobotTarget(), builder.getRobotLaserPower(), builder.getBlockPos(),
                    poseStack, buffers);
        }
    }
}
