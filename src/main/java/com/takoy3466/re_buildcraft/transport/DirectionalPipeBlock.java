package com.takoy3466.re_buildcraft.transport;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * 1 つの面に特別な役割を持つパイプ (木: 取り出し元, 鉄: 出力先)。その面を {@link #SPECIAL} として持ち、
 * モデルではその面の腕だけ別のテクスチャで描く (旧実装はメタデータとテクスチャ差し替え)。
 */
public class DirectionalPipeBlock extends PipeBlock {
    public static final EnumProperty<PipeSide> SPECIAL = EnumProperty.create("special", PipeSide.class);

    public DirectionalPipeBlock(Properties properties, PipeType pipeType) {
        super(properties, pipeType);
        registerDefaultState(defaultBlockState().setValue(SPECIAL, PipeSide.NONE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(SPECIAL);
    }
}
