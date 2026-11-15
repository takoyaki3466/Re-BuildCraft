package com.takoy3466.re_buildcraft.api.quarry;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * クアーリーの枠組みの設計図と、その建設順。
 * 元実装: {@code TileQuarry.initializeBluePrintBuilder} が作る {@code BptBlueprint} と {@code BptBuilderBlueprint}。
 * <p>
 * 作業範囲のうち、上下の層の外周と 4 本の縦の辺が枠組み、それ以外は空気。
 * 旧実装と同じく、まず上の層から順に「設計図と違う、柔らかくないブロック」を (ドロップ無しで) 撤去し、
 * 次に下の層から順に設計図どおりのブロック (枠組み/空気) を置いていく。
 */
public final class FrameBlueprint {

    /** 1 マス分の作業。{@code clear} が true なら撤去のみ、false なら設計図どおりに置く。 */
    public record Slot(BlockPos pos, boolean wantsFrame, boolean clear) {
    }

    private final Block frameBlock;
    private final Predicate<BlockState> softBlock;
    private final Deque<Slot> clearList = new ArrayDeque<>();
    private final Deque<Slot> buildList = new ArrayDeque<>();
    private boolean done;
    @Nullable
    private BlockPos waitingFor;

    /**
     * @param softBlock 撤去を省略してよい柔らかいブロック (旧 {@code BuildCraftAPI.softBlock})
     */
    public FrameBlueprint(Level level, QuarryArea area, Block frameBlock, Predicate<BlockState> softBlock) {
        this.frameBlock = frameBlock;
        this.softBlock = softBlock;
        for (int y = area.sizeY() - 1; y >= 0; y--) {
            addLayer(level, area, y, true, clearList);
        }
        for (int y = 0; y < area.sizeY(); y++) {
            addLayer(level, area, y, false, buildList);
        }
    }

    private void addLayer(Level level, QuarryArea area, int dy, boolean clear, Deque<Slot> list) {
        for (int dx = 0; dx < area.sizeX(); dx++) {
            for (int dz = 0; dz < area.sizeZ(); dz++) {
                BlockPos pos = new BlockPos(area.minX() + dx, area.minY() + dy, area.minZ() + dz);
                // 既に置かれている枠組みは設計図に取り込む (旧実装も範囲内の既存の枠組みを残していた)。
                boolean wantsFrame = isFrameEdge(area, dx, dy, dz) || level.getBlockState(pos).is(frameBlock);
                list.add(new Slot(pos, wantsFrame, clear));
            }
        }
    }

    private static boolean isFrameEdge(QuarryArea area, int dx, int dy, int dz) {
        boolean edgeX = dx == 0 || dx == area.sizeX() - 1;
        boolean edgeZ = dz == 0 || dz == area.sizeZ() - 1;
        boolean horizontalLayer = dy == 0 || dy == area.sizeY() - 1;
        return horizontalLayer ? edgeX || edgeZ : edgeX && edgeZ;
    }

    /** 設計図どおりの状態か。 */
    public boolean isValid(Level level, Slot slot) {
        BlockState state = level.getBlockState(slot.pos());
        return slot.wantsFrame() ? state.is(frameBlock) : state.isAir();
    }

    /** 次に作業すべきマス。すべて設計図どおりなら null を返し、{@link #isDone()} が true になる。 */
    @Nullable
    public Slot nextSlot(Level level) {
        waitingFor = null;
        Slot slot = pollInvalid(level, clearList, true);
        if (slot == null && waitingFor == null) {
            slot = pollInvalid(level, buildList, false);
        }
        done = clearList.isEmpty() && buildList.isEmpty();
        return slot;
    }

    /**
     * 直前の {@link #nextSlot} が、読み込まれていないチャンクのマスで止まった場合のその位置。
     * そのマスは捨てずに残しているため、チャンクが読み込まれてから作業を続けられる。
     */
    @Nullable
    public BlockPos waitingFor() {
        return waitingFor;
    }

    @Nullable
    private Slot pollInvalid(Level level, Deque<Slot> list, boolean skipSoft) {
        while (!list.isEmpty()) {
            Slot slot = list.pollFirst();
            if (!level.isLoaded(slot.pos())) {
                // 読み込まれていないマスを飛ばすと、そのマスの枠組みは作られないまま完了扱いになる。残して待つ。
                list.addFirst(slot);
                waitingFor = slot.pos();
                return null;
            }
            if (isValid(level, slot)) {
                continue;
            }
            if (skipSoft && softBlock.test(level.getBlockState(slot.pos()))) {
                continue;
            }
            return slot;
        }
        return null;
    }

    /** ロボットが作業を実行する。 */
    public void apply(Level level, Slot slot) {
        if (isValid(level, slot)) {
            return;
        }
        if (slot.clear() || !slot.wantsFrame()) {
            level.removeBlock(slot.pos(), false);
        } else {
            // 隣の枠組みとのつながりを反映した状態で置く。
            BlockState frame = Block.updateFromNeighbourShapes(frameBlock.defaultBlockState(), level, slot.pos());
            level.setBlock(slot.pos(), frame, Block.UPDATE_ALL);
        }
    }

    public boolean isDone() {
        return done;
    }
}
