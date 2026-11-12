package com.takoy3466.re_buildcraft.transport.behavior;

import com.takoy3466.re_buildcraft.transport.PipeBlock;
import com.takoy3466.re_buildcraft.transport.PipeBlockEntity;
import com.takoy3466.re_buildcraft.transport.PipeMaterial;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/**
 * 鉄のパイプ (輸送・防水)。1 つの面にだけ送り出し、レンチかレッドストーン信号の変化で出力面を切り替える。
 * 元実装: {@code buildcraft.transport.PipeLogicIron}。
 */
public class IronOutputBehavior implements PipeBehavior {
    private static final int SIDES = Direction.values().length;

    private boolean lastPower;

    @Override
    public boolean outputOpen(PipeBlockEntity pipe, Direction direction) {
        return direction == pipe.getSpecialSide();
    }

    @Override
    public void initialize(PipeBlockEntity pipe) {
        Level level = pipe.getLevel();
        if (level != null) {
            lastPower = level.hasNeighborSignal(pipe.getBlockPos());
        }
        if (pipe.getSpecialSide() == null) {
            switchPosition(pipe);
        }
    }

    @Override
    public void onNeighborChanged(PipeBlockEntity pipe) {
        Level level = pipe.getLevel();
        if (level == null) {
            return;
        }
        boolean powered = level.hasNeighborSignal(pipe.getBlockPos());
        if (powered != lastPower) {
            switchPosition(pipe);
            lastPower = powered;
        }
    }

    @Override
    public boolean onWrench(PipeBlockEntity pipe) {
        switchPosition(pipe);
        return true;
    }

    /** 次の「送り出せる面」(木のパイプ以外の接続先) へ切り替える (旧 switchPosition)。 */
    private static void switchPosition(PipeBlockEntity pipe) {
        Level level = pipe.getLevel();
        if (level == null) {
            return;
        }
        Direction current = pipe.getSpecialSide();
        int start = current == null ? 0 : current.get3DDataValue() + 1;
        for (int step = 0; step < SIDES; step++) {
            Direction candidate = Direction.from3DDataValue((start + step) % SIDES);
            if (pipe.isConnected(candidate) && !isWoodenPipe(level, pipe, candidate)) {
                pipe.setSpecialSide(candidate);
                return;
            }
        }
    }

    private static boolean isWoodenPipe(Level level, PipeBlockEntity pipe, Direction direction) {
        return level.getBlockState(pipe.getBlockPos().relative(direction)).getBlock() instanceof PipeBlock other
                && other.getPipeType().material() == PipeMaterial.WOOD;
    }
}
