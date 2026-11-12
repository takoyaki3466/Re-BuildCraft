package com.takoy3466.re_buildcraft.factory;

import com.takoy3466.re_buildcraft.api.IMachine;
import com.takoy3466.re_buildcraft.api.power.PoweredBlockEntity;
import com.takoy3466.re_buildcraft.api.recipe.RefineryRecipe;
import com.takoy3466.re_buildcraft.api.recipe.RefineryRecipeInput;
import com.takoy3466.re_buildcraft.api.util.SafeTimeTracker;
import com.takoy3466.re_buildcraft.registry.ModBlockEntities;
import com.takoy3466.re_buildcraft.registry.ModRecipes;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import org.jetbrains.annotations.Nullable;

/**
 * 精製機。2 つの材料槽の液体を MJ で精製し、結果槽に溜める。元実装: {@code buildcraft.factory.TileRefinery}。
 * <ul>
 *   <li>液体はどの面からでも入り、フィルターが設定されていれば一致する槽にだけ入る (旧 fill)。</li>
 *   <li>取り出せるのは結果槽の液体だけ (旧 empty)。</li>
 *   <li>精製の条件・消費・アニメーションの速度変化は旧 updateEntity と同じ順序で判定する。</li>
 * </ul>
 */
public class RefineryBlockEntity extends PoweredBlockEntity implements IMachine, MenuProvider {
    public static final int LIQUID_PER_SLOT = 4000;
    public static final int FILTER_COUNT = 2;
    /** 旧 configure(20, 25, 25, 25, 1000) の最小・最大受け入れ量・起動量・蓄積量。 */
    private static final int MIN_ENERGY_RECEIVED = 25;
    private static final int MAX_ENERGY_RECEIVED = 25;
    private static final int MIN_ACTIVATION_ENERGY = 25;
    private static final int MAX_ENERGY_STORED = 1000;
    /** クライアントへの同期間隔 (旧 2 * updateFactor)。 */
    private static final int SYNC_INTERVAL_TICKS = 20;

    /** アニメーション (磁石の往復) の段階。100 段ごとに往路・復路が切り替わる (旧 animationStage)。 */
    public static final int ANIMATION_CYCLE_START = 100;
    public static final int ANIMATION_CYCLE_END = 300;
    private static final float ANIMATION_IDLE_SPEED = 1.0F;
    private static final float ANIMATION_ACTIVE_SPEED = 2.0F;
    private static final float ANIMATION_MAX_SPEED = 5.0F;
    private static final float ANIMATION_SPEED_STEP = 0.1F;

    private static final String TAG_FIRST = "first";
    private static final String TAG_SECOND = "second";
    private static final String TAG_RESULT = "result";
    private static final String TAG_FILTER = "filter_";
    private static final String TAG_ANIMATION_STAGE = "animation_stage";
    private static final String TAG_ANIMATION_SPEED = "animation_speed";

    private final FluidTank first = createTank();
    private final FluidTank second = createTank();
    private final FluidTank result = createTank();
    private final Fluid[] filters = {Fluids.EMPTY, Fluids.EMPTY};
    private final IFluidHandler fluidHandler = new RefineryFluidHandler();
    private final SafeTimeTracker workTracker = new SafeTimeTracker();
    private final SafeTimeTracker syncTracker = new SafeTimeTracker();
    @Nullable
    private RecipeHolder<RefineryRecipe> lastRecipe;
    private float animationSpeed = ANIMATION_IDLE_SPEED;
    private int animationStage;
    private boolean active;
    private boolean hasUpdate;

