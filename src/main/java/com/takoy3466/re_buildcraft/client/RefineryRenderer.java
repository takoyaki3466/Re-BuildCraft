package com.takoy3466.re_buildcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.takoy3466.re_buildcraft.ReBuildcraft;
import com.takoy3466.re_buildcraft.api.client.BoxRenderer;
import com.takoy3466.re_buildcraft.factory.RefineryBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * 精製機の可動部と液体の描画。元実装: {@code buildcraft.factory.RenderRefinery}。
 * <p>
 * 3 本のタンクは向きごとに回した JSON モデルで描き、ここでは往復する 2 つの磁石と、各タンクの液面を描く。
 * 磁石の見た目 (4 種) は精製の速さで切り替わる。
 */
public class RefineryRenderer implements BlockEntityRenderer<RefineryBlockEntity> {
    private static final ResourceLocation TEXTURE = ReBuildcraft.id("textures/block/refinery.png");
    private static final int TEXTURE_WIDTH = 64;
    private static final int TEXTURE_HEIGHT = 32;
    private static final int MAGNET_COUNT = 4;
    private static final int MAGNET_U = 32;
    private static final int MAGNET_V_STEP = 8;
    /** 旧 animationSpeed の、磁石の見た目を切り替える境目。 */
    private static final float[] MAGNET_SPEED_LIMITS = {1.0F, 2.5F, 4.5F};
    /** 磁石の移動範囲 (ブロック単位) と、2 つ目の磁石の奥行き方向の位置。 */
    private static final float MAGNET_TRAVEL = 0.75F;
    private static final float SECOND_MAGNET_Z = 0.75F;
    private static final float STAGE_LENGTH = 100.0F;

    /** 液面の箱 (向き 0 = 東向きの座標)。{minX, minZ, maxX, maxZ}。 */
    private static final float[][] FLUID_BOXES = {
            {0.01F, 0.01F, 0.49F, 0.49F},
            {0.01F, 0.51F, 0.49F, 0.99F},
            {0.51F, 0.26F, 0.99F, 0.74F},
    };

    private final ModelPart[] magnets = new ModelPart[MAGNET_COUNT];

    public RefineryRenderer(BlockEntityRendererProvider.Context context) {
        for (int i = 0; i < MAGNET_COUNT; i++) {
            MeshDefinition mesh = new MeshDefinition();
            mesh.getRoot().addOrReplaceChild("magnet",
                    CubeListBuilder.create().texOffs(MAGNET_U, i * MAGNET_V_STEP).addBox(8.0F, 0.0F, 0.0F, 8, 4, 4), PartPose.ZERO);
            magnets[i] = LayerDefinition.create(mesh, TEXTURE_WIDTH, TEXTURE_HEIGHT).bakeRoot();
        }
    }

    @Override
    public void render(RefineryBlockEntity refinery, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(angle(refinery)));
        poseStack.translate(-0.5, -0.5, -0.5);

        renderMagnets(refinery, poseStack, buffers, packedLight, packedOverlay);
        FluidStack[] fluids = {refinery.getFirst(), refinery.getSecond(), refinery.getResult()};
        for (int i = 0; i < fluids.length; i++) {
            renderFluid(fluids[i], FLUID_BOXES[i], poseStack, buffers, packedLight);
        }
        poseStack.popPose();
    }

    /** 旧実装の向き (メタデータ 2..5) ごとの回転角。東向きが 0 度。 */
    private static float angle(RefineryBlockEntity refinery) {
        return switch (refinery.getFacing()) {
            case NORTH -> 90.0F;
            case SOUTH -> 270.0F;
            case WEST -> 180.0F;
            default -> 0.0F;
        };
    }

    private void renderMagnets(RefineryBlockEntity refinery, PoseStack poseStack, MultiBufferSource buffers,
                               int packedLight, int packedOverlay) {
        ModelPart magnet = magnets[magnetIndex(refinery.getAnimationSpeed())];
        float stage = refinery.getAnimationStage();
        float first;
        float second;
        if (stage <= STAGE_LENGTH) {
            first = MAGNET_TRAVEL * stage / STAGE_LENGTH;
            second = 0.0F;
        } else if (stage <= 2 * STAGE_LENGTH) {
            first = MAGNET_TRAVEL - MAGNET_TRAVEL * (stage - STAGE_LENGTH) / STAGE_LENGTH;
            second = MAGNET_TRAVEL * (stage - STAGE_LENGTH) / STAGE_LENGTH;
        } else {
            first = MAGNET_TRAVEL * (stage - 2 * STAGE_LENGTH) / STAGE_LENGTH;
            second = MAGNET_TRAVEL - MAGNET_TRAVEL * (stage - 2 * STAGE_LENGTH) / STAGE_LENGTH;
        }
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutout(TEXTURE));
        poseStack.pushPose();
        poseStack.translate(0.0F, first, 0.0F);
        magnet.render(poseStack, consumer, packedLight, packedOverlay);
        poseStack.popPose();
        poseStack.pushPose();
        poseStack.translate(0.0F, second, SECOND_MAGNET_Z);
        magnet.render(poseStack, consumer, packedLight, packedOverlay);
        poseStack.popPose();
    }

    private static int magnetIndex(float speed) {
        for (int i = 0; i < MAGNET_SPEED_LIMITS.length; i++) {
            if (speed <= MAGNET_SPEED_LIMITS[i]) {
                return i;
            }
        }
        return MAGNET_COUNT - 1;
    }

    private static void renderFluid(FluidStack fluid, float[] box, PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        if (fluid.isEmpty()) {
            return;
        }
        IClientFluidTypeExtensions extensions = IClientFluidTypeExtensions.of(fluid.getFluid());
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                .apply(extensions.getStillTexture(fluid));
        float height = Math.min(1.0F, (float) fluid.getAmount() / RefineryBlockEntity.LIQUID_PER_SLOT);
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityTranslucent(InventoryMenu.BLOCK_ATLAS));
        BoxRenderer.render(poseStack.last(), consumer, packedLight, extensions.getTintColor(fluid), BoxRenderer.Uv.of(sprite),
                box[0], 0.0F, box[1], box[2], height, box[3]);
    }
}
