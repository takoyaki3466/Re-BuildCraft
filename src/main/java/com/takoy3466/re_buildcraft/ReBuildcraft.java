package com.takoy3466.re_buildcraft;

import com.mojang.logging.LogUtils;
import com.takoy3466.re_buildcraft.api.engine.EngineFuel;
import com.takoy3466.re_buildcraft.registry.ModBlockEntities;
import com.takoy3466.re_buildcraft.registry.ModBlocks;
import com.takoy3466.re_buildcraft.registry.ModCapabilities;
import com.takoy3466.re_buildcraft.registry.ModCreativeTabs;
import com.takoy3466.re_buildcraft.registry.ModFeatures;
import com.takoy3466.re_buildcraft.registry.ModFluids;
import com.takoy3466.re_buildcraft.registry.ModItems;
import com.takoy3466.re_buildcraft.registry.ModMenus;
import com.takoy3466.re_buildcraft.registry.ModRecipes;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;
import org.slf4j.Logger;

/**
 * BuildCraft 3.1.5.x (BCx) の Minecraft 1.21.1 / NeoForge 向け移行版のエントリーポイント。
 * <p>
 * ここでは各レジストリとイベントの接続だけを行い、ゲームロジックは各機能パッケージに置く。
 */
@Mod(ReBuildcraft.MODID)
public class ReBuildcraft {
    public static final String MODID = "re_buildcraft";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ReBuildcraft(IEventBus modEventBus, ModContainer modContainer) {
        ModFluids.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModMenus.register(modEventBus);
        ModRecipes.register(modEventBus);
        ModFeatures.register(modEventBus);
        ModCreativeTabs.register(modEventBus);

        modEventBus.addListener(ModCapabilities::register);
        modEventBus.addListener(this::registerDataMapTypes);

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void registerDataMapTypes(RegisterDataMapTypesEvent event) {
        event.register(EngineFuel.DATA_MAP);
    }

    /** この Mod の名前空間を持つ {@link ResourceLocation} を作る。 */
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
