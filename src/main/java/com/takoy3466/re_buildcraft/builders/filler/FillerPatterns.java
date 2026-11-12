package com.takoy3466.re_buildcraft.builders.filler;

import com.takoy3466.re_buildcraft.Config;
import com.takoy3466.re_buildcraft.builders.FillerBlockEntity;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * BCx に登録されていたフィラーパターン 23 種 (旧 BuildCraftBuilders.registerFiller の呼び出し順)。
 * 各パターンの番号はモジュールの旧ダメージ値、図案は 3x3 の作業台枠に並べる材料
 * ({@code b} = レンガブロック、{@code g} = ガラス、{@code w} = 水入りバケツ、空白 = 何も置かない)。
 */
public final class FillerPatterns {
    /**
     * @param id      旧モジュールのダメージ値 (0 - 22)
     * @param name    モジュールの登録名の末尾
     * @param recipe  作業台枠の図案 (左右反転も可)
     * @param factory パターンの生成
     */
    public record Entry(int id, String name, List<String> recipe, Supplier<FillerPattern> factory) {
    }

    public static final List<Entry> ALL = List.of(
            new Entry(0, "fill", List.of("bbb", "bbb", "bbb"), FillAll::new),
            new Entry(1, "remove", List.of("ggg", "ggg", "ggg"), Remover::new),
            new Entry(2, "flatten", List.of("   ", "ggg", "bbb"), Flattener::new),
            new Entry(3, "box", List.of("bbb", "b b", "bbb"), FillWalls::new),
            new Entry(4, "pyramid", List.of("   ", " b ", "bbb"), Pyramid::new),
            new Entry(5, "stairs", List.of("  b", " bb", "bbb"), Stairs::new),
            new Entry(6, "erase", List.of("ggg", "g g", "ggg"), Eraser::new),
            new Entry(7, "walls", List.of("b b", "b b", "b b"), SideWalls::new),
            new Entry(8, "remove_old", List.of("   ", "ggg", "ggg"), RemoverOld::new),
            new Entry(9, "hole_fill", List.of("   ", "   ", "bbb"), HoleFill::new),
            new Entry(10, "pond", List.of("   ", "www", "www"), Pond::new),
            new Entry(11, "flooring", List.of("   ", "bbb", "ggg"), Flooring::new),
            new Entry(12, "tower", List.of("bb", "bb", "bb"), Tower::new),
            new Entry(13, "ceiling", List.of("ggg", "bbb", "   "), Ceiling::new),
            new Entry(14, "wall_change", List.of("gbg", "gbg", "gbg"), WallChange::new),
            new Entry(15, "outer_walls", List.of("b b", "   ", "b b"), OuterWalls::new),
            new Entry(16, "circle_wall", List.of(" b ", "b b", " b "), CircleWall::new),
            new Entry(17, "circle_erase", List.of(" g ", "ggg", " g "), CircleErase::new),
            new Entry(18, "water", List.of("www", "www", "www"), Water::new),
            new Entry(19, "underground_fill", List.of("   ", "bbb", "bbb"), UnderFill::new),
            new Entry(20, "hoe", List.of("bb ", " b ", " b "), Hoe::new),
            new Entry(21, "set_stairs", List.of("  b", " bb", "bb "), SetStairs::new),
            new Entry(22, "torch", List.of("b b", " b ", "b b"), Torch::new));

    private FillerPatterns() {
    }

    // ---- 埋める -------------------------------------------------------------

    /** 埋め立て (旧 FillerFillAll)。 */
    static class FillAll extends FillerPattern {
        @Override
        public void initialize(FillerBlockEntity filler) {
            super.initialize(filler);
            filler.setPower(16, 512);
        }

        @Override
        public boolean iterate(FillerBlockEntity filler, ItemStack stack) {
            return fill(filler, stack);
        }
    }

    /** 地下埋め (旧 FillerUnderFill): 範囲の下からワールドの最下層の 1 つ上まで。 */
    static class UnderFill extends FillAll {
        @Override
        public void initialize(FillerBlockEntity filler) {
            super.initialize(filler);
            filler.setPower(16, 256);
            FillerCursor c = filler.cursor();
            c.ey = c.sy - 1;
            c.sy = filler.serverLevel().getMinBuildHeight() + 1;
            c.cy = c.sy;
        }
    }

