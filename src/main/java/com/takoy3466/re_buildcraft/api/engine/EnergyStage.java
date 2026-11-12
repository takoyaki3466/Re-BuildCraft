package com.takoy3466.re_buildcraft.api.engine;

/** エンジンの熱段階。元実装: {@code buildcraft.energy.Engine.EnergyStage}。 */
public enum EnergyStage {
    BLUE,
    GREEN,
    YELLOW,
    RED,
    EXPLOSION;

    private static final EnergyStage[] VALUES = values();

    /** 同期用の序数から復元する。範囲外は {@link #BLUE}。 */
    public static EnergyStage byOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : BLUE;
    }
}
