package com.takoy3466.re_buildcraft.client.screen;

import com.takoy3466.re_buildcraft.ReBuildcraft;
import com.takoy3466.re_buildcraft.energy.EngineMenu;
import com.takoy3466.re_buildcraft.energy.SteamEngineLogic;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/** 石エンジンの GUI。元実装: {@code buildcraft.energy.GuiSteamEngine}。 */
public class SteamEngineScreen extends AbstractContainerScreen<EngineMenu> {
    private static final ResourceLocation TEXTURE = ReBuildcraft.id("textures/gui/steam_engine.png");
    private static final int FLAME_HEIGHT = 12;
    private static final int FLAME_X = 80;
    private static final int FLAME_Y = 24;
    private static final int FLAME_U = 176;
    private static final int FLAME_WIDTH = 14;

    public SteamEngineScreen(EngineMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        int totalBurnTime = menu.getData(SteamEngineLogic.DATA_TOTAL_BURN_TIME);
        if (totalBurnTime <= 0) {
            return;
        }
        int flame = (int) ((float) menu.getData(SteamEngineLogic.DATA_BURN_TIME) / totalBurnTime * FLAME_HEIGHT);
        if (flame > 0) {
            guiGraphics.blit(TEXTURE, leftPos + FLAME_X, topPos + FLAME_Y + FLAME_HEIGHT - flame,
                    FLAME_U, FLAME_HEIGHT - flame, FLAME_WIDTH, flame + 2);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }
}
