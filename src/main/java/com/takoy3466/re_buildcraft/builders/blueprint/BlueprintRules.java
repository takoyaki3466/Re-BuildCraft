package com.takoy3466.re_buildcraft.builders.blueprint;

import java.util.List;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.material.FluidState;

/**
 * 設計図の 1 マスをどう建てるか。元実装: {@code buildcraft.api.BptBlock} と、ブロックごとの {@code BptBlock*} (約 40 種)。
 * <p>
 * 旧実装はブロック ID ごとに処理クラスを登録していた。1.21.1 ではブロックステートとそのプロパティで同じ判断ができるため、
 * 次の規則にまとめた。
 * <ul>
 *   <li>必要なアイテム: ブロックのアイテム 1 個 (旧 addRequirements の既定)。2 枚重ねのハーフブロックは 2 個 (旧 BptBlockMask の stairDouble)。
 *       液体の水源はその液体のバケツ (旧 BptBlockLiquid)。</li>
 *   <li>建てない (旧 ignoreBuilding / BptBlockIgnore): 流れている液体、アイテムとして入手できないブロック (ピストンの頭・ポータル・火など)、
 *       2 マスのブロックの上半分とベッドの頭側 (旧 BptBlockDoor / BptBlockBed は下半分・足側を置くときに両方置く)。</li>
 *   <li>建て終わりの判定: 同じブロックであること (旧 isValid の既定: ブロック ID の一致)。</li>
 *   <li>先に建てるもの: 光を遮る立方体 (旧 primaryList: isOpaqueCube)。それ以外 (松明など支えの要るもの) は後。</li>
 * </ul>
 */
public final class BlueprintRules {
    private static final int DOUBLE_SLAB_COUNT = 2;

    private BlueprintRules() {
    }

    /** 建てる必要のないマスか (旧 ignoreBuilding)。空気は「建てない」のではなく「空気にする」ため対象外。 */
    public static boolean ignore(BlockState state) {
        if (state.isAir()) {
            return false;
        }
        if (state.getBlock() instanceof LiquidBlock) {
            return !state.getFluidState().isSource();
        }
        if (isCompanion(state)) {
            return true;
        }
        return state.getBlock().asItem() == Items.AIR;
    }

    /** 下半分・足側と一緒に置かれる側 (2 マスのブロックの上半分、ベッドの頭側)。 */
    public static boolean isCompanion(BlockState state) {
        return state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER
                || state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.HEAD;
    }

    /** 置くのに必要なアイテム。空気は何も要らない。 */
    public static List<ItemStack> requirements(BlockState state) {
        if (state.isAir() || ignore(state)) {
            return List.of();
        }
        FluidState fluid = state.getFluidState();
        if (state.getBlock() instanceof LiquidBlock) {
            Item bucket = fluid.getType().getBucket();
            return bucket == Items.AIR ? List.of() : List.of(new ItemStack(bucket));
        }
        int count = state.getBlock() instanceof SlabBlock && state.getValue(SlabBlock.TYPE) == SlabType.DOUBLE ? DOUBLE_SLAB_COUNT : 1;
        return List.of(new ItemStack(state.getBlock().asItem(), count));
    }

    /** 先に建てる (旧 primaryList) か。 */
    public static boolean isPrimary(BlockState state) {
        return !state.isAir() && state.canOcclude();
    }
}
