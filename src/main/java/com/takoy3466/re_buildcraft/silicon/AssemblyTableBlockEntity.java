package com.takoy3466.re_buildcraft.silicon;

import com.takoy3466.re_buildcraft.api.laser.ILaserTarget;
import com.takoy3466.re_buildcraft.api.recipe.AssemblyRecipe;
import com.takoy3466.re_buildcraft.api.recipe.AssemblyRecipeInput;
import com.takoy3466.re_buildcraft.api.transport.IPipeConnectable;
import com.takoy3466.re_buildcraft.api.util.InventoryUtils;
import com.takoy3466.re_buildcraft.registry.ModBlockEntities;
import com.takoy3466.re_buildcraft.registry.ModRecipes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * 組立台。材料を入れて作りたい品を「予約」し、レーザーのエネルギーで完成させる。
 * 元実装: {@code buildcraft.silicon.TileAssemblyTable}。
 * <p>
 * レシピはデータパックで再読み込みされ得るため、予約はレシピ ID で保持し、使う時点で解決する。
 */
public class AssemblyTableBlockEntity extends BlockEntity implements ILaserTarget, MenuProvider, IPipeConnectable {
    public static final int INVENTORY_SIZE = 12;

    private static final String TITLE_KEY = "container.re_buildcraft.assembly_table";
    private static final String TAG_INVENTORY = "inventory";
    private static final String TAG_ENERGY_STORED = "energyStored";
    private static final String TAG_PLANNED = "planned";
    private static final String TAG_CURRENT_RECIPE = "recipe";
    private static final double OUTPUT_DROP_OFFSET_XZ = 0.5;
    private static final double OUTPUT_DROP_OFFSET_Y = 0.7;

