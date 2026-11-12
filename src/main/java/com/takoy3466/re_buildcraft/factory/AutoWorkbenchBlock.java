package com.takoy3466.re_buildcraft.factory;

import com.takoy3466.re_buildcraft.transport.PipeBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * 自動作業台のブロック。元実装: {@code buildcraft.factory.BlockAutoWorkbench}。
 * パイプを持って右クリックした場合は GUI を開かず、パイプを設置できるようにする。
 */
public class AutoWorkbenchBlock extends Block implements EntityBlock {
    public AutoWorkbenchBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AutoWorkbenchBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (player.getMainHandItem().getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof PipeBlock) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof AutoWorkbenchBlockEntity workbench) {
            serverPlayer.openMenu(workbench, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof AutoWorkbenchBlockEntity workbench) {
            workbench.dropContents();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
