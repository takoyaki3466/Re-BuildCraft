package com.takoy3466.re_buildcraft.transport.gate;

import com.takoy3466.re_buildcraft.api.menu.PlayerInventorySlots;
import com.takoy3466.re_buildcraft.registry.ModMenus;
import com.takoy3466.re_buildcraft.transport.PipeBlockEntity;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * ゲートの設定画面のメニュー。元実装: {@code CraftingGateInterface} + {@code GuiGateInterface} の操作部分。
 * <p>
 * 旧実装ではクライアントがパイプを直接書き換えていたが、ここでは枠のクリックをメニューボタン
 * ({@link #buttonId}) としてサーバーへ送り、候補の選択・検証をサーバーで行う。
 * 選択中のトリガー等はパイプの同期データでクライアントへ届き、トリガーの成立状況だけをメニューで同期する。
 */
public class GateMenu extends AbstractContainerMenu {
    public static final int REGION_TRIGGER = 0;
    public static final int REGION_ACTION = 1;
    public static final int REGION_PARAMETER = 2;
    private static final int REGIONS = 3;
    private static final int PLAYER_INVENTORY_Y = 123;

    private final PipeBlockEntity pipe;
    private final ContainerData activeMask;

    public GateMenu(int containerId, Inventory playerInventory, PipeBlockEntity pipe) {
        this(containerId, playerInventory, pipe, null);
    }

    private GateMenu(int containerId, Inventory playerInventory, PipeBlockEntity pipe, @Nullable ContainerData clientData) {
        super(ModMenus.GATE.get(), containerId);
        this.pipe = pipe;
        this.activeMask = clientData != null ? clientData : new ActiveMask();
        PlayerInventorySlots.add(this::addSlot, playerInventory, PLAYER_INVENTORY_Y);
        addDataSlots(activeMask);
    }

    public static GateMenu createClient(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        if (playerInventory.player.level().getBlockEntity(extraData.readBlockPos()) instanceof PipeBlockEntity pipe) {
            return new GateMenu(containerId, playerInventory, pipe, new SimpleContainerData(1));
        }
        throw new IllegalStateException("Gate menu opened for a position without a pipe");
    }

    /** 画面のクリックをボタン番号に変換する。 */
    public static int buttonId(int region, int index, boolean reverse) {
        return ((region * GateKind.MAX_ENTRIES) + index) * 2 + (reverse ? 1 : 0);
    }

    public PipeBlockEntity pipe() {
        return pipe;
    }

    public boolean isTriggerActive(int index) {
        return (activeMask.get(0) >> index & 1) != 0;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        Gate gate = pipe.getGate();
        if (gate == null) {
            return false;
        }
        boolean reverse = (id & 1) != 0;
        int slot = id >> 1;
        int region = slot / GateKind.MAX_ENTRIES;
        int index = slot % GateKind.MAX_ENTRIES;
        if (region >= REGIONS || index >= gate.kind().entries()) {
            return false;
        }
        switch (region) {
            case REGION_TRIGGER -> gate.setTrigger(index,
                    cycle(GateStatements.potentialTriggers(pipe, gate.kind()), gate.trigger(index), reverse));
            case REGION_ACTION -> gate.setAction(index,
                    cycle(GateStatements.potentialActions(pipe, gate.kind()), gate.action(index), reverse));
            default -> {
                GateStatement.Trigger trigger = gate.trigger(index);
                if (!gate.kind().hasParameters() || trigger == null || !trigger.hasParameter()) {
                    return false;
                }
                ItemStack carried = getCarried();
                gate.setParameter(index, carried.isEmpty() ? ItemStack.EMPTY : carried.copyWithCount(1));
            }
        }
        pipe.scheduleSync();
        pipe.setChanged();
        return true;
    }

    /**
     * 候補を順送り (右クリックは逆送り) する。未選択なら先頭、最後を過ぎたら未選択に戻る。
     */
    @Nullable
    private static <T> T cycle(List<T> candidates, @Nullable T current, boolean reverse) {
        if (candidates.isEmpty()) {
            return null;
        }
        if (current == null) {
            return candidates.get(0);
        }
        int index = candidates.indexOf(current);
        int next = index + (reverse ? -1 : 1);
        return index < 0 || next < 0 || next >= candidates.size() ? null : candidates.get(next);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return pipe.getGate() != null && Container.stillValidBlockEntity(pipe, player);
    }

    /** サーバー側: 各組のトリガーが成り立っているかをビットで返す。 */
    private final class ActiveMask implements ContainerData {
        @Override
        public int get(int dataIndex) {
            Gate gate = pipe.getGate();
            if (gate == null) {
                return 0;
            }
            int mask = 0;
            for (int i = 0; i < gate.kind().entries(); i++) {
                if (gate.trigger(i) != null && GateStatements.isActive(pipe, gate.trigger(i), gate.parameter(i))) {
                    mask |= 1 << i;
                }
            }
            return mask;
        }

        @Override
        public void set(int dataIndex, int value) {
            // 表示専用。
        }

        @Override
        public int getCount() {
            return 1;
        }
    }
}
