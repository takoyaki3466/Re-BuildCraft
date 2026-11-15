package com.takoy3466.re_buildcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.takoy3466.re_buildcraft.ReBuildcraft;
import com.takoy3466.re_buildcraft.api.client.BoxRenderer;
import com.takoy3466.re_buildcraft.transport.FluidTransport;
import com.takoy3466.re_buildcraft.transport.ItemTransport;
import com.takoy3466.re_buildcraft.transport.PipeBlockEntity;
import com.takoy3466.re_buildcraft.transport.PowerTransport;
import com.takoy3466.re_buildcraft.transport.TravelingItem;
import com.takoy3466.re_buildcraft.transport.gate.Gate;
import com.takoy3466.re_buildcraft.transport.gate.GateKind;
import com.takoy3466.re_buildcraft.transport.gate.PipeSignals;
import com.takoy3466.re_buildcraft.transport.gate.WireColor;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;

/**
 * パイプの中身 (アイテム・液体・動力) と、ワイヤー・ゲートの描画。元実装: {@code buildcraft.transport.RenderPipe}。
 * パイプ本体は接続状態に応じたブロックモデル (マルチパート) で描く。
 */
public class PipeRenderer implements BlockEntityRenderer<PipeBlockEntity> {
    private static final float PIXEL = 1.0F / 16.0F;
    private static final float CORE_MIN = 4 * PIXEL;
    private static final float CORE_MAX = 12 * PIXEL;
    /**
     * パイプ内のアイテムの描画の大きさ (旧 RenderPipe.doRenderItem)。立体のモデル (ブロックなど) は 0.25、
     * 平らなアイテムは 0.5 ブロック。どちらもパイプの内径 (芯の幅 0.5) に収まる。
     * 以前は表示用の変形 GROUND (それ自体がアイテム 0.5 倍・ブロック 0.25 倍に縮める) にさらに 0.5 倍を掛けており、旧版の半分の大きさだった。
     */
    private static final float BLOCK_ITEM_SIZE = 0.25F;
    private static final float FLAT_ITEM_SIZE = 0.5F;
    /**
     * 論理上のアイテムの位置はパイプの「床」(ブロックの下から {@link ItemTransport#PIPE_FLOOR}) にあり、旧版は描画時に
     * その分を持ち上げてパイプの中心へ描いていた。床から中心までの高さ。
     */
    private static final double ITEM_CENTER_ABOVE_FLOOR = 0.5 - ItemTransport.PIPE_FLOOR;
    private static final float FLUID_MARGIN = 0.01F;
    private static final float POWER_MIN_HALF_WIDTH = 0.5F * PIXEL;
    private static final float POWER_MAX_HALF_WIDTH = 3.0F * PIXEL;
    private static final float WIRE_THICKNESS = PIXEL;
    private static final float GATE_MIN = 3.5F * PIXEL;
    private static final float GATE_MAX = 12.5F * PIXEL;
    private static final ResourceLocation POWER_TEXTURE = ReBuildcraft.id("textures/block/pipe/power.png");

    /** ワイヤーを通す芯の角 (色ごとに別の角)。 */
    private static final float[][] WIRE_CORNERS = {
            {CORE_MIN - WIRE_THICKNESS, CORE_MIN - WIRE_THICKNESS},
            {CORE_MAX, CORE_MIN - WIRE_THICKNESS},
            {CORE_MIN - WIRE_THICKNESS, CORE_MAX},
            {CORE_MAX, CORE_MAX}};

