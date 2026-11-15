package com.takoy3466.re_buildcraft.builders;

import com.takoy3466.re_buildcraft.api.menu.PlayerInventorySlots;
import com.takoy3466.re_buildcraft.builders.blueprint.BlueprintItem;
import com.takoy3466.re_buildcraft.registry.ModMenus;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * ビルダーの GUI メニュー。元実装: {@code CraftingBuilder} と {@code GuiBuilder} の必要アイテム欄。
 * 設計図を建てている間は、右側に残りに必要なアイテム (多い順に 28 種まで) を表示専用の枠で出す。
 */
public class BuilderMenu extends AbstractContainerMenu {
    public static final int NEEDED_COLUMNS = 4;
    public static final int NEEDED_ROWS = 7;
    private static final int NEEDED_SLOTS = NEEDED_COLUMNS * NEEDED_ROWS;
    private static final int NEEDED_LEFT = 179;
    private static final int NEEDED_TOP = 18;
    private static final int BLUEPRINT_X = 80;
    private static final int BLUEPRINT_Y = 27;
    private static final int MATERIAL_LEFT = 8;
    private static final int MATERIAL_TOP = 72;
    private static final int SLOT_SIZE = 18;
    private static final int COLUMNS = 9;
    private static final int PLAYER_INVENTORY_Y = 140;
    private static final int MACHINE_SLOTS = BuilderBlockEntity.SLOT_COUNT;
    private static final int PLAYER_END = MACHINE_SLOTS + Inventory.INVENTORY_SIZE;

    private final BuilderBlockEntity builder;
    private final SimpleContainer needed = new SimpleContainer(NEEDED_SLOTS);

    public BuilderMenu(int containerId, Inventory playerInventory, BuilderBlockEntity builder) {
        this(ModMenus.BUILDER.get(), containerId, playerInventory, builder);
    }

    /** 継承用 (アドオンのビルダーが自分のメニュー型で使う)。スロットの並びは同じ。 */
    protected BuilderMenu(MenuType<?> type, int containerId, Inventory playerInventory, BuilderBlockEntity builder) {
        super(type, containerId);
        this.builder = builder;
        addSlot(new SlotItemHandler(builder.getInventory(), BuilderBlockEntity.BLUEPRINT_SLOT, BLUEPRINT_X, BLUEPRINT_Y));
        for (int row = 0; row < (MACHINE_SLOTS - 1) / COLUMNS; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                addSlot(new SlotItemHandler(builder.getInventory(), 1 + column + row * COLUMNS,
                        MATERIAL_LEFT + column * SLOT_SIZE, MATERIAL_TOP + row * SLOT_SIZE));
            }
        }
        PlayerInventorySlots.add(this::addSlot, playerInventory, PLAYER_INVENTORY_Y);
        for (int row = 0; row < NEEDED_ROWS; row++) {
            for (int column = 0; column < NEEDED_COLUMNS; column++) {
                addSlot(new NeededSlot(column + row * NEEDED_COLUMNS, NEEDED_LEFT + column * SLOT_SIZE, NEEDED_TOP + row * SLOT_SIZE));
            }
        }
    }

    public static BuilderMenu createClient(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        if (playerInventory.player.level().getBlockEntity(extraData.readBlockPos()) instanceof BuilderBlockEntity builder) {
            return new BuilderMenu(containerId, playerInventory, builder);
        }
        throw new IllegalStateException("Builder menu opened for a position without a builder");
    }

    public BuilderBlockEntity getBuilder() {
        return builder;
    }

    /** サーバー: 必要アイテム欄を作り直してから同期する。 */
    @Override
    public void broadcastChanges() {
        if (builder.getLevel() != null && !builder.getLevel().isClientSide) {
            List<ItemStack> items = builder.neededItems();
            for (int i = 0; i < NEEDED_SLOTS; i++) {
                needed.setItem(i, i < items.size() ? items.get(i) : ItemStack.EMPTY);
            }
        }
        super.broadcastChanges();
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(builder, player);
    }

    /** 旧 CraftingBuilder.transferStackInSlot: テンプレート/設計図は先頭の枠、その他は材料枠へ。 */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem() || index >= PLAYER_END) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        boolean moved = index < MACHINE_SLOTS
                ? moveItemStackTo(stack, MACHINE_SLOTS, PLAYER_END, true)
                : moveItemStackTo(stack, stack.getItem() instanceof BlueprintItem ? 0 : 1, MACHINE_SLOTS, false);
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

    /** 表示専用の必要アイテムの枠。設計図を建てているときだけ見える。 */
    private final class NeededSlot extends Slot {
        NeededSlot(int index, int x, int y) {
            super(needed, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        @Override
        public boolean isActive() {
            return builder.isBuildingBlueprint();
        }
    }
}
