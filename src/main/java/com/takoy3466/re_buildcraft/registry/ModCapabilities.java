package com.takoy3466.re_buildcraft.registry;

import com.takoy3466.re_buildcraft.api.engine.EngineBlockEntity;
import com.takoy3466.re_buildcraft.api.power.BuildCraftCapabilities;
import com.takoy3466.re_buildcraft.api.power.PoweredBlockEntity;
import com.takoy3466.re_buildcraft.transport.FluidTransport;
import com.takoy3466.re_buildcraft.transport.PowerTransport;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.capability.wrappers.FluidBucketWrapper;

/**
 * ブロックエンティティ・アイテムのケーパビリティ登録。
 * 旧実装の {@code IPowerReceptor} / {@code ILiquidContainer} / {@code IInventory} の実装判定を置き換える。
 */
public final class ModCapabilities {

    private ModCapabilities() {
    }

    public static void register(RegisterCapabilitiesEvent event) {
        EngineBlockEntity.registerCapabilities(event, ModBlockEntities.ENGINE.get());
        PoweredBlockEntity.registerCapabilities(event, ModBlockEntities.MINING_WELL.get());
        PoweredBlockEntity.registerCapabilities(event, ModBlockEntities.PUMP.get());
        PoweredBlockEntity.registerCapabilities(event, ModBlockEntities.LASER.get());
        PoweredBlockEntity.registerCapabilities(event, ModBlockEntities.QUARRY.get());
        PoweredBlockEntity.registerCapabilities(event, ModBlockEntities.REFINERY.get());
        PoweredBlockEntity.registerCapabilities(event, ModBlockEntities.FILLER.get());
        PoweredBlockEntity.registerCapabilities(event, ModBlockEntities.BUILDER.get());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.BUILDER.get(),
                (builder, side) -> builder.getMaterials());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.ARCHITECT.get(),
                (architect, side) -> architect.getInventory());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.LIBRARY.get(),
                (library, side) -> library.getInventory());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.FILLER.get(),
                (filler, side) -> filler.getResources());

        // パイプ: 液体パイプは面ごとの液体ハンドラ、木・黒曜石パイプは MJ の受け手を公開する。
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ModBlockEntities.PIPE.get(),
                (pipe, side) -> side != null && pipe.transport() instanceof FluidTransport fluids ? fluids.handler(side) : null);
        event.registerBlockEntity(BuildCraftCapabilities.POWER_RECEPTOR, ModBlockEntities.PIPE.get(),
                (pipe, side) -> pipe.behavior().powerProvider() != null ? pipe : null);
        event.registerBlockEntity(BuildCraftCapabilities.POWER_CONDUIT, ModBlockEntities.PIPE.get(),
                (pipe, side) -> pipe.transport() instanceof PowerTransport power ? power : null);

        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ModBlockEntities.TANK.get(),
                (tank, side) -> tank.getFluidHandler());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.AUTO_WORKBENCH.get(),
                (workbench, side) -> workbench.getAutomationHandler());
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ModBlockEntities.REFINERY.get(),
                (refinery, side) -> refinery.getFluidHandler());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.ASSEMBLY_TABLE.get(),
                (table, side) -> table.getInventory());

        // NeoForge は BucketItem そのもののクラスにしか液体ケーパビリティを自動登録しないため、
        // 設置できない金オイルバケツには明示的に登録する。黒オイルバケツは BucketItem なので自動登録される。
        event.registerItem(Capabilities.FluidHandler.ITEM, (stack, context) -> new FluidBucketWrapper(stack),
                ModItems.FUEL_BUCKET.get());
    }
}