    public PipeRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(PipeBlockEntity pipe, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        switch (pipe.transport()) {
            case ItemTransport items -> renderItems(pipe, items, partialTick, poseStack, buffers, packedLight);
            case FluidTransport fluids -> renderFluids(pipe, fluids, poseStack, buffers, packedLight);
            case PowerTransport power -> renderPower(pipe, power, poseStack, buffers);
            default -> {
            }
        }
        renderWires(pipe, poseStack, buffers, packedLight);
        Gate gate = pipe.getGate();
        if (gate != null) {
            boolean lit = gate.isBroadcastingRedstone();
            for (WireColor color : WireColor.values()) {
                lit |= pipe.signals().isBroadcasting(color);
            }
            VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutout(gateTexture(gate.kind(), lit)));
            BoxRenderer.render(poseStack.last(), consumer, packedLight, GATE_MIN, GATE_MIN, GATE_MIN, GATE_MAX, GATE_MAX, GATE_MAX);
        }
    }

    private static void renderItems(PipeBlockEntity pipe, ItemTransport items, float partialTick, PoseStack poseStack,
                                    MultiBufferSource buffers, int packedLight) {
        BlockPos pos = pipe.getBlockPos();
        for (TravelingItem item : items.items()) {
            Vec3 position = item.position();
            Direction heading = item.heading();
            float step = item.speed() * partialTick;
            poseStack.pushPose();
            poseStack.translate(position.x - pos.getX() + heading.getStepX() * step,
                    position.y - pos.getY() + ITEM_CENTER_ABOVE_FLOOR + heading.getStepY() * step,
                    position.z - pos.getZ() + heading.getStepZ() * step);
            // 表示用の変形を使わず (NONE: モデルの中心が原点に来る)、大きさをここで決める。GROUND の「地面から浮かせる」移動や
            // 縮小が重ならないようにするため。
            ItemRenderer renderer = Minecraft.getInstance().getItemRenderer();
            BakedModel model = renderer.getModel(item.stack(), pipe.getLevel(), null, item.id());
            float size = model.isGui3d() ? BLOCK_ITEM_SIZE : FLAT_ITEM_SIZE;
            poseStack.scale(size, size, size);
            renderer.render(item.stack(), ItemDisplayContext.NONE, false, poseStack, buffers, packedLight,
                    OverlayTexture.NO_OVERLAY, model);
            poseStack.popPose();
        }
    }

    private static void renderFluids(PipeBlockEntity pipe, FluidTransport fluids, PoseStack poseStack,
                                     MultiBufferSource buffers, int packedLight) {
        if (fluids.isEmpty()) {
            return;
        }
        IClientFluidTypeExtensions extensions = IClientFluidTypeExtensions.of(fluids.fluid());
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(extensions.getStillTexture());
        BoxRenderer.Uv uv = BoxRenderer.Uv.of(sprite);
        int color = extensions.getTintColor();
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityTranslucent(InventoryMenu.BLOCK_ATLAS));

        float centerLevel = (float) fluids.centerAmount() / FluidTransport.LIQUID_IN_PIPE;
        if (centerLevel > 0.0F) {
            float min = CORE_MIN + FLUID_MARGIN;
            float max = CORE_MAX - FLUID_MARGIN;
            BoxRenderer.render(poseStack.last(), consumer, packedLight, color, uv, min, min, min, max, min + (max - min) * centerLevel, max);
        }
        for (Direction direction : Direction.values()) {
            float level = (float) fluids.sideAmount(direction) / FluidTransport.LIQUID_IN_PIPE;
            if (level > 0.0F && pipe.isConnected(direction)) {
                float[] box = armBox(direction, CORE_MIN + FLUID_MARGIN, CORE_MAX - FLUID_MARGIN);
                if (direction.getAxis().isHorizontal()) {
                    box[4] = box[1] + (box[4] - box[1]) * level;
                }
                BoxRenderer.render(poseStack.last(), consumer, packedLight, color, uv, box[0], box[1], box[2], box[3], box[4], box[5]);
            }
        }
    }

    private static void renderPower(PipeBlockEntity pipe, PowerTransport power, PoseStack poseStack, MultiBufferSource buffers) {
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutout(POWER_TEXTURE));
        for (Direction direction : Direction.values()) {
            int amount = power.displayPower(direction);
            if (amount <= 0 || !pipe.isConnected(direction)) {
                continue;
            }
            float ratio = (float) Math.min(amount, PowerTransport.MAX_ENERGY) / (float) PowerTransport.MAX_ENERGY;
            float halfWidth = POWER_MIN_HALF_WIDTH + (POWER_MAX_HALF_WIDTH - POWER_MIN_HALF_WIDTH) * ratio;
            float[] box = armBox(direction, 0.5F - halfWidth, 0.5F + halfWidth);
            BoxRenderer.render(poseStack.last(), consumer, LightTexture.FULL_BRIGHT, box[0], box[1], box[2], box[3], box[4], box[5]);
        }
    }

    /** ワイヤーは色ごとに芯の別の角を通し、接続している方向へ伸ばす。 */
    private static void renderWires(PipeBlockEntity pipe, PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        PipeSignals signals = pipe.signals();
        for (WireColor color : WireColor.values()) {
            if (!signals.hasWire(color)) {
                continue;
            }
            VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutout(wireTexture(color, signals.strength(color) > 0)));
            float a = WIRE_CORNERS[color.ordinal()][0];
            float b = WIRE_CORNERS[color.ordinal()][1];
            // 芯の周りの輪 (X 方向の辺)
            BoxRenderer.render(poseStack.last(), consumer, packedLight, CORE_MIN, a, b, CORE_MAX, a + WIRE_THICKNESS, b + WIRE_THICKNESS);
            for (Direction direction : Direction.values()) {
                if (!pipe.isConnected(direction)) {
                    continue;
                }
                float[] box = armBox(direction, 0.0F, 0.0F);
                float[] wire = wireAlong(direction, box, a, b);
                BoxRenderer.render(poseStack.last(), consumer, packedLight, wire[0], wire[1], wire[2], wire[3], wire[4], wire[5]);
            }
        }
    }

    /** 腕の範囲 (長さ方向は芯の端からブロックの端まで、断面は {@code min} - {@code max})。 */
    private static float[] armBox(Direction direction, float min, float max) {
        float[] box = {min, min, min, max, max, max};
        int axis = direction.getAxis().ordinal();
        boolean positive = direction.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        box[axis] = positive ? CORE_MAX : 0.0F;
        box[axis + 3] = positive ? 1.0F : CORE_MIN;
        return box;
    }

    /** 腕に沿ったワイヤー。断面は芯の角 (a, b) に置く。 */
    private static float[] wireAlong(Direction direction, float[] arm, float a, float b) {
        int axis = direction.getAxis().ordinal();
        float[] wire = new float[6];
        int other = 0;
        float[] corner = {a, b};
        for (int i = 0; i < 3; i++) {
            if (i == axis) {
                wire[i] = arm[i];
                wire[i + 3] = arm[i + 3];
            } else {
                wire[i] = corner[other];
                wire[i + 3] = corner[other] + WIRE_THICKNESS;
                other++;
            }
        }
        return wire;
    }

    private static ResourceLocation wireTexture(WireColor color, boolean lit) {
        return ReBuildcraft.id("textures/block/pipe/wire_" + color.id() + (lit ? "_on" : "_off") + ".png");
    }

    private static ResourceLocation gateTexture(GateKind kind, boolean lit) {
        return ReBuildcraft.id("textures/block/pipe/gate_" + kind.name().toLowerCase(Locale.ROOT) + (lit ? "_on" : "_off") + ".png");
    }

    @Override
    public boolean shouldRenderOffScreen(PipeBlockEntity pipe) {
        return false;
    }
}
