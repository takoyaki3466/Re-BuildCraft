package com.takoy3466.re_buildcraft.api;

/**
 * ゲートのアクションを受け取る機械。元実装: {@code buildcraft.api.IActionReceptor}。
 * <p>
 * ゲートは、トリガーが成り立っている運転指示アクションを、そのパイプに隣接するこのインターフェースのブロックエンティティすべてへ
 * 評価のたびに送る (旧 Pipe.resolveActions)。指示が届かなくなっても直前の指示を保持するかどうかは機械側が決める。
 * ゲートの画面に運転指示アクションを並べるには、隣接する機械が {@link IMachine#allowActions()} で true を返す必要がある。
 */
public interface IActionReceptor {
    void actionActivated(MachineControl control);
}
