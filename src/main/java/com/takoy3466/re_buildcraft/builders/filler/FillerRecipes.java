package com.takoy3466.re_buildcraft.builders.filler;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * 作業台枠の並びからパターンを決める。元実装: {@code buildcraft.core.FillerRecipe}。
 * <p>
 * 旧実装と同じく、パターンの登録順に「図案 (左右反転も可)」→「そのパターンのモジュール 1 個」の順で照合し、最初に一致したものを使う。
 * 図案の記号: {@code b} = レンガブロック、{@code g} = ガラス、{@code w} = 水入りバケツ。
 */
public final class FillerRecipes {
    public static final int GRID_WIDTH = 3;
    public static final int GRID_SIZE = GRID_WIDTH * GRID_WIDTH;
    private static final Map<Character, Item> SYMBOLS = Map.of('b', Items.BRICKS, 'g', Items.GLASS, 'w', Items.WATER_BUCKET);

    private FillerRecipes() {
    }

    /**
     * @param grid       作業台枠 (先頭 9 スロット)
     * @param moduleItem パターンに対応するモジュールのアイテム
     */
    public static Optional<FillerPatterns.Entry> match(IItemHandler grid, Function<FillerPatterns.Entry, Item> moduleItem) {
        for (FillerPatterns.Entry entry : FillerPatterns.ALL) {
            if (matchesShape(grid, entry.recipe()) || matchesSingle(grid, moduleItem.apply(entry))) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    private static boolean matchesShape(IItemHandler grid, List<String> rows) {
        int height = rows.size();
        int width = rows.get(0).length();
        Item[] input = new Item[width * height];
        for (int row = 0; row < height; row++) {
            for (int column = 0; column < width; column++) {
                input[column + row * width] = SYMBOLS.get(rows.get(row).charAt(column));
            }
        }
        return matchesAnywhere(grid, input, width, height);
    }

    private static boolean matchesSingle(IItemHandler grid, Item module) {
        return matchesAnywhere(grid, new Item[]{module}, 1, 1);
    }

    /** 旧 matches: 3x3 の中のどこに置いてもよく、左右反転も可。 */
    private static boolean matchesAnywhere(IItemHandler grid, Item[] input, int width, int height) {
        for (int left = 0; left <= GRID_WIDTH - width; left++) {
            for (int top = 0; top <= GRID_WIDTH - height; top++) {
                if (matchesAt(grid, input, width, height, left, top, true) || matchesAt(grid, input, width, height, left, top, false)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean matchesAt(IItemHandler grid, Item[] input, int width, int height, int left, int top, boolean mirrored) {
        for (int x = 0; x < GRID_WIDTH; x++) {
            for (int y = 0; y < GRID_WIDTH; y++) {
                int ix = x - left;
                int iy = y - top;
                Item expected = null;
                if (ix >= 0 && iy >= 0 && ix < width && iy < height) {
                    expected = mirrored ? input[width - ix - 1 + iy * width] : input[ix + iy * width];
                }
                ItemStack actual = grid.getStackInSlot(x + y * GRID_WIDTH);
                if (expected == null ? !actual.isEmpty() : !actual.is(expected)) {
                    return false;
                }
            }
        }
        return true;
    }
}
