package com.takoy3466.re_buildcraft.transport.behavior;

import com.takoy3466.re_buildcraft.api.power.PowerProvider;
import com.takoy3466.re_buildcraft.transport.ItemTransport;
import com.takoy3466.re_buildcraft.transport.PipeBlock;
import com.takoy3466.re_buildcraft.transport.PipeBlockEntity;
import com.takoy3466.re_buildcraft.transport.PipeKind;
import com.takoy3466.re_buildcraft.transport.PipeMaterial;
import com.takoy3466.re_buildcraft.transport.TravelingItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import org.jetbrains.annotations.Nullable;

/**
 * ストライプパイプ。元実装: {@code buildcraft.transport.pipes.PipeItemsStripes}。
 * <ul>
 *   <li>MJ を 1 受けるたびに、開いた口 (接続が 1 本だけのときの反対側) の前のブロックを壊し、ドロップをパイプへ取り込む。</li>
 *   <li>行き先が無く落とすはずのアイテムは、口の前で「使う」(ブロックなら設置)。</li>
 *   <li>他のアイテムパイプのアイテムが中心に着くと、自分をそのパイプに作り替える (1 本消費)。</li>
 * </ul>
 * ブロックの使用・設置は旧実装と同じく、サーバー側の偽プレイヤー ({@code BuildCraftAPI.getBuildCraftPlayer}) で行う。
 */
public class StripesBehavior implements PipeBehavior {
    private final PowerProvider powerProvider = new PowerProvider();
    /** 中心で作り替えが決まったパイプ。運搬処理の途中で差し替えないよう、次の tick に行う。 */
    @Nullable
    private PipeBlock pendingConversion;

    public StripesBehavior() {
        powerProvider.configure(1, 1, 1, 1);
        powerProvider.configurePowerPerdition(1, 1);
    }

    @Override
    public PowerProvider powerProvider() {
        return powerProvider;
    }

    @Override
    public void tick(PipeBlockEntity pipe) {
        if (pendingConversion != null) {
            PipeBlock target = pendingConversion;
            pendingConversion = null;
            pipe.convertTo(target);
        }
    }

    /** 口の前のブロックを壊してドロップを取り込む (旧 doWork)。破壊不能なブロックは壊さない。 */
    @Override
    public void doWork(PipeBlockEntity pipe) {
        if (!(pipe.getLevel() instanceof ServerLevel level) || powerProvider.useEnergy(1.0F, 1.0F, true) != 1.0F
                || !(pipe.transport() instanceof ItemTransport items)) {
            return;
        }
        Direction open = pipe.getOpenSide();
        if (open == null) {
            return;
        }
        BlockPos target = pipe.getBlockPos().relative(open);
        BlockState state = level.getBlockState(target);
        if (state.isAir() || state.getDestroySpeed(level, target) < 0.0F) {
            return;
        }
        for (ItemStack drop : Block.getDrops(state, level, target, level.getBlockEntity(target))) {
            items.injectFromSide(drop, open.getOpposite(), TravelingItem.NORMAL_SPEED);
        }
        level.removeBlock(target, false);
    }

    @Override
    public boolean consumeAtCenter(PipeBlockEntity pipe, TravelingItem item) {
        return reserveConversion(item);
    }

    /** 落とす代わりに口の前で使う。他のパイプなら作り替えに使う (旧 drop)。 */
    @Override
    public void beforeDrop(PipeBlockEntity pipe, TravelingItem item) {
        if (!(pipe.getLevel() instanceof ServerLevel level) || reserveConversion(item)) {
            return;
        }
        BlockPos front = pipe.getBlockPos().relative(item.heading());
        // 旧実装: 前が空気ならその下のブロックの上面に、そうでなければ前のブロックの上面に使う。
        BlockPos clicked = level.getBlockState(front).isAir() ? front.below() : front;
        FakePlayer player = FakePlayerFactory.getMinecraft(level);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(clicked), Direction.UP, clicked, false);
        ItemStack stack = item.stack();
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        stack.useOn(new UseOnContext(level, player, InteractionHand.MAIN_HAND, stack, hit));
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    /**
     * 他の種類のアイテムパイプなら 1 本消費して作り替えを予約する。
     *
     * @return 予約した (アイテムを消した) 場合 true
     */
    private boolean reserveConversion(TravelingItem item) {
        if (pendingConversion != null || !(item.stack().getItem() instanceof BlockItem blockItem)
                || !(blockItem.getBlock() instanceof PipeBlock pipeBlock)
                || pipeBlock.getPipeType().material() == PipeMaterial.STRIPES
                || pipeBlock.getPipeType().kind() != PipeKind.ITEMS) {
            return false;
        }
        pendingConversion = pipeBlock;
        item.stack().shrink(1);
        return item.stack().isEmpty();
    }
}
