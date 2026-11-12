package com.takoy3466.re_buildcraft.transport;

/**
 * パイプの素材。パイプ同士の接続制限を決める (元実装の各 {@code PipeLogic*.isPipeConnected})。
 * <ul>
 *   <li>木同士はつながらない (PipeLogicWood)</li>
 *   <li>丸石と石はつながらない (PipeLogicCobblestone / PipeLogicStone)</li>
 *   <li>黒曜石とストライプは、黒曜石・ストライプのどちらともつながらない (PipeLogicObsidian / PipeLogicStripes)</li>
 *   <li>金の輸送パイプ同士はつながらない (PipeItemsGold。液体・動力の金パイプには無い制限)</li>
 * </ul>
 */
public enum PipeMaterial {
    WOOD,
    COBBLESTONE,
    STONE,
    IRON,
    GOLD,
    DIAMOND,
    OBSIDIAN,
    STRIPES,
    VOID;

    /**
     * この素材のパイプが {@code other} のパイプを接続相手として受け入れるか。
     *
     * @param kind このパイプの輸送方式
     */
    public boolean accepts(PipeMaterial other, PipeKind kind) {
        return switch (this) {
            case WOOD -> other != WOOD;
            case COBBLESTONE -> other != STONE;
            case STONE -> other != COBBLESTONE;
            case OBSIDIAN, STRIPES -> other != OBSIDIAN && other != STRIPES;
            case GOLD -> kind != PipeKind.ITEMS || other != GOLD;
            default -> true;
        };
    }
}
