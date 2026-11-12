package com.takoy3466.re_buildcraft.builders;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * マーカーのブロック。元実装: {@code buildcraft.builders.BlockMarker}。
 * <ul>
 *   <li>置いた面の向きに立つ (旧 onBlockPlaced のメタデータ 0 - 5)。当たり判定は無く、選択範囲だけを持つ。</li>
 *   <li>原点のマーカーは別のテクスチャ (旧メタデータ +8 → {@link #ORIGIN})。</li>
 *   <li>マーカーを持ってスニークで右クリックすると自動設置、それ以外の右クリックで接続 (旧 blockActivated)。</li>
 *   <li>隣の変化でレッドストーン信号を確認する (旧 onNeighborBlockChange → switchSignals)。</li>
 * </ul>
 */
public class MarkerBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty ORIGIN = BooleanProperty.create("origin");
    /** 旧 collisionRayTrace の境界 (0.35 - 0.65、長さ 0.65)。 */
    private static final double NEAR = 5.6;
    private static final double FAR = 10.4;
    private static final Map<Direction, VoxelShape> SHAPES = createShapes();

    public MarkerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP).setValue(ORIGIN, false));
    }

    private static Map<Direction, VoxelShape> createShapes() {
        Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);
        shapes.put(Direction.UP, Block.box(NEAR, 0.0, NEAR, FAR, FAR, FAR));
        shapes.put(Direction.DOWN, Block.box(NEAR, NEAR, NEAR, FAR, 16.0, FAR));
        shapes.put(Direction.EAST, Block.box(0.0, NEAR, NEAR, FAR, FAR, FAR));
        shapes.put(Direction.WEST, Block.box(NEAR, NEAR, NEAR, 16.0, FAR, FAR));
        shapes.put(Direction.SOUTH, Block.box(NEAR, NEAR, 0.0, FAR, FAR, FAR));
        shapes.put(Direction.NORTH, Block.box(NEAR, NEAR, NEAR, FAR, FAR, 16.0));
        return shapes;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ORIGIN);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MarkerBlockEntity(pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!stack.is(asItem()) || !player.isShiftKeyDown()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.getBlockEntity(pos) instanceof MarkerBlockEntity marker) {
            marker.trySetBlockAndConnection(player, stack);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.getBlockEntity(pos) instanceof MarkerBlockEntity marker) {
            marker.tryConnection(player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos,
                                   boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (level.getBlockEntity(pos) instanceof MarkerBlockEntity marker) {
            marker.updateSignals();
        }
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this) && level.getBlockEntity(pos) instanceof MarkerBlockEntity marker) {
            marker.updateSignals();
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof MarkerBlockEntity marker) {
            marker.destroy();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
