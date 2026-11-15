package com.takoy3466.re_buildcraft.builders.blueprint;

import com.takoy3466.re_buildcraft.builders.FakePlayerPlacement;
import com.takoy3466.re_buildcraft.builders.SoftBlocks;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

/**
 * 設計図・テンプレートを 1 か所に建てる手順。元実装: {@code BptBuilderTemplate} / {@code BptBuilderBlueprint}。
 * <ul>
 *   <li>まず範囲の上の層から、設計図と違う柔らかくないブロックを取り除く (clearList)。</li>
 *   <li>テンプレート: 下の層から、記録された場所が柔らかければ材料のどれか 1 個を「使って」置く (buildList)。</li>
 *   <li>設計図: 下の層から、光を遮る立方体 (primaryList) → その他 (secondaryList) の順に、必要なアイテムをそろえて置く。
 *       そろわないマスは後回しにし、そろうまで建築は終わらない。ワールドの既定のゲームモードがクリエイティブならアイテムを使わない。</li>
 * </ul>
 * 1 回の {@link #next} で 1 マス分の作業を決め、実際にブロックを変えるのは建設ロボット ({@link Step#execute})。
 */
public final class BuildPlan {
    private enum Phase {
        CLEAR,
        BUILD
    }

    private record Cell(BlockPos pos, BlockState state, @Nullable CompoundTag blockEntity, boolean filled, Phase phase) {
    }

    /**
     * ロボットが実行する 1 マス分の作業。
     *
     * @param stackToUse テンプレートで使う材料 (設計図では空)
     */
    public record Step(BlockPos pos, Kind kind, BlockState state, @Nullable CompoundTag blockEntity,
                       @Nullable BlockPos companionPos, @Nullable BlockState companionState, ItemStack stackToUse) {
        public enum Kind {
            CLEAR,
            PLACE,
            USE_ITEM
        }

        /** ロボットの作業 (旧 EntityRobot.update)。 */
        public void execute(Level level) {
            switch (kind) {
                case CLEAR -> level.removeBlock(pos, false);
                case USE_ITEM -> {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    if (level instanceof ServerLevel serverLevel) {
                        FakePlayerPlacement.useAt(serverLevel, pos, Direction.UP, stackToUse);
                    }
                }
                case PLACE -> place(level);
            }
        }

        private void place(Level level) {
            // 2 マスのブロックは両方置いてから周りへ知らせる (片方だけの状態で壊れないように)。
            int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
            level.setBlock(pos, state, companionPos == null ? Block.UPDATE_ALL : flags);
            if (companionPos != null && companionState != null) {
                level.setBlock(companionPos, companionState, flags);
                level.updateNeighborsAt(pos, state.getBlock());
                level.updateNeighborsAt(companionPos, companionState.getBlock());
            }
            if (blockEntity != null) {
                BlockEntity placed = level.getBlockEntity(pos);
                if (placed != null) {
                    placed.loadCustomOnly(blockEntity, level.registryAccess());
                    placed.setChanged();
                }
            }
        }
    }

    private final Blueprint blueprint;
    private final BoundingBox box;
    private final Deque<Cell> clearList = new ArrayDeque<>();
    private final Deque<Cell> primaryList = new ArrayDeque<>();
    private final Deque<Cell> secondaryList = new ArrayDeque<>();
    private boolean done;

    /** @param anchorPos ビルダーの位置。設計図はすでにビルダーの向きへ回してあること */
    public BuildPlan(Blueprint blueprint, BlockPos anchorPos) {
        this.blueprint = blueprint;
        this.box = blueprint.boxAt(anchorPos);
        // 取り除く順は上の層から、建てる順は下の層から (旧実装と同じ)。
        for (int y = blueprint.sizeY() - 1; y >= 0; y--) {
            forLayer(y, Phase.CLEAR);
        }
        for (int y = 0; y < blueprint.sizeY(); y++) {
            forLayer(y, Phase.BUILD);
        }
    }

    private void forLayer(int y, Phase phase) {
        for (int x = 0; x < blueprint.sizeX(); x++) {
            for (int z = 0; z < blueprint.sizeZ(); z++) {
                BlockPos pos = new BlockPos(box.minX() + x, box.minY() + y, box.minZ() + z);
                boolean template = blueprint.kind() == Blueprint.Kind.TEMPLATE;
                boolean filled = template && blueprint.filled(x, y, z);
                BlockState state = template ? Blocks.AIR.defaultBlockState() : blueprint.state(x, y, z);
                CompoundTag blockEntity = template ? null : blueprint.blockEntity(x, y, z);
                Cell cell = new Cell(pos, state, blockEntity, filled, phase);
                if (phase == Phase.CLEAR) {
                    if (!template || !filled) {
                        clearList.add(cell);
                    }
                } else if (template) {
                    if (filled) {
                        primaryList.add(cell);
                    }
                } else if (BlueprintRules.isPrimary(state)) {
                    primaryList.add(cell);
                } else {
                    secondaryList.add(cell);
                }
            }
        }
    }

    public BoundingBox box() {
        return box;
    }

    public boolean isDone() {
        return done;
    }

    public boolean isTemplate() {
        return blueprint.kind() == Blueprint.Kind.TEMPLATE;
    }

