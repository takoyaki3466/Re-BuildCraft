package com.takoy3466.re_buildcraft.transport.behavior;

import com.takoy3466.re_buildcraft.api.power.PowerProvider;
import com.takoy3466.re_buildcraft.transport.PipeBlockEntity;
import com.takoy3466.re_buildcraft.transport.TravelingItem;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import org.jetbrains.annotations.Nullable;

/**
 * パイプの素材ごとの振る舞い。元実装で各パイプクラス ({@code PipeItemsWood} など) と
 * {@code PipeLogic*}・{@code IPipeTransportItemsHook}・{@code IItemTravelingHook} に分かれていた処理をまとめた差し替え点。
 * 何もしない既定の実装を持つので、各素材は必要なメソッドだけを上書きする。
 */
public interface PipeBehavior {
    /** 振る舞いを持たない素材 (丸石・石など)。 */
    PipeBehavior NONE = new PipeBehavior() {
    };

    /** MJ で動く素材 (木・黒曜石) のみ非 null。ブロックはこのとき MJ の受け手として公開される。 */
    @Nullable
    default PowerProvider powerProvider() {
        return null;
    }

    /** {@link #powerProvider()} の蓄積が規定量に達したときに呼ばれる。 */
    default void doWork(PipeBlockEntity pipe) {
    }

    /** 毎 tick (サーバー側)。 */
    default void tick(PipeBlockEntity pipe) {
    }

    /** {@code direction} 方向へ送り出してよいか (鉄パイプは出力方向のみ)。 */
    default boolean outputOpen(PipeBlockEntity pipe, Direction direction) {
        return true;
    }

    default void onItemEntered(PipeBlockEntity pipe, TravelingItem item) {
    }

    /** アイテムの行き先候補を絞り込む (ダイヤパイプのフィルター)。 */
    default List<Direction> filterMovements(PipeBlockEntity pipe, List<Direction> movements, TravelingItem item) {
        return movements;
    }

    /** アイテムがパイプの中心に着いたとき。true を返すとアイテムはその場で消える (虚空パイプ)。 */
    default boolean consumeAtCenter(PipeBlockEntity pipe, TravelingItem item) {
        return false;
    }

    /**
     * 行き先が無くアイテムを落とす直前 (旧 IItemTravelingHook.drop)。スタックを使い切れば落とさない (ストライプパイプ)。
     */
    default void beforeDrop(PipeBlockEntity pipe, TravelingItem item) {
    }

    /** パイプから落としたアイテム (黒曜石パイプが吸い直さないように記録する)。 */
    default void onItemDropped(ItemEntity entity) {
    }

    /** レンチで右クリックされた。処理した場合 true。 */
    default boolean onWrench(PipeBlockEntity pipe) {
        return false;
    }

    default void onNeighborChanged(PipeBlockEntity pipe) {
    }

    /** 設置後、最初の tick に呼ばれる。 */
    default void initialize(PipeBlockEntity pipe) {
    }

    /** パイプの中にエンティティが入った (黒曜石パイプの吸い込み)。 */
    default void onEntityInside(PipeBlockEntity pipe, Entity entity) {
    }

    /** ゲートが無いとき、素手やアイテムで右クリックして開く画面 (ダイヤパイプのフィルター)。 */
    @Nullable
    default MenuProvider menuProvider(PipeBlockEntity pipe) {
        return null;
    }

    default void save(CompoundTag tag, HolderLookup.Provider registries) {
    }

    default void load(CompoundTag tag, HolderLookup.Provider registries) {
    }
}
