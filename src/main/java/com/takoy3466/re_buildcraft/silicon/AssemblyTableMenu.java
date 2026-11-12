package com.takoy3466.re_buildcraft.silicon;

import com.takoy3466.re_buildcraft.api.recipe.AssemblyRecipe;
import com.takoy3466.re_buildcraft.api.menu.PlayerInventorySlots;
import com.takoy3466.re_buildcraft.registry.ModMenus;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * 組立台の GUI メニュー。左に材料 12 スロット、右に作成可能なレシピ (最大 8 件) を表示する。
 * 元実装: {@code buildcraft.silicon.ContainerAssemblyTable} と {@code GuiAssemblyTable} の状態部分。
 * <p>
 * 旧実装はクライアントがタイルエンティティを直接書き換えていたが、ここではレシピ枠のクリックを
 * メニューボタンとしてサーバーへ送り、予約状態は {@link ContainerData} でクライアントへ同期する。
 */
public class AssemblyTableMenu extends AbstractContainerMenu {
    /** 画面に並ぶレシピ枠の数 (2 列 x 4 段)。 */
    public static final int RECIPE_SLOT_COUNT = 8;
    public static final int RECIPE_STATE_NONE = 0;
    public static final int RECIPE_STATE_PLANNED = 1;
    public static final int RECIPE_STATE_ASSEMBLING = 2;

    private static final int INPUT_COLUMNS = 3;
    private static final int INPUT_ROWS = 4;
    private static final int INPUT_LEFT = 8;
    private static final int INPUT_TOP = 36;
    private static final int SLOT_SIZE = 18;
    private static final int PLAYER_INVENTORY_Y = 123;

    // ContainerData は 16 bit ずつしか送れないため、エネルギー量は上位・下位に分けて同期する。
    private static final int DATA_ENERGY_LOW = RECIPE_SLOT_COUNT;
    private static final int DATA_ENERGY_HIGH = RECIPE_SLOT_COUNT + 1;
    private static final int DATA_REQUIRED_LOW = RECIPE_SLOT_COUNT + 2;
    private static final int DATA_REQUIRED_HIGH = RECIPE_SLOT_COUNT + 3;
    private static final int DATA_COUNT = RECIPE_SLOT_COUNT + 4;
    private static final int SHORT_BITS = 16;
    private static final int SHORT_MASK = 0xFFFF;

    private final AssemblyTableBlockEntity table;
    private final ContainerData data;
    /** サーバー側で、直近の同期時点に表示対象だったレシピ。クリックされた枠の解決に使う。 */
    private List<RecipeHolder<AssemblyRecipe>> visibleRecipes = List.of();

    /** サーバー側。 */
    public AssemblyTableMenu(int containerId, Inventory playerInventory, AssemblyTableBlockEntity table) {
        this(containerId, playerInventory, table, null);
    }

    private AssemblyTableMenu(int containerId, Inventory playerInventory, AssemblyTableBlockEntity table, ContainerData clientData) {
        super(ModMenus.ASSEMBLY_TABLE.get(), containerId);
        this.table = table;
        this.data = clientData != null ? clientData : new ServerData();

        for (int row = 0; row < INPUT_ROWS; row++) {
            for (int column = 0; column < INPUT_COLUMNS; column++) {
                addSlot(new SlotItemHandler(table.getInventory(), column + row * INPUT_COLUMNS,
                        INPUT_LEFT + column * SLOT_SIZE, INPUT_TOP + row * SLOT_SIZE));
            }
        }
        PlayerInventorySlots.add(this::addSlot, playerInventory, PLAYER_INVENTORY_Y);
        addDataSlots(data);
    }

    /** クライアント側。 */
    public static AssemblyTableMenu createClient(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        if (playerInventory.player.level().getBlockEntity(extraData.readBlockPos()) instanceof AssemblyTableBlockEntity table) {
            return new AssemblyTableMenu(containerId, playerInventory, table, new SimpleContainerData(DATA_COUNT));
        }
        throw new IllegalStateException("Assembly table menu opened for a position without an assembly table");
    }

    // ---- 画面向けアクセサ ---------------------------------------------------

    /** 現在の材料で作成できるレシピ (クライアントでは同期済みスロット内容から計算する)。 */
    public List<RecipeHolder<AssemblyRecipe>> getPotentialOutputs() {
        return table.getPotentialOutputs();
    }

    /** {@code RECIPE_STATE_*} のいずれか。 */
    public int getRecipeState(int recipeSlot) {
        return data.get(recipeSlot);
    }

    public int getEnergyStored() {
        return combine(data.get(DATA_ENERGY_LOW), data.get(DATA_ENERGY_HIGH));
    }

    public int getRequiredEnergy() {
        return combine(data.get(DATA_REQUIRED_LOW), data.get(DATA_REQUIRED_HIGH));
    }

    private static int combine(int low, int high) {
        return (high & SHORT_MASK) << SHORT_BITS | (low & SHORT_MASK);
    }

    // ---- メニュー操作 -------------------------------------------------------

    @Override
    public void broadcastChanges() {
        visibleRecipes = table.getPotentialOutputs();
        super.broadcastChanges();
    }

    /** レシピ枠のクリック。{@code id} は枠の番号。 */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id < 0 || id >= RECIPE_SLOT_COUNT || id >= visibleRecipes.size()) {
            return false;
        }
        table.togglePlan(visibleRecipes.get(id).id());
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(table, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int tableSlots = AssemblyTableBlockEntity.INVENTORY_SIZE;
        boolean moved = index < tableSlots
                ? moveItemStackTo(stack, tableSlots, slots.size(), true)
                : moveItemStackTo(stack, 0, tableSlots, false);
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

    /** サーバー側の同期元。表示中のレシピ枠ごとの予約状態と、エネルギーの進み具合を返す。 */
    private final class ServerData implements ContainerData {
        @Override
        public int get(int index) {
            if (index < RECIPE_SLOT_COUNT) {
                return recipeState(index);
            }
            int energy = (int) table.getEnergyStored();
            int required = table.getRequiredEnergy();
            return switch (index) {
                case DATA_ENERGY_LOW -> energy & SHORT_MASK;
                case DATA_ENERGY_HIGH -> energy >>> SHORT_BITS;
                case DATA_REQUIRED_LOW -> required & SHORT_MASK;
                case DATA_REQUIRED_HIGH -> required >>> SHORT_BITS;
                default -> 0;
            };
        }

        private int recipeState(int recipeSlot) {
            if (recipeSlot >= visibleRecipes.size()) {
                return RECIPE_STATE_NONE;
            }
            var id = visibleRecipes.get(recipeSlot).id();
            if (table.isAssembling(id)) {
                return RECIPE_STATE_ASSEMBLING;
            }
            return table.isPlanned(id) ? RECIPE_STATE_PLANNED : RECIPE_STATE_NONE;
        }

        @Override
        public void set(int index, int value) {
            // 表示専用。
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    }
}
