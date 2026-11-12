package com.takoy3466.re_buildcraft.registry;

import com.takoy3466.re_buildcraft.ReBuildcraft;
import com.takoy3466.re_buildcraft.api.engine.EngineBlock;
import com.takoy3466.re_buildcraft.api.engine.RedstoneEngineLogic;
import com.takoy3466.re_buildcraft.api.laser.LaserBlock;
import com.takoy3466.re_buildcraft.api.quarry.QuarryBlock;
import com.takoy3466.re_buildcraft.builders.FillerBlock;
import com.takoy3466.re_buildcraft.builders.MarkerBlock;
import com.takoy3466.re_buildcraft.energy.CombustionEngineLogic;
import com.takoy3466.re_buildcraft.energy.SteamEngineLogic;
import com.takoy3466.re_buildcraft.factory.AutoWorkbenchBlock;
import com.takoy3466.re_buildcraft.factory.FrameBlock;
import com.takoy3466.re_buildcraft.factory.MiningPipeBlock;
import com.takoy3466.re_buildcraft.factory.MiningWellBlock;
import com.takoy3466.re_buildcraft.factory.PumpBlock;
import com.takoy3466.re_buildcraft.factory.RefineryBlock;
import com.takoy3466.re_buildcraft.factory.TankBlock;
import com.takoy3466.re_buildcraft.silicon.AssemblyTableBlock;
import com.takoy3466.re_buildcraft.transport.DirectionalPipeBlock;
import com.takoy3466.re_buildcraft.transport.PipeBlock;
import com.takoy3466.re_buildcraft.transport.PipeType;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Re-BuildCraft のブロック登録。 */
public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ReBuildcraft.MODID);

    /** BCx の設定項目 "MachineHardness" の既定値。 */
    private static final float MACHINE_HARDNESS = 1.5F;
    private static final float MINING_WELL_RESISTANCE = 10.0F;
    private static final float TANK_HARDNESS = 0.5F;
    private static final float OIL_HARDNESS = 100.0F;
    private static final float FRAME_HARDNESS = 0.5F;
    /** 旧 BlockMarker.setLightValue(0.5F)。 */
    private static final int MARKER_LIGHT = 7;
    private static final float AUTO_WORKBENCH_HARDNESS = 1.0F;

    // ---- energy ----
    public static final DeferredBlock<EngineBlock> REDSTONE_ENGINE = BLOCKS.register("redstone_engine",
            () -> new EngineBlock(machine().noOcclusion(), ModBlockEntities.ENGINE, RedstoneEngineLogic::new));
    public static final DeferredBlock<EngineBlock> STEAM_ENGINE = BLOCKS.register("steam_engine",
            () -> new EngineBlock(machine().noOcclusion(), ModBlockEntities.ENGINE, SteamEngineLogic::new));
    public static final DeferredBlock<EngineBlock> COMBUSTION_ENGINE = BLOCKS.register("combustion_engine",
            () -> new EngineBlock(machine().noOcclusion(), ModBlockEntities.ENGINE, CombustionEngineLogic::new));
    public static final DeferredBlock<LiquidBlock> OIL = BLOCKS.register("oil",
            () -> new LiquidBlock(ModFluids.OIL.get(), BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .replaceable()
                    .noCollission()
                    .strength(OIL_HARDNESS)
                    .pushReaction(PushReaction.DESTROY)
                    .noLootTable()
                    .liquid()));

    // ---- factory ----
    public static final DeferredBlock<MiningWellBlock> MINING_WELL = BLOCKS.register("mining_well",
            () -> new MiningWellBlock(machine().strength(MACHINE_HARDNESS, MINING_WELL_RESISTANCE).sound(SoundType.STONE)));
    public static final DeferredBlock<MiningPipeBlock> MINING_PIPE = BLOCKS.register("mining_pipe",
            () -> new MiningPipeBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.NONE)
                    .sound(SoundType.GLASS)
                    .noOcclusion()
                    .noLootTable()));
    public static final DeferredBlock<TankBlock> TANK = BLOCKS.register("tank",
            () -> new TankBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.NONE)
                    .strength(TANK_HARDNESS)
                    .sound(SoundType.GLASS)
                    .noOcclusion()));
    public static final DeferredBlock<QuarryBlock> QUARRY = BLOCKS.register("quarry",
            () -> new QuarryBlock(machine().strength(MACHINE_HARDNESS, MINING_WELL_RESISTANCE).sound(SoundType.STONE),
                    ModBlockEntities.QUARRY));
    public static final DeferredBlock<FrameBlock> FRAME = BLOCKS.register("frame",
            () -> new FrameBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.NONE)
                    .strength(FRAME_HARDNESS)
                    .sound(SoundType.GLASS)
                    .noOcclusion()
                    .noLootTable()));
    /** 旧 BlockAutoWorkbench: Material.wood, hardness 1.0。 */
    public static final DeferredBlock<AutoWorkbenchBlock> AUTO_WORKBENCH = BLOCKS.register("auto_workbench",
            () -> new AutoWorkbenchBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(AUTO_WORKBENCH_HARDNESS)
                    .sound(SoundType.WOOD)
                    .ignitedByLava()));
    /** 旧 BlockRefinery: Material.glass, hardness 0.5。 */
    public static final DeferredBlock<RefineryBlock> REFINERY = BLOCKS.register("refinery",
            () -> new RefineryBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.NONE)
                    .strength(TANK_HARDNESS)
                    .sound(SoundType.GLASS)
                    .noOcclusion()));
    public static final DeferredBlock<PumpBlock> PUMP = BLOCKS.register("pump", () -> new PumpBlock(machine()));

    // ---- builders ----
    public static final DeferredBlock<MarkerBlock> MARKER = BLOCKS.register("marker",
            () -> new MarkerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.NONE)
                    .noCollission()
                    .instabreak()
                    .lightLevel(state -> MARKER_LIGHT)
                    .sound(SoundType.WOOD)
                    .pushReaction(PushReaction.DESTROY)));

    public static final DeferredBlock<FillerBlock> FILLER = BLOCKS.register("filler",
            () -> new FillerBlock(machine()));

    // ---- silicon ----
    public static final DeferredBlock<LaserBlock> LASER = BLOCKS.register("laser",
            () -> new LaserBlock(machine().noOcclusion(), ModBlockEntities.LASER));
    public static final DeferredBlock<AssemblyTableBlock> ASSEMBLY_TABLE = BLOCKS.register("assembly_table",
            () -> new AssemblyTableBlock(machine().noOcclusion()));

    // ---- transport ----
    private static final Map<PipeType, DeferredBlock<PipeBlock>> PIPES = new EnumMap<>(PipeType.class);

    static {
        for (PipeType type : PipeType.values()) {
            PIPES.put(type, BLOCKS.register(type.id(), () -> type.directional()
                    ? new DirectionalPipeBlock(pipeProperties(), type)
                    : new PipeBlock(pipeProperties(), type)));
        }
    }

    private ModBlocks() {
    }

    /** 旧 BlockGenericPipe: ガラス素材・硬さ 0 (すぐ壊れる)。 */
    private static BlockBehaviour.Properties pipeProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.NONE)
                .instabreak()
                .noOcclusion();
    }

    public static Map<PipeType, DeferredBlock<PipeBlock>> pipes() {
        return Collections.unmodifiableMap(PIPES);
    }

    /** 鉄素材の機械に共通する設定 (旧 {@code Material.iron} + machineHardness)。 */
    private static BlockBehaviour.Properties machine() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(MACHINE_HARDNESS)
                .sound(SoundType.METAL)
                .requiresCorrectToolForDrops();
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
