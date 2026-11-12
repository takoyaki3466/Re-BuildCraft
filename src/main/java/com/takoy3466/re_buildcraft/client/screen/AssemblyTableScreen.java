package com.takoy3466.re_buildcraft.client.screen;

import com.takoy3466.re_buildcraft.ReBuildcraft;
import com.takoy3466.re_buildcraft.api.recipe.AssemblyRecipe;
import com.takoy3466.re_buildcraft.silicon.AssemblyTableMenu;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jetbrains.annotations.Nullable;

/**
 * 組立台の GUI。元実装: {@code buildcraft.silicon.GuiAssemblyTable}。
 * 右側のレシピ枠をクリックすると、そのレシピの予約・取り消しをサーバーへ送る。
 */
public class AssemblyTableScreen extends AbstractContainerScreen<AssemblyTableMenu> {
    private static final ResourceLocation TEXTURE = ReBuildcraft.id("textures/gui/assembly_table.png");
    private static final int IMAGE_WIDTH = 175;
    private static final int IMAGE_HEIGHT = 207;
    private static final int RECIPE_LEFT = 134;
    private static final int RECIPE_TOP = 36;
    private static final int RECIPE_ROWS = 4;
    private static final int SLOT_SIZE = 18;
    private static final int ITEM_SIZE = 16;
    private static final int PLANNED_U = 177;
    private static final int ASSEMBLING_U = 196;
    private static final int MARKER_V = 1;
    private static final int PROGRESS_X = 95;
    private static final int PROGRESS_U = 176;
    private static final int PROGRESS_V = 18;
    private static final int PROGRESS_WIDTH = 4;
    private static final int PROGRESS_HEIGHT = 70;
    private static final int TITLE_X = 65;
    private static final int TITLE_Y = 10;
    private static final int ENERGY_X = 59;
    private static final int ENERGY_Y = 20;
    private static final int INVENTORY_LABEL_OFFSET = 97;
    private static final int TEXT_COLOR = 0x404040;

    public AssemblyTableScreen(AssemblyTableMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = IMAGE_WIDTH;
        imageHeight = IMAGE_HEIGHT;
        titleLabelX = TITLE_X;
        titleLabelY = TITLE_Y;
        inventoryLabelY = IMAGE_HEIGHT - INVENTORY_LABEL_OFFSET;
    }

    private static int recipeSlotX(int recipeSlot) {
        return RECIPE_LEFT + SLOT_SIZE * (recipeSlot / RECIPE_ROWS);
    }

    private static int recipeSlotY(int recipeSlot) {
        return RECIPE_TOP + SLOT_SIZE * (recipeSlot % RECIPE_ROWS);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);

        List<RecipeHolder<AssemblyRecipe>> recipes = menu.getPotentialOutputs();
        for (int slot = 0; slot < AssemblyTableMenu.RECIPE_SLOT_COUNT && slot < recipes.size(); slot++) {
            int x = leftPos + recipeSlotX(slot);
            int y = topPos + recipeSlotY(slot);
            int state = menu.getRecipeState(slot);
            if (state == AssemblyTableMenu.RECIPE_STATE_ASSEMBLING) {
                guiGraphics.blit(TEXTURE, x, y, ASSEMBLING_U, MARKER_V, ITEM_SIZE, ITEM_SIZE);
            } else if (state == AssemblyTableMenu.RECIPE_STATE_PLANNED) {
                guiGraphics.blit(TEXTURE, x, y, PLANNED_U, MARKER_V, ITEM_SIZE, ITEM_SIZE);
            }
            ItemStack result = recipes.get(slot).value().result();
            guiGraphics.renderItem(result, x, y);
            guiGraphics.renderItemDecorations(font, result, x, y);
        }

        int required = menu.getRequiredEnergy();
        if (required > 0) {
            int progress = (int) (Math.min(1.0F, (float) menu.getEnergyStored() / required) * PROGRESS_HEIGHT);
            guiGraphics.blit(TEXTURE, leftPos + PROGRESS_X, topPos + RECIPE_TOP + PROGRESS_HEIGHT - progress,
                    PROGRESS_U, PROGRESS_V, PROGRESS_WIDTH, progress);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        super.renderLabels(guiGraphics, mouseX, mouseY);
        String energy = String.format("% 8d / % 8d", menu.getEnergyStored(), menu.getRequiredEnergy());
        guiGraphics.drawString(font, energy, ENERGY_X, ENERGY_Y, TEXT_COLOR, false);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        RecipeHolder<AssemblyRecipe> hovered = getRecipeAt(mouseX, mouseY);
        if (hovered != null) {
            guiGraphics.renderTooltip(font, hovered.value().result(), mouseX, mouseY);
        } else {
            renderTooltip(guiGraphics, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int slot = getRecipeSlotAt(mouseX, mouseY);
        Minecraft minecraft = this.minecraft;
        if (slot >= 0 && slot < menu.getPotentialOutputs().size() && minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, slot);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Nullable
    private RecipeHolder<AssemblyRecipe> getRecipeAt(double mouseX, double mouseY) {
        int slot = getRecipeSlotAt(mouseX, mouseY);
        List<RecipeHolder<AssemblyRecipe>> recipes = menu.getPotentialOutputs();
        return slot >= 0 && slot < recipes.size() ? recipes.get(slot) : null;
    }

    private int getRecipeSlotAt(double mouseX, double mouseY) {
        for (int slot = 0; slot < AssemblyTableMenu.RECIPE_SLOT_COUNT; slot++) {
            int x = leftPos + recipeSlotX(slot);
            int y = topPos + recipeSlotY(slot);
            if (mouseX >= x && mouseX < x + ITEM_SIZE && mouseY >= y && mouseY < y + ITEM_SIZE) {
                return slot;
            }
        }
        return -1;
    }
}
