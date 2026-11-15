package com.takoy3466.re_buildcraft.builders;

import com.takoy3466.re_buildcraft.api.menu.PlayerInventorySlots;
import com.takoy3466.re_buildcraft.builders.blueprint.BlueprintItem;
import com.takoy3466.re_buildcraft.registry.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.SlotItemHandler;

/** 設計台の GUI メニュー。元実装: {@code CraftingTemplateRoot} / {@code CraftingTemplate}。 */
public class ArchitectMenu extends AbstractContainerMenu {
    private static final int INPUT_X = 55;
    private static final int OUTPUT_X = 114;
    private static final int SLOT_Y = 35;
    private static final int PLAYER_INVENTORY_Y = 84;
    private static final int MACHINE_SLOTS = 2;
    private static final int PLAYER_END = MACHINE_SLOTS + Inventory.INVENTORY_SIZE;

    private final ArchitectBlockEntity architect;
    private final ContainerData data;

    public ArchitectMenu(int containerId, Inventory playerInventory, ArchitectBlockEntity architect) {
        this(containerId, playerInventory, architect, new ContainerData() {
            @Override
            public int get(int index) {
                return architect.getComputingTime();
            }

            @Override
            public void set(int index, int value) {
                // サーバーの値はブロックエンティティから読む。
            }

            @Override
            public int getCount() {
                return 1;
            }
        });
    }

    private ArchitectMenu(int containerId, Inventory playerInventory, ArchitectBlockEntity architect, ContainerData data) {
        super(ModMenus.ARCHITECT.get(), containerId);
        this.architect = architect;
        this.data = data;
        addSlot(new SlotItemHandler(architect.getInventory(), ArchitectBlockEntity.INPUT_SLOT, INPUT_X, SLOT_Y));
        addSlot(new SlotItemHandler(architect.getInventory(), ArchitectBlockEntity.OUTPUT_SLOT, OUTPUT_X, SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });
        PlayerInventorySlots.add(this::addSlot, playerInventory, PLAYER_INVENTORY_Y);
        addDataSlots(data);
    }

    public static ArchitectMenu createClient(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        if (playerInventory.player.level().getBlockEntity(extraData.readBlockPos()) instanceof ArchitectBlockEntity architect) {
            return new ArchitectMenu(containerId, playerInventory, architect, new SimpleContainerData(1));
        }
        throw new IllegalStateException("Architect menu opened for a position without an architect table");
    }

    public ArchitectBlockEntity getArchitect() {
        return architect;
    }

    /** 記録の進み具合 (0 - {@link ArchitectBlockEntity#COMPUTE_TICKS})。 */
    public int getComputingTime() {
        return data.get(0);
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(architect, player);
    }

    /** 旧 CraftingTemplate.transferStackInSlot: テンプレート/設計図は入力枠へ。 */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        boolean moved = index < MACHINE_SLOTS
                ? moveItemStackTo(stack, MACHINE_SLOTS, PLAYER_END, true)
                : stack.getItem() instanceof BlueprintItem && moveItemStackTo(stack, 0, 1, false);
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
