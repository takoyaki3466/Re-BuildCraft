package com.takoy3466.re_buildcraft.transport.behavior;

import com.takoy3466.re_buildcraft.transport.PipeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import org.jetbrains.annotations.Nullable;

/**
 * 木のパイプの「取り出し元」の面の選び方。元実装: {@code buildcraft.transport.PipeLogicWood}。
 * 輸送・防水・導電のどの木のパイプも同じ規則で、パイプ以外のインベントリ/液体容器のある面を取り出し元にする。
 */
public final class WoodSource {
    private static final int SIDES = Direction.values().length;

    private WoodSource() {
    }

    /** 取り出し元になれる面か (旧 isInput)。 */
    public static boolean isInput(PipeBlockEntity pipe, Direction direction) {
        Level level = pipe.getLevel();
        if (level == null || !pipe.isConnected(direction)) {
            return false;
        }
        BlockPos neighborPos = pipe.getBlockPos().relative(direction);
        if (level.getBlockEntity(neighborPos) instanceof PipeBlockEntity) {
            return false;
        }
        Direction side = direction.getOpposite();
        return level.getCapability(Capabilities.ItemHandler.BLOCK, neighborPos, side) != null
                || level.getCapability(Capabilities.FluidHandler.BLOCK, neighborPos, side) != null;
    }

    /** 現在の取り出し元の次から順に探して切り替える (レンチ操作, 旧 switchSource)。 */
    public static void switchSource(PipeBlockEntity pipe) {
        Direction current = pipe.getSpecialSide();
        int start = current == null ? 0 : current.get3DDataValue() + 1;
        Direction next = null;
        for (int step = 0; step < SIDES; step++) {
            Direction candidate = Direction.from3DDataValue((start + step) % SIDES);
            if (isInput(pipe, candidate)) {
                next = candidate;
                break;
            }
        }
        if (next != current) {
            pipe.setSpecialSide(next);
        }
    }

    /** 取り出し元が無い、または無効になっていれば選び直す (旧 switchSourceIfNeeded)。 */
    public static void switchSourceIfNeeded(PipeBlockEntity pipe) {
        Direction current = pipe.getSpecialSide();
        if (current == null || !isInput(pipe, current)) {
            switchSource(pipe);
        }
    }

    @Nullable
    public static Direction source(PipeBlockEntity pipe) {
        return pipe.getSpecialSide();
    }
}
