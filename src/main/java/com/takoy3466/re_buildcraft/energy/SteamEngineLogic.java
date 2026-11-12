package com.takoy3466.re_buildcraft.energy;

import com.takoy3466.re_buildcraft.Config;
import com.takoy3466.re_buildcraft.ReBuildcraft;
import com.takoy3466.re_buildcraft.api.engine.EngineBlockEntity;
import com.takoy3466.re_buildcraft.api.engine.EngineLogic;
import com.takoy3466.re_buildcraft.registry.ModMenus;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * 石エンジン (旧名 Steam Engine)。かまどの燃料を燃やして動く。
 * 元実装: {@code buildcraft.energy.EngineStone}。
 */
public class SteamEngineLogic extends EngineLogic {
    public static final int DATA_BURN_TIME = 0;
    public static final int DATA_TOTAL_BURN_TIME = 1;
    public static final int DATA_COUNT = 2;
    public static final int FUEL_SLOT_X = 80;

    private static final ResourceLocation TEXTURE = ReBuildcraft.id("textures/block/engine/base_stone.png");
    private static final String TITLE_KEY = "container.re_buildcraft.steam_engine";
    private static final String TAG_BURN_TIME = "burnTime";
    private static final String TAG_TOTAL_BURN_TIME = "totalBurnTime";
    private static final int MAX_ENERGY = 10000;
    private static final int MAX_ENERGY_EXTRACTED = 100;
    private static final int MAX_ENERGY_RECEIVED = 200;
    private static final int EXPLOSION_RANGE = 4;
    private static final int BASE_POWER = 1;
    private static final int POWERED_UP_POWER = 2;
    private static final float[] PISTON_SPEED_BY_STAGE = {0.02F, 0.04F, 0.08F, 0.16F};

    private int burnTime;
    private int totalBurnTime;

    private final ContainerData containerData = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_BURN_TIME -> burnTime;
                case DATA_TOTAL_BURN_TIME -> totalBurnTime;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case DATA_BURN_TIME -> burnTime = value;
                case DATA_TOTAL_BURN_TIME -> totalBurnTime = value;
                default -> {
                }
            }
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public SteamEngineLogic(EngineBlockEntity tile) {
        super(tile);
        this.maxEnergy = MAX_ENERGY;
        this.maxEnergyExtracted = MAX_ENERGY_EXTRACTED;
    }

    @Override
    public ResourceLocation getTexture() {
        return TEXTURE;
    }

    @Override
    public int explosionRange() {
        return EXPLOSION_RANGE;
    }

    @Override
    public int maxEnergyReceived() {
        return MAX_ENERGY_RECEIVED;
    }

    @Override
    public float getPistonSpeed() {
        int stage = getEnergyStage().ordinal();
        return stage < PISTON_SPEED_BY_STAGE.length ? PISTON_SPEED_BY_STAGE[stage] : 0.0F;
    }

    @Override
    public boolean isBurning() {
        return burnTime > 0;
    }

    @Override
    public void burn() {
        if (burnTime > 0) {
            burnTime--;
            addEnergy(Config.ENGINE_POWER_UP.get() ? POWERED_UP_POWER : BASE_POWER);
        }
        if (burnTime == 0 && tile.isRedstonePowered()) {
            ItemStack fuel = tile.getFuelStack();
            burnTime = totalBurnTime = fuel.isEmpty() ? 0 : fuel.getBurnTime(RecipeType.SMELTING);
            if (burnTime > 0) {
                tile.consumeFuelItem();
            }
        }
    }

    @Override
    public int getScaledBurnTime(int scale) {
        return totalBurnTime <= 0 ? 0 : (int) ((float) burnTime / (float) totalBurnTime * scale);
    }

    @Override
    public int getHeat() {
        return energy;
    }

    @Override
    public boolean usesInventory() {
        return true;
    }

    @Override
    public MenuProvider getMenuProvider() {
        return new SimpleMenuProvider(
                (containerId, playerInventory, player) -> new EngineMenu(
                        ModMenus.STEAM_ENGINE.get(), containerId, playerInventory, tile, containerData, FUEL_SLOT_X),
                Component.translatable(TITLE_KEY));
    }

    @Override
    public void save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt(TAG_BURN_TIME, burnTime);
        tag.putInt(TAG_TOTAL_BURN_TIME, totalBurnTime);
    }

    @Override
    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        burnTime = tag.getInt(TAG_BURN_TIME);
        totalBurnTime = tag.getInt(TAG_TOTAL_BURN_TIME);
    }
}
