package com.takoy3466.re_buildcraft.core;

import com.takoy3466.re_buildcraft.api.transport.PipeConnections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 中心の芯 (4 - 12) と、接続している方向へ伸びる腕からなる形状。パイプと枠組みが共有する
 * (旧 {@code BlockGenericPipe} / {@code BlockFrame} の当たり判定計算)。
 */
public final class ConnectedShapes {
    private static final double MIN = 4.0;
    private static final double MAX = 12.0;
    private static final double EDGE = 16.0;
    private static final VoxelShape CORE = Block.box(MIN, MIN, MIN, MAX, MAX, MAX);
    private static final Map<Direction, VoxelShape> ARMS = createArms();

    private ConnectedShapes() {
    }

    private static Map<Direction, VoxelShape> createArms() {
        Map<Direction, VoxelShape> arms = new EnumMap<>(Direction.class);
        arms.put(Direction.DOWN, Block.box(MIN, 0, MIN, MAX, MIN, MAX));
        arms.put(Direction.UP, Block.box(MIN, MAX, MIN, MAX, EDGE, MAX));
        arms.put(Direction.NORTH, Block.box(MIN, MIN, 0, MAX, MAX, MIN));
        arms.put(Direction.SOUTH, Block.box(MIN, MIN, MAX, MAX, MAX, EDGE));
        arms.put(Direction.WEST, Block.box(0, MIN, MIN, MIN, MAX, MAX));
        arms.put(Direction.EAST, Block.box(MAX, MIN, MIN, EDGE, MAX, MAX));
        return arms;
    }

    /** 全状態の形状を前もって計算する。 */
    public static Map<BlockState, VoxelShape> forAllStates(List<BlockState> states) {
        Map<BlockState, VoxelShape> shapes = new HashMap<>();
        for (BlockState state : states) {
            shapes.put(state, of(state));
        }
        return Map.copyOf(shapes);
    }

    /** {@link PipeConnections} のプロパティを持つブロックステートの形状。 */
    public static VoxelShape of(BlockState state) {
        VoxelShape shape = CORE;
        for (Direction direction : Direction.values()) {
            if (state.getValue(PipeConnections.property(direction))) {
                shape = Shapes.or(shape, ARMS.get(direction));
            }
        }
        return shape;
    }
}