    private final ItemStackHandler inventory = new ItemStackHandler(INVENTORY_SIZE) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (currentRecipe == null && level != null && !level.isClientSide) {
                setNextCurrentRecipe();
            }
        }
    };
    private final List<ResourceLocation> plannedOutput = new ArrayList<>();
    @Nullable
    private ResourceLocation currentRecipe;
    private float energyStored;

    public AssemblyTableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ASSEMBLY_TABLE.get(), pos, state);
    }

    // ---- tick -------------------------------------------------------------

    public void serverTick() {
        RecipeHolder<AssemblyRecipe> recipe = resolve(currentRecipe);
        if (recipe == null) {
            if (currentRecipe != null) {
                // データパックの変更で消えたレシピは予約から外す。
                cancelPlanOutput(currentRecipe);
            }
            return;
        }

        if (!canBeDone(recipe)) {
            setNextCurrentRecipe();
            recipe = resolve(currentRecipe);
            if (recipe == null) {
                return;
            }
        }

        if (energyStored >= recipe.value().energy()) {
            energyStored = 0.0F;
            if (canBeDone(recipe)) {
                consumeIngredients(recipe.value());
                ejectResult(recipe.value().result().copy());
                setNextCurrentRecipe();
            }
            setChanged();
        }
    }

    private void consumeIngredients(AssemblyRecipe recipe) {
        for (SizedIngredient ingredient : recipe.ingredients()) {
            int remaining = ingredient.count();
            for (int slot = 0; slot < inventory.getSlots() && remaining > 0; slot++) {
                if (ingredient.ingredient().test(inventory.getStackInSlot(slot))) {
                    remaining -= inventory.extractItem(slot, remaining, false).getCount();
                }
            }
        }
    }

    private void ejectResult(ItemStack result) {
        if (level == null) {
            return;
        }
        ItemStack remainder = InventoryUtils.insertIntoAdjacentInventories(level, worldPosition, result);
        if (!remainder.isEmpty() && !InventoryUtils.insertIntoAdjacentPipe(level, worldPosition, remainder)) {
            level.addFreshEntity(new ItemEntity(level,
                    worldPosition.getX() + OUTPUT_DROP_OFFSET_XZ,
                    worldPosition.getY() + OUTPUT_DROP_OFFSET_Y,
                    worldPosition.getZ() + OUTPUT_DROP_OFFSET_XZ,
                    remainder));
        }
    }

    // ---- レシピの解決 -------------------------------------------------------

    @Nullable
    @SuppressWarnings("unchecked")
    private RecipeHolder<AssemblyRecipe> resolve(@Nullable ResourceLocation id) {
        if (id == null || level == null) {
            return null;
        }
        return level.getRecipeManager().byKey(id)
                .filter(holder -> holder.value() instanceof AssemblyRecipe)
                .map(holder -> (RecipeHolder<AssemblyRecipe>) holder)
                .orElse(null);
    }

    private AssemblyRecipeInput createInput() {
        List<ItemStack> items = new ArrayList<>(inventory.getSlots());
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            items.add(inventory.getStackInSlot(slot));
        }
        return new AssemblyRecipeInput(items);
    }

    private boolean canBeDone(RecipeHolder<AssemblyRecipe> recipe) {
        return level != null && recipe.value().matches(createInput(), level);
    }

    private boolean canBeDone(ResourceLocation id) {
        RecipeHolder<AssemblyRecipe> recipe = resolve(id);
        return recipe != null && canBeDone(recipe);
    }

    /**
     * 現在の材料で作成できるレシピの一覧。
     * サーバーとクライアントで同じ並びになるよう、レシピ ID 順に整列する。
     */
    public List<RecipeHolder<AssemblyRecipe>> getPotentialOutputs() {
        if (level == null) {
            return List.of();
        }
        AssemblyRecipeInput input = createInput();
        return level.getRecipeManager().getAllRecipesFor(ModRecipes.ASSEMBLY_TYPE.get()).stream()
                .filter(holder -> holder.value().matches(input, level))
                .sorted(Comparator.comparing((RecipeHolder<AssemblyRecipe> holder) -> holder.id()))
                .toList();
    }

    // ---- 予約の操作 ---------------------------------------------------------

    public boolean isPlanned(ResourceLocation id) {
        return plannedOutput.contains(id);
    }

    public boolean isAssembling(ResourceLocation id) {
        return id.equals(currentRecipe);
    }

    /** 予約済みなら取り消し、未予約なら予約する (GUI のクリック操作)。 */
    public void togglePlan(ResourceLocation id) {
        if (isPlanned(id)) {
            cancelPlanOutput(id);
        } else {
            planOutput(id);
        }
        setChanged();
    }

    private void planOutput(ResourceLocation id) {
        plannedOutput.add(id);
        if (currentRecipe == null || !isPlanned(currentRecipe)) {
            currentRecipe = id;
        }
    }

    private void cancelPlanOutput(ResourceLocation id) {
        if (isAssembling(id)) {
            currentRecipe = null;
        }
        plannedOutput.remove(id);
        if (!plannedOutput.isEmpty()) {
            currentRecipe = plannedOutput.get(0);
        }
    }

    /** 予約の中から、現在のレシピの次に作成可能なものを選ぶ。無ければ先頭から探す。 */
    private void setNextCurrentRecipe() {
        boolean takeNext = false;
        for (ResourceLocation id : plannedOutput) {
            if (Objects.equals(id, currentRecipe)) {
                takeNext = true;
            } else if (takeNext && canBeDone(id)) {
                currentRecipe = id;
                return;
            }
        }
        for (ResourceLocation id : plannedOutput) {
            if (canBeDone(id)) {
                currentRecipe = id;
                return;
            }
        }
        currentRecipe = null;
    }

    @Override
    public boolean isPipeConnected(Direction side) {
        return true;
    }

    // ---- ILaserTarget -----------------------------------------------------

    @Override
    public boolean requiresLaserEnergy() {
        return !isRemoved() && currentRecipe != null;
    }

    @Override
    public void receiveLaserEnergy(float energy) {
        energyStored += energy;
    }

    // ---- GUI ----------------------------------------------------------------

    public ItemStackHandler getInventory() {
        return inventory;
    }

    public float getEnergyStored() {
        return energyStored;
    }

    /** 現在組み立て中のレシピが必要とするエネルギー。組み立て中でなければ 0。 */
    public int getRequiredEnergy() {
        RecipeHolder<AssemblyRecipe> recipe = resolve(currentRecipe);
        return recipe == null ? 0 : recipe.value().energy();
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable(TITLE_KEY);
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new AssemblyTableMenu(containerId, playerInventory, this);
    }

    public void dropContents() {
        if (level != null) {
            InventoryUtils.dropAll(level, worldPosition, inventory);
        }
    }

    // ---- 保存 ---------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(TAG_INVENTORY, inventory.serializeNBT(registries));
        tag.putFloat(TAG_ENERGY_STORED, energyStored);
        ListTag planned = new ListTag();
        for (ResourceLocation id : plannedOutput) {
            planned.add(StringTag.valueOf(id.toString()));
        }
        tag.put(TAG_PLANNED, planned);
        if (currentRecipe != null) {
            tag.putString(TAG_CURRENT_RECIPE, currentRecipe.toString());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        inventory.deserializeNBT(registries, tag.getCompound(TAG_INVENTORY));
        energyStored = tag.getFloat(TAG_ENERGY_STORED);
        plannedOutput.clear();
        ListTag planned = tag.getList(TAG_PLANNED, Tag.TAG_STRING);
        for (int i = 0; i < planned.size(); i++) {
            ResourceLocation id = ResourceLocation.tryParse(planned.getString(i));
            if (id != null) {
                plannedOutput.add(id);
            }
        }
        currentRecipe = tag.contains(TAG_CURRENT_RECIPE) ? ResourceLocation.tryParse(tag.getString(TAG_CURRENT_RECIPE)) : null;
    }
}