    /** 穴埋め (旧 FillerHoleFill)。 */
    static class HoleFill extends FillerPattern {
        @Override
        public void initialize(FillerBlockEntity filler) {
            super.initialize(filler);
            filler.setPower(24, 64);
        }

        @Override
        public boolean iterate(FillerBlockEntity filler, ItemStack stack) {
            return fill2(filler, stack);
        }
    }

    /** タワー (旧 FillerTower): 最下層にブロックがある列だけを積み上げる。 */
    static class Tower extends FillerPattern {
        @Override
        public void initialize(FillerBlockEntity filler) {
            super.initialize(filler);
            filler.setPower(32, 8);
        }

        @Override
        public boolean iterate(FillerBlockEntity filler, ItemStack stack) {
            return fill(filler, stack);
        }

        @Override
        protected boolean shouldFill(FillerBlockEntity filler, int x, int y, int z, ItemStack stack) {
            return !state(filler, x, filler.cursor().sy, z).isAir() && isFilling(filler, x, y, z);
        }
    }

    /** ピラミッド (旧 FillerFillPyramid): 1 層ごとに外周を 1 つ縮める。フィラーが範囲より上にあれば逆さに作る。 */
    static class Pyramid extends FillerPattern {
        @Override
        public void initialize(FillerBlockEntity filler) {
            super.initialize(filler);
            filler.setPower(16, 256);
            reverseIfAbove(filler);
        }

        @Override
        public boolean iterate(FillerBlockEntity filler, ItemStack stack) {
            if (stack.isEmpty()) {
                return false;
            }
            FillerCursor c = filler.cursor();
            while (isRange(c.sy, c.ey, c.cy, c.my) && inHeight(filler, c.cy) && !(c.sx > c.ex || c.sz > c.ez)) {
                while (c.cz >= c.sz && c.cz <= c.ez) {
                    while (c.cx >= c.sx && c.cx <= c.ex) {
                        if (isSoft(state(filler, c.cx, c.cy, c.cz))) {
                            setBlock(filler, c.cx, c.cy, c.cz, stack);
                            c.cx++;
                            return false;
                        }
                        c.cx++;
                    }
                    c.cx = c.sx;
                    c.cz++;
                }
                c.cz = c.sz;
                c.sx++;
                c.ex--;
                c.cx = c.sx;
                c.sz++;
                c.ez--;
                c.cz = c.sz;
                c.cy += c.my;
            }
            c.cy = c.sy;
            return true;
        }
    }

    /** 範囲の上端以上にフィラーがあれば、上から下へ進むように入れ替える (旧 Pyramid / Stairs の initialize)。 */
    static void reverseIfAbove(FillerBlockEntity filler) {
        FillerCursor c = filler.cursor();
        c.my = 1;
        if (filler.getBlockPos().getY() >= c.ey) {
            c.cy = c.ey;
            c.ey = c.sy;
            c.sy = c.cy;
            c.my = -1;
        }
    }

    /** 階段 (旧 FillerFillStairs): フィラーのある側から 1 段ずつ奥へずらして積む。 */
    static class Stairs extends FillerPattern {
        @Override
        public void initialize(FillerBlockEntity filler) {
            filler.initRotatePosition();
            filler.setPower(16, 256);
            reverseIfAbove(filler);
        }

        @Override
        public boolean iterate(FillerBlockEntity filler, ItemStack stack) {
            if (stack.isEmpty()) {
                return false;
            }
            FillerCursor c = filler.cursor();
            while (isRange(c.sy, c.ey, c.cy, c.my) && inHeight(filler, c.cy)) {
                while (isRange(c.sz, c.ez, c.cz, c.mz)) {
                    while (isRange(c.sx, c.ex, c.cx, c.mx)) {
                        if (isSoft(state(filler, c.cx, c.cy, c.cz))) {
                            setBlock(filler, c.cx, c.cy, c.cz, stack);
                            c.cx += c.mx;
                            return false;
                        }
                        c.cx += c.mx;
                    }
                    c.cx = c.sx;
                    c.cz += c.mz;
                }
                c.cz = c.sz;
                stepInward(filler);
                c.cy += c.my;
            }
            c.cy = c.sy;
            return true;
        }

        /** 1 層ごとに、フィラーの面している辺の開始位置を 1 つ進める。 */
        protected void stepInward(FillerBlockEntity filler) {
            FillerCursor c = filler.cursor();
            if (filler.getBlockPos().getZ() == c.sz) {
                c.sx += c.mx;
                c.cx = c.sx;
            }
            if (filler.getBlockPos().getX() == c.sx) {
                c.sz += c.mz;
                c.cz = c.sz;
            }
        }
    }

