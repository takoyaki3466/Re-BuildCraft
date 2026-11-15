package com.takoy3466.re_buildcraft.registry;

import com.takoy3466.re_buildcraft.ReBuildcraft;
import com.takoy3466.re_buildcraft.builders.ArchitectMenu;
import com.takoy3466.re_buildcraft.builders.BuilderMenu;
import com.takoy3466.re_buildcraft.builders.FillerMenu;
import com.takoy3466.re_buildcraft.builders.LibraryMenu;
import com.takoy3466.re_buildcraft.energy.CombustionEngineLogic;
import com.takoy3466.re_buildcraft.energy.EngineMenu;
import com.takoy3466.re_buildcraft.energy.SteamEngineLogic;
import com.takoy3466.re_buildcraft.factory.AutoWorkbenchMenu;
import com.takoy3466.re_buildcraft.factory.RefineryMenu;
import com.takoy3466.re_buildcraft.silicon.AssemblyTableMenu;
import com.takoy3466.re_buildcraft.transport.DiamondFilterMenu;
import com.takoy3466.re_buildcraft.transport.gate.GateMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Re-BuildCraft の GUI メニュー登録。
 */
public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, ReBuildcraft.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<EngineMenu>> STEAM_ENGINE = MENUS.register("steam_engine", () -> IMenuTypeExtension.create((containerId, playerInventory, extraData) -> EngineMenu.createClient(ModMenus.STEAM_ENGINE.get(), containerId, playerInventory, extraData, SteamEngineLogic.DATA_COUNT, SteamEngineLogic.FUEL_SLOT_X)));
    public static final DeferredHolder<MenuType<?>, MenuType<EngineMenu>> COMBUSTION_ENGINE = MENUS.register("combustion_engine", () -> IMenuTypeExtension.create((containerId, playerInventory, extraData) -> EngineMenu.createClient(ModMenus.COMBUSTION_ENGINE.get(), containerId, playerInventory, extraData, CombustionEngineLogic.DATA_COUNT, CombustionEngineLogic.FUEL_SLOT_X)));
    public static final DeferredHolder<MenuType<?>, MenuType<AssemblyTableMenu>> ASSEMBLY_TABLE = MENUS.register("assembly_table", () -> IMenuTypeExtension.create(AssemblyTableMenu::createClient));
    public static final DeferredHolder<MenuType<?>, MenuType<GateMenu>> GATE = MENUS.register("gate", () -> IMenuTypeExtension.create(GateMenu::createClient));
    public static final DeferredHolder<MenuType<?>, MenuType<DiamondFilterMenu>> DIAMOND_PIPE = MENUS.register("diamond_pipe", () -> IMenuTypeExtension.create(DiamondFilterMenu::createClient));
    public static final DeferredHolder<MenuType<?>, MenuType<RefineryMenu>> REFINERY = MENUS.register("refinery", () -> IMenuTypeExtension.create(RefineryMenu::createClient));
    public static final DeferredHolder<MenuType<?>, MenuType<AutoWorkbenchMenu>> AUTO_WORKBENCH = MENUS.register("auto_workbench", () -> IMenuTypeExtension.create(AutoWorkbenchMenu::createClient));
    public static final DeferredHolder<MenuType<?>, MenuType<FillerMenu>> FILLER = MENUS.register("filler", () -> IMenuTypeExtension.create(FillerMenu::createClient));
    public static final DeferredHolder<MenuType<?>, MenuType<ArchitectMenu>> ARCHITECT = MENUS.register("architect", () -> IMenuTypeExtension.create(ArchitectMenu::createClient));
    public static final DeferredHolder<MenuType<?>, MenuType<BuilderMenu>> BUILDER = MENUS.register("builder", () -> IMenuTypeExtension.create(BuilderMenu::createClient));
    public static final DeferredHolder<MenuType<?>, MenuType<LibraryMenu>> LIBRARY = MENUS.register("blueprint_library", () -> IMenuTypeExtension.create(LibraryMenu::createClient));

    private ModMenus() {
    }

    public static void register(IEventBus modEventBus) {
        MENUS.register(modEventBus);
    }
}
