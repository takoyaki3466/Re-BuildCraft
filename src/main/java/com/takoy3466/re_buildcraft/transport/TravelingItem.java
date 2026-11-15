package com.takoy3466.re_buildcraft.transport;

import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * パイプ内を移動中のアイテム。元実装: {@code buildcraft.api.EntityPassiveItem} と {@code PipeTransportItems.EntityData}。
 * 座標はワールド座標で持ち、パイプ間を移るときもそのまま引き継ぐ。
 */
public final class TravelingItem {
    /**
     * 旧 Utils.pipeNormalSpeed。
     */
    public static final float NORMAL_SPEED = 0.01F;

    private static final String TAG_ID = "id";
    private static final String TAG_STACK = "stack";
    private static final String TAG_X = "x";
    private static final String TAG_Y = "y";
    private static final String TAG_Z = "z";
    private static final String TAG_SPEED = "speed";
    private static final String TAG_HEADING = "heading";
    private static final String TAG_TO_CENTER = "toCenter";

    private final int id;
    private ItemStack stack;
    private Vec3 position;
    private float speed;
    private Direction heading;
    private boolean toCenter = true;

    public TravelingItem(int id, ItemStack stack, Vec3 position, float speed, Direction heading) {
        this.id = id;
        this.stack = stack;
        this.position = position;
        this.speed = speed;
        this.heading = heading;
    }

    public void move() {
        position = position.add(heading.getStepX() * speed, heading.getStepY() * speed, heading.getStepZ() * speed);
    }

    /**
     * 経路選択の決定的な乱数に使う識別子 (旧 entityId + deterministicRandomization)。
     */
    public int id() {
        return id;
    }

    public ItemStack stack() {
        return stack;
    }

    public void setStack(ItemStack stack) {
        this.stack = stack;
    }

    public Vec3 position() {
        return position;
    }

    public void setPosition(Vec3 position) {
        this.position = position;
    }

    public float speed() {
        return speed;
    }

    public void setSpeed(float speed) {
        this.speed = speed;
    }

    public Direction heading() {
        return heading;
    }

    public void setHeading(Direction heading) {
        this.heading = heading;
    }

    /**
     * 中心へ向かっている途中か (中心を過ぎると出口へ向かう)。
     */
    public boolean toCenter() {
        return toCenter;
    }

    public void setToCenter(boolean toCenter) {
        this.toCenter = toCenter;
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(TAG_ID, id);
        tag.put(TAG_STACK, stack.save(registries));
        tag.putDouble(TAG_X, position.x);
        tag.putDouble(TAG_Y, position.y);
        tag.putDouble(TAG_Z, position.z);
        tag.putFloat(TAG_SPEED, speed);
        tag.putInt(TAG_HEADING, heading.get3DDataValue());
        tag.putBoolean(TAG_TO_CENTER, toCenter);
        return tag;
    }

    public static TravelingItem load(CompoundTag tag, HolderLookup.Provider registries) {
        TravelingItem item = new TravelingItem(tag.getInt(TAG_ID), ItemStack.parseOptional(registries, tag.getCompound(TAG_STACK)), new Vec3(tag.getDouble(TAG_X), tag.getDouble(TAG_Y), tag.getDouble(TAG_Z)), tag.getFloat(TAG_SPEED), Direction.from3DDataValue(tag.getInt(TAG_HEADING)));
        item.toCenter = tag.getBoolean(TAG_TO_CENTER);
        return item;
    }
}
