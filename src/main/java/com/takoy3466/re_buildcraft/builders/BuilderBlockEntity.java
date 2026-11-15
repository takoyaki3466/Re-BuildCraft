package com.takoy3466.re_buildcraft.builders;

import com.takoy3466.re_buildcraft.api.IMachine;
import com.takoy3466.re_buildcraft.api.power.PoweredBlockEntity;
import com.takoy3466.re_buildcraft.api.quarry.ConstructionRobot;
import com.takoy3466.re_buildcraft.api.quarry.QuarryArea;
import com.takoy3466.re_buildcraft.api.util.InventoryUtils;
import com.takoy3466.re_buildcraft.builders.blueprint.Blueprint;
import com.takoy3466.re_buildcraft.builders.blueprint.BlueprintItem;
import com.takoy3466.re_buildcraft.builders.blueprint.BlueprintRef;
import com.takoy3466.re_buildcraft.builders.blueprint.BlueprintStore;
import com.takoy3466.re_buildcraft.builders.blueprint.BuildPlan;
import com.takoy3466.re_buildcraft.builders.blueprint.BuilderMaterials;
import com.takoy3466.re_buildcraft.registry.ModBlockEntities;
import com.takoy3466.re_buildcraft.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.RangedWrapper;
import org.jetbrains.annotations.Nullable;

/**
 * ビルダー。元実装: {@code buildcraft.builders.TileBuilder}。
 * <ul>
 *   <li>先頭の枠のテンプレート/設計図を、ビルダーの向きに合わせて回し、ビルダーを基点に建てる (旧 instanciateBluePrint)。</li>
 *   <li>25 MJ ごとに 1 マス分の作業を決め、建設ロボットに予約する。ロボットが一定間隔で実行する (旧 doWork / EntityRobot)。</li>
 *   <li>材料は自分の材料枠と隣接するインベントリから取る (旧 SurroundingInventory)。</li>
 *   <li>隣 (3x3x3) に緑マーカーがあれば、その経路に沿って設計図を繰り返し建てる。緑マーカーは撤去してアイテムにする (旧 initialize / PathIterator)。</li>
 * </ul>
 */
