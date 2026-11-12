package com.takoy3466.re_buildcraft;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * BCx の {@code config/buildcraft.cfg} のうち、移行済み機能が実際に参照する項目だけを定義する。
 * <p>
 * ブロック ID / アイテム ID の項目は 1.21.1 ではレジストリ名に置き換わるため存在しない。
 */
public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    /** BCx "enginePowerUp": 石エンジンと鉄エンジンの出力 MJ を引き上げる。 */
    public static final ModConfigSpec.BooleanValue ENGINE_POWER_UP = BUILDER
            .comment("Power up the output MJ of the Steam (stone) engine x2 and the Combustion (iron) engine x3.")
            .define("enginePowerUp", false);

    /** BuildCraftCore.consumeWaterSources: ポンプが水源ブロックを消費するかどうか。 */
    public static final ModConfigSpec.BooleanValue CONSUME_WATER_SOURCES = BUILDER
            .comment("Set to false if the pump should not remove the water source blocks it pumps.")
            .define("consumeWaterSources", true);

    /** BuildCraftTransport "pipes.alwaysConnect": 素材によるパイプ同士の接続制限を無効にする。 */
    public static final ModConfigSpec.BooleanValue PIPES_ALWAYS_CONNECT = BUILDER
            .comment("Set to true to let all pipes of the same kind connect regardless of material (wood-wood, stone-cobblestone ...).")
            .define("pipesAlwaysConnect", false);

    /** BuildCraftTransport "pipes.groupItemsTrigger": パイプ内のアイテム数がこれを超えると同種のアイテムをまとめる。 */
    public static final ModConfigSpec.IntValue PIPE_GROUP_ITEMS_TRIGGER = BUILDER
            .comment("When a pipe contains more items than this, identical items are merged into stacks.")
            .defineInRange("pipeGroupItemsTrigger", 32, 1, 10000);

    /** BuildCraftTransport "pipes.maxItems": パイプ内のアイテム数がこれを超えるとパイプが壊れる。 */
    public static final ModConfigSpec.IntValue PIPE_MAX_ITEMS = BUILDER
            .comment("A pipe holding more items than this explodes.")
            .defineInRange("pipeMaxItems", 100, 1, 10000);

    /** BuildCraftCore "modifyWorld": 石油などのワールド生成を行うかどうか。 */
    public static final ModConfigSpec.BooleanValue MODIFY_WORLD = BUILDER
            .comment("Set to false to stop BuildCraft from generating oil in new chunks.")
            .define("modifyWorld", true);

    /** BCx "MarkerMaxRange": マーカー同士をつなげる最大距離。 */
    public static final ModConfigSpec.IntValue MARKER_MAX_RANGE = BUILDER
            .comment("Maximum distance between two markers that can be linked (BCx MarkerMaxRange).")
            .defineInRange("markerMaxRange", 256, 64, 65536);

    /** BCx "BlueMarkerRange": 信号を受けたマーカーの目安線の長さ (0=16, 1=64, 2=128, 3=256)。 */
    public static final ModConfigSpec.IntValue MARKER_SIGNAL_RANGE = BUILDER
            .comment("Length of the guide lines of a powered marker: 0=16, 1=64, 2=128, 3=256 (BCx BlueMarkerRange).")
            .defineInRange("markerSignalRange", 2, 0, 3);

    /** BCx "BlueMarkerVerticalLine": 目安線の区切りに縦線を描くか。 */
    public static final ModConfigSpec.BooleanValue MARKER_VERTICAL_LINE = BUILDER
            .comment("Draw vertical lines at each step of the marker guide lines (BCx BlueMarkerVerticalLine).")
            .define("markerVerticalLine", true);

    /** BCx "fillingLava": フィラーが溶岩も埋める対象にするか。 */
    public static final ModConfigSpec.BooleanValue FILLING_LAVA = BUILDER
            .comment("Set to true to let the filler fill lava as well (BCx fillingLava).")
            .define("fillingLava", false);

    /** BCx "fillerEndPortalErase": フィラーがエンドポータル (枠) も消すか。 */
    public static final ModConfigSpec.BooleanValue FILLER_ERASE_END_PORTAL = BUILDER
            .comment("Set to true to let the filler erase end portals and end portal frames (BCx fillerEndPortalErase).")
            .define("fillerEndPortalErase", false);

    /** BCx "intervalTorch": 松明設置モジュールの間隔。 */
    public static final ModConfigSpec.IntValue FILLER_TORCH_INTERVAL = BUILDER
            .comment("Interval of torches placed by the torch filler module (BCx intervalTorch).")
            .defineInRange("fillerTorchInterval", 6, 2, 64);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {
    }
}
