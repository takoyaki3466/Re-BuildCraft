package com.takoy3466.re_buildcraft.client.screen;

import com.takoy3466.re_buildcraft.ReBuildcraft;
import com.takoy3466.re_buildcraft.transport.gate.Gate;
import com.takoy3466.re_buildcraft.transport.gate.GateKind;
import com.takoy3466.re_buildcraft.transport.gate.GateMenu;
import com.takoy3466.re_buildcraft.transport.gate.GateStatement;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * ゲートの設定画面。元実装: {@code buildcraft.transport.GuiGateInterface}。
 * トリガー・アクション枠を左クリックで次の候補、右クリックで前の候補に切り替え、パラメーター枠はカーソルのアイテムで設定する。
 */
public class GateScreen extends AbstractContainerScreen<GateMenu> {
    private static final ResourceLocation TRIGGER_ICONS = ReBuildcraft.id("textures/gui/trigger_textures.png");
    private static final int IMAGE_WIDTH = 175;
    private static final int IMAGE_HEIGHT = 207;
    private static final int SLOT_SIZE = 16;
    private static final int ROW_HEIGHT = 18;
    private static final int ICONS_PER_ROW = 16;
    private static final int INDICATOR_U = 176;
    private static final int INDICATOR_V = 18;
    private static final int INDICATOR_WIDTH = 18;
    private static final int INDICATOR_HEIGHT = 4;
    private static final int PARAMETER_COVER_U = 176;
    private static final int PARAMETER_COVER_SIZE = 18;
    private static final int INVENTORY_LABEL_OFFSET = 97;

    /** 枠 1 つ分の位置。 */
    private record SlotPosition(int region, int index, int x, int y) {
    }

    private final List<SlotPosition> layout;
    private final ResourceLocation background;

    public GateScreen(GateMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = IMAGE_WIDTH;
        imageHeight = IMAGE_HEIGHT;
        inventoryLabelY = IMAGE_HEIGHT - INVENTORY_LABEL_OFFSET;
        Gate gate = menu.pipe().getGate();
        GateKind kind = gate == null ? GateKind.SINGLE : gate.kind();
        this.layout = createLayout(kind);
        this.background = ReBuildcraft.id("textures/gui/gate_interface_" + backgroundNumber(kind) + ".png");
    }

    private static int backgroundNumber(GateKind kind) {
        return switch (kind.entries()) {
            case 1 -> 1;
            case 2 -> 2;
            case 4 -> 3;
            default -> 4;
        };
    }

