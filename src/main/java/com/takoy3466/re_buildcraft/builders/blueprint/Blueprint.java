package com.takoy3466.re_buildcraft.builders.blueprint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.capabilities.Capabilities;
import org.jetbrains.annotations.Nullable;

/**
 * 設計図・テンプレートの中身。元実装: {@code buildcraft.core.BptBase} / {@code BptBlueprint} / {@code BptTemplate}。
 * <ul>
 *   <li>テンプレート: マスごとに「何かある / 無い」だけ (旧 mask)。</li>
 *   <li>設計図: マスごとのブロックステートと、必要ならブロックエンティティのデータ。旧実装の「数値 ID + メタデータ」の代わりに、
 *       レジストリ名とプロパティでブロックステートを保存する ({@link #save} / {@link #load})。</li>
 * </ul>
 * 位置は設計図の最小の角からの相対座標で、{@code anchor} は設計台 (ビルダー) の位置。
 */
public final class Blueprint {
    public enum Kind {
        TEMPLATE,
        BLUEPRINT
    }

    private static final String TAG_KIND = "kind";
    private static final String TAG_NAME = "name";
    private static final String TAG_AUTHOR = "author";
    private static final String TAG_SIZE = "size";
    private static final String TAG_ANCHOR = "anchor";
    private static final String TAG_MASK = "mask";
    private static final String TAG_PALETTE = "palette";
    private static final String TAG_BLOCKS = "blocks";
    private static final String TAG_BLOCK_ENTITIES = "block_entities";
    private static final String TAG_INDEX = "index";
    private static final String TAG_DATA = "data";

    private final Kind kind;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final int anchorX;
    private final int anchorY;
    private final int anchorZ;
    /** テンプレートのマス。 */
    private final boolean[] mask;
    /** 設計図のマス (空気を含む)。 */
    private final BlockState[] states;
    /** 設計図のマスのブロックエンティティのデータ (無ければ null)。 */
    private final CompoundTag[] blockEntities;
    private String name;
    private String author;

    private Blueprint(Kind kind, int sizeX, int sizeY, int sizeZ, int anchorX, int anchorY, int anchorZ) {
        this.kind = kind;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        this.anchorZ = anchorZ;
        int volume = sizeX * sizeY * sizeZ;
        this.mask = new boolean[kind == Kind.TEMPLATE ? volume : 0];
        this.states = new BlockState[kind == Kind.BLUEPRINT ? volume : 0];
        this.blockEntities = new CompoundTag[kind == Kind.BLUEPRINT ? volume : 0];
        Arrays.fill(states, Blocks.AIR.defaultBlockState());
        this.name = "";
        this.author = "";
    }

    /** 空のテンプレート (旧形式の読み込み用)。 */
    public static Blueprint emptyTemplate(int sizeX, int sizeY, int sizeZ, int anchorX, int anchorY, int anchorZ) {
        return new Blueprint(Kind.TEMPLATE, sizeX, sizeY, sizeZ, anchorX, anchorY, anchorZ);
    }

    /** 空気だけの設計図 (旧形式の読み込み用)。 */
    public static Blueprint emptyBlueprint(int sizeX, int sizeY, int sizeZ, int anchorX, int anchorY, int anchorZ) {
        return new Blueprint(Kind.BLUEPRINT, sizeX, sizeY, sizeZ, anchorX, anchorY, anchorZ);
    }

    // ---- 作成 (設計台) -----------------------------------------------------------

    /**
     * 範囲を読み取る (旧 TileArchitect.createBptTemplate / createBptBlueprint)。
     *
     * @param anchor   設計台の位置
     * @param inverted テンプレートで、設計台がレッドストーン信号を受けているとき「空いているマス」を記録する (旧 isBlockIndirectlyGettingPowered)
     */
    public static Blueprint capture(Level level, BoundingBox box, BlockPos anchor, Kind kind, boolean inverted) {
        Blueprint blueprint = new Blueprint(kind, box.getXSpan(), box.getYSpan(), box.getZSpan(),
                anchor.getX() - box.minX(), anchor.getY() - box.minY(), anchor.getZ() - box.minZ());
        for (int x = 0; x < blueprint.sizeX; x++) {
            for (int y = 0; y < blueprint.sizeY; y++) {
                for (int z = 0; z < blueprint.sizeZ; z++) {
                    BlockPos pos = new BlockPos(box.minX() + x, box.minY() + y, box.minZ() + z);
                    BlockState state = level.getBlockState(pos);
                    int index = blueprint.index(x, y, z);
                    if (kind == Kind.TEMPLATE) {
                        blueprint.mask[index] = !state.isAir() != inverted;
                    } else {
                        blueprint.states[index] = state;
                        blueprint.blockEntities[index] = captureBlockEntity(level, pos);
                    }
                }
            }
        }
        return blueprint;
    }

