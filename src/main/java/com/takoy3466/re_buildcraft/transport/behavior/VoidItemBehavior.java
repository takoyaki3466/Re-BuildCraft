package com.takoy3466.re_buildcraft.transport.behavior;

import com.takoy3466.re_buildcraft.transport.PipeBlockEntity;
import com.takoy3466.re_buildcraft.transport.TravelingItem;

/**
 * 虚空パイプ。中心に着いたアイテムを消す。元実装: {@code addClass.PipeItemsVoid} (centerReached でアイテムを除去)。
 */
public class VoidItemBehavior implements PipeBehavior {

    @Override
    public boolean consumeAtCenter(PipeBlockEntity pipe, TravelingItem item) {
        return true;
    }
}
