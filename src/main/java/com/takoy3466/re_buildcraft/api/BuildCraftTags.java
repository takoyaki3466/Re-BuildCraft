package com.takoy3466.re_buildcraft.api;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/** Re-BuildCraft が参照するタグ。 */
public final class BuildCraftTags {
    private static final String COMMON_NAMESPACE = "c";

    /**
     * 機械の向きを変えられるレンチ。旧実装は {@code BuildCraftCore.wrenchItem} との同一性で判定していたが、
     * 共通タグ {@code c:tools/wrench} にすることで他 Mod のレンチとも連携できる。
     */
    public static final TagKey<Item> WRENCHES =
            TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(COMMON_NAMESPACE, "tools/wrench"));

    private BuildCraftTags() {
    }
}
