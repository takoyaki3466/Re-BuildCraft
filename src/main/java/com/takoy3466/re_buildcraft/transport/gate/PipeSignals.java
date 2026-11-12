package com.takoy3466.re_buildcraft.transport.gate;

import com.takoy3466.re_buildcraft.transport.PipeBlockEntity;
import com.takoy3466.re_buildcraft.transport.PipeKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

/**
 * パイプに張られたワイヤーと、その信号の強さ。元実装: {@code Pipe} の wireSet / signalStrength / broadcastSignal と
 * updateSignalState / readNearbyPipesSignal / receiveSignal。
 * <p>
 * ゲートが信号を出すと強さ 255 になり、ワイヤーでつながった隣のパイプへ 1 ずつ弱まりながら伝わる。
 */
public final class PipeSignals {
    private static final int MAX_STRENGTH = 255;
    private static final int COLORS = WireColor.values().length;
    private static final String TAG_WIRES = "wires";
    private static final String TAG_STRENGTH = "strength";
    private static final String TAG_BROADCAST = "broadcast";

    private final boolean[] wires = new boolean[COLORS];
    private final int[] strength = new int[COLORS];
    private final boolean[] broadcast = new boolean[COLORS];
    private boolean updateScheduled;

    public boolean hasWire(WireColor color) {
        return wires[color.ordinal()];
    }

    public boolean hasAnyWire() {
        for (boolean wire : wires) {
            if (wire) {
                return true;
            }
        }
        return false;
    }

    public void setWire(WireColor color, boolean present) {
        wires[color.ordinal()] = present;
        if (!present) {
            strength[color.ordinal()] = 0;
            broadcast[color.ordinal()] = false;
        }
    }

    public int strength(WireColor color) {
        return strength[color.ordinal()];
    }

    public boolean isBroadcasting(WireColor color) {
        return broadcast[color.ordinal()];
    }

    /** ゲートのアクションによる信号の送出状態を設定する。変化があれば true。 */
    public boolean setBroadcast(WireColor color, boolean value) {
        boolean changed = broadcast[color.ordinal()] != value;
        broadcast[color.ordinal()] = value;
        return changed;
    }

    /** 予約された信号の再計算を行う。 */
    public void tick(PipeBlockEntity pipe) {
        if (updateScheduled) {
            updateScheduled = false;
            update(pipe);
        }
    }

    /** 全色の信号を計算し直し、隣へ伝える (旧 updateSignalState)。 */
    public void update(PipeBlockEntity pipe) {
        for (WireColor color : WireColor.values()) {
            updateColor(pipe, color);
        }
    }

    private void updateColor(PipeBlockEntity pipe, WireColor color) {
        if (!hasWire(color)) {
            return;
        }
        if (isBroadcasting(color)) {
            receive(pipe, MAX_STRENGTH, color);
        } else {
            readNearbySignal(pipe, color);
        }
        int current = strength(color);
        if (current > 1) {
            for (Direction direction : Direction.values()) {
                PipeBlockEntity neighbor = wiredNeighbor(pipe, direction, color);
                if (neighbor != null) {
                    neighbor.signals().receive(neighbor, current - 1, color);
                }
            }
        }
    }

    private void readNearbySignal(PipeBlockEntity pipe, WireColor color) {
        boolean found = false;
        for (Direction direction : Direction.values()) {
            PipeBlockEntity neighbor = wiredNeighbor(pipe, direction, color);
            if (neighbor != null) {
                found |= receive(pipe, neighbor.signals().strength(color) - 1, color);
            }
        }
        if (!found && strength(color) != 0) {
            strength[color.ordinal()] = 0;
            pipe.scheduleSync();
            for (Direction direction : Direction.values()) {
                if (pipe.getLevel() != null
                        && pipe.getLevel().getBlockEntity(pipe.getBlockPos().relative(direction)) instanceof PipeBlockEntity neighbor) {
                    neighbor.signals().updateScheduled = true;
                }
            }
        }
    }

    private boolean receive(PipeBlockEntity pipe, int value, WireColor color) {
        int previous = strength(color);
        if (value >= previous && value != 0) {
            strength[color.ordinal()] = value;
            updateScheduled = true;
            if (previous == 0) {
                pipe.scheduleSync();
            }
            return true;
        }
        return false;
    }

    /**
     * ワイヤーでつながった隣のパイプ。同じ色のワイヤーがあり、パイプ同士が接続しているか、どちらかが構造パイプなら
     * つながっている (旧 isWireConnectedTo)。
     */
    @Nullable
    private static PipeBlockEntity wiredNeighbor(PipeBlockEntity pipe, Direction direction, WireColor color) {
        Level level = pipe.getLevel();
        if (level == null || !(level.getBlockEntity(pipe.getBlockPos().relative(direction)) instanceof PipeBlockEntity neighbor)
                || !neighbor.signals().hasWire(color)) {
            return null;
        }
        boolean structure = pipe.type().kind() == PipeKind.STRUCTURE || neighbor.type().kind() == PipeKind.STRUCTURE;
        return structure || pipe.isConnected(direction) ? neighbor : null;
    }

    /** 撤去時、張られていたワイヤーを落とす。 */
    public void dropWires(Level level, BlockPos pos) {
        for (WireColor color : WireColor.values()) {
            if (hasWire(color)) {
                Block.popResource(level, pos, new ItemStack(color.item()));
            }
        }
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        byte[] wireBytes = new byte[COLORS];
        byte[] broadcastBytes = new byte[COLORS];
        for (int i = 0; i < COLORS; i++) {
            wireBytes[i] = (byte) (wires[i] ? 1 : 0);
            broadcastBytes[i] = (byte) (broadcast[i] ? 1 : 0);
        }
        tag.putByteArray(TAG_WIRES, wireBytes);
        tag.putByteArray(TAG_BROADCAST, broadcastBytes);
        tag.putIntArray(TAG_STRENGTH, strength);
        return tag;
    }

    public void load(CompoundTag tag) {
        byte[] wireBytes = tag.getByteArray(TAG_WIRES);
        byte[] broadcastBytes = tag.getByteArray(TAG_BROADCAST);
        int[] savedStrength = tag.getIntArray(TAG_STRENGTH);
        for (int i = 0; i < COLORS; i++) {
            wires[i] = i < wireBytes.length && wireBytes[i] != 0;
            broadcast[i] = i < broadcastBytes.length && broadcastBytes[i] != 0;
            strength[i] = i < savedStrength.length ? savedStrength[i] : 0;
        }
    }
}
