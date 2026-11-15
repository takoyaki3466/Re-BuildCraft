package com.takoy3466.re_buildcraft.builders;

import com.takoy3466.re_buildcraft.api.IActionReceptor;
import com.takoy3466.re_buildcraft.api.IMachine;
import com.takoy3466.re_buildcraft.api.MachineControl;
import com.takoy3466.re_buildcraft.api.area.AreaProviders;
import com.takoy3466.re_buildcraft.api.area.IAreaProvider;
import com.takoy3466.re_buildcraft.api.power.PoweredBlockEntity;
import com.takoy3466.re_buildcraft.api.util.InventoryUtils;
import com.takoy3466.re_buildcraft.builders.filler.FillerCursor;
import com.takoy3466.re_buildcraft.builders.filler.FillerPattern;
import com.takoy3466.re_buildcraft.builders.filler.FillerPatterns;
import com.takoy3466.re_buildcraft.builders.filler.FillerRecipes;
import com.takoy3466.re_buildcraft.registry.ModBlockEntities;
import com.takoy3466.re_buildcraft.registry.ModItems;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.RangedWrapper;
import org.jetbrains.annotations.Nullable;

/**
 * フィラー。元実装: {@code buildcraft.builders.TileFiller} (BCx 版)。
 * <ul>
 *   <li>隣のマーカーなどから作業範囲を受け取り、マーカーは撤去する (旧 initialize)。</li>
 *   <li>作業台枠 (0 - 8) の並びかモジュールでパターンが決まる (旧 computeRecipe / FillerRecipe)。</li>
 *   <li>材料枠 (9 - 35) の最初のアイテムを材料として、作業量分の MJ が蓄えられている限り同じ tick の中で作業を繰り返す (旧 doWork の再帰呼び出し)。</li>
 *   <li>ゲートの運転指示: Off で停止、Loop で終わっても最初からやり直す、On で通常運転 (旧 actionActivated / lastMode)。</li>
 * </ul>
 */
public class FillerBlockEntity extends PoweredBlockEntity implements IMachine, IActionReceptor, MenuProvider, AreaHolder {
    public static final int GRID_SLOTS = FillerRecipes.GRID_SIZE;
    public static final int RESOURCE_SLOTS = 27;
    public static final int SLOT_COUNT = GRID_SLOTS + RESOURCE_SLOTS;
    /** ブロックステートの「パターンなし」。 */
    public static final int NO_PATTERN = 0;
    /** 旧コンストラクタの configure(10, 25, 100, 25, 100) と configurePowerPerdition(0, 1000)。 */
    private static final int INITIAL_MIN_RECEIVED = 25;
    private static final int INITIAL_MAX_RECEIVED = 100;
    private static final int INITIAL_WORK = 25;
    private static final int INITIAL_MAX_STORED = 100;
    private static final int PERDITION_LOSS = 0;
    private static final int PERDITION_REGULARITY = 1000;
    /** 旧 setPower の configure(10, 16, work * max, work, work * max) の最小受け入れ量。 */
    private static final int MIN_RECEIVED = 16;

    private static final String TAG_ITEMS = "items";
    private static final String TAG_BOX = "box";
    private static final String TAG_DONE = "done";
    private static final String TAG_LAST_MODE = "last_mode";

