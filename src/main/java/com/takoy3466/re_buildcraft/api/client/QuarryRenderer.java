package com.takoy3466.re_buildcraft.api.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.takoy3466.re_buildcraft.ReBuildcraft;
import com.takoy3466.re_buildcraft.api.laser.LaserBlockEntity;
import com.takoy3466.re_buildcraft.api.quarry.MechanicalArm;
import com.takoy3466.re_buildcraft.api.quarry.QuarryArea;
import com.takoy3466.re_buildcraft.api.quarry.QuarryBlockEntity;
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
 * クアーリーのドリルアーム・建設ロボット・作業範囲の縞模様のレーザーの描画。
 * 元実装: {@code EntityMechanicalArm} が出していた 4 本の {@code EntityBlock}、{@code EntityRobot} + {@code RenderRobot}、
 * {@code Box.createLasers(LaserKind.Stripes)}。CE の改良型クアーリーもこのレンダラーを使う。
 */
public class QuarryRenderer<T extends QuarryBlockEntity> implements BlockEntityRenderer<T> {
    private static final ResourceLocation DRILL_TEXTURE = ReBuildcraft.id("textures/block/quarry/drill.png");
    private static final ResourceLocation HEAD_TEXTURE = ReBuildcraft.id("textures/block/quarry/drill_head.png");
    private static final ResourceLocation STRIPES_TEXTURE = ReBuildcraft.id("textures/block/quarry/stripes.png");
    private static final ResourceLocation ROBOT_TEXTURE = ReBuildcraft.id("textures/entity/robot.png");
    private static final ResourceLocation[] BEAM_TEXTURES = new ResourceLocation[LaserBlockEntity.BEAM_STAGE_COUNT];
    /** アームの棒の太さ (旧 EntityBlock の 0.5 x 0.5)。 */
    private static final float BAR_SIZE = 0.5F;
    private static final double HEAD_OFFSET = 0.4;
    private static final float HEAD_SIZE = 0.2F;
    private static final float HEAD_HEIGHT = 1.0F;
    private static final double BAR_OFFSET = 0.25;
    private static final float STRIPE_HALF_WIDTH = 0.05F;
    private static final float ROBOT_HALF_SIZE = 0.25F;
    private static final float ROBOT_BEAM_HALF_WIDTH = 0.5F / 16.0F;

    static {
        for (int stage = 0; stage < BEAM_TEXTURES.length; stage++) {
            BEAM_TEXTURES[stage] = ReBuildcraft.id("textures/entity/laser_" + (stage + 1) + ".png");
        }
    }

    public QuarryRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(T quarry, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        BlockPos origin = quarry.getBlockPos();
        QuarryArea area = quarry.getArea();
        if (area != null && quarry.isBuildingFrame()) {
            renderAreaOutline(area, origin, poseStack, buffers);
        }
        MechanicalArm arm = quarry.getArm();
        if (arm != null) {
            renderArm(arm, origin, poseStack, buffers, packedLight);
        }
        Vec3 robot = quarry.getRobotPosition();
        if (robot != null && quarry.isBuildingFrame()) {
            renderRobot(quarry, robot, origin, poseStack, buffers);
        }
    }

