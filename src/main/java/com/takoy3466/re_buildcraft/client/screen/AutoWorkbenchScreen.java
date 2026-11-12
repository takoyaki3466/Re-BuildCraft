package com.takoy3466.re_buildcraft.client.screen;

import com.takoy3466.re_buildcraft.factory.AutoWorkbenchMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * 自動作業台の GUI。元実装: {@code buildcraft.factory.GuiAutoCrafting}。
 * 旧実装と同じくバニラの作業台の背景をそのまま使う。
 */
public class AutoWorkbenchScreen extends AbstractContainerScreen<AutoWorkbenchMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/gui/container/crafting_table.png");
    private static final int TITLE_X = 28;

    public AutoWorkbenchScreen(AutoWorkbenchMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        titleLabelX = TITLE_X;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
    }
}
