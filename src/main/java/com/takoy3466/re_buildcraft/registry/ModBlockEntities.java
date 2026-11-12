package com.takoy3466.re_buildcraft.registry;

import com.takoy3466.re_buildcraft.ReBuildcraft;
import com.takoy3466.re_buildcraft.api.engine.EngineBlockEntity;
import com.takoy3466.re_buildcraft.api.laser.LaserBlockEntity;
import com.takoy3466.re_buildcraft.api.laser.LaserSpec;
import com.takoy3466.re_buildcraft.api.quarry.QuarryBlockEntity;
import com.takoy3466.re_buildcraft.builders.FillerBlockEntity;
import com.takoy3466.re_buildcraft.builders.MarkerBlockEntity;
import com.takoy3466.re_buildcraft.factory.AutoWorkbenchBlockEntity;
import com.takoy3466.re_buildcraft.factory.MiningWellBlockEntity;
import com.takoy3466.re_buildcraft.factory.PumpBlockEntity;
import com.takoy3466.re_buildcraft.factory.RefineryBlockEntity;
import com.takoy3466.re_buildcraft.factory.TankBlockEntity;
import com.takoy3466.re_buildcraft.silicon.AssemblyTableBlockEntity;
import com.takoy3466.re_buildcraft.transport.PipeBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Re-BuildCraft のブロックエンティティ登録。 */
public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ReBuildcraft.MODID);

    /** 木・石・鉄エンジン共用。種類ごとの違いはブロックが持つ EngineLogic のファクトリで決まる。 */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EngineBlockEntity>> ENGINE =
            BLOCK_ENTITY_TYPES.register("engine", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new EngineBlockEntity(ModBlockEntities.ENGINE.get(), pos, state),
                    ModBlocks.REDSTONE_ENGINE.get(), ModBlocks.STEAM_ENGINE.get(), ModBlocks.COMBUSTION_ENGINE.get()
            ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MiningWellBlockEntity>> MINING_WELL =
            BLOCK_ENTITY_TYPES.register("mining_well", () -> BlockEntityType.Builder.of(
                    MiningWellBlockEntity::new, ModBlocks.MINING_WELL.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<QuarryBlockEntity>> QUARRY =
            BLOCK_ENTITY_TYPES.register("quarry", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new QuarryBlockEntity(ModBlockEntities.QUARRY.get(), pos, state),
                    ModBlocks.QUARRY.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TankBlockEntity>> TANK =
            BLOCK_ENTITY_TYPES.register("tank", () -> BlockEntityType.Builder.of(
                    TankBlockEntity::new, ModBlocks.TANK.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AutoWorkbenchBlockEntity>> AUTO_WORKBENCH =
            BLOCK_ENTITY_TYPES.register("auto_workbench", () -> BlockEntityType.Builder.of(
                    AutoWorkbenchBlockEntity::new, ModBlocks.AUTO_WORKBENCH.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RefineryBlockEntity>> REFINERY =
            BLOCK_ENTITY_TYPES.register("refinery", () -> BlockEntityType.Builder.of(
                    RefineryBlockEntity::new, ModBlocks.REFINERY.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PumpBlockEntity>> PUMP =
            BLOCK_ENTITY_TYPES.register("pump", () -> BlockEntityType.Builder.of(
                    PumpBlockEntity::new, ModBlocks.PUMP.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LaserBlockEntity>> LASER =
            BLOCK_ENTITY_TYPES.register("laser", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new LaserBlockEntity(ModBlockEntities.LASER.get(), pos, state, LaserSpec.STANDARD),
                    ModBlocks.LASER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AssemblyTableBlockEntity>> ASSEMBLY_TABLE =
            BLOCK_ENTITY_TYPES.register("assembly_table", () -> BlockEntityType.Builder.of(
                    AssemblyTableBlockEntity::new, ModBlocks.ASSEMBLY_TABLE.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MarkerBlockEntity>> MARKER =
            BLOCK_ENTITY_TYPES.register("marker", () -> BlockEntityType.Builder.of(
                    MarkerBlockEntity::new, ModBlocks.MARKER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FillerBlockEntity>> FILLER =
            BLOCK_ENTITY_TYPES.register("filler", () -> BlockEntityType.Builder.of(
                    FillerBlockEntity::new, ModBlocks.FILLER.get()).build(null));

    /** 全パイプ共用。輸送方式と素材はブロック (PipeType) から決まる。 */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PipeBlockEntity>> PIPE =
            BLOCK_ENTITY_TYPES.register("pipe", () -> BlockEntityType.Builder.of(PipeBlockEntity::new,
                    ModBlocks.pipes().values().stream().map(DeferredBlock::get).toArray(Block[]::new)).build(null));

    private ModBlockEntities() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITY_TYPES.register(modEventBus);
    }
}
