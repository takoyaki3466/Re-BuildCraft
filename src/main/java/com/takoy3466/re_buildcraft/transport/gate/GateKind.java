package com.takoy3466.re_buildcraft.transport.gate;

import com.takoy3466.re_buildcraft.registry.ModItems;
import java.util.function.Supplier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * ゲートの種類。元実装: {@code Pipe.GateKind} (None を除く) と {@code ItemGate} のダメージ値 0 - 6。
 *
 * @param entries 設定できるトリガーとアクションの組の数 (旧 GuiGateInterface.nbEntries)
 * @param and     複数の条件を AND で組み合わせるか (OR / Single は false)
 */
public enum GateKind {
    SINGLE(1, false, ModItems.GATE),
    AND_2(2, true, ModItems.IRON_AND_GATE),
    OR_2(2, false, ModItems.IRON_OR_GATE),
    AND_3(4, true, ModItems.GOLD_AND_GATE),
    OR_3(4, false, ModItems.GOLD_OR_GATE),
    AND_4(8, true, ModItems.DIAMOND_AND_GATE),
    OR_4(8, false, ModItems.DIAMOND_OR_GATE);

    /** 1 つのゲートが持てる組の最大数。 */
    public static final int MAX_ENTRIES = 8;

    private final int entries;
    private final boolean and;
    private final Supplier<? extends Item> item;

    GateKind(int entries, boolean and, Supplier<? extends Item> item) {
        this.entries = entries;
        this.and = and;
        this.item = item;
    }

    public int entries() {
        return entries;
    }

    public boolean isAnd() {
        return and;
    }

    /** トリガーにパラメーター (アイテム指定) を付けられるか。金ゲート以上。 */
    public boolean hasParameters() {
        return ordinal() >= AND_3.ordinal();
    }

    public ItemStack toItemStack() {
        return new ItemStack(item.get());
    }

    @Nullable
    public static GateKind byItem(Item candidate) {
        for (GateKind kind : values()) {
            if (kind.item.get() == candidate) {
                return kind;
            }
        }
        return null;
    }
}
