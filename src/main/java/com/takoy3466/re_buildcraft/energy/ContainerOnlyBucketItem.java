package com.takoy3466.re_buildcraft.energy;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;

/**
 * ワールドへ設置できない液体のバケツ (金オイルバケツ)。
 * <p>
 * 旧実装の fuel はブロックを持たない「アイテムとしての液体」で、バケツは単なるアイテムだった。
 * タンクやエンジンとは液体ケーパビリティ経由でやり取りできるよう {@link BucketItem} を継承しつつ、
 * 右クリックでの設置だけを無効にする。
 */
public class ContainerOnlyBucketItem extends BucketItem {

    public ContainerOnlyBucketItem(Fluid content, Properties properties) {
        super(content, properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }
}
