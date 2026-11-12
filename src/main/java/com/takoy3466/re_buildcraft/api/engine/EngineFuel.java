package com.takoy3466.re_buildcraft.api.engine;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.takoy3466.re_buildcraft.ReBuildcraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import org.jetbrains.annotations.Nullable;

/**
 * 鉄エンジン (Combustion Engine) が燃やせる液体 1 種類分の性能。
 * 元実装: {@code buildcraft.api.IronEngineFuel} と {@code BuildCraftAPI.ironEngineFuel}。
 * <p>
 * 旧 API は static な Map への登録だったが、1.21.1 ではデータマップ
 * ({@code data/re_buildcraft/data_maps/fluid/engine_fuel.json}) として定義する。
 * アドオンやデータパックは同じパスの JSON を追加するだけで燃料を増やせる。
 *
 * @param powerPerCycle    1 tick の燃焼で発生する MJ
 * @param totalBurningTime 1 バケツ (1000 mB) が燃え続ける tick 数
 */
public record EngineFuel(int powerPerCycle, int totalBurningTime) {
    public static final Codec<EngineFuel> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.intRange(0, Integer.MAX_VALUE).fieldOf("power_per_cycle").forGetter(EngineFuel::powerPerCycle),
            Codec.intRange(1, Integer.MAX_VALUE).fieldOf("total_burning_time").forGetter(EngineFuel::totalBurningTime)
    ).apply(instance, EngineFuel::new));

    public static final DataMapType<Fluid, EngineFuel> DATA_MAP =
            DataMapType.builder(ReBuildcraft.id("engine_fuel"), Registries.FLUID, CODEC).synced(CODEC, true).build();

    /** 指定した液体が燃料として登録されていればその性能を返す。 */
    @Nullable
    public static EngineFuel of(Fluid fluid) {
        return fluid.builtInRegistryHolder().getData(DATA_MAP);
    }
}
