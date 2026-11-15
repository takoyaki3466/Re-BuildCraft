package com.takoy3466.re_buildcraft.builders;

import com.takoy3466.re_buildcraft.api.menu.PlayerInventorySlots;
import com.takoy3466.re_buildcraft.builders.blueprint.BlueprintItem;
import com.takoy3466.re_buildcraft.registry.ModMenus;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.SlotItemHandler;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 設計図ライブラリの GUI メニュー。元実装: {@code ContainerBlueprintLibrary} と {@code GuiBlueprintLibrary} の操作部分。
 * <p>
 * 一覧はサーバーが {@link BuilderPayloads.LibraryContents} で送り、選択・Del・Lock はメニューボタンとして送ってサーバーで検証する。
 */
public class LibraryMenu extends AbstractContainerMenu {
    public static final int BUTTON_DELETE = 0;
    public static final int BUTTON_LOCK = 1;
    /** 選択ボタンの番号 = この値 + 設計図の番号。 */
    public static final int BUTTON_SELECT_BASE = 1000;
    private static final int PLAYER_INVENTORY_Y = 140;
    private static final int MACHINE_SLOTS = 4;
    private static final int PLAYER_END = MACHINE_SLOTS + Inventory.INVENTORY_SIZE;
    private static final int DATA_PROGRESS_IN = 0;
    private static final int DATA_PROGRESS_OUT = 1;
    private static final int DATA_SELECTED_LOW = 2;
    private static final int DATA_SELECTED_HIGH = 3;
    private static final int DATA_LOCKED = 4;
    private static final int DATA_COUNT = 5;
    private static final int SHORT_BITS = 16;
    private static final int SHORT_MASK = 0xFFFF;

    private final LibraryBlockEntity library;
    private final Player player;
    private final ContainerData data;
    private List<BuilderPayloads.LibraryEntry> entries = List.of();
    private int sentListVersion = -1;

    public LibraryMenu(int containerId, Inventory playerInventory, LibraryBlockEntity library) {
        this(containerId, playerInventory, library, new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case DATA_PROGRESS_IN -> library.getProgressIn();
                    case DATA_PROGRESS_OUT -> library.getProgressOut();
                    case DATA_SELECTED_LOW -> library.getSelected() & SHORT_MASK;
                    case DATA_SELECTED_HIGH -> library.getSelected() >>> SHORT_BITS & SHORT_MASK;
                    default -> library.isLocked() ? 1 : 0;
                };
            }

            @Override
            public void set(int index, int value) {
                // サーバーの値はブロックエンティティから読む。
            }

            @Override
            public int getCount() {
                return DATA_COUNT;
            }
        });
    }

    private LibraryMenu(int containerId, Inventory playerInventory, LibraryBlockEntity library, ContainerData data) {
        super(ModMenus.LIBRARY.get(), containerId);
        this.library = library;
        this.player = playerInventory.player;
        this.data = data;
        addSlot(new SlotItemHandler(library.getInventory(), LibraryBlockEntity.INPUT_SLOT, 153, 61));
        addSlot(outputSlot(LibraryBlockEntity.REGISTERED_SLOT, 109, 61));
        addSlot(new SlotItemHandler(library.getInventory(), LibraryBlockEntity.COPY_SLOT, 109, 79));
        addSlot(outputSlot(LibraryBlockEntity.COPIED_SLOT, 153, 79));
        PlayerInventorySlots.add(this::addSlot, playerInventory, PLAYER_INVENTORY_Y);
        addDataSlots(data);
    }

    private Slot outputSlot(int index, int x, int y) {
        return new SlotItemHandler(library.getInventory(), index, x, y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        };
    }

    public static LibraryMenu createClient(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        if (playerInventory.player.level().getBlockEntity(extraData.readBlockPos()) instanceof LibraryBlockEntity library) {
            return new LibraryMenu(containerId, playerInventory, library, new SimpleContainerData(DATA_COUNT));
        }
        throw new IllegalStateException("Library menu opened for a position without a blueprint library");
    }

    // ---- 画面向け -------------------------------------------------------------

    public List<BuilderPayloads.LibraryEntry> getEntries() {
        return entries;
    }

    /** クライアント: サーバーから届いた一覧。 */
    public void setEntries(List<BuilderPayloads.LibraryEntry> entries) {
        this.entries = List.copyOf(entries);
    }

    public int getProgressIn() {
        return data.get(DATA_PROGRESS_IN);
    }

    public int getProgressOut() {
        return data.get(DATA_PROGRESS_OUT);
    }

    public int getSelected() {
        return (data.get(DATA_SELECTED_HIGH) & SHORT_MASK) << SHORT_BITS | data.get(DATA_SELECTED_LOW) & SHORT_MASK;
    }

    public boolean isLocked() {
        return data.get(DATA_LOCKED) != 0;
    }

    // ---- サーバー ---------------------------------------------------------------

    /** 一覧が変わったら、開いているプレイヤーへ送り直す。 */
    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (player instanceof ServerPlayer serverPlayer && sentListVersion != library.getListVersion()
                && library.getLevel() instanceof ServerLevel serverLevel) {
            sentListVersion = library.getListVersion();
            entries = library.entries(serverLevel, serverPlayer);
            PacketDistributor.sendToPlayer(serverPlayer, new BuilderPayloads.LibraryContents(containerId, entries));
        }
    }

    @Override
    public boolean clickMenuButton(Player clicker, int id) {
        if (!(library.getLevel() instanceof ServerLevel serverLevel)) {
            return false;
        }
        if (id == BUTTON_DELETE) {
            library.deleteSelected(serverLevel, clicker);
        } else if (id == BUTTON_LOCK) {
            library.toggleLocked();
        } else if (id >= BUTTON_SELECT_BASE) {
            library.select(serverLevel, clicker, id - BUTTON_SELECT_BASE);
        } else {
            return false;
        }
        return true;
    }

    @Override
    public boolean stillValid(Player viewer) {
        return Container.stillValidBlockEntity(library, viewer) && library.canOpen(viewer);
    }

    @Override
    public ItemStack quickMoveStack(Player viewer, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        boolean moved = index < MACHINE_SLOTS
                ? moveItemStackTo(stack, MACHINE_SLOTS, PLAYER_END, true)
                : stack.getItem() instanceof BlueprintItem && (moveItemStackTo(stack, 0, 1, false) || moveItemStackTo(stack, 2, 3, false));
        if (!moved) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }
}
