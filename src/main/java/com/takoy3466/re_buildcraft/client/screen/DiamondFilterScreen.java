package com.takoy3466.re_buildcraft.client.screen;

import com.takoy3466.re_buildcraft.ReBuildcraft;
import com.takoy3466.re_buildcraft.transport.DiamondFilterMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/** ダイヤパイプのフィルター設定画面。元実装: {@code buildcraft.transport.GuiDiamondPipe}。 */
public class DiamondFilterScreen extends AbstractContainerScreen<DiamondFilterMenu> {
    private static final ResourceLocation TEXTURE = ReBuildcraft.id("textures/gui/filter.png");
    private static final int IMAGE_WIDTH = 175;
    private static final int IMAGE_HEIGHT = 225;
    private static final int INVENTORY_LABEL_OFFSET = 97;

    public DiamondFilterScreen(DiamondFilterMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = IMAGE_WIDTH;
        imageHeight = IMAGE_HEIGHT;
        inventoryLabelY = IMAGE_HEIGHT - INVENTORY_LABEL_OFFSET;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }
}
