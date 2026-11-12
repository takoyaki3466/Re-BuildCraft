package com.takoy3466.re_buildcraft.api.engine;

import com.takoy3466.re_buildcraft.ReBuildcraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * 木エンジン (Redstone Engine)。レッドストーン入力だけで少しずつ蓄熱する。
 * 元実装: {@code buildcraft.energy.EngineWood}。
 * <p>
 * 圧縮木エンジンなど、木エンジン系の派生エンジンの基底としても使う。
 */
public class RedstoneEngineLogic extends EngineLogic {
    private static final ResourceLocation TEXTURE = ReBuildcraft.id("textures/block/engine/base_wood.png");
    private static final int MAX_ENERGY = 1000;
    private static final int MAX_ENERGY_RECEIVED = 50;
    private static final int EXPLOSION_RANGE = 1;
    private static final int HEAT_UP_INTERVAL_TICKS = 20;
    private static final float[] PISTON_SPEED_BY_STAGE = {0.01F, 0.02F, 0.04F, 0.08F};

    public RedstoneEngineLogic(EngineBlockEntity tile) {
        super(tile);
        this.maxEnergy = MAX_ENERGY;
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
    public void update() {
        super.update();
        Level level = tile.getLevel();
        if (level != null && tile.isRedstonePowered() && level.getGameTime() % HEAT_UP_INTERVAL_TICKS == 0L) {
            energy++;
        }
    }

    @Override
    public boolean isBurning() {
        return tile.isRedstonePowered();
    }

    @Override
    public void burn() {
    }
}
