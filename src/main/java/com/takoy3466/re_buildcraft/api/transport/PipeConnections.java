package com.takoy3466.re_buildcraft.api.transport;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * パイプの接続状態を表すブロックステートのプロパティ。
 * 旧実装はタイルエンティティが毎回接続を計算していたが、1.21.1 では形状・モデルを接続状態から決めるため
 * ブロックステートに保持する ({@code BlockGenericPipe} のモデル・当たり判定計算に相当)。
 */
public final class PipeConnections {
    private static final Map<Direction, BooleanProperty> PROPERTIES = createProperties();

    private PipeConnections() {
    }

    private static Map<Direction, BooleanProperty> createProperties() {
        Map<Direction, BooleanProperty> properties = new EnumMap<>(Direction.class);
        for (Direction direction : Direction.values()) {
            properties.put(direction, BooleanProperty.create(direction.getSerializedName()));
        }
        return properties;
    }

    public static BooleanProperty property(Direction direction) {
        return PROPERTIES.get(direction);
    }

    /** {@code pos} のパイプが {@code side} 方向に接続しているか。パイプでなければ false。 */
    public static boolean isPipeConnectedTo(BlockGetter level, BlockPos pos, Direction side) {
        BlockState state = level.getBlockState(pos);
        BooleanProperty property = PROPERTIES.get(side);
        return state.hasProperty(property) && state.getValue(property);
    }
}
