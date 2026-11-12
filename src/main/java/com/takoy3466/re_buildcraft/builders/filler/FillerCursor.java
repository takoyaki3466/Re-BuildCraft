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
}
