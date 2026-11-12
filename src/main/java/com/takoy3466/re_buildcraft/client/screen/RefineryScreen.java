package com.takoy3466.re_buildcraft.client.screen;

import com.takoy3466.re_buildcraft.ReBuildcraft;
import com.takoy3466.re_buildcraft.api.recipe.RefineryRecipe;
import com.takoy3466.re_buildcraft.api.recipe.RefineryRecipeInput;
import com.takoy3466.re_buildcraft.factory.RefineryBlockEntity;
import com.takoy3466.re_buildcraft.factory.RefineryMenu;
import com.takoy3466.re_buildcraft.registry.ModRecipes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;

/**
 * 精製機のフィルター設定 GUI。元実装: {@code buildcraft.factory.GuiRefinery}。
 * <p>
 * 左右の材料枠を液体入りの容器を持ってクリックするとその液体がフィルターになり、空の手でクリックすると解除される。
 * 中央にはフィルターの組み合わせで作れる液体を表示する。液体は対応するバケツのアイコンで表す。
 */
public class RefineryScreen extends AbstractContainerScreen<RefineryMenu> {
    private static final ResourceLocation TEXTURE = ReBuildcraft.id("textures/gui/refinery.png");
    private static final int IMAGE_WIDTH = 175;
    private static final int IMAGE_HEIGHT = 207;
    private static final int[] FILTER_X = {38, 126};
    private static final int RESULT_X = 82;
    private static final int SLOT_Y = 54;
    private static final int ITEM_SIZE = 16;
    private static final int TITLE_X = 28;
    private static final int TITLE_Y = 6;
    private static final int ARROW_Y = 59;
    private static final int LEFT_ARROW_X = 63;
    private static final int RIGHT_ARROW_X = 106;
    private static final int INVENTORY_LABEL_OFFSET = 94;
    private static final int TEXT_COLOR = 0x404040;
    private static final Component RIGHT_ARROW = Component.literal("->");
    private static final Component LEFT_ARROW = Component.literal("<-");

    public RefineryScreen(RefineryMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = IMAGE_WIDTH;
        imageHeight = IMAGE_HEIGHT;
        titleLabelX = TITLE_X;
        titleLabelY = TITLE_Y;
        inventoryLabelY = IMAGE_HEIGHT - INVENTORY_LABEL_OFFSET;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        for (int i = 0; i < RefineryBlockEntity.FILTER_COUNT; i++) {
            renderFluid(guiGraphics, menu.getFilter(i), FILTER_X[i]);
        }
        renderFluid(guiGraphics, previewResult(), RESULT_X);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        super.renderLabels(guiGraphics, mouseX, mouseY);
        guiGraphics.drawString(font, RIGHT_ARROW, LEFT_ARROW_X, ARROW_Y, TEXT_COLOR, false);
        guiGraphics.drawString(font, LEFT_ARROW, RIGHT_ARROW_X, ARROW_Y, TEXT_COLOR, false);
    }

    private void renderFluid(GuiGraphics guiGraphics, Fluid fluid, int x) {
        if (fluid == Fluids.EMPTY) {
            return;
        }
        ItemStack icon = new ItemStack(fluid.getBucket());
        if (!icon.isEmpty()) {
            guiGraphics.renderItem(icon, leftPos + x, topPos + SLOT_Y);
        }
    }

    /** 旧実装と同じく、各フィルターの液体が 1000 mB ずつある場合のレシピの結果を表示する。 */
    private Fluid previewResult() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return Fluids.EMPTY;
        }
        RefineryRecipeInput input = new RefineryRecipeInput(bucketOf(menu.getFilter(0)), bucketOf(menu.getFilter(1)));
        return minecraft.level.getRecipeManager().getRecipeFor(ModRecipes.REFINERY_TYPE.get(), input, minecraft.level)
                .map(RecipeHolder::value).map(RefineryRecipe::result).map(FluidStack::getFluid).orElse(Fluids.EMPTY);
    }

    private static FluidStack bucketOf(Fluid fluid) {
        return fluid == Fluids.EMPTY ? FluidStack.EMPTY : new FluidStack(fluid, FluidType.BUCKET_VOLUME);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (int i = 0; i < RefineryBlockEntity.FILTER_COUNT; i++) {
            if (isHovering(FILTER_X[i], SLOT_Y, ITEM_SIZE, ITEM_SIZE, mouseX, mouseY) && minecraft != null && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, i);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
