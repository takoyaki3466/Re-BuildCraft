package com.takoy3466.re_buildcraft.registry;

import com.takoy3466.re_buildcraft.ReBuildcraft;
import com.takoy3466.re_buildcraft.builders.filler.FillerPatterns;
import com.takoy3466.re_buildcraft.core.WrenchItem;
import com.takoy3466.re_buildcraft.energy.ContainerOnlyBucketItem;
import com.takoy3466.re_buildcraft.transport.PipeType;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Re-BuildCraft のアイテム登録。 */
public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ReBuildcraft.MODID);

    // ---- core ----
    public static final DeferredItem<Item> WOODEN_GEAR = ITEMS.registerSimpleItem("wooden_gear");
    public static final DeferredItem<Item> STONE_GEAR = ITEMS.registerSimpleItem("stone_gear");
    public static final DeferredItem<Item> IRON_GEAR = ITEMS.registerSimpleItem("iron_gear");
    public static final DeferredItem<Item> GOLD_GEAR = ITEMS.registerSimpleItem("gold_gear");
    public static final DeferredItem<Item> DIAMOND_GEAR = ITEMS.registerSimpleItem("diamond_gear");
    public static final DeferredItem<WrenchItem> WRENCH = ITEMS.registerItem("wrench", WrenchItem::new, new Item.Properties().stacksTo(1));

    // ---- energy ----
    public static final DeferredItem<BucketItem> OIL_BUCKET = ITEMS.register("oil_bucket",
            () -> new BucketItem(ModFluids.OIL.get(), bucketProperties()));
    public static final DeferredItem<ContainerOnlyBucketItem> FUEL_BUCKET = ITEMS.register("fuel_bucket",
            () -> new ContainerOnlyBucketItem(ModFluids.FUEL.get(), bucketProperties()));

    // ---- silicon ----
    public static final DeferredItem<Item> REDSTONE_CHIPSET = ITEMS.registerSimpleItem("redstone_chipset");
    public static final DeferredItem<Item> IRON_CHIPSET = ITEMS.registerSimpleItem("iron_chipset");
    public static final DeferredItem<Item> GOLD_CHIPSET = ITEMS.registerSimpleItem("gold_chipset");
    public static final DeferredItem<Item> DIAMOND_CHIPSET = ITEMS.registerSimpleItem("diamond_chipset");

    // ---- transport ----
    public static final DeferredItem<Item> PIPE_WATERPROOF = ITEMS.registerSimpleItem("pipe_waterproof");
    public static final DeferredItem<Item> RED_PIPE_WIRE = ITEMS.registerSimpleItem("red_pipe_wire");
    public static final DeferredItem<Item> BLUE_PIPE_WIRE = ITEMS.registerSimpleItem("blue_pipe_wire");
    public static final DeferredItem<Item> GREEN_PIPE_WIRE = ITEMS.registerSimpleItem("green_pipe_wire");
    public static final DeferredItem<Item> YELLOW_PIPE_WIRE = ITEMS.registerSimpleItem("yellow_pipe_wire");
    public static final DeferredItem<Item> GATE = ITEMS.registerSimpleItem("gate");
    public static final DeferredItem<Item> IRON_AND_GATE = ITEMS.registerSimpleItem("iron_and_gate");
    public static final DeferredItem<Item> IRON_OR_GATE = ITEMS.registerSimpleItem("iron_or_gate");
    public static final DeferredItem<Item> GOLD_AND_GATE = ITEMS.registerSimpleItem("gold_and_gate");
    public static final DeferredItem<Item> GOLD_OR_GATE = ITEMS.registerSimpleItem("gold_or_gate");
    public static final DeferredItem<Item> DIAMOND_AND_GATE = ITEMS.registerSimpleItem("diamond_and_gate");
    public static final DeferredItem<Item> DIAMOND_OR_GATE = ITEMS.registerSimpleItem("diamond_or_gate");

    static {
        for (PipeType type : PipeType.values()) {
            ITEMS.registerSimpleBlockItem(ModBlocks.pipes().get(type));
        }
    }

    // ---- ブロックアイテム ----
    public static final DeferredItem<BlockItem> REDSTONE_ENGINE = ITEMS.registerSimpleBlockItem(ModBlocks.REDSTONE_ENGINE);
    public static final DeferredItem<BlockItem> STEAM_ENGINE = ITEMS.registerSimpleBlockItem(ModBlocks.STEAM_ENGINE);
    public static final DeferredItem<BlockItem> COMBUSTION_ENGINE = ITEMS.registerSimpleBlockItem(ModBlocks.COMBUSTION_ENGINE);
    public static final DeferredItem<BlockItem> MINING_WELL = ITEMS.registerSimpleBlockItem(ModBlocks.MINING_WELL);
    public static final DeferredItem<BlockItem> MINING_PIPE = ITEMS.registerSimpleBlockItem(ModBlocks.MINING_PIPE);
    public static final DeferredItem<BlockItem> QUARRY = ITEMS.registerSimpleBlockItem(ModBlocks.QUARRY);
    public static final DeferredItem<BlockItem> TANK = ITEMS.registerSimpleBlockItem(ModBlocks.TANK);
    public static final DeferredItem<BlockItem> PUMP = ITEMS.registerSimpleBlockItem(ModBlocks.PUMP);
    public static final DeferredItem<BlockItem> REFINERY = ITEMS.registerSimpleBlockItem(ModBlocks.REFINERY);
    public static final DeferredItem<BlockItem> MARKER = ITEMS.registerSimpleBlockItem(ModBlocks.MARKER);
    public static final DeferredItem<BlockItem> FILLER = ITEMS.registerSimpleBlockItem(ModBlocks.FILLER);

    /** フィラーモジュール 23 種 (旧 ItemFillerModule のダメージ値 0 - 22)。キーは旧ダメージ値。 */
    private static final Map<Integer, DeferredItem<Item>> FILLER_MODULES = new HashMap<>();

    static {
        for (FillerPatterns.Entry entry : FillerPatterns.ALL) {
            FILLER_MODULES.put(entry.id(), ITEMS.registerSimpleItem("filler_module_" + entry.name()));
        }
    }
    public static final DeferredItem<BlockItem> AUTO_WORKBENCH = ITEMS.registerSimpleBlockItem(ModBlocks.AUTO_WORKBENCH);
    public static final DeferredItem<BlockItem> LASER = ITEMS.registerSimpleBlockItem(ModBlocks.LASER);
    public static final DeferredItem<BlockItem> ASSEMBLY_TABLE = ITEMS.registerSimpleBlockItem(ModBlocks.ASSEMBLY_TABLE);

    private ModItems() {
    }

    private static Item.Properties bucketProperties() {
        return new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1);
    }

    public static DeferredItem<Item> fillerModule(int id) {
        return FILLER_MODULES.get(id);
    }

    public static boolean isFillerModule(ItemStack stack) {
        for (DeferredItem<Item> module : FILLER_MODULES.values()) {
            if (stack.is(module.get())) {
                return true;
            }
        }
        return false;
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
