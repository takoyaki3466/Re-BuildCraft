package com.takoy3466.re_buildcraft.api.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.takoy3466.re_buildcraft.ReBuildcraft;
import com.takoy3466.re_buildcraft.api.engine.EnergyStage;
import com.takoy3466.re_buildcraft.api.engine.EngineBlockEntity;
import com.takoy3466.re_buildcraft.api.engine.EngineLogic;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

/**
 * エンジンのピストン描画。元実装: {@code buildcraft.energy.RenderEngine}。
 * <p>
 * 土台・可動板・蛇腹・熱で色が変わる芯の 4 部品を、エンジンの向き (上向きを基準) に回転して描く。
 * テクスチャは {@link EngineLogic#getTexture()} で決まるため、派生エンジンもこのレンダラーをそのまま使える。
 */
public class EngineRenderer<T extends EngineBlockEntity> implements BlockEntityRenderer<T> {
    private static final int TEXTURE_WIDTH = 64;
    private static final int TEXTURE_HEIGHT = 32;
    private static final float PIXEL = 1.0F / 16.0F;
    /** ピストンが最も伸びたときの可動板の移動量 (ピクセル)。 */
    private static final float MAX_STROKE_PIXELS = 8.0F;
    /** 蛇腹 1 段の高さ (ピクセル)。 */
    private static final int CHAMBER_STEP_PIXELS = 2;
    private static final float HALF_STROKE = 0.5F;

    private static final ResourceLocation CHAMBER_TEXTURE = ReBuildcraft.id("textures/block/engine/chamber.png");
    private static final Map<EnergyStage, ResourceLocation> TRUNK_TEXTURES = createTrunkTextures();

    private final ModelPart base;
    private final ModelPart movingBox;
    private final ModelPart chamber;
    private final ModelPart trunk;

    public EngineRenderer(BlockEntityRendererProvider.Context context) {
        this.base = bakeBox(0, 0, 0, 16, 4, 16);
        this.movingBox = bakeBox(0, 4, 0, 16, 4, 16);
        this.chamber = bakeBox(3, 4, 3, 10, 2, 10);
        this.trunk = bakeBox(4, 4, 4, 8, 12, 8);
    }

    private static Map<EnergyStage, ResourceLocation> createTrunkTextures() {
        Map<EnergyStage, ResourceLocation> textures = new EnumMap<>(EnergyStage.class);
        textures.put(EnergyStage.BLUE, ReBuildcraft.id("textures/block/engine/trunk_blue.png"));
        textures.put(EnergyStage.GREEN, ReBuildcraft.id("textures/block/engine/trunk_green.png"));
        textures.put(EnergyStage.YELLOW, ReBuildcraft.id("textures/block/engine/trunk_yellow.png"));
        textures.put(EnergyStage.RED, ReBuildcraft.id("textures/block/engine/trunk_red.png"));
        textures.put(EnergyStage.EXPLOSION, ReBuildcraft.id("textures/block/engine/trunk_red.png"));
        return textures;
    }

    /** ピクセル単位の直方体 1 つからなる部品を作る (UV は旧 ModelRenderer と同じく原点から)。 */
    private static ModelPart bakeBox(float x, float y, float z, float width, float height, float depth) {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("box",
                CubeListBuilder.create().texOffs(0, 0).addBox(x, y, z, width, height, depth), PartPose.ZERO);
        return LayerDefinition.create(mesh, TEXTURE_WIDTH, TEXTURE_HEIGHT).bakeRoot();
    }

    @Override
    public void render(T engineEntity, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        EngineLogic engine = engineEntity.getEngine();
        Direction facing = engineEntity.getFacing();
        float progress = engine.getProgress();
        float strokePixels = progress > HALF_STROKE
                ? MAX_STROKE_PIXELS - (progress - HALF_STROKE) * 2.0F * MAX_STROKE_PIXELS
                : progress * 2.0F * MAX_STROKE_PIXELS;

        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.mulPose(facing.getRotation());
        poseStack.translate(-0.5, -0.5, -0.5);

        VertexConsumer baseBuffer = buffers.getBuffer(RenderType.entityCutout(engine.getTexture()));
        base.render(poseStack, baseBuffer, packedLight, packedOverlay);

        poseStack.pushPose();
        poseStack.translate(0.0, strokePixels * PIXEL, 0.0);
        movingBox.render(poseStack, baseBuffer, packedLight, packedOverlay);
        poseStack.popPose();

        VertexConsumer chamberBuffer = buffers.getBuffer(RenderType.entityCutout(CHAMBER_TEXTURE));
        poseStack.pushPose();
        for (int step = 0; step <= strokePixels; step += CHAMBER_STEP_PIXELS) {
            chamber.render(poseStack, chamberBuffer, packedLight, packedOverlay);
            poseStack.translate(0.0, CHAMBER_STEP_PIXELS * PIXEL, 0.0);
        }
        poseStack.popPose();

        VertexConsumer trunkBuffer = buffers.getBuffer(RenderType.entityCutout(TRUNK_TEXTURES.get(engine.getEnergyStage())));
        trunk.render(poseStack, trunkBuffer, packedLight, packedOverlay);

        poseStack.popPose();
    }
}
