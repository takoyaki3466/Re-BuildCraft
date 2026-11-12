package com.takoy3466.re_buildcraft.factory;

import com.takoy3466.re_buildcraft.Config;
import com.takoy3466.re_buildcraft.api.IMachine;
import com.takoy3466.re_buildcraft.api.power.PoweredBlockEntity;
import com.takoy3466.re_buildcraft.registry.ModBlockEntities;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/**
 * ポンプ。チューブを真下の液面まで降ろし、つながった液体の水源を上の層から順に汲み上げ、隣接する液体容器へ送る。
 * 元実装: {@code buildcraft.factory.TilePump}。
 * <p>
 * 旧実装はチューブを専用エンティティ ({@code EntityBlock}) で表示していたが、
 * ここではチューブ先端の高さを同期して BlockEntityRenderer で描画する。
 */
public class PumpBlockEntity extends PoweredBlockEntity implements IMachine {
    private static final int ENERGY_PER_BLOCK = 10;
    private static final int MAX_ENERGY_STORED = 100;
    private static final double TUBE_SPEED = 0.1;
    /** チューブ先端が目標高さからこれ以上離れていれば、まだ降下中とみなす。 */
    private static final double TUBE_ARRIVAL_MARGIN = TUBE_SPEED + 0.4;
    /** 汲み上げ範囲 (水平距離の 2 乗)。64^2 = 4096 と 65^2 = 4225 の間。 */
    private static final int MAX_HORIZONTAL_DISTANCE_SQUARED = 4160;

    private static final String TAG_INTERNAL_LIQUID = "internalLiquid";
    private static final String TAG_LIQUID = "liquid";
    private static final String TAG_AIM_Y = "aimY";
    private static final String TAG_TUBE_Y = "tubeY";

    /** 汲み上げ待ちの水源。キーは高さで、高い層から処理する。 */
    private final TreeMap<Integer, Deque<BlockPos>> blocksToPump = new TreeMap<>();
    private int internalLiquid;
    private Fluid liquid = Fluids.EMPTY;
    private double tubeY = Double.NaN;
    private int aimY;
    private boolean doWork;

