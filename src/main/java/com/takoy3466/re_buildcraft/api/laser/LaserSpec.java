package com.takoy3466.re_buildcraft.api.laser;

/**
 * レーザー 1 種類分の性能。
 * 旧実装では {@code TileLaser} のフィールドをサブクラス ({@code CELaserTile}) が書き換えていた値をまとめたもの。
 *
 * @param maxEnergyPerTick    1 tick に照射できる最大エネルギー (旧 useMaxEnergy)
 * @param searchRange         照射対象を探す距離 (ブロック)
 * @param requestStored       対象が無いとき、これ以上蓄えていれば動力を要求しない (旧 requestStored)
 * @param minEnergyReceived   1 回に受け取る最小エネルギー
 * @param maxEnergyReceived   1 回に受け取る最大エネルギー
 * @param minActivationEnergy 動作に必要な最小蓄積エネルギー
 * @param maxEnergyStored     蓄積できる最大エネルギー
 * @param beamWidth           ビームの太さの倍率 (1.0 が標準)
 */
public record LaserSpec(float maxEnergyPerTick, int searchRange, float requestStored,
                        int minEnergyReceived, int maxEnergyReceived, int minActivationEnergy, int maxEnergyStored,
                        float beamWidth) {

    /** BuildCraft 標準のレーザー。 */
    public static final LaserSpec STANDARD = new LaserSpec(4.0F, 5, 200.0F, 25, 25, 25, 1000, 1.0F);
}