    /** 箱作成 (旧 FillerFillWalls): 床 → 西 → 北 → 東 → 南 → 天井の順。 */
    static class FillWalls extends FillerPattern {
        @Override
        public void initialize(FillerBlockEntity filler) {
            super.initialize(filler);
            filler.setPower(16, 512);
        }

        @Override
        public boolean iterate(FillerBlockEntity filler, ItemStack stack) {
            FillerCursor c = filler.cursor();
            if (c.mode == 0 && fill(filler, c.sx, c.sy, c.sz, c.ex, c.sy, c.ez, stack)) {
                modeChange(c, c.sx, c.sy, c.sz);
            }
            if (c.mode == 1 && fill(filler, c.sx, c.sy, c.sz, c.sx, c.ey, c.ez, stack)) {
                modeChange(c, c.sx, c.sy, c.sz);
            }
            if (c.mode == 2 && fill(filler, c.sx, c.sy, c.sz, c.ex, c.ey, c.sz, stack)) {
                modeChange(c, c.ex, c.sy, c.sz);
            }
            if (c.mode == 3 && fill(filler, c.ex, c.sy, c.sz, c.ex, c.ey, c.ez, stack)) {
                modeChange(c, c.sx, c.sy, c.ez);
            }
            if (c.mode == 4 && fill(filler, c.sx, c.sy, c.ez, c.ex, c.ey, c.ez, stack)) {
                modeChange(c, c.sx, c.ey, c.sz);
            }
            return c.mode == 5 && fill(filler, c.sx, c.ey, c.sz, c.ex, c.ey, c.ez, stack);
        }

        protected static void modeChange(FillerCursor c, int x, int y, int z) {
            c.cx = x;
            c.cy = y;
            c.cz = z;
            c.mode++;
        }
    }

    /** 壁作成 (旧 FillerFillSideWalls): 箱作成のうち 4 面の壁だけ。 */
    static class SideWalls extends FillWalls {
        @Override
        public void initialize(FillerBlockEntity filler) {
            super.initialize(filler);
            filler.cursor().mode = 1;
        }

        @Override
        public boolean iterate(FillerBlockEntity filler, ItemStack stack) {
            FillerCursor c = filler.cursor();
            if (c.mode == 1 && fill(filler, c.sx, c.sy, c.sz, c.sx, c.ey, c.ez, stack)) {
                modeChange(c, c.sx, c.sy, c.sz);
            }
            if (c.mode == 2 && fill(filler, c.sx, c.sy, c.sz, c.ex, c.ey, c.sz, stack)) {
                modeChange(c, c.ex, c.sy, c.sz);
            }
            if (c.mode == 3 && fill(filler, c.ex, c.sy, c.sz, c.ex, c.ey, c.ez, stack)) {
                modeChange(c, c.sx, c.sy, c.ez);
            }
            return c.mode == 4 && fill(filler, c.sx, c.sy, c.ez, c.ex, c.ey, c.ez, stack);
        }
    }

    /** 外壁作成 (旧 FillerOuterWalls): 範囲を 1 回り広げて箱を作る。 */
    static class OuterWalls extends FillWalls {
        @Override
        public void initialize(FillerBlockEntity filler) {
            super.initialize(filler);
            FillerCursor c = filler.cursor();
            c.sx--;
            c.sy--;
            c.sz--;
            c.ex++;
            c.ey++;
            c.ez++;
            c.cx = c.sx;
            c.cy = c.sy;
            c.cz = c.sz;
        }
    }

    // ---- 張り替える -----------------------------------------------------------

    /** 空気・フィラー・材料と同じブロック以外か (旧 isChange)。 */
    static boolean isChange(FillerBlockEntity filler, int x, int y, int z, ItemStack stack) {
        BlockState state = filler.serverLevel().getBlockState(new BlockPos(x, y, z));
        return !state.isAir() && !state.is(filler.getBlockState().getBlock())
                && !(stack.getItem() instanceof BlockItem blockItem && state.is(blockItem.getBlock()));
    }

