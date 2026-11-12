package com.takoy3466.re_buildcraft.api.area;

import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * 直方体の範囲を提供するブロックエンティティ (マーカーなど)。元実装: {@code buildcraft.api.IAreaProvider}。
 * クアーリーやフィラーは、隣に置かれたこのブロックエンティティから作業範囲を受け取る ({@link AreaProviders#findNearby})。
 */
public interface IAreaProvider {
    /** 両端を含む範囲。 */
    BoundingBox getArea();

    /** 範囲の指定に使ったもの (マーカー) をワールドから取り除く。 */
    void removeFromWorld();
}
