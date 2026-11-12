package com.takoy3466.re_buildcraft.transport.gate;

import com.takoy3466.re_buildcraft.api.IMachine;
import com.takoy3466.re_buildcraft.api.engine.EnergyStage;
import com.takoy3466.re_buildcraft.api.engine.EngineBlockEntity;
import java.util.function.IntPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * 隣接ブロックを対象とするトリガーの実装。
 */
final class NeighborTriggers {

    private NeighborTriggers() {
    }

    /** 容器の状態の区分 (旧 TriggerInventory.State / TriggerLiquidContainer.State)。 */
    enum ContainerState {
        EMPTY,
        CONTAINS,
        SPACE,
        FULL;

        boolean hasParameter() {
            return this == CONTAINS || this == SPACE;
        }
    }

    /** インベントリの空/中身あり/空きあり/満杯。元実装: {@code buildcraft.core.TriggerInventory}。 */
    record Inventory(String id, int iconIndex, ContainerState state) implements GateStatement.NeighborTrigger {
        @Override
        public boolean hasParameter() {
            return state.hasParameter();
        }

        @Override
        public boolean isActive(Level level, BlockPos neighborPos, Direction side, ItemStack parameter) {
            IItemHandler inventory = level.getCapability(Capabilities.ItemHandler.BLOCK, neighborPos, side);
            if (inventory == null || inventory.getSlots() == 0) {
                return false;
            }
            boolean hasItem = false;
            boolean hasSpace = false;
            for (int slot = 0; slot < inventory.getSlots(); slot++) {
                ItemStack stack = inventory.getStackInSlot(slot);
                if (stack.isEmpty()) {
                    hasSpace = true;
                    continue;
                }
                hasItem |= parameter.isEmpty() || ItemStack.isSameItemSameComponents(stack, parameter);
                if (!parameter.isEmpty() && stack.getCount() < stack.getMaxStackSize()
                        && ItemStack.isSameItemSameComponents(stack, parameter)) {
                    hasSpace = true;
                }
            }
            return switch (state) {
                case EMPTY -> !hasItem;
                case CONTAINS -> hasItem;
                case SPACE -> hasSpace;
                case FULL -> !hasSpace;
            };
        }
    }

    /** 液体容器の空/中身あり/空きあり/満杯。元実装: {@code buildcraft.core.TriggerLiquidContainer}。 */
    record Liquid(String id, int iconIndex, ContainerState state) implements GateStatement.NeighborTrigger {
        @Override
        public boolean hasParameter() {
            return state.hasParameter();
        }

        @Override
        public boolean isActive(Level level, BlockPos neighborPos, Direction side, ItemStack parameter) {
            IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, neighborPos, side);
            if (handler == null || handler.getTanks() == 0) {
                return false;
            }
            Fluid searched = parameter.isEmpty() ? Fluids.EMPTY
                    : FluidUtil.getFluidContained(parameter).map(FluidStack::getFluid).orElse(Fluids.EMPTY);
            return switch (state) {
                case EMPTY -> allTanks(handler, tank -> handler.getFluidInTank(tank).isEmpty());
                case CONTAINS -> anyTank(handler, tank -> {
                    FluidStack fluid = handler.getFluidInTank(tank);
                    return !fluid.isEmpty() && (searched == Fluids.EMPTY || fluid.getFluid().isSame(searched));
                });
                case SPACE -> anyTank(handler, tank -> {
                    FluidStack fluid = handler.getFluidInTank(tank);
                    return fluid.isEmpty() || fluid.getAmount() < handler.getTankCapacity(tank)
                            && (searched == Fluids.EMPTY || fluid.getFluid().isSame(searched));
                });
                case FULL -> allTanks(handler, tank -> handler.getFluidInTank(tank).getAmount() >= handler.getTankCapacity(tank));
            };
        }

        private static boolean anyTank(IFluidHandler handler, IntPredicate predicate) {
            for (int tank = 0; tank < handler.getTanks(); tank++) {
                if (predicate.test(tank)) {
                    return true;
                }
            }
            return false;
        }

        private static boolean allTanks(IFluidHandler handler, IntPredicate predicate) {
            return !anyTank(handler, predicate.negate());
        }
    }

    /** 機械の作業中/作業完了。元実装: {@code buildcraft.core.TriggerMachine}。 */
    record Machine(String id, int iconIndex, boolean active) implements GateStatement.NeighborTrigger {
        @Override
        public boolean hasParameter() {
            return false;
        }

        @Override
        public boolean isActive(Level level, BlockPos neighborPos, Direction side, ItemStack parameter) {
            return level.getBlockEntity(neighborPos) instanceof IMachine machine && machine.isActive() == active;
        }
    }

    /** エンジンの熱段階。元実装: {@code buildcraft.energy.TriggerEngineHeat}。 */
    record EngineHeat(String id, int iconIndex, EnergyStage stage) implements GateStatement.NeighborTrigger {
        @Override
        public boolean hasParameter() {
            return false;
        }

        @Override
        public boolean isActive(Level level, BlockPos neighborPos, Direction side, ItemStack parameter) {
            return level.getBlockEntity(neighborPos) instanceof EngineBlockEntity engine
                    && engine.getEngine().getEnergyStage() == stage;
        }
    }
}