    /** 床張替え (旧 FillerFlooring): 範囲の 1 つ下の層を材料のブロックに張り替える。 */
    static class Flooring extends FillerPattern {
        @Override
        public void initialize(FillerBlockEntity filler) {
            super.initialize(filler);
            filler.cursor().cy--;
            filler.setPower(32, 128);
        }

        @Override
        public boolean iterate(FillerBlockEntity filler, ItemStack stack) {
            if (stack.isEmpty()) {
                return false;
            }
            FillerCursor c = filler.cursor();
            while (c.cz >= c.sz && c.cz <= c.ez) {
                while (c.cx >= c.sx && c.cx <= c.ex) {
                    if (isChange(filler, c.cx, c.cy, c.cz, stack)) {
                        setBlock(filler, c.cx, c.cy, c.cz, stack);
                        c.cx++;
                        return false;
                    }
                    c.cx++;
                }
                c.cx = c.sx;
                c.cz++;
            }
            return true;
        }
    }

    /** 天井張替え (旧 FillerCeiling): 範囲の 1 つ上の層。 */
    static class Ceiling extends Flooring {
        @Override
        public void initialize(FillerBlockEntity filler) {
            super.initialize(filler);
            filler.cursor().cy = filler.cursor().ey + 1;
        }
    }

    /** 壁張替え (旧 FillerWallChange): 範囲の外側の 4 面 (西・東・北・南) を張り替える。 */
    static class WallChange extends FillWalls {
        @Override
        public void initialize(FillerBlockEntity filler) {
            super.initialize(filler);
            FillerCursor c = filler.cursor();
            modeChange(c, c.sx - 1, c.sy, c.sz);
            filler.setPower(32, 128);
        }

        @Override
        public boolean iterate(FillerBlockEntity filler, ItemStack stack) {
            FillerCursor c = filler.cursor();
            if (c.mode <= 2) {
                if (c.sz == c.ez) {
                    c.mode++;
                    modeChange(c, c.sx, c.sy, c.sz - 1);
                }
                if (c.mode == 1 && fill(filler, c.sx - 1, c.sy, c.sz, c.sx - 1, c.ey, c.ez, stack)) {
                    modeChange(c, c.ex + 1, c.sy, c.sz);
                }
                if (c.mode == 2 && fill(filler, c.ex + 1, c.sy, c.sz, c.ex + 1, c.ey, c.ez, stack)) {
                    modeChange(c, c.sx, c.sy, c.sz - 1);
                }
            }
            if (c.mode >= 3) {
                if (c.sx == c.ex) {
                    return true;
                }
                if (c.mode == 3 && fill(filler, c.sx, c.sy, c.sz - 1, c.ex, c.ey, c.sz - 1, stack)) {
                    modeChange(c, c.sx, c.sy, c.ez + 1);
                }
                return c.mode == 4 && fill(filler, c.sx, c.sy, c.ez + 1, c.ex, c.ey, c.ez + 1, stack);
            }
            return false;
        }

        @Override
        protected boolean shouldFill(FillerBlockEntity filler, int x, int y, int z, ItemStack stack) {
            return isChange(filler, x, y, z, stack);
        }
    }

    // ---- 壊す・消す -------------------------------------------------------------

    /** 撤去 (旧 FillerRemover): フィラー側から下の層を壊し、ドロップを残す。 */
    static class Remover extends FillerPattern {
        @Override
        public void initialize(FillerBlockEntity filler) {
            filler.initRotatePosition();
            filler.setPower(32, 2);
        }

        @Override
        public boolean iterate(FillerBlockEntity filler, ItemStack stack) {
            return emptyDU(filler, stack);
        }
    }

    /** 1.4.7 型撤去 (旧 FillerRemover2): 上の層から壊す。ドロップはすぐ消える。 */
    static class RemoverOld extends FillerPattern {
        @Override
        public void initialize(FillerBlockEntity filler) {
            super.initialize(filler);
            filler.cursor().cy = filler.cursor().ey;
            filler.setPower(16, 8);
        }

        @Override
        public boolean iterate(FillerBlockEntity filler, ItemStack stack) {
            return emptyUD(filler);
        }
    }

    /** 完全消去 (旧 FillerEraser): 上の層からドロップ無しで消す。 */
    static class Eraser extends FillerPattern {
        @Override
        public void initialize(FillerBlockEntity filler) {
            super.initialize(filler);
            filler.cursor().cy = filler.cursor().ey;
            filler.setPower(24, 256);
        }

