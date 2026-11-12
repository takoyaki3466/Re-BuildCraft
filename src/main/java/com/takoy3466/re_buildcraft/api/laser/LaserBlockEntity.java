package com.takoy3466.re_buildcraft.api.laser;

import com.takoy3466.re_buildcraft.api.power.PoweredBlockEntity;
import com.takoy3466.re_buildcraft.api.util.SafeTimeTracker;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * レーザーのブロックエンティティ。前方の {@link ILaserTarget} を探してエネルギーを照射する。
 * 元実装: {@code buildcraft.silicon.TileLaser} (+ ビーム表示用の {@code EntityEnergyLaser})。
 * <p>
 * 向きはブロックの {@link BlockStateProperties#FACING} から取得する。性能は {@link LaserSpec} で与えるため、
 * 性能違いのレーザーはこのクラスを継承せずに追加できる。
 * 旧実装はビームを専用エンティティで表示していたが、ここでは照射先を同期して BlockEntityRenderer で描画する。
 */
public class LaserBlockEntity extends PoweredBlockEntity {
    /** ビームの見た目の段階数 (laser_1 - laser_4)。 */
    public static final int BEAM_STAGE_COUNT = 4;

    private static final int POWER_HISTORY_SIZE = 100;
    private static final int SEARCH_INTERVAL_BASE_TICKS = 190;
    private static final int SEARCH_INTERVAL_RANDOM_TICKS = 20;
    private static final int INITIAL_SEARCH_INTERVAL_TICKS = 200;
    private static final int JITTER_INTERVAL_BASE_TICKS = 5;
    private static final int JITTER_INTERVAL_RANDOM_TICKS = 10;
    private static final int INITIAL_JITTER_INTERVAL_TICKS = 10;
    private static final double BEAM_ORIGIN_OFFSET = 0.3;
    private static final double TARGET_CENTER_XZ = 0.475;
    private static final double TARGET_HEIGHT = 0.5625;
    private static final double TARGET_JITTER_DIVISOR = 5.0;
    private static final String TAG_TARGET = "target";
    private static final String TAG_BEAM_STAGE = "beamStage";

    private final LaserSpec spec;
    private final SafeTimeTracker searchTracker = new SafeTimeTracker();
    private final SafeTimeTracker jitterTracker = new SafeTimeTracker();
    private final float[] powerHistory = new float[POWER_HISTORY_SIZE];
    private int powerHistoryIndex;
    private float powerHistorySum;
    private int nextSearchInterval = INITIAL_SEARCH_INTERVAL_TICKS;
    private int nextJitterInterval = INITIAL_JITTER_INTERVAL_TICKS;

    @Nullable
    private BlockPos targetPos;
    private int beamStage;
    /** クライアント表示用: 照射点のゆらぎ込みの座標。 */
    @Nullable
    private Vec3 beamEnd;

    public LaserBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, LaserSpec spec) {
        super(type, pos, state);
        this.spec = spec;
        powerProvider.configure(spec.minEnergyReceived(), spec.maxEnergyReceived(), spec.minActivationEnergy(), spec.maxEnergyStored());
    }

    public final void tick() {
        if (level == null) {
            return;
        }
        if (level.isClientSide) {
            clientTick(level);
        } else {
            serverTick(level);
        }
    }

    private void serverTick(Level level) {
        if (powerProvider.getEnergyStored() == 0.0F) {
            setTarget(null);
            return;
        }

        if (searchTracker.markTimeIfDelay(level, nextSearchInterval)) {
            aim(level);
            nextSearchInterval = SEARCH_INTERVAL_BASE_TICKS + level.random.nextInt(SEARCH_INTERVAL_RANDOM_TICKS);
        }

        ILaserTarget target = getTarget(level);
        if (target == null || !target.requiresLaserEnergy()) {
            setTarget(null);
            return;
        }

        float energy = powerProvider.useEnergy(0.0F, spec.maxEnergyPerTick(), true);
        pushPower(energy / (spec.maxEnergyPerTick() / BEAM_STAGE_COUNT));
        target.receiveLaserEnergy(energy);
        setChanged();
    }

    private void clientTick(Level level) {
        if (targetPos == null) {
            beamEnd = null;
            return;
        }
        if (beamEnd == null || jitterTracker.markTimeIfDelay(level, nextJitterInterval)) {
            beamEnd = new Vec3(
                    targetPos.getX() + TARGET_CENTER_XZ + (level.random.nextFloat() - 0.5) / TARGET_JITTER_DIVISOR,
                    targetPos.getY() + TARGET_HEIGHT,
                    targetPos.getZ() + TARGET_CENTER_XZ + (level.random.nextFloat() - 0.5) / TARGET_JITTER_DIVISOR);
            nextJitterInterval = JITTER_INTERVAL_BASE_TICKS + level.random.nextInt(JITTER_INTERVAL_RANDOM_TICKS);
        }
    }

    /** 前方の探索範囲から、照射を必要としている対象を無作為に 1 つ選ぶ。 */
    private void aim(Level level) {
        Direction facing = getFacing();
        int range = spec.searchRange();
        BlockPos min = worldPosition.offset(-range, -range, -range);
        BlockPos max = worldPosition.offset(range, range, range);
        // 向いている軸方向については、自分より後ろ側を探索範囲から除く。
        BlockPos clampedMin = facing.getAxisDirection() == Direction.AxisDirection.POSITIVE
                ? clampToOwnPosition(min, facing.getAxis()) : min;
        BlockPos clampedMax = facing.getAxisDirection() == Direction.AxisDirection.NEGATIVE
                ? clampToOwnPosition(max, facing.getAxis()) : max;

        List<BlockPos> candidates = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(clampedMin, clampedMax)) {
            if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof ILaserTarget target && target.requiresLaserEnergy()) {
                candidates.add(pos.immutable());
            }
        }
        if (!candidates.isEmpty()) {
            setTarget(candidates.get(level.random.nextInt(candidates.size())));
        }
    }

    private BlockPos clampToOwnPosition(BlockPos pos, Direction.Axis axis) {
        return switch (axis) {
            case X -> new BlockPos(worldPosition.getX(), pos.getY(), pos.getZ());
            case Y -> new BlockPos(pos.getX(), worldPosition.getY(), pos.getZ());
            case Z -> new BlockPos(pos.getX(), pos.getY(), worldPosition.getZ());
        };
    }

    @Nullable
    private ILaserTarget getTarget(Level level) {
        if (targetPos == null || !level.isLoaded(targetPos)) {
            return null;
        }
        return level.getBlockEntity(targetPos) instanceof ILaserTarget target ? target : null;
    }

    private void setTarget(@Nullable BlockPos newTarget) {
        if (Objects.equals(targetPos, newTarget)) {
            return;
        }
        targetPos = newTarget;
        sendNetworkUpdate();
    }

    /** 直近の照射量の移動平均から、ビームの見た目の段階を決める。 */
    private void pushPower(float power) {
        powerHistorySum += power - powerHistory[powerHistoryIndex];
        powerHistory[powerHistoryIndex] = power;
        powerHistoryIndex = (powerHistoryIndex + 1) % POWER_HISTORY_SIZE;

        float average = powerHistorySum / POWER_HISTORY_SIZE;
        int stage = Math.min(BEAM_STAGE_COUNT - 1, Math.max(0, (int) Math.ceil(average) - 1));
        if (stage != beamStage) {
            beamStage = stage;
            sendNetworkUpdate();
        }
    }

    private void sendNetworkUpdate() {
        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    // ---- 表示用アクセサ -----------------------------------------------------

    public Direction getFacing() {
        return getBlockState().getValue(BlockStateProperties.FACING);
    }

    public LaserSpec getSpec() {
        return spec;
    }

    /** ビームの始点 (ワールド座標)。 */
    public Vec3 getBeamStart() {
        Direction facing = getFacing();
        return Vec3.atCenterOf(worldPosition).add(
                facing.getStepX() * BEAM_ORIGIN_OFFSET, facing.getStepY() * BEAM_ORIGIN_OFFSET, facing.getStepZ() * BEAM_ORIGIN_OFFSET);
    }

    /** ビームの終点 (ワールド座標)。照射していない間は null。 */
    @Nullable
    public Vec3 getBeamEnd() {
        return beamEnd;
    }

    /** 0 から {@link #BEAM_STAGE_COUNT} - 1。 */
    public int getBeamStage() {
        return beamStage;
    }

    // ---- IPowerReceptor ---------------------------------------------------

    @Override
    public void doWork() {
    }

    @Override
    public int powerRequest() {
        boolean idleAndCharged = powerProvider.getEnergyStored() >= spec.requestStored() && targetPos == null;
        return idleAndCharged ? 0 : powerProvider.getMaxEnergyReceived();
    }

    // ---- 保存と同期 ---------------------------------------------------------

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        // 以下は同期パケットにのみ含まれる。
        targetPos = tag.contains(TAG_TARGET) ? BlockPos.of(tag.getLong(TAG_TARGET)) : null;
        beamStage = tag.getInt(TAG_BEAM_STAGE);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        if (targetPos != null) {
            tag.putLong(TAG_TARGET, targetPos.asLong());
        }
        tag.putInt(TAG_BEAM_STAGE, beamStage);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
