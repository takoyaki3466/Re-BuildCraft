package com.takoy3466.re_buildcraft.transport;

import com.takoy3466.re_buildcraft.transport.behavior.DiamondFilterBehavior;
import com.takoy3466.re_buildcraft.transport.behavior.GoldItemBehavior;
import com.takoy3466.re_buildcraft.transport.behavior.IronOutputBehavior;
import com.takoy3466.re_buildcraft.transport.behavior.ObsidianSuctionBehavior;
import com.takoy3466.re_buildcraft.transport.behavior.PipeBehavior;
import com.takoy3466.re_buildcraft.transport.behavior.StripesBehavior;
import com.takoy3466.re_buildcraft.transport.behavior.VoidItemBehavior;
import com.takoy3466.re_buildcraft.transport.behavior.WoodFluidExtractionBehavior;
import com.takoy3466.re_buildcraft.transport.behavior.WoodItemExtractionBehavior;
import com.takoy3466.re_buildcraft.transport.behavior.WoodPowerBehavior;
import java.util.function.Supplier;

/**
 * BCx 3.1.5.17 に登録されていたパイプの一覧 ({@code BuildCraftTransport.createPipe} の呼び出し)。
 *
 * @param fluidFlowRate 液体パイプの 1 tick あたりの流量 (旧 PipeTransportLiquids.flowRate)
 */
public enum PipeType implements PipeSpec {
    WOODEN_TRANSPORT("wooden_transport_pipe", PipeKind.ITEMS, PipeMaterial.WOOD, true, WoodItemExtractionBehavior::new),
    COBBLESTONE_TRANSPORT("cobblestone_transport_pipe", PipeKind.ITEMS, PipeMaterial.COBBLESTONE),
    STONE_TRANSPORT("stone_transport_pipe", PipeKind.ITEMS, PipeMaterial.STONE),
    IRON_TRANSPORT("iron_transport_pipe", PipeKind.ITEMS, PipeMaterial.IRON, true, IronOutputBehavior::new),
    GOLDEN_TRANSPORT("golden_transport_pipe", PipeKind.ITEMS, PipeMaterial.GOLD, false, GoldItemBehavior::new),
    DIAMOND_TRANSPORT("diamond_transport_pipe", PipeKind.ITEMS, PipeMaterial.DIAMOND, false, DiamondFilterBehavior::new),
    OBSIDIAN_TRANSPORT("obsidian_transport_pipe", PipeKind.ITEMS, PipeMaterial.OBSIDIAN, false, ObsidianSuctionBehavior::new),
    STRIPES_TRANSPORT("stripes_transport_pipe", PipeKind.ITEMS, PipeMaterial.STRIPES, false, StripesBehavior::new),
    VOID_TRANSPORT("void_transport_pipe", PipeKind.ITEMS, PipeMaterial.VOID, false, VoidItemBehavior::new),
    WOODEN_WATERPROOF("wooden_waterproof_pipe", PipeKind.FLUIDS, PipeMaterial.WOOD, true, WoodFluidExtractionBehavior::new, 1000),
    COBBLESTONE_WATERPROOF("cobblestone_waterproof_pipe", PipeKind.FLUIDS, PipeMaterial.COBBLESTONE),
    STONE_WATERPROOF("stone_waterproof_pipe", PipeKind.FLUIDS, PipeMaterial.STONE),
    IRON_WATERPROOF("iron_waterproof_pipe", PipeKind.FLUIDS, PipeMaterial.IRON, true, IronOutputBehavior::new, 250),
    GOLDEN_WATERPROOF("golden_waterproof_pipe", PipeKind.FLUIDS, PipeMaterial.GOLD, false, () -> PipeBehavior.NONE, 250),
    WOODEN_CONDUCTIVE("wooden_conductive_pipe", PipeKind.POWER, PipeMaterial.WOOD, true, WoodPowerBehavior::new),
    COBBLESTONE_CONDUCTIVE("cobblestone_conductive_pipe", PipeKind.POWER, PipeMaterial.COBBLESTONE),
    STONE_CONDUCTIVE("stone_conductive_pipe", PipeKind.POWER, PipeMaterial.STONE),
    GOLDEN_CONDUCTIVE("golden_conductive_pipe", PipeKind.POWER, PipeMaterial.GOLD),
    COBBLESTONE_STRUCTURE("cobblestone_structure_pipe", PipeKind.STRUCTURE, PipeMaterial.COBBLESTONE);

    /** 旧 PipeTransportLiquids の既定流量。 */
    private static final int DEFAULT_FLOW_RATE = 50;

    private final String id;
    private final PipeKind kind;
    private final PipeMaterial material;
    private final boolean directional;
    private final Supplier<? extends PipeBehavior> behaviorFactory;
    private final int fluidFlowRate;

    PipeType(String id, PipeKind kind, PipeMaterial material) {
        this(id, kind, material, false, () -> PipeBehavior.NONE);
    }

    PipeType(String id, PipeKind kind, PipeMaterial material, boolean directional, Supplier<? extends PipeBehavior> behaviorFactory) {
        this(id, kind, material, directional, behaviorFactory, DEFAULT_FLOW_RATE);
    }

    PipeType(String id, PipeKind kind, PipeMaterial material, boolean directional,
             Supplier<? extends PipeBehavior> behaviorFactory, int fluidFlowRate) {
        this.id = id;
        this.kind = kind;
        this.material = material;
        this.directional = directional;
        this.behaviorFactory = behaviorFactory;
        this.fluidFlowRate = fluidFlowRate;
    }

    /** レジストリ名。 */
    @Override
    public String id() {
        return id;
    }

    @Override
    public PipeKind kind() {
        return kind;
    }

    @Override
    public PipeMaterial material() {
        return material;
    }

    /** 取り出し元/出力先の面をブロックステートに持つか (木・鉄)。 */
    @Override
    public boolean directional() {
        return directional;
    }

    @Override
    public PipeBehavior createBehavior() {
        return behaviorFactory.get();
    }

    @Override
    public int fluidFlowRate() {
        return fluidFlowRate;
    }
}
