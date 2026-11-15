package com.takoy3466.re_buildcraft.builders;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * 緑マーカーのブロック。元実装: {@code BlockPathMarker} (BlockMarker を継承)。
 * 形・設置・右クリック・信号の扱いは青マーカーと同じで、つなぎ方だけがブロックエンティティ ({@link PathMarkerBlockEntity}) で異なる。
 */
public class PathMarkerBlock extends MarkerBlock {
    public PathMarkerBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PathMarkerBlockEntity(pos, state);
    }
}
