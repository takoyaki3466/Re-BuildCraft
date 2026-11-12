package com.takoy3466.re_buildcraft.transport;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * パイプの輸送処理の共通部分。元実装: {@code buildcraft.transport.PipeTransport}。
 */
public abstract class PipeTransport {
    protected final PipeBlockEntity pipe;

    protected PipeTransport(PipeBlockEntity pipe) {
        this.pipe = pipe;
    }

    /** 輸送方式に応じた実装を作る。 */
    public static PipeTransport create(PipeBlockEntity pipe, PipeType type) {
        return switch (type.kind()) {
            case ITEMS -> new ItemTransport(pipe);
            case FLUIDS -> new FluidTransport(pipe, type.fluidFlowRate());
            case POWER -> new PowerTransport(pipe);
            case STRUCTURE -> new StructureTransport(pipe);
        };
    }

    public abstract void tick(ServerLevel level);

    /** クライアント側の見た目だけの更新。 */
    public void clientTick() {
    }

    public void onNeighborChanged() {
    }

    /** 中身が何も無いか (ゲートのトリガー「パイプが空」)。 */
    public abstract boolean isEmpty();

    /** 撤去時に中身を落とす。 */
    public void dropContents(Level level, BlockPos pos) {
    }

    public abstract CompoundTag save(HolderLookup.Provider registries);

    public abstract void load(CompoundTag tag, HolderLookup.Provider registries);

    /** クライアントへ送る表示用の状態。既定では保存内容と同じ。 */
    public CompoundTag saveForClient(HolderLookup.Provider registries) {
        return save(registries);
    }
}
