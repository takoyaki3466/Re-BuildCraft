package com.takoy3466.re_buildcraft.core;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;

/**
 * BC レンチ。機械側が {@link BuildCraftTags#WRENCHES} を見て向きを変える。
 * 元実装: {@code BuildCraftCore.wrenchItem} (専用クラス無し)。
 */
public class WrenchItem extends Item {

    public WrenchItem(Properties properties) {
        super(properties);
    }

    /**
     * スニーク中でもブロック側の右クリック処理を呼ばせる。
     * エンジンはスニーク + レンチで逆順に向きを変える (旧 {@code switchOrientationShift})。
     */
    @Override
    public boolean doesSneakBypassUse(ItemStack stack, LevelReader level, BlockPos pos, Player player) {
        return true;
    }
}
