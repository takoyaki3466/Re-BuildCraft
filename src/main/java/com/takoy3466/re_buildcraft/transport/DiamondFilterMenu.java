package com.takoy3466.re_buildcraft.transport;

import com.takoy3466.re_buildcraft.api.menu.PlayerInventorySlots;
import com.takoy3466.re_buildcraft.registry.ModMenus;
import com.takoy3466.re_buildcraft.transport.behavior.DiamondFilterBehavior;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * ダイヤパイプのフィルター設定画面のメニュー。元実装: {@code CraftingDiamondPipe} + {@code GuiDiamondPipe}。
 * フィルター枠は見本なので、クリックするとカーソルのアイテムの写し (1 個) を置き、素手なら消す。
 */
public class DiamondFilterMenu extends AbstractContainerMenu {
    private static final int SLOT_SIZE = 18;
    private static final int FILTER_LEFT = 8;
    private static final int FILTER_TOP = 18;
    private static final int PLAYER_INVENTORY_Y = 140;

    @Nullable
    private final PipeBlockEntity pipe;
    private final Container filters;

    public DiamondFilterMenu(int containerId, Inventory playerInventory, Container filters, @Nullable PipeBlockEntity pipe) {
        super(ModMenus.DIAMOND_PIPE.get(), containerId);
        this.pipe = pipe;
        this.filters = filters;
        for (int row = 0; row < filters.getContainerSize() / DiamondFilterBehavior.SLOTS_PER_SIDE; row++) {
            for (int column = 0; column < DiamondFilterBehavior.SLOTS_PER_SIDE; column++) {
                addSlot(new FilterSlot(filters, column + row * DiamondFilterBehavior.SLOTS_PER_SIDE, FILTER_LEFT + column * SLOT_SIZE, FILTER_TOP + row * SLOT_SIZE));
            }
        }
        PlayerInventorySlots.add(this::addSlot, playerInventory, PLAYER_INVENTORY_Y);
    }

    /**
     * クライアント側。フィルターの中身はメニューのスロット同期で届く。
     */
    public static DiamondFilterMenu createClient(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        extraData.readBlockPos();
        return new DiamondFilterMenu(containerId, playerInventory, new SimpleContainer(DiamondFilterBehavior.FILTER_SLOTS), null);
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < DiamondFilterBehavior.FILTER_SLOTS) {
            ItemStack carried = getCarried();
            filters.setItem(slotId, carried.isEmpty() ? ItemStack.EMPTY : carried.copyWithCount(1));
            if (pipe != null) {
                pipe.setChanged();
            }
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        // 見本の枠へは移動させない。
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return pipe == null || Container.stillValidBlockEntity(pipe, player);
    }

    /**
     * 見本用の枠。直接の出し入れはできない。
     */
    private static final class FilterSlot extends Slot {
        private FilterSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }
    }
}
