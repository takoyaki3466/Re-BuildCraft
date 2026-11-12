package com.takoy3466.re_buildcraft.api.engine;

import com.takoy3466.re_buildcraft.api.power.BuildCraftCapabilities;
import com.takoy3466.re_buildcraft.api.power.IPowerReceptor;
import com.takoy3466.re_buildcraft.api.power.PowerProvider;
import com.takoy3466.re_buildcraft.api.transport.IPipeConnectable;
import com.takoy3466.re_buildcraft.api.util.InventoryUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * エンジンのブロックエンティティ。ピストン運動と、正面の機械への動力供給を担当する。
 * 元実装: {@code buildcraft.energy.TileEngine}。
 * <p>
 * エンジン固有の計算は {@link EngineLogic} に委譲する。向きは BlockState の
 * {@link EngineBlock#FACING} で保持する (旧実装の {@code orientation} フィールドに相当)。
 */
public class EngineBlockEntity extends BlockEntity implements IPowerReceptor, IPipeConnectable {
    /** ピストンが停止している。 */
    protected static final int STROKE_IDLE = 0;
    /** ピストンが伸びている途中 (行程の前半)。半分を過ぎた時点で動力を送る。 */
    protected static final int STROKE_EXTENDING = 1;
    /** 動力を送り終え、ピストンが戻っている途中 (行程の後半)。 */
    protected static final int STROKE_RETRACTING = 2;

    private static final int FUEL_SLOT = 0;
    private static final int IDLE_SYNC_INTERVAL_TICKS = 20;
    private static final float STROKE_HALF = 0.5F;
    private static final float STROKE_END = 1.0F;

    private static final String TAG_PROGRESS = "progress";
    private static final String TAG_ENERGY = "energy";
    private static final String TAG_INVENTORY = "inventory";
    private static final String TAG_ENGINE = "engine";
    private static final String TAG_PROGRESS_PART = "progressPart";
    private static final String TAG_PISTON_SPEED = "pistonSpeed";
    private static final String TAG_ENERGY_STAGE = "energyStage";
    private static final String TAG_REDSTONE_POWERED = "redstonePowered";

    protected final EngineLogic engine;
    private final PowerProvider provider = new PowerProvider();
    private final ItemStackHandler inventory = new ItemStackHandler(1) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    protected int progressPart = STROKE_IDLE;
    private float clientPistonSpeed;
    private boolean redstonePowered;
    private boolean initialized;

    public EngineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        if (!(state.getBlock() instanceof EngineBlock engineBlock)) {
            throw new IllegalStateException("EngineBlockEntity requires an EngineBlock, but got " + state.getBlock());
        }
        this.engine = engineBlock.createLogic(this);
    }

    /** エンジン用ブロックエンティティが共通して公開するケーパビリティを登録する。 */
    public static <E extends EngineBlockEntity> void registerCapabilities(RegisterCapabilitiesEvent event, BlockEntityType<E> type) {
        event.registerBlockEntity(BuildCraftCapabilities.POWER_RECEPTOR, type, (engineEntity, side) -> engineEntity);
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, type,
                (engineEntity, side) -> engineEntity.getEngine().usesInventory() ? engineEntity.getInventory() : null);
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, type,
                (engineEntity, side) -> engineEntity.getEngine().getFluidHandler());
    }

    // ---- tick -------------------------------------------------------------

    public final void tick() {
        if (level == null) {
            return;
        }
        if (level.isClientSide) {
            clientTick();
            return;
        }
        if (!initialized) {
            initialize();
            initialized = true;
        }
        provider.update(this, level);
        if (!isRemoved()) {
            serverTick();
        }
    }

    protected void initialize() {
        provider.configure(1, engine.maxEnergyReceived(), 1, engine.getMaxEnergy());
        checkRedstonePower();
    }

    private void clientTick() {
        if (progressPart == STROKE_IDLE) {
            return;
        }
        engine.setProgress(engine.getProgress() + clientPistonSpeed);
        if (engine.getProgress() > STROKE_END) {
            engine.setProgress(0.0F);
            progressPart = STROKE_IDLE;
        }
    }

    protected void serverTick() {
        engine.update();
        if (isRemoved()) {
            return;
        }

        if (progressPart != STROKE_IDLE) {
            engine.setProgress(engine.getProgress() + engine.getPistonSpeed());
            if (engine.getProgress() > STROKE_HALF && progressPart == STROKE_EXTENDING) {
                progressPart = STROKE_RETRACTING;
                transferEnergy(true);
            } else if (engine.getProgress() >= STROKE_END) {
                engine.setProgress(0.0F);
                progressPart = STROKE_IDLE;
            }
        } else if (redstonePowered && engine.isActive()) {
            if (transferEnergy(false) > 0) {
                progressPart = STROKE_EXTENDING;
                sendNetworkUpdate();
            }
        } else if (level.getGameTime() % IDLE_SYNC_INTERVAL_TICKS == 0L) {
            sendNetworkUpdate();
        }

        engine.burn();
        setChanged();
    }

    /**
     * 正面の機械が受け取れる範囲でエンジンからエネルギーを取り出す。
     *
     * @param doTransfer false の場合は送れる量を調べるだけ
     * @return 送った (送れる) エネルギー量
     */
    protected int transferEnergy(boolean doTransfer) {
        IPowerReceptor receptor = getReceptor(getFacing());
        if (receptor == null) {
            return 0;
        }
        PowerProvider target = receptor.getPowerProvider();
        int extracted = engine.extractEnergy(target.getMinEnergyReceived(), target.getMaxEnergyReceived(), doTransfer);
        if (doTransfer && extracted > 0) {
            target.receiveEnergy(extracted, getFacing().getOpposite());
        }
        return extracted;
    }

    // ---- 向き・動力の受け手 --------------------------------------------------

    public Direction getFacing() {
        return getBlockState().getValue(EngineBlock.FACING);
    }

    /** 指定方向の隣接ブロックが動力を受け取れるならその受け手を返す。 */
    @Nullable
    protected IPowerReceptor getReceptor(Direction direction) {
        if (level == null) {
            return null;
        }
        return level.getCapability(BuildCraftCapabilities.POWER_RECEPTOR, worldPosition.relative(direction), direction.getOpposite());
    }

    /**
     * 動力を受け取れる隣接ブロックを探して、そちらへ向きを変える (レンチ操作)。
     *
     * @param reverse true の場合は逆順に探す (スニーク時)
     * @return 向きを変えられた場合 true
     */
    public boolean switchOrientation(boolean reverse) {
        if (level == null) {
            return false;
        }
        Direction next = findReceptorDirection(getFacing(), reverse);
        if (next == null) {
            return false;
        }
        level.setBlock(worldPosition, getBlockState().setValue(EngineBlock.FACING, next), Block.UPDATE_ALL);
        return true;
    }

    @Nullable
    private Direction findReceptorDirection(Direction current, boolean reverse) {
        int directionCount = Direction.values().length;
        for (int step = 1; step <= directionCount; step++) {
            int index = Math.floorMod(current.get3DDataValue() + (reverse ? -step : step), directionCount);
            Direction candidate = Direction.from3DDataValue(index);
            if (getReceptor(candidate) != null) {
                return candidate;
            }
        }
        return null;
    }

    // ---- レッドストーン -----------------------------------------------------

    public boolean isRedstonePowered() {
        return redstonePowered;
    }

    /** 隣接ブロック更新時に呼ばれ、レッドストーン入力の有無を取り直す。 */
    public void checkRedstonePower() {
        if (level == null || level.isClientSide) {
            return;
        }
        setRedstonePowered(computeRedstonePowered(level));
    }

    /** レッドストーン入力をエンジンの稼働条件としてどう解釈するか。 */
    protected boolean computeRedstonePowered(Level level) {
        return level.hasNeighborSignal(worldPosition);
    }

    private void setRedstonePowered(boolean powered) {
        if (redstonePowered == powered || level == null) {
            return;
        }
        redstonePowered = powered;
        // エンジンは稼働中、正面へレッドストーン信号を出す (EngineBlock#getSignal)。
        // 正面に連結されたエンジンへ状態変化を伝えるため、隣接ブロックを更新する。
        level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
        sendNetworkUpdate();
    }

    // ---- IPowerReceptor ---------------------------------------------------

    @Override
    public PowerProvider getPowerProvider() {
        return provider;
    }

    @Override
    public void doWork() {
        engine.addEnergy((int) provider.useEnergy(1.0F, engine.maxEnergyReceived(), true));
    }

    @Override
    public int powerRequest() {
        return 0;
    }

    /**
     * 木エンジン系はパイプと接続しない。それ以外は出力面以外で接続する (旧 {@code TileEngine.isPipeConnected})。
     */
    @Override
    public boolean isPipeConnected(Direction side) {
        return !(engine instanceof RedstoneEngineLogic) && side != getFacing();
    }

    // ---- その他の操作 -------------------------------------------------------

    public EngineLogic getEngine() {
        return engine;
    }

    public ItemStackHandler getInventory() {
        return inventory;
    }

    public ItemStack getFuelStack() {
        return inventory.getStackInSlot(FUEL_SLOT);
    }

    /**
     * 燃料スロットから 1 個消費する。バケツなどの容器が残る場合は隣接インベントリへ送り、
     * 入らなければ上へ放出する (旧 {@code TileBuildCraft.decContainerSlot})。
     */
    public void consumeFuelItem() {
        ItemStack stack = getFuelStack();
        if (stack.isEmpty()) {
            return;
        }
        ItemStack remainder = stack.getCraftingRemainingItem();
        stack.shrink(1);
        inventory.setStackInSlot(FUEL_SLOT, stack.isEmpty() ? ItemStack.EMPTY : stack);
        ejectItem(remainder);
    }

    /** アイテムを隣接インベントリへ送り、入り切らない分は上へ放出する。 */
    public void ejectItem(ItemStack stack) {
        if (level == null || stack.isEmpty()) {
            return;
        }
        ItemStack remainder = InventoryUtils.insertIntoAdjacentInventories(level, worldPosition, stack);
        InventoryUtils.ejectUpward(level, worldPosition, remainder);
    }

    /** ブロックごと消滅して爆発する (過熱時)。 */
    public void explode(int range) {
        if (level == null || level.isClientSide || isRemoved()) {
            return;
        }
        BlockPos pos = worldPosition;
        level.removeBlock(pos, false);
        level.explode(null, pos.getX(), pos.getY(), pos.getZ(), range, Level.ExplosionInteraction.BLOCK);
    }

    /** ブロックが取り除かれたときに内容物を落とす。 */
    public void dropContents() {
        if (level != null) {
            InventoryUtils.dropStack(level, worldPosition, getFuelStack());
            inventory.setStackInSlot(FUEL_SLOT, ItemStack.EMPTY);
        }
        engine.onRemoved();
    }

    public boolean isBurning() {
        return engine.isBurning();
    }

    protected void sendNetworkUpdate() {
        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    // ---- 保存と同期 ---------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putFloat(TAG_PROGRESS, engine.getProgress());
        tag.putInt(TAG_ENERGY, engine.getEnergy());
        tag.put(TAG_INVENTORY, inventory.serializeNBT(registries));
        CompoundTag engineTag = new CompoundTag();
        engine.save(engineTag, registries);
        tag.put(TAG_ENGINE, engineTag);
        // 以下はクライアント表示用。サーバー側では読み込み後に再計算される。
        tag.putInt(TAG_PROGRESS_PART, progressPart);
        tag.putFloat(TAG_PISTON_SPEED, engine.getPistonSpeed());
        tag.putInt(TAG_ENERGY_STAGE, engine.getEnergyStage().ordinal());
        tag.putBoolean(TAG_REDSTONE_POWERED, redstonePowered);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        engine.setProgress(tag.getFloat(TAG_PROGRESS));
        engine.setEnergy(tag.getInt(TAG_ENERGY));
        inventory.deserializeNBT(registries, tag.getCompound(TAG_INVENTORY));
        engine.load(tag.getCompound(TAG_ENGINE), registries);
        progressPart = tag.getInt(TAG_PROGRESS_PART);
        clientPistonSpeed = tag.getFloat(TAG_PISTON_SPEED);
        engine.setEnergyStage(EnergyStage.byOrdinal(tag.getInt(TAG_ENERGY_STAGE)));
        redstonePowered = tag.getBoolean(TAG_REDSTONE_POWERED);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
