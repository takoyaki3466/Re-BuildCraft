package com.takoy3466.re_buildcraft.api.util;

import com.takoy3466.re_buildcraft.api.transport.IPipeEntry;
import com.takoy3466.re_buildcraft.api.transport.PipeConnections;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/**
 * 機械が生成物を周囲へ送り出すための共通処理。
 * 元実装: {@code buildcraft.core.StackUtil#addToRandomInventory} と {@code TileBuildCraft#addPipeStackUtil}。
 */
public final class InventoryUtils {
    private static final float EJECT_SPREAD = 0.8F;
    private static final float EJECT_MARGIN = 0.1F;
    private static final float EJECT_HEIGHT_OFFSET = 0.5F;
    private static final float EJECT_RANDOM_SPEED = 0.05F;
    private static final float EJECT_UPWARD_SPEED = 1.0F;

    private InventoryUtils() {
    }

    /**
     * 機械の生成物を、隣接インベントリ → 隣接するアイテムパイプ → 上方への放出、の順に送り出す。
     * 元実装: {@code TileBuildCraft#addInventoryAndPipeStackUtil}。
     */
    public static void ejectFromMachine(Level level, BlockPos pos, ItemStack stack) {
        ItemStack remainder = insertIntoAdjacentInventories(level, pos, stack);
        if (!remainder.isEmpty() && !insertIntoAdjacentPipe(level, pos, remainder)) {
            ejectUpward(level, pos, remainder);
        }
    }

    /**
     * 隣接するアイテムパイプのうち無作為な 1 本へ流し込む。元実装: {@code Utils.addToRandomPipeEntry}。
     *
     * @return 流し込めた場合 true
     */
    public static boolean insertIntoAdjacentPipe(Level level, BlockPos pos, ItemStack stack) {
        List<Direction> candidates = new ArrayList<>();
        for (Direction direction : Direction.values()) {
            if (level.getBlockEntity(pos.relative(direction)) instanceof IPipeEntry entry && entry.acceptsItems()
                    && PipeConnections.isPipeConnectedTo(level, pos.relative(direction), direction.getOpposite())) {
                candidates.add(direction);
            }
        }
        if (candidates.isEmpty()) {
            return false;
        }
        Direction heading = candidates.get(level.random.nextInt(candidates.size()));
        ((IPipeEntry) level.getBlockEntity(pos.relative(heading))).injectItem(stack, heading);
        return true;
    }

    /**
     * 隣接するインベントリへ無作為な順番で搬入する。
     *
     * @return 入り切らなかった残り。全て入った場合は {@link ItemStack#EMPTY}
     */
    public static ItemStack insertIntoAdjacentInventories(Level level, BlockPos pos, ItemStack stack) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        List<IItemHandler> handlers = new ArrayList<>();
        for (Direction direction : Direction.values()) {
            IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos.relative(direction), direction.getOpposite());
            if (handler != null) {
                handlers.add(handler);
            }
        }

        ItemStack remainder = stack;
        RandomSource random = level.random;
        while (!handlers.isEmpty() && !remainder.isEmpty()) {
            IItemHandler handler = handlers.remove(random.nextInt(handlers.size()));
            remainder = ItemHandlerHelper.insertItemStacked(handler, remainder, false);
        }
        return remainder;
    }

    /** 機械の上へ勢いよく放出する (搬入先が無い場合の既定動作)。 */
    public static void ejectUpward(Level level, BlockPos pos, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        RandomSource random = level.random;
        double x = pos.getX() + random.nextFloat() * EJECT_SPREAD + EJECT_MARGIN;
        double y = pos.getY() + random.nextFloat() * EJECT_SPREAD + EJECT_MARGIN + EJECT_HEIGHT_OFFSET;
        double z = pos.getZ() + random.nextFloat() * EJECT_SPREAD + EJECT_MARGIN;
        ItemEntity itemEntity = new ItemEntity(level, x, y, z, stack);
        itemEntity.setDeltaMovement(
                random.nextGaussian() * EJECT_RANDOM_SPEED,
                random.nextGaussian() * EJECT_RANDOM_SPEED + EJECT_UPWARD_SPEED,
                random.nextGaussian() * EJECT_RANDOM_SPEED);
        level.addFreshEntity(itemEntity);
    }

    /** ブロック破壊時などに、その場へアイテムを落とす。 */
    public static void dropStack(Level level, BlockPos pos, ItemStack stack) {
        if (!stack.isEmpty()) {
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
        }
    }

    /** インベントリの中身を全てその場へ落とし、スロットを空にする。 */
    public static void dropAll(Level level, BlockPos pos, IItemHandlerModifiable inventory) {
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            dropStack(level, pos, inventory.getStackInSlot(slot));
            inventory.setStackInSlot(slot, ItemStack.EMPTY);
        }
    }
}
