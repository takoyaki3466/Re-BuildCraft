package com.takoy3466.re_buildcraft.client.screen;

import com.takoy3466.re_buildcraft.ReBuildcraft;
import com.takoy3466.re_buildcraft.energy.CombustionEngineLogic;
import com.takoy3466.re_buildcraft.energy.EngineMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;

/** 鉄エンジンの GUI。燃料と冷却水の量をゲージで表示する。元実装: {@code buildcraft.energy.GuiCombustionEngine}。 */
public class CombustionEngineScreen extends AbstractContainerScreen<EngineMenu> {
    private static final ResourceLocation TEXTURE = ReBuildcraft.id("textures/gui/combustion_engine.png");
    private static final int GAUGE_HEIGHT = 58;
    private static final int GAUGE_TOP = 19;
    private static final int FUEL_GAUGE_X = 104;
    private static final int COOLANT_GAUGE_X = 122;
    private static final int GAUGE_WIDTH = 16;
    private static final int GAUGE_OVERLAY_U = 176;
    private static final int GAUGE_OVERLAY_HEIGHT = 60;
    private static final float COLOR_MAX = 255.0F;

    public CombustionEngineScreen(EngineMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        renderGauge(guiGraphics, FUEL_GAUGE_X,
                menu.getData(CombustionEngineLogic.DATA_FUEL_AMOUNT), menu.getData(CombustionEngineLogic.DATA_FUEL_ID));
        renderGauge(guiGraphics, COOLANT_GAUGE_X,
                menu.getData(CombustionEngineLogic.DATA_COOLANT_AMOUNT), menu.getData(CombustionEngineLogic.DATA_COOLANT_ID));
    }

    private void renderGauge(GuiGraphics guiGraphics, int gaugeX, int amount, int fluidId) {
        int scaled = (int) ((float) amount / CombustionEngineLogic.MAX_LIQUID * GAUGE_HEIGHT);
        Fluid fluid = BuiltInRegistries.FLUID.byId(fluidId);
        if (scaled <= 0 || fluid == Fluids.EMPTY) {
            return;
        }
        IClientFluidTypeExtensions fluidExtensions = IClientFluidTypeExtensions.of(fluid);
        TextureAtlasSprite sprite = Minecraft.getInstance()
                .getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                .apply(fluidExtensions.getStillTexture());
        int tint = fluidExtensions.getTintColor();
        guiGraphics.setColor((tint >> 16 & 0xFF) / COLOR_MAX, (tint >> 8 & 0xFF) / COLOR_MAX, (tint & 0xFF) / COLOR_MAX, 1.0F);

        int x = leftPos + gaugeX;
        int bottom = topPos + GAUGE_TOP + GAUGE_HEIGHT;
        for (int drawn = 0; drawn < scaled; drawn += GAUGE_WIDTH) {
            int height = Math.min(GAUGE_WIDTH, scaled - drawn);
            guiGraphics.blit(x, bottom - drawn - height, 0, GAUGE_WIDTH, height, sprite);
        }
        guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);

        guiGraphics.blit(TEXTURE, x, topPos + GAUGE_TOP, GAUGE_OVERLAY_U, 0, GAUGE_WIDTH, GAUGE_OVERLAY_HEIGHT);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }
}
