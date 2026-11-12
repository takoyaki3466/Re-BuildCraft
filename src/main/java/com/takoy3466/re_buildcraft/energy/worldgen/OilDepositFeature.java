package com.takoy3466.re_buildcraft.energy.worldgen;

import com.mojang.serialization.Codec;
import com.takoy3466.re_buildcraft.Config;
import com.takoy3466.re_buildcraft.ReBuildcraft;
import com.takoy3466.re_buildcraft.registry.ModBlocks;
import com.takoy3466.re_buildcraft.registry.ModFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * 石油の生成。元実装: {@code buildcraft.energy.OilPopulate.doPopulate} (チャンクごとに 1 回)。
 * <ul>
 *   <li>{@link #DESERT_BIOMES} のバイオームでは 3% の確率で、砂の上に小さな油だまりを作る。</li>
 *   <li>0.15% の確率で中規模、0.005% の確率で大規模な地下の油の球を作り、地表まで油の柱を通して地表にも油だまりを作る。</li>
 * </ul>
 * 1.21.1 との差異:
 * <ul>
 *   <li>地物は生成中チャンクとその周囲 1 チャンクにしか書けない ({@code WorldGenRegion.ensureCanWrite})。
 *       大規模油田の地表の油だまり (半径最大 39) はこの範囲を越えるため、範囲外のブロックは置かない。</li>
 *   <li>地表の探索は旧ワールドの天井 (y=128) からではなく、生成時の地表の高さマップから始める。</li>
 *   <li>旧 {@code world.worldProvider.worldType == 0} (地上のみ) はバイオーム修飾子の対象 ({@code #minecraft:is_overworld}) で表す。</li>
 * </ul>
 */
public class OilDepositFeature extends Feature<NoneFeatureConfiguration> {
    /** 砂漠の油だまりを作るバイオーム (旧 BiomeGenBase.desert)。データパックで変更できる。 */
    public static final TagKey<Biome> DESERT_BIOMES = TagKey.create(Registries.BIOME, ReBuildcraft.id("has_desert_oil"));

    private static final float DESERT_CHANCE = 0.03F;
    private static final int DESERT_OFFSET_MIN = 2;
    private static final int DESERT_OFFSET_RANGE = 10;
    private static final int DESERT_MIN_Y = 65;
    private static final int DESERT_DEPOSIT_RADIUS = 3;

    private static final double MEDIUM_CHANCE = 0.0015;
    private static final double LARGE_CHANCE = 0.00005;
    private static final int WELL_MIN_Y = 20;
    private static final int WELL_Y_RANGE = 10;
    private static final int LARGE_RADIUS_MIN = 8;
    private static final int LARGE_RADIUS_RANGE = 9;
    private static final int MEDIUM_RADIUS_MIN = 4;
    private static final int MEDIUM_RADIUS_RANGE = 4;
    private static final int LARGE_DEPOSIT_MIN = 20;
    private static final int LARGE_DEPOSIT_RANGE = 20;
    private static final int MEDIUM_DEPOSIT_MIN = 5;
    private static final int MEDIUM_DEPOSIT_RANGE = 5;
    private static final int LARGE_SPOUT_HEIGHT = 30;
    private static final int MEDIUM_SPOUT_HEIGHT = 4;
    /** 地物が書き込める、生成中チャンクからのチャンク距離 (FEATURES 段階の blockStateWriteRadius)。 */
    private static final int WRITE_RADIUS_CHUNKS = 1;

    public OilDepositFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        if (!Config.MODIFY_WORLD.get()) {
            return false;
        }
        Generation generation = new Generation(context.level(), context.random(), new ChunkPos(context.origin()));
        boolean desert = generation.desertDeposit();
        boolean well = generation.well();
        return desert || well;
    }

    /** 1 チャンク分の生成処理。旧実装の静的メソッドが共有していた world / rand をまとめて持つ。 */
    private static final class Generation {
        private final WorldGenLevel level;
        private final RandomSource random;
        private final ChunkPos chunk;
        private final BlockState oil;
        private boolean placed;

        Generation(WorldGenLevel level, RandomSource random, ChunkPos chunk) {
            this.level = level;
            this.random = random;
            this.chunk = chunk;
            this.oil = ModBlocks.OIL.get().defaultBlockState();
        }

        boolean desertDeposit() {
            BlockPos biomePos = new BlockPos(chunk.getMinBlockX(), DESERT_MIN_Y, chunk.getMinBlockZ());
            if (!level.getBiome(biomePos).is(DESERT_BIOMES) || random.nextFloat() <= 1.0F - DESERT_CHANCE) {
                return false;
            }
            int x = chunk.getMinBlockX() + random.nextInt(DESERT_OFFSET_RANGE) + DESERT_OFFSET_MIN;
            int z = chunk.getMinBlockZ() + random.nextInt(DESERT_OFFSET_RANGE) + DESERT_OFFSET_MIN;
            for (int y = surfaceY(x, z); y > DESERT_MIN_Y; y--) {
                BlockState state = level.getBlockState(new BlockPos(x, y, z));
                if (!state.isAir()) {
                    if (state.is(Blocks.SAND)) {
                        surfaceDeposit(x, y, z, DESERT_DEPOSIT_RADIUS);
                    }
                    break;
                }
            }
            return placed;
        }

        boolean well() {
            boolean medium = random.nextDouble() <= MEDIUM_CHANCE;
            boolean large = random.nextDouble() <= LARGE_CHANCE;
            if (!medium && !large) {
                return false;
            }
            int x = chunk.getMinBlockX();
            int z = chunk.getMinBlockZ();
            int centerY = WELL_MIN_Y + random.nextInt(WELL_Y_RANGE);
            int radius = large ? LARGE_RADIUS_MIN + random.nextInt(LARGE_RADIUS_RANGE)
                    : MEDIUM_RADIUS_MIN + random.nextInt(MEDIUM_RADIUS_RANGE);
            int radiusSq = radius * radius;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (dx * dx + dy * dy + dz * dz <= radiusSq) {
                            set(x + dx, centerY + dy, z + dz, oil);
                        }
                    }
                }
            }
            int top = level.getMaxBuildHeight() - 1;
            boolean reachedGround = false;
            for (int y = Math.min(surfaceY(x, z), top); y >= centerY; y--) {
                if (reachedGround) {
                    set(x, y, z, oil);
                    continue;
                }
                if (!isSkippedOnSurface(level.getBlockState(new BlockPos(x, y, z)))) {
                    reachedGround = true;
                    surfaceDeposit(x, y, z, large ? LARGE_DEPOSIT_MIN + random.nextInt(LARGE_DEPOSIT_RANGE)
                            : MEDIUM_DEPOSIT_MIN + random.nextInt(MEDIUM_DEPOSIT_RANGE));
                    int spoutTop = Math.min(y + (large ? LARGE_SPOUT_HEIGHT : MEDIUM_SPOUT_HEIGHT), top);
                    for (int spoutY = y + 1; spoutY <= spoutTop; spoutY++) {
                        set(x, spoutY, z, oil);
                    }
                }
            }
            return placed;
        }

        /** 旧実装が地表探索で読み飛ばしたブロック (空気・葉・原木・草ブロック)。 */
        private static boolean isSkippedOnSurface(BlockState state) {
            return state.isAir() || state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS) || state.is(Blocks.GRASS_BLOCK);
        }

        private int surfaceY(int x, int z) {
            return level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
        }

        /** 旧 generateSurfaceDeposit: 中心から離れるほど薄くなる油だまり。 */
        private void surfaceDeposit(int x, int y, int z, int radius) {
            setOilWithChance(1.0F, x, y, z, true);
            for (int ring = 1; ring <= radius; ring++) {
                float chance = (float) (radius - ring + 4) / (float) (radius + 4);
                for (int offset = -ring; offset <= ring; offset++) {
                    setOilWithChance(chance, x + offset, y, z + ring, false);
                    setOilWithChance(chance, x + offset, y, z - ring, false);
                    setOilWithChance(chance, x + ring, y, z + offset, false);
                    setOilWithChance(chance, x - ring, y, z + offset, false);
                }
            }
            // 四方を油に囲まれた窪みを埋める。
            for (int fx = x - radius; fx <= x + radius; fx++) {
                for (int fz = z - radius; fz <= z + radius; fz++) {
                    if (!isOil(fx, y - 1, fz) && isOil(fx + 1, y - 1, fz) && isOil(fx - 1, y - 1, fz)
                            && isOil(fx, y - 1, fz + 1) && isOil(fx, y - 1, fz - 1)) {
                        setOilWithChance(1.0F, fx, y, fz, true);
                    }
                }
            }
        }

        /** 旧 setOilWithProba。 */
        private void setOilWithChance(float chance, int x, int y, int z, boolean force) {
            if (!force && !(random.nextFloat() <= chance && !level.getBlockState(new BlockPos(x, y - 2, z)).isAir())) {
                return;
            }
            boolean nextToOil = false;
            for (int d = -1; d <= 1; d++) {
                if (isOil(x + d, y - 1, z) || isOil(x - d, y - 1, z) || isOil(x, y - 1, z + d) || isOil(x, y - 1, z - d)) {
                    nextToOil = true;
                }
            }
            if (!nextToOil && !force) {
                return;
            }
            BlockState current = level.getBlockState(new BlockPos(x, y, z));
            boolean liquid = current.getFluidState().is(FluidTags.WATER) || isOil(current);
            set(x, y, z, liquid ? oil : Blocks.AIR.defaultBlockState());
            set(x, y - 1, z, oil);
        }

        private boolean isOil(int x, int y, int z) {
            return isOil(level.getBlockState(new BlockPos(x, y, z)));
        }

        private static boolean isOil(BlockState state) {
            return state.getFluidState().getType().isSame(ModFluids.OIL.get());
        }

        /** 書き込める範囲 (生成中チャンクの周囲 1 チャンク・高さの範囲内) だけに置く。 */
        private void set(int x, int y, int z, BlockState state) {
            BlockPos pos = new BlockPos(x, y, z);
            if (Math.abs((x >> 4) - chunk.x) > WRITE_RADIUS_CHUNKS || Math.abs((z >> 4) - chunk.z) > WRITE_RADIUS_CHUNKS
                    || level.isOutsideBuildHeight(pos)) {
                return;
            }
            level.setBlock(pos, state, Block.UPDATE_CLIENTS);
            if (!state.getFluidState().isEmpty()) {
                level.scheduleTick(pos, state.getFluidState().getType(), 0);
            }
            placed = true;
        }
    }
}
