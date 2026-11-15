package com.takoy3466.re_buildcraft.builders;

import com.takoy3466.re_buildcraft.registry.ModBlockEntities;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;

/**
 * 緑マーカー (パスマーカー)。元実装: {@code buildcraft.builders.TilePathMarker} (TileMarker を継承)。
 * <ul>
 *   <li>右クリックで、半径 64 (立方体) の中で最も近い、まだつながり切っていない別の緑マーカーとつなぐ。1 個につき最大 2 本 (旧 tryConnection)。</li>
 *   <li>つながった線をたどった順の位置の並びが、ビルダーの経路になる (旧 getPath)。</li>
 *   <li>壊すと相手からもつながりを外す。</li>
 * </ul>
 * 旧実装は立方体の全座標 (129^3) を距離順に並べ、1 tick に 1000 か所ずつ調べていた。ここでは読み込み済みのチャンクの
 * ブロックエンティティから候補を集め、同じ順序 (距離、同じ距離なら X → Y → Z の小さい順) で最初の 1 つを選ぶ。
 * 調べ終わるまでの数十秒の待ちが無くなるが、選ばれる相手は (その間にワールドが変わらなければ) 同じになる。
 */
public class PathMarkerBlockEntity extends MarkerBlockEntity {
    /** 旧 searchSize。 */
    public static final int SEARCH_RADIUS = 64;
    public static final int MAX_LINKS = 2;
    private static final String TAG_LINK = "path_link";

    private final BlockPos[] links = new BlockPos[MAX_LINKS];

    public PathMarkerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PATH_MARKER.get(), pos, state);
    }

    public boolean isFullyConnected() {
        return links[0] != null && links[1] != null;
    }

    public boolean isLinkedTo(BlockPos pos) {
        return pos.equals(links[0]) || pos.equals(links[1]);
    }

    /** 描画用: つながっている相手。 */
    public List<BlockPos> getLinks() {
        List<BlockPos> result = new ArrayList<>(MAX_LINKS);
        for (BlockPos link : links) {
            if (link != null) {
                result.add(link);
            }
        }
        return result;
    }

    /** 旧 tryConnection: 範囲の線形つなぎではなく、最も近い緑マーカーとつなぐ。 */
    @Override
    public void tryConnection(@Nullable Player player) {
        if (!(level instanceof ServerLevel serverLevel) || isFullyConnected()) {
            return;
        }
        PathMarkerBlockEntity nearest = findNearestCandidate(serverLevel);
        if (nearest != null) {
            connect(nearest);
            nearest.connect(this);
        }
    }

    /** 旧 trySetBlockAndConnection も tryConnection を呼ぶだけだった。 */
    @Override
    public boolean trySetBlockAndConnection(Player player, ItemStack held) {
        tryConnection(player);
        return true;
    }

    @Nullable
    private PathMarkerBlockEntity findNearestCandidate(ServerLevel serverLevel) {
        List<PathMarkerBlockEntity> candidates = new ArrayList<>();
        int minChunkX = SectionPos.blockToSectionCoord(worldPosition.getX() - SEARCH_RADIUS);
        int maxChunkX = SectionPos.blockToSectionCoord(worldPosition.getX() + SEARCH_RADIUS);
        int minChunkZ = SectionPos.blockToSectionCoord(worldPosition.getZ() - SEARCH_RADIUS);
        int maxChunkZ = SectionPos.blockToSectionCoord(worldPosition.getZ() + SEARCH_RADIUS);
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                LevelChunk chunk = serverLevel.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (blockEntity instanceof PathMarkerBlockEntity other && other != this && inRange(other.worldPosition)
                            && !other.isFullyConnected() && !isLinkedTo(other.worldPosition)) {
                        candidates.add(other);
                    }
                }
            }
        }
        return candidates.stream().min(Comparator
                .comparingDouble((PathMarkerBlockEntity other) -> other.worldPosition.distSqr(worldPosition))
                .thenComparingInt(other -> other.worldPosition.getX())
                .thenComparingInt(other -> other.worldPosition.getY())
                .thenComparingInt(other -> other.worldPosition.getZ())).orElse(null);
    }

    private boolean inRange(BlockPos pos) {
        return Math.abs(pos.getX() - worldPosition.getX()) <= SEARCH_RADIUS
                && Math.abs(pos.getY() - worldPosition.getY()) <= SEARCH_RADIUS
                && Math.abs(pos.getZ() - worldPosition.getZ()) <= SEARCH_RADIUS;
    }

    private void connect(PathMarkerBlockEntity other) {
        for (int i = 0; i < MAX_LINKS; i++) {
            if (links[i] == null) {
                links[i] = other.worldPosition.immutable();
                sync();
                return;
            }
        }
    }

    private void unlink(BlockPos pos) {
        for (int i = 0; i < MAX_LINKS; i++) {
            if (pos.equals(links[i])) {
                links[i] = null;
            }
        }
        sync();
    }

    /** 旧 getPath: 自分から、まだ通っていないつながりを先にあるもの (0 番) から順にたどる。 */
    public List<BlockPos> getPath() {
        List<BlockPos> path = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        PathMarkerBlockEntity current = this;
        while (current != null) {
            path.add(current.worldPosition);
            visited.add(current.worldPosition);
            PathMarkerBlockEntity next = null;
            for (BlockPos link : current.links) {
                if (link != null && !visited.contains(link) && level != null
                        && level.getBlockEntity(link) instanceof PathMarkerBlockEntity linked) {
                    next = linked;
                    break;
                }
            }
            current = next;
        }
        return path;
    }

    /** 壊れたとき: 相手からもつながりを外す (旧 invalidate)。 */
    @Override
    public void destroy() {
        super.destroy();
        if (level == null || level.isClientSide) {
            return;
        }
        for (BlockPos link : links) {
            if (link != null && level.getBlockEntity(link) instanceof PathMarkerBlockEntity other) {
                other.unlink(worldPosition);
            }
        }
    }

    // ---- 保存 (旧実装は 2 本目のつながりを 1 本目の名前で上書きして失っていたため、両方を別々に保存する) ------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        writeLinks(tag);
    }

    private void writeLinks(CompoundTag tag) {
        for (int i = 0; i < MAX_LINKS; i++) {
            if (links[i] != null) {
                tag.put(TAG_LINK + i, NbtUtils.writeBlockPos(links[i]));
            }
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        for (int i = 0; i < MAX_LINKS; i++) {
            links[i] = NbtUtils.readBlockPos(tag, TAG_LINK + i).orElse(null);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        writeLinks(tag);
        return tag;
    }
}