    /**
     * 次に建てる 1 マス (旧 getNextBlock)。今は建てられない (材料不足・待ち) なら null。
     */
    @Nullable
    public Step next(ServerLevel level, BuilderMaterials materials) {
        Step step;
        if (!clearList.isEmpty()) {
            step = isTemplate() ? nextTemplate(level, materials, clearList) : nextBlueprint(level, materials, clearList);
        } else if (!primaryList.isEmpty()) {
            step = isTemplate() ? nextTemplate(level, materials, primaryList) : nextBlueprint(level, materials, primaryList);
        } else if (!secondaryList.isEmpty()) {
            step = nextBlueprint(level, materials, secondaryList);
        } else {
            step = null;
        }
        done = clearList.isEmpty() && primaryList.isEmpty() && secondaryList.isEmpty();
        return step;
    }

    /** 旧 BptBuilderTemplate.internalGetNextBlock: 柔らかさが記録と違う先頭のマスを処理する。材料が無ければそのマスで待つ。 */
    @Nullable
    private Step nextTemplate(ServerLevel level, BuilderMaterials materials, Deque<Cell> list) {
        while (!list.isEmpty()) {
            Cell cell = list.peekFirst();
            boolean worldSoft = SoftBlocks.isSoft(level.getBlockState(cell.pos()));
            if (worldSoft == !cell.filled()) {
                list.pollFirst();
                continue;
            }
            if (cell.phase() == Phase.CLEAR) {
                list.pollFirst();
                return new Step(cell.pos(), Step.Kind.CLEAR, cell.state(), null, null, null, ItemStack.EMPTY);
            }
            ItemStack material = materials.takeAny();
            if (material.isEmpty()) {
                return null;
            }
            list.pollFirst();
            return new Step(cell.pos(), Step.Kind.USE_ITEM, cell.state(), null, null, null, material);
        }
        return null;
    }

    /** 旧 BptBuilderBlueprint.internalGetNextBlock: 設計図と違うマスを処理し、材料のそろわないマスは後回しにする。 */
    @Nullable
    private Step nextBlueprint(ServerLevel level, BuilderMaterials materials, Deque<Cell> list) {
        List<Cell> deferred = new ArrayList<>();
        Step result = null;
        while (!list.isEmpty()) {
            Cell cell = list.pollFirst();
            BlockState current = level.getBlockState(cell.pos());
            if (!level.isLoaded(cell.pos()) || current.is(cell.state().getBlock()) || BlueprintRules.ignore(cell.state())) {
                continue;
            }
            if (cell.phase() == Phase.CLEAR) {
                if (!SoftBlocks.isSoft(current)) {
                    result = new Step(cell.pos(), Step.Kind.CLEAR, cell.state(), null, null, null, ItemStack.EMPTY);
                    break;
                }
                continue;
            }
            List<ItemStack> requirements = BlueprintRules.requirements(cell.state());
            boolean creative = level.getServer().getWorldData().getGameType() == GameType.CREATIVE;
            if (creative || materials.hasAll(requirements)) {
                if (!creative) {
                    materials.useAll(requirements);
                }
                result = placeStep(cell);
                break;
            }
            deferred.add(cell);
        }
        list.addAll(deferred);
        return result;
    }

    /** 2 マスのブロックの下半分・ベッドの足側なら、もう片方も一緒に置く。 */
    private Step placeStep(Cell cell) {
        BlockState state = cell.state();
        BlockPos companionPos = null;
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            companionPos = cell.pos().above();
        } else if (state.getBlock() instanceof BedBlock) {
            companionPos = cell.pos().relative(state.getValue(BedBlock.FACING));
        }
        BlockState companionState = companionPos == null ? null : stateAt(companionPos);
        if (companionState != null && !BlueprintRules.isCompanion(companionState)) {
            companionPos = null;
            companionState = null;
        }
        return new Step(cell.pos(), Step.Kind.PLACE, state, cell.blockEntity(), companionPos, companionState, ItemStack.EMPTY);
    }

    @Nullable
    private BlockState stateAt(BlockPos pos) {
        if (!box.isInside(pos) || isTemplate()) {
            return null;
        }
        return blueprint.state(pos.getX() - box.minX(), pos.getY() - box.minY(), pos.getZ() - box.minZ());
    }

    /**
     * 残りのマスに必要なアイテムの合計 (旧 recomputeNeededItems / neededItems)。多い順。
     * 設計図だけが対象で、テンプレートでは空。
     */
    public List<ItemStack> neededItems() {
        if (isTemplate()) {
            return List.of();
        }
        Map<Item, Integer> totals = new LinkedHashMap<>();
        for (Deque<Cell> list : List.of(primaryList, secondaryList)) {
            for (Cell cell : list) {
                for (ItemStack required : BlueprintRules.requirements(cell.state())) {
                    totals.merge(required.getItem(), required.getCount(), Integer::sum);
                }
            }
        }
        List<ItemStack> result = new ArrayList<>();
        totals.forEach((item, count) -> result.add(new ItemStack(item, count)));
        result.sort((a, b) -> Integer.compare(b.getCount(), a.getCount()));
        return result;
    }

}
