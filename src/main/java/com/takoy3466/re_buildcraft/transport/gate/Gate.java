package com.takoy3466.re_buildcraft.transport.gate;

import com.takoy3466.re_buildcraft.api.IActionReceptor;
import com.takoy3466.re_buildcraft.transport.PipeBlockEntity;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * パイプに取り付けたゲート。トリガーとアクションの組を最大 8 つ持ち、10 tick ごとに評価して
 * レッドストーン信号・ワイヤー信号を出す。元実装: {@code Pipe} の gateKind / activatedTriggers / triggerParameters /
 * activatedActions / broadcastRedstone と resolveActions。
 */
public final class Gate {
    /** 評価の間隔 (旧 actionTracker.markTimeIfDelay(worldObj, 10))。 */
    private static final int RESOLVE_INTERVAL_TICKS = 10;
    private static final String TAG_KIND = "kind";
    private static final String TAG_TRIGGER = "trigger";
    private static final String TAG_ACTION = "action";
    private static final String TAG_PARAMETER = "parameter";
    private static final String TAG_REDSTONE = "redstone";

    private final GateKind kind;
    private final GateStatement.Trigger[] triggers = new GateStatement.Trigger[GateKind.MAX_ENTRIES];
    private final ItemStack[] parameters = new ItemStack[GateKind.MAX_ENTRIES];
    private final GateStatement.Action[] actions = new GateStatement.Action[GateKind.MAX_ENTRIES];
    private boolean broadcastRedstone;
    private long lastResolve = Long.MIN_VALUE;

    public Gate(GateKind kind) {
        this.kind = kind;
        Arrays.fill(parameters, ItemStack.EMPTY);
    }

    public GateKind kind() {
        return kind;
    }

    @Nullable
    public GateStatement.Trigger trigger(int index) {
        return triggers[index];
    }

    @Nullable
    public GateStatement.Action action(int index) {
        return actions[index];
    }

    public ItemStack parameter(int index) {
        return parameters[index];
    }

    /** トリガーを変えるとパラメーターは消える (旧 GuiGateInterface.mouseClicked)。 */
    public void setTrigger(int index, @Nullable GateStatement.Trigger trigger) {
        triggers[index] = trigger;
        parameters[index] = ItemStack.EMPTY;
    }

    public void setAction(int index, @Nullable GateStatement.Action action) {
        actions[index] = action;
    }

    public void setParameter(int index, ItemStack parameter) {
        parameters[index] = parameter;
    }

    public boolean isBroadcastingRedstone() {
        return broadcastRedstone;
    }

    public void tick(PipeBlockEntity pipe) {
        Level level = pipe.getLevel();
        if (level == null) {
            return;
        }
        long now = level.getGameTime();
        if (now < lastResolve || now - lastResolve >= RESOLVE_INTERVAL_TICKS) {
            lastResolve = now;
            resolveActions(pipe);
        }
    }

    /**
     * アクションごとに、それを選んだ組のトリガーを AND (AND ゲート) または OR (OR・単体ゲート) で合成し、
     * 成り立ったアクションを実行する。
     */
    private void resolveActions(PipeBlockEntity pipe) {
        Map<GateStatement.Action, Boolean> results = new LinkedHashMap<>();
        for (int i = 0; i < kind.entries(); i++) {
            GateStatement.Trigger trigger = triggers[i];
            GateStatement.Action action = actions[i];
            if (trigger == null || action == null) {
                continue;
            }
            boolean active = GateStatements.isActive(pipe, trigger, parameters[i]);
            results.merge(action, active, kind.isAnd() ? Boolean::logicalAnd : Boolean::logicalOr);
        }

        boolean previousRedstone = broadcastRedstone;
        broadcastRedstone = false;
        boolean signalsChanged = false;
        boolean[] broadcastSignals = new boolean[WireColor.values().length];
        for (Map.Entry<GateStatement.Action, Boolean> entry : results.entrySet()) {
            if (!entry.getValue()) {
                continue;
            }
            switch (entry.getKey()) {
                case GateStatement.RedstoneOutputAction redstone -> broadcastRedstone = true;
                case GateStatement.SignalOutputAction signal -> broadcastSignals[signal.color().ordinal()] = true;
                case GateStatement.MachineControlAction control -> sendControl(pipe, control);
            }
        }
        for (WireColor color : WireColor.values()) {
            signalsChanged |= pipe.signals().setBroadcast(color, broadcastSignals[color.ordinal()]);
        }

        if (previousRedstone != broadcastRedstone && pipe.getLevel() != null) {
            pipe.getLevel().updateNeighborsAt(pipe.getBlockPos(), pipe.getBlockState().getBlock());
            pipe.scheduleSync();
        }
        if (signalsChanged) {
            pipe.signals().update(pipe);
            pipe.scheduleSync();
        }
    }

    /** 隣接するすべての {@link IActionReceptor} へ運転指示を送る。 */
    private static void sendControl(PipeBlockEntity pipe, GateStatement.MachineControlAction action) {
        Level level = pipe.getLevel();
        if (level == null) {
            return;
        }
        for (Direction direction : Direction.values()) {
            if (level.getBlockEntity(pipe.getBlockPos().relative(direction)) instanceof IActionReceptor receptor) {
                receptor.actionActivated(action.control());
            }
        }
    }

    // ---- 保存 ---------------------------------------------------------------

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_KIND, kind.name());
        for (int i = 0; i < GateKind.MAX_ENTRIES; i++) {
            if (triggers[i] != null) {
                tag.putString(TAG_TRIGGER + i, triggers[i].id());
            }
            if (actions[i] != null) {
                tag.putString(TAG_ACTION + i, actions[i].id());
            }
            if (!parameters[i].isEmpty()) {
                tag.put(TAG_PARAMETER + i, parameters[i].save(registries));
            }
        }
        tag.putBoolean(TAG_REDSTONE, broadcastRedstone);
        return tag;
    }

    @Nullable
    public static Gate load(CompoundTag tag, HolderLookup.Provider registries) {
        GateKind kind;
        try {
            kind = GateKind.valueOf(tag.getString(TAG_KIND));
        } catch (IllegalArgumentException unknownKind) {
            // 保存データが壊れている、または将来の種類。ゲートが無いものとして扱う。
            return null;
        }
        Gate gate = new Gate(kind);
        for (int i = 0; i < GateKind.MAX_ENTRIES; i++) {
            gate.triggers[i] = GateStatements.trigger(tag.getString(TAG_TRIGGER + i));
            gate.actions[i] = GateStatements.action(tag.getString(TAG_ACTION + i));
            gate.parameters[i] = tag.contains(TAG_PARAMETER + i)
                    ? ItemStack.parseOptional(registries, tag.getCompound(TAG_PARAMETER + i)) : ItemStack.EMPTY;
        }
        gate.broadcastRedstone = tag.getBoolean(TAG_REDSTONE);
        return gate;
    }
}
