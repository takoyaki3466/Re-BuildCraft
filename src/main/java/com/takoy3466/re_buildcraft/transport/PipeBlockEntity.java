package com.takoy3466.re_buildcraft.transport;

import com.takoy3466.re_buildcraft.api.power.IPowerReceptor;
import com.takoy3466.re_buildcraft.api.power.PowerProvider;
import com.takoy3466.re_buildcraft.api.transport.IPipeEntry;
import com.takoy3466.re_buildcraft.api.transport.PipeConnections;
import com.takoy3466.re_buildcraft.transport.behavior.PipeBehavior;
import com.takoy3466.re_buildcraft.transport.gate.Gate;
import com.takoy3466.re_buildcraft.transport.gate.PipeSignals;
import com.takoy3466.re_buildcraft.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * パイプのブロックエンティティ。元実装: {@code buildcraft.transport.TileGenericPipe} + {@code Pipe}。
 * <p>
 * 輸送処理は {@link PipeTransport}、素材ごとの振る舞いは {@link PipeBehavior}、
 * ワイヤーの信号は {@link PipeSignals}、ゲートは {@link Gate} に委譲し、このクラスはそれらの橋渡しと保存・同期を受け持つ。
 */
public class PipeBlockEntity extends BlockEntity implements IPipeEntry, IPowerReceptor {
    private static final String TAG_TRANSPORT = "transport";
    private static final String TAG_BEHAVIOR = "behavior";
    private static final String TAG_POWER = "power";
    private static final String TAG_SIGNALS = "signals";
    private static final String TAG_GATE = "gate";

    private final PipeSpec type;
    private final PipeTransport transport;
    private final PipeBehavior behavior;
    private final PipeSignals signals = new PipeSignals();
    @Nullable
    private Gate gate;
    private boolean initialized;
    private boolean syncScheduled;

