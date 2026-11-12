package com.takoy3466.re_buildcraft.transport;

import java.util.Locale;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.Nullable;

/**
 * 方向付きパイプ (木の取り出し元・鉄の出力先) が指している面。旧実装はメタデータ 0 - 5 と 6 (なし) で表していた。
 */
public enum PipeSide implements StringRepresentable {
    NONE(null),
    DOWN(Direction.DOWN),
    UP(Direction.UP),
    NORTH(Direction.NORTH),
    SOUTH(Direction.SOUTH),
    WEST(Direction.WEST),
    EAST(Direction.EAST);

    @Nullable
    private final Direction direction;

    PipeSide(@Nullable Direction direction) {
        this.direction = direction;
    }

    @Nullable
    public Direction direction() {
        return direction;
    }

    public static PipeSide of(@Nullable Direction direction) {
        if (direction == null) {
            return NONE;
        }
        for (PipeSide side : values()) {
            if (side.direction == direction) {
                return side;
            }
        }
        return NONE;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
