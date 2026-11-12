package com.takoy3466.re_buildcraft.transport.behavior;

import com.takoy3466.re_buildcraft.transport.DiamondFilterMenu;
import com.takoy3466.re_buildcraft.transport.PipeBlockEntity;
import com.takoy3466.re_buildcraft.transport.TravelingItem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;

/**
 * ダイヤの輸送パイプ。面ごとに 9 個のフィルターを持ち、一致するアイテムはその面へ、
 * どのフィルターにも一致しないアイテムはフィルターの無い面へ送る。元実装: {@code PipeItemsDiamond} + {@code PipeLogicDiamond}。
 * フィルターは見本であり、パイプを壊しても落ちない (旧 doDrop = false)。
 */
public class DiamondFilterBehavior implements PipeBehavior {
    public static final int SLOTS_PER_SIDE = 9;
    public static final int FILTER_SLOTS = SLOTS_PER_SIDE * Direction.values().length;
    private static final String TITLE_KEY = "container.re_buildcraft.diamond_pipe";

    private final SimpleContainer filters = new SimpleContainer(FILTER_SLOTS);

    @Override
    public List<Direction> filterMovements(PipeBlockEntity pipe, List<Direction> movements, TravelingItem item) {
        List<Direction> filtered = new ArrayList<>();
        List<Direction> unfiltered = new ArrayList<>();
        for (Direction direction : movements) {
            boolean hasFilter = false;
            for (int slot = 0; slot < SLOTS_PER_SIDE; slot++) {
                ItemStack filter = filters.getItem(direction.get3DDataValue() * SLOTS_PER_SIDE + slot);
                if (filter.isEmpty()) {
                    continue;
                }
                hasFilter = true;
                if (matches(filter, item.stack())) {
                    filtered.add(direction);
                    break;
                }
            }
            if (!hasFilter) {
                unfiltered.add(direction);
            }
        }
        return filtered.isEmpty() ? unfiltered : filtered;
    }

    /** 耐久値を持つアイテムは種類だけ、それ以外は成分まで一致を見る (旧: 損傷可能ならダメージ値を無視)。 */
    private static boolean matches(ItemStack filter, ItemStack stack) {
        return stack.isDamageableItem() ? ItemStack.isSameItem(filter, stack) : ItemStack.isSameItemSameComponents(filter, stack);
    }

    @Override
    public MenuProvider menuProvider(PipeBlockEntity pipe) {
        return new SimpleMenuProvider((containerId, playerInventory, player) ->
                new DiamondFilterMenu(containerId, playerInventory, filters, pipe), Component.translatable(TITLE_KEY));
    }

    @Override
    public void save(CompoundTag tag, HolderLookup.Provider registries) {
        ContainerHelper.saveAllItems(tag, filters.getItems(), registries);
    }

    @Override
    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        filters.clearContent();
        ContainerHelper.loadAllItems(tag, filters.getItems(), registries);
    }
}
