package com.takoy3466.re_buildcraft.builders;

import com.takoy3466.re_buildcraft.Config;
import com.takoy3466.re_buildcraft.api.area.IAreaProvider;
import com.takoy3466.re_buildcraft.api.util.InventoryUtils;
import com.takoy3466.re_buildcraft.registry.ModBlockEntities;
import com.takoy3466.re_buildcraft.registry.ModBlocks;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

/**
 * マーカー (青マーカー)。元実装: {@code buildcraft.builders.TileMarker} (BCx 版)。
 * <ul>
 *   <li>右クリックで X・Y・Z の各軸方向に {@code markerMaxRange} 未満の距離にある別のマーカーを探してつなぐ (旧 tryConnection / setVect)。</li>
 *   <li>つながったマーカーの組は 1 つの「原点」を共有し、原点が範囲 (直方体) を持って赤いレーザーで囲む (旧 Origin / createLasers)。</li>
 *   <li>レッドストーン信号を受けている間、まだつながっていない軸に沿って距離の目安となる線を出す (旧 switchSignals)。</li>
 *   <li>マーカーを持ってスニークで右クリックすると、残りの角に自動でマーカーを置く (旧 trySetBlockAndConnection)。</li>
 *   <li>どれか 1 つが壊れると組全体のつながりが解除される (旧 destroy)。</li>
 * </ul>
 * 旧実装はつながったマーカーが同じ Origin オブジェクトを共有していた。ここでは各マーカーが原点の位置を、
 * 原点のマーカーが各軸の相手の位置を保存し、チャンクの読み込み状態に関係なく復元できるようにした。
 */
public class MarkerBlockEntity extends BlockEntity implements IAreaProvider {
    public static final int AXIS_COUNT = 3;
    private static final Direction.Axis[] AXES = {Direction.Axis.X, Direction.Axis.Y, Direction.Axis.Z};
    /** 旧 checkMarker(ai, i) の既定値。自動設置ではつなぎ替えによる撤去を行わない。 */
    private static final int NO_REPLACE_DISTANCE = 0x70000000;
    /** 自動設置で上に置くか下に置くかの境目 (旧 rotationPitch < 75)。 */
    private static final float DOWNWARD_PITCH = 75.0F;
    private static final String TAG_ORIGIN = "origin";
    private static final String TAG_LINK = "link";
    private static final String TAG_SIGNALS = "signals";
    private static final String TAG_LINKED = "linked";

    /** 組の原点のマーカーの位置 (自分が原点なら自分)。つながっていなければ null。 */
    @Nullable
    private BlockPos originPos;
    /** 原点のときだけ使う: 各軸でつながっている相手の位置。 */
    private final BlockPos[] links = new BlockPos[AXIS_COUNT];
    /** 原点のときだけ使う: 範囲。 */
    @Nullable
    private BoundingBox box;
    /** つながっている軸 (描画用。原点から全員へ配る)。 */
    private final boolean[] linkedAxes = new boolean[AXIS_COUNT];
    private boolean signals;

    public MarkerBlockEntity(BlockPos pos, BlockState state) {
        this(ModBlockEntities.MARKER.get(), pos, state);
    }

    /** 緑マーカー ({@link PathMarkerBlockEntity}) など、マーカーを元にしたブロックエンティティ用。 */
    protected MarkerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    // ---- 状態 -----------------------------------------------------------------

    public boolean isLinked() {
        return originPos != null;
    }

    public boolean isOrigin() {
        return worldPosition.equals(originPos);
    }

    /** 原点のときの範囲 (描画用)。 */
    @Nullable
    public BoundingBox getBox() {
        return isOrigin() ? box : null;
    }

    public boolean isAxisLinked(int axis) {
        return linkedAxes[axis];
    }

    public boolean showsSignals() {
        return signals;
    }

    @Nullable
    private MarkerBlockEntity markerAt(@Nullable BlockPos pos) {
        return pos != null && level != null && level.getBlockEntity(pos) instanceof MarkerBlockEntity marker ? marker : null;
    }

    @Nullable
    private MarkerBlockEntity origin() {
        return markerAt(originPos);
    }

    /** 自分の組で、指定の軸がまだつながっていないか (旧 !origin.isSet() || !origin.vect[i].isSet())。 */
    private boolean isAxisFree(int axis) {
        MarkerBlockEntity origin = origin();
        return origin == null || origin.links[axis] == null;
    }