        @Override
        public boolean iterate(FillerBlockEntity filler, ItemStack stack) {
            return erase(filler);
        }
    }

    /** 整地 (旧 FillerFlattener): 範囲の上をワールドの天井まで消してから、下の穴を埋める。 */
    static class Flattener extends FillerPattern {
        @Override
        public void initialize(FillerBlockEntity filler) {
            super.initialize(filler);
            FillerCursor c = filler.cursor();
            c.ey = filler.serverLevel().getMaxBuildHeight() - 1;
            c.cy = c.ey;
            filler.setPower(24, 64);
        }

        @Override
        public boolean iterate(FillerBlockEntity filler, ItemStack stack) {
            FillerCursor c = filler.cursor();
            if (c.mode == 0 && erase(filler)) {
                c.mode++;
            }
            return c.mode == 1 && fill2(filler, stack);
        }
    }

    // ---- 円 -----------------------------------------------------------------

    /** 旧 initCircle の半径の 2 乗の 2 倍 (半径 4 のときだけ 4.5 で計算)。 */
    private static final int SPECIAL_RADIUS = 4;
    private static final double SPECIAL_RADIUS_OFFSET = 0.5;

    static int doubledRadiusSquared(int radius) {
        return radius == SPECIAL_RADIUS
                ? (int) Math.pow(radius + SPECIAL_RADIUS_OFFSET, 2) << 1
                : (radius * radius) << 1;
    }

    /** 円壁作成 (旧 FillerCircleWall): 範囲の短い辺を直径とする円周を、各層に 4 象限ずつ置く。 */
    static class CircleWall extends FillerPattern {
        @Override
        public void initialize(FillerBlockEntity filler) {
            super.initialize(filler);
            filler.setPower(32, 8);
            FillerCursor c = filler.cursor();
            int diameter = Math.min(c.ex - c.sx + 1, c.ez - c.sz + 1);
            c.diameter = diameter;
            c.radius = (diameter - 1) >> 1;
            c.radius2 = doubledRadiusSquared(c.radius);
            c.mx = (diameter >> 1) + c.sx;
            c.mz = (diameter >> 1) + c.sz;
            c.nx = c.mx;
            c.nz = c.mz;
            if ((diameter & 1) == 0) {
                c.nx--;
                c.nz--;
            }
            c.mode = 0;
            initPosition(c);
        }

        private static void initPosition(FillerCursor c) {
            c.cx = 0;
            c.cz = c.radius;
            c.ex = 0;
        }

        @Override
        public boolean iterate(FillerBlockEntity filler, ItemStack stack) {
            if (stack.isEmpty()) {
                return false;
            }
            FillerCursor c = filler.cursor();
            while (c.cy >= c.sy && c.cy <= c.ey && inHeight(filler, c.cy)) {
                while (c.mode < 4) {
                    while (c.cz >= 0 && c.cx <= c.radius) {
                        boolean skipped = switch (c.mode) {
                            case 0 -> trySet(filler, c.cx + c.mx, -c.cz + c.nz, stack);
                            case 1 -> trySet(filler, c.cz + c.mx, c.cx + c.mz, stack);
                            case 2 -> trySet(filler, -c.cx + c.nx, c.cz + c.mz, stack);
                            default -> trySet(filler, -c.cz + c.nx, -c.cx + c.nz, stack);
                        };
                        if (c.cx < c.cz) {
                            int d = ((c.cx + 1) * (c.cx + 1) << 1) + c.cz * c.cz + (c.cz - 1) * (c.cz - 1);
                            if (d >= c.radius2) {
                                c.cz--;
                            }
                            c.cx++;
                        } else {
                            int d = ((c.cz - 1) * (c.cz - 1) << 1) + c.cx * c.cx + (c.cx + 1) * (c.cx + 1);
                            if (d < c.radius2) {
                                c.cx++;
                            }
                            c.cz--;
                        }
                        if (!skipped) {
                            return false;
                        }
                    }
                    initPosition(c);
                    c.mode++;
                }
                c.mode = 0;
                c.cy++;
            }
            c.cy = c.sy;
            return true;
        }

        /** @return 置かずに次へ進む場合 true (旧 trySet)。 */
        private boolean trySet(FillerBlockEntity filler, int x, int z, ItemStack stack) {
            if (isFilling(filler, x, filler.cursor().cy, z)) {
                setBlock(filler, x, filler.cursor().cy, z, stack);
                return false;
            }
            return true;
        }
    }

