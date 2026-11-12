package com.takoy3466.re_buildcraft.api.laser;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * レーザー。設置した面の法線方向を向き、その先の組立台へエネルギーを照射する。
 * 元実装: {@code buildcraft.silicon.BlockLaser} (形状は {@code mod_BuildCraftSilicon.renderWorldBlock})。
 * <p>
 * CE の精密レーザーなど、性能の違うレーザーもブロックエンティティ型を差し替えてこのクラスを使う。
 */
public class LaserBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    /** 向きごとの形状: 背面の板 (厚さ 4) と、照射方向へ伸びる筒。 */
    private static final Map<Direction, VoxelShape> SHAPES = createShapes();

    private final Supplier<? extends BlockEntityType<? extends LaserBlockEntity>> blockEntityType;

    public LaserBlock(Properties properties, Supplier<? extends BlockEntityType<? extends LaserBlockEntity>> blockEntityType) {
        super(properties);
        this.blockEntityType = blockEntityType;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP));
    }

    private static Map<Direction, VoxelShape> createShapes() {
        Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);
        shapes.put(Direction.UP, Shapes.or(box(0, 0, 0, 16, 4, 16), box(5, 4, 5, 11, 13, 11)));
        shapes.put(Direction.DOWN, Shapes.or(box(0, 12, 0, 16, 16, 16), box(5, 3, 5, 11, 12, 11)));
        shapes.put(Direction.NORTH, Shapes.or(box(0, 0, 12, 16, 16, 16), box(5, 5, 3, 11, 11, 12)));
        shapes.put(Direction.SOUTH, Shapes.or(box(0, 0, 0, 16, 16, 4), box(5, 5, 4, 11, 11, 13)));
        shapes.put(Direction.WEST, Shapes.or(box(12, 0, 0, 16, 16, 16), box(3, 5, 5, 12, 11, 11)));
        shapes.put(Direction.EAST, Shapes.or(box(0, 0, 0, 4, 16, 16), box(4, 5, 5, 13, 11, 11)));
        return shapes;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return blockEntityType.get().create(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return (tickLevel, tickPos, tickState, blockEntity) -> {
            if (blockEntity instanceof LaserBlockEntity laser) {
                laser.tick();
            }
        };
    }
}
