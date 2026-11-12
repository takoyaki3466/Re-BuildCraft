package com.takoy3466.re_buildcraft.api.power;

/**
 * MJ を中継する導電パイプの状態。元の CE の計測器は {@code PipeTransportPower.internalPower / internalNextPower} の
 * 最大値を「流量」として表示していたため、その値を公開する。
 * {@link BuildCraftCapabilities#POWER_CONDUIT} で取得する。
 */
public interface IPowerConduit {

    /** いずれかの面に今 tick / 次 tick に流れている MJ の最大値。 */
    double getPowerLoad();

    /** 1 面にこれ以上流れ込むとパイプが壊れる量。 */
    double getMaxPowerLoad();
}