    private final ItemStackHandler inventory = new ItemStackHandler(SLOT_COUNT) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (slot < GRID_SLOTS) {
                computeRecipe();
            }
        }
    };
    private final IItemHandler resources = new RangedWrapper(inventory, GRID_SLOTS, SLOT_COUNT);
    private final FillerCursor cursor = new FillerCursor();
    @Nullable
    private BoundingBox box;
    @Nullable
    private FillerPatterns.Entry patternEntry;
    @Nullable
    private FillerPattern pattern;
    private boolean done = true;
    private boolean forceDone;
    /** 最後に受けた運転指示 (旧 lastMode。Unknown は null)。 */
    @Nullable
    private MachineControl lastMode;
    private int workEnergy = INITIAL_WORK;
    private boolean initialized;

    public FillerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FILLER.get(), pos, state);
        powerProvider.configure(INITIAL_MIN_RECEIVED, INITIAL_MAX_RECEIVED, INITIAL_WORK, INITIAL_MAX_STORED);
        powerProvider.configurePowerPerdition(PERDITION_LOSS, PERDITION_REGULARITY);
        // 大出力のエンジンから 1 tick に蓄電量 (パターンの作業量 x 回数) 以上が届いても、その分を捨てずに連続作業に使う。
        powerProvider.configureTickInputBuffer(true);
    }

    // ---- パターンから使う ------------------------------------------------------

    public ServerLevel serverLevel() {
        return (ServerLevel) level;
    }

    public FillerCursor cursor() {
        return cursor;
    }

    /** 作業範囲。パターンの初期化は範囲がある場合だけ行われる。 */
    public BoundingBox getArea() {
        return box == null ? new BoundingBox(worldPosition) : box;
    }

    public FakePlayer fakePlayer() {
        return FakePlayerPlacement.player(serverLevel());
    }

    /** 旧 setPower: 1 回の作業に使う MJ と、その何回分を蓄えるか。 */
    public void setPower(int work, int max) {
        powerProvider.configure(MIN_RECEIVED, work * max, work, work * max);
        workEnergy = work;
    }

    /** 旧 initTargetPosition: 範囲の最小の角から始める。 */
    public void initTargetPosition() {
        BoundingBox area = getArea();
        cursor.sx = area.minX();
        cursor.sy = area.minY();
        cursor.sz = area.minZ();
        cursor.ex = area.maxX();
        cursor.ey = area.maxY();
        cursor.ez = area.maxZ();
        cursor.cx = cursor.sx;
        cursor.cy = cursor.sy;
        cursor.cz = cursor.sz;
        cursor.mode = 0;
    }

    /** 旧 initRotatePosition: フィラーのある側から X・Z 方向に進む。 */
    public void initRotatePosition() {
        BoundingBox area = getArea();
        cursor.sy = area.minY();
        cursor.ey = area.maxY();
        if (area.minX() >= worldPosition.getX()) {
            cursor.sx = area.minX();
            cursor.ex = area.maxX();
            cursor.mx = 1;
        } else {
            cursor.sx = area.maxX();
            cursor.ex = area.minX();
            cursor.mx = -1;
        }
        if (area.minZ() >= worldPosition.getZ()) {
            cursor.sz = area.minZ();
            cursor.ez = area.maxZ();
            cursor.mz = 1;
        } else {
            cursor.sz = area.maxZ();
            cursor.ez = area.minZ();
            cursor.mz = -1;
        }
        cursor.mode = 0;
        cursor.cx = cursor.sx;
        cursor.cy = cursor.sy;
        cursor.cz = cursor.sz;
    }

    /** 液体の空き容器などを材料枠へ戻し、入らなければ排出する (旧 addItem / addPipeStackUtil)。 */
    public void storeOrEject(ItemStack stack) {
        ItemStack rest = ItemHandlerHelper.insertItemStacked(resources, stack, false);
        if (!rest.isEmpty()) {
            if (!InventoryUtils.insertIntoAdjacentPipe(serverLevel(), worldPosition, rest)) {
                InventoryUtils.ejectUpward(serverLevel(), worldPosition, rest);
            }
        }
    }

    // ---- tick -----------------------------------------------------------------

    public void serverTick() {
        if (level == null) {
            return;
        }
        if (!initialized) {
            initialized = true;
            initialize();
        }
        powerProvider.update(this, level);
        if (done) {
            if (lastMode == MachineControl.LOOP) {
                done = false;
            } else {
                updateBlockState();
                return;
            }
        }
        if (powerProvider.getEnergyStored() > workEnergy) {
            doWork();
        }
        updateBlockState();
    }

    /** 旧 initialize: 隣の範囲提供者 (マーカー) から範囲を受け取り、提供者を撤去してからパターンを決める。 */
    private void initialize() {
        IAreaProvider provider = AreaProviders.findNearby(level, worldPosition);
        if (provider != null) {
            box = provider.getArea();
            provider.removeFromWorld();
            sync();
        }
        computeRecipe();
    }

    /**
     * 旧 doWork: 運転指示が Off でなく、範囲・パターンがあり未完了なら、作業量分の MJ が蓄えられている限り同じ tick の中で作業を繰り返す
     * (旧実装は doWork の最後で蓄積 &gt; 作業量なら自分を呼び直していた)。1 tick の回数に固定の上限は無い。
     * <ul>
     *   <li>1 回の作業の前に、作業量分の MJ があるかを確かめる (足りなければ作業しない)。</li>
     *   <li>パターンを 1 回進め、ワールドを変えたか作業位置が進んだ場合だけ、作業量分の MJ を使う。</li>
     *   <li>何も変わらなかった場合 (材料が無い・置けない・液体が無いなど) は、同じ対象を繰り返さずにその tick の作業をやめる。MJ は使わない。</li>
     *   <li>完了したら繰り返しをやめる。完了後の空回りで MJ を使わない。</li>
     * </ul>
     * 繰り返すたびに作業量 (1 以上) の MJ を使うため、繰り返しの回数は蓄積量 ÷ 作業量で必ず終わる。
     */
    @Override
    public void doWork() {
        if (level == null || level.isClientSide || lastMode == MachineControl.OFF || box == null || pattern == null) {
            return;
        }
        while (!done && powerProvider.getEnergyStored() >= workEnergy) {
            int slot = firstResourceSlot();
            ItemStack stack = slot < 0 ? ItemStack.EMPTY : inventory.getStackInSlot(slot);
            int[] before = cursor.progressKey();
            cursor.acted = false;
            boolean finished = pattern.iterate(this, stack);
            if (slot >= 0) {
                // 材料はスタックを直接減らすため、変化を通知する。
                inventory.setStackInSlot(slot, stack.isEmpty() ? ItemStack.EMPTY : stack);
            }
            boolean progressed = cursor.acted || !Arrays.equals(before, cursor.progressKey());
            if (!finished && !progressed) {
                return;
            }
            powerProvider.useEnergy(workEnergy, workEnergy, true);
            done = finished;
        }
    }

    private int firstResourceSlot() {
        for (int slot = GRID_SLOTS; slot < SLOT_COUNT; slot++) {
            if (!inventory.getStackInSlot(slot).isEmpty()) {
                return slot;
            }
        }
        return -1;
    }

    /** 旧 computeRecipe: 作業台枠からパターンを決め直す。同じパターンならそのまま続ける。 */
    public void computeRecipe() {
        if (level == null || level.isClientSide) {
            return;
        }
        FillerPatterns.Entry found = FillerRecipes.match(inventory, entry -> ModItems.fillerModule(entry.id()).get()).orElse(null);
        if (found == patternEntry) {
            return;
        }
        patternEntry = found;
        pattern = found == null ? null : found.factory().get();
        if (pattern != null && box != null) {
            pattern.initialize(this);
        }
        if (pattern == null || forceDone) {
            done = lastMode != MachineControl.LOOP;
            forceDone = false;
        } else {
            done = false;
        }
        updateBlockState();
    }

    /** 作業台枠から作れるモジュール (旧 CraftingFiller.onCraftMatrixChanged)。 */
    public ItemStack moduleForCurrentPattern() {
        FillerPatterns.Entry entry = currentEntry();
        return entry == null ? ItemStack.EMPTY : new ItemStack(ModItems.fillerModule(entry.id()).get());
    }

    /** 現在のパターン。クライアントでは作業台枠の内容から求める。 */
    @Nullable
    public FillerPatterns.Entry currentEntry() {
        if (level != null && level.isClientSide) {
            return FillerRecipes.match(inventory, entry -> ModItems.fillerModule(entry.id()).get()).orElse(null);
        }
        return patternEntry;
    }

    /** モジュールを取り出したとき: 作業台枠の各スロットから 1 個ずつ使い、容器 (バケツ) は返す (旧 SlotFillerMode.onPickupFromSlot)。 */
    public void consumeGridForModule(Player player) {
        for (int slot = 0; slot < GRID_SLOTS; slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack container = stack.getCraftingRemainingItem();
            inventory.extractItem(slot, 1, false);
            if (!container.isEmpty() && !player.getInventory().add(container)) {
                if (inventory.getStackInSlot(slot).isEmpty()) {
                    inventory.setStackInSlot(slot, container);
                } else {
                    player.drop(container, false);
                }
            }
        }
    }

    private void updateBlockState() {
        if (level == null) {
            return;
        }
        BlockState state = getBlockState();
        int patternState = patternEntry == null ? NO_PATTERN : patternEntry.id() + 1;
        BlockState updated = state.setValue(FillerBlock.ACTIVE, isActive()).setValue(FillerBlock.PATTERN, patternState);
        if (updated != state) {
            level.setBlock(worldPosition, updated, Block.UPDATE_CLIENTS);
        }
    }

    // ---- IMachine / IActionReceptor -------------------------------------------------

    @Override
    public boolean isActive() {
        return !done && lastMode != MachineControl.OFF;
    }

    @Override
    public boolean manageSolids() {
        return true;
    }

    @Override
    public boolean allowActions() {
        return true;
    }

    @Override
    public void actionActivated(MachineControl control) {
        lastMode = control;
        setChanged();
    }

    @Override
    public int powerRequest() {
        return isActive() ? powerProvider.getMaxEnergyReceived() : 0;
    }

    // ---- 入出力 -----------------------------------------------------------------

    public ItemStackHandler getInventory() {
        return inventory;
    }

    /** パイプ・ホッパー向け。材料枠だけを公開する (旧 addItem / extractItem はスロット 9 以降)。 */
    public IItemHandler getResources() {
        return resources;
    }

    /** 旧 doDrop: 材料枠、作業台枠の順に落とす。 */
    public void dropContents() {
        if (level == null) {
            return;
        }
        for (int slot = GRID_SLOTS; slot < SLOT_COUNT; slot++) {
            InventoryUtils.dropStack(level, worldPosition, inventory.getStackInSlot(slot));
        }
        for (int slot = 0; slot < GRID_SLOTS; slot++) {
            InventoryUtils.dropStack(level, worldPosition, inventory.getStackInSlot(slot));
        }
    }

    // ---- MenuProvider -------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new FillerMenu(containerId, playerInventory, this);
    }

    @Nullable
    @Override
    public BoundingBox getBox() {
        return box;
    }

    // ---- 保存と同期 ---------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(TAG_ITEMS, inventory.serializeNBT(registries));
        writeBox(tag);
        tag.putBoolean(TAG_DONE, done);
        tag.putByte(TAG_LAST_MODE, (byte) (lastMode == null ? -1 : lastMode.ordinal()));
    }

    private void writeBox(CompoundTag tag) {
        if (box != null) {
            tag.put(TAG_BOX, new IntArrayTag(new int[]{box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()}));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        readBox(tag);
        if (!tag.contains(TAG_ITEMS)) {
            // クライアントへの同期 (範囲のみ)。インベントリはメニューで同期する。
            return;
        }
        inventory.deserializeNBT(registries, tag.getCompound(TAG_ITEMS));
        done = tag.getBoolean(TAG_DONE);
        int mode = tag.contains(TAG_LAST_MODE) ? tag.getByte(TAG_LAST_MODE) : -1;
        lastMode = mode >= 0 && mode < MachineControl.values().length ? MachineControl.values()[mode] : null;
        // 旧 readFromNBT: 読み込み直後は、パターンを決め直しても完了状態を保つ。
        forceDone = done;
    }

    private void readBox(CompoundTag tag) {
        int[] values = tag.contains(TAG_BOX, Tag.TAG_INT_ARRAY) ? tag.getIntArray(TAG_BOX) : new int[0];
        box = values.length == 6 ? new BoundingBox(values[0], values[1], values[2], values[3], values[4], values[5]) : null;
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        writeBox(tag);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
