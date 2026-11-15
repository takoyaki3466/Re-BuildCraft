package com.takoy3466.re_buildcraft.transport;

import com.takoy3466.re_buildcraft.Config;
import com.takoy3466.re_buildcraft.api.transport.IPipeEntry;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

/**
 * アイテム輸送。元実装: {@code buildcraft.transport.PipeTransportItems}。
 * <p>
 * アイテムは入口から中心へ進み、中心で行き先 (出口) を決め、出口に着いたら隣のパイプ・インベントリへ渡す。
 * 行き先が無ければ来た方向へ跳ね返し、それも無理なら 1 tick 待ってから落とす。
 */
public final class ItemTransport extends PipeTransport {
    /**
     * アイテムが流れる高さ (旧 Utils.getPipeFloorOf)。
     */
    public static final double PIPE_FLOOR = 0.25;
    private static final double BLOCK_CENTER = 0.5;
    private static final double ENTRANCE_OFFSET = 0.5;
    private static final float CENTER_TOLERANCE = 1.01F;
    private static final float OVERFLOW_EXPLOSION_POWER = 1.0F;
    private static final int DROP_PICKUP_DELAY = 10;
    /**
     * 行き先が無いアイテムを落とすまでに待つ回数 (旧 PipeTransportItems.wait)。
     */
    private static final int DROP_RETRY = 2;
    private static final String TAG_ITEMS = "items";

    private final List<TravelingItem> items = new ArrayList<>();
    private int dropWait;

    ItemTransport(PipeBlockEntity pipe) {
        super(pipe);
    }

    // ---- 受け入れ -------------------------------------------------------------

    /**
     * 機械や木のパイプがアイテムを入れる。アイテムは {@code heading} の反対側の面から入ってくる。
     */
    public void injectFromSide(ItemStack stack, Direction heading, float speed) {
        Level level = pipe.getLevel();

        if (level == null || stack.isEmpty()) {
            return;
        }

        BlockPos pos = pipe.getBlockPos();
        Vec3 entrance = new Vec3(pos.getX() + BLOCK_CENTER, pos.getY() + PIPE_FLOOR, pos.getZ() + BLOCK_CENTER).subtract(heading.getStepX() * ENTRANCE_OFFSET, heading.getStepY() * ENTRANCE_OFFSET, heading.getStepZ() * ENTRANCE_OFFSET);

        entering(new TravelingItem(level.random.nextInt(), stack, entrance, speed, heading));
    }

    /**
     * 隣のパイプからアイテムが入ってきた (旧 entityEntering)。
     */
    public void entering(TravelingItem item) {
        if (item.stack().isEmpty()) {
            return;
        }

        Direction heading = item.heading();

        if (heading.getAxis().isHorizontal()) {
            Vec3 position = item.position();
            item.setPosition(new Vec3(position.x, pipe.getBlockPos().getY() + PIPE_FLOOR, position.z));
        }

        item.setToCenter(true);

        pipe.behavior().onItemEntered(pipe, item);
        items.add(item);
        pipe.scheduleSync();

        if (items.size() > Config.PIPE_GROUP_ITEMS_TRIGGER.get()) {
            groupItems();
            if (items.size() > Config.PIPE_MAX_ITEMS.get() && pipe.getLevel() != null) {
                BlockPos pos = pipe.getBlockPos();
                pipe.getLevel().explode(null, pos.getX(), pos.getY(), pos.getZ(), OVERFLOW_EXPLOSION_POWER, Level.ExplosionInteraction.BLOCK);
            }
        }
    }

    // ---- 移動 -----------------------------------------------------------------

    @Override
    public void tick(ServerLevel level) {
        List<TravelingItem> snapshot = new ArrayList<>(items);
        for (TravelingItem item : snapshot) {
            if (!items.contains(item)) {
                continue;
            }
            item.move();
            if (item.toCenter() && (middleReached(item) || outOfBounds(item))) {
                reachCenter(level, item);
            } else if (!item.toCenter() && endReached(item)) {
                items.remove(item);
                pipe.scheduleSync();
                handleTileReached(level, item);
            }
        }
    }

    private void reachCenter(ServerLevel level, TravelingItem item) {
        item.setToCenter(false);
        item.setPosition(center());

        if (pipe.behavior().consumeAtCenter(pipe, item)) {
            items.remove(item);
            pipe.scheduleSync();
            return;
        }

        Direction destination = resolveDestination(level, item);

        if (destination == null) {
            if (dropWait == 0) {
                dropWait = DROP_RETRY;
            }
            if (dropWait-- > 1) {
                item.setToCenter(true);
            } else {
                dropWait = 0;
                items.remove(item);
                dropItem(level, item);
            }
        } else {
            dropWait = 0;
            item.setHeading(destination);
        }

        pipe.scheduleSync();
    }

    @Nullable
    private Direction resolveDestination(Level level, TravelingItem item) {
        List<Direction> movements = getPossibleMovements(level, item);

        if (movements.isEmpty()) {
            return null;
        }

        BlockPos pos = pipe.getBlockPos();
        int index = Math.abs(item.id() + pos.getX() + pos.getY() + pos.getZ()) % movements.size();

        return movements.get(index);
    }

    private List<Direction> getPossibleMovements(Level level, TravelingItem item) {
        List<Direction> movements = new ArrayList<>();
        Direction back = item.heading().getOpposite();

        for (Direction direction : Direction.values()) {
            if (direction != back && pipe.outputOpen(direction) && canReceive(level, direction, item)) {
                movements.add(direction);
            }
        }

        if (movements.isEmpty() && canReceive(level, back, item)) {
            movements.add(back);
        }

        return pipe.behavior().filterMovements(pipe, movements, item);
    }

