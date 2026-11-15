package com.takoy3466.re_buildcraft.factory;

import com.takoy3466.re_buildcraft.api.util.InventoryUtils;
import com.takoy3466.re_buildcraft.registry.ModBlockEntities;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * 自動作業台。元実装: {@code buildcraft.factory.TileAutoWorkbench} (BCx 版)。
 * <p>
 * 3x3 のスロットそのものがレシピの見本兼材料置き場になる。
 * <ul>
 *   <li>パイプなどからの搬入は、同じアイテムが入っているスロットのうち最も少ないスロットにだけ入る (旧 addItem)。</li>
 *   <li>木のパイプなどで取り出すと 1 回分クラフトする。各スロットに 1 個しか無い材料は見本として残し、
 *       隣接するインベントリ (上・下・北・南・西・東の順) から 1 個ずつ取ってくる (旧 extractItem(doRemove, false))。</li>
 *   <li>GUI の完成品枠から取ると、見本の最後の 1 個も材料に使う (旧 SlotAutoCrafting → extractItem(true, true))。</li>
 *   <li>バケツなどのクラフト後に残るアイテムは、材料を取ったインベントリへ戻し、入らなければ隣接パイプへ流すか上へ放出する。</li>
 *   <li>材料はすべてそろうことを確かめてから取り出す。足りなければどのインベントリにも触れない ({@link #craft})。</li>
 * </ul>
 * 旧 BCAPI の「耐久値を消費するクラフト素材」は、1.21.1 ではレシピの残りアイテム ({@code getRemainingItems}) で表される。
 */
public class AutoWorkbenchBlockEntity extends BlockEntity implements MenuProvider {
    public static final int GRID_WIDTH = 3;
    public static final int GRID_SIZE = GRID_WIDTH * GRID_WIDTH;
    /** 外部向けハンドラでの完成品スロットの番号。 */
    public static final int OUTPUT_SLOT = GRID_SIZE;
    private static final String TAG_GRID = "grid";
    /** 旧 getNearbyItem の探索順 (YPos, YNeg, ZNeg, ZPos, XNeg, XPos)。 */
    private static final Direction[] NEARBY_ORDER = {
            Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

    private final ItemStackHandler grid = new ItemStackHandler(GRID_SIZE) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private final IItemHandler automationHandler = new AutomationHandler();

    public AutoWorkbenchBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.AUTO_WORKBENCH.get(), pos, state);
    }

    public ItemStackHandler getGrid() {
        return grid;
    }

    /** パイプ・ホッパーなどに公開するハンドラ (0..8 は搬入のみ、{@link #OUTPUT_SLOT} は取り出しのみ)。 */
    public IItemHandler getAutomationHandler() {
        return automationHandler;
    }

    // ---- レシピ ---------------------------------------------------------------

    /** 今のスロットの並びで作れる物 (旧 findRecipe)。各スロットを 1 個として照合する。 */
    public ItemStack previewResult() {
        if (level == null) {
            return ItemStack.EMPTY;
        }
        List<ItemStack> items = new ArrayList<>(GRID_SIZE);
        for (int slot = 0; slot < GRID_SIZE; slot++) {
            items.add(grid.getStackInSlot(slot).copyWithCount(1));
        }
        CraftingInput input = CraftingInput.of(GRID_WIDTH, GRID_WIDTH, items);
        return findRecipe(level, input).map(recipe -> recipe.value().assemble(input, level.registryAccess())).orElse(ItemStack.EMPTY);
    }

    private static Optional<RecipeHolder<CraftingRecipe>> findRecipe(Level level, CraftingInput input) {
        return level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level);
    }

    /**
     * 1 回分クラフトする (旧 extractItem)。
     * <p>
     * 先にレシピと材料の出どころ (グリッドのスロット・隣接インベントリのスロット) をすべて決め、何も動かさずに確かめてから
     * 実際に取り出す。材料が 1 つでも足りない場合・レシピが無い場合は、どのインベントリにも触れずに {@link ItemStack#EMPTY} を返す。
     * 旧実装 (およびこの移行版の以前の実装) は「1 個ずつ取り出し、足りなければ戻す」方式で、戻し先が搬入を受け付けない
     * インベントリ (ProjectE のコンデンサーの出力枠など、取り出し専用のもの) だった場合に、戻せなかった材料が
     * 別の隣接インベントリへ入ってしまっていた。
     *
     * @param doRemove      false なら何も動かさず、作れる物だけを返す
     * @param useLastItem   true なら各スロットの最後の 1 個も材料に使う (GUI から取る場合)
     * @return 完成品。作れなければ {@link ItemStack#EMPTY}
     */
    public ItemStack craft(boolean doRemove, boolean useLastItem) {
        if (level == null) {
            return ItemStack.EMPTY;
        }
        // 材料は見本と同じアイテム (同じコンポーネント) なので、レシピは見本 1 個ずつの並びで決まる。
        List<ItemStack> items = new ArrayList<>(GRID_SIZE);
        for (int slot = 0; slot < GRID_SIZE; slot++) {
            ItemStack template = grid.getStackInSlot(slot);
            items.add(template.isEmpty() ? ItemStack.EMPTY : template.copyWithCount(1));
        }
        CraftingInput.Positioned positioned = CraftingInput.ofPositioned(GRID_WIDTH, GRID_WIDTH, items);
        CraftingInput input = positioned.input();
        Optional<RecipeHolder<CraftingRecipe>> recipe = findRecipe(level, input);
        if (recipe.isEmpty()) {
            return ItemStack.EMPTY;
        }
        Source[] sources = planSources(useLastItem ? 0 : 1);
        if (sources == null) {
            return ItemStack.EMPTY;
        }
        ItemStack result = recipe.get().value().assemble(input, level.registryAccess());
        if (!doRemove) {
            return result;
        }
        Taken[] taken = takeAll(sources);
        if (taken == null) {
            return ItemStack.EMPTY;
        }
        returnRemainders(recipe.get().value().getRemainingItems(input), positioned, taken);
        return result;
    }

    /**
     * 各見本スロットの材料の出どころを決める (何も動かさない)。
     * 見本が {@code keep} 個より多ければそのスロット自身から、そうでなければ隣接インベントリから取る (旧 getNearbyItem)。
     * 同じ隣接スロットを複数の材料に使う場合は、その合計個数を取り出せるかを試して確かめる。
     *
     * @return 見本スロットごとの出どころ (空のスロットは null)。足りない材料があれば null
     */
    @Nullable
    private Source[] planSources(int keep) {
        Source[] sources = new Source[GRID_SIZE];
        Map<Source, Integer> reserved = new HashMap<>();
        for (int slot = 0; slot < GRID_SIZE; slot++) {
            ItemStack template = grid.getStackInSlot(slot);
            if (template.isEmpty()) {
                continue;
            }
            Source source = template.getCount() > keep ? new Source(null, slot) : findNearby(template, reserved);
            if (source == null) {
                return null;
            }
            reserved.merge(source, 1, Integer::sum);
            sources[slot] = source;
        }
        return sources;
    }

    /** 隣接するインベントリから、見本と同じアイテムをもう 1 個取り出せるスロットを探す (旧 getNearbyItem)。 */
    @Nullable
    private Source findNearby(ItemStack template, Map<Source, Integer> reserved) {
        for (Direction direction : NEARBY_ORDER) {
            IItemHandler handler = nearbyHandler(direction);
            if (handler == null) {
                continue;
            }
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                if (!ItemStack.isSameItemSameComponents(handler.getStackInSlot(slot), template)) {
                    continue;
                }
                Source candidate = new Source(direction, slot);
                int needed = reserved.getOrDefault(candidate, 0) + 1;
                ItemStack simulated = handler.extractItem(slot, needed, true);
                if (simulated.getCount() >= needed && ItemStack.isSameItemSameComponents(simulated, template)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    /** 隣接インベントリ (自動作業台どうしは除く)。 */
    @Nullable
    private IItemHandler nearbyHandler(Direction direction) {
        BlockPos neighbor = worldPosition.relative(direction);
        if (level.getBlockEntity(neighbor) instanceof AutoWorkbenchBlockEntity) {
            return null;
        }
        return level.getCapability(Capabilities.ItemHandler.BLOCK, neighbor, direction.getOpposite());
    }

    /**
     * 決めた出どころから材料を 1 個ずつ取り出す。直前に確かめているため通常は失敗しないが、
     * 取り出せなかった場合はそれまでに取り出した材料を戻して null を返す。
     */
    @Nullable
    private Taken[] takeAll(Source[] sources) {
        Taken[] taken = new Taken[GRID_SIZE];
        for (int slot = 0; slot < GRID_SIZE; slot++) {
            Source source = sources[slot];
            if (source == null) {
                continue;
            }
            IItemHandler handler = source.side() == null ? grid : nearbyHandler(source.side());
            ItemStack item = handler == null ? ItemStack.EMPTY : handler.extractItem(source.slot(), 1, false);
            if (item.isEmpty()) {
                giveBack(taken);
                return null;
            }
            taken[slot] = new Taken(handler, source.slot(), item, slot);
        }
        return taken;
    }

    /** レシピの残りアイテム (空バケツなど) を、元の材料を取った場所へ戻す。 */
    private void returnRemainders(NonNullList<ItemStack> remainders, CraftingInput.Positioned positioned, Taken[] taken) {
        CraftingInput input = positioned.input();
        for (int row = 0; row < input.height(); row++) {
            for (int column = 0; column < input.width(); column++) {
                ItemStack remainder = remainders.get(column + row * input.width());
                if (remainder.isEmpty()) {
                    continue;
                }
                Taken source = taken[column + positioned.left() + (row + positioned.top()) * GRID_WIDTH];
                ItemStack rest = source == null ? remainder : source.handler().insertItem(source.slot(), remainder, false);
                if (!rest.isEmpty() && !InventoryUtils.insertIntoAdjacentPipe(level, worldPosition, rest)) {
                    InventoryUtils.ejectUpward(level, worldPosition, rest);
                }
            }
        }
    }

    /**
     * 取り出した材料を元の場所へ戻す (旧 resetPointers)。元の場所が受け付けない場合は、その材料の見本スロットへ入れ、
     * それも入らなければ上へ放出する。無関係な隣接インベントリへは入れない。
     */
    private void giveBack(Taken[] taken) {
        for (Taken take : taken) {
            if (take == null) {
                continue;
            }
            ItemStack rest = take.handler().insertItem(take.slot(), take.item(), false);
            if (!rest.isEmpty()) {
                rest = grid.insertItem(take.gridSlot(), rest, false);
            }
            InventoryUtils.ejectUpward(level, worldPosition, rest);
        }
    }

    /**
     * 材料の出どころ。
     *
     * @param side 隣接インベントリの方向。グリッド自身のスロットなら null
     * @param slot そのインベントリ内のスロット
     */
    private record Source(@Nullable Direction side, int slot) {
    }

    /** 取り出した材料と、その出どころ・使った見本スロット。 */
    private record Taken(IItemHandler handler, int slot, ItemStack item, int gridSlot) {
    }

    // ---- 搬入規則 -------------------------------------------------------------

    /** 同じアイテムが入っていて、まだ入る余地があるスロットのうち最も少ないもの。無ければ -1 (旧 addItem)。 */
    private int bestSlotFor(ItemStack stack) {
        int best = -1;
        int bestCount = Integer.MAX_VALUE;
        for (int slot = 0; slot < GRID_SIZE; slot++) {
            ItemStack current = grid.getStackInSlot(slot);
            if (!current.isEmpty() && ItemStack.isSameItemSameComponents(current, stack)
                    && current.getCount() < grid.getSlotLimit(slot) && current.getCount() < current.getMaxStackSize()
                    && current.getCount() < bestCount) {
                best = slot;
                bestCount = current.getCount();
            }
        }
        return best;
    }

    // ---- MenuProvider -------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new AutoWorkbenchMenu(containerId, playerInventory, this);
    }

    public void dropContents() {
        if (level != null) {
            InventoryUtils.dropAll(level, worldPosition, grid);
        }
    }

    // ---- 保存 ---------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(TAG_GRID, grid.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        grid.deserializeNBT(registries, tag.getCompound(TAG_GRID));
    }

    /** パイプ・ホッパー向けの入出力。 */
    private final class AutomationHandler implements IItemHandler {
        @Override
        public int getSlots() {
            return GRID_SIZE + 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return slot == OUTPUT_SLOT ? previewResult() : grid.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot == OUTPUT_SLOT || stack.isEmpty() || slot != bestSlotFor(stack)) {
                return stack;
            }
            return grid.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot != OUTPUT_SLOT) {
                return ItemStack.EMPTY;
            }
            // 返す量は amount を超えられないため、完成品の個数が amount を超える場合は作らない。
            // 試しに取り出す場合も、材料が実際にそろうか (隣接インベントリを含む) まで確かめる (旧 extractItem(false, ...))。
            ItemStack result = craft(false, false);
            if (result.isEmpty() || result.getCount() > amount) {
                return ItemStack.EMPTY;
            }
            return simulate ? result : craft(true, false);
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot == OUTPUT_SLOT ? Item.DEFAULT_MAX_STACK_SIZE : grid.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot != OUTPUT_SLOT && slot == bestSlotFor(stack);
        }
    }
}
