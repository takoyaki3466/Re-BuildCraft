package com.takoy3466.re_buildcraft.client.screen;

import com.takoy3466.re_buildcraft.ReBuildcraft;
import com.takoy3466.re_buildcraft.builders.ArchitectBlockEntity;
import com.takoy3466.re_buildcraft.builders.ArchitectMenu;
import com.takoy3466.re_buildcraft.builders.BuilderPayloads;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * 設計台の GUI。元実装: {@code buildcraft.builders.GuiTemplate}。
 * 名前欄に入力した内容はサーバーへ送り、サーバー側で使える文字と長さを確かめてから設定する。
 */
public class ArchitectScreen extends AbstractContainerScreen<ArchitectMenu> {
    private static final ResourceLocation TEXTURE = ReBuildcraft.id("textures/gui/architect.png");
    private static final int IMAGE_WIDTH = 175;
    private static final int IMAGE_HEIGHT = 166;
    private static final int NAME_X = 51;
    private static final int NAME_Y = 61;
    private static final int NAME_WIDTH = 88;
    private static final int NAME_HEIGHT = 10;
    private static final int NAME_COLOR = 0x404040;
    private static final int PROGRESS_X = 79;
    private static final int PROGRESS_Y = 34;
    private static final int PROGRESS_U = 176;
    private static final int PROGRESS_V = 14;
    private static final int PROGRESS_WIDTH = 24;
    private static final int PROGRESS_HEIGHT = 16;
    private static final int TITLE_X = 8;
    private static final int TITLE_Y = 6;
    private static final int INVENTORY_LABEL_OFFSET = 94;

    private EditBox nameField;

    public ArchitectScreen(ArchitectMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = IMAGE_WIDTH;
        imageHeight = IMAGE_HEIGHT;
        titleLabelX = TITLE_X;
        titleLabelY = TITLE_Y;
        inventoryLabelY = IMAGE_HEIGHT - INVENTORY_LABEL_OFFSET;
    }

    @Override
    protected void init() {
        super.init();
        nameField = new EditBox(font, leftPos + NAME_X, topPos + NAME_Y, NAME_WIDTH, NAME_HEIGHT, Component.empty());
        nameField.setBordered(false);
        // 旧 GuiTemplate と同じ濃い灰色で描く。EditBox の既定の文字色 (明るい灰色) は設計台の背景に溶けて、入力しても見えなかった。
        nameField.setTextColor(NAME_COLOR);
        nameField.setMaxLength(ArchitectBlockEntity.MAX_NAME_LENGTH);
        nameField.setValue(menu.getArchitect().getName());
        nameField.setFilter(text -> text.equals(ArchitectBlockEntity.sanitizeName(text)));
        nameField.setResponder(text -> PacketDistributor.sendToServer(
                new BuilderPayloads.ArchitectName(menu.getArchitect().getBlockPos(), text)));
        addRenderableWidget(nameField);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 名前の入力中は、インベントリを閉じるキーなどを名前欄に渡す。
        if (nameField.isFocused() && nameField.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (nameField.isFocused() && keyCode != GLFW.GLFW_KEY_ESCAPE) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        int progress = menu.getComputingTime() * PROGRESS_WIDTH / ArchitectBlockEntity.COMPUTE_TICKS;
        guiGraphics.blit(TEXTURE, leftPos + PROGRESS_X, topPos + PROGRESS_Y, PROGRESS_U, PROGRESS_V, progress + 1, PROGRESS_HEIGHT);
    }
}
