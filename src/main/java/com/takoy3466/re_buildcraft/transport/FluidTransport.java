package com.takoy3466.re_buildcraft.transport;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/**
 * 液体輸送。元実装: {@code buildcraft.transport.PipeTransportLiquids}。
 * <p>
 * 6 つの面と中心にそれぞれバッファを持ち、毎 tick 各バッファから流量 ({@code flowRate}) までを動かす。
 * 外から入ってきた面は「入力」となり中心へ流し、中心の液体は出力可能な面へ均等に分け、出力面は隣へ渡す。
 * 隣が受け取らない面は跳ね返りを数え、続くと入力扱いに戻る。旧実装の travelDelay は全パイプで 1 なので、
 * 遅延用のリングバッファは持たず 1 tick ごとに直接動かす。
 */
public final class FluidTransport extends PipeTransport {
    /** 1 バッファの基本容量 (旧 LIQUID_IN_PIPE)。実際の容量はこれに流量を足したもの。 */
    public static final int LIQUID_IN_PIPE = 250;
    private static final int CENTER = 6;
    private static final int BUFFER_COUNT = 7;
    private static final int LOCK_LIMIT_TICKS = 20;
    private static final int BOUNCE_LIMIT = 20;
    private static final int EMPTY_LIMIT_TICKS = 20;
    private static final int SYNC_INTERVAL_TICKS = 10;
    private static final String TAG_FLUID = "fluid";
    private static final String TAG_AMOUNTS = "amounts";
    private static final String TAG_INPUTS = "inputs";

    private final int flowRate;
    private final int[] amounts = new int[BUFFER_COUNT];
    private final boolean[] isInput = new boolean[Direction.values().length];
    private final boolean[] isOutput = new boolean[Direction.values().length];
    private final int[] emptyTime = new int[Direction.values().length];
    private final int[] totalBounced = new int[Direction.values().length];
    private final IFluidHandler[] sideHandlers = new IFluidHandler[Direction.values().length];
    private Fluid fluid = Fluids.EMPTY;
    private int lockedTime;
    private boolean displayChanged;

    FluidTransport(PipeBlockEntity pipe, int flowRate) {
        super(pipe);
        this.flowRate = flowRate;
        for (Direction direction : Direction.values()) {
            sideHandlers[direction.get3DDataValue()] = new SideHandler(direction);
        }
    }

    private int capacity() {
        return LIQUID_IN_PIPE + flowRate;
    }

    /** 外部に公開する、{@code side} の面の液体ハンドラ。 */
    public IFluidHandler handler(Direction side) {
        return sideHandlers[side.get3DDataValue()];
    }

    /**
     * {@code side} の面のバッファへ入れる。入れた面は入力扱いになる (旧 fill)。
     *
     * @return 入れた量
     */
    public int fill(Direction side, FluidStack resource, boolean execute) {
        if (resource.isEmpty()) {
            return 0;
        }
        int index = side.get3DDataValue();
        isInput[index] = true;
        if (!isEmpty() && !resource.getFluid().isSame(fluid)) {
            return 0;
        }
        int accepted = Math.min(resource.getAmount(), capacity() - amounts[index]);
        if (execute && accepted > 0) {
            fluid = resource.getFluid();
            amounts[index] += accepted;
            displayChanged = true;
        }
        return accepted;
    }

    @Override
    public void tick(ServerLevel level) {
        if (computeOutputs(level) == 0) {
            lockedTime++;
        } else {
            lockedTime = 0;
        }
        if (lockedTime > LOCK_LIMIT_TICKS) {
            Arrays.fill(isInput, false);
            computeOutputs(level);
        }

        List<Direction> order = splitOrder(level);
        int[] leaving = new int[BUFFER_COUNT];
        for (int i = 0; i < BUFFER_COUNT; i++) {
            leaving[i] = Math.min(amounts[i], flowRate);
            amounts[i] -= leaving[i];
        }

        amounts[CENTER] += leaving[CENTER] - distributeFromCenter(order, leaving[CENTER]);
        for (Direction direction : order) {
            moveSide(level, direction, leaving[direction.get3DDataValue()]);
        }

        if (isEmpty()) {
            fluid = Fluids.EMPTY;
        }
        if (displayChanged && level.getGameTime() % SYNC_INTERVAL_TICKS == 0L) {
            displayChanged = false;
            pipe.scheduleSync();
        }
    }

    private int computeOutputs(Level level) {
        int count = 0;
        for (Direction direction : Direction.values()) {
            int index = direction.get3DDataValue();
            isOutput[index] = pipe.outputOpen(direction) && canReceive(level, direction) && !isInput[index];
            if (isOutput[index]) {
                count++;
            }
        }
        return count;
    }

    private boolean canReceive(Level level, Direction direction) {
        return pipe.isConnected(direction) && neighborHandler(level, direction) != null;
    }

    @Nullable
    private IFluidHandler neighborHandler(Level level, Direction direction) {
        BlockPos neighborPos = pipe.getBlockPos().relative(direction);
        if (level.getBlockEntity(neighborPos) instanceof PipeBlockEntity next) {
            return next.transport() instanceof FluidTransport nextFluids ? nextFluids.handler(direction.getOpposite()) : null;
        }
        return level.getCapability(Capabilities.FluidHandler.BLOCK, neighborPos, direction.getOpposite());
    }

    /** 毎 tick 無作為に並べた面の順 (旧 getSplitVector)。 */
    private static List<Direction> splitOrder(Level level) {
        List<Direction> order = new ArrayList<>(List.of(Direction.values()));
        Collections.shuffle(order, new Random(level.random.nextLong()));
        return order;
    }

