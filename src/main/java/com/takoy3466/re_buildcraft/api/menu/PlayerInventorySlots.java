package com.takoy3466.re_buildcraft.api.menu;

import java.util.function.Consumer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/** GUI メニューへプレイヤーインベントリ (3 段 + ホットバー) のスロットを並べる共通処理。 */
public final class PlayerInventorySlots {
    private static final int SLOT_SIZE = 18;
    private static final int LEFT_MARGIN = 8;
    private static final int COLUMNS = 9;
    private static final int MAIN_ROWS = 3;
    /** メインインベントリ最上段からホットバーまでの縦方向の距離。 */
    private static final int HOTBAR_OFFSET_Y = 58;

    private PlayerInventorySlots() {
    }

    /**
     * @param slotAdder    {@code AbstractContainerMenu#addSlot} への参照
     * @param inventoryTop メインインベントリ最上段の y 座標
     */
    public static void add(Consumer<Slot> slotAdder, Inventory playerInventory, int inventoryTop) {
        for (int row = 0; row < MAIN_ROWS; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                slotAdder.accept(new Slot(playerInventory, column + row * COLUMNS + COLUMNS,
                        LEFT_MARGIN + column * SLOT_SIZE, inventoryTop + row * SLOT_SIZE));
            }
        }
        for (int column = 0; column < COLUMNS; column++) {
            slotAdder.accept(new Slot(playerInventory, column, LEFT_MARGIN + column * SLOT_SIZE, inventoryTop + HOTBAR_OFFSET_Y));
        }
    }
}
