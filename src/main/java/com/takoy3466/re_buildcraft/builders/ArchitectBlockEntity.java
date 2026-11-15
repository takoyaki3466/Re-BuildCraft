package com.takoy3466.re_buildcraft.builders;

import com.takoy3466.re_buildcraft.api.area.AreaProviders;
import com.takoy3466.re_buildcraft.api.area.IAreaProvider;
import com.takoy3466.re_buildcraft.api.util.InventoryUtils;
import com.takoy3466.re_buildcraft.builders.blueprint.Blueprint;
import com.takoy3466.re_buildcraft.builders.blueprint.BlueprintItem;
import com.takoy3466.re_buildcraft.builders.blueprint.BlueprintRef;
import com.takoy3466.re_buildcraft.builders.blueprint.BlueprintStore;
import com.takoy3466.re_buildcraft.registry.ModBlockEntities;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * 設計台 (Architect Table)。元実装: {@code buildcraft.builders.TileArchitect}。
 * <ul>
 *   <li>隣のマーカーの範囲を記録範囲にし、マーカーを撤去する。範囲が 2,097,152 ブロックを超える場合は使わない (旧 builderLimitSize)。</li>
 *   <li>白紙・記録済みのテンプレート/設計図を入れると 40 tick かけて範囲を記録し、記録済みのアイテムを出力枠に出す (旧 createBpt)。</li>
 *   <li>テンプレートは「何かあるか」だけを記録し、レッドストーン信号を受けている間は空いているマスを記録する。</li>
 *   <li>設計図は設計台の向きに合わせて東向きの基準へ回して保存する。同じ中身を続けて記録した場合は前回の番号を使う (旧 lastBptId)。</li>
 * </ul>
 */
