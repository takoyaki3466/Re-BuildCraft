package com.takoy3466.re_buildcraft.builders.blueprint;

import com.mojang.serialization.Dynamic;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.BlockStateData;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * BCx 3.1.5 の設計図ファイル (.bpt) の読み込み。元実装: {@code BptBase.loadBluePrint}、{@code BptTemplate.loadAttribute}、
 * {@code BptBlueprint.loadAttribute}、{@code BptDataStream}。
 * <p>
 * ファイルは ISO-8859-1 のテキストで、{@code 属性:値} の行が並ぶ。テンプレートは {@code mask:} の行に 0/1 をカンマ区切りで持つ。
 * 設計図は {@code idMap:} (ブロックの署名 = 1.2.5 の数値 ID) と {@code contents:} (マスごとに 1 行、NBT を値ごとにカンマで区切った文字列)
 * を持つ。各マスの NBT の {@code bId} / {@code meta} がブロック ID とメタデータ。
 * <p>
 * ブロックの変換規則:
 * <ul>
 *   <li>署名のブロッククラス名が {@code *} (= 旧 BuildCraftAPI.LAST_ORIGINAL_BLOCK 以下のバニラのブロック) の ID だけを変換する。</li>
 *   <li>変換には 1.21.1 に含まれる 1.13 の平坦化の対応表 ({@link BlockStateData}: 1.12 の「ID x 16 + メタデータ」→ ブロックステート) と、
 *       そこから現在までのデータ修正 ({@link DataFixers}) を使う。1.2.5 のバニラのブロック ID は 1.12 まで変わっておらず、
 *       メタデータも同じ意味の下位ビットとして引き継がれているため、この表で解釈する。</li>
 *   <li>Mod のブロック (BC・CE を含む。ID は設定で変わる)、表に無い ID / メタデータの組み合わせは変換できないため、
 *       その設計図は取り込まない (別のブロックに置き換えない)。</li>
 *   <li>マスのブロックエンティティのデータ (旧 cpt の bId / meta 以外) と、記録された必要アイテム (requirements) は、
 *       1.2.5 の形式から変換できないため使わない。必要アイテムは {@link BlueprintRules} が現在のブロックから求め直す。</li>
 * </ul>
 */
public final class LegacyBlueprintReader {
    /** 旧 BuildCraftAPI.LAST_ORIGINAL_BLOCK。これ以下の ID がバニラのブロック。 */
    private static final int LAST_ORIGINAL_BLOCK = 122;
    /** {@link BlockStateData} の表の形式のデータバージョン (1.13 の平坦化, 17w47a)。 */
    private static final int FLATTENING_DATA_VERSION = 1451;
    private static final int META_BITS = 4;
    private static final int META_MASK = 0xF;
    private static final String UNSUPPORTED_VERSION = "3.1.0";

    /** 読み込みの結果。成功なら {@code blueprint}、失敗なら {@code error}。 */
    public record Result(@Nullable Blueprint blueprint, String error) {
        static Result success(Blueprint blueprint) {
            return new Result(blueprint, "");
        }

        static Result failure(String error) {
            return new Result(null, error);
        }
    }

    private LegacyBlueprintReader() {
    }