public class BuilderBlockEntity extends PoweredBlockEntity implements IMachine, MenuProvider, AreaHolder,
        RotatableMachineBlock.Ticking, RotatableMachineBlock.Droppable {
    public static final int BLUEPRINT_SLOT = 0;
    public static final int SLOT_COUNT = 28;
    /** 旧 configure(10, 25, 25, 25, 25) と、1 マスの予約に使う MJ。 */
    private static final int WORK_ENERGY = 25;
    private static final int ROBOT_SYNC_INTERVAL_TICKS = 5;
    /** 経路をたどる刻み (旧 PathIterator の 1 / 10)。 */
    private static final double PATH_STEP = 0.1;
    private static final String TAG_ITEMS = "items";
    private static final String TAG_BOX = "box";
    private static final String TAG_PATH = "path";
    private static final String TAG_DONE = "done";
    private static final String TAG_ROBOT = "robot";
    private static final String TAG_ROBOT_TARGET = "robot_target";
    private static final String TAG_ROBOT_POWER = "robot_power";

    private final ItemStackHandler inventory = new ItemStackHandler(SLOT_COUNT) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot != BLUEPRINT_SLOT || stack.getItem() instanceof BlueprintItem;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (slot == BLUEPRINT_SLOT) {
                blueprintChanged();
            }
        }
    };
    private final IItemHandler materials = new RangedWrapper(inventory, BLUEPRINT_SLOT + 1, SLOT_COUNT);
    @Nullable
    private BuildPlan plan;
    @Nullable
    private BoundingBox box;
    @Nullable
    private ConstructionRobot robot;
    @Nullable
    private List<BlockPos> path;
    @Nullable
    private PathIterator pathIterator;
    private boolean done = true;
    private boolean initialized;
    // クライアントの描画用 (サーバーから同期)。
    @Nullable
    private Vec3 clientRobot;
    @Nullable
    private BlockPos clientRobotTarget;
    private float clientRobotPower;

    public BuilderBlockEntity(BlockPos pos, BlockState state) {
        this(ModBlockEntities.BUILDER.get(), pos, state);
    }

    /** 継承用 (アドオンのビルダーが自分のブロックエンティティ型で使う)。 */
    protected BuilderBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        powerProvider.configure(WORK_ENERGY, WORK_ENERGY, WORK_ENERGY, WORK_ENERGY);
    }

    // ---- tick -----------------------------------------------------------------

    @Override
    public void serverTick() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (!initialized) {
            initialized = true;
            takePathFromMarkers(serverLevel);
            iterateBlueprint(serverLevel);
        }
        powerProvider.update(this, serverLevel);
        if (robot != null) {
            robot.update(serverLevel, SoftBlocks::isSoft);
            if (serverLevel.getGameTime() % ROBOT_SYNC_INTERVAL_TICKS == 0L) {
                sync();
            }
        }
        // 旧 updateEntity: 建てる物も作業中のロボットも無くなったら、範囲の表示とロボットを片付ける。
        boolean building = plan != null && !plan.isDone();
        if (!building && (robot == null || robot.done()) && (box != null || robot != null)) {
            box = null;
            robot = null;
            sync();
        }
    }

    /** 旧 initialize: 隣の緑マーカーから経路を受け取り、マーカーを撤去してアイテムにする。 */
    private void takePathFromMarkers(ServerLevel serverLevel) {
        if (path != null) {
            return;
        }
        for (BlockPos pos : BlockPos.betweenClosed(worldPosition.offset(-1, -1, -1), worldPosition.offset(1, 1, 1))) {
            if (serverLevel.getBlockEntity(pos) instanceof PathMarkerBlockEntity marker) {
                List<BlockPos> markers = marker.getPath();
                for (BlockPos markerPos : markers) {
                    serverLevel.removeBlock(markerPos, false);
                    InventoryUtils.dropStack(serverLevel, worldPosition, new ItemStack(ModBlocks.PATH_MARKER.get()));
                }
                List<BlockPos> route = new ArrayList<>(markers);
                route.set(0, worldPosition.immutable());
                path = route;
                setChanged();
                return;
            }
        }
    }

    /** 旧 doWork: 未完了で、ロボットに空きがあり、25 MJ を使えたら 1 マス分の作業を予約する。 */
    @Override
    public void doWork() {
        if (!(level instanceof ServerLevel serverLevel) || done || robot != null && !robot.readyToBuild()) {
            return;
        }
        if (powerProvider.useEnergy(WORK_ENERGY, WORK_ENERGY, true) < WORK_ENERGY) {
            return;
        }
        iterateBlueprint(serverLevel);
        if (plan == null || plan.isDone()) {
            return;
        }
        if (box == null) {
            box = plan.box();
        }
        if (robot == null) {
            robot = new ConstructionRobot(new QuarryArea(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()));
        }
        BuildPlan.Step step = plan.next(serverLevel, createMaterials(serverLevel));
        if (step != null) {
            robot.schedule(new ConstructionRobot.Task(step.pos(), step::execute));
        }
        sync();
    }

    /**
     * 1 マス分の材料を探すインベントリの集まり (旧 SurroundingInventory)。
     * アドオンは上書きして材料の判定・消費を差し替えられる (旧 TileBuilder.createBptBuilderBase の差し替え点に相当)。
     */
    protected BuilderMaterials createMaterials(ServerLevel serverLevel) {
        return BuilderMaterials.around(serverLevel, worldPosition, materials);
    }

    /** 旧 iterateBpt: 建てる設計図 (経路があれば経路上の次の位置) を用意する。 */
    private void iterateBlueprint(ServerLevel serverLevel) {
        ItemStack stack = inventory.getStackInSlot(BLUEPRINT_SLOT);
        if (!(stack.getItem() instanceof BlueprintItem)) {
            plan = null;
            robot = null;
            box = null;
            pathIterator = null;
            return;
        }
        if (plan != null && !plan.isDone()) {
            return;
        }
        if (path != null) {
            if (pathIterator == null && path.size() > 1) {
                pathIterator = new PathIterator(path, 0);
            }
            plan = pathIterator == null ? null : pathIterator.next(serverLevel);
            while (plan == null && pathIterator != null) {
                pathIterator = pathIterator.nextSegment();
                plan = pathIterator == null ? null : pathIterator.next(serverLevel);
            }
            if (plan != null) {
                box = plan.box();
            } else {
                done = true;
            }
        } else if (plan != null) {
            done = true;
            plan = null;
        } else {
            plan = instantiate(serverLevel, worldPosition, getBlockState().getValue(RotatableMachineBlock.FACING).getOpposite());
            if (plan != null) {
                box = plan.box();
            }
        }
        sync();
    }

    /** 旧 instanciateBluePrint: 設計図を {@code back} (ビルダーの背中側) に合わせて回し、{@code pos} を基点に建てる手順を作る。 */
    @Nullable
    private BuildPlan instantiate(ServerLevel serverLevel, BlockPos pos, Direction back) {
        BlueprintRef ref = BlueprintItem.ref(inventory.getStackInSlot(BLUEPRINT_SLOT));
        Blueprint blueprint = ref == null ? null : BlueprintStore.get(serverLevel.getServer()).get(ref.id());
        if (blueprint == null) {
            return null;
        }
        return new BuildPlan(blueprint.rotateClockwise(RotatableMachineBlock.turnsFromEast(back)), pos);
    }

    /** 先頭の枠が変わったとき (旧 setInventorySlotContents(0, …) → iterateBpt, done = false)。 */
    private void blueprintChanged() {
        if (level instanceof ServerLevel serverLevel) {
            plan = null;
            pathIterator = null;
            done = inventory.getStackInSlot(BLUEPRINT_SLOT).isEmpty();
            iterateBlueprint(serverLevel);
        }
    }

    /**
     * 経路の 1 区間 (旧 PathIterator)。区間の始点から終点へ 0.1 ずつ進み、前に建てた範囲と重ならない最初の位置に建てる。
     * 設計図の向きは区間の進む向き (X と Z の大きい方)。
     */
    private final class PathIterator {
        private final int segment;
        private final Vec3 step;
        private final BlockPos to;
        private final Direction direction;
        private Vec3 position;
        private double lastDistance;
        @Nullable
        private BoundingBox previous;

        PathIterator(List<BlockPos> route, int segment) {
            this.segment = segment;
            BlockPos from = route.get(segment);
            this.to = route.get(segment + 1);
            Vec3 delta = Vec3.atLowerCornerOf(to.subtract(from));
            this.step = delta.normalize().scale(PATH_STEP);
            this.position = Vec3.atLowerCornerOf(from);
            this.lastDistance = position.distanceToSqr(Vec3.atLowerCornerOf(to));
            this.direction = Math.abs(delta.x) > Math.abs(delta.z)
                    ? delta.x > 0 ? Direction.EAST : Direction.WEST
                    : delta.z > 0 ? Direction.SOUTH : Direction.NORTH;
        }

        @Nullable
        BuildPlan next(ServerLevel serverLevel) {
            while (true) {
                BlockPos at = BlockPos.containing(Math.round(position.x), Math.round(position.y), Math.round(position.z));
                BuildPlan candidate = instantiate(serverLevel, at, direction);
                if (candidate == null) {
                    return null;
                }
                if (previous == null || !previous.intersects(candidate.box())) {
                    previous = candidate.box();
                    return candidate;
                }
                position = position.add(step);
                double distance = position.distanceToSqr(Vec3.atLowerCornerOf(to));
                if (distance > lastDistance) {
                    return null;
                }
                lastDistance = distance;
            }
        }

        /** 旧 iterate: 次の区間へ。前の区間で最後に建てた範囲を引き継ぐ。 */
        @Nullable
        PathIterator nextSegment() {
            if (path == null || segment + 2 >= path.size()) {
                return null;
            }
            PathIterator next = new PathIterator(path, segment + 1);
            next.previous = previous;
            return next;
        }
    }

    // ---- 状態 -----------------------------------------------------------------

    @Override
    public boolean isActive() {
        return !done;
    }

    @Override
    public boolean manageSolids() {
        return true;
    }

    @Override
    public int powerRequest() {
        return (plan != null || pathIterator != null) && !done ? powerProvider.getMaxEnergyReceived() : 0;
    }

    /** GUI: 設計図を建てているか (旧 isBuildingBlueprint)。 */
    public boolean isBuildingBlueprint() {
        ItemStack stack = inventory.getStackInSlot(BLUEPRINT_SLOT);
        return stack.getItem() instanceof BlueprintItem item && item.kind() == Blueprint.Kind.BLUEPRINT;
    }

    /** GUI: 残りに必要なアイテム (旧 getNeededItems)。 */
    public List<ItemStack> neededItems() {
        return plan == null ? List.of() : plan.neededItems();
    }

    public ItemStackHandler getInventory() {
        return inventory;
    }

    /** パイプ・ホッパー向け: 材料枠だけ。 */
    public IItemHandler getMaterials() {
        return materials;
    }

    @Nullable
    @Override
    public BoundingBox getBox() {
        return box;
    }

    @Nullable
    public Vec3 getRobotPosition() {
        return clientRobot;
    }

    @Nullable
    public BlockPos getRobotTarget() {
        return clientRobotTarget;
    }

    public float getRobotLaserPower() {
        return clientRobotPower;
    }

    @Override
    public void dropContents() {
        if (level != null) {
            InventoryUtils.dropAll(level, worldPosition, inventory);
        }
    }

    // ---- MenuProvider -------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new BuilderMenu(containerId, playerInventory, this);
    }

    // ---- 保存と同期 ---------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(TAG_ITEMS, inventory.serializeNBT(registries));
        writeBox(tag);
        if (path != null) {
            ListTag list = new ListTag();
            path.forEach(pos -> list.add(NbtUtils.writeBlockPos(pos)));
            tag.put(TAG_PATH, list);
        }
        tag.putBoolean(TAG_DONE, done);
    }

    private void writeBox(CompoundTag tag) {
        if (box != null) {
            tag.put(TAG_BOX, new IntArrayTag(new int[]{box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()}));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        int[] values = tag.contains(TAG_BOX, Tag.TAG_INT_ARRAY) ? tag.getIntArray(TAG_BOX) : new int[0];
        box = values.length == 6 ? new BoundingBox(values[0], values[1], values[2], values[3], values[4], values[5]) : null;
        if (tag.contains(TAG_ROBOT)) {
            int[] robotValues = tag.getIntArray(TAG_ROBOT);
            clientRobot = robotValues.length == 3 ? new Vec3(robotValues[0] / 16.0, robotValues[1] / 16.0, robotValues[2] / 16.0) : null;
            clientRobotTarget = NbtUtils.readBlockPos(tag, TAG_ROBOT_TARGET).orElse(null);
            clientRobotPower = tag.getFloat(TAG_ROBOT_POWER);
        } else {
            clientRobot = null;
            clientRobotTarget = null;
        }
        if (!tag.contains(TAG_ITEMS)) {
            return;
        }
        inventory.deserializeNBT(registries, tag.getCompound(TAG_ITEMS));
        if (tag.contains(TAG_PATH, Tag.TAG_LIST)) {
            ListTag list = tag.getList(TAG_PATH, Tag.TAG_INT_ARRAY);
            List<BlockPos> loaded = new ArrayList<>();
            for (int i = 0; i < list.size(); i++) {
                int[] pos = list.getIntArray(i);
                if (pos.length == 3) {
                    loaded.add(new BlockPos(pos[0], pos[1], pos[2]));
                }
            }
            path = loaded;
        }
        done = tag.getBoolean(TAG_DONE);
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        writeBox(tag);
        if (robot != null) {
            Vec3 position = robot.position();
            tag.putIntArray(TAG_ROBOT, new int[]{(int) Math.round(position.x * 16), (int) Math.round(position.y * 16),
                    (int) Math.round(position.z * 16)});
            BlockPos target = robot.currentTarget();
            if (target != null) {
                tag.put(TAG_ROBOT_TARGET, NbtUtils.writeBlockPos(target));
            }
            tag.putFloat(TAG_ROBOT_POWER, robot.getLaserPower());
        }
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
