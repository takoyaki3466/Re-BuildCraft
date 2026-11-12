package com.takoy3466.re_buildcraft.api.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.takoy3466.re_buildcraft.registry.ModRecipes;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

/**
 * 精製機 (Refinery) のレシピ。元実装: {@code buildcraft.api.RefineryRecipe} と {@code BCAPI.addRecipeRefinery}。
 * <p>
 * 旧 API はコードから static なリストへ追加していたが、1.21.1 ではレシピ種別
 * {@code re_buildcraft:refinery} のデータパック JSON として定義する。
 *
 * @param first  1 つ目の材料の液体と 1 回あたりの消費量 (mB)
 * @param second 2 つ目の材料。無いレシピもある
 * @param energy 1 回の精製に必要な MJ (この量が蓄えられるまで動かない)
 * @param delay  精製の間隔 (tick)
 * @param result 1 回の精製でできる液体
 */
public record RefineryRecipe(SizedFluidIngredient first, Optional<SizedFluidIngredient> second, int energy, int delay,
                             FluidStack result) implements Recipe<RefineryRecipeInput> {

    /**
     * 旧 {@code BuildCraftAPI.findRefineryRecipe} の照合規則。量は見ず、液体の組み合わせだけを比べる。
     * <ul>
     *   <li>どちらかの槽に 1 つ目の材料が入っていること。</li>
     *   <li>2 つ目の材料が無いレシピでは、もう一方の槽は空か同じ液体であること。</li>
     *   <li>2 つ目の材料があるレシピでは、もう一方の槽にその液体が入っていること。</li>
     * </ul>
     */
    @Override
    public boolean matches(RefineryRecipeInput input, Level level) {
        return matchesInOrder(input.first(), input.second()) || matchesInOrder(input.second(), input.first());
    }

    private boolean matchesInOrder(FluidStack main, FluidStack other) {
        if (main.isEmpty() || !first.ingredient().test(main)) {
            return false;
        }
        if (second.isEmpty()) {
            return other.isEmpty() || FluidStack.isSameFluidSameComponents(main, other);
        }
        return !other.isEmpty() && second.get().ingredient().test(other);
    }

    /** 液体のレシピなので完成品のアイテムは無い。 */
    @Override
    public ItemStack assemble(RefineryRecipeInput input, HolderLookup.Provider registries) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return ItemStack.EMPTY;
    }

    /** レシピ本には載せない。 */
    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.REFINERY_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipes.REFINERY_TYPE.get();
    }

    public static final class Serializer implements RecipeSerializer<RefineryRecipe> {
        private static final MapCodec<RefineryRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                SizedFluidIngredient.FLAT_CODEC.fieldOf("first").forGetter(RefineryRecipe::first),
                SizedFluidIngredient.FLAT_CODEC.optionalFieldOf("second").forGetter(RefineryRecipe::second),
                Codec.INT.fieldOf("energy").forGetter(RefineryRecipe::energy),
                Codec.INT.fieldOf("delay").forGetter(RefineryRecipe::delay),
                FluidStack.CODEC.fieldOf("result").forGetter(RefineryRecipe::result)
        ).apply(instance, RefineryRecipe::new));

        private static final StreamCodec<RegistryFriendlyByteBuf, RefineryRecipe> STREAM_CODEC = StreamCodec.composite(
                SizedFluidIngredient.STREAM_CODEC, RefineryRecipe::first,
                ByteBufCodecs.optional(SizedFluidIngredient.STREAM_CODEC), RefineryRecipe::second,
                ByteBufCodecs.VAR_INT, RefineryRecipe::energy,
                ByteBufCodecs.VAR_INT, RefineryRecipe::delay,
                FluidStack.STREAM_CODEC, RefineryRecipe::result,
                RefineryRecipe::new);

        @Override
        public MapCodec<RefineryRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, RefineryRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