    public PumpBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PUMP.get(), pos, state);
        powerProvider.configure(ENERGY_PER_BLOCK, ENERGY_PER_BLOCK, ENERGY_PER_BLOCK, MAX_ENERGY_STORED);
    }

    public void serverTick() {
        if (level == null) {
            return;
        }
        if (Double.isNaN(tubeY)) {
            tubeY = worldPosition.getY();
            aimY = worldPosition.getY();
            sendNetworkUpdate();
        }
        powerProvider.update(this, level);

        if (tubeY - aimY > TUBE_ARRIVAL_MARGIN) {
            tubeY -= TUBE_SPEED;
            setChanged();
            sendNetworkUpdate();
            return;
        }

        if (internalLiquid <= 0 && !pumpNextBlock(level)) {
            initializePumpFromPosition(level, new BlockPos(worldPosition.getX(), aimY, worldPosition.getZ()));
            if (nextBlockToPump() == null) {
                lowerTubeToLiquid(level);
            }
        }

        if (internalLiquid > 0) {
            pushLiquidToNeighbours(level);
        }
    }

    /** 真下を走査し、最初に見つかった液体の高さを次の目標にする。途中に固体があれば何もしない。 */
    private void lowerTubeToLiquid(Level level) {
        for (int y = worldPosition.getY() - 1; y > level.getMinBuildHeight(); y--) {
            BlockPos pos = new BlockPos(worldPosition.getX(), y, worldPosition.getZ());
            if (isLiquid(level, pos)) {
                aimY = y;
                setChanged();
                return;
            }
            if (!level.getBlockState(pos).isAir()) {
                return;
            }
        }
    }

    /**
     * 次の水源を 1 つ汲み上げる。
     *
     * @return 汲み上げ対象が見つかった場合 true (エネルギー不足で汲めなかった場合も含む)
     */
    private boolean pumpNextBlock(Level level) {
        BlockPos next = nextBlockToPump();
        if (next == null || !isPumpableLiquid(level, next)) {
            doWork = false;
            return false;
        }
        Fluid fluid = level.getFluidState(next).getType();
        if (internalLiquid == 0 || liquid.isSame(fluid)) {
            liquid = fluid;
            if (powerProvider.useEnergy(ENERGY_PER_BLOCK, ENERGY_PER_BLOCK, true) == ENERGY_PER_BLOCK) {
                popNextBlock();
                if (!fluid.isSame(Fluids.WATER) || Config.CONSUME_WATER_SOURCES.get()) {
                    level.setBlock(next, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
                internalLiquid += FluidType.BUCKET_VOLUME;
                setChanged();
            }
        }
        doWork = true;
        return true;
    }

    private void pushLiquidToNeighbours(Level level) {
        for (Direction direction : Direction.values()) {
            IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK,
                    worldPosition.relative(direction), direction.getOpposite());
            if (handler == null) {
                continue;
            }
            internalLiquid -= handler.fill(new FluidStack(liquid, internalLiquid), IFluidHandler.FluidAction.EXECUTE);
            setChanged();
            if (internalLiquid <= 0) {
                return;
            }
        }
    }

    // ---- 汲み上げ対象の探索 --------------------------------------------------

    /** 最も高い層の、次に汲み上げる水源。 */
    @Nullable
    private BlockPos nextBlockToPump() {
        Map.Entry<Integer, Deque<BlockPos>> top = topNonEmptyLayer();
        return top == null ? null : top.getValue().peekFirst();
    }

    private void popNextBlock() {
        Map.Entry<Integer, Deque<BlockPos>> top = topNonEmptyLayer();
        if (top == null) {
            return;
        }
        top.getValue().pollFirst();
        if (top.getValue().isEmpty()) {
            blocksToPump.remove(top.getKey());
        }
    }

    @Nullable
    private Map.Entry<Integer, Deque<BlockPos>> topNonEmptyLayer() {
        for (Map.Entry<Integer, Deque<BlockPos>> entry : blocksToPump.descendingMap().entrySet()) {
            if (!entry.getValue().isEmpty()) {
                return entry;
            }
        }
        return null;
    }

    /** 起点の液体とつながっている同じ種類の液体を水平・上方向に探索し、水源を層ごとに登録する。 */
    private void initializePumpFromPosition(Level level, BlockPos start) {
        if (!isLiquid(level, start)) {
            return;
        }
        Fluid fluid = level.getFluidState(start).getType();
        Set<BlockPos> visited = new HashSet<>();
        Set<BlockPos> frontier = new HashSet<>();
        visit(level, start, fluid, visited, frontier);
        while (!frontier.isEmpty()) {
            Set<BlockPos> current = frontier;
            frontier = new HashSet<>();
            for (BlockPos pos : current) {
                for (Direction direction : Direction.Plane.HORIZONTAL) {
                    visit(level, pos.relative(direction), fluid, visited, frontier);
                }
                visit(level, pos.above(), fluid, visited, frontier);
            }
        }
    }

    private void visit(Level level, BlockPos pos, Fluid fluid, Set<BlockPos> visited, Set<BlockPos> frontier) {
        if (!level.isLoaded(pos) || !visited.add(pos) || !level.getFluidState(pos).getType().isSame(fluid)) {
            return;
        }
        int dx = pos.getX() - worldPosition.getX();
        int dz = pos.getZ() - worldPosition.getZ();
        if (dx * dx + dz * dz > MAX_HORIZONTAL_DISTANCE_SQUARED) {
            return;
        }
        if (isPumpableLiquid(level, pos)) {
            blocksToPump.computeIfAbsent(pos.getY(), y -> new ArrayDeque<>()).addFirst(pos);
        }
        if (isLiquid(level, pos)) {
            frontier.add(pos);
        }
    }

    /** 液体ブロックであること (水没したブロックなどは対象外)。 */
    private static boolean isLiquid(Level level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof LiquidBlock && !level.getFluidState(pos).isEmpty();
    }

    private static boolean isPumpableLiquid(Level level, BlockPos pos) {
        FluidState fluidState = level.getFluidState(pos);
        return isLiquid(level, pos) && fluidState.isSource();
    }

    // ---- IPowerReceptor / 状態 --------------------------------------------

    @Override
    public void doWork() {
        // 汲み上げはチューブの状態に依存するため serverTick で行う。
    }

    @Override
    public boolean isActive() {
        return doWork && internalLiquid == 0;
    }

    @Override
    public boolean manageLiquids() {
        return true;
    }

    @Override
    public int powerRequest() {
        return isActive() ? powerProvider.getMaxEnergyReceived() : 0;
    }

    /** チューブ先端の高さ (ワールド座標)。未初期化ならポンプ自身の高さ。 */
    public double getTubeY() {
        return Double.isNaN(tubeY) ? worldPosition.getY() : tubeY;
    }

    private void sendNetworkUpdate() {
        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    // ---- 保存と同期 ---------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(TAG_INTERNAL_LIQUID, internalLiquid);
        tag.putString(TAG_LIQUID, BuiltInRegistries.FLUID.getKey(liquid).toString());
        tag.putInt(TAG_AIM_Y, aimY);
        if (!Double.isNaN(tubeY)) {
            tag.putDouble(TAG_TUBE_Y, tubeY);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        internalLiquid = tag.getInt(TAG_INTERNAL_LIQUID);
        ResourceLocation liquidId = ResourceLocation.tryParse(tag.getString(TAG_LIQUID));
        liquid = liquidId == null ? Fluids.EMPTY : BuiltInRegistries.FLUID.get(liquidId);
        aimY = tag.getInt(TAG_AIM_Y);
        tubeY = tag.contains(TAG_TUBE_Y) ? tag.getDouble(TAG_TUBE_Y) : Double.NaN;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        if (!Double.isNaN(tubeY)) {
            tag.putDouble(TAG_TUBE_Y, tubeY);
        }
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
