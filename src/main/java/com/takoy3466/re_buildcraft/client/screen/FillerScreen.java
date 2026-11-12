package com.takoy3466.re_buildcraft.client.screen;

import com.takoy3466.re_buildcraft.ReBuildcraft;
import com.takoy3466.re_buildcraft.builders.FillerBlock;
import com.takoy3466.re_buildcraft.builders.FillerMenu;
import com.takoy3466.re_buildcraft.builders.filler.FillerPatterns;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * フィラーの GUI。元実装: {@code buildcraft.builders.GuiFiller}。
 * 左に稼働状態のアイコン、右のモジュール枠の下に現在のパターンの絵柄を表示する。
 */
public class FillerScreen extends AbstractContainerScreen<FillerMenu> {
    private static final ResourceLocation TEXTURE = ReBuildcraft.id("textures/gui/filler.png");
    /** 旧 fillerTexture (16x16 区画、16 列)。 */
    private static final ResourceLocation ICONS = ReBuildcraft.id("textures/gui/filler_icons.png");
    private static final int IMAGE_WIDTH = 175;
    private static final int IMAGE_HEIGHT = 240;
    private static final int ICON_SIZE = 16;
    private static final int ICON_COLUMNS = 16;
    private static final int ICON_ATLAS_SIZE = 256;
    private static final int ICON_ACTIVE = 0;
    private static final int ICON_INACTIVE = 1;
    /** パターンの絵柄の番号 = モジュール番号 + 16 (旧 registerFiller の iconIndex)。 */
    private static final int PATTERN_ICON_OFFSET = 16;
    private static final int STATUS_X = 8;
    private static final int STATUS_Y = 34;
    private static final int PATTERN_X = 125;
    private static final int PATTERN_Y = 34;
    private static final int TITLE_X = 75;
    private static final int TITLE_Y = 6;
    private static final int RESOURCES_X = 8;
    private static final int RESOURCES_Y = 74;
    private static final int INVENTORY_LABEL_Y = 142;
    private static final int TEXT_COLOR = 0x404040;
    private static final Component RESOURCES = Component.translatable("gui.re_buildcraft.filler.resources");

    public FillerScreen(FillerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = IMAGE_WIDTH;
        imageHeight = IMAGE_HEIGHT;
        titleLabelX = TITLE_X;
        titleLabelY = TITLE_Y;
        inventoryLabelY = INVENTORY_LABEL_Y;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        boolean active = menu.getFiller().getBlockState().getValue(FillerBlock.ACTIVE);
        icon(guiGraphics, active ? ICON_ACTIVE : ICON_INACTIVE, STATUS_X, STATUS_Y);
        FillerPatterns.Entry entry = menu.getFiller().currentEntry();
        if (entry != null) {
            icon(guiGraphics, entry.id() + PATTERN_ICON_OFFSET, PATTERN_X, PATTERN_Y);
        }
    }

    private void icon(GuiGraphics guiGraphics, int index, int x, int y) {
        guiGraphics.blit(ICONS, leftPos + x, topPos + y, (index % ICON_COLUMNS) * ICON_SIZE, (index / ICON_COLUMNS) * ICON_SIZE,
                ICON_SIZE, ICON_SIZE, ICON_ATLAS_SIZE, ICON_ATLAS_SIZE);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        super.renderLabels(guiGraphics, mouseX, mouseY);
        guiGraphics.drawString(font, RESOURCES, RESOURCES_X, RESOURCES_Y, TEXT_COLOR, false);
    }
}
