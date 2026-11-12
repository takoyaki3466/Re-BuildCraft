package com.takoy3466.re_buildcraft.api.recipe;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * 精製機の 2 つの材料槽の中身。アイテムは扱わない。
 *
 * @param first  1 つ目の槽
 * @param second 2 つ目の槽
 */
public record RefineryRecipeInput(FluidStack first, FluidStack second) implements RecipeInput {
    @Override
    public ItemStack getItem(int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public int size() {
        return 0;
    }

    /** 液体の材料のみなので、空かどうかは槽の中身で判断する。 */
    @Override
    public boolean isEmpty() {
        return first.isEmpty() && second.isEmpty();
    }
}