    /** 中心の液体を出力面へ均等に分ける。2 回目は 1 回目で満杯にならなかった面だけに分け直す (旧 splitLiquid)。 */
    private int distributeFromCenter(List<Direction> order, int amount) {
        boolean[] filled = new boolean[Direction.values().length];
        int moved = split(order, amount, countOutputs(filled), filled);
        if (moved < amount) {
            moved += split(order, amount - moved, countOutputs(filled), filled);
        }
        return moved;
    }

    private int countOutputs(boolean[] filled) {
        int count = 0;
        for (int i = 0; i < isOutput.length; i++) {
            if (isOutput[i] && !filled[i]) {
                count++;
            }
        }
        return count;
    }

    private int split(List<Direction> order, int amount, int outputs, boolean[] filled) {
        if (outputs == 0 || amount <= 0) {
            return 0;
        }
        int share = (int) Math.ceil((double) amount / outputs);
        int moved = 0;
        int remaining = amount;
        for (Direction direction : order) {
            int index = direction.get3DDataValue();
            if (!isOutput[index] || filled[index]) {
                continue;
            }
            int offer = Math.min(share, remaining);
            int accepted = Math.min(offer, capacity() - amounts[index]);
            amounts[index] += accepted;
            moved += accepted;
            remaining -= offer;
            if (accepted != offer) {
                filled[index] = true;
            }
        }
        return moved;
    }

    private void moveSide(Level level, Direction direction, int amount) {
        int index = direction.get3DDataValue();
        if (amount > 0) {
            int moved = 0;
            if (isInput[index]) {
                moved = fillCenter(amount);
            } else if (isOutput[index]) {
                IFluidHandler target = neighborHandler(level, direction);
                moved = target == null ? 0 : target.fill(new FluidStack(fluid, amount), IFluidHandler.FluidAction.EXECUTE);
                if (moved == 0) {
                    totalBounced[index]++;
                    moved = fillCenter(amount);
                } else {
                    totalBounced[index] = 0;
                }
            }
            amounts[index] += amount - moved;
            displayChanged = true;
        }

        if (amounts[index] != 0) {
            emptyTime[index] = 0;
        }
        if (totalBounced[index] > BOUNCE_LIMIT) {
            isInput[index] = true;
        } else if (amounts[index] == 0) {
            emptyTime[index]++;
        }
        if (emptyTime[index] > EMPTY_LIMIT_TICKS) {
            isInput[index] = false;
        }
    }

    private int fillCenter(int amount) {
        int accepted = Math.min(amount, capacity() - amounts[CENTER]);
        amounts[CENTER] += accepted;
        return accepted;
    }

    @Override
    public void onNeighborChanged() {
        for (Direction direction : Direction.values()) {
            if (!pipe.isConnected(direction)) {
                amounts[direction.get3DDataValue()] = 0;
                isInput[direction.get3DDataValue()] = false;
            }
        }
    }

    // ---- 状態 -----------------------------------------------------------------

    public Fluid fluid() {
        return fluid;
    }

    /** 描画用: 面ごとの量 (容量は {@link #LIQUID_IN_PIPE} で頭打ち)。 */
    public int sideAmount(Direction direction) {
        return Math.min(amounts[direction.get3DDataValue()], LIQUID_IN_PIPE);
    }

    public int centerAmount() {
        return Math.min(amounts[CENTER], LIQUID_IN_PIPE);
    }

    public int totalAmount() {
        int total = 0;
        for (int amount : amounts) {
            total += amount;
        }
        return total;
    }

    @Override
    public boolean isEmpty() {
        return totalAmount() == 0;
    }

    /** ゲートのトリガー「液体通過中」。{@code filter} が空なら液体の種類を問わない。 */
    public boolean contains(Fluid filter) {
        return !isEmpty() && (filter == Fluids.EMPTY || fluid.isSame(filter));
    }

    @Override
    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_FLUID, BuiltInRegistries.FLUID.getKey(fluid).toString());
        tag.putIntArray(TAG_AMOUNTS, amounts);
        byte[] inputs = new byte[isInput.length];
        for (int i = 0; i < isInput.length; i++) {
            inputs[i] = (byte) (isInput[i] ? 1 : 0);
        }
        tag.putByteArray(TAG_INPUTS, inputs);
        return tag;
    }

    @Override
    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        ResourceLocation fluidId = ResourceLocation.tryParse(tag.getString(TAG_FLUID));
        fluid = fluidId == null ? Fluids.EMPTY : BuiltInRegistries.FLUID.get(fluidId);
        int[] savedAmounts = tag.getIntArray(TAG_AMOUNTS);
        System.arraycopy(savedAmounts, 0, amounts, 0, Math.min(savedAmounts.length, amounts.length));
        byte[] inputs = tag.getByteArray(TAG_INPUTS);
        for (int i = 0; i < Math.min(inputs.length, isInput.length); i++) {
            isInput[i] = inputs[i] != 0;
        }
    }

    /** 1 つの面から見た液体ハンドラ。取り出しはできない (旧 PipeTransportLiquids.empty は常に 0)。 */
    private final class SideHandler implements IFluidHandler {
        private final Direction side;

        private SideHandler(Direction side) {
            this.side = side;
        }

        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return isEmpty() ? FluidStack.EMPTY : new FluidStack(fluid, totalAmount());
        }

        @Override
        public int getTankCapacity(int tank) {
            return capacity() * BUFFER_COUNT;
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return isEmpty() || stack.getFluid().isSame(fluid);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return FluidTransport.this.fill(side, resource, action.execute());
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return FluidStack.EMPTY;
        }
    }
}
