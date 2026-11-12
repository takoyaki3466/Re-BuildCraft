package com.takoy3466.re_buildcraft.api.util;

import net.minecraft.world.level.Level;

/**
 * 「前回の実行から一定 tick 経過したか」を判定する小さなタイマー。
 * 元実装: {@code buildcraft.api.SafeTimeTracker}。
 */
public final class SafeTimeTracker {
    private long lastMark;

    /**
     * 前回マークしてから {@code delay} tick 以上経過していればマークを更新して true を返す。
     * ワールド時刻が巻き戻った場合 (時刻変更コマンド等) は基準を取り直すだけで false を返す。
     */
    public boolean markTimeIfDelay(Level level, long delay) {
        long currentTime = level.getGameTime();
        if (currentTime < lastMark) {
            lastMark = currentTime;
            return false;
        }
        if (lastMark + delay <= currentTime) {
            lastMark = currentTime;
            return true;
        }
        return false;
    }
}
