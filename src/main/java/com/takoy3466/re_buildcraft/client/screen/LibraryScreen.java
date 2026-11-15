package com.takoy3466.re_buildcraft.client.screen;

import com.takoy3466.re_buildcraft.ReBuildcraft;
import com.takoy3466.re_buildcraft.builders.BuilderPayloads;
import com.takoy3466.re_buildcraft.builders.LibraryBlockEntity;
import com.takoy3466.re_buildcraft.builders.LibraryMenu;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * 設計図ライブラリの GUI。元実装: {@code buildcraft.builders.GuiBlueprintLibrary}。
 * 一覧を 12 件ずつ表示し、&lt; / &gt; でページを送る。行をクリックすると選び、Del で一覧から外し、Lock で持ち主以外が開けないようにする。
 */
public class LibraryScreen extends AbstractContainerScreen<LibraryMenu> {
    private static final ResourceLocation TEXTURE = ReBuildcraft.id("textures/gui/library.png");
    private static final int IMAGE_WIDTH = 176;
    private static final int IMAGE_HEIGHT = 225;
    private static final int PAGE_SIZE = 12;
    private static final int LIST_LEFT = 8;
    private static final int LIST_RIGHT = 88;
    private static final int LIST_TOP = 24;
    private static final int ROW_HEIGHT = 9;
    private static final int TEXT_X = 9;
    private static final int TEXT_Y = 25;
    private static final int HIGHLIGHT = 0x80FFFFFF;
    private static final int TEXT_COLOR = 0x404040;
    private static final int TITLE_X = 6;
    private static final int TITLE_Y = 6;
    private static final int INVENTORY_LABEL_OFFSET = 97;
    private static final int PROGRESS_WIDTH = 22;
    private static final int PROGRESS_HEIGHT = 16;
    private static final int IN_X = 128;
    private static final int IN_Y = 61;
    private static final int IN_U = 198;
    private static final int IN_V = 16;
    private static final int OUT_X = 128;
    private static final int OUT_Y = 78;
    private static final int OUT_U = 176;
    private static final int OUT_V = 0;

    private int firstIndex;
    private Button lockButton;

    public LibraryScreen(LibraryMenu menu, Inventory playerInventory, Component title) {
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
        addRenderableWidget(Button.builder(Component.literal("<"), button -> page(-PAGE_SIZE))
                .bounds(leftPos + 100, topPos + 23, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), button -> page(PAGE_SIZE))
                .bounds(leftPos + 122, topPos + 23, 20, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.re_buildcraft.library.delete"),
                        button -> click(LibraryMenu.BUTTON_DELETE))
                .bounds(leftPos + 100, topPos + 114, 25, 20).build());
        lockButton = addRenderableWidget(Button.builder(lockLabel(), button -> click(LibraryMenu.BUTTON_LOCK))
                .bounds(leftPos + 127, topPos + 114, 40, 20).build());
    }

    private Component lockLabel() {
        return Component.translatable(menu.isLocked() ? "gui.re_buildcraft.library.unlock" : "gui.re_buildcraft.library.lock");
    }

    private void page(int delta) {
        int size = menu.getEntries().size();
        firstIndex = Math.max(0, Math.min(firstIndex + delta, Math.max(0, size - 1) / PAGE_SIZE * PAGE_SIZE));
    }

    private void click(int buttonId) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, buttonId);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        lockButton.setMessage(lockLabel());
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        int in = menu.getProgressIn() * PROGRESS_WIDTH / LibraryBlockEntity.PROGRESS_MAX;
        int out = menu.getProgressOut() * PROGRESS_WIDTH / LibraryBlockEntity.PROGRESS_MAX;
        guiGraphics.blit(TEXTURE, leftPos + IN_X + PROGRESS_WIDTH - in, topPos + IN_Y, IN_U - in, IN_V, in, PROGRESS_HEIGHT);
        guiGraphics.blit(TEXTURE, leftPos + OUT_X, topPos + OUT_Y, OUT_U, OUT_V, out, PROGRESS_HEIGHT);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        super.renderLabels(guiGraphics, mouseX, mouseY);
        List<BuilderPayloads.LibraryEntry> page = visibleEntries();
        for (int row = 0; row < page.size(); row++) {
            BuilderPayloads.LibraryEntry entry = page.get(row);
            if (entry.id() == menu.getSelected()) {
                guiGraphics.fill(LIST_LEFT, LIST_TOP + ROW_HEIGHT * row, LIST_RIGHT, LIST_TOP + ROW_HEIGHT * (row + 1), HIGHLIGHT);
            }
            guiGraphics.drawString(font, font.plainSubstrByWidth(entry.name(), LIST_RIGHT - LIST_LEFT), TEXT_X,
                    TEXT_Y + ROW_HEIGHT * row, TEXT_COLOR, false);
        }
    }

    private List<BuilderPayloads.LibraryEntry> visibleEntries() {
        List<BuilderPayloads.LibraryEntry> entries = menu.getEntries();
        int from = Math.min(firstIndex, entries.size());
        return entries.subList(from, Math.min(entries.size(), from + PAGE_SIZE));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double x = mouseX - leftPos;
        double y = mouseY - topPos;
        if (x >= LIST_LEFT && x <= LIST_RIGHT && y >= LIST_TOP) {
            int row = (int) ((y - LIST_TOP) / ROW_HEIGHT);
            List<BuilderPayloads.LibraryEntry> page = visibleEntries();
            if (row >= 0 && row < page.size()) {
                click(LibraryMenu.BUTTON_SELECT_BASE + page.get(row).id());
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
