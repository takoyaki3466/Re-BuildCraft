package com.takoy3466.re_buildcraft.transport.gate;

import com.takoy3466.re_buildcraft.api.IMachine;
import com.takoy3466.re_buildcraft.api.MachineControl;
import com.takoy3466.re_buildcraft.api.engine.EnergyStage;
import com.takoy3466.re_buildcraft.api.engine.EngineBlockEntity;
import com.takoy3466.re_buildcraft.transport.PipeBlockEntity;
import com.takoy3466.re_buildcraft.transport.PipeKind;
import com.takoy3466.re_buildcraft.transport.gate.NeighborTriggers.ContainerState;
import com.takoy3466.re_buildcraft.transport.gate.PipeTriggers.ContentsKind;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * BCx が登録していたトリガー・アクションの一覧と、ゲートの画面に並べる候補の決め方。
 * 元実装: {@code BuildCraftCore} / {@code BuildCraftEnergy} / {@code BuildCraftTransport} の static 初期化、
 * {@code DefaultTriggerProvider} / {@code DefaultActionProvider} / {@code PipeTriggerProvider} / {@code Pipe.getActions}。
 * <p>
 * 旧実装の「機械の On/Off/Loop」アクションは、受け付ける機械 ({@code IActionReceptor}: フィラー) が未移行のため登録していない。
 */
public final class GateStatements {
    public static final GateStatement.Trigger INVENTORY_EMPTY = new NeighborTriggers.Inventory("inventory_empty", 36, ContainerState.EMPTY);
    public static final GateStatement.Trigger INVENTORY_CONTAINS = new NeighborTriggers.Inventory("inventory_contains", 37, ContainerState.CONTAINS);
    public static final GateStatement.Trigger INVENTORY_SPACE = new NeighborTriggers.Inventory("inventory_space", 38, ContainerState.SPACE);
    public static final GateStatement.Trigger INVENTORY_FULL = new NeighborTriggers.Inventory("inventory_full", 39, ContainerState.FULL);
    public static final GateStatement.Trigger LIQUID_EMPTY = new NeighborTriggers.Liquid("liquid_empty", 32, ContainerState.EMPTY);
    public static final GateStatement.Trigger LIQUID_CONTAINS = new NeighborTriggers.Liquid("liquid_contains", 33, ContainerState.CONTAINS);
    public static final GateStatement.Trigger LIQUID_SPACE = new NeighborTriggers.Liquid("liquid_space", 34, ContainerState.SPACE);
    public static final GateStatement.Trigger LIQUID_FULL = new NeighborTriggers.Liquid("liquid_full", 35, ContainerState.FULL);
    public static final GateStatement.Trigger MACHINE_ACTIVE = new NeighborTriggers.Machine("machine_active", 64, true);
    public static final GateStatement.Trigger MACHINE_INACTIVE = new NeighborTriggers.Machine("machine_inactive", 65, false);
    public static final GateStatement.Trigger ENGINE_BLUE = new NeighborTriggers.EngineHeat("engine_blue", 16, EnergyStage.BLUE);
    public static final GateStatement.Trigger ENGINE_GREEN = new NeighborTriggers.EngineHeat("engine_green", 17, EnergyStage.GREEN);
    public static final GateStatement.Trigger ENGINE_YELLOW = new NeighborTriggers.EngineHeat("engine_yellow", 18, EnergyStage.YELLOW);
    public static final GateStatement.Trigger ENGINE_RED = new NeighborTriggers.EngineHeat("engine_red", 19, EnergyStage.RED);
    public static final GateStatement.Trigger REDSTONE_ACTIVE = new PipeTriggers.RedstoneInput("redstone_active", 0, true);
    public static final GateStatement.Trigger REDSTONE_INACTIVE = new PipeTriggers.RedstoneInput("redstone_inactive", 1, false);
    public static final GateStatement.Trigger PIPE_EMPTY = new PipeTriggers.Contents("pipe_empty", 48, ContentsKind.EMPTY);
    public static final GateStatement.Trigger PIPE_ITEMS = new PipeTriggers.Contents("pipe_items", 49, ContentsKind.ITEMS);
    public static final GateStatement.Trigger PIPE_LIQUIDS = new PipeTriggers.Contents("pipe_liquids", 50, ContentsKind.LIQUIDS);
    public static final GateStatement.Trigger PIPE_ENERGY = new PipeTriggers.Contents("pipe_energy", 51, ContentsKind.ENERGY);
    public static final GateStatement.Action REDSTONE_OUTPUT = new GateStatement.RedstoneOutputAction("redstone_output", 0);
    public static final GateStatement.Action MACHINE_ON = new GateStatement.MachineControlAction("machine_on", 66, MachineControl.ON);
    public static final GateStatement.Action MACHINE_OFF = new GateStatement.MachineControlAction("machine_off", 67, MachineControl.OFF);
    public static final GateStatement.Action MACHINE_LOOP = new GateStatement.MachineControlAction("machine_loop", 68, MachineControl.LOOP);