    /** 円柱消去 (旧 FillerCircleErase): 範囲の短い辺を直径とする円の内側を、上の層から消す。 */
    static class CircleErase extends FillerPattern {
        @Override
        public void initialize(FillerBlockEntity filler) {
            super.initialize(filler);
            filler.setPower(32, 32);
            FillerCursor c = filler.cursor();
            int sizeX = c.ex - c.sx + 1;
            int sizeZ = c.ez - c.sz + 1;
            boolean[][] pattern = new boolean[sizeX][sizeZ];
            int diameter = Math.min(sizeX, sizeZ);
            int radius = (diameter - 1) >> 1;
            int radius2 = doubledRadiusSquared(radius);
            c.radius = radius;
            int mxz = diameter >> 1;
            int nxz = (diameter & 1) == 0 ? mxz - 1 : mxz;
            int x = 0;
            int z = radius;
            while (z >= 0 && x <= radius) {
                for (int i = z; i >= 0; i--) {
                    pattern[mxz + x][nxz - i] = true;
                    pattern[mxz + x][mxz + i] = true;
                    pattern[nxz - x][mxz + i] = true;
                    pattern[nxz - x][nxz - i] = true;
                }
                if (x < z) {
                    int d = ((x + 1) * (x + 1) << 1) + z * z + (z - 1) * (z - 1);
                    if (d >= radius2) {
                        z--;
                    }
                    x++;
                } else {
                    int d = ((z - 1) * (z - 1) << 1) + x * x + (x + 1) * (x + 1);
                    if (d < radius2) {
                        x++;
                    }
                    z--;
                }
            }
            c.pattern = pattern;
            c.cy = c.ey;
        }

