package com.takoy3466.re_buildcraft.transport.gate;

import com.takoy3466.re_buildcraft.transport.FluidTransport;
import com.takoy3466.re_buildcraft.transport.ItemTransport;
import com.takoy3466.re_buildcraft.transport.PipeBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;

/**
 * パイプ自身を対象とするトリガーの実装。
 */
final class PipeTriggers {

    private PipeTriggers() {
    }

    /** ワイヤーの信号の有無。元実装: {@code buildcraft.transport.TriggerPipeSignal}。 */
    record Signal(String id, int iconIndex, WireColor color, boolean active) implements GateStatement.PipeTrigger {
        @Override
        public boolean hasParameter() {
            return false;
        }

        @Override
        public boolean isActive(PipeBlockEntity pipe, ItemStack parameter) {
            return (pipe.signals().strength(color) > 0) == active;
        }
    }

    /** パイプ自身へのレッドストーン入力。元実装: {@code buildcraft.transport.TriggerRedstoneInput}。 */
    record RedstoneInput(String id, int iconIndex, boolean active) implements GateStatement.PipeTrigger {
        @Override
        public boolean hasParameter() {
            return false;
        }

        @Override
        public boolean isActive(PipeBlockEntity pipe, ItemStack parameter) {
            return pipe.getLevel() != null && pipe.getLevel().hasNeighborSignal(pipe.getBlockPos()) == active;
        }
    }

    /** パイプの中身。元実装: {@code buildcraft.transport.TriggerPipeContents}。 */
    enum ContentsKind {
        EMPTY,
        ITEMS,
        LIQUIDS,
        ENERGY
    }

    record Contents(String id, int iconIndex, ContentsKind kind) implements GateStatement.PipeTrigger {
        @Override
        public boolean hasParameter() {
            return kind == ContentsKind.ITEMS || kind == ContentsKind.LIQUIDS;
        }

        @Override
        public boolean isActive(PipeBlockEntity pipe, ItemStack parameter) {
            return switch (kind) {
                case EMPTY -> pipe.transport().isEmpty();
                case ITEMS -> pipe.transport() instanceof ItemTransport items && items.contains(parameter);
                case LIQUIDS -> pipe.transport() instanceof FluidTransport fluids && fluids.contains(
                        parameter.isEmpty() ? Fluids.EMPTY
                                : FluidUtil.getFluidContained(parameter).map(FluidStack::getFluid).orElse(Fluids.EMPTY));
                case ENERGY -> !pipe.transport().isEmpty();
            };
        }
    }
}
