package com.takoy3466.re_buildcraft.factory;

import com.takoy3466.re_buildcraft.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import org.jetbrains.annotations.Nullable;

/**
 * ボーリングマシン (Mining Well)。真下へ 1 列掘り進める。
 * 元実装: {@code buildcraft.factory.BlockMiningWell}。
 */
public class MiningWellBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    public MiningWellBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** 正面 (前面テクスチャ) を設置したプレイヤーへ向ける。 */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MiningWellBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return (tickLevel, tickPos, tickState, blockEntity) -> {
            if (blockEntity instanceof MiningWellBlockEntity well) {
                well.serverTick();
            }
        };
    }

    /** 本体を壊したら、真下に伸びている採掘パイプもまとめて取り除く (旧 removeBlockByPlayer)。 */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            BlockPos.MutableBlockPos cursor = pos.mutable().move(Direction.DOWN);
            while (cursor.getY() >= level.getMinBuildHeight() && level.getBlockState(cursor).is(ModBlocks.MINING_PIPE.get())) {
                level.removeBlock(cursor, false);
                cursor.move(Direction.DOWN);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
