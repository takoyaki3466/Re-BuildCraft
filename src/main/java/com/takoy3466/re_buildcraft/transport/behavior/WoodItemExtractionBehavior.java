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
            int amount = (int) powerProvider.useEnergy(1.0F, available.getCount(), true);
            ItemStack extracted = inventory.extractItem(slot, amount, false);
            if (!extracted.isEmpty()) {
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
