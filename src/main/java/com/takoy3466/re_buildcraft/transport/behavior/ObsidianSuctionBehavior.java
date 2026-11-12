package com.takoy3466.re_buildcraft.transport.behavior;

import com.takoy3466.re_buildcraft.api.power.PowerProvider;
import com.takoy3466.re_buildcraft.transport.ItemTransport;
import com.takoy3466.re_buildcraft.transport.PipeBlockEntity;
import com.takoy3466.re_buildcraft.transport.TravelingItem;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.vehicle.AbstractMinecartContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 黒曜石の輸送パイプ。接続が 1 本だけのとき、反対側 (開いた口) の前方 4 ブロックまでのアイテム・矢・チェスト付きトロッコの中身を吸い込む。
 * 中に入ったアイテムは触れた時点で吸い込む。元実装: {@code buildcraft.transport.pipes.PipeItemsObsidian}。
 */
public class ObsidianSuctionBehavior implements PipeBehavior {
    private static final int MAX_ENERGY_RECEIVED = 64;
    private static final int MAX_ENERGY_STORED = 256;
    private static final int MAX_DISTANCE = 4;
    /** 自分で落としたアイテムを吸い直さないために覚えておく数 (旧 entitiesDropped)。 */
    private static final int REMEMBERED_DROPS = 32;
    private static final double MIN_SPEED = 0.01;
    private static final double SPEED_DIVISOR = 2.0;
    private static final double SPEED_OFFSET = 0.05;
    private static final float POP_VOLUME = 0.2F;
    private static final float POP_PITCH_SPREAD = 0.7F;
    private static final float POP_PITCH_FACTOR = 2.0F;

    private final PowerProvider powerProvider = new PowerProvider();
    private final Deque<UUID> droppedItems = new ArrayDeque<>();

    public ObsidianSuctionBehavior() {
        powerProvider.configure(1, MAX_ENERGY_RECEIVED, 1, MAX_ENERGY_STORED);
        powerProvider.configurePowerPerdition(1, 1);
    }

    @Override
    public PowerProvider powerProvider() {
        return powerProvider;
    }

    @Override
    public void doWork(PipeBlockEntity pipe) {
        for (int distance = 1; distance <= MAX_DISTANCE; distance++) {
            if (trySuck(pipe, distance)) {
                return;
            }
        }
        powerProvider.useEnergy(1.0F, 1.0F, true);
    }

    @Override
    public void onEntityInside(PipeBlockEntity pipe, Entity entity) {
        if (canSuck(entity, 0)) {
            pullIntoPipe(pipe, entity, 0);
        }
    }

    @Override
    public void onItemDropped(ItemEntity entity) {
        droppedItems.addLast(entity.getUUID());
        if (droppedItems.size() > REMEMBERED_DROPS) {
            droppedItems.removeFirst();
        }
    }

    private boolean trySuck(PipeBlockEntity pipe, int distance) {
        Level level = pipe.getLevel();
        Direction open = pipe.getOpenSide();
        if (level == null || open == null) {
            return false;
        }
        List<Entity> entities = level.getEntities((Entity) null, suckingBox(pipe.getBlockPos(), open, distance), Entity::isAlive);
        for (Entity entity : entities) {
            if (canSuck(entity, distance)) {
                pullIntoPipe(pipe, entity, distance);
                return true;
            }
            if (distance == 1 && entity instanceof AbstractMinecartContainer cart && extractFromCart(pipe, cart)) {
                return true;
            }
        }
        return false;
    }

    /** 吸い込み口の前方 {@code distance} ブロック目の、幅が距離とともに広がる範囲 (旧 getSuckingBox)。 */
    private static AABB suckingBox(BlockPos pos, Direction open, int distance) {
        BlockPos front = pos.relative(open, distance);
        AABB slice = new AABB(front);
        int spread = distance;
        return switch (open.getAxis()) {
            case X -> slice.inflate(0, spread, spread);
            case Y -> slice.inflate(spread, 0, spread);
            case Z -> slice.inflate(spread, spread, 0);
        };
    }

    private boolean extractFromCart(PipeBlockEntity pipe, AbstractMinecartContainer cart) {
        Level level = pipe.getLevel();
        if (level == null) {
            return false;
        }
        for (int slot = 0; slot < cart.getContainerSize(); slot++) {
            if (!cart.getItem(slot).isEmpty() && powerProvider.useEnergy(1.0F, 1.0F, false) == 1.0F) {
                powerProvider.useEnergy(1.0F, 1.0F, true);
                ItemStack stack = cart.removeItem(slot, 1);
                Vec3 position = cart.position();
                ItemEntity entity = new ItemEntity(level, position.x, position.y + 0.3, position.z, stack);
                entity.setPickUpDelay(10);
                level.addFreshEntity(entity);
                pullIntoPipe(pipe, entity, 1);
                return true;
            }
        }
        return false;
    }

    private boolean canSuck(Entity entity, int distance) {
        if (!entity.isAlive()) {
            return false;
        }
        if (entity instanceof ItemEntity item) {
            return !item.getItem().isEmpty() && !droppedItems.contains(item.getUUID())
                    && powerProvider.useEnergy(1.0F, distance, false) >= distance;
        }
        return entity instanceof Arrow && powerProvider.useEnergy(1.0F, distance, false) >= distance;
    }

    private void pullIntoPipe(PipeBlockEntity pipe, Entity entity, int distance) {
        Level level = pipe.getLevel();
        Direction open = pipe.getOpenSide();
        if (level == null || level.isClientSide || open == null || !(pipe.transport() instanceof ItemTransport items)) {
            return;
        }
        level.playSound(null, entity.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, POP_VOLUME,
                ((level.random.nextFloat() - level.random.nextFloat()) * POP_PITCH_SPREAD + 1.0F) * POP_PITCH_FACTOR);
        ItemStack stack;
        double speed = MIN_SPEED;
        if (entity instanceof ItemEntity item) {
            ItemStack contents = item.getItem();
            float energyUsed = powerProvider.useEnergy(distance, contents.getCount() * distance, true);
            if (distance != 0 && energyUsed / distance != contents.getCount()) {
                stack = contents.split((int) (energyUsed / distance));
            } else {
                stack = contents.copy();
                item.discard();
            }
            Vec3 motion = item.getDeltaMovement();
            speed = Math.max(MIN_SPEED, motion.length() / SPEED_DIVISOR - SPEED_OFFSET);
        } else {
            powerProvider.useEnergy(distance, distance, true);
            stack = new ItemStack(Items.ARROW);
            entity.discard();
        }
        Direction heading = open.getOpposite();
        items.injectFromSide(stack, heading, (float) Math.max(speed, TravelingItem.NORMAL_SPEED));
    }
}