    // ---- IAreaProvider ----------------------------------------------------

    @Override
    public BoundingBox getArea() {
        MarkerBlockEntity origin = origin();
        if (origin != null && origin.box != null) {
            return origin.box;
        }
        return new BoundingBox(worldPosition);
    }

    /** 組のマーカーをすべて撤去し、1 個ずつこのマーカーの位置に落とす (旧 removeFromWorld / tryMarkerDrop)。 */
    @Override
    public void removeFromWorld() {
        if (level == null || !isLinked()) {
            return;
        }
        Level currentLevel = level;
        BlockPos dropPos = worldPosition;
        List<BlockPos> members = groupPositions();
        for (BlockPos member : members) {
            currentLevel.removeBlock(member, false);
            InventoryUtils.dropStack(currentLevel, dropPos, new ItemStack(ModBlocks.MARKER.get()));
        }
    }

    /** 組のマーカーの位置 (原点を含む)。 */
    private List<BlockPos> groupPositions() {
        List<BlockPos> positions = new ArrayList<>();
        MarkerBlockEntity origin = origin();
        if (origin == null) {
            return positions;
        }
        for (BlockPos link : origin.links) {
            if (link != null) {
                positions.add(link);
            }
        }
        positions.add(origin.worldPosition);
        return positions;
    }

    // ---- つなぐ -------------------------------------------------------------

    /** 右クリック: つながっていない軸ごとに相手を探す (旧 tryConnection)。 */
    public void tryConnection(@Nullable Player player) {
        if (level == null || level.isClientSide) {
            return;
        }
        for (int axis = 0; axis < AXIS_COUNT; axis++) {
            if (isAxisFree(axis)) {
                searchAxis(axis, player);
            }
        }
    }

    /** 旧 setVect: 近い順に正方向・負方向を交互に調べる。Y 方向はワールドの高さの範囲内だけ。 */
    private void searchAxis(int axis, @Nullable Player player) {
        int maxRange = Config.MARKER_MAX_RANGE.get();
        for (int distance = 1; distance < maxRange; distance++) {
            BlockPos plus = worldPosition.relative(AXES[axis], distance);
            if ((axis != 1 || plus.getY() < level.getMaxBuildHeight()) && checkMarker(plus, axis, distance, player)) {
                break;
            }
            BlockPos minus = worldPosition.relative(AXES[axis], -distance);
            if ((axis != 1 || minus.getY() >= level.getMinBuildHeight()) && checkMarker(minus, axis, distance, player)) {
                break;
            }
        }
    }

    /**
     * 旧 checkMarker: その位置にマーカーがあればつなぐ。相手がすでに同じ軸でつながった組に属している場合は、
     * 範囲が上限を超えないことを確認し、つないだ結果が相手を越えた先になったら間の相手を撤去する。
     *
     * @return 探索を終えてよい場合 true
     */
    private boolean checkMarker(BlockPos pos, int axis, int distance, @Nullable Player player) {
        MarkerBlockEntity other = markerAt(pos);
        if (other == null) {
            return false;
        }
        MarkerBlockEntity otherOrigin = other.origin();
        if (otherOrigin != null && otherOrigin.links[axis] != null) {
            int originCoordinate = otherOrigin.worldPosition.get(AXES[axis]);
            int size = Math.abs(pos.get(AXES[axis]) - originCoordinate) + 1;
            if (size > Config.MARKER_MAX_RANGE.get()) {
                message(player, Component.translatable("message.re_buildcraft.marker.too_far"));
                return true;
            }
        }
        if (!linkTo(other, axis)) {
            return false;
        }
        MarkerBlockEntity origin = origin();
        if (origin != null && origin.size(axis) > distance + 1 && other != this && !other.isOrigin()) {
            // 相手を越えた位置のマーカーへつなぎ替えた: 間に残った相手を撤去する。
            other.resetLink();
            level.removeBlock(other.worldPosition, false);
            InventoryUtils.dropStack(level, worldPosition, new ItemStack(ModBlocks.MARKER.get()));
        }
        return true;
    }

