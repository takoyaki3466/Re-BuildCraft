package com.takoy3466.re_buildcraft.registry;

import com.takoy3466.re_buildcraft.ReBuildcraft;
import com.takoy3466.re_buildcraft.energy.worldgen.OilDepositFeature;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * ワールド生成の地物。配置は {@code data/re_buildcraft/worldgen} と
 * {@code data/re_buildcraft/neoforge/biome_modifier} の JSON で行う。
 */
public final class ModFeatures {
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, ReBuildcraft.MODID);

    public static final DeferredHolder<Feature<?>, OilDepositFeature> OIL_DEPOSIT = FEATURES.register("oil_deposit", () -> new OilDepositFeature(NoneFeatureConfiguration.CODEC));

    private ModFeatures() {
    }

    public static void register(IEventBus modEventBus) {
        FEATURES.register(modEventBus);
    }
}
