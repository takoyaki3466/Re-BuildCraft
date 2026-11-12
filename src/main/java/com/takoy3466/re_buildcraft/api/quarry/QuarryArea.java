package com.takoy3466.re_buildcraft.api.quarry;

import net.minecraft.nbt.CompoundTag;

/**
 * クアーリーの作業範囲 (両端を含む直方体)。元実装: {@code buildcraft.core.Box}。
 */
public record QuarryArea(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    private static final String TAG_MIN_X = "xMin";
    private static final String TAG_MIN_Y = "yMin";
    private static final String TAG_MIN_Z = "zMin";
    private static final String TAG_MAX_X = "xMax";
    private static final String TAG_MAX_Y = "yMax";
    private static final String TAG_MAX_Z = "zMax";

    /**
     * 開始座標と各辺の長さから作る。
     */
    public static QuarryArea ofSize(int minX, int minY, int minZ, int sizeX, int sizeY, int sizeZ) {
        return new QuarryArea(minX, minY, minZ, minX + sizeX - 1, minY + sizeY - 1, minZ + sizeZ - 1);
    }

    public int sizeX() {
        return maxX - minX + 1;
    }

    public int sizeY() {
        return maxY - minY + 1;
    }

    public int sizeZ() {
        return maxZ - minZ + 1;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(TAG_MIN_X, minX);
        tag.putInt(TAG_MIN_Y, minY);
        tag.putInt(TAG_MIN_Z, minZ);
        tag.putInt(TAG_MAX_X, maxX);
        tag.putInt(TAG_MAX_Y, maxY);
        tag.putInt(TAG_MAX_Z, maxZ);
        return tag;
    }

    public static QuarryArea load(CompoundTag tag) {
        return new QuarryArea(tag.getInt(TAG_MIN_X), tag.getInt(TAG_MIN_Y), tag.getInt(TAG_MIN_Z), tag.getInt(TAG_MAX_X), tag.getInt(TAG_MAX_Y), tag.getInt(TAG_MAX_Z));
    }

    /**
     * 作業範囲の高さが指定値を下回らないようにする。
     *
     * @param minimumHeight 最低高さ
     * @return 高さを調整した作業範囲
     */
    public QuarryArea withMinimumHeight(int minimumHeight) {
        if (minimumHeight < 1) {
            throw new IllegalArgumentException("minimumHeight must be at least 1");
        }

        if (sizeY() >= minimumHeight) {
            return this;
        }

        return ofSize(minX, minY, minZ, sizeX(), minimumHeight, sizeZ());
    }
}
