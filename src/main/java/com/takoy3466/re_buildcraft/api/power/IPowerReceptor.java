package com.takoy3466.re_buildcraft.api.power;

/**
 * MJ (BuildCraft の動力) を受け取れるブロックエンティティの契約。
 * 元実装: {@code buildcraft.api.IPowerReceptor}。
 * <p>
 * 他 Mod からは {@link BuildCraftCapabilities#POWER_RECEPTOR} 経由で取得する。
 */
public interface IPowerReceptor {

    /** 受け取ったエネルギーを蓄えるバッファ。null を返してはならない。 */
    PowerProvider getPowerProvider();

    /**
     * 蓄積エネルギーが {@link PowerProvider#getMinActivationEnergy()} 以上のときに
     * {@link PowerProvider#update} から呼ばれる。
     */
    void doWork();

    /** 現在要求しているエネルギー量 (MJ)。不要なら 0。 */
    int powerRequest();
}