    /** 原点の範囲の、指定の軸の長さ (旧 Origin.getSize)。 */
    private int size(int axis) {
        if (box == null || links[axis] == null) {
            return 0;
        }
        return switch (axis) {
            case 0 -> box.getXSpan();
            case 1 -> box.getYSpan();
            default -> box.getZSpan();
        };
    }

    /** 旧 linkTo の 3 通り: どちらも未接続なら自分が原点、片方だけ接続済みならその組に入れる。両方接続済みならつながない。 */
    private boolean linkTo(MarkerBlockEntity other, int axis) {
        if (isLinked() && other.isLinked()) {
            return false;
        }
        MarkerBlockEntity origin;
        if (!isLinked() && !other.isLinked()) {
            origin = this;
            originPos = worldPosition;
            setLink(origin, axis, other);
        } else if (!isLinked()) {
            origin = other.origin();
            if (origin == null) {
                return false;
            }
            originPos = origin.worldPosition;
            setLink(origin, axis, this);
        } else {
            origin = origin();
            if (origin == null) {
                return false;
            }
            setLink(origin, axis, other);
        }
        origin.updateBox();
        origin.refreshGroup();
        return true;
    }

    /** 原点の {@code axis} 軸の相手を差し替える。前の相手は組から外す (取り残されたマーカーが組の一員のままにならないように)。 */
    private static void setLink(MarkerBlockEntity origin, int axis, MarkerBlockEntity partner) {
        BlockPos previous = origin.links[axis];
        if (previous != null && !previous.equals(partner.worldPosition)) {
            MarkerBlockEntity old = origin.markerAt(previous);
            if (old != null && old != origin) {
                old.resetLink();
            }
        }
        origin.links[axis] = partner.worldPosition;
        partner.originPos = origin.worldPosition;
    }

    /** 旧 createLasers の範囲計算: つながっていない軸は原点の座標だけ。 */
    private void updateBox() {
        int[] min = {worldPosition.getX(), worldPosition.getY(), worldPosition.getZ()};
        int[] max = min.clone();
        for (int axis = 0; axis < AXIS_COUNT; axis++) {
            if (links[axis] != null) {
                int partner = links[axis].get(AXES[axis]);
                min[axis] = Math.min(min[axis], partner);
                max[axis] = Math.max(max[axis], partner);
            }
        }
        box = new BoundingBox(min[0], min[1], min[2], max[0], max[1], max[2]);
    }

    /** 組の全員に、つながっている軸・原点の見た目を反映して同期する。 */
    private void refreshGroup() {
        boolean[] linked = new boolean[AXIS_COUNT];
        for (int axis = 0; axis < AXIS_COUNT; axis++) {
            linked[axis] = links[axis] != null;
        }
        for (BlockPos member : groupPositions()) {
            MarkerBlockEntity marker = markerAt(member);
            if (marker != null) {
                System.arraycopy(linked, 0, marker.linkedAxes, 0, AXIS_COUNT);
                marker.setOriginState(marker.isOrigin());
                marker.sync();
            }
        }
    }

    /** つながりを解除する (自分だけ)。 */
    private void resetLink() {
        originPos = null;
        box = null;
        Arrays.fill(links, null);
        Arrays.fill(linkedAxes, false);
        setOriginState(false);
        sync();
    }

    /** 壊れたとき: 組全体のつながりを解除する (旧 destroy)。 */
    public void destroy() {
        if (level == null || level.isClientSide || !isLinked()) {
            return;
        }
        for (BlockPos member : groupPositions()) {
            MarkerBlockEntity marker = markerAt(member);
            if (marker != null && marker != this) {
                marker.resetLink();
            }
        }
        originPos = null;
        box = null;
        Arrays.fill(links, null);
    }

    private void setOriginState(boolean origin) {
        BlockState state = getBlockState();
        if (level != null && state.hasProperty(MarkerBlock.ORIGIN) && state.getValue(MarkerBlock.ORIGIN) != origin) {
            level.setBlock(worldPosition, state.setValue(MarkerBlock.ORIGIN, origin), Block.UPDATE_ALL);
        }
    }

    // ---- 自動設置 -------------------------------------------------------------

