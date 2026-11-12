package com.takoy3466.re_buildcraft.api.transport;

import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;

/**
 * 機械からアイテムを受け取れるパイプ。元実装: {@code buildcraft.api.IPipeEntry}。
 * 機械は隣接インベントリへの搬入に失敗したとき、この入口へアイテムを流し込む。
 */
public interface IPipeEntry {

    /** アイテムを受け取れる輸送方式か。 */
    boolean acceptsItems();

    /**
     * @param stack   流し込むアイテム (呼び出し側はこのスタックを以後使わない)
     * @param heading アイテムが進む向き (機械からパイプへ向かう方向)
     */
    void injectItem(ItemStack stack, Direction heading);
}
