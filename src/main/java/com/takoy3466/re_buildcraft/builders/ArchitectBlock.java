package com.takoy3466.re_buildcraft.builders;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** 設計台のブロック。元実装: {@code BlockArchitect}。 */
public class ArchitectBlock extends RotatableMachineBlock {
    public ArchitectBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ArchitectBlockEntity(pos, state);
    }
}
