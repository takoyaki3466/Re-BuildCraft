package com.takoy3466.re_buildcraft.transport.behavior;

import com.takoy3466.re_buildcraft.api.power.PowerProvider;
import com.takoy3466.re_buildcraft.transport.PipeBlockEntity;
import com.takoy3466.re_buildcraft.transport.PowerTransport;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/**
 * 木の導電パイプ。エンジンから受け取った MJ を、つながっている導電パイプへ毎 tick 送り出す。
 * 元実装: {@code buildcraft.transport.pipes.PipePowerWood}。
 */
public class WoodPowerBehavior implements PipeBehavior {
    private static final int MAX_ENERGY = 1000;
    private static final int POWER_LOSS = 1;
    private static final int POWER_LOSS_REGULARITY = 100;
    private static final float HIGH_THRESHOLD = 40.0F;
    private static final float MEDIUM_THRESHOLD = 10.0F;
    private static final float HIGH_DIVISOR = 40.0F;
    private static final float HIGH_BONUS = 4.0F;
    private static final float MEDIUM_DIVISOR = 10.0F;
    private static final float MINIMUM_PUSH = 1.0F;

    private final PowerProvider powerProvider = new PowerProvider();

    public WoodPowerBehavior() {
        powerProvider.configure(1, MAX_ENERGY, 1, MAX_ENERGY);
        powerProvider.configurePowerPerdition(POWER_LOSS, POWER_LOSS_REGULARITY);
    }

    @Override
    public PowerProvider powerProvider() {
        return powerProvider;
    }

    @Override
    public void tick(PipeBlockEntity pipe) {
        Level level = pipe.getLevel();
        if (level == null || !(pipe.transport() instanceof PowerTransport own)) {
            return;
        }
        for (Direction direction : Direction.values()) {
            PipeBlockEntity next = pipe.getConnectedPipe(direction);
            if (next == null || !(next.transport() instanceof PowerTransport power)) {
                continue;
            }
            float used = powerProvider.useEnergy(1.0F, energyToPush(), true);
            power.receiveEnergy(level, direction.getOpposite(), used);
            own.addDisplayedPower(direction, used);
        }
    }

    /** 蓄積量が多いほど多く送る (旧 updateEntity の energyToRemove)。 */
    private float energyToPush() {
        float stored = powerProvider.getEnergyStored();
        if (stored > HIGH_THRESHOLD) {
            return stored / HIGH_DIVISOR + HIGH_BONUS;
        }
        if (stored > MEDIUM_THRESHOLD) {
            return stored / MEDIUM_DIVISOR;
        }
        return MINIMUM_PUSH;
    }

    @Override
    public boolean onWrench(PipeBlockEntity pipe) {
        WoodSource.switchSource(pipe);
        return true;
    }

    @Override
    public void initialize(PipeBlockEntity pipe) {
        WoodSource.switchSourceIfNeeded(pipe);
    }

    @Override
    public void onNeighborChanged(PipeBlockEntity pipe) {
        WoodSource.switchSourceIfNeeded(pipe);
    }
}