    /** 色ごとの信号トリガー (オン/オフ) と信号アクション。アイコン番号は旧 TriggerPipeSignal / ActionSignalOutput。 */
    private static final int[] SIGNAL_ON_ICONS = {3, 5, 7, 9};
    private static final int[] SIGNAL_OFF_ICONS = {2, 4, 6, 8};
    private static final List<GateStatement.Trigger> SIGNAL_ACTIVE = new ArrayList<>();
    private static final List<GateStatement.Trigger> SIGNAL_INACTIVE = new ArrayList<>();
    private static final List<GateStatement.Action> SIGNAL_OUTPUT = new ArrayList<>();

    static {
        for (WireColor color : WireColor.values()) {
            SIGNAL_ACTIVE.add(new PipeTriggers.Signal(color.id() + "_signal_active", SIGNAL_ON_ICONS[color.ordinal()], color, true));
            SIGNAL_INACTIVE.add(new PipeTriggers.Signal(color.id() + "_signal_inactive", SIGNAL_OFF_ICONS[color.ordinal()], color, false));
            SIGNAL_OUTPUT.add(new GateStatement.SignalOutputAction(color.id() + "_signal_output", SIGNAL_ON_ICONS[color.ordinal()], color));
        }
    }

    private static final Map<String, GateStatement.Trigger> TRIGGERS = index(allTriggers(), GateStatement::id);
    private static final Map<String, GateStatement.Action> ACTIONS = index(allActions(), GateStatement::id);

    private GateStatements() {
    }

    private static List<GateStatement.Trigger> allTriggers() {
        List<GateStatement.Trigger> triggers = new ArrayList<>(List.of(
                INVENTORY_EMPTY, INVENTORY_CONTAINS, INVENTORY_SPACE, INVENTORY_FULL,
                LIQUID_EMPTY, LIQUID_CONTAINS, LIQUID_SPACE, LIQUID_FULL,
                MACHINE_ACTIVE, MACHINE_INACTIVE,
                ENGINE_BLUE, ENGINE_GREEN, ENGINE_YELLOW, ENGINE_RED,
                REDSTONE_ACTIVE, REDSTONE_INACTIVE,
                PIPE_EMPTY, PIPE_ITEMS, PIPE_LIQUIDS, PIPE_ENERGY));
        triggers.addAll(SIGNAL_ACTIVE);
        triggers.addAll(SIGNAL_INACTIVE);
        return triggers;
    }

    private static List<GateStatement.Action> allActions() {
        List<GateStatement.Action> actions = new ArrayList<>();
        actions.add(REDSTONE_OUTPUT);
        actions.addAll(SIGNAL_OUTPUT);
        actions.addAll(List.of(MACHINE_ON, MACHINE_OFF, MACHINE_LOOP));
        return actions;
    }

    private static <T> Map<String, T> index(List<T> values, Function<T, String> key) {
        return values.stream().collect(Collectors.toUnmodifiableMap(key, Function.identity()));
    }

    @Nullable
    public static GateStatement.Trigger trigger(String id) {
        return TRIGGERS.get(id);
    }

    @Nullable
    public static GateStatement.Action action(String id) {
        return ACTIONS.get(id);
    }

    // ---- 画面に並べる候補 -----------------------------------------------------

    /** パイプ自身のトリガーと、周囲のブロックから得られるトリガー (重複なし・見つかった順)。 */
    public static List<GateStatement.Trigger> potentialTriggers(PipeBlockEntity pipe, GateKind kind) {
        Set<GateStatement.Trigger> triggers = new LinkedHashSet<>(pipeTriggers(pipe, kind));
        Level level = pipe.getLevel();
        if (level != null) {
            for (Direction direction : Direction.values()) {
                triggers.addAll(neighborTriggers(level, pipe.getBlockPos().relative(direction), direction.getOpposite()));
            }
        }
        return List.copyOf(triggers);
    }

    /**
     * レッドストーン出力は常に、信号出力は張られたワイヤーとゲートの段階に応じて、
     * 運転指示は隣接する機械が受け付ける場合に (旧 DefaultActionProvider)。
     */
    public static List<GateStatement.Action> potentialActions(PipeBlockEntity pipe, GateKind kind) {
        List<GateStatement.Action> actions = new ArrayList<>();
        for (WireColor color : WireColor.values()) {
            if (pipe.signals().hasWire(color) && color.usableWith(kind)) {
                actions.add(SIGNAL_OUTPUT.get(color.ordinal()));
            }
        }
        actions.add(REDSTONE_OUTPUT);
        if (hasControllableNeighbor(pipe)) {
            actions.addAll(List.of(MACHINE_ON, MACHINE_OFF, MACHINE_LOOP));
        }
        return actions;
    }

