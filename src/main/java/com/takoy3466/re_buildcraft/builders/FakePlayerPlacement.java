package com.takoy3466.re_buildcraft.builders;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/**
 * 機械が偽プレイヤーにアイテムを「使わせる」処理 (旧 BuildCraftAPI.getBuildCraftPlayer + Item.onItemUse)。
 * フィラーのパターンとビルダーのテンプレート建築が使う。
 */
public final class FakePlayerPlacement {
    private FakePlayerPlacement() {
    }

    public static FakePlayer player(ServerLevel level) {
        return FakePlayerFactory.getMinecraft(level);
    }

    /**
     * {@code pos} にアイテムを使う。ブロックなら {@code pos} そのものに置き、それ以外のアイテムは {@code pos} の {@code face} 面に使う。
     * 使った分はスタックから減る。
     */
    public static InteractionResult useAt(ServerLevel level, BlockPos pos, Direction face, ItemStack stack) {
        if (stack.isEmpty()) {
            return InteractionResult.FAIL;
        }
        FakePlayer player = player(level);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        try {
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), face, pos, false);
            if (stack.getItem() instanceof BlockItem blockItem) {
                return blockItem.place(new BlockPlaceContext(level, player, InteractionHand.MAIN_HAND, stack, hit));
            }
            return stack.useOn(new UseOnContext(level, player, InteractionHand.MAIN_HAND, stack, hit));
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }
    }
}
