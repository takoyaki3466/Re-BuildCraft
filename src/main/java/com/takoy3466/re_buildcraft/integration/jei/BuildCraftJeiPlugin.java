package com.takoy3466.re_buildcraft.integration.jei;

import com.takoy3466.re_buildcraft.ReBuildcraft;
import com.takoy3466.re_buildcraft.api.recipe.AssemblyRecipe;
import com.takoy3466.re_buildcraft.api.recipe.RefineryRecipe;
import com.takoy3466.re_buildcraft.registry.ModBlocks;
import com.takoy3466.re_buildcraft.registry.ModRecipes;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * JEI 連携。JEI が導入されているときだけ、JEI が {@link JeiPlugin} を検出して読み込む。
 * <p>
 * Re-BuildCraft の他のクラスはこのパッケージを参照しないため、JEI が無い環境では JEI のクラスは読み込まれない。
 * <ul>
 *   <li>組立台・精製機: 機械が実際に照合に使うレシピ型 ({@code re_buildcraft:assembly} / {@code re_buildcraft:refinery}) に
 *       登録されたレシピを、クライアントへ同期されたものからそのまま表示する (表示用のレシピを別に持たない)。</li>
 *   <li>自動作業台: バニラの作業台レシピ ({@code RecipeType.CRAFTING}) をそのまま使うため、JEI の既存の作業台カテゴリに
 *       触媒として追加するだけにする (カテゴリを重複させない)。</li>
 * </ul>
 * それ以外の機械 (エンジンの燃料・クアーリー・ポンプなど) はレシピ型を使わないため、表示対象にしていない。
 */
@JeiPlugin
public final class BuildCraftJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID = ReBuildcraft.id("jei_plugin");
    /** カテゴリ ID は機械のレシピ型 ID と同じ。本 Mod の名前空間なので他と衝突しない。 */
    static final RecipeType<RecipeHolder<AssemblyRecipe>> ASSEMBLY = RecipeType.createRecipeHolderType(ReBuildcraft.id("assembly"));
    static final RecipeType<RecipeHolder<RefineryRecipe>> REFINERY = RecipeType.createRecipeHolderType(ReBuildcraft.id("refinery"));

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper guiHelper = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(new AssemblyRecipeCategory(guiHelper), new RefineryRecipeCategory(guiHelper));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        registration.addRecipes(ASSEMBLY, List.copyOf(level.getRecipeManager().getAllRecipesFor(ModRecipes.ASSEMBLY_TYPE.get())));
        registration.addRecipes(REFINERY, List.copyOf(level.getRecipeManager().getAllRecipesFor(ModRecipes.REFINERY_TYPE.get())));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalysts(ASSEMBLY, ModBlocks.ASSEMBLY_TABLE.get(), ModBlocks.LASER.get());
        registration.addRecipeCatalysts(REFINERY, ModBlocks.REFINERY.get());
        registration.addRecipeCatalysts(RecipeTypes.CRAFTING, ModBlocks.AUTO_WORKBENCH.get());
    }
}
