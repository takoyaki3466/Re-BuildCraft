package com.takoy3466.re_buildcraft.transport;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

/**
 * 構造パイプ (砂利パイプ)。何も運ばず、構造パイプ同士とだけつながってワイヤーの信号を中継する。
 * 元実装: {@code buildcraft.transport.PipeTransportStructure}。
 */
public final class StructureTransport extends PipeTransport {

    StructureTransport(PipeBlockEntity pipe) {
        super(pipe);
    }

    @Override
    public void tick(ServerLevel level) {
        // 運ぶものが無い。
    }

    @Override
    public boolean isEmpty() {
        return true;
    }

    @Override
    public CompoundTag save(HolderLookup.Provider registries) {
        return new CompoundTag();
    }

    @Override
    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        // 保存する状態が無い。
    }
}
