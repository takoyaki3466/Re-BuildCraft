package com.takoy3466.re_buildcraft.builders;

import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 旧 BuildCraftAPI.softBlock (空気・草・雪・水・オイルなど、上書きしてよいブロック)。フィラー・ビルダー・建設ロボットが使う。
 * 1.21.1 では「空気、または置き換え可能で溶岩でないブロック」とする (旧実装でも溶岩は柔らかいブロックに含まれない)。
 */
public final class SoftBlocks {
    private SoftBlocks() {
    }

    public static boolean isSoft(BlockState state) {
        return state.isAir() || state.canBeReplaced() && !state.getFluidState().is(FluidTags.LAVA);
    }
}
