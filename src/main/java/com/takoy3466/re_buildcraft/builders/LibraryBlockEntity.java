package com.takoy3466.re_buildcraft.builders;

import com.takoy3466.re_buildcraft.api.util.InventoryUtils;
import com.takoy3466.re_buildcraft.builders.blueprint.Blueprint;
import com.takoy3466.re_buildcraft.builders.blueprint.BlueprintItem;
import com.takoy3466.re_buildcraft.builders.blueprint.BlueprintRef;
import com.takoy3466.re_buildcraft.builders.blueprint.BlueprintStore;
import com.takoy3466.re_buildcraft.registry.ModBlockEntities;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * 設計図ライブラリ。元実装: {@code buildcraft.builders.TileBlueprintLibrary}。
 * <ul>
 *   <li>登録: 入力枠 (0) に記録済みのテンプレート/設計図を入れると 100 tick で出力枠 (1) へ移り、その設計図を一覧に加える。</li>
 *   <li>複製: 複製枠 (2) にテンプレート/設計図を入れると 100 tick で、一覧で選んだ設計図を記録した同じ種類のアイテムを出力枠 (3) に出す
 *       (選んでいなければ白紙)。</li>
 *   <li>一覧から外す (Del)、ロック (持ち主以外は開けない) ができる。</li>
 * </ul>
 * 旧実装の一覧は「操作しているプレイヤー」(シングルプレイのローカルプレイヤー) のものだった。
 * サーバーではライブラリを置いたプレイヤー (持ち主) の一覧を使う。持ち主がいなければ開いたプレイヤーの一覧を使う。
 */
public class LibraryBlockEntity extends BlockEntity implements MenuProvider, RotatableMachineBlock.Ticking, RotatableMachineBlock.Droppable {
    public static final int INPUT_SLOT = 0;
    public static final int REGISTERED_SLOT = 1;
    public static final int COPY_SLOT = 2;
    public static final int COPIED_SLOT = 3;
    public static final int PROGRESS_MAX = 100;
    private static final String TAG_ITEMS = "items";
    private static final String TAG_OWNER = "owner";
    private static final String TAG_LOCKED = "locked";
    private static final String TAG_SELECTED = "selected";

    private final ItemStackHandler inventory = new ItemStackHandler(4) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return (slot == INPUT_SLOT || slot == COPY_SLOT) && stack.getItem() instanceof BlueprintItem;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    @Nullable
    private UUID owner;
    private boolean locked;
    /** 一覧で選んでいる設計図の番号 (0 は未選択)。 */
    private int selected;
    private int progressIn;
    private int progressOut;
    /** 一覧が変わるたびに増える (開いている画面へ送り直すため。保存しない)。 */
    private int listVersion;

