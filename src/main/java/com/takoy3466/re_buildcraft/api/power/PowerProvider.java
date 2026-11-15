package com.takoy3466.re_buildcraft.api.power;

import com.takoy3466.re_buildcraft.api.util.SafeTimeTracker;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;

/**
 * 機械 1 台分の MJ バッファ。
 * 元実装: {@code buildcraft.api.PowerProvider} + {@code buildcraft.energy.PneumaticPowerProvider}。
 * <p>
 * BCx では PowerFramework が常に PneumaticPowerProvider を生成しており
 * (RedstonePowerFramework は設定ごと無効化済み)、Pneumatic 版は latency を常に 0 に固定する。
 * そのためここでは latency の概念を持たず、フレームワークの切り替え機構も移行していない。
 */
public final class PowerProvider {
    private static final String TAG_STORED_ENERGY = "storedEnergy";
    private static final int DEFAULT_POWER_LOSS = 1;
    private static final int DEFAULT_POWER_LOSS_REGULARITY = 100;
    /** エネルギーを受け取った面を「動力源」と見なし続ける tick 数。 */
    private static final int POWER_SOURCE_TICKS = 2;

    private int minEnergyReceived;
    private int maxEnergyReceived;
    private int maxEnergyStored;
    private int minActivationEnergy;
    private float energyStored;
    private int powerLoss = DEFAULT_POWER_LOSS;
    private int powerLossRegularity = DEFAULT_POWER_LOSS_REGULARITY;
    private final SafeTimeTracker energyLossTracker = new SafeTimeTracker();
    private final int[] powerSources = new int[Direction.values().length];
    /** {@link #configureTickInputBuffer} が有効か。 */
    private boolean holdsTickInput;
    /** 前回の {@link #update} から受け取った量 (保存しない)。 */
    private float receivedSinceUpdate;

    public void configure(int minEnergyReceived, int maxEnergyReceived, int minActivationEnergy, int maxEnergyStored) {
        this.minEnergyReceived = minEnergyReceived;
        this.maxEnergyReceived = maxEnergyReceived;
        this.minActivationEnergy = minActivationEnergy;
        this.maxEnergyStored = maxEnergyStored;
    }

    /**
     * 有効にすると、蓄電量の上限 ({@code maxEnergyStored}) を、前回の {@link #update} から受け取った量の分だけ一時的に超えて受け取る。
     * 受け取った MJ を次の作業の機会まで捨てずに持つためのもので、1 回の作業量は変わらない。
     * 大出力のエンジンから 1 tick に蓄電量以上が届いても、届いた分を同じ (または次の) tick の連続作業に使える。
     * 使い切れずに残った分は、次に受け取るときの上限を広げないため、残量が増え続けることはない
     * (残量の上限は「蓄電量 + 1 回の更新の間に受け取った量」)。既定は無効 (旧実装と同じく上限で切り捨てる)。
     */
    public void configureTickInputBuffer(boolean enabled) {
        this.holdsTickInput = enabled;
    }

    public void configurePowerPerdition(int powerLoss, int powerLossRegularity) {
        this.powerLoss = powerLoss;
        this.powerLossRegularity = powerLossRegularity;
    }

    /**
     * 毎 tick (サーバー側) 呼び出す。必要なら {@link IPowerReceptor#doWork()} を実行し、自然損失を適用する。
     *
     * @return doWork を呼んだ場合 true
     */
    public boolean update(IPowerReceptor receptor, Level level) {
        receivedSinceUpdate = 0.0F;
        boolean worked = false;
        if (energyStored >= minActivationEnergy) {
            receptor.doWork();
            worked = true;
        }

        if (powerLoss > 0 && energyLossTracker.markTimeIfDelay(level, powerLossRegularity)) {
            energyStored = Math.max(0, energyStored - powerLoss);
        }

        for (int i = 0; i < powerSources.length; i++) {
            if (powerSources[i] > 0) {
                powerSources[i]--;
            }
        }
        return worked;
    }

    /**
     * {@code min} 以上蓄えられていれば最大 {@code max} まで取り出す。
     *
     * @return 取り出せた (または取り出せる) エネルギー量。蓄積が {@code min} 未満なら 0
     */
    public float useEnergy(float min, float max, boolean doUse) {
        if (energyStored < min) {
            return 0;
        }
        float result = Math.min(energyStored, max);
        if (doUse) {
            energyStored -= result;
        }
        return result;
    }

    public void receiveEnergy(float quantity, Direction from) {
        powerSources[from.ordinal()] = POWER_SOURCE_TICKS;
        if (!holdsTickInput) {
            energyStored = Math.min(energyStored + quantity, maxEnergyStored);
            return;
        }
        receivedSinceUpdate += quantity;
        float limit = maxEnergyStored + receivedSinceUpdate;
        // すでに上限を超えて残っている分は減らさない (受け取った分を捨てるだけ)。
        energyStored = Math.max(energyStored, Math.min(energyStored + quantity, limit));
    }

    public boolean isPowerSource(Direction from) {
        return powerSources[from.ordinal()] != 0;
    }

    public void save(CompoundTag tag) {
        tag.putFloat(TAG_STORED_ENERGY, energyStored);
    }

    public void load(CompoundTag tag) {
        energyStored = tag.getFloat(TAG_STORED_ENERGY);
    }

    public int getMinEnergyReceived() {
        return minEnergyReceived;
    }

    public int getMaxEnergyReceived() {
        return maxEnergyReceived;
    }

    public int getMaxEnergyStored() {
        return maxEnergyStored;
    }

    public int getMinActivationEnergy() {
        return minActivationEnergy;
    }

    public float getEnergyStored() {
        return energyStored;
    }
}
