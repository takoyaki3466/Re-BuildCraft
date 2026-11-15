package com.takoy3466.re_buildcraft.builders;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** ビルダーのブロック。元実装: {@code BlockBuilder}。 */
public class BuilderBlock extends RotatableMachineBlock {
    public BuilderBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BuilderBlockEntity(pos, state);
    }
}
