package com.takoy3466.re_buildcraft.api.transport;

import net.minecraft.core.Direction;

/**
 * パイプとの接続可否を自分で決めるブロックエンティティ。元実装: {@code buildcraft.api.IPipeConnection}。
 * 実装していないブロックエンティティは、輸送方式ごとの既定の判定 (ケーパビリティの有無など) で接続される。
 */
public interface IPipeConnectable {

    /** @param side このブロックから見たパイプの方向 */
    boolean isPipeConnected(Direction side);
}
