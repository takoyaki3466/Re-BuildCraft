package com.takoy3466.re_buildcraft.transport.gate;

import com.takoy3466.re_buildcraft.registry.ModItems;
import java.util.Locale;
import java.util.function.Supplier;
import net.minecraft.world.item.Item;

/**
 * パイプワイヤーの色。元実装: {@code buildcraft.api.IPipe.WireColor}。
 *
 * @param minimumGate この色の信号トリガー/アクションを使えるゲートの最低段階 (旧 Pipe.getActions / PipeTriggerProvider)
 */
public enum WireColor {
    RED(GateKind.AND_2, ModItems.RED_PIPE_WIRE),
    BLUE(GateKind.AND_3, ModItems.BLUE_PIPE_WIRE),
    GREEN(GateKind.AND_4, ModItems.GREEN_PIPE_WIRE),
    YELLOW(GateKind.AND_4, ModItems.YELLOW_PIPE_WIRE);

    private final GateKind minimumGate;
    private final Supplier<? extends Item> item;

    WireColor(GateKind minimumGate, Supplier<? extends Item> item) {
        this.minimumGate = minimumGate;
        this.item = item;
    }

    /** {@code kind} のゲートでこの色の信号を扱えるか。 */
    public boolean usableWith(GateKind kind) {
        return kind.ordinal() >= minimumGate.ordinal();
    }

    public Item item() {
        return item.get();
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
