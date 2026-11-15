package com.takoy3466.re_buildcraft.builders.filler;

/**
 * フィラーの作業位置と、パターンが使う作業用の値。元実装: {@code TileFiller} の public フィールド
 * (cx/cy/cz 現在位置、sx/sy/sz 開始位置、ex/ey/ez 終了位置、mx/my/mz 移動方向、mode、円の計算用の値、pattern)。
 * <p>
 * 旧実装と同じく保存しない。読み込み後はパターンの初期化からやり直し、済んだ場所は判定で飛ばされる。
 */
public final class FillerCursor {
    public int cx;
    public int cy;
    public int cz;
    public int sx;
    public int sy;
    public int sz;
    public int ex;
    public int ey;
    public int ez;
    public int mx;
    public int my;
    public int mz;
    public int mode;
    public int diameter;
    public int radius;
    public int radius2;
    public int nx;
    public int nz;
    /** 円柱消去で使う、XZ 平面の消去対象。 */
    public boolean[][] pattern = new boolean[0][0];
    /** 階段設置で使う、偽プレイヤーの向き (度)。 */
    public float playerYaw;
    /**
     * 直近の 1 回の作業で、ワールドを実際に変えたか (置いた・壊した・消した・液体を置いた)。
     * 作業の前に false に戻し、{@link FillerPattern} の処理が成功したときだけ true にする。
     */
    public boolean acted;

    /** 作業位置と作業用の値。1 回の作業の前後で比べ、位置が進んだかを判定する。 */
    public int[] progressKey() {
        return new int[]{cx, cy, cz, sx, sy, sz, ex, ey, ez, mx, my, mz, mode};
    }
}
