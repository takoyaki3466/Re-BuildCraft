package com.takoy3466.re_buildcraft.api.engine;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/**
 * エンジン 1 種類分の振る舞い (蓄熱・燃焼・出力)。
 * 元実装: {@code buildcraft.energy.Engine}。
 * <p>
 * ブロックエンティティ側 ({@link EngineBlockEntity}) はピストン運動と動力の受け渡しだけを担当し、
 * エンジン固有の計算はこのクラスの実装に委ねる。新しいエンジンはこのクラスを継承して
 * {@link EngineBlock} に渡すだけで追加できる。
 */
public abstract class EngineLogic {
    private static final double BLUE_LIMIT_PERCENT = 25.0;
    private static final double GREEN_LIMIT_PERCENT = 50.0;
    private static final double YELLOW_LIMIT_PERCENT = 75.0;
    private static final double RED_LIMIT_PERCENT = 100.0;

    protected final EngineBlockEntity tile;
    protected int maxEnergy;
    protected int maxEnergyExtracted = 1;
    protected int energy;
    protected float progress;
    protected EnergyStage energyStage = EnergyStage.BLUE;

    protected EngineLogic(EngineBlockEntity tile) {
        this.tile = tile;
    }

    // ---- 実装必須 -------------------------------------------------------

    /** ピストン機構のテクスチャ (64x32)。 */
    public abstract ResourceLocation getTexture();

    public abstract int explosionRange();

    /** 他のエンジンなどから 1 回に受け取れる最大エネルギー。 */
    public abstract int maxEnergyReceived();

    /** 1 tick あたりのピストン進行量 (0.0 - 1.0 で 1 往復)。 */
    public abstract float getPistonSpeed();

    public abstract boolean isBurning();

    /** 毎 tick の最後に呼ばれる燃焼処理。 */
    public abstract void burn();

    // ---- 既定の振る舞い ---------------------------------------------------

    /** 毎 tick、ピストン処理の前に呼ばれる。 */
    public void update() {
        if (!tile.isRedstonePowered() && energy > 1) {
            energy--;
        }
    }

    protected void computeEnergyStage() {
        double percent = (double) energy / (double) maxEnergy * 100.0;
        if (percent <= BLUE_LIMIT_PERCENT) {
            energyStage = EnergyStage.BLUE;
        } else if (percent <= GREEN_LIMIT_PERCENT) {
            energyStage = EnergyStage.GREEN;
        } else if (percent <= YELLOW_LIMIT_PERCENT) {
            energyStage = EnergyStage.YELLOW;
        } else if (percent <= RED_LIMIT_PERCENT) {
            energyStage = EnergyStage.RED;
        } else {
            energyStage = EnergyStage.EXPLOSION;
        }
    }

    /** サーバー側では現在値から再計算し、クライアント側では同期済みの値を返す。 */
    public final EnergyStage getEnergyStage() {
        Level level = tile.getLevel();
        if (level != null && !level.isClientSide) {
            computeEnergyStage();
        }
        return energyStage;
    }

    public void addEnergy(int amount) {
        energy += amount;
        if (getEnergyStage() == EnergyStage.EXPLOSION) {
            tile.explode(explosionRange());
        }
        if (energy > maxEnergy) {
            energy = maxEnergy;
        }
    }

    /**
     * @param min       これ未満しか蓄えていなければ何も取り出さない
     * @param max       取り出す上限 ({@link #maxEnergyExtracted} でさらに制限される)
     * @param doExtract false の場合は取り出せる量を返すだけ
     */
    public int extractEnergy(int min, int max, boolean doExtract) {
        if (energy < min) {
            return 0;
        }
        int extracted = Math.min(Math.min(max, maxEnergyExtracted), energy);
        if (doExtract) {
            energy -= extracted;
        }
        return extracted;
    }

    /** GUI の燃焼ゲージ用。{@code scale} を最大値とした現在値。 */
    public int getScaledBurnTime(int scale) {
        return 0;
    }

    /** ブロック破壊時に、保持している内容物を落とすなどの後始末を行う。 */
    public void onRemoved() {
    }

    public void save(CompoundTag tag, HolderLookup.Provider registries) {
    }

    public void load(CompoundTag tag, HolderLookup.Provider registries) {
    }

    /** false の間はピストンが新しい行程を開始しない。 */
    public boolean isActive() {
        return true;
    }

    /** 計測器向けの熱量。熱の概念が無いエンジンは 0。 */
    public int getHeat() {
        return 0;
    }

    /** 計測器向け: 停止後、再始動できるようになるまでの残り tick 数 (鉄エンジンのみ)。 */
    public int getShutdownPenaltyTicks() {
        return 0;
    }

    /** 燃料などを入れる 1 スロットのインベントリを使うか。 */
    public boolean usesInventory() {
        return false;
    }

    /** 液体を受け入れるエンジンの場合のみ非 null。 */
    @Nullable
    public IFluidHandler getFluidHandler() {
        return null;
    }

    /** 右クリックで開く GUI。GUI を持たないエンジンは null。 */
    @Nullable
    public MenuProvider getMenuProvider() {
        return null;
    }

    // ---- 状態アクセス -----------------------------------------------------

    public int getEnergy() {
        return energy;
    }

    public void setEnergy(int energy) {
        this.energy = energy;
    }

    public int getMaxEnergy() {
        return maxEnergy;
    }

    public int getMaxEnergyExtracted() {
        return maxEnergyExtracted;
    }

    public float getProgress() {
        return progress;
    }

    public void setProgress(float progress) {
        this.progress = progress;
    }

    /** クライアント同期専用。 */
    public void setEnergyStage(EnergyStage energyStage) {
        this.energyStage = energyStage;
    }
}
