package com.takoy3466.re_buildcraft.api.laser;

/**
 * レーザーのエネルギーを受け取れるブロックエンティティ (組立台など)。
 * 旧実装ではレーザーが {@code TileAssemblyTable} を直接参照していた部分を契約として切り出したもの。
 */
public interface ILaserTarget {

    /** 現在レーザーの照射を必要としているか (旧 {@code currentRecipe != null})。 */
    boolean requiresLaserEnergy();

    void receiveLaserEnergy(float energy);
}