public class ArchitectBlockEntity extends BlockEntity implements MenuProvider, AreaHolder, RotatableMachineBlock.Ticking,
        RotatableMachineBlock.Droppable {
    public static final int INPUT_SLOT = 0;
    public static final int OUTPUT_SLOT = 1;
    /** 旧 createTime。 */
    public static final int COMPUTE_TICKS = 40;
    /** 旧 builderLimitSize。 */
    private static final int MAX_VOLUME = 2_097_152;
    /** 名前の長さの上限 (旧 GUI は幅 88 ピクセルまで)。 */
    public static final int MAX_NAME_LENGTH = 32;
    private static final double MESSAGE_RANGE = 16.0;
    private static final String TAG_ITEMS = "items";
    private static final String TAG_BOX = "box";
    private static final String TAG_NAME = "name";
    private static final String TAG_LAST_ID = "last_blueprint_id";
    private static final String TAG_LAST_NAME = "last_blueprint_name";
    private static final String TAG_COMPUTING = "computing_time";

    private final ItemStackHandler inventory = new ItemStackHandler(2) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == INPUT_SLOT && stack.getItem() instanceof BlueprintItem;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    @Nullable
    private BoundingBox box;
    private String name = "";
    private int lastBlueprintId;
    /** {@link #lastBlueprintId} を保存したときに指定されていた名前 (保存時に重複回避で末尾が変わる前のもの)。 */
    private String lastBlueprintName = "";
    /** 記録の進み具合 (0 - {@link #COMPUTE_TICKS})。0 は記録していない状態。 */
    private int computingTime;
    private boolean initialized;

    public ArchitectBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ARCHITECT.get(), pos, state);
    }

    // ---- tick -----------------------------------------------------------------

    @Override
    public void serverTick() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (!initialized) {
            initialized = true;
            takeAreaFromMarkers(serverLevel);
        }
        if (!canCompute()) {
            computingTime = 0;
            return;
        }
        if (computingTime < COMPUTE_TICKS) {
            computingTime++;
        } else {
            createBlueprint(serverLevel);
            computingTime = 0;
        }
    }

    /** 旧 initialize: 範囲がまだ無ければ、隣のマーカーから受け取る。 */
    private void takeAreaFromMarkers(ServerLevel serverLevel) {
        if (box != null) {
            return;
        }
        IAreaProvider provider = AreaProviders.findNearby(serverLevel, worldPosition);
        if (provider == null) {
            return;
        }
        BoundingBox area = provider.getArea();
        long volume = (long) area.getXSpan() * area.getYSpan() * area.getZSpan();
        if (volume > MAX_VOLUME) {
            Component message = Component.translatable("message.re_buildcraft.architect.too_large");
            for (Player player : serverLevel.players()) {
                if (player.distanceToSqr(worldPosition.getCenter()) <= MESSAGE_RANGE * MESSAGE_RANGE) {
                    player.displayClientMessage(message, false);
                }
            }
            return;
        }
        box = area;
        provider.removeFromWorld();
        sync();
    }

    /** 旧 initializeComputing の条件: 範囲があり、入力枠にテンプレート/設計図があり、出力枠が空。 */
    private boolean canCompute() {
        return box != null && inventory.getStackInSlot(INPUT_SLOT).getItem() instanceof BlueprintItem
                && inventory.getStackInSlot(OUTPUT_SLOT).isEmpty();
    }

    /** 旧 createBpt。 */
    private void createBlueprint(ServerLevel serverLevel) {
        ItemStack input = inventory.getStackInSlot(INPUT_SLOT);
        if (box == null || !(input.getItem() instanceof BlueprintItem item)) {
            return;
        }
        Blueprint captured = Blueprint.capture(serverLevel, box, worldPosition, item.kind(), serverLevel.hasNeighborSignal(worldPosition));
        if (!name.isEmpty()) {
            captured.setName(name);
        }
        // 設計台の背中側が東になるよう回す (ビルダーで逆に回して元の向きに戻す)。
        int turns = RotatableMachineBlock.turnsFromEast(getBlockState().getValue(RotatableMachineBlock.FACING).getOpposite());
        Blueprint normalized = captured.rotateClockwise(4 - turns);
        BlueprintStore store = BlueprintStore.get(serverLevel.getServer());
        Blueprint previous = store.get(lastBlueprintId);
        int id;
        // 直前と同じ内容なら前の設計図を使い回す。ただし別の名前を付けた場合は新しく保存する
        // (使い回すと前の名前のままになり、名前を付けられない)。既存の設計図の名前は書き換えない (それを指す他のアイテムがあるため)。
        if (normalized.sameContents(previous) && (name.isEmpty() || name.equals(lastBlueprintName))) {
            id = lastBlueprintId;
        } else {
            id = store.store(normalized);
            lastBlueprintId = id;
            lastBlueprintName = name;
        }
        Blueprint stored = store.get(id);
        inventory.setStackInSlot(OUTPUT_SLOT, item.withBlueprint(new BlueprintRef(id, stored == null ? "" : stored.name())));
        inventory.setStackInSlot(INPUT_SLOT, ItemStack.EMPTY);
        setChanged();
    }

    // ---- 名前 -----------------------------------------------------------------

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        sync();
    }

    /** 旧 GuiTemplate の入力規則: 英数字と空白だけ。長さは {@link #MAX_NAME_LENGTH} まで。 */
    public static String sanitizeName(String name) {
        StringBuilder builder = new StringBuilder();
        for (char c : name.toCharArray()) {
            if ((Character.isLetterOrDigit(c) || c == ' ') && builder.length() < MAX_NAME_LENGTH) {
                builder.append(c);
            }
        }
        return builder.toString();
    }

    public int getComputingTime() {
        return computingTime;
    }

    public ItemStackHandler getInventory() {
        return inventory;
    }

    @Nullable
    @Override
    public BoundingBox getBox() {
        return box;
    }

    @Override
    public void dropContents() {
        if (level != null) {
            InventoryUtils.dropAll(level, worldPosition, inventory);
        }
    }

    // ---- MenuProvider -------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new ArchitectMenu(containerId, playerInventory, this);
    }

    // ---- 保存と同期 ---------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(TAG_ITEMS, inventory.serializeNBT(registries));
        writeShared(tag);
        tag.putInt(TAG_LAST_ID, lastBlueprintId);
        tag.putString(TAG_LAST_NAME, lastBlueprintName);
        tag.putInt(TAG_COMPUTING, computingTime);
    }

    private void writeShared(CompoundTag tag) {
        tag.putString(TAG_NAME, name);
        if (box != null) {
            tag.put(TAG_BOX, new IntArrayTag(new int[]{box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()}));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        name = tag.getString(TAG_NAME);
        int[] values = tag.contains(TAG_BOX, Tag.TAG_INT_ARRAY) ? tag.getIntArray(TAG_BOX) : new int[0];
        box = values.length == 6 ? new BoundingBox(values[0], values[1], values[2], values[3], values[4], values[5]) : null;
        if (tag.contains(TAG_ITEMS)) {
            inventory.deserializeNBT(registries, tag.getCompound(TAG_ITEMS));
            lastBlueprintId = tag.getInt(TAG_LAST_ID);
            lastBlueprintName = tag.getString(TAG_LAST_NAME);
            computingTime = tag.getInt(TAG_COMPUTING);
        }
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
        writeShared(tag);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
