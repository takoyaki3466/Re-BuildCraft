package com.takoy3466.re_buildcraft.api.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.takoy3466.re_buildcraft.registry.ModRecipes;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

/**
 * 組立台 (Assembly Table) のレシピ。必要な材料がそろった状態でレーザーから {@code energy} MJ を受けると完成する。
 * 元実装: {@code buildcraft.core.AssemblyRecipe} と {@code BCAPI.addRecipeAssembly}。
 * <p>
 * 旧 API はコードから static なリストへ追加していたが、1.21.1 ではレシピ種別
 * {@code re_buildcraft:assembly} のデータパック JSON として定義する。
 *
 * @param ingredients 必要な材料 (個数付き)。組立台内の同種アイテムは複数スロットに分かれていても合算する
 * @param energy      完成に必要なレーザーエネルギー (MJ)
 * @param result      完成品
 */
public record AssemblyRecipe(List<SizedIngredient> ingredients, int energy, ItemStack result)
        implements Recipe<AssemblyRecipeInput> {

    @Override
    public boolean matches(AssemblyRecipeInput input, Level level) {
        for (SizedIngredient ingredient : ingredients) {
            if (input.count(ingredient) < ingredient.count()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack assemble(AssemblyRecipeInput input, HolderLookup.Provider registries) {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return result;
    }

    @Override
    public boolean isSpecial() {
        // レシピブックには載せない。
        return true;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.ASSEMBLY_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipes.ASSEMBLY_TYPE.get();
    }

    /** {@code re_buildcraft:assembly} の JSON / ネットワーク表現。 */
    public static final class Serializer implements RecipeSerializer<AssemblyRecipe> {
        private static final MapCodec<AssemblyRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                SizedIngredient.FLAT_CODEC.listOf().fieldOf("ingredients").forGetter(AssemblyRecipe::ingredients),
                Codec.intRange(1, Integer.MAX_VALUE).fieldOf("energy").forGetter(AssemblyRecipe::energy),
                ItemStack.STRICT_CODEC.fieldOf("result").forGetter(AssemblyRecipe::result)
        ).apply(instance, AssemblyRecipe::new));

        private static final StreamCodec<RegistryFriendlyByteBuf, AssemblyRecipe> STREAM_CODEC = StreamCodec.composite(
                SizedIngredient.STREAM_CODEC.apply(ByteBufCodecs.list()), AssemblyRecipe::ingredients,
                ByteBufCodecs.VAR_INT, AssemblyRecipe::energy,
                ItemStack.STREAM_CODEC, AssemblyRecipe::result,
                AssemblyRecipe::new);

        @Override
        public MapCodec<AssemblyRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, AssemblyRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
