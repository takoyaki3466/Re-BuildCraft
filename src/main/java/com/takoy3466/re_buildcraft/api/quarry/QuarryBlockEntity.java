package com.takoy3466.re_buildcraft.api.quarry;

import com.takoy3466.re_buildcraft.api.IMachine;
import com.takoy3466.re_buildcraft.api.area.AreaProviders;
import com.takoy3466.re_buildcraft.api.area.IAreaProvider;
import com.takoy3466.re_buildcraft.api.power.PoweredBlockEntity;
import com.takoy3466.re_buildcraft.api.transport.IPipeConnectable;
import com.takoy3466.re_buildcraft.api.util.InventoryUtils;
import com.takoy3466.re_buildcraft.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import java.util.Comparator;
import org.jetbrains.annotations.Nullable;

/**
 * クアーリー。後ろ側に枠組みを組み立て、その内側をドリルアームで 1 ブロックずつ掘り下げる。
 * 元実装: {@code buildcraft.factory.TileQuarry}。
 * <p>
 * 処理の流れは旧実装と同じ:
 * <ol>
 *   <li>作業範囲を決め、枠組みの設計図を作る ({@link FrameBlueprint})。</li>
 *   <li>1 マスにつき 25 MJ を使って建設ロボット ({@link ConstructionRobot}) に作業を予約し、ロボットが実行する。</li>
 *   <li>枠組みが完成したらドリルアーム ({@link MechanicalArm}) を出し、上の層から蛇行しながら採掘対象を探す。</li>
 *   <li>アームは蓄積 MJ に応じた速さで移動し、到達したブロックを掘って隣接インベントリ → パイプ → 上方の順に排出する。</li>
 * </ol>
 * CE の改良型クアーリーは、このクラスの protected メソッドを上書きして挙動を変える
 * (旧 {@code compactengine.TileQuarry2} が {@code TileQuarry} を継承していたのと同じ関係)。
 */
public class QuarryBlockEntity extends PoweredBlockEntity implements IMachine, IPipeConnectable {
    /** BC 標準クアーリーの既定の作業範囲 (旧 DefaultAreaProvider: 11 x 5 x 11)。 */
    private static final int DEFAULT_AREA_SIZE = 11;
    private static final int DEFAULT_AREA_HALF_WIDTH = 5;
    protected static final int MIN_AREA_HEIGHT = 5;
    /** マーカーの範囲を使う最小の幅・奥行き (旧 xSize < 3 || zSize < 3 なら既定範囲)。 */
    private static final int MIN_AREA_SIDE = 3;
    private static final int MAX_ENERGY_STORED = 7000;
    protected static final int FRAME_COST = 25;
    /**
     * 作業範囲のうち読み込まれていないチャンクを、作業する間だけ読み込むための一時チケット。
     * 期限 (tick) が切れると自動で外れる。同じチャンクへ付け直すと期限が延びる。常時読み込み (チャンクローダー) ではない。
     */
    private static final TicketType<ChunkPos> WORK_CHUNK_TICKET =
            TicketType.create("re_buildcraft_quarry", Comparator.comparingLong(ChunkPos::toLong), 300);
    /** チケットの読み込み半径。中心チャンクとその隣までを読み込み、ブロック更新・液体の隣接判定を行えるようにする。 */
    private static final int WORK_CHUNK_TICKET_RADIUS = 1;
    /** 採掘対象の探索を始める高さ (クアーリーより 3 ブロック上から)。 */
    private static final int SEARCH_START_ABOVE = 3;
    private static final int NO_TARGET_Y = Integer.MAX_VALUE;
    private static final double ARM_RAIL_OFFSET = 0.75;
    private static final double ARM_RAIL_HEIGHT_OFFSET = 0.25;
    private static final double ARM_LENGTH_OFFSET = 0.5;
    private static final int ARM_FRAME_INSET = 2;
    private static final int PARKING_HEIGHT = 2;
    private static final int ROBOT_SYNC_INTERVAL_TICKS = 5;

