package com.takoy3466.re_buildcraft.api.power;

import com.takoy3466.re_buildcraft.ReBuildcraft;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.capabilities.BlockCapability;

/** Re-BuildCraft が公開するブロックケーパビリティ。 */
public final class BuildCraftCapabilities {

    /**
     * MJ を受け取れるブロック。コンテキストは「エネルギーが入ってくる面」。
     * 旧 API の {@code tile instanceof IPowerReceptor} 判定を置き換える。
     */
    public static final BlockCapability<IPowerReceptor, Direction> POWER_RECEPTOR =
            BlockCapability.createSided(ReBuildcraft.id("power_receptor"), IPowerReceptor.class);

    /** 導電パイプ。流量の取得に使う。コンテキストは問い合わせる面。 */
    public static final BlockCapability<IPowerConduit, Direction> POWER_CONDUIT =
            BlockCapability.createSided(ReBuildcraft.id("power_conduit"), IPowerConduit.class);

    private BuildCraftCapabilities() {
    }
}
