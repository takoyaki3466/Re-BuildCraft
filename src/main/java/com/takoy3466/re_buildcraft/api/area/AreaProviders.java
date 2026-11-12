package com.takoy3466.re_buildcraft.api.area;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** 範囲提供者の探索。元実装: {@code buildcraft.core.Utils.getNearbyAreaProvider}。 */
public final class AreaProviders {
    /** 旧実装の探索順 (x+1, x-1, z+1, z-1, y+1, y-1)。 */
    private static final Direction[] SEARCH_ORDER = {
            Direction.EAST, Direction.WEST, Direction.SOUTH, Direction.NORTH, Direction.UP, Direction.DOWN};

    private AreaProviders() {
    }

    /** {@code pos} に隣接する範囲提供者。無ければ null。 */
    @Nullable
    public static IAreaProvider findNearby(Level level, BlockPos pos) {
        for (Direction direction : SEARCH_ORDER) {
            if (level.getBlockEntity(pos.relative(direction)) instanceof IAreaProvider provider) {
                return provider;
            }
        }
        return null;
    }
}