        @Override
        public boolean iterate(FillerBlockEntity filler, ItemStack stack) {
            FillerCursor c = filler.cursor();
            while (c.cy >= c.sy && c.cy <= c.ey && inHeight(filler, c.cy)) {
                while (c.cz >= c.sz && c.cz <= c.ez) {
                    while (c.cx >= c.sx && c.cx <= c.ex) {
                        if (c.pattern[c.cx - c.sx][c.cz - c.sz] && isErase(filler)) {
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
    }

    // ---- 液体 -----------------------------------------------------------------

    /** 池作成 (旧 FillerPond): 範囲の下の穴を、下から液体で満たす。 */
    static class Pond extends FillerPattern {
        /** 旧 useQuantity: 上のタンクから 1 回に使う量。 */
        private static final int USE_QUANTITY = FluidType.BUCKET_VOLUME;

        @Override
        public void initialize(FillerBlockEntity filler) {
            super.initialize(filler);
            filler.setPower(24, 32);
        }

        @Override
        public boolean iterate(FillerBlockEntity filler, ItemStack stack) {
            FillerCursor c = filler.cursor();
            int y = c.cy - 1;
            while (c.cz >= c.sz && c.cz <= c.ez) {
                while (c.cx >= c.sx && c.cx <= c.ex) {
                    if (isLiquidTarget(filler, c.cx, y, c.cz)) {
                        while (y > filler.serverLevel().getMinBuildHeight() && isLiquidTarget(filler, c.cx, y - 1, c.cz)) {
                            y--;
                        }
                        setLiquid(filler, new BlockPos(c.cx, y, c.cz), stack);
                        return false;
                    }
                    c.cx++;
                }
                c.cx = c.sx;
                c.cz++;
            }
            return true;
        }

        /** 旧 isFilling (池): 空気、水源以外の柔らかいブロック、流れている溶岩。 */
        protected static boolean isLiquidTarget(FillerBlockEntity filler, int x, int y, int z) {
            BlockState state = state(filler, x, y, z);
            FluidState fluid = state.getFluidState();
            return state.isAir() || isSoft(state) && !(fluid.is(FluidTags.WATER) && fluid.isSource())
                    || fluid.is(FluidTags.LAVA) && !fluid.isSource();
        }

        /**
         * 旧 setLiquidBlock: フィラーの上にあるタンクから 1 バケツ分を取れればその液体を、取れなければ材料の液体入り容器を使う。
         * 使った容器の空き容器はフィラーのインベントリへ戻し、入らなければ排出する。
         */
        protected static void setLiquid(FillerBlockEntity filler, BlockPos pos, ItemStack stack) {
            if (placeFromTank(filler, pos)) {
                return;
            }
            if (stack.isEmpty()) {
                return;
            }
            FluidStack contained = FluidUtil.getFluidContained(stack).orElse(FluidStack.EMPTY);
            BlockState block = contained.getAmount() >= USE_QUANTITY ? sourceBlock(contained) : null;
            if (block == null) {
                return;
            }
            ItemStack container = stack.getCraftingRemainingItem();
            filler.serverLevel().setBlock(pos, block, Block.UPDATE_ALL);
            stack.shrink(1);
            if (!container.isEmpty()) {
                filler.storeOrEject(container);
            }
        }

        private static boolean placeFromTank(FillerBlockEntity filler, BlockPos pos) {
            IFluidHandler tank = filler.serverLevel().getCapability(Capabilities.FluidHandler.BLOCK,
                    filler.getBlockPos().above(), Direction.DOWN);
            if (tank == null) {
                return false;
            }
            FluidStack available = tank.drain(USE_QUANTITY, IFluidHandler.FluidAction.SIMULATE);
            BlockState block = available.getAmount() == USE_QUANTITY ? sourceBlock(available) : null;
            if (block == null) {
                return false;
            }
            tank.drain(USE_QUANTITY, IFluidHandler.FluidAction.EXECUTE);
            filler.serverLevel().setBlock(pos, block, Block.UPDATE_ALL);
            return true;
        }

        /** 液体の水源ブロック。ワールドに置けない液体 (金オイルなど) は null。 */
        private static BlockState sourceBlock(FluidStack fluid) {
            BlockState block = fluid.getFluid().defaultFluidState().createLegacyBlock();
            return fluid.getFluid() == Fluids.EMPTY || block.isAir() ? null : block;
        }
    }

    /** 水設置 (旧 FillerWater): 範囲全体を液体で満たす。 */
    static class Water extends Pond {
        @Override
        public boolean iterate(FillerBlockEntity filler, ItemStack stack) {
            FillerCursor c = filler.cursor();
            while (c.cy >= c.sy && c.cy <= c.ey && inHeight(filler, c.cy)) {
                while (c.cz >= c.sz && c.cz <= c.ez) {
                    while (c.cx >= c.sx && c.cx <= c.ex) {
                        if (isLiquidTarget(filler, c.cx, c.cy, c.cz)) {
                            setLiquid(filler, new BlockPos(c.cx, c.cy, c.cz), stack);
                            return false;
                        }
                        c.cx++;
                    }
                    c.cx = c.sx;
                    c.cz++;
                }
                c.cz = c.sz;
                c.cy++;
            }
            c.cy = c.sy;
            return true;
        }
    }

    // ---- 道具・設置物 -------------------------------------------------------------

    /** クワ (旧 FillerHoe): 範囲の柔らかい場所の 1 つ下のブロックの上面に材料を使う (耕す・種をまくなど)。 */
    static class Hoe extends FillAll {
        @Override
        public void initialize(FillerBlockEntity filler) {
            super.initialize(filler);
            filler.setPower(24, 32);
        }

        @Override
        protected void setBlock(FillerBlockEntity filler, int x, int y, int z, ItemStack stack) {
            place(filler, new BlockPos(x, y - 1, z), Direction.UP, stack);
        }
    }

    /** 松明設置 (旧 FillerTorch): フィラーに近い角から設定 {@code torchInterval} ごとに、範囲の最下層へ置く。 */
    static class Torch extends FillerPattern {
        @Override
        public void initialize(FillerBlockEntity filler) {
            filler.initTargetPosition();
            FillerCursor c = filler.cursor();
            c.mx = c.sx <= filler.getBlockPos().getX() ? c.sx : c.ex;
            c.mz = c.sz <= filler.getBlockPos().getZ() ? c.sz : c.ez;
            filler.setPower(32, 16);
        }

        @Override
        public boolean iterate(FillerBlockEntity filler, ItemStack stack) {
            if (stack.isEmpty()) {
                return false;
            }
            FillerCursor c = filler.cursor();
            int interval = Config.FILLER_TORCH_INTERVAL.get();
            while (c.cz >= c.sz && c.cz <= c.ez) {
                while (c.cx >= c.sx && c.cx <= c.ex) {
                    if (isFilling(filler, c.cx, c.cy, c.cz) && Math.abs(c.cx - c.mx) % interval == 0
                            && Math.abs(c.cz - c.mz) % interval == 0) {
                        setBlock(filler, c.cx, c.cy, c.cz, stack);
                        c.cx++;
                        return false;
                    }
                    c.cx++;
                }
                c.cx = c.sx;
                c.cz++;
            }
            return true;
        }
    }

    /**
     * 階段設置 (旧 FillerSetStairs): 階段ブロックを、フィラーのある側を向けて 1 段ずつ置く。
     * フィラーが範囲の最上層にあれば逆さの階段、範囲より上にあればハーフブロックを上付きで置く。範囲より下なら何もしない。
     */
    static class SetStairs extends Stairs {
        private static final int NOTHING = -1;
        private static final int SLAB_MODE = 8;
        private static final int UPPER_FLAG = 4;
        private static final float QUARTER = 90.0F;

        @Override
        public void initialize(FillerBlockEntity filler) {
            filler.initRotatePosition();
            initPlayerRotation(filler);
            filler.setPower(32, 32);
            FillerCursor c = filler.cursor();
            c.my = 1;
            setFillArea(c);
            if (c.mode > 3) {
                c.cy = c.ey;
                c.ey = c.sy;
                c.sy = c.cy;
                c.my = -1;
            }
        }

        /** 旧 TileFiller.initFillerPlayerRotation: フィラーの位置から階段の向きと置き方を決める。 */
        private static void initPlayerRotation(FillerBlockEntity filler) {
            FillerCursor c = filler.cursor();
            BlockPos pos = filler.getBlockPos();
            BoundingBox box = filler.getArea();
            c.mode = 0;
            if (box.minY() != box.maxY() && (pos.getY() == box.minY() || pos.getY() == box.maxY())) {
                if (pos.getZ() < box.minZ()) {
                    c.playerYaw = 0.0F;
                }
                if (pos.getX() > box.maxX()) {
                    c.playerYaw = QUARTER;
                }
                if (pos.getZ() > box.maxZ()) {
                    c.playerYaw = 2 * QUARTER;
                }
                if (pos.getX() < box.minX()) {
                    c.playerYaw = 3 * QUARTER;
                }
                c.mode = Mth.floor(c.playerYaw / QUARTER + 2.5) & 3;
                if (pos.getY() == box.maxY()) {
                    c.mode += UPPER_FLAG;
                }
            }
            if (pos.getY() < box.minY()) {
                c.mode = NOTHING;
            }
            if (pos.getY() > box.maxY()) {
                c.mode = SLAB_MODE;
            }
        }

        /** 1 段に 1 列だけ置くよう、向きに応じて範囲を絞る。 */
        private static void setFillArea(FillerCursor c) {
            if (c.mode == SLAB_MODE) {
                return;
            }
            int yaw = c.mode & 3;
            if (yaw == 0 || yaw == 2) {
                c.ez = c.sz;
            }
            if (yaw == 1 || yaw == 3) {
                c.ex = c.sx;
            }
        }

        @Override
        public boolean iterate(FillerBlockEntity filler, ItemStack stack) {
            if (filler.cursor().mode == NOTHING) {
                return true;
            }
            return super.iterate(filler, stack);
        }

        @Override
        protected void stepInward(FillerBlockEntity filler) {
            FillerCursor c = filler.cursor();
            if (filler.getBlockPos().getZ() == c.sz) {
                c.sx += c.mx;
                c.cx = c.sx;
                setFillArea(c);
            }
            if (filler.getBlockPos().getX() == c.sx) {
                c.sz += c.mz;
                c.cz = c.sz;
                setFillArea(c);
            }
        }

        /** 旧 setBlock: 偽プレイヤーの向きで置き、逆さの階段・上付きのハーフブロックは下面に使ったものとして置く。 */
        @Override
        protected void setBlock(FillerBlockEntity filler, int x, int y, int z, ItemStack stack) {
            BlockPos pos = new BlockPos(x, y, z);
            filler.serverLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            FillerCursor c = filler.cursor();
            Block block = stack.getItem() instanceof BlockItem blockItem ? blockItem.getBlock() : null;
            boolean upper = block instanceof StairBlock && (c.mode & UPPER_FLAG) != 0
                    || block instanceof SlabBlock && c.mode == SLAB_MODE;
            filler.fakePlayer().setYRot(c.playerYaw);
            place(filler, pos, upper ? Direction.DOWN : Direction.UP, stack);
        }
    }
}
