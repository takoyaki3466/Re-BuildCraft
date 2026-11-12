package com.takoy3466.re_buildcraft.registry;

import com.takoy3466.re_buildcraft.ReBuildcraft;
import com.takoy3466.re_buildcraft.api.recipe.AssemblyRecipe;
import com.takoy3466.re_buildcraft.api.recipe.RefineryRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Re-BuildCraft 独自のレシピ種別。 */
public final class ModRecipes {
    private static final String ASSEMBLY = "assembly";
    private static final String REFINERY = "refinery";

    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
            DeferredRegister.create(Registries.RECIPE_TYPE, ReBuildcraft.MODID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, ReBuildcraft.MODID);

    public static final DeferredHolder<RecipeType<?>, RecipeType<AssemblyRecipe>> ASSEMBLY_TYPE =
            RECIPE_TYPES.register(ASSEMBLY, () -> RecipeType.simple(ReBuildcraft.id(ASSEMBLY)));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<AssemblyRecipe>> ASSEMBLY_SERIALIZER =
            RECIPE_SERIALIZERS.register(ASSEMBLY, AssemblyRecipe.Serializer::new);

    public static final DeferredHolder<RecipeType<?>, RecipeType<RefineryRecipe>> REFINERY_TYPE =
            RECIPE_TYPES.register(REFINERY, () -> RecipeType.simple(ReBuildcraft.id(REFINERY)));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<RefineryRecipe>> REFINERY_SERIALIZER =
            RECIPE_SERIALIZERS.register(REFINERY, RefineryRecipe.Serializer::new);

    private ModRecipes() {
    }

    public static void register(IEventBus modEventBus) {
        RECIPE_TYPES.register(modEventBus);
        RECIPE_SERIALIZERS.register(modEventBus);
    }
}