    public PipeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PIPE.get(), pos, state);

        if (!(state.getBlock() instanceof PipeBlock pipeBlock)) {
            throw new IllegalStateException("PipeBlockEntity requires a PipeBlock, but got " + state.getBlock());
        }

        this.type = pipeBlock.getPipeType();
        this.transport = PipeTransport.create(this, type);
        this.behavior = type.createBehavior();
    }

    // ---- tick -------------------------------------------------------------

    public void tick() {
        if (level == null) {
            return;
        }

        if (level.isClientSide) {
            transport.clientTick();
            return;
        }

        ServerLevel serverLevel = (ServerLevel) level;

        if (!initialized) {
            initialized = true;
            behavior.initialize(this);
            signals.update(this);
        }

        PowerProvider provider = behavior.powerProvider();

        if (provider != null) {
            provider.update(this, serverLevel);
        }

        behavior.tick(this);
        transport.tick(serverLevel);
        signals.tick(this);

        if (gate != null) {
            gate.tick(this);
        }

        if (syncScheduled) {
            syncScheduled = false;
            BlockState state = getBlockState();
            serverLevel.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }

        setChanged();
    }

    /**
     * この tick の終わりにクライアントへ状態を送る。
     */
    public void scheduleSync() {
        syncScheduled = true;
    }

    // ---- 接続 -------------------------------------------------------------

    public boolean isConnected(Direction direction) {
        return getBlockState().getValue(PipeConnections.property(direction));
    }

    /**
     * 接続している隣のパイプ。
     */
    @Nullable
    public PipeBlockEntity getConnectedPipe(Direction direction) {
        if (level == null || !isConnected(direction)) {
            return null;
        }
        return level.getBlockEntity(worldPosition.relative(direction)) instanceof PipeBlockEntity other ? other : null;
    }

    /**
     * 方向付きパイプ (木・鉄) が指している面。
     */
    @Nullable
    public Direction getSpecialSide() {
        BlockState state = getBlockState();
        return state.hasProperty(DirectionalPipeBlock.SPECIAL) ? state.getValue(DirectionalPipeBlock.SPECIAL).direction() : null;
    }

    public void setSpecialSide(@Nullable Direction direction) {
        BlockState state = getBlockState();

        if (level != null && state.hasProperty(DirectionalPipeBlock.SPECIAL)) {
            level.setBlock(worldPosition, state.setValue(DirectionalPipeBlock.SPECIAL, PipeSide.of(direction)), Block.UPDATE_ALL);
        }
    }

    /**
     * 接続している面がちょうど 1 つなら、その反対側 (開いた口)。そうでなければ null (旧 Pipe.getOpenOrientation)。
     */
    @Nullable
    public Direction getOpenSide() {
        Direction connected = null;
        for (Direction direction : Direction.values()) {
            if (isConnected(direction)) {
                if (connected != null) {
                    return null;
                }
                connected = direction;
            }
        }
        return connected == null ? null : connected.getOpposite();
    }

    /**
     * 別の種類のアイテムパイプに作り替える。運搬中のアイテム・ワイヤー・ゲートは引き継ぐ (旧 PipeItemsStripes.convertPipe)。
     */
    public void convertTo(PipeBlock newBlock) {
        if (level == null || newBlock.getPipeType().kind() != type.kind()) {
            return;
        }

        HolderLookup.Provider registries = level.registryAccess();

        CompoundTag transportTag = transport.save(registries);
        CompoundTag signalsTag = signals.save();
        CompoundTag gateTag = gate == null ? null : gate.save(registries);

        BlockPos pos = worldPosition;
        Level currentLevel = level;
        BlockState newState = Block.updateFromNeighbourShapes(newBlock.defaultBlockState(), currentLevel, pos);

        // 置き換えで中身を落とさないよう、先に空にしてから差し替える。
        transport.load(new CompoundTag(), registries);
        signals.load(new CompoundTag());
        gate = null;

        currentLevel.setBlock(pos, newState, Block.UPDATE_ALL);

        if (currentLevel.getBlockEntity(pos) instanceof PipeBlockEntity converted) {
            converted.transport.load(transportTag, registries);
            converted.signals.load(signalsTag);
            converted.gate = gateTag == null ? null : Gate.load(gateTag, registries);

            converted.scheduleSync();
            converted.setChanged();
        }
    }

    /**
     * 素材の制約込みで、{@code direction} へ送り出せるか (旧 Pipe.outputOpen)。
     */
    public boolean outputOpen(Direction direction) {
        return behavior.outputOpen(this, direction);
    }

    // ---- ブロックからの通知 ----------------------------------------------------

    public void onNeighborChanged() {
        behavior.onNeighborChanged(this);
        transport.onNeighborChanged();
        signals.update(this);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        behavior.onRemoved(this);
    }

    /**
     * 撤去時: 中身・ワイヤー・ゲートを落とす。
     */
    public void dropContents() {
        if (level == null) {
            return;
        }

        transport.dropContents(level, worldPosition);
        signals.dropWires(level, worldPosition);

        if (gate != null) {
            Block.popResource(level, worldPosition, gate.kind().toItemStack());
        }
    }

    // ---- ゲート ---------------------------------------------------------------

    @Nullable
    public Gate getGate() {
        return gate;
    }

    public void setGate(@Nullable Gate gate) {
        this.gate = gate;
        scheduleSync();
        setChanged();
    }

    public PipeSignals signals() {
        return signals;
    }

    // ---- アクセサ -------------------------------------------------------------

    public PipeSpec type() {
        return type;
    }

    public PipeTransport transport() {
        return transport;
    }

    public PipeBehavior behavior() {
        return behavior;
    }

    // ---- IPipeEntry -----------------------------------------------------------

    @Override
    public boolean acceptsItems() {
        return transport instanceof ItemTransport;
    }

    @Override
    public void injectItem(ItemStack stack, Direction heading) {
        if (transport instanceof ItemTransport items) {
            items.injectFromSide(stack, heading, TravelingItem.NORMAL_SPEED);
        }
    }

    // ---- IPowerReceptor (木・黒曜石パイプのみケーパビリティとして公開) ------------------

    @Override
    public PowerProvider getPowerProvider() {
        PowerProvider provider = behavior.powerProvider();

        if (provider == null) {
            throw new IllegalStateException(type.id() + " does not receive power");
        }

        return provider;
    }

    @Override
    public void doWork() {
        behavior.doWork(this);
    }

    @Override
    public int powerRequest() {
        PowerProvider provider = behavior.powerProvider();

        return provider == null ? 0 : provider.getMaxEnergyReceived();
    }

    // ---- 保存と同期 ---------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(TAG_TRANSPORT, transport.save(registries));

        CompoundTag behaviorTag = new CompoundTag();

        behavior.save(behaviorTag, registries);
        tag.put(TAG_BEHAVIOR, behaviorTag);

        PowerProvider provider = behavior.powerProvider();

        if (provider != null) {
            CompoundTag powerTag = new CompoundTag();
            provider.save(powerTag);

            tag.put(TAG_POWER, powerTag);

        }

        writeSharedState(tag, registries);
    }

    private void writeSharedState(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put(TAG_SIGNALS, signals.save());
        if (gate != null) {
            tag.put(TAG_GATE, gate.save(registries));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);

        if (tag.contains(TAG_TRANSPORT)) {
            transport.load(tag.getCompound(TAG_TRANSPORT), registries);
        }

        behavior.load(tag.getCompound(TAG_BEHAVIOR), registries);
        PowerProvider provider = behavior.powerProvider();

        if (provider != null) {
            provider.load(tag.getCompound(TAG_POWER));
        }

        signals.load(tag.getCompound(TAG_SIGNALS));
        gate = tag.contains(TAG_GATE) ? Gate.load(tag.getCompound(TAG_GATE), registries) : null;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.put(TAG_TRANSPORT, transport.saveForClient(registries));
        writeSharedState(tag, registries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
