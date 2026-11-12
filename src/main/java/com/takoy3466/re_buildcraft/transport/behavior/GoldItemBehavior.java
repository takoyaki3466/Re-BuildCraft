package com.takoy3466.re_buildcraft.transport.behavior;

import com.takoy3466.re_buildcraft.transport.PipeBlockEntity;
import com.takoy3466.re_buildcraft.transport.TravelingItem;
import net.minecraft.world.level.Level;

/**
 * 金の輸送パイプ。レッドストーン信号を受けていない間、通るアイテムを通常の 20 倍の速さにする。
 * 元実装: {@code buildcraft.transport.pipes.PipeItemsGold.entityEntered}。
 */
public class GoldItemBehavior implements PipeBehavior {
    private static final float SPEED_MULTIPLIER = 20.0F;

    @Override
    public void onItemEntered(PipeBlockEntity pipe, TravelingItem item) {
        Level level = pipe.getLevel();
        if (level != null && !level.hasNeighborSignal(pipe.getBlockPos())) {
            item.setSpeed(TravelingItem.NORMAL_SPEED * SPEED_MULTIPLIER);
        }
    }
}
