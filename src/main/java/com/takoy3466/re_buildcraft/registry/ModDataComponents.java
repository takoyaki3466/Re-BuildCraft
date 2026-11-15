package com.takoy3466.re_buildcraft.registry;

import com.takoy3466.re_buildcraft.ReBuildcraft;
import com.takoy3466.re_buildcraft.builders.blueprint.BlueprintRef;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Re-BuildCraft のアイテムデータコンポーネント。
 */
public final class ModDataComponents {
    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, ReBuildcraft.MODID);

    /**
     * テンプレート・設計図アイテムが指す設計図。無ければ白紙。
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BlueprintRef>> BLUEPRINT =
            DATA_COMPONENTS.registerComponentType("blueprint", builder -> builder
                    .persistent(BlueprintRef.CODEC)
                    .networkSynchronized(BlueprintRef.STREAM_CODEC));

    private ModDataComponents() {
    }

    public static void register(IEventBus modEventBus) {
        DATA_COMPONENTS.register(modEventBus);
    }
}
