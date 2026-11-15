package com.takoy3466.re_buildcraft.integration.jei;

import com.takoy3466.re_buildcraft.api.recipe.AssemblyRecipe;
import com.takoy3466.re_buildcraft.registry.ModBlocks;
import com.takoy3466.re_buildcraft.silicon.AssemblyTableBlockEntity;
import java.util.List;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

/**
 * 組立台のレシピ ({@code re_buildcraft:assembly})。
 * <p>
 * 材料は組立台の材料枠と同じ 12 マス (4 列 x 3 行) に、必要個数を付けて並べる。組立台は同じアイテムが複数スロットに分かれていても
 * 合算して照合するため、表示上の並び順に意味は無い。完成に必要なレーザーのエネルギー (MJ) を下に表示する。
 */
final class AssemblyRecipeCategory extends AbstractRecipeCategory<RecipeHolder<AssemblyRecipe>> {
    private static final int SLOT = 18;
    private static final int COLUMNS = 4;
    private static final int ROWS = AssemblyTableBlockEntity.INVENTORY_SIZE / COLUMNS;
    private static final int ARROW_X = COLUMNS * SLOT + 6;
    private static final int ARROW_Y = SLOT - 1;
    private static final int OUTPUT_X = ARROW_X + 30;
    private static final int OUTPUT_Y = SLOT;
    private static final int TEXT_Y = ROWS * SLOT + 3;
    private static final int WIDTH = OUTPUT_X + SLOT;
    private static final int HEIGHT = TEXT_Y + 10;
    private static final int TEXT_COLOR = 0xFF808080;

    AssemblyRecipeCategory(IGuiHelper guiHelper) {
        super(BuildCraftJeiPlugin.ASSEMBLY, ModBlocks.ASSEMBLY_TABLE.get().getName(),
                guiHelper.createDrawableItemLike(ModBlocks.ASSEMBLY_TABLE.get()), WIDTH, HEIGHT);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<AssemblyRecipe> holder, IFocusGroup focuses) {
        AssemblyRecipe recipe = holder.value();
        List<SizedIngredient> ingredients = recipe.ingredients();
        for (int index = 0; index < ingredients.size() && index < AssemblyTableBlockEntity.INVENTORY_SIZE; index++) {
            builder.addInputSlot(index % COLUMNS * SLOT + 1, index / COLUMNS * SLOT + 1)
                    .setStandardSlotBackground()
                    .addItemStacks(List.of(ingredients.get(index).getItems()));
        }
        builder.addOutputSlot(OUTPUT_X + 1, OUTPUT_Y + 1)
                .setOutputSlotBackground()
                .addItemStack(recipe.result());
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, RecipeHolder<AssemblyRecipe> holder, IFocusGroup focuses) {
        builder.addRecipeArrow().setPosition(ARROW_X, ARROW_Y);
    }

    @Override
    public void draw(RecipeHolder<AssemblyRecipe> holder, IRecipeSlotsView recipeSlotsView, GuiGraphics graphics, double mouseX, double mouseY) {
        Component text = Component.translatable("jei.re_buildcraft.assembly.energy", holder.value().energy());
        graphics.drawString(Minecraft.getInstance().font, text, 0, TEXT_Y, TEXT_COLOR, false);
    }
}
