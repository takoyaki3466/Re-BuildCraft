package com.takoy3466.re_buildcraft.builders.blueprint;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * ビルダーが材料を取り出せるインベントリの集まり。元実装: {@code buildcraft.core.SurroundingInventory}
 * (ビルダー自身の材料枠と、隣接する 6 方向のインベントリ)。
 * <p>
 * アドオン (EMC ビルダーなど) が材料の判定・消費を差し替えられるよう、継承して {@link #hasAll} / {@link #useAll} を上書きできる。
 */
public class BuilderMaterials {
    private final List<IItemHandler> handlers;

    private BuilderMaterials(List<IItemHandler> handlers) {
        this.handlers = handlers;
    }

    /** 継承用: {@code base} と同じインベントリの集まりを使う。 */
    protected BuilderMaterials(BuilderMaterials base) {
        this(base.handlers);
    }

    public static BuilderMaterials around(Level level, BlockPos pos, IItemHandler own) {
        List<IItemHandler> handlers = new ArrayList<>();
        handlers.add(own);
        for (Direction direction : Direction.values()) {
            IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos.relative(direction), direction.getOpposite());
            if (handler != null && handler != own) {
                handlers.add(handler);
            }
        }
        return new BuilderMaterials(handlers);
    }

    /** どれでもよいので材料を 1 個取り出す (旧 BptBuilderTemplate: isBuildingMaterial のスロットの先頭から)。 */
    public ItemStack takeAny() {
        for (IItemHandler handler : handlers) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack taken = handler.extractItem(slot, 1, false);
                if (!taken.isEmpty()) {
                    return taken;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    /** 必要なアイテムがすべてそろっているか (旧 checkRequirements)。 */
    public boolean hasAll(List<ItemStack> requirements) {
        for (ItemStack required : requirements) {
            if (count(required) < required.getCount()) {
                return false;
            }
        }
        return true;
    }

    /** 必要なアイテムを取り出して使う (旧 useRequirements)。{@link #hasAll} を確認してから呼ぶ。 */
    public void useAll(List<ItemStack> requirements) {
        for (ItemStack required : requirements) {
            int remaining = required.getCount();
            for (IItemHandler handler : handlers) {
                for (int slot = 0; slot < handler.getSlots() && remaining > 0; slot++) {
                    if (ItemStack.isSameItem(handler.getStackInSlot(slot), required)) {
                        remaining -= handler.extractItem(slot, remaining, false).getCount();
                    }
                }
            }
        }
    }

    private int count(ItemStack required) {
        int total = 0;
        for (IItemHandler handler : handlers) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (ItemStack.isSameItem(stack, required)) {
                    total += handler.extractItem(slot, stack.getCount(), true).getCount();
                }
            }
        }
        return total;
    }
}
