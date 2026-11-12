package com.takoy3466.re_buildcraft.energy;

import com.takoy3466.re_buildcraft.api.engine.EngineBlockEntity;
import com.takoy3466.re_buildcraft.api.menu.PlayerInventorySlots;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * 燃料スロットを 1 つ持つエンジンの GUI メニュー (石エンジン・鉄エンジン共用)。
 * 元実装: {@code buildcraft.energy.ContainerEngineRoot} / {@code ContainerEngine}。
 */
public class EngineMenu extends AbstractContainerMenu {
    private static final int FUEL_SLOT_INDEX = 0;
    private static final int FUEL_SLOT_Y = 41;
    private static final int PLAYER_INVENTORY_Y = 84;
    private static final int ENGINE_SLOT_COUNT = 1;

    private final EngineBlockEntity engine;
    private final ContainerData data;

    public EngineMenu(MenuType<?> type, int containerId, Inventory playerInventory, EngineBlockEntity engine,
                      ContainerData data, int fuelSlotX) {
        super(type, containerId);
        this.engine = engine;
        this.data = data;
        addSlot(new SlotItemHandler(engine.getInventory(), FUEL_SLOT_INDEX, fuelSlotX, FUEL_SLOT_Y));
        PlayerInventorySlots.add(this::addSlot, playerInventory, PLAYER_INVENTORY_Y);
        addDataSlots(data);
    }

    /** クライアント側: サーバーが送ったブロック位置からエンジンを取得してメニューを組み立てる。 */
    public static EngineMenu createClient(MenuType<?> type, int containerId, Inventory playerInventory,
                                          FriendlyByteBuf extraData, int dataCount, int fuelSlotX) {
        if (playerInventory.player.level().getBlockEntity(extraData.readBlockPos()) instanceof EngineBlockEntity engine) {
            return new EngineMenu(type, containerId, playerInventory, engine, new SimpleContainerData(dataCount), fuelSlotX);
        }
        throw new IllegalStateException("Engine menu opened for a position without an engine block entity");
    }

    public int getData(int index) {
        return data.get(index);
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(engine, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        boolean moved = index < ENGINE_SLOT_COUNT
                ? moveItemStackTo(stack, ENGINE_SLOT_COUNT, slots.size(), true)
                : moveItemStackTo(stack, 0, ENGINE_SLOT_COUNT, false);
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