    /** 旧 GuiGateInterface のコンストラクターにあった枠の配置。 */
    private static List<SlotPosition> createLayout(GateKind kind) {
        List<SlotPosition> slots = new ArrayList<>();
        switch (kind.entries()) {
            case 1 -> {
                slots.add(new SlotPosition(GateMenu.REGION_TRIGGER, 0, 65, 54));
                slots.add(new SlotPosition(GateMenu.REGION_ACTION, 0, 99, 54));
            }
            case 2 -> {
                for (int i = 0; i < 2; i++) {
                    slots.add(new SlotPosition(GateMenu.REGION_TRIGGER, i, 65, 46 + ROW_HEIGHT * i));
                    slots.add(new SlotPosition(GateMenu.REGION_ACTION, i, 99, 46 + ROW_HEIGHT * i));
                }
            }
            case 4 -> {
                for (int i = 0; i < 4; i++) {
                    slots.add(new SlotPosition(GateMenu.REGION_TRIGGER, i, 60, 36 + ROW_HEIGHT * i));
                    slots.add(new SlotPosition(GateMenu.REGION_PARAMETER, i, 78, 36 + ROW_HEIGHT * i));
                    slots.add(new SlotPosition(GateMenu.REGION_ACTION, i, 112, 36 + ROW_HEIGHT * i));
                }
            }
            default -> {
                for (int i = 0; i < 8; i++) {
                    int column = i / 4;
                    int y = 36 + ROW_HEIGHT * (i % 4);
                    slots.add(new SlotPosition(GateMenu.REGION_TRIGGER, i, column == 0 ? 8 : 100, y));
                    slots.add(new SlotPosition(GateMenu.REGION_PARAMETER, i, column == 0 ? 26 : 118, y));
                    slots.add(new SlotPosition(GateMenu.REGION_ACTION, i, column == 0 ? 60 : 152, y));
                }
            }
        }
        return List.copyOf(slots);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(background, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        Gate gate = menu.pipe().getGate();
        if (gate == null) {
            return;
        }
        for (SlotPosition slot : layout) {
            int x = leftPos + slot.x();
            int y = topPos + slot.y();
            switch (slot.region()) {
                case GateMenu.REGION_TRIGGER -> {
                    drawIcon(guiGraphics, gate.trigger(slot.index()), x, y);
                    drawTriggerState(guiGraphics, gate, slot.index(), x, y);
                }
                case GateMenu.REGION_ACTION -> drawIcon(guiGraphics, gate.action(slot.index()), x, y);
                default -> {
                    ItemStack parameter = gate.parameter(slot.index());
                    if (!parameter.isEmpty()) {
                        guiGraphics.renderItem(parameter, x, y);
                    }
                }
            }
        }
    }

    /** 成り立っているトリガーの横に印を付け、パラメーターを取らないトリガーのパラメーター枠は塞ぐ。 */
    private void drawTriggerState(GuiGraphics guiGraphics, Gate gate, int index, int x, int y) {
        GateStatement.Trigger trigger = gate.trigger(index);
        boolean active = menu.isTriggerActive(index);
        if (gate.kind().hasParameters()) {
            if (active) {
                guiGraphics.blit(background, x + 35, y + 6, INDICATOR_U, INDICATOR_V, INDICATOR_WIDTH, INDICATOR_HEIGHT);
            }
            if (trigger == null || !trigger.hasParameter()) {
                guiGraphics.blit(background, x + 17, y - 1, PARAMETER_COVER_U, 0, PARAMETER_COVER_SIZE, PARAMETER_COVER_SIZE);
            }
        } else if (active) {
            guiGraphics.blit(background, x + 17, y + 6, INDICATOR_U, INDICATOR_V, INDICATOR_WIDTH, INDICATOR_HEIGHT);
        }
    }

    private static void drawIcon(GuiGraphics guiGraphics, @Nullable GateStatement statement, int x, int y) {
        if (statement == null) {
            return;
        }
        int icon = statement.iconIndex();
        guiGraphics.blit(TRIGGER_ICONS, x, y, (icon % ICONS_PER_ROW) * SLOT_SIZE, (icon / ICONS_PER_ROW) * SLOT_SIZE, SLOT_SIZE, SLOT_SIZE);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        SlotPosition hovered = slotAt(mouseX, mouseY);
        Gate gate = menu.pipe().getGate();
        GateStatement statement = null;
        if (hovered != null && gate != null) {
            statement = switch (hovered.region()) {
                case GateMenu.REGION_TRIGGER -> gate.trigger(hovered.index());
                case GateMenu.REGION_ACTION -> gate.action(hovered.index());
                default -> null;
            };
        }
        if (statement != null) {
            guiGraphics.renderTooltip(font, statement.description(), mouseX, mouseY);
        } else {
            renderTooltip(guiGraphics, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        SlotPosition slot = slotAt(mouseX, mouseY);
        Minecraft minecraft = this.minecraft;
        if (slot != null && minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, GateMenu.buttonId(slot.region(), slot.index(), button == 1));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Nullable
    private SlotPosition slotAt(double mouseX, double mouseY) {
        for (SlotPosition slot : layout) {
            int x = leftPos + slot.x();
            int y = topPos + slot.y();
            if (mouseX >= x && mouseX < x + SLOT_SIZE && mouseY >= y && mouseY < y + SLOT_SIZE) {
                return slot;
            }
        }
        return null;
    }
}