    private static boolean hasControllableNeighbor(PipeBlockEntity pipe) {
        Level level = pipe.getLevel();
        if (level == null) {
            return false;
        }
        for (Direction direction : Direction.values()) {
            if (level.getBlockEntity(pipe.getBlockPos().relative(direction)) instanceof IMachine machine && machine.allowActions()) {
                return true;
            }
        }
        return false;
    }

    private static List<GateStatement.Trigger> pipeTriggers(PipeBlockEntity pipe, GateKind kind) {
        List<GateStatement.Trigger> triggers = new ArrayList<>();
        for (WireColor color : WireColor.values()) {
            if (pipe.signals().hasWire(color) && color.usableWith(kind)) {
                triggers.add(SIGNAL_ACTIVE.get(color.ordinal()));
                triggers.add(SIGNAL_INACTIVE.get(color.ordinal()));
            }
        }
        PipeKind pipeKind = pipe.type().kind();
        if (pipeKind != PipeKind.STRUCTURE) {
            triggers.add(PIPE_EMPTY);
            triggers.add(switch (pipeKind) {
                case ITEMS -> PIPE_ITEMS;
                case FLUIDS -> PIPE_LIQUIDS;
                default -> PIPE_ENERGY;
            });
        }
        return triggers;
    }

    private static List<GateStatement.Trigger> neighborTriggers(Level level, BlockPos neighborPos, Direction side) {
        if (!level.isLoaded(neighborPos)) {
            return List.of();
        }
        BlockEntity blockEntity = level.getBlockEntity(neighborPos);
        if (blockEntity instanceof PipeBlockEntity) {
            return List.of();
        }
        List<GateStatement.Trigger> triggers = new ArrayList<>();
        if (blockEntity instanceof EngineBlockEntity engine) {
            // エンジンは既定の候補を上書きする (旧 TileEngine.getTriggers)。
            triggers.addAll(List.of(ENGINE_BLUE, ENGINE_GREEN, ENGINE_YELLOW, ENGINE_RED));
            if (engine.getEngine().getFluidHandler() != null) {
                triggers.addAll(List.of(LIQUID_EMPTY, LIQUID_CONTAINS, LIQUID_SPACE, LIQUID_FULL));
            } else if (engine.getEngine().usesInventory()) {
                triggers.addAll(List.of(INVENTORY_EMPTY, INVENTORY_CONTAINS, INVENTORY_SPACE, INVENTORY_FULL));
            }
            return triggers;
        }
        IItemHandler inventory = level.getCapability(Capabilities.ItemHandler.BLOCK, neighborPos, side);
        if (inventory != null && inventory.getSlots() > 0) {
            triggers.addAll(List.of(INVENTORY_EMPTY, INVENTORY_CONTAINS, INVENTORY_SPACE, INVENTORY_FULL));
        }
        IFluidHandler fluids = level.getCapability(Capabilities.FluidHandler.BLOCK, neighborPos, side);
        if (fluids != null && fluids.getTanks() > 0) {
            triggers.addAll(List.of(LIQUID_EMPTY, LIQUID_CONTAINS, LIQUID_SPACE, LIQUID_FULL));
        }
        if (blockEntity instanceof IMachine) {
            triggers.addAll(List.of(MACHINE_ACTIVE, MACHINE_INACTIVE));
        }
        if (level.getBlockState(neighborPos).isSignalSource()) {
            triggers.addAll(List.of(REDSTONE_ACTIVE, REDSTONE_INACTIVE));
        }
        return triggers;
    }

    /** トリガーが成り立っているか (旧 Pipe.isNearbyTriggerActive)。 */
    public static boolean isActive(PipeBlockEntity pipe, @Nullable GateStatement.Trigger trigger, ItemStack parameter) {
        if (trigger instanceof GateStatement.PipeTrigger pipeTrigger) {
            return pipeTrigger.isActive(pipe, parameter);
        }
        if (!(trigger instanceof GateStatement.NeighborTrigger neighborTrigger) || pipe.getLevel() == null) {
            return false;
        }
        Level level = pipe.getLevel();
        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = pipe.getBlockPos().relative(direction);
            if (level.isLoaded(neighborPos) && level.getBlockEntity(neighborPos) != null
                    && !(level.getBlockEntity(neighborPos) instanceof PipeBlockEntity)
                    && neighborTrigger.isActive(level, neighborPos, direction.getOpposite(), parameter)) {
                return true;
            }
        }
        return false;
    }
}
