package com.takoy3466.re_buildcraft.factory;

import com.takoy3466.re_buildcraft.api.transport.PipeConnections;
import com.takoy3466.re_buildcraft.core.ConnectedShapes;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * クアーリーの枠組み。隣の枠組みとつながった棒の形をとり、壊しても何も落とさない。
 * 元実装: {@code buildcraft.factory.BlockFrame}。
 */
public class FrameBlock extends Block {
    /** 状態ごとの形状。サーバー/クライアント両スレッドから読むため、生成後は変更しない。 */
    private final Map<BlockState, VoxelShape> shapes;

    public FrameBlock(Properties properties) {
        super(properties);
        BlockState state = stateDefinition.any();
        for (Direction direction : Direction.values()) {
            state = state.setValue(PipeConnections.property(direction), false);
        }
        registerDefaultState(state);
        shapes = ConnectedShapes.forAllStates(stateDefinition.getPossibleStates());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        for (Direction direction : Direction.values()) {
            builder.add(PipeConnections.property(direction));
        }
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState();
        for (Direction direction : Direction.values()) {
            state = state.setValue(PipeConnections.property(direction),
                    context.getLevel().getBlockState(context.getClickedPos().relative(direction)).is(this));
        }
        return state;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        return state.setValue(PipeConnections.property(direction), neighborState.is(this));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes.get(state);
    }
}
