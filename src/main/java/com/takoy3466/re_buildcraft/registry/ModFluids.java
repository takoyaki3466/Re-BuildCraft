package com.takoy3466.re_buildcraft.registry;

import com.takoy3466.re_buildcraft.ReBuildcraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * 黒オイル (oil) と金オイル (fuel) の液体登録。
 * 元実装: {@code BuildCraftEnergy} の oilStill / oilMoving / fuel。
 * <p>
 * 旧実装と同じく、oil はワールドに置ける液体ブロックを持ち、fuel は容器とタンクの中だけに存在する。
 */
public final class ModFluids {
    public static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, ReBuildcraft.MODID);
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, ReBuildcraft.MODID);

    public static final DeferredHolder<FluidType, FluidType> OIL_TYPE =
            FLUID_TYPES.register("oil", () -> new FluidType(FluidType.Properties.create()));
    public static final DeferredHolder<FluidType, FluidType> FUEL_TYPE =
            FLUID_TYPES.register("fuel", () -> new FluidType(FluidType.Properties.create()));

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> OIL =
            FLUIDS.register("oil", () -> new BaseFlowingFluid.Source(oilProperties()));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> FLOWING_OIL =
            FLUIDS.register("flowing_oil", () -> new BaseFlowingFluid.Flowing(oilProperties()));

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> FUEL =
            FLUIDS.register("fuel", () -> new BaseFlowingFluid.Source(fuelProperties()));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> FLOWING_FUEL =
            FLUIDS.register("flowing_fuel", () -> new BaseFlowingFluid.Flowing(fuelProperties()));

    private ModFluids() {
    }

    private static BaseFlowingFluid.Properties oilProperties() {
        return new BaseFlowingFluid.Properties(OIL_TYPE, OIL, FLOWING_OIL)
                .bucket(ModItems.OIL_BUCKET)
                .block(ModBlocks.OIL);
    }

    private static BaseFlowingFluid.Properties fuelProperties() {
        // 液体ブロックを持たないため、ワールドには設置できない。
        return new BaseFlowingFluid.Properties(FUEL_TYPE, FUEL, FLOWING_FUEL).bucket(ModItems.FUEL_BUCKET);
    }

    public static void register(IEventBus modEventBus) {
        FLUID_TYPES.register(modEventBus);
        FLUIDS.register(modEventBus);
    }
}
