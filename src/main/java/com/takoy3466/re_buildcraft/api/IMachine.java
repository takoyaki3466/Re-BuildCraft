package com.takoy3466.re_buildcraft.api;

/**
 * 稼働状態を持つ機械。元実装: {@code buildcraft.core.IMachine}。
 * ゲートのトリガー「作業中/作業完了」と、パイプの接続判定に使われる。
 */
public interface IMachine {

    boolean isActive();

    /** 液体パイプを接続させるか。 */
    default boolean manageLiquids() {
        return false;
    }

    /** アイテムパイプを接続させるか。 */
    default boolean manageSolids() {
        return false;
    }

    /**
     * ゲートの運転指示アクション (On / Off / Loop) を受け付けるか。元実装: {@code IMachine.allowActions} と
     * {@code DefaultActionProvider}。true にする機械は {@link IActionReceptor} も実装する。
     */
    default boolean allowActions() {
        return false;
    }
}
