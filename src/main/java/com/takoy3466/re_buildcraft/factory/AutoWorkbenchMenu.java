package com.takoy3466.re_buildcraft.factory;

import com.takoy3466.re_buildcraft.api.menu.PlayerInventorySlots;
import com.takoy3466.re_buildcraft.registry.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * 自動作業台の GUI メニュー。元実装: {@code buildcraft.factory.ContainerAutoWorkbench}。
 * 配置はバニラの作業台と同じで、完成品枠から取ると 3x3 の各スロットから 1 個ずつ消費する。
 */
public class AutoWorkbenchMenu extends AbstractContainerMenu {
    private static final int RESULT_X = 124;
    private static final int RESULT_Y = 35;
    private static final int GRID_LEFT = 30;
    private static final int GRID_TOP = 17;
    private static final int SLOT_SIZE = 18;
    private static final int PLAYER_INVENTORY_Y = 84;
    private static final int RESULT_SLOT = 0;
    private static final int GRID_START = 1;
    private static final int PLAYER_START = GRID_START + AutoWorkbenchBlockEntity.GRID_SIZE;
    private static final int PLAYER_END = PLAYER_START + Inventory.INVENTORY_SIZE;

    private final AutoWorkbenchBlockEntity workbench;
    private final ResultContainer result = new ResultContainer();

    public AutoWorkbenchMenu(int containerId, Inventory playerInventory, AutoWorkbenchBlockEntity workbench) {
        super(ModMenus.AUTO_WORKBENCH.get(), containerId);
        this.workbench = workbench;
        addSlot(new ResultSlot(result, RESULT_X, RESULT_Y));
        for (int row = 0; row < AutoWorkbenchBlockEntity.GRID_WIDTH; row++) {
            for (int column = 0; column < AutoWorkbenchBlockEntity.GRID_WIDTH; column++) {
                addSlot(new SlotItemHandler(workbench.getGrid(), column + row * AutoWorkbenchBlockEntity.GRID_WIDTH,
                        GRID_LEFT + column * SLOT_SIZE, GRID_TOP + row * SLOT_SIZE));
            }
        }
        PlayerInventorySlots.add(this::addSlot, playerInventory, PLAYER_INVENTORY_Y);
        refreshResult();
    }

    public static AutoWorkbenchMenu createClient(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        if (playerInventory.player.level().getBlockEntity(extraData.readBlockPos()) instanceof AutoWorkbenchBlockEntity workbench) {
            return new AutoWorkbenchMenu(containerId, playerInventory, workbench);
        }
        throw new IllegalStateException("Automatic crafting table menu opened for a position without one");
    }

    /** 3x3 の並びから完成品枠の表示を作り直す (旧 updateCraftingResults)。 */
    private void refreshResult() {
        result.setItem(0, workbench.previewResult());
    }

    @Override
    public void broadcastChanges() {
        refreshResult();
        super.broadcastChanges();
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(workbench, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        if (index == RESULT_SLOT) {
            // 1 回分作ってプレイヤーのインベントリへ入れる。入り切らない分は足元へ落とす。
            ItemStack crafted = workbench.craft(true, true);
            if (crafted.isEmpty()) {
                return ItemStack.EMPTY;
            }
            crafted.onCraftedBy(player.level(), player, crafted.getCount());
            ItemStack copy = crafted.copy();
            if (!moveItemStackTo(crafted, PLAYER_START, PLAYER_END, true)) {
                player.drop(crafted, false);
            } else if (!crafted.isEmpty()) {
                player.drop(crafted, false);
            }
            refreshResult();
            return copy;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        boolean moved = index < PLAYER_START
                ? moveItemStackTo(stack, PLAYER_START, PLAYER_END, true)
                : moveItemStackTo(stack, GRID_START, PLAYER_START, false);
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

    /** 完成品枠。置けず、取ると材料を消費する (旧 SlotAutoCrafting)。 */
    private final class ResultSlot extends Slot {
        ResultSlot(Container container, int x, int y) {
            super(container, 0, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public void onTake(Player player, ItemStack stack) {
            if (!player.level().isClientSide) {
                workbench.craft(true, true);
                stack.onCraftedBy(player.level(), player, stack.getCount());
            }
            refreshResult();
            super.onTake(player, stack);
        }
    }
}
