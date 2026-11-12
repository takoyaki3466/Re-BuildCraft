package com.takoy3466.re_buildcraft.transport.gate;

import com.takoy3466.re_buildcraft.api.MachineControl;
import com.takoy3466.re_buildcraft.transport.PipeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * ゲートで選べる条件 (トリガー) と動作 (アクション) の共通部分。元実装: {@code buildcraft.api.Trigger} / {@code Action}。
 */
public sealed interface GateStatement permits GateStatement.Trigger, GateStatement.Action {
    String TRANSLATION_PREFIX = "gate.re_buildcraft.";

    /** 保存と翻訳キーに使う識別子。 */
    String id();

    /** {@code trigger_textures.png} (16x16 区画) の番号 (旧 getIndexInTexture)。 */
    int iconIndex();

    default Component description() {
        return Component.translatable(TRANSLATION_PREFIX + id());
    }

    /** トリガー。 */
    sealed interface Trigger extends GateStatement permits NeighborTrigger, PipeTrigger {
        /** パラメーター (アイテム指定) を受け付けるか。 */
        boolean hasParameter();
    }

    /**
     * 隣接ブロックの状態を見るトリガー。ゲートは自分の周囲のパイプ以外のブロックすべてについて評価し、どれか 1 つで成り立てば有効
     * (旧 Pipe.isNearbyTriggerActive)。
     */
    non-sealed interface NeighborTrigger extends Trigger {
        /**
         * @param side      隣接ブロックから見たパイプの方向
         * @param parameter パラメーター (無ければ空)
         */
        boolean isActive(Level level, BlockPos neighborPos, Direction side, ItemStack parameter);
    }

    /** パイプ自身の状態を見るトリガー (旧 ITriggerPipe)。 */
    non-sealed interface PipeTrigger extends Trigger {
        boolean isActive(PipeBlockEntity pipe, ItemStack parameter);
    }

    /** アクション。 */
    sealed interface Action extends GateStatement permits RedstoneOutputAction, SignalOutputAction, MachineControlAction {
    }

    /** ゲートからレッドストーン信号を出す (旧 ActionRedstoneOutput)。 */
    record RedstoneOutputAction(String id, int iconIndex) implements Action {
    }

    /** ゲートからワイヤーへ信号を出す (旧 ActionSignalOutput)。 */
    record SignalOutputAction(String id, int iconIndex, WireColor color) implements Action {
    }

    /** 隣接する機械へ運転指示を送る (旧 ActionMachineControl)。 */
    record MachineControlAction(String id, int iconIndex, MachineControl control) implements Action {
    }
}
