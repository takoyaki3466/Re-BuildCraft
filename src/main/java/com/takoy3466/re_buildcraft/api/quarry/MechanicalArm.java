package com.takoy3466.re_buildcraft.api.quarry;

import net.minecraft.nbt.CompoundTag;

/**
 * クアーリーのドリルアーム。ヘッドを目標位置へ水平・垂直に動かし、両方そろったら通知する。
 * 元実装: {@code buildcraft.factory.EntityMechanicalArm}。
 * <p>
 * 旧実装はアームと 4 本の棒をそれぞれエンティティとして出していたが、ここでは状態だけを持ち、
 * 描画はクアーリーの BlockEntityRenderer が行う。
 */
public final class MechanicalArm {
    private static final double INITIAL_HEAD_DROP = 2.0;
    private static final double ARRIVAL_FACTOR = 2.0;
    private static final double VERTICAL_SPEED_DIVISOR = 2.0;

    private static final String TAG_ORIGIN_X = "originX";
    private static final String TAG_ORIGIN_Z = "originZ";
    private static final String TAG_BASE_Y = "baseY";
    private static final String TAG_LENGTH_X = "lengthX";
    private static final String TAG_LENGTH_Z = "lengthZ";
    private static final String TAG_HEAD_X = "headPosX";
    private static final String TAG_HEAD_Y = "headPosY";
    private static final String TAG_HEAD_Z = "headPosZ";
    private static final String TAG_TARGET_X = "targetX";
    private static final String TAG_TARGET_Y = "targetY";
    private static final String TAG_TARGET_Z = "targetZ";
    private static final String TAG_ANGLE = "angle";
    private static final String TAG_SPEED = "speed";
    private static final String TAG_PROGRESSION_XZ = "progressionXZ";
    private static final String TAG_PROGRESSION_Y = "progressionY";

    /** アームの到達通知を受け取る側。元実装: {@code IArmListener}。 */
    @FunctionalInterface
    public interface Listener {
        void positionReached(MechanicalArm arm);
    }

    private double originX;
    private double originZ;
    private double baseY;
    private double lengthX;
    private double lengthZ;
    private double headX;
    private double headY;
    private double headZ;
    private double targetX;
    private double targetY;
    private double targetZ;
    private double angle;
    private double speed;
    private boolean inProgressionXZ;
    private boolean inProgressionY;

    private MechanicalArm() {
    }

    /**
     * @param x       X 方向の棒の始点
     * @param y       棒 (レール) の高さ
     * @param z       Z 方向の棒の始点
     * @param lengthX X 方向の棒の長さ
     * @param lengthZ Z 方向の棒の長さ
     */
    public static MechanicalArm create(double x, double y, double z, double lengthX, double lengthZ) {
        MechanicalArm arm = new MechanicalArm();
        arm.originX = x;
        arm.originZ = z;
        arm.baseY = y;
        arm.lengthX = lengthX;
        arm.lengthZ = lengthZ;
        arm.headX = x;
        arm.headY = y - INITIAL_HEAD_DROP;
        arm.headZ = z;
        arm.setTarget(arm.headX, arm.headY, arm.headZ);
        arm.inProgressionXZ = false;
        arm.inProgressionY = false;
        return arm;
    }

    public void setTarget(double x, double y, double z) {
        targetX = x;
        targetY = y;
        targetZ = z;
        angle = Math.atan2(targetZ - headZ, targetX - headX);
        inProgressionXZ = true;
        inProgressionY = true;
    }

    /** ヘッドを {@code distance} だけ動かす。目標に着いた tick に {@code listener} を 1 回呼ぶ。 */
    public void doMove(double distance, Listener listener) {
        speed = distance;
        if (inProgressionXZ) {
            if (Math.abs(targetX - headX) < distance * ARRIVAL_FACTOR && Math.abs(targetZ - headZ) < distance * ARRIVAL_FACTOR) {
                headX = targetX;
                headZ = targetZ;
                inProgressionXZ = false;
                if (!inProgressionY) {
                    listener.positionReached(this);
                }
            } else {
                headX += Math.cos(angle) * distance;
                headZ += Math.sin(angle) * distance;
            }
        }
        if (inProgressionY) {
            if (Math.abs(targetY - headY) < distance * ARRIVAL_FACTOR) {
                headY = targetY;
                inProgressionY = false;
                if (!inProgressionXZ) {
                    listener.positionReached(this);
                }
            } else if (targetY > headY) {
                headY += distance / VERTICAL_SPEED_DIVISOR;
            } else {
                headY -= distance / VERTICAL_SPEED_DIVISOR;
            }
        }
    }

    public double originX() {
        return originX;
    }

    public double originZ() {
        return originZ;
    }

    public double baseY() {
        return baseY;
    }

    public double lengthX() {
        return lengthX;
    }

    public double lengthZ() {
        return lengthZ;
    }

    public double headX() {
        return headX;
    }

    public double headY() {
        return headY;
    }

    public double headZ() {
        return headZ;
    }

    public double targetX() {
        return targetX;
    }

    public double targetY() {
        return targetY;
    }

    public double targetZ() {
        return targetZ;
    }

    public double speed() {
        return speed;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putDouble(TAG_ORIGIN_X, originX);
        tag.putDouble(TAG_ORIGIN_Z, originZ);
        tag.putDouble(TAG_BASE_Y, baseY);
        tag.putDouble(TAG_LENGTH_X, lengthX);
        tag.putDouble(TAG_LENGTH_Z, lengthZ);
        tag.putDouble(TAG_HEAD_X, headX);
        tag.putDouble(TAG_HEAD_Y, headY);
        tag.putDouble(TAG_HEAD_Z, headZ);
        tag.putDouble(TAG_TARGET_X, targetX);
        tag.putDouble(TAG_TARGET_Y, targetY);
        tag.putDouble(TAG_TARGET_Z, targetZ);
        tag.putDouble(TAG_ANGLE, angle);
        tag.putDouble(TAG_SPEED, speed);
        tag.putBoolean(TAG_PROGRESSION_XZ, inProgressionXZ);
        tag.putBoolean(TAG_PROGRESSION_Y, inProgressionY);
        return tag;
    }

    public static MechanicalArm load(CompoundTag tag) {
        MechanicalArm arm = new MechanicalArm();
        arm.originX = tag.getDouble(TAG_ORIGIN_X);
        arm.originZ = tag.getDouble(TAG_ORIGIN_Z);
        arm.baseY = tag.getDouble(TAG_BASE_Y);
        arm.lengthX = tag.getDouble(TAG_LENGTH_X);
        arm.lengthZ = tag.getDouble(TAG_LENGTH_Z);
        arm.headX = tag.getDouble(TAG_HEAD_X);
        arm.headY = tag.getDouble(TAG_HEAD_Y);
        arm.headZ = tag.getDouble(TAG_HEAD_Z);
        arm.targetX = tag.getDouble(TAG_TARGET_X);
        arm.targetY = tag.getDouble(TAG_TARGET_Y);
        arm.targetZ = tag.getDouble(TAG_TARGET_Z);
        arm.angle = tag.getDouble(TAG_ANGLE);
        arm.speed = tag.getDouble(TAG_SPEED);
        arm.inProgressionXZ = tag.getBoolean(TAG_PROGRESSION_XZ);
        arm.inProgressionY = tag.getBoolean(TAG_PROGRESSION_Y);
        return arm;
    }
}
