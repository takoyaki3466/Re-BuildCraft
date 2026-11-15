package com.takoy3466.re_buildcraft.client;

import com.takoy3466.re_buildcraft.ReBuildcraft;
import com.takoy3466.re_buildcraft.api.client.EngineRenderer;
import com.takoy3466.re_buildcraft.api.client.LaserBeamRenderer;
import com.takoy3466.re_buildcraft.api.client.QuarryRenderer;
import com.takoy3466.re_buildcraft.builders.blueprint.BlueprintItem;
import com.takoy3466.re_buildcraft.client.screen.ArchitectScreen;
import com.takoy3466.re_buildcraft.client.screen.AssemblyTableScreen;
import com.takoy3466.re_buildcraft.client.screen.AutoWorkbenchScreen;
import com.takoy3466.re_buildcraft.client.screen.BuilderScreen;
import com.takoy3466.re_buildcraft.client.screen.CombustionEngineScreen;
import com.takoy3466.re_buildcraft.client.screen.DiamondFilterScreen;
import com.takoy3466.re_buildcraft.client.screen.FillerScreen;
import com.takoy3466.re_buildcraft.client.screen.GateScreen;
import com.takoy3466.re_buildcraft.client.screen.LibraryScreen;
import com.takoy3466.re_buildcraft.client.screen.RefineryScreen;
import com.takoy3466.re_buildcraft.client.screen.SteamEngineScreen;
import com.takoy3466.re_buildcraft.registry.ModBlockEntities;
import com.takoy3466.re_buildcraft.registry.ModFluids;
import com.takoy3466.re_buildcraft.registry.ModItems;
import com.takoy3466.re_buildcraft.registry.ModMenus;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/** クライアント専用の登録 (描画・GUI 画面・液体テクスチャ)。サーバーではこのクラスは読み込まれない。 */
@EventBusSubscriber(modid = ReBuildcraft.MODID, value = Dist.CLIENT)
public final class ReBuildcraftClient {

    private ReBuildcraftClient() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.ENGINE.get(), EngineRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.LASER.get(), LaserBeamRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.PUMP.get(), PumpTubeRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.QUARRY.get(), QuarryRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.PIPE.get(), PipeRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.REFINERY.get(), RefineryRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.MARKER.get(), MarkerRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.FILLER.get(), AreaRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.ARCHITECT.get(), AreaRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.BUILDER.get(), BuilderRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.PATH_MARKER.get(), PathMarkerRenderer::new);
    }

    /** テンプレート・設計図アイテムの見た目を、記録済みかどうかで切り替える (旧 getIconFromDamage)。 */
    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            for (BlueprintItem item : new BlueprintItem[]{ModItems.TEMPLATE.get(), ModItems.BLUEPRINT.get()}) {
                ItemProperties.register(item, ReBuildcraft.id("written"),
                        (stack, level, entity, seed) -> BlueprintItem.ref(stack) == null ? 0.0F : 1.0F);
            }
        });
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.STEAM_ENGINE.get(), SteamEngineScreen::new);
        event.register(ModMenus.COMBUSTION_ENGINE.get(), CombustionEngineScreen::new);
        event.register(ModMenus.ASSEMBLY_TABLE.get(), AssemblyTableScreen::new);
        event.register(ModMenus.GATE.get(), GateScreen::new);
        event.register(ModMenus.DIAMOND_PIPE.get(), DiamondFilterScreen::new);
        event.register(ModMenus.REFINERY.get(), RefineryScreen::new);
        event.register(ModMenus.AUTO_WORKBENCH.get(), AutoWorkbenchScreen::new);
        event.register(ModMenus.FILLER.get(), FillerScreen::new);
        event.register(ModMenus.ARCHITECT.get(), ArchitectScreen::new);
        event.register(ModMenus.BUILDER.get(), BuilderScreen::new);
        event.register(ModMenus.LIBRARY.get(), LibraryScreen::new);
    }

    @SubscribeEvent
    public static void registerClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerFluidType(fluidTextures("oil"), ModFluids.OIL_TYPE.get());
        event.registerFluidType(fluidTextures("fuel"), ModFluids.FUEL_TYPE.get());
    }

    private static IClientFluidTypeExtensions fluidTextures(String name) {
        ResourceLocation still = ReBuildcraft.id("block/" + name + "_still");
        ResourceLocation flowing = ReBuildcraft.id("block/" + name + "_flow");
        return new IClientFluidTypeExtensions() {
            @Override
            public ResourceLocation getStillTexture() {
                return still;
            }

            @Override
            public ResourceLocation getFlowingTexture() {
                return flowing;
            }
        };
    }
}
