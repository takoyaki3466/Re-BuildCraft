package com.takoy3466.re_buildcraft.factory;

import com.takoy3466.re_buildcraft.api.util.InventoryUtils;
import com.takoy3466.re_buildcraft.registry.ModBlockEntities;
import java.util.ArrayList;
import java.util.List;
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
     *
     * @param doRemove      false なら材料を戻し、作れる物だけを返す
     * @param useLastItem   true なら各スロットの最後の 1 個も材料に使う (GUI から取る場合)
     * @return 完成品。作れなければ {@link ItemStack#EMPTY}
     */
    public ItemStack craft(boolean doRemove, boolean useLastItem) {
        if (level == null) {
            return ItemStack.EMPTY;
        }
        int keep = useLastItem ? 0 : 1;
        List<Taken> taken = new ArrayList<>(GRID_SIZE);
        List<ItemStack> items = new ArrayList<>(GRID_SIZE);
        for (int slot = 0; slot < GRID_SIZE; slot++) {
            ItemStack template = grid.getStackInSlot(slot);
            if (template.isEmpty()) {
                items.add(ItemStack.EMPTY);
                continue;
            }
            Taken take = template.getCount() <= keep ? takeNearby(template) : new Taken(grid, slot, grid.extractItem(slot, 1, false));
            if (take == null) {
                giveBack(taken);
                return ItemStack.EMPTY;
            }
            taken.add(take);
            items.add(take.item());
        }
        CraftingInput.Positioned positioned = CraftingInput.ofPositioned(GRID_WIDTH, GRID_WIDTH, items);
        CraftingInput input = positioned.input();
        Optional<RecipeHolder<CraftingRecipe>> recipe = findRecipe(level, input);
        if (recipe.isEmpty() || !doRemove) {
            giveBack(taken);
            return recipe.map(holder -> holder.value().assemble(input, level.registryAccess())).orElse(ItemStack.EMPTY);
        }
        ItemStack result = recipe.get().value().assemble(input, level.registryAccess());
        returnRemainders(recipe.get().value().getRemainingItems(input), positioned, items, taken);
        return result;
    }

    /** レシピの残りアイテム (空バケツなど) を、元の材料を取った場所へ戻す。 */
    private void returnRemainders(NonNullList<ItemStack> remainders, CraftingInput.Positioned positioned,
                                  List<ItemStack> items, List<Taken> taken) {
        CraftingInput input = positioned.input();
        for (int row = 0; row < input.height(); row++) {
            for (int column = 0; column < input.width(); column++) {
                ItemStack remainder = remainders.get(column + row * input.width());
                if (remainder.isEmpty()) {
                    continue;
                }
                int gridSlot = column + positioned.left() + (row + positioned.top()) * GRID_WIDTH;
                Taken source = sourceOf(items.get(gridSlot), taken);
                ItemStack rest = source == null ? remainder : source.handler().insertItem(source.slot(), remainder, false);
                if (!rest.isEmpty() && !InventoryUtils.insertIntoAdjacentPipe(level, worldPosition, rest)) {
                    InventoryUtils.ejectUpward(level, worldPosition, rest);
                }
            }
        }
    }

    @Nullable
    private static Taken sourceOf(ItemStack item, List<Taken> taken) {
        for (Taken take : taken) {
            if (take.item() == item) {
                return take;
            }
        }
        return null;
    }

    /** 隣接するインベントリから、見本と同じアイテムを 1 個取ってくる (旧 getNearbyItem)。 */
    @Nullable
    private Taken takeNearby(ItemStack template) {
        for (Direction direction : NEARBY_ORDER) {
            BlockPos neighbor = worldPosition.relative(direction);
            if (level.getBlockEntity(neighbor) instanceof AutoWorkbenchBlockEntity) {
                continue;
            }
            IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, neighbor, direction.getOpposite());
            if (handler == null) {
                continue;
            }
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                if (ItemStack.isSameItemSameComponents(handler.getStackInSlot(slot), template)) {
                    ItemStack item = handler.extractItem(slot, 1, false);
                    if (!item.isEmpty()) {
                        return new Taken(handler, slot, item);
                    }
                }
            }
        }
        return null;
    }

    /** 取ったアイテムを元の場所へ戻す (旧 resetPointers)。 */
    private void giveBack(List<Taken> taken) {
        for (Taken take : taken) {
            ItemStack rest = take.handler().insertItem(take.slot(), take.item(), false);
            if (!rest.isEmpty()) {
                rest = InventoryUtils.insertIntoAdjacentInventories(level, worldPosition, rest);
                InventoryUtils.ejectUpward(level, worldPosition, rest);
            }
        }
    }

    /** 取り出したアイテムとその出どころ。 */
    private record Taken(IItemHandler handler, int slot, ItemStack item) {
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
            ItemStack preview = previewResult();
            if (preview.isEmpty() || preview.getCount() > amount) {
                return ItemStack.EMPTY;
            }
            return simulate ? preview : craft(true, false);
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
