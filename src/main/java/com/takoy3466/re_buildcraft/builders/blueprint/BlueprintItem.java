package com.takoy3466.re_buildcraft.builders.blueprint;

import com.takoy3466.re_buildcraft.registry.ModDataComponents;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;

/**
 * テンプレート・設計図のアイテム。元実装: {@code ItemBptBase} / {@code ItemBptTemplate} / {@code ItemBptBluePrint}。
 * 白紙か記録済みかで見た目が変わり (旧 getIconFromDamage)、記録済みなら説明文に設計図の名前を出す。
 */
public class BlueprintItem extends Item {
    private final Blueprint.Kind kind;

    public BlueprintItem(Properties properties, Blueprint.Kind kind) {
        super(properties);
        this.kind = kind;
    }

    public Blueprint.Kind kind() {
        return kind;
    }

    @Nullable
    public static BlueprintRef ref(ItemStack stack) {
        return stack.get(ModDataComponents.BLUEPRINT);
    }

    /** 同じ種類のアイテムで、指定の設計図を指すもの (白紙なら ref = null)。 */
    public ItemStack withBlueprint(@Nullable BlueprintRef ref) {
        ItemStack stack = new ItemStack(this);
        if (ref != null) {
            stack.set(ModDataComponents.BLUEPRINT, ref);
        }
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        BlueprintRef ref = ref(stack);
        if (ref != null) {
            tooltip.add(Component.literal(ref.name()).withStyle(ChatFormatting.GRAY));
        }
    }
}
