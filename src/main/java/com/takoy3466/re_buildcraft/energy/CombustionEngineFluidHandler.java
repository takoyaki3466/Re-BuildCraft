package com.takoy3466.re_buildcraft.energy;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/**
 * 鉄エンジンの液体入出力。燃料と冷却水を、液体の種類に応じて別々のタンクへ振り分ける。
 * 旧実装 ({@code EngineIron.fill} / {@code TileEngine.empty}) と同じく、外部からの取り出しはできない。
 */
final class CombustionEngineFluidHandler implements IFluidHandler {
    private final FluidTank[] tanks;

    CombustionEngineFluidHandler(FluidTank fuelTank, FluidTank coolantTank) {
        this.tanks = new FluidTank[]{fuelTank, coolantTank};
    }

    @Override
    public int getTanks() {
        return tanks.length;
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        return tanks[tank].getFluid();
    }

    @Override
    public int getTankCapacity(int tank) {
        return tanks[tank].getCapacity();
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return tanks[tank].isFluidValid(stack);
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        for (FluidTank tank : tanks) {
            if (tank.isFluidValid(resource)) {
                return tank.fill(resource, action);
            }
        }
        return 0;
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        return FluidStack.EMPTY;
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        return FluidStack.EMPTY;
    }
}
