package com.takoy3466.re_buildcraft.api.power;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * MJ を受け取って動く機械の共通基底。{@link PowerProvider} の保持・保存とケーパビリティ公開を受け持つ。
 * 元実装: {@code TileBuildCraft} の IPowerReceptor 関連部分 + {@code PowerFramework.load/savePowerProvider}。
 */
public abstract class PoweredBlockEntity extends BlockEntity implements IPowerReceptor {
    private static final String TAG_POWER = "power";

    protected final PowerProvider powerProvider = new PowerProvider();

    protected PoweredBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** この型のブロックエンティティを MJ の受け手として公開する。 */
    public static <E extends PoweredBlockEntity> void registerCapabilities(RegisterCapabilitiesEvent event, BlockEntityType<E> type) {
        event.registerBlockEntity(BuildCraftCapabilities.POWER_RECEPTOR, type, (machine, side) -> machine);
    }

    @Override
    public PowerProvider getPowerProvider() {
        return powerProvider;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        CompoundTag powerTag = new CompoundTag();
        powerProvider.save(powerTag);
        tag.put(TAG_POWER, powerTag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        powerProvider.load(tag.getCompound(TAG_POWER));
    }
}
