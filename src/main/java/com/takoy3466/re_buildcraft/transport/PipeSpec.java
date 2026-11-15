package com.takoy3466.re_buildcraft.transport;

import com.takoy3466.re_buildcraft.transport.behavior.PipeBehavior;

/**
 * パイプ 1 種類の仕様。{@link PipeBlock} はこれを 1 つ持ち、輸送方式・接続制限・振る舞いはここから決まる。
 * <p>
 * Re-BuildCraft 自身のパイプは列挙型 {@link PipeType} で定義する。アドオン (Additional Pipes など) は
 * この interface を実装した独自の仕様で {@link PipeBlock} を作り、{@code BlockEntityTypeAddBlocksEvent} で
 * {@code re_buildcraft:pipe} のブロックエンティティ型へ自分のブロックを追加することで、
 * 輸送処理・描画・ケーパビリティをそのまま共有できる。
 */
public interface PipeSpec {
    /**
     * レジストリ名 (名前空間を除く)。
     */
    String id();

    PipeKind kind();

    PipeMaterial material();

    /**
     * 取り出し元/出力先の面をブロックステートに持つか (木・鉄)。
     */
    boolean directional();

    /**
     * パイプ 1 本ごとに新しい振る舞いを作る。
     */
    PipeBehavior createBehavior();

    /**
     * 液体パイプの 1 tick あたりの流量。液体以外では使わない。
     */
    int fluidFlowRate();

    /**
     * 同じ輸送方式の {@code other} のパイプを接続相手として受け入れるか。接続は両方が受け入れたときだけ成立する。
     * 既定では素材の組み合わせ ({@link PipeMaterial#accepts}) で決まる。
     */
    default boolean acceptsPipe(PipeSpec other) {
        return material().accepts(other.material(), kind());
    }

    /**
     * パイプ以外のブロック (インベントリ・タンク・機械) とも接続するか。
     * false のパイプはパイプ同士でのみつながる (旧 Additional Pipes のテレポートパイプ)。
     */
    default boolean connectsToNonPipeBlocks() {
        return true;
    }
}
