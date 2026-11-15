package com.takoy3466.re_buildcraft.builders.filler;

import com.takoy3466.re_buildcraft.Config;
import com.takoy3466.re_buildcraft.builders.FakePlayerPlacement;
import com.takoy3466.re_buildcraft.builders.FillerBlockEntity;
import com.takoy3466.re_buildcraft.builders.SoftBlocks;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * フィラーのパターン。元実装: {@code buildcraft.builders.FillerPatternCore} (BCx 版)。
 * <p>
 * {@link #iterate} は 1 回の作業で 1 か所だけ処理して false を返し、範囲を最後まで処理し終えたら true を返す。
 * 作業位置は {@link FillerCursor} に持ち、旧実装の while 文の書き方 (途中で return して次の呼び出しで続きから) をそのまま移した。
 * 旧 1.2.5 のブロック ID による判定は、次のように 1.21.1 のブロックステートに置き換えた。
 * <ul>
 *   <li>softBlock (空気・草・雪・水・オイル): 空気、または置き換え可能で溶岩でないブロック</li>
 *   <li>unbreakableBlock (岩盤・溶岩・エンドポータル): 破壊不能 (硬さ &lt; 0)、溶岩、エンドポータル (枠)</li>
 *   <li>isFallBlock (BlockSand): {@link FallingBlock}</li>
 *   <li>isLiquidBlock (水・溶岩の素材): {@link LiquidBlock}</li>
 * </ul>
 */
public abstract class FillerPattern {
    /** 旧 FillerPatternCore.initialize の setPower(25, 4)。 */
    private static final int DEFAULT_WORK = 25;
    private static final int DEFAULT_MAX = 4;
    /** 旧 breakBlock の age 引数: 5980 で壊したドロップはすぐ消える (旧 1.4.7 型撤去)。 */
    protected static final int SHORT_LIVED_AGE = 5980;
    private static final float DROP_RANGE = 0.9F;
    private static final int DROP_PICKUP_DELAY = 10;
    private static final float ERASE_VOLUME = 0.5F;
    private static final float ERASE_PITCH = 3.0F;
    private static final float BREAK_PITCH = 0.8F;

    /** 作業範囲と動力の初期設定。 */
    public void initialize(FillerBlockEntity filler) {
        filler.initTargetPosition();
        filler.setPower(DEFAULT_WORK, DEFAULT_MAX);
    }

    /**
     * 1 回分の作業。
     *
     * @param stack 材料 (無ければ {@link ItemStack#EMPTY})。使った分はこのスタックから減らす
     * @return 範囲をすべて処理し終えた場合 true
     */
    public abstract boolean iterate(FillerBlockEntity filler, ItemStack stack);

    // ---- 判定 -----------------------------------------------------------------

    protected static BlockState state(FillerBlockEntity filler, int x, int y, int z) {
        return filler.serverLevel().getBlockState(new BlockPos(x, y, z));
    }

    /** 旧 BuildCraftAPI.softBlock ({@link SoftBlocks})。 */
    protected static boolean isSoft(BlockState state) {
        return SoftBlocks.isSoft(state);
    }

    /** 旧 BCAPI.unbreakableBlock。 */
    protected static boolean isUnbreakable(FillerBlockEntity filler, BlockPos pos, BlockState state) {
        return state.getDestroySpeed(filler.serverLevel(), pos) < 0.0F || state.getFluidState().is(FluidTags.LAVA)
                || state.is(Blocks.END_PORTAL) || state.is(Blocks.END_PORTAL_FRAME);
    }

    /** 旧 isFilling: 柔らかいブロック、設定 {@code fillingLava} が有効なら溶岩も。 */
    protected static boolean isFilling(FillerBlockEntity filler, int x, int y, int z) {
        BlockState state = state(filler, x, y, z);
        return isSoft(state) || Config.FILLING_LAVA.get() && state.getFluidState().is(FluidTags.LAVA);
    }

    /** 旧 isErase: 空気・フィラー自身・(設定で許可しない限り) エンドポータル以外。 */
    protected static boolean isErase(FillerBlockEntity filler) {
        FillerCursor c = filler.cursor();
        BlockState state = state(filler, c.cx, c.cy, c.cz);
        return !state.isAir() && !state.is(filler.getBlockState().getBlock())
                && (Config.FILLER_ERASE_END_PORTAL.get() || !state.is(Blocks.END_PORTAL_FRAME) && !state.is(Blocks.END_PORTAL));
    }

    /** 旧 cy <= 255 && cy > 0 (ワールドの最下層は扱わない)。 */
    protected static boolean inHeight(FillerBlockEntity filler, int y) {
        return y > filler.serverLevel().getMinBuildHeight() && y < filler.serverLevel().getMaxBuildHeight();
    }

    /** 旧 isRange: 移動方向に応じて範囲内か。 */
    protected static boolean isRange(int start, int end, int current, int vector) {
        if (vector > 0) {
            return current >= start && current <= end;
        }
        if (vector < 0) {
            return current >= end && current <= start;
        }
        return false;
    }

    // ---- 共通の処理 -------------------------------------------------------------

    /** 旧 fill: 範囲全体を下の層から埋める。 */
    protected boolean fill(FillerBlockEntity filler, ItemStack stack) {
        FillerCursor c = filler.cursor();
        return fill(filler, c.sx, c.sy, c.sz, c.ex, c.ey, c.ez, stack);
    }

    protected boolean fill(FillerBlockEntity filler, int sx, int sy, int sz, int ex, int ey, int ez, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        FillerCursor c = filler.cursor();
        while (c.cy >= sy && c.cy <= ey && inHeight(filler, c.cy)) {
            while (c.cz >= sz && c.cz <= ez) {
                while (c.cx >= sx && c.cx <= ex) {
                    if (shouldFill(filler, c.cx, c.cy, c.cz, stack)) {
                        setBlock(filler, c.cx, c.cy, c.cz, stack);
                        c.cx++;
                        return false;
                    }
                    c.cx++;
                }
                c.cx = sx;
                c.cz++;
            }
            c.cz = sz;
            c.cy++;
        }
        c.cy = sy;
        return true;
    }

    /** {@link #fill} で置く場所か。既定は {@link #isFilling}。 */
    protected boolean shouldFill(FillerBlockEntity filler, int x, int y, int z, ItemStack stack) {
        return isFilling(filler, x, y, z);
    }

    /** 旧 fill2: 範囲の 1 つ下の層の穴を、下から順に埋める。 */
    protected boolean fill2(FillerBlockEntity filler, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        FillerCursor c = filler.cursor();
        int y = c.sy - 1;
        while (c.cz >= c.sz && c.cz <= c.ez) {
            while (c.cx >= c.sx && c.cx <= c.ex) {
                if (isFilling(filler, c.cx, y, c.cz)) {
                    while (y > filler.serverLevel().getMinBuildHeight() && isFilling(filler, c.cx, y - 1, c.cz)) {
                        y--;
                    }
                    setBlock(filler, c.cx, y, c.cz, stack);
                    return false;
                }
                c.cx++;
            }
            c.cx = c.sx;
            c.cz++;
        }
        c.cz = c.sz;
        return true;
    }

    /** 旧 emptyUD: 上の層から壊す (ドロップはすぐ消える)。 */
    protected boolean emptyUD(FillerBlockEntity filler) {
        FillerCursor c = filler.cursor();
        while (c.cy >= c.sy && c.cy <= c.ey && inHeight(filler, c.cy)) {
            while (c.cz >= c.sz && c.cz <= c.ez) {
                while (c.cx >= c.sx && c.cx <= c.ex) {
                    BlockPos pos = new BlockPos(c.cx, c.cy, c.cz);
                    BlockState state = filler.serverLevel().getBlockState(pos);
                    if (!isSoft(state) && !isUnbreakable(filler, pos, state)) {
                        breakBlock(filler, pos, SHORT_LIVED_AGE);
                        return false;
                    }
                    c.cx++;
                }
                c.cx = c.sx;
                c.cz++;
            }
            c.cz = c.sz;
            c.cy--;
        }
        c.cy = c.ey;
        return true;
    }

    /**
     * 旧 emptyDU: 下の層から壊す。最下層では上のブロックを先に壊し、上に砂・砂利が積もっていれば一番上から壊す。
     * 水・溶岩は、材料に砂があれば 1 個使って消す。
     */
    protected boolean emptyDU(FillerBlockEntity filler, ItemStack stack) {
        FillerCursor c = filler.cursor();
        while (c.cy >= c.sy && c.cy <= c.ey && inHeight(filler, c.cy)) {
            while (isRange(c.sz, c.ez, c.cz, c.mz)) {
                while (isRange(c.sx, c.ex, c.cx, c.mx)) {
                    BlockPos pos = new BlockPos(c.cx, c.cy, c.cz);
                    BlockState state = filler.serverLevel().getBlockState(pos);
                    if (!isSoft(state) && !isUnbreakable(filler, pos, state)) {
                        int y = c.cy;
                        BlockPos abovePos = pos.above();
                        BlockState above = filler.serverLevel().getBlockState(abovePos);
                        if (c.ey > c.sy && y == c.sy && !isSoft(above) && !isUnbreakable(filler, abovePos, above)) {
                            y++;
                        }
                        while (state(filler, c.cx, y + 1, c.cz).getBlock() instanceof FallingBlock) {
                            y++;
                        }
                        breakBlock(filler, new BlockPos(c.cx, y, c.cz), 0);
                        return false;
                    } else if (state.getBlock() instanceof LiquidBlock && stack.is(Items.SAND)) {
                        if (filler.serverLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL)) {
                            stack.shrink(1);
                            c.acted = true;
                        }
                        c.cx += c.mx;
                        return false;
                    }
                    c.cx += c.mx;
                }
                c.cx = c.sx;
                c.cz += c.mz;
            }
            c.cz = c.sz;
            c.cy++;
        }
        c.cy = c.sy;
        return true;
    }

    /** 旧 erase: 上の層からドロップ無しで消す。 */
    protected boolean erase(FillerBlockEntity filler) {
        FillerCursor c = filler.cursor();
        while (c.cy >= c.sy && c.cy <= c.ey && inHeight(filler, c.cy)) {
            while (c.cz >= c.sz && c.cz <= c.ez) {
                while (c.cx >= c.sx && c.cx <= c.ex) {
                    if (isErase(filler)) {
                        eraseBlock(filler, new BlockPos(c.cx, c.cy, c.cz));
                        c.cx++;
                        return false;
                    }
                    c.cx++;
                }
                c.cx = c.sx;
                c.cz++;
            }
            c.cz = c.sz;
            c.cy--;
        }
        c.cy = c.ey;
        return true;
    }

    /** 旧 eraseBlock: ドロップ無しで消し、火が消える音を出す。 */
    protected static void eraseBlock(FillerBlockEntity filler, BlockPos pos) {
        ServerLevel level = filler.serverLevel();
        if (level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL)) {
            filler.cursor().acted = true;
        }
        level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, ERASE_VOLUME, ERASE_PITCH);
    }

    /** 旧 breakBlock: ドロップをその場に落として壊す。{@code age} が大きいほどドロップは早く消える。 */
    protected static void breakBlock(FillerBlockEntity filler, BlockPos pos, int age) {
        ServerLevel level = filler.serverLevel();
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return;
        }
        // ドロップは撤去に成功した場合だけ出す (撤去できないブロックからアイテムが増えないように)。
        List<ItemStack> drops = Block.getDrops(state, level, pos, level.getBlockEntity(pos));
        if (!level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL)) {
            return;
        }
        filler.cursor().acted = true;
        RandomSource random = level.random;
        for (ItemStack drop : drops) {
            double offset = (1.0F - DROP_RANGE) * 0.5;
            ItemEntity item = new ItemEntity(level, pos.getX() + random.nextFloat() * DROP_RANGE + offset,
                    pos.getY() + random.nextFloat() * DROP_RANGE + offset, pos.getZ() + random.nextFloat() * DROP_RANGE + offset, drop);
            item.setPickUpDelay(DROP_PICKUP_DELAY);
            item.lifespan = Math.max(1, item.lifespan - age);
            level.addFreshEntity(item);
        }
        SoundType sound = state.getSoundType(level, pos, null);
        level.playSound(null, pos, sound.getBreakSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F,
                sound.getPitch() * BREAK_PITCH);
    }

    /**
     * 旧 setBlock: 場所を空気にしてから、偽プレイヤーに材料を「使わせて」置く。置けなかった場合は、
     * その場所にいる生き物を取り除いてもう一度試す (旧実装と同じ。ただしプレイヤーは対象外)。
     */
    protected void setBlock(FillerBlockEntity filler, int x, int y, int z, ItemStack stack) {
        BlockPos pos = new BlockPos(x, y, z);
        filler.serverLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        if (!place(filler, pos, Direction.UP, stack).consumesAction()) {
            for (LivingEntity entity : filler.serverLevel().getEntitiesOfClass(LivingEntity.class, new AABB(pos),
                    entity -> !(entity instanceof Player))) {
                entity.discard();
            }
            place(filler, pos, Direction.UP, stack);
        }
    }

    /** {@code pos} に材料を使う ({@link FakePlayerPlacement#useAt})。使えた場合は作業したことを記録する。 */
    protected static InteractionResult place(FillerBlockEntity filler, BlockPos pos, Direction face, ItemStack stack) {
        InteractionResult result = FakePlayerPlacement.useAt(filler.serverLevel(), pos, face, stack);
        if (result.consumesAction()) {
            filler.cursor().acted = true;
        }
        return result;
    }
}
