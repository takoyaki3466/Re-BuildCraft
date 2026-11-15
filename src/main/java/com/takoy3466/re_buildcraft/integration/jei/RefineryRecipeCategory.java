package com.takoy3466.re_buildcraft.integration.jei;

import com.takoy3466.re_buildcraft.api.recipe.RefineryRecipe;
import com.takoy3466.re_buildcraft.registry.ModBlocks;
import java.util.List;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

/**
 * 精製機のレシピ ({@code re_buildcraft:refinery})。液体だけのレシピなので、材料 1 - 2 種と生成物を液体の枠で表示する。
 * 枠の量は 1 回の精製で消費・生成する量 (mB)。必要な MJ と精製の間隔 (tick) を下に表示する。
 */
final class RefineryRecipeCategory extends AbstractRecipeCategory<RecipeHolder<RefineryRecipe>> {
    private static final int SLOT = 18;
    private static final int FLUID_SIZE = 16;
    private static final int SECOND_X = SLOT;
    private static final int ARROW_X = SLOT * 2 + 6;
    private static final int OUTPUT_X = ARROW_X + 30;
    private static final int TEXT_Y = SLOT + 3;
    private static final int WIDTH = OUTPUT_X + SLOT + 40;
    private static final int HEIGHT = TEXT_Y + 10;
    private static final int TEXT_COLOR = 0xFF808080;

    RefineryRecipeCategory(IGuiHelper guiHelper) {
        super(BuildCraftJeiPlugin.REFINERY, ModBlocks.REFINERY.get().getName(),
                guiHelper.createDrawableItemLike(ModBlocks.REFINERY.get()), WIDTH, HEIGHT);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<RefineryRecipe> holder, IFocusGroup focuses) {
        RefineryRecipe recipe = holder.value();
        fluidSlot(builder.addInputSlot(1, 1), recipe.first());
        recipe.second().ifPresent(second -> fluidSlot(builder.addInputSlot(SECOND_X + 1, 1), second));
        builder.addOutputSlot(OUTPUT_X + 1, 1)
                .setOutputSlotBackground()
                .setFluidRenderer(Math.max(1, recipe.result().getAmount()), false, FLUID_SIZE, FLUID_SIZE)
                .addIngredients(NeoForgeTypes.FLUID_STACK, List.of(recipe.result()));
    }

    /** 材料の液体の枠。候補の液体それぞれを必要量 (mB) 付きで並べる。 */
    private static void fluidSlot(IRecipeSlotBuilder slot, SizedFluidIngredient ingredient) {
        List<FluidStack> fluids = List.of(ingredient.getFluids());
        slot.setStandardSlotBackground()
                .setFluidRenderer(Math.max(1, ingredient.amount()), false, FLUID_SIZE, FLUID_SIZE)
                .addIngredients(NeoForgeTypes.FLUID_STACK, fluids);
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, RecipeHolder<RefineryRecipe> holder, IFocusGroup focuses) {
        builder.addRecipeArrow().setPosition(ARROW_X, 0);
    }

    @Override
    public void draw(RecipeHolder<RefineryRecipe> holder, IRecipeSlotsView recipeSlotsView, GuiGraphics graphics, double mouseX, double mouseY) {
        RefineryRecipe recipe = holder.value();
        Component text = Component.translatable("jei.re_buildcraft.refinery.cost", recipe.energy(), recipe.delay());
        graphics.drawString(Minecraft.getInstance().font, text, 0, TEXT_Y, TEXT_COLOR, false);
    }
}
