package com.takoy3466.re_buildcraft.api.engine;

import com.takoy3466.re_buildcraft.api.power.BuildCraftCapabilities;
import com.takoy3466.re_buildcraft.api.BuildCraftTags;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * エンジンのブロック。元実装: {@code buildcraft.energy.BlockEngine} + {@code ItemEngine}。
 * <p>
 * 旧実装はメタデータでエンジンの種類を切り替えていたが、1.21.1 では種類ごとに別ブロックとして登録し、
 * 種類固有の振る舞いは {@link EngineLogic} のファクトリとして受け取る。
 */
public class EngineBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    private static final int REDSTONE_SIGNAL = 15;
    private static final float PARTICLE_SIDE_OFFSET = 0.52F;
    private static final float PARTICLE_SPREAD = 0.6F;
    private static final float PARTICLE_MAX_HEIGHT = 6.0F / 16.0F;

    private final Supplier<? extends BlockEntityType<? extends EngineBlockEntity>> blockEntityType;
    private final Function<EngineBlockEntity, EngineLogic> logicFactory;

    public EngineBlock(Properties properties,
                       Supplier<? extends BlockEntityType<? extends EngineBlockEntity>> blockEntityType,
                       Function<EngineBlockEntity, EngineLogic> logicFactory) {
        super(properties);
        this.blockEntityType = blockEntityType;
        this.logicFactory = logicFactory;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP));
    }

    /** このブロックに対応するエンジンの振る舞いを生成する。 */
    public EngineLogic createLogic(EngineBlockEntity tile) {
        return logicFactory.apply(tile);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        // 本体はピストンの動きに合わせて BlockEntityRenderer が描画する。
        return RenderShape.ENTITYBLOCK_ANIMATED;
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
            if (blockEntity instanceof EngineBlockEntity engineEntity) {
                engineEntity.tick();
            }
        };
    }

    /**
     * クリックしたブロックの方を向けて設置する。そこが動力を受け取れない場合は、
     * 受け取れる隣接ブロックを探してそちらへ向ける (旧 {@code ItemEngine.onItemUse})。
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Direction preferred = context.getClickedFace().getOpposite();
        Direction facing = preferred;
        if (!canReceivePower(level, pos, preferred)) {
            int directionCount = Direction.values().length;
            for (int step = 1; step < directionCount; step++) {
                Direction candidate = Direction.from3DDataValue((preferred.get3DDataValue() + step) % directionCount);
                if (canReceivePower(level, pos, candidate)) {
                    facing = candidate;
                    break;
                }
            }
        }
        return defaultBlockState().setValue(FACING, facing);
    }

    private static boolean canReceivePower(Level level, BlockPos enginePos, Direction direction) {
        return level.getCapability(BuildCraftCapabilities.POWER_RECEPTOR, enginePos.relative(direction), direction.getOpposite()) != null;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!stack.is(BuildCraftTags.WRENCHES)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof EngineBlockEntity engineEntity) {
            engineEntity.switchOrientation(player.isShiftKeyDown());
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!(level.getBlockEntity(pos) instanceof EngineBlockEntity engineEntity)) {
            return InteractionResult.PASS;
        }
        MenuProvider menuProvider = engineEntity.getEngine().getMenuProvider();
        if (menuProvider == null) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(menuProvider, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (level.getBlockEntity(pos) instanceof EngineBlockEntity engineEntity) {
            engineEntity.checkRedstonePower();
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        // 向きの変更 (同じブロックへの置き換え) では中身を落とさない。
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof EngineBlockEntity engineEntity) {
            engineEntity.dropContents();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    /**
     * レッドストーン入力を受けているエンジンは正面のブロックへ信号を出す。
     * これにより直列に並べたエンジンがまとめて稼働する (旧 {@code BlockEngine.isPoweringTo})。
     *
     * @param direction 信号を問い合わせているブロックから見た、このブロックの方向
     */
    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        if (direction != state.getValue(FACING).getOpposite()) {
            return 0;
        }
        return level.getBlockEntity(pos) instanceof EngineBlockEntity engineEntity && engineEntity.isRedstonePowered()
                ? REDSTONE_SIGNAL : 0;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!(level.getBlockEntity(pos) instanceof EngineBlockEntity engineEntity) || !engineEntity.isBurning()) {
            return;
        }
        double centerX = pos.getX() + 0.5;
        double y = pos.getY() + random.nextFloat() * PARTICLE_MAX_HEIGHT;
        double centerZ = pos.getZ() + 0.5;
        double spread = random.nextFloat() * PARTICLE_SPREAD - PARTICLE_SPREAD / 2.0F;
        level.addParticle(DustParticleOptions.REDSTONE, centerX - PARTICLE_SIDE_OFFSET, y, centerZ + spread, 0.0, 0.0, 0.0);
        level.addParticle(DustParticleOptions.REDSTONE, centerX + PARTICLE_SIDE_OFFSET, y, centerZ + spread, 0.0, 0.0, 0.0);
        level.addParticle(DustParticleOptions.REDSTONE, centerX + spread, y, centerZ - PARTICLE_SIDE_OFFSET, 0.0, 0.0, 0.0);
        level.addParticle(DustParticleOptions.REDSTONE, centerX + spread, y, centerZ + PARTICLE_SIDE_OFFSET, 0.0, 0.0, 0.0);
    }
}