    private static final String TAG_AREA = "box";
    private static final String TAG_TARGET_X = "targetX";
    private static final String TAG_TARGET_Y = "targetY";
    private static final String TAG_TARGET_Z = "targetZ";
    private static final String TAG_ARM = "arm";
    private static final String TAG_BUILDING = "building";
    private static final String TAG_ROBOT_X = "robotX";
    private static final String TAG_ROBOT_Y = "robotY";
    private static final String TAG_ROBOT_Z = "robotZ";
    private static final String TAG_ROBOT_TARGET = "robotTarget";
    private static final String TAG_ROBOT_POWER = "robotPower";

    @Nullable
    protected QuarryArea area;
    @Nullable
    protected FrameBlueprint blueprint;
    @Nullable
    protected ConstructionRobot robot;
    @Nullable
    protected MechanicalArm arm;
    protected boolean inProcess;
    protected boolean isDigging;
    protected int targetX;
    /** 直前の探索が読み込まれていないチャンクで止まったか (保存しない。次の探索で決め直す)。 */
    private boolean waitingForChunk;
    protected int targetY = NO_TARGET_Y;
    protected int targetZ;
    private boolean initialized;

    // クライアント表示用
    private boolean clientBuilding;
    @Nullable
    private Vec3 clientRobotPosition;
    @Nullable
    private BlockPos clientRobotTarget;
    private float clientRobotPower;

