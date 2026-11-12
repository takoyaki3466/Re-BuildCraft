package com.takoy3466.re_buildcraft.builders;

import com.takoy3466.re_buildcraft.api.menu.PlayerInventorySlots;
import com.takoy3466.re_buildcraft.registry.ModItems;
import com.takoy3466.re_buildcraft.registry.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * フィラーの GUI メニュー。元実装: {@code buildcraft.builders.CraftingFiller} と {@code SlotFillerMode}。
 * <ul>
 *   <li>作業台枠 3x3 (パターン指定) と材料枠 27。</li>
 *   <li>右の枠に現在のパターンのモジュールが表示され、取ると作業台枠から 1 個ずつ消費してモジュールになる。
 *       表示内容はサーバーが作業台枠から決めるため、クライアントの申告は使わない。</li>
 * </ul>
 */
public class FillerMenu extends AbstractContainerMenu {
    private static final int GRID_LEFT = 31;
    private static final int GRID_TOP = 16;
    private static final int RESOURCE_LEFT = 8;
    private static final int RESOURCE_TOP = 85;
    private static final int SLOT_SIZE = 18;
    private static final int COLUMNS = 9;
    private static final int GRID_COLUMNS = 3;
    private static final int PLAYER_INVENTORY_Y = 153;
    private static final int MODULE_X = 125;
    private static final int MODULE_Y = 34;
    /** モジュール枠を空けるときに優先して入れる作業台枠の中央 (旧 transferStackInSlot の 4 → 0-3 → 5-8)。 */
    private static final int GRID_CENTER = 4;
    private static final int PLAYER_START = FillerBlockEntity.SLOT_COUNT;
    private static final int PLAYER_END = PLAYER_START + Inventory.INVENTORY_SIZE;
    private static final int MODULE_SLOT = PLAYER_END;

    private final FillerBlockEntity filler;
    private final ResultContainer module = new ResultContainer();

    public FillerMenu(int containerId, Inventory playerInventory, FillerBlockEntity filler) {
        super(ModMenus.FILLER.get(), containerId);
        this.filler = filler;
        for (int row = 0; row < GRID_COLUMNS; row++) {
            for (int column = 0; column < GRID_COLUMNS; column++) {
                addSlot(new SlotItemHandler(filler.getInventory(), column + row * GRID_COLUMNS,
                        GRID_LEFT + column * SLOT_SIZE, GRID_TOP + row * SLOT_SIZE));
            }
        }
        for (int row = 0; row < FillerBlockEntity.RESOURCE_SLOTS / COLUMNS; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                addSlot(new SlotItemHandler(filler.getInventory(), FillerBlockEntity.GRID_SLOTS + column + row * COLUMNS,
                        RESOURCE_LEFT + column * SLOT_SIZE, RESOURCE_TOP + row * SLOT_SIZE));
            }
        }
        PlayerInventorySlots.add(this::addSlot, playerInventory, PLAYER_INVENTORY_Y);
        addSlot(new ModuleSlot(module, MODULE_X, MODULE_Y));
        refreshModule();
    }

    public static FillerMenu createClient(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        if (playerInventory.player.level().getBlockEntity(extraData.readBlockPos()) instanceof FillerBlockEntity filler) {
            return new FillerMenu(containerId, playerInventory, filler);
        }
        throw new IllegalStateException("Filler menu opened for a position without a filler");
    }

    public FillerBlockEntity getFiller() {
        return filler;
    }

    private void refreshModule() {
        module.setItem(0, filler.moduleForCurrentPattern());
    }

    @Override
    public void broadcastChanges() {
        refreshModule();
        super.broadcastChanges();
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(filler, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index == MODULE_SLOT) {
            if (!moveItemStackTo(stack, PLAYER_START, PLAYER_END, false)) {
                return ItemStack.EMPTY;
            }
            slot.onTake(player, original);
            return ItemStack.EMPTY;
        }
        boolean moved;
        if (index < PLAYER_START) {
            moved = moveItemStackTo(stack, PLAYER_START, PLAYER_END, false);
        } else if (ModItems.isFillerModule(stack)) {
            moved = moveItemStackTo(stack, GRID_CENTER, GRID_CENTER + 1, false)
                    || moveItemStackTo(stack, 0, GRID_CENTER, false)
                    || moveItemStackTo(stack, GRID_CENTER + 1, FillerBlockEntity.GRID_SLOTS, false)
                    || moveItemStackTo(stack, FillerBlockEntity.GRID_SLOTS, FillerBlockEntity.SLOT_COUNT, false);
        } else {
            moved = moveItemStackTo(stack, FillerBlockEntity.GRID_SLOTS, FillerBlockEntity.SLOT_COUNT, false);
        }
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

    /** モジュール枠。置けず、取ると作業台枠を消費する (旧 SlotFillerMode)。 */
    private final class ModuleSlot extends Slot {
        ModuleSlot(Container container, int x, int y) {
            super(container, 0, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public void onTake(Player player, ItemStack stack) {
            if (!player.level().isClientSide) {
                filler.consumeGridForModule(player);
            }
            refreshModule();
            super.onTake(player, stack);
        }
    }
}
