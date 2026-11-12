package com.takoy3466.re_buildcraft.registry;

import com.takoy3466.re_buildcraft.ReBuildcraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * クリエイティブタブ。登録済みの全アイテムを登録順に並べる。
 * (1.2.5 には Mod 用タブが無く、各ブロックの addCreativeItems で既存タブに追加していた)
 */
public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ReBuildcraft.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = CREATIVE_MODE_TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + ReBuildcraft.MODID))
                    .icon(() -> ModItems.DIAMOND_GEAR.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        for (var entry : ModItems.ITEMS.getEntries()) {
                            output.accept(entry.get());
                        }
                    })
                    .build());

    private ModCreativeTabs() {
    }

    public static void register(IEventBus modEventBus) {
        CREATIVE_MODE_TABS.register(modEventBus);
    }
}
