package com.takoy3466.re_buildcraft.api.recipe;

import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

/**
 * 組立台の材料スロットの内容。
 *
 * @param items 各スロットのアイテム (空スロットは {@link ItemStack#EMPTY})
 */
public record AssemblyRecipeInput(List<ItemStack> items) implements RecipeInput {

    @Override
    public ItemStack getItem(int index) {
        return items.get(index);
    }

    @Override
    public int size() {
        return items.size();
    }

    /** 指定した材料に一致するアイテムの総数 (全スロット合算)。 */
    public int count(SizedIngredient ingredient) {
        int total = 0;
        for (ItemStack stack : items) {
            if (ingredient.ingredient().test(stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }
}