    /**
     * 旧 trySetBlockAndConnection: X だけつながっていれば Z 方向に、Z だけなら X 方向に、X・Z がつながっていて Y が未接続なら
     * 上下方向に、すでにある辺と同じ長さの位置へマーカーを置いてつなぐ。
     *
     * @return 何か処理した場合 true
     */
    public boolean trySetBlockAndConnection(Player player, ItemStack held) {
        if (level == null || level.isClientSide) {
            return false;
        }
        MarkerBlockEntity origin = origin();
        if (origin == null) {
            tryConnection(player);
            return true;
        }
        BlockPos base = origin.worldPosition;
        int quadrant = Mth.floor(player.getYRot() / 90.0F + 2.0F) & 3; // 0: 北東, 1: 南東, 2: 南西, 3: 北西
        boolean x = origin.links[0] != null;
        boolean y = origin.links[1] != null;
        boolean z = origin.links[2] != null;
        BlockPos target;
        int axis;
        if (x && !z) {
            int range = Math.abs(origin.links[0].getX() - base.getX());
            target = base.offset(0, 0, quadrant == 1 || quadrant == 2 ? range : -range);
            axis = 2;
        } else if (!x && z) {
            int range = Math.abs(origin.links[2].getZ() - base.getZ());
            target = base.offset(quadrant == 0 || quadrant == 1 ? range : -range, 0, 0);
            axis = 0;
        } else if (x && !y) {
            int range = Math.max(Math.abs(origin.links[0].getX() - base.getX()), Math.abs(origin.links[2].getZ() - base.getZ()));
            int targetY = Mth.clamp(base.getY() + (player.getXRot() < DOWNWARD_PITCH ? range : -range),
                    level.getMinBuildHeight(), level.getMaxBuildHeight() - 1);
            target = new BlockPos(base.getX(), targetY, base.getZ());
            axis = 1;
        } else {
            return true;
        }
        if (placeMarker(target, player, held)) {
            checkMarker(target, axis, NO_REPLACE_DISTANCE, player);
        }
        return true;
    }

    /** 旧 trySetMarker: ブロックエンティティのある場所には置かない。置き換えられないブロックも壊さない (変更点)。 */
    private boolean placeMarker(BlockPos target, Player player, ItemStack held) {
        if (level.getBlockEntity(target) != null || !level.getBlockState(target).canBeReplaced()) {
            message(player, Component.translatable("message.re_buildcraft.marker.blocked", target.getX(), target.getY(), target.getZ()));
            return false;
        }
        level.setBlock(target, ModBlocks.MARKER.get().defaultBlockState(), Block.UPDATE_ALL);
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }
        return true;
    }

    private static void message(@Nullable Player player, Component text) {
        if (player != null) {
            player.displayClientMessage(text, false);
        }
    }

    // ---- 信号表示 ---------------------------------------------------------------

    /** 隣の変化: レッドストーン信号の有無で目安線の表示を切り替える (旧 switchSignals)。 */
    public void updateSignals() {
        if (level == null || level.isClientSide) {
            return;
        }
        boolean powered = level.hasNeighborSignal(worldPosition);
        if (powered != signals) {
            signals = powered;
            sync();
        }
    }

    protected void sync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    // ---- 保存と同期 ---------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        writeState(tag);
    }

    private void writeState(CompoundTag tag) {
        if (originPos != null) {
            tag.put(TAG_ORIGIN, NbtUtils.writeBlockPos(originPos));
        }
        for (int axis = 0; axis < AXIS_COUNT; axis++) {
            if (links[axis] != null) {
                tag.put(TAG_LINK + axis, NbtUtils.writeBlockPos(links[axis]));
            }
        }
        for (int axis = 0; axis < AXIS_COUNT; axis++) {
            tag.putBoolean(TAG_LINKED + axis, linkedAxes[axis]);
        }
        tag.putBoolean(TAG_SIGNALS, signals);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        originPos = NbtUtils.readBlockPos(tag, TAG_ORIGIN).orElse(null);
        for (int axis = 0; axis < AXIS_COUNT; axis++) {
            links[axis] = NbtUtils.readBlockPos(tag, TAG_LINK + axis).orElse(null);
        }
        signals = tag.getBoolean(TAG_SIGNALS);
        if (isOrigin()) {
            updateBox();
        } else {
            box = null;
        }
        for (int axis = 0; axis < AXIS_COUNT; axis++) {
            linkedAxes[axis] = tag.getBoolean(TAG_LINKED + axis);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        writeState(tag);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
