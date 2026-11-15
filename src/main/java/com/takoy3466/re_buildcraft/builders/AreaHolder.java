package com.takoy3466.re_buildcraft.builders;

import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

/** 作業範囲をしま模様のレーザーで示すビルダー系の機械 (旧 Box.createLasers(LaserKind.Stripes))。 */
public interface AreaHolder {
    /** 表示する範囲。無ければ null。 */
    @Nullable
    BoundingBox getBox();
}
