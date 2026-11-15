package com.takoy3466.re_buildcraft.client.screen;

import com.takoy3466.re_buildcraft.ReBuildcraft;
import com.takoy3466.re_buildcraft.builders.BuilderMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * ビルダーの GUI。元実装: {@code buildcraft.builders.GuiBuilder}。
 * 設計図を建てている間は右側の必要アイテム欄の付いた背景 (幅 256) を使う。
 */
public class BuilderScreen extends AbstractContainerScreen<BuilderMenu> {
    private static final ResourceLocation TEXTURE = ReBuildcraft.id("textures/gui/builder.png");
    private static final ResourceLocation TEXTURE_BLUEPRINT = ReBuildcraft.id("textures/gui/builder_blueprint.png");
    private static final int IMAGE_WIDTH = 176;
    private static final int BLUEPRINT_WIDTH = 256;
    private static final int IMAGE_HEIGHT = 225;
    private static final int TITLE_X = 73;
    private static final int TITLE_Y = 12;
    private static final int RESOURCES_X = 8;
    private static final int RESOURCES_Y = 60;
    private static final int NEEDED_X = 185;
    private static final int NEEDED_Y = 7;
    private static final int INVENTORY_LABEL_OFFSET = 97;
    private static final int TEXT_COLOR = 0x404040;
    private static final Component RESOURCES = Component.translatable("gui.re_buildcraft.builder.resources");
    private static final Component NEEDED = Component.translatable("gui.re_buildcraft.builder.needed");

    public BuilderScreen(BuilderMenu menu, Inventory playerInventory, Component title) {
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
        boolean blueprint = menu.getBuilder().isBuildingBlueprint();
        guiGraphics.blit(blueprint ? TEXTURE_BLUEPRINT : TEXTURE, leftPos, topPos, 0, 0, blueprint ? BLUEPRINT_WIDTH : IMAGE_WIDTH, imageHeight);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        super.renderLabels(guiGraphics, mouseX, mouseY);
        guiGraphics.drawString(font, RESOURCES, RESOURCES_X, RESOURCES_Y, TEXT_COLOR, false);
        if (menu.getBuilder().isBuildingBlueprint()) {
            guiGraphics.drawString(font, NEEDED, NEEDED_X, NEEDED_Y, TEXT_COLOR, false);
        }
    }
}