    private static void renderAreaOutline(QuarryArea area, BlockPos origin, PoseStack poseStack, MultiBufferSource buffers) {
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutout(STRIPES_TEXTURE));
        BoxRenderer.renderLaserBox(poseStack, consumer, LightTexture.FULL_BRIGHT, Vec3.atLowerCornerOf(origin),
                area.minX(), area.minY(), area.minZ(), area.maxX(), area.maxY(), area.maxZ(), STRIPE_HALF_WIDTH);
    }

    /** 旧 EntityMechanicalArm.updatePosition と同じ配置で、X・Z の棒、縦の棒、ドリルヘッドを描く。 */
    private static void renderArm(MechanicalArm arm, BlockPos origin, PoseStack poseStack, MultiBufferSource buffers, int light) {
        double ox = origin.getX();
        double oy = origin.getY();
        double oz = origin.getZ();
        VertexConsumer drill = buffers.getBuffer(RenderType.entityCutout(DRILL_TEXTURE));
        float baseY = (float) (arm.baseY() - oy);
        // X 方向の棒: レールの高さで、ヘッドの Z 位置に沿って動く
        float xStart = (float) (arm.originX() - ox);
        float xBarZ = (float) (arm.headZ() + BAR_OFFSET - oz);
        BoxRenderer.render(poseStack.last(), drill, light, xStart, baseY, xBarZ,
                xStart + (float) arm.lengthX(), baseY + BAR_SIZE, xBarZ + BAR_SIZE);
        // Z 方向の棒: ヘッドの X 位置に沿って動く
        float zStart = (float) (arm.originZ() - oz);
        float zBarX = (float) (arm.headX() + BAR_OFFSET - ox);
        BoxRenderer.render(poseStack.last(), drill, light, zBarX, baseY, zStart,
                zBarX + BAR_SIZE, baseY + BAR_SIZE, zStart + (float) arm.lengthZ());
        // 縦の棒: レールからヘッドの上まで
        float yBottom = (float) (arm.headY() + HEAD_HEIGHT - oy);
        if (baseY > yBottom) {
            BoxRenderer.render(poseStack.last(), drill, light, zBarX, yBottom, xBarZ, zBarX + BAR_SIZE, baseY, xBarZ + BAR_SIZE);
        }
        VertexConsumer head = buffers.getBuffer(RenderType.entityCutout(HEAD_TEXTURE));
        float headX = (float) (arm.headX() + HEAD_OFFSET - ox);
        float headY = (float) (arm.headY() - oy);
        float headZ = (float) (arm.headZ() + HEAD_OFFSET - oz);
        BoxRenderer.render(poseStack.last(), head, light, headX, headY, headZ, headX + HEAD_SIZE, headY + HEAD_HEIGHT, headZ + HEAD_SIZE);
    }

    private static void renderRobot(QuarryBlockEntity quarry, Vec3 robot, BlockPos origin, PoseStack poseStack, MultiBufferSource buffers) {
        Vec3 local = robot.subtract(Vec3.atLowerCornerOf(origin));
        VertexConsumer body = buffers.getBuffer(RenderType.entityCutout(ROBOT_TEXTURE));
        BoxRenderer.render(poseStack.last(), body, LightTexture.FULL_BRIGHT,
                (float) local.x - ROBOT_HALF_SIZE, (float) local.y - ROBOT_HALF_SIZE, (float) local.z - ROBOT_HALF_SIZE,
                (float) local.x + ROBOT_HALF_SIZE, (float) local.y + ROBOT_HALF_SIZE, (float) local.z + ROBOT_HALF_SIZE);
        BlockPos target = quarry.getRobotTarget();
        if (target != null) {
            int stage = Math.min(BEAM_TEXTURES.length - 1, Math.max(0, (int) Math.ceil(quarry.getRobotLaserPower()) - 1));
            VertexConsumer beam = buffers.getBuffer(RenderType.entityCutout(BEAM_TEXTURES[stage]));
            BoxRenderer.renderBeam(poseStack, beam, LightTexture.FULL_BRIGHT, local,
                    Vec3.atCenterOf(target).subtract(Vec3.atLowerCornerOf(origin)), ROBOT_BEAM_HALF_WIDTH);
        }
    }

    @Override
    public boolean shouldRenderOffScreen(T quarry) {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(T quarry) {
        QuarryArea area = quarry.getArea();
        if (area == null) {
            return new AABB(quarry.getBlockPos());
        }
        double minY = quarry.getArm() == null ? area.minY() : Math.min(area.minY(), quarry.getArm().headY());
        return new AABB(area.minX(), minY, area.minZ(), area.maxX() + 1, area.maxY() + 1, area.maxZ() + 1)
                .minmax(new AABB(quarry.getBlockPos()));
    }
}