    /**
     * ブロックエンティティのデータ。旧 BptBlockInventory は中身を空にして置いていたため、アイテム・液体を持つもの (チェスト・タンクなど)
     * のデータは記録しない (中身の複製を防ぐ)。看板の文字などそれ以外のデータは記録し、置くときに復元する。
     */
    @Nullable
    private static CompoundTag captureBlockEntity(Level level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null || blockEntity instanceof Container
                || level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null) != null
                || level.getCapability(Capabilities.FluidHandler.BLOCK, pos, null) != null) {
            return null;
        }
        return blockEntity.saveCustomOnly(level.registryAccess());
    }

    // ---- 参照 -----------------------------------------------------------------

    private int index(int x, int y, int z) {
        return (x * sizeY + y) * sizeZ + z;
    }

    public Kind kind() {
        return kind;
    }

    public int sizeX() {
        return sizeX;
    }

    public int sizeY() {
        return sizeY;
    }

    public int sizeZ() {
        return sizeZ;
    }

    public int anchorX() {
        return anchorX;
    }

    public int anchorY() {
        return anchorY;
    }

    public int anchorZ() {
        return anchorZ;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String author() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public boolean filled(int x, int y, int z) {
        return mask[index(x, y, z)];
    }

    public void setFilled(int x, int y, int z, boolean filled) {
        mask[index(x, y, z)] = filled;
    }

    public BlockState state(int x, int y, int z) {
        return states[index(x, y, z)];
    }

    public void setState(int x, int y, int z, BlockState state) {
        states[index(x, y, z)] = state;
    }

    @Nullable
    public CompoundTag blockEntity(int x, int y, int z) {
        return blockEntities[index(x, y, z)];
    }

    /** {@code anchorPos} に設計台 (ビルダー) を置いたときの範囲 (旧 getBoxForPos)。 */
    public BoundingBox boxAt(BlockPos anchorPos) {
        int minX = anchorPos.getX() - anchorX;
        int minY = anchorPos.getY() - anchorY;
        int minZ = anchorPos.getZ() - anchorZ;
        return new BoundingBox(minX, minY, minZ, minX + sizeX - 1, minY + sizeY - 1, minZ + sizeZ - 1);
    }

    // ---- 回転 -----------------------------------------------------------------

    /**
     * 上から見て時計回りに 90 度回した設計図 (旧 rotateLeft: 新しい [x][y][z] = 旧 [z][y][sizeZ - 1 - x])。
     * ブロックステートの向き (階段・ドアなど) も同じだけ回す。
     */
    public Blueprint rotateClockwise() {
        Blueprint rotated = new Blueprint(kind, sizeZ, sizeY, sizeX, sizeZ - 1 - anchorZ, anchorY, anchorX);
        rotated.name = name;
        rotated.author = author;
        for (int x = 0; x < rotated.sizeX; x++) {
            for (int y = 0; y < sizeY; y++) {
                for (int z = 0; z < rotated.sizeZ; z++) {
                    int from = index(z, y, sizeZ - 1 - x);
                    int to = rotated.index(x, y, z);
                    if (kind == Kind.TEMPLATE) {
                        rotated.mask[to] = mask[from];
                    } else {
                        rotated.states[to] = states[from].rotate(Rotation.CLOCKWISE_90);
                        rotated.blockEntities[to] = blockEntities[from] == null ? null : blockEntities[from].copy();
                    }
                }
            }
        }
        return rotated;
    }

    /** 時計回りに {@code turns} 回 90 度回した設計図。 */
    public Blueprint rotateClockwise(int turns) {
        Blueprint result = this;
        for (int i = 0; i < Math.floorMod(turns, 4); i++) {
            result = result.rotateClockwise();
        }
        return result;
    }

    /** 中身が同じか (旧 equals: 名前・作者は比べない)。同じ範囲を続けて記録したときに新しい ID を作らないために使う。 */
    public boolean sameContents(@Nullable Blueprint other) {
        return other != null && kind == other.kind && sizeX == other.sizeX && sizeY == other.sizeY && sizeZ == other.sizeZ
                && anchorX == other.anchorX && anchorY == other.anchorY && anchorZ == other.anchorZ
                && Arrays.equals(mask, other.mask) && Arrays.equals(states, other.states)
                && Arrays.equals(blockEntities, other.blockEntities);
    }

    // ---- 保存 -----------------------------------------------------------------

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_KIND, kind.name());
        tag.putString(TAG_NAME, name);
        tag.putString(TAG_AUTHOR, author);
        tag.putIntArray(TAG_SIZE, new int[]{sizeX, sizeY, sizeZ});
        tag.putIntArray(TAG_ANCHOR, new int[]{anchorX, anchorY, anchorZ});
        if (kind == Kind.TEMPLATE) {
            byte[] bytes = new byte[mask.length];
            for (int i = 0; i < mask.length; i++) {
                bytes[i] = (byte) (mask[i] ? 1 : 0);
            }
            tag.putByteArray(TAG_MASK, bytes);
            return tag;
        }
        // ブロックステートは種類ごとに 1 回だけ書き、マスにはその番号を入れる。
        Map<BlockState, Integer> palette = new HashMap<>();
        ListTag paletteTag = new ListTag();
        int[] blocks = new int[states.length];
        for (int i = 0; i < states.length; i++) {
            Integer id = palette.get(states[i]);
            if (id == null) {
                id = palette.size();
                palette.put(states[i], id);
                paletteTag.add(NbtUtils.writeBlockState(states[i]));
            }
            blocks[i] = id;
        }
        tag.put(TAG_PALETTE, paletteTag);
        tag.putIntArray(TAG_BLOCKS, blocks);
        ListTag entities = new ListTag();
        for (int i = 0; i < blockEntities.length; i++) {
            if (blockEntities[i] != null) {
                CompoundTag entry = new CompoundTag();
                entry.putInt(TAG_INDEX, i);
                entry.put(TAG_DATA, blockEntities[i]);
                entities.add(entry);
            }
        }
        tag.put(TAG_BLOCK_ENTITIES, entities);
        return tag;
    }

    /**
     * 保存したデータから読み込む。登録されていないブロックを含む設計図は、別のブロックに置き換えずに読み込みを失敗させる。
     *
     * @return 読み込めなかった場合 null
     */
    @Nullable
    public static Blueprint load(CompoundTag tag, HolderLookup.Provider registries) {
        Kind kind;
        try {
            kind = Kind.valueOf(tag.getString(TAG_KIND));
        } catch (IllegalArgumentException e) {
            return null;
        }
        int[] size = tag.getIntArray(TAG_SIZE);
        int[] anchor = tag.getIntArray(TAG_ANCHOR);
        if (size.length != 3 || anchor.length != 3 || size[0] <= 0 || size[1] <= 0 || size[2] <= 0) {
            return null;
        }
        Blueprint blueprint = new Blueprint(kind, size[0], size[1], size[2], anchor[0], anchor[1], anchor[2]);
        blueprint.name = tag.getString(TAG_NAME);
        blueprint.author = tag.getString(TAG_AUTHOR);
        if (kind == Kind.TEMPLATE) {
            byte[] bytes = tag.getByteArray(TAG_MASK);
            if (bytes.length != blueprint.mask.length) {
                return null;
            }
            for (int i = 0; i < bytes.length; i++) {
                blueprint.mask[i] = bytes[i] != 0;
            }
            return blueprint;
        }
        HolderGetter<Block> blockLookup = registries.lookupOrThrow(Registries.BLOCK);
        ListTag paletteTag = tag.getList(TAG_PALETTE, Tag.TAG_COMPOUND);
        List<BlockState> palette = new ArrayList<>(paletteTag.size());
        for (int i = 0; i < paletteTag.size(); i++) {
            CompoundTag entry = paletteTag.getCompound(i);
            BlockState state = NbtUtils.readBlockState(blockLookup, entry);
            // readBlockState は未知のブロックを空気にする。空気でないはずのものが空気になったら読み込みを失敗させる。
            if (state.isAir() && !Objects.equals(entry.getString("Name"), "minecraft:air")) {
                return null;
            }
            palette.add(state);
        }
        int[] blocks = tag.getIntArray(TAG_BLOCKS);
        if (blocks.length != blueprint.states.length) {
            return null;
        }
        for (int i = 0; i < blocks.length; i++) {
            if (blocks[i] < 0 || blocks[i] >= palette.size()) {
                return null;
            }
            blueprint.states[i] = palette.get(blocks[i]);
        }
        ListTag entities = tag.getList(TAG_BLOCK_ENTITIES, Tag.TAG_COMPOUND);
        for (int i = 0; i < entities.size(); i++) {
            CompoundTag entry = entities.getCompound(i);
            int index = entry.getInt(TAG_INDEX);
            if (index >= 0 && index < blueprint.blockEntities.length) {
                blueprint.blockEntities[index] = entry.getCompound(TAG_DATA);
            }
        }
        return blueprint;
    }
}
