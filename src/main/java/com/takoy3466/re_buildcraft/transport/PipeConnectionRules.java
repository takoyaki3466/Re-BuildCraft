package com.takoy3466.re_buildcraft.transport;

import com.takoy3466.re_buildcraft.Config;
import com.takoy3466.re_buildcraft.api.IMachine;
import com.takoy3466.re_buildcraft.api.power.BuildCraftCapabilities;
import com.takoy3466.re_buildcraft.api.transport.IPipeConnectable;
import com.takoy3466.re_buildcraft.api.transport.IPipeEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * パイプが隣接ブロックと接続するかの判定。元実装: {@code Utils.checkPipesConnections} と
 * 各 {@code PipeTransport*.isPipeConnected} / {@code PipeLogic*.isPipeConnected}。
 */
public final class PipeConnectionRules {

    private PipeConnectionRules() {
    }

    /** {@code pos} にある {@code type} のパイプが {@code direction} 方向の隣と接続するか。 */
    public static boolean canConnect(Level level, BlockPos pos, Direction direction, PipeType type) {
        BlockPos neighborPos = pos.relative(direction);
        if (!level.isLoaded(neighborPos)) {
            return false;
        }
        if (level.getBlockState(neighborPos).getBlock() instanceof PipeBlock other) {
            return canConnectPipes(type, other.getPipeType());
        }
        BlockEntity neighbor = level.getBlockEntity(neighborPos);
        Direction sideOfNeighbor = direction.getOpposite();
        if (neighbor instanceof IPipeConnectable connectable && !connectable.isPipeConnected(sideOfNeighbor)) {
            return false;
        }
        return switch (type.kind()) {
            case ITEMS -> neighbor instanceof IPipeEntry
                    || level.getCapability(Capabilities.ItemHandler.BLOCK, neighborPos, sideOfNeighbor) != null
                    || neighbor instanceof IMachine machine && machine.manageSolids();
            case FLUIDS -> hasTanks(level.getCapability(Capabilities.FluidHandler.BLOCK, neighborPos, sideOfNeighbor))
                    || neighbor instanceof IMachine machine && machine.manageLiquids();
            case POWER -> neighbor instanceof IMachine
                    || level.getCapability(BuildCraftCapabilities.POWER_RECEPTOR, neighborPos, sideOfNeighbor) != null;
            case STRUCTURE -> false;
        };
    }

    /** パイプ同士: 同じ輸送方式で、互いの素材が相手を受け入れる場合のみ (設定で素材制限を外せる)。 */
    public static boolean canConnectPipes(PipeType self, PipeType other) {
        if (self.kind() != other.kind()) {
            return false;
        }
        if (Config.PIPES_ALWAYS_CONNECT.get()) {
            return true;
        }
        return self.material().accepts(other.material(), self.kind()) && other.material().accepts(self.material(), other.kind());
    }

    private static boolean hasTanks(IFluidHandler handler) {
        return handler != null && handler.getTanks() > 0;
    }
}
