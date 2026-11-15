package com.takoy3466.re_buildcraft.transport.behavior;

import com.takoy3466.re_buildcraft.api.power.PowerProvider;
import com.takoy3466.re_buildcraft.transport.ItemTransport;
import com.takoy3466.re_buildcraft.transport.PipeBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * 木の輸送パイプ。MJ を受けると、取り出し元のインベントリから先頭のアイテムを MJ の量だけ (1 MJ = 1 個) 取り出す。
 * 元実装: {@code buildcraft.transport.pipes.PipeItemsWood}。
 * <p>
 * 旧実装は自動作業台などの特殊インベントリ ({@code ISpecialInventory}) から、蓄積量に関係なく完成品 1 回分を
 * まとめて取り出していた。1.21.1 ではどのインベントリも {@code IItemHandler} で扱うため、「指定した個数では取り出せず、
 * まとめてなら取り出せる枠」を特殊インベントリと同じ扱いにする (蓄積がアイテム数に足りなくても 1 回分を取り出す)。
 * 蓄積は毎 tick 失われる (旧 configurePowerPerdition(64, 1)) ため、この扱いが無いと低出力のエンジンでは複数個のクラフト結果を取り出せない。
 */
public class WoodItemExtractionBehavior implements PipeBehavior {
    private static final int MAX_ENERGY = 64;
    /** 取り出したアイテムの初速 (旧実装の 0.04)。 */
    private static final float EXTRACT_SPEED = 0.04F;

    private final PowerProvider powerProvider = new PowerProvider();

    public WoodItemExtractionBehavior() {
        powerProvider.configure(1, MAX_ENERGY, 1, MAX_ENERGY);
        powerProvider.configurePowerPerdition(MAX_ENERGY, 1);
    }

    @Override
    public PowerProvider powerProvider() {
        return powerProvider;
    }

    @Override
    public void doWork(PipeBlockEntity pipe) {
        Level level = pipe.getLevel();
        Direction source = WoodSource.source(pipe);
        if (level == null || source == null || powerProvider.getEnergyStored() <= 0.0F
                || !(pipe.transport() instanceof ItemTransport items)) {
            return;
        }
        IItemHandler inventory = level.getCapability(Capabilities.ItemHandler.BLOCK,
                pipe.getBlockPos().relative(source), source.getOpposite());
        if (inventory == null) {
            return;
        }
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack available = inventory.extractItem(slot, inventory.getSlotLimit(slot), true);
            if (available.isEmpty()) {
                continue;
            }
            // 1 MJ = 1 個。蓄積が 1 MJ 未満なら取り出さない (旧 useEnergy(1, n) が 0 を返す場合)。
            int affordable = (int) Math.min(available.getCount(), powerProvider.getEnergyStored());
            if (affordable < 1) {
                return;
            }
            ItemStack extracted = inventory.extractItem(slot, affordable, false);
            if (extracted.isEmpty() && affordable < available.getCount()) {
                // 一部だけでは渡せず、まとめてしか取り出せない枠 (自動作業台の完成品など。旧 ISpecialInventory)。
                // 旧実装は特殊インベントリから蓄積量に関係なく 1 回分をまとめて取り出していたため、それに合わせる。
                extracted = inventory.extractItem(slot, available.getCount(), false);
            }
            if (!extracted.isEmpty()) {
                // 取り出せた分だけ消費する (取り出しに失敗した場合は消費しない)。
                powerProvider.useEnergy(1.0F, extracted.getCount(), true);
                items.injectFromSide(extracted, source.getOpposite(), EXTRACT_SPEED);
            }
            return;
        }
    }

    @Override
    public boolean onWrench(PipeBlockEntity pipe) {
        WoodSource.switchSource(pipe);
        return true;
    }

    @Override
    public void initialize(PipeBlockEntity pipe) {
        WoodSource.switchSourceIfNeeded(pipe);
    }

    @Override
    public void onNeighborChanged(PipeBlockEntity pipe) {
        WoodSource.switchSourceIfNeeded(pipe);
    }
}
