package com.takoy3466.re_buildcraft.api.quarry;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 建設ロボット。予約された作業を一定間隔で 1 つずつ実行し、作業範囲の周りを飛び回る。
 * 元実装: {@code buildcraft.core.EntityRobot} (旧実装ではエンティティ)。クアーリーの枠組みとビルダーの両方が使う。
 */
public final class ConstructionRobot {
    /**
     * ロボットに予約する 1 マス分の作業。
     *
     * @param pos    作業するマス (ビームの向き先)
     * @param action 実行する処理
     */
    public record Task(BlockPos pos, Consumer<Level> action) {
    }

    /** 予約できる作業の上限 (旧 MAX_SIZE)。 */
    public static final int MAX_QUEUE = 20;
    /** 旧 EntityRobot.buildSpeed の既定値。 */
    private static final float BUILD_SPEED = 3.0F;
    private static final int WAIT_OFFSET = 2;
    private static final double ARRIVAL_DISTANCE = 0.1;
    private static final double SPEED_DIVISOR = 50.0;
    private static final double BLOCK_CENTER = 0.5;
    private static final float LASER_POWER_SCALE = 4.0F;

    private final QuarryArea area;
    private final Deque<Task> targets = new ArrayDeque<>();
    private Vec3 position;
    private BlockPos destination;
    private Vec3 velocity = Vec3.ZERO;
    private int wait;

    public ConstructionRobot(QuarryArea area) {
        this.area = area;
        this.destination = new BlockPos((area.minX() + area.maxX()) / 2, (area.minY() + area.maxY()) / 2, (area.minZ() + area.maxZ()) / 2);
        this.position = Vec3.atCenterOf(destination);
    }

    public boolean readyToBuild() {
        return targets.size() < MAX_QUEUE;
    }

    public boolean done() {
        return targets.isEmpty();
    }

    public void schedule(Task task) {
        targets.add(task);
    }

    /**
     * 1 tick 分動く。待ち時間が切れたら先頭の作業を実行する。
     *
     * @param softBlock 飛び回る先として選べる柔らかいブロック
     */
    public void update(Level level, Predicate<BlockState> softBlock) {
        move(level, softBlock);
        updateWait();
        if (!targets.isEmpty() && wait <= 0) {
            targets.pollFirst().action().accept(level);
        }
    }

    private void updateWait() {
        if (targets.isEmpty()) {
            return;
        }
        if (wait <= 0) {
            wait = (int) ((MAX_QUEUE - targets.size() + WAIT_OFFSET) / BUILD_SPEED);
            if (wait == 0) {
                wait = 1;
            }
        } else {
            wait--;
        }
    }

    private void move(Level level, Predicate<BlockState> softBlock) {
        Vec3 center = Vec3.atCenterOf(destination);
        if (Math.abs(position.x - center.x) < ARRIVAL_DISTANCE && Math.abs(position.y - center.y) < ARRIVAL_DISTANCE
                && Math.abs(position.z - center.z) < ARRIVAL_DISTANCE) {
            chooseNextDestination(level, softBlock);
        } else {
            position = position.add(velocity.scale(getLaserPower() + 1.0F));
        }
    }

    private void chooseNextDestination(Level level, Predicate<BlockState> softBlock) {
        List<BlockPos> candidates = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(destination.offset(-1, -1, -1), destination.offset(1, 1, 1))) {
            if (pos.getX() >= area.minX() - 1 && pos.getX() <= area.maxX() + 1
                    && pos.getY() >= area.minY() - 1 && pos.getY() <= area.maxY() + 1
                    && pos.getZ() >= area.minZ() - 1 && pos.getZ() <= area.maxZ() + 1
                    && level.isLoaded(pos) && softBlock.test(level.getBlockState(pos))) {
                candidates.add(pos.immutable());
            }
        }
        if (!candidates.isEmpty()) {
            setDestination(candidates.get(level.random.nextInt(candidates.size())));
        }
    }

    private void setDestination(BlockPos pos) {
        destination = pos;
        Vec3 delta = new Vec3(pos.getX() + BLOCK_CENTER, pos.getY() + BLOCK_CENTER, pos.getZ() + BLOCK_CENTER).subtract(position);
        double length = delta.length();
        velocity = length == 0.0 ? Vec3.ZERO : delta.scale(1.0 / length / SPEED_DIVISOR);
    }

    /** ビームの太さ・ロボットの速さに使う値 (旧 laser.pushPower の引数)。 */
    public float getLaserPower() {
        return (float) targets.size() / MAX_QUEUE * LASER_POWER_SCALE;
    }

    public Vec3 position() {
        return position;
    }

    /** 現在作業中のマス。ビームの向き先。 */
    @Nullable
    public BlockPos currentTarget() {
        Task task = targets.peekFirst();
        return task == null ? null : task.pos();
    }
}