    public LibraryBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LIBRARY.get(), pos, state);
    }

    // ---- tick (旧 updateEntity) ------------------------------------------------------

    @Override
    public void serverTick() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        progressIn = advance(progressIn, INPUT_SLOT);
        progressOut = advance(progressOut, COPY_SLOT);
        if (progressIn == PROGRESS_MAX && inventory.getStackInSlot(REGISTERED_SLOT).isEmpty()) {
            register(serverLevel);
        }
        if (progressOut == PROGRESS_MAX && inventory.getStackInSlot(COPIED_SLOT).isEmpty()) {
            copy(serverLevel);
        }
    }

    /** 枠にテンプレート/設計図があれば 1 から 100 まで進め、無ければ 0 に戻す。 */
    private int advance(int progress, int slot) {
        if (!(inventory.getStackInSlot(slot).getItem() instanceof BlueprintItem)) {
            return 0;
        }
        return Math.min(PROGRESS_MAX, Math.max(1, progress + 1));
    }

    private void register(ServerLevel serverLevel) {
        ItemStack stack = inventory.getStackInSlot(INPUT_SLOT);
        inventory.setStackInSlot(REGISTERED_SLOT, stack);
        inventory.setStackInSlot(INPUT_SLOT, ItemStack.EMPTY);
        progressIn = 0;
        BlueprintRef ref = BlueprintItem.ref(stack);
        UUID indexOwner = owner;
        if (ref != null && indexOwner != null) {
            BlueprintStore.get(serverLevel.getServer()).addToPlayer(indexOwner, ref.id());
            listVersion++;
        }
    }

    private void copy(ServerLevel serverLevel) {
        ItemStack stack = inventory.getStackInSlot(COPY_SLOT);
        if (!(stack.getItem() instanceof BlueprintItem item)) {
            return;
        }
        Blueprint blueprint = selected == 0 ? null : BlueprintStore.get(serverLevel.getServer()).get(selected);
        inventory.setStackInSlot(COPIED_SLOT, item.withBlueprint(blueprint == null ? null : new BlueprintRef(selected, blueprint.name())));
        inventory.setStackInSlot(COPY_SLOT, ItemStack.EMPTY);
        progressOut = 0;
    }

    // ---- 一覧の操作 (メニューから。値はサーバーで検証する) ------------------------------------------

    /** 一覧に使うプレイヤー (持ち主、いなければ開いたプレイヤー)。 */
    public UUID indexOwner(Player viewer) {
        return owner != null ? owner : viewer.getUUID();
    }

    public List<BuilderPayloads.LibraryEntry> entries(ServerLevel serverLevel, Player viewer) {
        BlueprintStore store = BlueprintStore.get(serverLevel.getServer());
        List<BuilderPayloads.LibraryEntry> entries = new ArrayList<>();
        for (int id : store.playerIndex(indexOwner(viewer))) {
            Blueprint blueprint = store.get(id);
            if (blueprint != null) {
                entries.add(new BuilderPayloads.LibraryEntry(id, blueprint.name()));
            }
        }
        return entries;
    }

    /** 一覧にある設計図だけを選べる。 */
    public void select(ServerLevel serverLevel, Player viewer, int id) {
        if (BlueprintStore.get(serverLevel.getServer()).playerIndex(indexOwner(viewer)).contains(id)) {
            selected = id;
            setChanged();
        }
    }

    /** 旧 Del ボタン: 選んでいる設計図を一覧から外す (設計図そのものは消さない)。 */
    public void deleteSelected(ServerLevel serverLevel, Player viewer) {
        if (selected != 0) {
            BlueprintStore.get(serverLevel.getServer()).removeFromPlayer(indexOwner(viewer), selected);
            selected = 0;
            listVersion++;
            setChanged();
        }
    }

    public void toggleLocked() {
        locked = !locked;
        setChanged();
    }

    /** ロックされていれば持ち主だけが開ける (旧 BlockBlueprintLibrary.blockActivated)。 */
    public boolean canOpen(Player player) {
        return !locked || owner == null || owner.equals(player.getUUID());
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
        setChanged();
    }

    public boolean isLocked() {
        return locked;
    }

    public int getSelected() {
        return selected;
    }

    public int getProgressIn() {
        return progressIn;
    }

    public int getProgressOut() {
        return progressOut;
    }

    public int getListVersion() {
        return listVersion;
    }

    public ItemStackHandler getInventory() {
        return inventory;
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
        return new LibraryMenu(containerId, playerInventory, this);
    }

    // ---- 保存 -----------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(TAG_ITEMS, inventory.serializeNBT(registries));
        if (owner != null) {
            tag.putUUID(TAG_OWNER, owner);
        }
        tag.putBoolean(TAG_LOCKED, locked);
        tag.putInt(TAG_SELECTED, selected);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        inventory.deserializeNBT(registries, tag.getCompound(TAG_ITEMS));
        owner = tag.hasUUID(TAG_OWNER) ? tag.getUUID(TAG_OWNER) : null;
        locked = tag.getBoolean(TAG_LOCKED);
        selected = tag.getInt(TAG_SELECTED);
    }
}
