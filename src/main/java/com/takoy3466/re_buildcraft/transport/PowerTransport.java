package com.takoy3466.re_buildcraft.transport;

import com.takoy3466.re_buildcraft.api.power.BuildCraftCapabilities;
import com.takoy3466.re_buildcraft.api.power.IPowerConduit;
import com.takoy3466.re_buildcraft.api.power.IPowerReceptor;

import java.util.Arrays;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * 動力 (MJ) 輸送。元実装: {@code buildcraft.transport.PipeTransportPower}。
 * <p>
 * 各面について「その面から入ってきた MJ」と「その面の先が要求している MJ」を 1 tick 遅れで持ち、
 * 入ってきた MJ を、他の面の要求量に比例して配る。要求は隣のパイプへ (自分の面以外の要求の合計として) 伝わっていく。
 * 機械の要求量は {@link IPowerReceptor#powerRequest()} で取得する。
 * 1 つの面に 1 tick で {@link #MAX_ENERGY} 以上が流れ込むとパイプが爆発する。
 */
public final class PowerTransport extends PipeTransport implements IPowerConduit {
    public static final double MAX_ENERGY = 1000.0;
    private static final int SIDES = Direction.values().length;
    private static final float OVERLOAD_EXPLOSION_POWER = 2.0F;
    private static final int SYNC_INTERVAL_TICKS = 10;
    private static final String TAG_QUERY = "powerQuery";
    private static final String TAG_NEXT_QUERY = "nextPowerQuery";
    private static final String TAG_POWER = "internalPower";
    private static final String TAG_NEXT_POWER = "internalNextPower";
    private static final String TAG_DISPLAY = "displayPower";

    private final int[] powerQuery = new int[SIDES];
    private final int[] nextPowerQuery = new int[SIDES];
    private final double[] internalPower = new double[SIDES];
    private final double[] internalNextPower = new double[SIDES];
    /**
     * 直前の tick に各面を流れた量 (描画とトリガー用)。
     */
    private final int[] displayPower = new int[SIDES];
    private long currentDate = Long.MIN_VALUE;
    private boolean displayChanged;

    PowerTransport(PipeBlockEntity pipe) {
        super(pipe);
    }

    /**
     * 1 tick に 1 回、次の値を現在の値へ繰り上げる (旧 step)。
     */
    private void step(Level level) {
        if (currentDate != level.getGameTime()) {
            currentDate = level.getGameTime();
            System.arraycopy(nextPowerQuery, 0, powerQuery, 0, SIDES);
            System.arraycopy(internalNextPower, 0, internalPower, 0, SIDES);
            Arrays.fill(nextPowerQuery, 0);
            Arrays.fill(internalNextPower, 0.0);
        }
    }

    @Override
    public void tick(ServerLevel level) {
        step(level);
        int[] previousDisplay = displayPower.clone();
        Arrays.fill(displayPower, 0);

        PowerTransport[] pipes = new PowerTransport[SIDES];
        IPowerReceptor[] receptors = new IPowerReceptor[SIDES];
        for (Direction direction : Direction.values()) {
            if (!pipe.isConnected(direction)) {
                continue;
            }
            int index = direction.get3DDataValue();
            pipes[index] = neighborPipe(level, direction);
            if (pipes[index] == null) {
                receptors[index] = level.getCapability(BuildCraftCapabilities.POWER_RECEPTOR, pipe.getBlockPos().relative(direction), direction.getOpposite());
            }
        }

        distributePower(level, pipes, receptors);
        requestFromReceptors(level, receptors);
        forwardRequests(level, pipes);

        if (!Arrays.equals(previousDisplay, displayPower)) {
            displayChanged = true;
        }
        if (displayChanged && level.getGameTime() % SYNC_INTERVAL_TICKS == 0L) {
            displayChanged = false;
            pipe.scheduleSync();
        }
    }

    private void distributePower(Level level, PowerTransport[] pipes, IPowerReceptor[] receptors) {
        for (int from = 0; from < SIDES; from++) {
            if (internalPower[from] <= 0.0) {
                continue;
            }
            double totalQuery = 0.0;
            for (int to = 0; to < SIDES; to++) {
                if (to != from && powerQuery[to] > 0 && (pipes[to] != null || receptors[to] != null)) {
                    totalQuery += powerQuery[to];
                }
            }
            if (totalQuery <= 0.0) {
                continue;
            }
            double available = internalPower[from];
            for (int to = 0; to < SIDES; to++) {
                if (to == from || powerQuery[to] <= 0) {
                    continue;
                }
                double share = available / totalQuery * powerQuery[to];
                Direction toDirection = Direction.from3DDataValue(to);
                if (pipes[to] != null) {
                    pipes[to].receiveEnergy(level, toDirection.getOpposite(), share);
                } else if (receptors[to] != null) {
                    receptors[to].getPowerProvider().receiveEnergy((float) share, toDirection.getOpposite());
                } else {
                    continue;
                }
                internalPower[from] -= share;
                displayPower[to] += (int) share;
            }
        }
    }

    private void requestFromReceptors(Level level, IPowerReceptor[] receptors) {
        for (int side = 0; side < SIDES; side++) {
            if (receptors[side] != null) {
                int request = receptors[side].powerRequest();
                if (request > 0) {
                    requestEnergy(level, Direction.from3DDataValue(side), request);
                }
            }
        }
    }

    private void forwardRequests(Level level, PowerTransport[] pipes) {
        for (int side = 0; side < SIDES; side++) {
            if (pipes[side] == null) {
                continue;
            }
            int others = 0;
            for (int other = 0; other < SIDES; other++) {
                if (other != side) {
                    others += powerQuery[other];
                }
            }
            if (others != 0) {
                pipes[side].requestEnergy(level, Direction.from3DDataValue(side).getOpposite(), others);
            }
        }
    }

    @Nullable
    private PowerTransport neighborPipe(Level level, Direction direction) {
        return level.getBlockEntity(pipe.getBlockPos().relative(direction)) instanceof PipeBlockEntity next && next.transport() instanceof PowerTransport power ? power : null;
    }

    /**
     * {@code from} の面から MJ が入ってくる。
     */
    public void receiveEnergy(Level level, Direction from, double amount) {
        if (pipe.behavior().interceptPower(pipe, from, amount)) {
            addDisplayedPower(from, amount);
            return;
        }
        step(level);
        int index = from.get3DDataValue();
        internalNextPower[index] += amount;
        if (internalNextPower[index] >= MAX_ENERGY) {
            BlockPos pos = pipe.getBlockPos();
            level.explode(null, pos.getX(), pos.getY(), pos.getZ(), OVERLOAD_EXPLOSION_POWER, Level.ExplosionInteraction.BLOCK);
        }
    }

    /**
     * {@code from} の面の先が MJ を要求している。
     */
    public void requestEnergy(Level level, Direction from, int amount) {
        step(level);
        nextPowerQuery[from.get3DDataValue()] += amount;
        pipe.behavior().onPowerRequested(pipe, from, amount);
    }

    /**
     * いずれかの面の先が MJ を要求しているか (この tick の要求)。
     */
    public boolean isRequestingPower() {
        for (int query : powerQuery) {
            if (query > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * 木の動力パイプが自分の面から送り出した量を表示に足す。
     */
    public void addDisplayedPower(Direction direction, double amount) {
        displayPower[direction.get3DDataValue()] += (int) amount;
    }

    /**
     * 描画用: 面ごとの直前の流量。
     */
    public int displayPower(Direction direction) {
        return displayPower[direction.get3DDataValue()];
    }

    @Override
    public double getPowerLoad() {
        double load = 0.0;
        for (int side = 0; side < SIDES; side++) {
            load = Math.max(load, Math.max(internalPower[side], internalNextPower[side]));
        }
        return load;
    }

    @Override
    public double getMaxPowerLoad() {
        return MAX_ENERGY;
    }

    @Override
    public boolean isEmpty() {
        for (int power : displayPower) {
            if (power != 0) {
                return false;
            }
        }
        return true;
    }

    @Override
    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putIntArray(TAG_QUERY, powerQuery);
        tag.putIntArray(TAG_NEXT_QUERY, nextPowerQuery);
        tag.putLongArray(TAG_POWER, toBits(internalPower));
        tag.putLongArray(TAG_NEXT_POWER, toBits(internalNextPower));
        tag.putIntArray(TAG_DISPLAY, displayPower);
        return tag;
    }

    @Override
    public CompoundTag saveForClient(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putIntArray(TAG_DISPLAY, displayPower);
        return tag;
    }

    @Override
    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        copyInto(tag.getIntArray(TAG_QUERY), powerQuery);
        copyInto(tag.getIntArray(TAG_NEXT_QUERY), nextPowerQuery);
        fromBits(tag.getLongArray(TAG_POWER), internalPower);
        fromBits(tag.getLongArray(TAG_NEXT_POWER), internalNextPower);
        copyInto(tag.getIntArray(TAG_DISPLAY), displayPower);
    }

    private static void copyInto(int[] source, int[] target) {
        System.arraycopy(source, 0, target, 0, Math.min(source.length, target.length));
    }

    private static long[] toBits(double[] values) {
        long[] bits = new long[values.length];
        for (int i = 0; i < values.length; i++) {
            bits[i] = Double.doubleToRawLongBits(values[i]);
        }
        return bits;
    }

    private static void fromBits(long[] bits, double[] target) {
        for (int i = 0; i < Math.min(bits.length, target.length); i++) {
            target[i] = Double.longBitsToDouble(bits[i]);
        }
    }
}
