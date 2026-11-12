package com.takoy3466.re_buildcraft.api.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * テクスチャを 6 面に貼った直方体と、2 点を結ぶ棒 (ビーム) を描く。
 * レーザービーム・ポンプのチューブ・クアーリーのアーム・パイプの中身など、旧版でエンティティや
 * 手書きの描画処理で表していた単純な形に使う。
 */
public final class BoxRenderer {
    private static final double BLOCK_CENTER = 0.5;
    /** テクスチャ全体。 */
    public static final Uv FULL = new Uv(0.0F, 0.0F, 1.0F, 1.0F);
    public static final int WHITE = 0xFFFFFFFF;
    private static final int COLOR_MASK = 0xFF;
    private static final Vector3f BEAM_AXIS = new Vector3f(1.0F, 0.0F, 0.0F);

    /** 貼り付けるテクスチャの範囲。 */
    public record Uv(float u0, float v0, float u1, float v1) {
        public static Uv of(TextureAtlasSprite sprite) {
            return new Uv(sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1());
        }
    }

    private BoxRenderer() {
    }

    public static void render(PoseStack.Pose pose, VertexConsumer consumer, int packedLight,
                              float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        render(pose, consumer, packedLight, WHITE, FULL, minX, minY, minZ, maxX, maxY, maxZ);
    }

    public static void render(PoseStack.Pose pose, VertexConsumer consumer, int packedLight, int argb, Uv uv,
                              float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        Face face = new Face(pose, consumer, packedLight, argb, uv);
        face.quad(0, -1, 0, minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ);
        face.quad(0, 1, 0, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, minX, maxY, minZ);
        face.quad(0, 0, -1, maxX, maxY, minZ, maxX, minY, minZ, minX, minY, minZ, minX, maxY, minZ);
        face.quad(0, 0, 1, minX, maxY, maxZ, minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ);
        face.quad(-1, 0, 0, minX, maxY, minZ, minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ);
        face.quad(1, 0, 0, maxX, maxY, maxZ, maxX, minY, maxZ, maxX, minY, minZ, maxX, maxY, minZ);
    }

    /**
     * {@code start} から {@code end} への、断面が {@code halfWidth} の 2 倍の四角柱を描く。
     * 座標は {@code poseStack} の現在の原点からの相対位置。
     */
    public static void renderBeam(PoseStack poseStack, VertexConsumer consumer, int packedLight, Vec3 start, Vec3 end, float halfWidth) {
        Vec3 beam = end.subtract(start);
        float length = (float) beam.length();
        if (length <= 0.0F) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(start.x, start.y, start.z);
        poseStack.mulPose(new Quaternionf().rotationTo(BEAM_AXIS, beam.normalize().toVector3f()));
        render(poseStack.last(), consumer, packedLight, 0.0F, -halfWidth, -halfWidth, length, halfWidth, halfWidth);
        poseStack.popPose();
    }

    /**
     * 両端を含むブロック範囲を、各ブロックの中心を結ぶ 12 本のビームで囲む (旧 Utils.createLaserBox / Box.createLasers)。
     *
     * @param base {@code poseStack} の原点にあたるワールド座標 (ブロックエンティティの位置)
     */
    public static void renderLaserBox(PoseStack poseStack, VertexConsumer consumer, int packedLight, Vec3 base,
                                      int minX, int minY, int minZ, int maxX, int maxY, int maxZ, float halfWidth) {
        double[] xs = {minX + BLOCK_CENTER, maxX + BLOCK_CENTER};
        double[] ys = {minY + BLOCK_CENTER, maxY + BLOCK_CENTER};
        double[] zs = {minZ + BLOCK_CENTER, maxZ + BLOCK_CENTER};
        for (double y : ys) {
            for (double z : zs) {
                renderBeam(poseStack, consumer, packedLight, new Vec3(xs[0], y, z).subtract(base), new Vec3(xs[1], y, z).subtract(base), halfWidth);
            }
            for (double x : xs) {
                renderBeam(poseStack, consumer, packedLight, new Vec3(x, y, zs[0]).subtract(base), new Vec3(x, y, zs[1]).subtract(base), halfWidth);
            }
        }
        for (double x : xs) {
            for (double z : zs) {
                renderBeam(poseStack, consumer, packedLight, new Vec3(x, ys[0], z).subtract(base), new Vec3(x, ys[1], z).subtract(base), halfWidth);
            }
        }
    }

    private record Face(PoseStack.Pose pose, VertexConsumer consumer, int light, int argb, Uv uv) {
        void quad(float normalX, float normalY, float normalZ,
                  float x0, float y0, float z0, float x1, float y1, float z1,
                  float x2, float y2, float z2, float x3, float y3, float z3) {
            vertex(normalX, normalY, normalZ, x0, y0, z0, uv.u0(), uv.v0());
            vertex(normalX, normalY, normalZ, x1, y1, z1, uv.u0(), uv.v1());
            vertex(normalX, normalY, normalZ, x2, y2, z2, uv.u1(), uv.v1());
            vertex(normalX, normalY, normalZ, x3, y3, z3, uv.u1(), uv.v0());
        }

        private void vertex(float normalX, float normalY, float normalZ, float x, float y, float z, float u, float v) {
            consumer.addVertex(pose, x, y, z)
                    .setColor(argb >> 16 & COLOR_MASK, argb >> 8 & COLOR_MASK, argb & COLOR_MASK, argb >>> 24)
                    .setUv(u, v)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(light)
                    .setNormal(pose, normalX, normalY, normalZ);
        }
    }
}
