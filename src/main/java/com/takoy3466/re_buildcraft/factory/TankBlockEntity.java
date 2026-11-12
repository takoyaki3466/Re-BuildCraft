package com.takoy3466.re_buildcraft.factory;

import com.takoy3466.re_buildcraft.api.util.SafeTimeTracker;
import com.takoy3466.re_buildcraft.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

/**
 * タンク。縦に積むと 1 本の柱として振る舞い、液体は下から溜まって上から抜ける。
 * 元実装: {@code buildcraft.factory.TileTank}。
 */
public class TankBlockEntity extends BlockEntity {
    public static final int CAPACITY = 16000;

    private static final String TAG_TANK = "tank";
    /** 下のタンクへ液体を落とす間隔。 */
    private static final int DOWN_MOVE_INTERVAL_TICKS = 10;
    /** 1 回に下へ落とす量 (容量の 1/16)。 */
    private static final int DOWN_MOVE_AMOUNT = CAPACITY >> 4;
    /** クライアントへの同期間隔 (旧 2 * updateFactor)。 */
    private static final int SYNC_INTERVAL_TICKS = 20;

    private final FluidTank tank = new FluidTank(CAPACITY) {
        @Override
        protected void onContentsChanged() {
            hasUpdate = true;
            setChanged();
        }
    };
    private final IFluidHandler columnHandler = new ColumnHandler();
    private final SafeTimeTracker syncTracker = new SafeTimeTracker();
    private boolean hasUpdate;
    private int downMoveTimer;

    public TankBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TANK.get(), pos, state);
    }

    public void serverTick() {
        if (level == null) {
            return;
        }
        if (hasUpdate && syncTracker.markTimeIfDelay(level, SYNC_INTERVAL_TICKS)) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
            hasUpdate = false;
        }
        if (downMoveTimer == 0) {
            downMoveTimer = DOWN_MOVE_INTERVAL_TICKS;
            moveLiquidDown();
        }
        downMoveTimer--;
    }

    /** 柱の途中に空きができた場合に備え、少しずつ下のタンクへ液体を移す。 */
    private void moveLiquidDown() {
        TankBlockEntity below = getTankAt(worldPosition.below());
        if (below == null || tank.isEmpty() || below.tank.getFluidAmount() == below.tank.getCapacity()) {
            return;
        }
        FluidStack moving = tank.getFluid().copyWithAmount(Math.min(DOWN_MOVE_AMOUNT, tank.getFluidAmount()));
        int moved = below.fillUpward(moving, IFluidHandler.FluidAction.EXECUTE);
        tank.drain(moved, IFluidHandler.FluidAction.EXECUTE);
    }

    // ---- 柱としての入出力 ---------------------------------------------------

    /** 外部に公開する液体ハンドラ。どの段に対する操作でも柱全体として処理される。 */
    public IFluidHandler getFluidHandler() {
        return columnHandler;
    }

    /** このタンク単体の中身 (描画用)。 */
    public FluidStack getFluid() {
        return tank.getFluid();
    }

    @Nullable
    private TankBlockEntity getTankAt(BlockPos pos) {
        return level != null && level.getBlockEntity(pos) instanceof TankBlockEntity other ? other : null;
    }

    private TankBlockEntity bottomOfColumn() {
        TankBlockEntity current = this;
        for (TankBlockEntity below = getTankAt(worldPosition.below()); below != null;
             below = getTankAt(current.worldPosition.below())) {
            current = below;
        }
        return current;
    }

    private TankBlockEntity topOfColumn() {
        TankBlockEntity current = this;
        for (TankBlockEntity above = getTankAt(worldPosition.above()); above != null;
             above = getTankAt(current.worldPosition.above())) {
            current = above;
        }
        return current;
    }

    /** このタンクへ入れ、入り切らない分は上のタンクへ回す。 */
    private int fillUpward(FluidStack resource, IFluidHandler.FluidAction action) {
        if (!tank.isEmpty() && !FluidStack.isSameFluidSameComponents(tank.getFluid(), resource)) {
            return 0;
        }
        int filled = tank.fill(resource, action);
        int remaining = resource.getAmount() - filled;
        TankBlockEntity above = remaining > 0 ? getTankAt(worldPosition.above()) : null;
        if (above != null) {
            filled += above.fillUpward(resource.copyWithAmount(remaining), action);
        }
        return filled;
    }

    /** このタンクから抜き、足りない分は下のタンクから抜く。 */
    private FluidStack drainDownward(int maxDrain, IFluidHandler.FluidAction action) {
        FluidStack drained = tank.drain(maxDrain, action);
        int remaining = maxDrain - drained.getAmount();
        TankBlockEntity below = remaining > 0 ? getTankAt(worldPosition.below()) : null;
        if (below == null) {
            return drained;
        }
        boolean compatible = drained.isEmpty() || below.tank.isEmpty()
                || FluidStack.isSameFluidSameComponents(drained, below.tank.getFluid());
        if (!compatible) {
            return drained;
        }
        FluidStack fromBelow = below.drainDownward(remaining, action);
        if (drained.isEmpty()) {
            return fromBelow;
        }
        return drained.copyWithAmount(drained.getAmount() + fromBelow.getAmount());
    }

    /** 柱全体に入っている液体 (同じ種類の液体が続く範囲の合計)。 */
    private FluidStack getColumnFluid() {
        FluidStack total = FluidStack.EMPTY;
        for (TankBlockEntity current = bottomOfColumn(); current != null; current = current.getTankAt(current.worldPosition.above())) {
            FluidStack fluid = current.tank.getFluid();
            if (fluid.isEmpty()) {
                continue;
            }
            if (total.isEmpty()) {
                total = fluid.copy();
            } else if (FluidStack.isSameFluidSameComponents(total, fluid)) {
                total.grow(fluid.getAmount());
            }
        }
        return total;
    }

    private int getColumnCapacity() {
        int capacity = 0;
        for (TankBlockEntity current = bottomOfColumn(); current != null; current = current.getTankAt(current.worldPosition.above())) {
            capacity += current.tank.getCapacity();
        }
        return capacity;
    }

    private final class ColumnHandler implements IFluidHandler {
        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int tankIndex) {
            return getColumnFluid();
        }

        @Override
        public int getTankCapacity(int tankIndex) {
            return getColumnCapacity();
        }

        @Override
        public boolean isFluidValid(int tankIndex, FluidStack stack) {
            return true;
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return resource.isEmpty() ? 0 : bottomOfColumn().fillUpward(resource, action);
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            if (resource.isEmpty() || !FluidStack.isSameFluidSameComponents(resource, getColumnFluid())) {
                return FluidStack.EMPTY;
            }
            return drain(resource.getAmount(), action);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return maxDrain <= 0 ? FluidStack.EMPTY : topOfColumn().drainDownward(maxDrain, action);
        }
    }

    // ---- 保存と同期 ---------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(TAG_TANK, tank.writeToNBT(registries, new CompoundTag()));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        tank.readFromNBT(registries, tag.getCompound(TAG_TANK));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
