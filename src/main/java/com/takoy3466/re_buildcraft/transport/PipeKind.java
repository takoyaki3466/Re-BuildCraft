package com.takoy3466.re_buildcraft.transport;

/**
 * パイプの輸送方式。元実装の {@code PipeTransport} のサブクラス (Items / Liquids / Power / Structure) に対応する。
 * 異なる方式のパイプ同士は接続しない (旧 {@code PipeTransport.allowsConnect})。
 */
public enum PipeKind {
    ITEMS,
    FLUIDS,
    POWER,
    STRUCTURE
}
