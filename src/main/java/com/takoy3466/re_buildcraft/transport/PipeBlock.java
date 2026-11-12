package com.takoy3466.re_buildcraft.transport;

import com.takoy3466.re_buildcraft.api.BuildCraftTags;
import com.takoy3466.re_buildcraft.api.transport.PipeConnections;
import com.takoy3466.re_buildcraft.core.ConnectedShapes;
import com.takoy3466.re_buildcraft.transport.gate.Gate;
import com.takoy3466.re_buildcraft.transport.gate.GateKind;
import com.takoy3466.re_buildcraft.transport.gate.GateMenu;
import com.takoy3466.re_buildcraft.transport.gate.WireColor;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * パイプのブロック。種類ごとに 1 ブロックを登録する (旧実装は 1 つの BlockGenericPipe とパイプごとのアイテム)。
 * 元実装: {@code buildcraft.transport.BlockGenericPipe} と {@code ItemPipe}。
 * <p>
 * 右クリックの扱いは旧実装と同じ: ワイヤー/ゲートを持っていれば取り付け、レンチなら素材ごとの操作、
 * スニーク (レンチ以外) ならゲートとワイヤーを外し、ゲートがあればゲート画面、なければ素材の画面を開く。
 */
public class PipeBlock extends Block implements EntityBlock {
    private static final String GATE_TITLE_KEY = "container.re_buildcraft.gate";
    private static final int REDSTONE_SIGNAL = 15;

    private final PipeType pipeType;
    private final Map<BlockState, VoxelShape> shapes;

    public PipeBlock(Properties properties, PipeType pipeType) {
        super(properties);
        this.pipeType = pipeType;
        BlockState state = stateDefinition.any();
        for (Direction direction : Direction.values()) {
            state = state.setValue(PipeConnections.property(direction), false);
        }
        registerDefaultState(state);
        this.shapes = ConnectedShapes.forAllStates(stateDefinition.getPossibleStates());
    }

    public PipeType getPipeType() {
        return pipeType;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        for (Direction direction : Direction.values()) {
            builder.add(PipeConnections.property(direction));
        }
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes.get(state);
    }

    // ---- 接続 -------------------------------------------------------------

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState();
        for (Direction direction : Direction.values()) {
            state = state.setValue(PipeConnections.property(direction),
                    PipeConnectionRules.canConnect(context.getLevel(), context.getClickedPos(), direction, pipeType));
        }
        return state;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        if (level instanceof Level realLevel) {
            return state.setValue(PipeConnections.property(direction),
                    PipeConnectionRules.canConnect(realLevel, pos, direction, pipeType));
        }
        return state;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos,
                                   boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (level.isClientSide) {
            return;
        }
        // ブロックエンティティの追加・削除はブロックの形状更新を伴わないことがあるため、ここでも接続を取り直す。
        BlockState updated = state;
        for (Direction direction : Direction.values()) {
            updated = updated.setValue(PipeConnections.property(direction),
                    PipeConnectionRules.canConnect(level, pos, direction, pipeType));
        }
        if (updated != state) {
            level.setBlock(pos, updated, Block.UPDATE_ALL);
        }
        if (level.getBlockEntity(pos) instanceof PipeBlockEntity pipe) {
            pipe.onNeighborChanged();
        }
    }

    // ---- ブロックエンティティ -------------------------------------------------------

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PipeBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return (tickLevel, tickPos, tickState, blockEntity) -> {
            if (blockEntity instanceof PipeBlockEntity pipe) {
                pipe.tick();
            }
        };
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof PipeBlockEntity pipe) {
            pipe.dropContents();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof PipeBlockEntity pipe) {
            pipe.behavior().onEntityInside(pipe, entity);
        }
    }

    // ---- 右クリック -------------------------------------------------------------

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hitResult) {
        // パイプを持っていれば、パイプに向けて別のパイプを置けるようにする。
        if (!(level.getBlockEntity(pos) instanceof PipeBlockEntity pipe) || stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof PipeBlock) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        boolean wrench = stack.is(BuildCraftTags.WRENCHES);
        if (player.isShiftKeyDown() && !wrench) {
            return removeGateAndWires(level, pos, pipe) ? ItemInteractionResult.sidedSuccess(level.isClientSide)
                    : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (wrench) {
            if (!level.isClientSide) {
                pipe.behavior().onWrench(pipe);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        for (WireColor color : WireColor.values()) {
            if (stack.is(color.item())) {
                if (pipe.signals().hasWire(color)) {
                    return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
                }
                if (!level.isClientSide) {
                    pipe.signals().setWire(color, true);
                    pipe.signals().update(pipe);
                    stack.consume(1, player);
                    pipe.scheduleSync();
                }
                return ItemInteractionResult.sidedSuccess(level.isClientSide);
            }
        }
        GateKind gateKind = GateKind.byItem(stack.getItem());
        if (gateKind != null && pipe.getGate() == null) {
            if (!level.isClientSide) {
                pipe.setGate(new Gate(gateKind));
                stack.consume(1, player);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!(level.getBlockEntity(pos) instanceof PipeBlockEntity pipe)) {
            return InteractionResult.PASS;
        }
        if (player.isShiftKeyDown()) {
            return removeGateAndWires(level, pos, pipe) ? InteractionResult.sidedSuccess(level.isClientSide) : InteractionResult.PASS;
        }
        MenuProvider menu = pipe.getGate() != null
                ? new SimpleMenuProvider((id, inventory, menuPlayer) -> new GateMenu(id, inventory, pipe), Component.translatable(GATE_TITLE_KEY))
                : pipe.behavior().menuProvider(pipe);
        if (menu == null) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(menu, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** ゲートとワイヤーを外して落とす (旧 Pipe.onGateRemoval)。 */
    private static boolean removeGateAndWires(Level level, BlockPos pos, PipeBlockEntity pipe) {
        if (pipe.getGate() == null && !pipe.signals().hasAnyWire()) {
            return false;
        }
        if (!level.isClientSide) {
            pipe.signals().dropWires(level, pos);
            for (WireColor color : WireColor.values()) {
                pipe.signals().setWire(color, false);
            }
            Gate gate = pipe.getGate();
            if (gate != null) {
                Block.popResource(level, pos, gate.kind().toItemStack());
                pipe.setGate(null);
            }
            level.updateNeighborsAt(pos, pipe.getBlockState().getBlock());
            pipe.scheduleSync();
        }
        return true;
    }

    // ---- レッドストーン -----------------------------------------------------------

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    /**
     * ゲートがレッドストーン出力中なら、接続しているパイプ以外の方向へ信号を出す (旧 Pipe.isPoweringTo)。
     */
    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        if (!(level.getBlockEntity(pos) instanceof PipeBlockEntity pipe) || pipe.getGate() == null
                || !pipe.getGate().isBroadcastingRedstone()) {
            return 0;
        }
        Direction toward = direction.getOpposite();
        boolean pipeThere = level.getBlockEntity(pos.relative(toward)) instanceof PipeBlockEntity && pipe.isConnected(toward);
        return pipeThere ? 0 : REDSTONE_SIGNAL;
    }
}