    public static Result read(Path file, HolderLookup.Provider registries) {
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.ISO_8859_1)) {
            return read(reader, registries);
        } catch (IOException | RuntimeException e) {
            return Result.failure(e.toString());
        }
    }

    static Result read(BufferedReader reader, HolderLookup.Provider registries) throws IOException {
        Map<String, String> header = new HashMap<>();
        String kind = null;
        String line;
        while ((line = reader.readLine()) != null) {
            int colon = line.indexOf(':');
            if (colon < 0) {
                continue;
            }
            String attribute = line.substring(0, colon);
            String value = line.substring(colon + 1);
            switch (attribute) {
                case "kind" -> kind = value;
                case "mask" -> {
                    return "template".equals(kind) ? readTemplate(header, value) : Result.failure("mask in a non-template file");
                }
                case "idMap", "blockMap" -> {
                    if (!"blueprint".equals(kind)) {
                        return Result.failure("idMap in a non-blueprint file");
                    }
                    if (UNSUPPORTED_VERSION.equals(header.get("version"))) {
                        return Result.failure("blueprint format 3.1.0 is not supported (as in BCx)");
                    }
                    return readBlueprint(header, reader, registries);
                }
                default -> header.putIfAbsent(attribute, value);
            }
        }
        return Result.failure("no contents");
    }

    private static Blueprint create(Map<String, String> header, boolean template) {
        int sizeX = Integer.parseInt(header.get("sizeX"));
        int sizeY = Integer.parseInt(header.get("sizeY"));
        int sizeZ = Integer.parseInt(header.get("sizeZ"));
        int anchorX = Integer.parseInt(header.get("anchorX"));
        int anchorY = Integer.parseInt(header.get("anchorY"));
        int anchorZ = Integer.parseInt(header.get("anchorZ"));
        if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) {
            throw new IllegalArgumentException("invalid size");
        }
        Blueprint blueprint = template ? Blueprint.emptyTemplate(sizeX, sizeY, sizeZ, anchorX, anchorY, anchorZ)
                : Blueprint.emptyBlueprint(sizeX, sizeY, sizeZ, anchorX, anchorY, anchorZ);
        blueprint.setName(header.getOrDefault("name", ""));
        blueprint.setAuthor(header.getOrDefault("author", ""));
        return blueprint;
    }

    /** 旧 BptTemplate.loadAttribute: x → y → z の順に、0 以外なら「何かある」。 */
    private static Result readTemplate(Map<String, String> header, String mask) {
        Blueprint blueprint = create(header, true);
        String[] values = mask.split(",");
        if (values.length < blueprint.sizeX() * blueprint.sizeY() * blueprint.sizeZ()) {
            return Result.failure("template mask is too short");
        }
        int index = 0;
        for (int x = 0; x < blueprint.sizeX(); x++) {
            for (int y = 0; y < blueprint.sizeY(); y++) {
                for (int z = 0; z < blueprint.sizeZ(); z++) {
                    blueprint.setFilled(x, y, z, Integer.parseInt(values[index++].trim()) != 0);
                }
            }
        }
        return Result.success(blueprint);
    }

    /** 旧 BptBlueprint.loadAttribute: idMap → contents (requirements は使わない)。 */
    private static Result readBlueprint(Map<String, String> header, BufferedReader reader, HolderLookup.Provider registries)
            throws IOException {
        Map<Integer, String> signatures = new HashMap<>();
        String line;
        while ((line = reader.readLine()) != null && !line.equals(":idMap") && !line.equals(":blockMap")) {
            int equals = line.lastIndexOf('=');
            if (equals > 0) {
                signatures.put(Integer.parseInt(line.substring(equals + 1).trim()), line.substring(0, equals));
            }
        }
        while ((line = reader.readLine()) != null && !line.equals("contents:")) {
            // idMap と contents の間には何も無いはずだが、念のため読み飛ばす。
        }
        if (line == null) {
            return Result.failure("no contents");
        }
        Blueprint blueprint = create(header, false);
        for (int x = 0; x < blueprint.sizeX(); x++) {
            for (int y = 0; y < blueprint.sizeY(); y++) {
                for (int z = 0; z < blueprint.sizeZ(); z++) {
                    line = reader.readLine();
                    if (line == null || line.equals(":contents")) {
                        return Result.success(blueprint);
                    }
                    if (line.isEmpty()) {
                        continue;
                    }
                    CompoundTag cell = LegacyNbt.readNamedCompound(new StringReader(line));
                    int id = cell.getInt("bId");
                    int meta = cell.getInt("meta");
                    BlockState state = convert(id, meta, signatures.get(id), registries);
                    if (state == null) {
                        return Result.failure("cannot convert block id " + id + " meta " + meta
                                + " (" + signatures.getOrDefault(id, "no signature") + ")");
                    }
                    blueprint.setState(x, y, z, state);
                }
            }
        }
        return Result.success(blueprint);
    }

    /** バニラのブロック ID とメタデータを現在のブロックステートへ。変換できなければ null。 */
    @Nullable
    static BlockState convert(int id, int meta, @Nullable String signature, HolderLookup.Provider registries) {
        if (id == 0) {
            return Blocks.AIR.defaultBlockState();
        }
        if (id < 0 || id > LAST_ORIGINAL_BLOCK || signature == null || !isVanillaSignature(signature)
                || meta < 0 || meta > META_MASK) {
            return null;
        }
        @SuppressWarnings("unchecked")
        Dynamic<Tag> flattened = (Dynamic<Tag>) BlockStateData.getTag(id << META_BITS | meta);
        if ("minecraft:air".equals(flattened.get("Name").asString(""))) {
            // 表に無い ID / メタデータの組み合わせ (getTag は空気を返す)。
            return null;
        }
        int current = SharedConstants.getCurrentVersion().getDataVersion().getVersion();
        Dynamic<Tag> upgraded = DataFixers.getDataFixer().update(References.BLOCK_STATE, flattened, FLATTENING_DATA_VERSION, current);
        if (!(upgraded.getValue() instanceof CompoundTag tag)) {
            return null;
        }
        BlockState state = NbtUtils.readBlockState(registries.lookupOrThrow(Registries.BLOCK), tag);
        return state.isAir() ? null : state;
    }

    /** 旧 BlockSignature: {@code #B/クラス名/タイルクラス名/ブロック名/Mod/版/追加}。バニラのブロックはクラス名が {@code *}。 */
    private static boolean isVanillaSignature(String signature) {
        String[] parts = signature.split("/");
        int offset = parts.length > 0 && parts[0].equals("#B") ? 1 : 0;
        return parts.length > offset && parts[offset].equals("*");
    }

    /**
     * 旧 BptDataStream で書かれた NBT の読み込み。値は文字列で、数値はそのまま、文字列は {@code "..."}、各値はカンマで区切られる。
     * 中身は 1.2.5 の NBTBase.writeNamedTag の並び (型番号、名前、値)。
     */
    static final class LegacyNbt {
        private static final int END = 0;
        private static final int BYTE = 1;
        private static final int SHORT = 2;
        private static final int INT = 3;
        private static final int LONG = 4;
        private static final int FLOAT = 5;
        private static final int DOUBLE = 6;
        private static final int BYTE_ARRAY = 7;
        private static final int STRING = 8;
        private static final int LIST = 9;
        private static final int COMPOUND = 10;
        private static final int INT_ARRAY = 11;

        private LegacyNbt() {
        }

        static CompoundTag readNamedCompound(Reader reader) throws IOException {
            int type = (int) readNumber(reader);
            if (type != COMPOUND) {
                throw new IOException("root tag is not a compound: " + type);
            }
            readString(reader);
            return (CompoundTag) readPayload(reader, type);
        }

        private static Tag readPayload(Reader reader, int type) throws IOException {
            return switch (type) {
                case BYTE -> ByteTag.valueOf((byte) readNumber(reader));
                case SHORT -> ShortTag.valueOf((short) readNumber(reader));
                case INT -> IntTag.valueOf((int) readNumber(reader));
                case LONG -> LongTag.valueOf(readNumber(reader));
                case FLOAT -> FloatTag.valueOf((float) readDecimal(reader));
                case DOUBLE -> DoubleTag.valueOf(readDecimal(reader));
                case BYTE_ARRAY -> {
                    // 旧 BptDataStream.write(byte[]) は何も書かないため、長さだけが残る。
                    readNumber(reader);
                    yield new ByteArrayTag(new byte[0]);
                }
                case STRING -> StringTag.valueOf(readString(reader));
                case LIST -> {
                    int elementType = (int) readNumber(reader);
                    int size = (int) readNumber(reader);
                    ListTag list = new ListTag();
                    for (int i = 0; i < size; i++) {
                        list.add(readPayload(reader, elementType));
                    }
                    yield list;
                }
                case COMPOUND -> {
                    CompoundTag compound = new CompoundTag();
                    for (int childType = (int) readNumber(reader); childType != END; childType = (int) readNumber(reader)) {
                        String name = readString(reader);
                        compound.put(name, readPayload(reader, childType));
                    }
                    yield compound;
                }
                case INT_ARRAY -> {
                    int[] values = new int[(int) readNumber(reader)];
                    for (int i = 0; i < values.length; i++) {
                        values[i] = (int) readNumber(reader);
                    }
                    yield new IntArrayTag(values);
                }
                default -> throw new IOException("unknown tag type " + type);
            };
        }

        /** 旧 readLong: 次のカンマ (または終わり) までの数字。 */
        private static long readNumber(Reader reader) throws IOException {
            return Long.parseLong(readToken(reader).trim());
        }

        private static double readDecimal(Reader reader) throws IOException {
            return Double.parseDouble(readToken(reader).trim());
        }

        private static String readToken(Reader reader) throws IOException {
            StringBuilder builder = new StringBuilder();
            for (int c = reader.read(); c >= 0 && c != ','; c = reader.read()) {
                builder.append((char) c);
            }
            return builder.toString();
        }

        /** 旧 readUTF: {@code "} で始まり {@code "} で終わる文字列と、その後のカンマ。{@code \} の次の文字はそのまま使う。 */
        private static String readString(Reader reader) throws IOException {
            if (reader.read() != '"') {
                throw new IOException("string does not start with '\"'");
            }
            StringBuilder builder = new StringBuilder();
            for (int c = reader.read(); c >= 0 && c != '"'; c = reader.read()) {
                builder.append((char) (c == '\\' ? reader.read() : c));
            }
            int next = reader.read();
            if (next >= 0 && next != ',') {
                throw new IOException("missing ',' after a string");
            }
            return builder.toString();
        }
    }
}