    private boolean canReceive(Level level, Direction direction, TravelingItem item) {
        if (!pipe.isConnected(direction)) {
            return false;
        }

        BlockPos neighborPos = pipe.getBlockPos().relative(direction);

        if (level.getBlockEntity(neighborPos) instanceof IPipeEntry entry) {
            return entry.acceptsItems();
        }

        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, neighborPos, direction.getOpposite());

        return handler != null && ItemHandlerHelper.insertItemStacked(handler, item.stack().copy(), true).getCount() < item.stack().getCount();
    }

    private void handleTileReached(ServerLevel level, TravelingItem item) {
        Direction heading = item.heading();
        BlockPos neighborPos = pipe.getBlockPos().relative(heading);

        if (level.getBlockEntity(neighborPos) instanceof PipeBlockEntity next && next.transport() instanceof ItemTransport nextItems) {
            nextItems.entering(item);
            return;
        }

        if (level.getBlockEntity(neighborPos) instanceof IPipeEntry entry && entry.acceptsItems()) {
            entry.injectItem(item.stack(), heading);
            return;
        }

        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, neighborPos, heading.getOpposite());

        if (handler != null) {
            item.setStack(ItemHandlerHelper.insertItemStacked(handler, item.stack(), false));
        }

        if (!item.stack().isEmpty()) {
            dropItem(level, item);
        }
    }

    private void dropItem(Level level, TravelingItem item) {
        pipe.behavior().beforeDrop(pipe, item);
        if (item.stack().isEmpty()) {
            return;
        }

        Vec3 position = item.position();
        ItemEntity entity = new ItemEntity(level, position.x, position.y, position.z, item.stack());
        Direction heading = item.heading();

        entity.setDeltaMovement(heading.getStepX() * item.speed(), heading.getStepY() * item.speed(), heading.getStepZ() * item.speed());
        entity.setPickUpDelay(DROP_PICKUP_DELAY);

        level.addFreshEntity(entity);
        pipe.behavior().onItemDropped(entity);
    }

    private Vec3 center() {
        BlockPos pos = pipe.getBlockPos();
        return new Vec3(pos.getX() + BLOCK_CENTER, pos.getY() + PIPE_FLOOR, pos.getZ() + BLOCK_CENTER);
    }

    private boolean middleReached(TravelingItem item) {
        Vec3 center = center();
        Vec3 position = item.position();
        double tolerance = item.speed() * CENTER_TOLERANCE;

        return Math.abs(center.x - position.x) < tolerance && Math.abs(center.y - position.y) < tolerance && Math.abs(center.z - position.z) < tolerance;
    }

    /**
     * 自分のブロックの外へ出た。
     */
    private boolean endReached(TravelingItem item) {
        return isOutside(item, 0.0, 1.0);
    }

    /**
     * 隣のブロックよりさらに外へ出た (位置が壊れている)。
     */
    private boolean outOfBounds(TravelingItem item) {
        return isOutside(item, -1.0, 2.0);
    }

    private boolean isOutside(TravelingItem item, double min, double max) {
        BlockPos pos = pipe.getBlockPos();
        Vec3 position = item.position();

        return position.x > pos.getX() + max || position.x < pos.getX() + min || position.y > pos.getY() + max || position.y < pos.getY() + min || position.z > pos.getZ() + max || position.z < pos.getZ() + min;
    }

    /**
     * 同じアイテムをまとめる (旧 groupEntities)。
     */
    private void groupItems() {
        for (int i = 0; i < items.size(); i++) {
            TravelingItem base = items.get(i);
            Iterator<TravelingItem> iterator = items.listIterator(i + 1);

            while (iterator.hasNext()) {
                TravelingItem other = iterator.next();

                if (ItemStack.isSameItemSameComponents(base.stack(), other.stack()) && base.stack().getCount() + other.stack().getCount() < base.stack().getMaxStackSize()) {
                    base.stack().grow(other.stack().getCount());
                    iterator.remove();
                }
            }
        }
    }

    // ---- クライアント ---------------------------------------------------------

    @Override
    public void clientTick() {
        Iterator<TravelingItem> iterator = items.iterator();

        while (iterator.hasNext()) {
            TravelingItem item = iterator.next();

            if (item.toCenter() && middleReached(item)) {
                // 行き先はサーバーが決める。届くまで中心で待つ。
                item.setPosition(center());
                continue;
            }
            item.move();

            if (!item.toCenter() && endReached(item)) {
                iterator.remove();
            }
        }
    }

    // ---- 状態 -----------------------------------------------------------------

    public List<TravelingItem> items() {
        return items;
    }

    @Override
    public boolean isEmpty() {
        return items.isEmpty();
    }

    /**
     * ゲートのトリガー「アイテム通過中」(パラメーター付き)。
     */
    public boolean contains(ItemStack filter) {
        for (TravelingItem item : items) {
            if (filter.isEmpty() || ItemStack.isSameItemSameComponents(item.stack(), filter)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void dropContents(Level level, BlockPos pos) {
        groupItems();

        for (TravelingItem item : items) {
            Block.popResource(level, pos, item.stack());
        }

        items.clear();
    }

    @Override
    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();

        for (TravelingItem item : items) {
            if (!item.stack().isEmpty()) {
                list.add(item.save(registries));
            }
        }

        tag.put(TAG_ITEMS, list);
        return tag;
    }

    @Override
    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        items.clear();
        ListTag list = tag.getList(TAG_ITEMS, Tag.TAG_COMPOUND);

        for (int i = 0; i < list.size(); i++) {
            TravelingItem item = TravelingItem.load(list.getCompound(i), registries);
            if (!item.stack().isEmpty()) {
                items.add(item);
            }
        }
    }
}