    public QuarryBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        configurePower(true);
    }

    // ---- 調整用の差し替え点 (CE の改良型クアーリーが上書きする) ------------------

    /**
     * 枠組み建設中 ({@code building} = true) と採掘中の動力設定。
     * 元実装: {@code TileQuarry.setProviderConfigure}。
     */
    protected void configurePower(boolean building) {
        if (building) {
            powerProvider.configure(FRAME_COST, FRAME_COST, FRAME_COST, MAX_ENERGY_STORED);
        } else {
            powerProvider.configure(30, 200, 50, MAX_ENERGY_STORED);
        }
    }

    /** アームを 1 tick 動かすのに使うエネルギー (旧 moveArm の energyToUse)。 */
    protected float armEnergyPerTick() {
        return 2.0F + powerProvider.getEnergyStored() / 1000.0F;
    }

    /** 使ったエネルギーに対するアームの移動量。 */
    protected double armStep(float energy) {
        return 0.015 + energy / 200.0;
    }

    /** 掘り下げる最下層。旧実装の miningYpos (0) は 1.21.1 ではワールドの最下層にあたる。 */
    protected int miningMinY(Level level) {
        return level.getMinBuildHeight();
    }

    /**
     * 周囲にマーカーが無いときの作業範囲 (旧 setBoundaries の既定値)。
     * クアーリーの背面側に、幅 11・奥行き 11・高さ 5 の範囲を取る。
     */
    protected QuarryArea defaultArea(Direction facing) {
        int x = worldPosition.getX();
        int y = worldPosition.getY();
        int z = worldPosition.getZ();
        int startX;
        int startZ;
        switch (facing) {
            case SOUTH -> {
                startX = x - DEFAULT_AREA_HALF_WIDTH;
                startZ = z - DEFAULT_AREA_SIZE;
            }
            case WEST -> {
                startX = x + 1;
                startZ = z - DEFAULT_AREA_HALF_WIDTH;
            }
            case EAST -> {
                startX = x - DEFAULT_AREA_SIZE;
                startZ = z - DEFAULT_AREA_HALF_WIDTH;
            }
            default -> {
                startX = x - DEFAULT_AREA_HALF_WIDTH;
                startZ = z + 1;
            }
        }
        return QuarryArea.ofSize(startX, y, startZ, DEFAULT_AREA_SIZE, MIN_AREA_HEIGHT, DEFAULT_AREA_SIZE);
    }

    /**
     * 最初の作業範囲 (旧 setBoundaries(false))。隣にマーカーなどの範囲提供者があり、X・Z が 3 以上ならその範囲
     * (高さは最低 5) を使い、提供者 (マーカー) を撤去する。無ければ、または小さすぎれば {@link #defaultArea}。
     */
    protected QuarryArea initialArea(Level level) {
        IAreaProvider provider = AreaProviders.findNearby(level, worldPosition);
        if (provider != null) {
            BoundingBox box = provider.getArea();
            if (box.getXSpan() >= MIN_AREA_SIDE && box.getZSpan() >= MIN_AREA_SIDE) {
                QuarryArea marked = new QuarryArea(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ());
                provider.removeFromWorld();
                return marked.withMinimumHeight(MIN_AREA_HEIGHT);
            }
        }
        Direction facing = getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        return defaultArea(facing).withMinimumHeight(MIN_AREA_HEIGHT);
    }

    /** 柔らかいブロック (空気・液体・草など)。枠組み建設の撤去対象外で、採掘対象にもならない。 */
    protected boolean isSoft(BlockState state) {
        return state.isAir() || state.canBeReplaced();
    }

    /** 採掘できず、その下も掘らせない障害物 (旧 blockDig: 岩盤・溶岩)。 */
    protected boolean blocksDigging(Level level, BlockPos pos, BlockState state) {
        return state.getDestroySpeed(level, pos) < 0.0F || state.getFluidState().is(FluidTags.LAVA);
    }

    /** 掘ってよいブロックか (旧 canDig)。 */
    protected boolean canDig(Level level, BlockPos pos, BlockState state) {
        return !blocksDigging(level, pos, state) && !isSoft(state) && !isFrame(state)
                && !state.is(Blocks.END_PORTAL) && !state.is(Blocks.END_PORTAL_FRAME);
    }

    /** クアーリーの枠組みか。枠組みは掘らない。 */
    protected final boolean isFrame(BlockState state) {
        return state.is(ModBlocks.FRAME.get());
    }

    /** 採掘対象として選んでよいか。上に岩盤や溶岩があるとアームが届かないので選ばない (旧 checkTarget)。 */
    protected boolean isValidTarget(Level level, BlockPos pos, BlockState state) {
        if (!canDig(level, pos, state)) {
            return false;
        }
        BlockPos.MutableBlockPos above = pos.mutable();
        for (int y = pos.getY() + 1; y < level.getMaxBuildHeight(); y++) {
            above.setY(y);
            if (blocksDigging(level, above, level.getBlockState(above))) {
                return false;
            }
        }
        return true;
    }

    /** アームが目標に着いたときの処理 (旧 positionReached)。既定では真下のブロックを掘る。 */
    protected void onTargetReached(ServerLevel level, BlockPos pos) {
        if (canDig(level, pos, level.getBlockState(pos))) {
            digBlock(level, pos);
        }
    }

    /** 採掘を終えたときの処理 (旧 stopQuarry)。 */
    protected void onQuarryStopped() {
    }

    // ---- tick -------------------------------------------------------------

    public final void tick() {
        if (level == null) {
            return;
        }
        if (level.isClientSide) {
            clientTick();
            return;
        }
        if (!initialized) {
            initialized = true;
            createUtilsIfNeeded(level);
            sendNetworkUpdate();
        }
        powerProvider.update(this, level);
        if (inProcess && arm != null) {
            moveArm();
        }
        if (robot != null && blueprint != null) {
            robot.update(level, this::isSoft);
            if (level.getGameTime() % ROBOT_SYNC_INTERVAL_TICKS == 0L) {
                sendNetworkUpdate();
            }
        }
    }

    private void clientTick() {
        if (arm != null && arm.speed() > 0.0) {
            arm.doMove(arm.speed(), movedArm -> { });
        }
    }

    private void createUtilsIfNeeded(Level level) {
        if (area == null) {
            area = initialArea(level);
        }
        if (blueprint == null) {
            blueprint = new FrameBlueprint(level, area, ModBlocks.FRAME.get(), this::isSoft);
        }
        if (!blueprint.isDone()) {
            isDigging = true;
        } else if (arm == null) {
            // 旧実装: アームを出した直後に採掘対象があれば採掘を開始する。
            createArm();
        }
        setChanged();
    }

    private void createArm() {
        if (area == null) {
            return;
        }
        arm = MechanicalArm.create(
                area.minX() + ARM_RAIL_OFFSET,
                worldPosition.getY() + area.sizeY() - 1 + ARM_RAIL_HEIGHT_OFFSET,
                area.minZ() + ARM_RAIL_OFFSET,
                area.sizeX() - ARM_FRAME_INSET + ARM_LENGTH_OFFSET,
                area.sizeZ() - ARM_FRAME_INSET + ARM_LENGTH_OFFSET);
        if (level != null && findTarget(level, false)) {
            isDigging = true;
        }
    }

    private void moveArm() {
        float energyToUse = armEnergyPerTick();
        float energy = powerProvider.useEnergy(energyToUse, energyToUse, true);
        if (energy > 0.0F && arm != null) {
            arm.doMove(armStep(energy), this::positionReached);
        }
    }

    // ---- 作業 (IPowerReceptor#doWork) ------------------------------------

    @Override
    public void doWork() {
        if (!(level instanceof ServerLevel serverLevel) || inProcess || !isDigging) {
            return;
        }
        createUtilsIfNeeded(serverLevel);
        if (blueprint != null && !blueprint.isDone()) {
            buildFrame(serverLevel);
            return;
        }
        if (robot != null && robot.done()) {
            robot = null;
        }
        if (robot == null) {
            if (arm == null) {
                createArm();
            }
            configurePower(false);
            if (findTarget(serverLevel, true)) {
                inProcess = true;
                sendNetworkUpdate();
            } else if (!waitingForChunk) {
                stopQuarry();
                inProcess = true;
                sendNetworkUpdate();
            }
        }
    }

    private void buildFrame(ServerLevel level) {
        configurePower(true);
        if (powerProvider.useEnergy(FRAME_COST, FRAME_COST, true) != FRAME_COST || area == null || blueprint == null) {
            return;
        }
        if (robot == null) {
            robot = new ConstructionRobot(area);
        }
        if (robot.readyToBuild()) {
            FrameBlueprint.Slot slot = blueprint.nextSlot(level);
            if (slot == null && blueprint.waitingFor() != null) {
                requestWorkChunk(level, blueprint.waitingFor());
            }
            if (slot != null) {
                FrameBlueprint frames = blueprint;
                robot.schedule(new ConstructionRobot.Task(slot.pos(), robotLevel -> frames.apply(robotLevel, slot)));
            }
        }
    }

    private void stopQuarry() {
        if (arm != null && area != null) {
            arm.setTarget(area.minX() + arm.lengthX() / 2.0, worldPosition.getY() + PARKING_HEIGHT, area.minZ() + arm.lengthX() / 2.0);
        }
        isDigging = false;
        targetY = NO_TARGET_Y;
        onQuarryStopped();
    }

    // ---- 採掘対象の探索 ---------------------------------------------------

    /** @param setTarget true ならアームの目標に設定する */
    protected boolean findTarget(Level level, boolean setTarget) {
        waitingForChunk = false;
        int startY = Math.min(targetY, worldPosition.getY() + SEARCH_START_ABOVE);
        for (int y = startY; y >= miningMinY(level); y--) {
            if (findTargetInLayer(level, setTarget, y) || waitingForChunk) {
                return !waitingForChunk;
            }
        }
        return false;
    }

    /**
     * 直前の探索が、読み込まれていないチャンクの位置で止まったか。
     * true の間は「掘る物が無い」のではなく「まだ調べられない」ので、採掘を終了しない。
     * 探索を上書きする派生クラスは、{@link #tryTarget} が false を返した後にこれを確認し、true なら探索をそこで止めること。
     */
    protected final boolean isWaitingForChunk() {
        return waitingForChunk;
    }

    /**
     * 作業範囲の位置を含むチャンクを、一時チケットで読み込ませる (読み込みは非同期で、後の tick に完了する)。
     * 旧版 (1.2.5) はブロックを調べるだけでチャンクが読み込まれたため、範囲の一部が読み込まれていなくても採掘が続いた。
     */
    protected final void requestWorkChunk(Level level, BlockPos pos) {
        if (level instanceof ServerLevel serverLevel) {
            ChunkPos chunk = new ChunkPos(pos);
            serverLevel.getChunkSource().addRegionTicket(WORK_CHUNK_TICKET, chunk, WORK_CHUNK_TICKET_RADIUS, chunk);
        }
    }

    /**
     * 1 層分を、層ごとに向きを変えながら蛇行して探す (旧 findTarget(boolean, int))。
     * 範囲は枠組みの内側 (外周 1 ブロックを除く)。
     */
    protected boolean findTargetInLayer(Level level, boolean setTarget, int y) {
        if (area == null) {
            return false;
        }
        int innerX = area.sizeX() - 2;
        int innerZ = area.sizeZ() - 2;
        boolean forwardX = Math.floorMod(y, 2) == 0;
        for (int i = 0; i < innerX; i++) {
            int cx = forwardX ? i : innerX - 1 - i;
            boolean forwardZ = Math.floorMod(cx, 2) == Math.floorMod(y, 2);
            for (int j = 0; j < innerZ; j++) {
                int cz = forwardZ ? j : innerZ - 1 - j;
                if (tryTarget(level, setTarget, cx, y, cz)) {
                    return true;
                }
                if (waitingForChunk) {
                    return false;
                }
            }
        }
        return false;
    }

    /** 枠組みの内側座標 (cx, cz) のブロックを調べ、対象ならアームの目標にする。 */
    protected final boolean tryTarget(Level level, boolean setTarget, int cx, int y, int cz) {
        if (area == null) {
            return false;
        }
        BlockPos pos = new BlockPos(area.minX() + cx + 1, y, area.minZ() + cz + 1);
        if (!level.isLoaded(pos)) {
            // 読み込まれていない位置を「掘る物が無い」として飛ばすと、その部分は二度と掘られない。読み込みを頼んで待つ。
            requestWorkChunk(level, pos);
            waitingForChunk = true;
            return false;
        }
        if (!isValidTarget(level, pos, level.getBlockState(pos))) {
            return false;
        }
        if (setTarget && arm != null) {
            // アームが着くまで目標のチャンクが外れないよう、読み込みを延長しておく。
            requestWorkChunk(level, pos);
            arm.setTarget(pos.getX(), pos.getY() + 1, pos.getZ());
            targetX = pos.getX();
            targetY = pos.getY() + 1;
            targetZ = pos.getZ();
            setChanged();
        }
        return true;
    }

    private void positionReached(MechanicalArm reachedArm) {
        inProcess = false;
        if (level instanceof ServerLevel serverLevel) {
            BlockPos pos = new BlockPos(targetX, targetY - 1, targetZ);
            if (serverLevel.isLoaded(pos)) {
                onTargetReached(serverLevel, pos);
            }
        }
        setChanged();
    }

    /** ブロックを掘り、ドロップを排出する (旧 positionReached(int, int, int))。 */
    protected final void digBlock(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        for (ItemStack drop : Block.getDrops(state, level, pos, level.getBlockEntity(pos))) {
            popItem(drop);
        }
        level.removeBlock(pos, false);
    }

    /** 生成物を隣接インベントリ → パイプ → 上方の順に排出する (旧 popItem)。 */
    protected final void popItem(ItemStack stack) {
        if (level != null && !stack.isEmpty()) {
            InventoryUtils.ejectFromMachine(level, worldPosition, stack);
        }
    }

    // ---- 操作 -------------------------------------------------------------

    /** レンチで右クリックされたとき: 枠組みを作り直して採掘をやり直す (旧 reinitalize)。 */
    public void reinitialize() {
        if (level == null || area == null) {
            return;
        }
        blueprint = new FrameBlueprint(level, area, ModBlocks.FRAME.get(), this::isSoft);
        isDigging = true;
        targetY = NO_TARGET_Y;
        onReinitialized();
        setChanged();
    }

    /** 再初期化時の追加処理。 */
    protected void onReinitialized() {
    }

    /** プレイヤーが壊したとき、枠組みの上部 (最上層から 5 層) を取り除く (旧 BlockQuarry.removeBlockByPlayer)。 */
    public void removeFrames() {
        if (level == null || area == null) {
            return;
        }
        for (BlockPos pos : BlockPos.betweenClosed(area.minX(), area.maxY() - (MIN_AREA_HEIGHT - 1), area.minZ(),
                area.maxX(), area.maxY(), area.maxZ())) {
            if (level.getBlockState(pos).is(ModBlocks.FRAME.get())) {
                level.removeBlock(pos, false);
            }
        }
    }

    // ---- IMachine / IPipeConnectable / IPowerReceptor ---------------------

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

    // ---- 表示用 -------------------------------------------------------------

    @Nullable
    public QuarryArea getArea() {
        return area;
    }

    @Nullable
    public MechanicalArm getArm() {
        return arm;
    }

    /** 枠組みを建設中か (作業範囲の縞模様のレーザーを表示する)。 */
    public boolean isBuildingFrame() {
        return level != null && level.isClientSide ? clientBuilding : blueprint != null && !blueprint.isDone();
    }

    @Nullable
    public Vec3 getRobotPosition() {
        return clientRobotPosition;
    }

    @Nullable
    public BlockPos getRobotTarget() {
        return clientRobotTarget;
    }

    public float getRobotLaserPower() {
        return clientRobotPower;
    }

    protected final void sendNetworkUpdate() {
        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    // ---- 保存と同期 ---------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        writeSharedState(tag);
        tag.putInt(TAG_TARGET_X, targetX);
        tag.putInt(TAG_TARGET_Y, targetY);
        tag.putInt(TAG_TARGET_Z, targetZ);
    }

    private void writeSharedState(CompoundTag tag) {
        if (area != null) {
            tag.put(TAG_AREA, area.save());
        }
        if (arm != null) {
            tag.put(TAG_ARM, arm.save());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        area = tag.contains(TAG_AREA) ? QuarryArea.load(tag.getCompound(TAG_AREA)) : null;
        arm = tag.contains(TAG_ARM) ? MechanicalArm.load(tag.getCompound(TAG_ARM)) : null;
        if (tag.contains(TAG_TARGET_Y)) {
            targetX = tag.getInt(TAG_TARGET_X);
            targetY = tag.getInt(TAG_TARGET_Y);
            targetZ = tag.getInt(TAG_TARGET_Z);
        }
        clientBuilding = tag.getBoolean(TAG_BUILDING);
        clientRobotPosition = tag.contains(TAG_ROBOT_X)
                ? new Vec3(tag.getDouble(TAG_ROBOT_X), tag.getDouble(TAG_ROBOT_Y), tag.getDouble(TAG_ROBOT_Z)) : null;
        clientRobotTarget = tag.contains(TAG_ROBOT_TARGET) ? BlockPos.of(tag.getLong(TAG_ROBOT_TARGET)) : null;
        clientRobotPower = tag.getFloat(TAG_ROBOT_POWER);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        writeSharedState(tag);
        tag.putBoolean(TAG_BUILDING, isBuildingFrame());
        if (robot != null) {
            Vec3 position = robot.position();
            tag.putDouble(TAG_ROBOT_X, position.x);
            tag.putDouble(TAG_ROBOT_Y, position.y);
            tag.putDouble(TAG_ROBOT_Z, position.z);
            tag.putFloat(TAG_ROBOT_POWER, robot.getLaserPower());
            BlockPos target = robot.currentTarget();
            if (target != null) {
                tag.putLong(TAG_ROBOT_TARGET, target.asLong());
            }
        }
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
