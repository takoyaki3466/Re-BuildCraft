package com.takoy3466.re_buildcraft.factory;

import com.takoy3466.re_buildcraft.api.IMachine;
import com.takoy3466.re_buildcraft.api.power.PoweredBlockEntity;
import com.takoy3466.re_buildcraft.api.transport.IPipeConnectable;
import com.takoy3466.re_buildcraft.api.util.InventoryUtils;
import com.takoy3466.re_buildcraft.registry.ModBlockEntities;
import com.takoy3466.re_buildcraft.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * ボーリングマシン。MJ を受けるたびに真下の 1 ブロックを掘り、採掘パイプに置き換える。
 * 元実装: {@code buildcraft.factory.TileMiningWell}。
 */
public class MiningWellBlockEntity extends PoweredBlockEntity implements IMachine, IPipeConnectable {
    private static final int ENERGY_PER_BLOCK = 25;
    private static final int MAX_ENERGY_STORED = 1000;

    private boolean isDigging = true;

    public MiningWellBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MINING_WELL.get(), pos, state);
        powerProvider.configure(ENERGY_PER_BLOCK, ENERGY_PER_BLOCK, ENERGY_PER_BLOCK, MAX_ENERGY_STORED);
    }

    public void serverTick() {
        if (level != null) {
            powerProvider.update(this, level);
        }
    }

    @Override
    public void doWork() {
        if (!(level instanceof ServerLevel serverLevel)
                || powerProvider.useEnergy(ENERGY_PER_BLOCK, ENERGY_PER_BLOCK, true) < ENERGY_PER_BLOCK) {
            return;
        }

        BlockPos.MutableBlockPos target = worldPosition.mutable().move(Direction.DOWN);
        while (serverLevel.getBlockState(target).is(ModBlocks.MINING_PIPE.get())) {
            target.move(Direction.DOWN);
        }
        if (!canDig(serverLevel, target)) {
            isDigging = false;
            return;
        }

        BlockState state = serverLevel.getBlockState(target);
        if (!state.isAir()) {
            for (ItemStack drop : Block.getDrops(state, serverLevel, target, serverLevel.getBlockEntity(target))) {
                InventoryUtils.ejectFromMachine(serverLevel, worldPosition, drop);
            }
        }
        serverLevel.setBlock(target, ModBlocks.MINING_PIPE.get().defaultBlockState(), Block.UPDATE_ALL);
        setChanged();
    }

    /** 岩盤などの破壊不能ブロック・液体・ワールドの底に当たったら掘削をやめる。 */
    private static boolean canDig(ServerLevel level, BlockPos pos) {
        if (pos.getY() < level.getMinBuildHeight()) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return state.getDestroySpeed(level, pos) >= 0.0F && state.getFluidState().isEmpty();
    }

    @Override
    public boolean isActive() {
        return isDigging;
    }

    @Override
    public boolean manageSolids() {
        return true;
    }

    @Override
    public boolean isPipeConnected(Direction side) {
        return true;
    }

    @Override
    public int powerRequest() {
        return isActive() ? powerProvider.getMaxEnergyReceived() : 0;
    }
}
