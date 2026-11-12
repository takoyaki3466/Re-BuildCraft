package com.takoy3466.re_buildcraft.transport.behavior;

import com.takoy3466.re_buildcraft.api.power.PowerProvider;
import com.takoy3466.re_buildcraft.transport.FluidTransport;
import com.takoy3466.re_buildcraft.transport.PipeBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/**
 * 木の防水パイプ。1 MJ ごとに 1 バケツ分の「取り出し枠」を貯め、毎 tick 流量の範囲で取り出し元の液体容器から吸い出す。
 * 元実装: {@code buildcraft.transport.pipes.PipeLiquidsWood}。
 */
public class WoodFluidExtractionBehavior implements PipeBehavior {
    private static final int MAX_ENERGY = 64;
    /** 取り出し枠の上限 (旧 MAX_LIQUID_STORED = 64 バケツ)。 */
    private static final int MAX_LIQUID_STORED = 64 * FluidType.BUCKET_VOLUME;
    private static final String TAG_TO_EXTRACT = "liquidToExtract";

    private final PowerProvider powerProvider = new PowerProvider();
    private int liquidToExtract;

    public WoodFluidExtractionBehavior() {
        powerProvider.configure(1, MAX_ENERGY, 1, MAX_ENERGY);
        powerProvider.configurePowerPerdition(MAX_ENERGY, 1);
    }

    @Override
    public PowerProvider powerProvider() {
        return powerProvider;
    }

    @Override
    public void doWork(PipeBlockEntity pipe) {
        if (sourceHandler(pipe) != null && liquidToExtract <= MAX_LIQUID_STORED) {
            liquidToExtract += (int) (powerProvider.useEnergy(1.0F, 1.0F, true) * FluidType.BUCKET_VOLUME);
        }
    }

    @Override
    public void tick(PipeBlockEntity pipe) {
        Direction source = WoodSource.source(pipe);
        IFluidHandler handler = sourceHandler(pipe);
        if (liquidToExtract <= 0 || source == null || handler == null || !(pipe.transport() instanceof FluidTransport fluids)) {
            return;
        }
        int limit = Math.min(liquidToExtract, pipe.type().fluidFlowRate());
        FluidStack available = handler.drain(limit, IFluidHandler.FluidAction.SIMULATE);
        int accepted = fluids.fill(source, available, true);
        if (accepted > 0) {
            handler.drain(available.copyWithAmount(accepted), IFluidHandler.FluidAction.EXECUTE);
            liquidToExtract -= accepted;
        }
    }

    @Nullable
    private static IFluidHandler sourceHandler(PipeBlockEntity pipe) {
        Level level = pipe.getLevel();
        Direction source = WoodSource.source(pipe);
        if (level == null || source == null) {
            return null;
        }
        return level.getCapability(Capabilities.FluidHandler.BLOCK, pipe.getBlockPos().relative(source), source.getOpposite());
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

    @Override
    public void save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt(TAG_TO_EXTRACT, liquidToExtract);
    }

    @Override
    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        liquidToExtract = tag.getInt(TAG_TO_EXTRACT);
    }
}