    public RefineryBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.REFINERY.get(), pos, state);
        powerProvider.configure(MIN_ENERGY_RECEIVED, MAX_ENERGY_RECEIVED, MIN_ACTIVATION_ENERGY, MAX_ENERGY_STORED);
    }

    private FluidTank createTank() {
        return new FluidTank(LIQUID_PER_SLOT) {
            @Override
            protected void onContentsChanged() {
                hasUpdate = true;
                setChanged();
            }
        };
    }

    // ---- tick ---------------------------------------------------------------

    public void clientTick() {
        // 旧 simpleAnimationIterate: サーバーから届いた速度で磁石を動かす。
        if (animationSpeed > ANIMATION_IDLE_SPEED) {
            advanceAnimation();
        } else if (animationStage > 0) {
            animationStage--;
        }
    }

    public void serverTick() {
        if (level == null) {
            return;
        }
        powerProvider.update(this, level);
        tryRefine(level);
        if (hasUpdate && syncTracker.markTimeIfDelay(level, SYNC_INTERVAL_TICKS)) {
            hasUpdate = false;
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    private void tryRefine(Level level) {
        boolean wasActive = active;
        float previousSpeed = animationSpeed;
        active = false;
        RefineryRecipe recipe = findRecipe(level).orElse(null);
        if (recipe == null || !result.isEmpty() && !FluidStack.isSameFluidSameComponents(result.getFluid(), recipe.result())
                || result.getFluidAmount() + recipe.result().getAmount() > LIQUID_PER_SLOT) {
            decreaseAnimation();
        } else {
            active = true;
            if (powerProvider.getEnergyStored() < recipe.energy()) {
                decreaseAnimation();
            } else {
                increaseAnimation();
                if (workTracker.markTimeIfDelay(level, recipe.delay())) {
                    refine(recipe);
                }
            }
        }
        if (wasActive != active || previousSpeed != animationSpeed) {
            hasUpdate = true;
        }
    }

    private void refine(RefineryRecipe recipe) {
        if (!containsInput(recipe.first()) || recipe.second().isPresent() && !containsInput(recipe.second().get())) {
            decreaseAnimation();
            return;
        }
        float used = powerProvider.useEnergy(recipe.energy(), recipe.energy(), true);
        if (used != 0.0F && consumeInput(recipe.first()) && recipe.second().map(this::consumeInput).orElse(true)) {
            result.fill(recipe.result().copy(), IFluidHandler.FluidAction.EXECUTE);
        }
    }

    private Optional<RefineryRecipe> findRecipe(Level level) {
        RefineryRecipeInput input = new RefineryRecipeInput(first.getFluid(), second.getFluid());
        Optional<RecipeHolder<RefineryRecipe>> found =
                level.getRecipeManager().getRecipeFor(ModRecipes.REFINERY_TYPE.get(), input, level, lastRecipe);
        found.ifPresent(holder -> lastRecipe = holder);
        return found.map(RecipeHolder::value);
    }

    private boolean containsInput(SizedFluidIngredient ingredient) {
        return holds(first, ingredient) || holds(second, ingredient);
    }

    private static boolean holds(FluidTank tank, SizedFluidIngredient ingredient) {
        return !tank.isEmpty() && ingredient.ingredient().test(tank.getFluid()) && tank.getFluidAmount() >= ingredient.amount();
    }

    private boolean consumeInput(SizedFluidIngredient ingredient) {
        FluidTank source = holds(first, ingredient) ? first : holds(second, ingredient) ? second : null;
        if (source == null) {
            return false;
        }
        source.drain(ingredient.amount(), IFluidHandler.FluidAction.EXECUTE);
        return true;
    }

    // ---- アニメーション (旧 increaseAnimation / decreaseAnimation) --------------------

    private void increaseAnimation() {
        if (animationSpeed < ANIMATION_ACTIVE_SPEED) {
            animationSpeed = ANIMATION_ACTIVE_SPEED;
        } else if (animationSpeed <= ANIMATION_MAX_SPEED) {
            animationSpeed += ANIMATION_SPEED_STEP;
        }
        advanceAnimation();
    }

    private void decreaseAnimation() {
        if (animationSpeed >= ANIMATION_IDLE_SPEED) {
            animationSpeed -= ANIMATION_SPEED_STEP;
            advanceAnimation();
        } else if (animationStage > 0) {
            animationStage--;
        }
    }

    private void advanceAnimation() {
        animationStage += (int) animationSpeed;
        if (animationStage > ANIMATION_CYCLE_END) {
            animationStage = ANIMATION_CYCLE_START;
        }
    }

    // ---- フィルター ----------------------------------------------------------

    public Fluid getFilter(int index) {
        return filters[index];
    }

    /** GUI の材料枠をクリックしたときに、持っている容器の液体 (空なら解除) を設定する。 */
    public void setFilter(int index, Fluid fluid) {
        filters[index] = fluid;
        setChanged();
    }

    private boolean hasFilters() {
        for (Fluid filter : filters) {
            if (filter != Fluids.EMPTY) {
                return true;
            }
        }
        return false;
    }

    // ---- 描画・GUI 向けアクセサ ------------------------------------------------

    public FluidStack getFirst() {
        return first.getFluid();
    }

    public FluidStack getSecond() {
        return second.getFluid();
    }

    public FluidStack getResult() {
        return result.getFluid();
    }

    public int getAnimationStage() {
        return animationStage;
    }

    public float getAnimationSpeed() {
        return animationSpeed;
    }

    public IFluidHandler getFluidHandler() {
        return fluidHandler;
    }

    public Direction getFacing() {
        return getBlockState().getValue(RefineryBlock.FACING);
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public boolean manageLiquids() {
        return true;
    }

    @Override
    public void doWork() {
        // 精製は毎 tick の serverTick で行う (旧 doWork も空)。
    }

    @Override
    public int powerRequest() {
        return isActive() ? powerProvider.getMaxEnergyReceived() : 0;
    }

    // ---- MenuProvider -------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.re_buildcraft.refinery");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new RefineryMenu(containerId, playerInventory, this);
    }

    // ---- 保存と同期 ---------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        writeFluids(tag, registries);
        for (int i = 0; i < FILTER_COUNT; i++) {
            if (filters[i] != Fluids.EMPTY) {
                tag.putString(TAG_FILTER + i, BuiltInRegistries.FLUID.getKey(filters[i]).toString());
            }
        }
        tag.putInt(TAG_ANIMATION_STAGE, animationStage);
    }

    private void writeFluids(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put(TAG_FIRST, first.writeToNBT(registries, new CompoundTag()));
        tag.put(TAG_SECOND, second.writeToNBT(registries, new CompoundTag()));
        tag.put(TAG_RESULT, result.writeToNBT(registries, new CompoundTag()));
        tag.putFloat(TAG_ANIMATION_SPEED, animationSpeed);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        first.readFromNBT(registries, tag.getCompound(TAG_FIRST));
        second.readFromNBT(registries, tag.getCompound(TAG_SECOND));
        result.readFromNBT(registries, tag.getCompound(TAG_RESULT));
        animationSpeed = tag.contains(TAG_ANIMATION_SPEED) ? tag.getFloat(TAG_ANIMATION_SPEED) : ANIMATION_IDLE_SPEED;
        if (tag.contains(TAG_ANIMATION_STAGE)) {
            animationStage = tag.getInt(TAG_ANIMATION_STAGE);
        }
        for (int i = 0; i < FILTER_COUNT; i++) {
            ResourceLocation id = tag.contains(TAG_FILTER + i) ? ResourceLocation.tryParse(tag.getString(TAG_FILTER + i)) : null;
            filters[i] = id == null ? Fluids.EMPTY : BuiltInRegistries.FLUID.get(id);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        writeFluids(tag, registries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** どの面からでも材料を入れられ、結果だけを取り出せる液体ハンドラ。 */
    private final class RefineryFluidHandler implements IFluidHandler {
        @Override
        public int getTanks() {
            return 3;
        }

        private FluidTank tank(int index) {
            return switch (index) {
                case 0 -> first;
                case 1 -> second;
                default -> result;
            };
        }

        @Override
        public FluidStack getFluidInTank(int index) {
            return tank(index).getFluid();
        }

        @Override
        public int getTankCapacity(int index) {
            return LIQUID_PER_SLOT;
        }

        @Override
        public boolean isFluidValid(int index, FluidStack stack) {
            return index < FILTER_COUNT && (!hasFilters() || filters[index] == stack.getFluid());
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (resource.isEmpty()) {
                return 0;
            }
            int filled = 0;
            for (int i = 0; i < FILTER_COUNT && filled < resource.getAmount(); i++) {
                if (isFluidValid(i, resource)) {
                    filled += tank(i).fill(resource.copyWithAmount(resource.getAmount() - filled), action);
                }
            }
            return filled;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            if (resource.isEmpty() || !FluidStack.isSameFluidSameComponents(resource, result.getFluid())) {
                return FluidStack.EMPTY;
            }
            return result.drain(resource.getAmount(), action);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return result.drain(maxDrain, action);
        }
    }
}
