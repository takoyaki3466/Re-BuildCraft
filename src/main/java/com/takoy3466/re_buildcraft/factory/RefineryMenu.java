package com.takoy3466.re_buildcraft.factory;

import com.takoy3466.re_buildcraft.api.menu.PlayerInventorySlots;
import com.takoy3466.re_buildcraft.registry.ModMenus;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;

/**
 * 精製機のフィルター設定 GUI のメニュー。元実装: {@code buildcraft.factory.ContainerRefinery} と GuiRefinery の操作部分。
 * <p>
 * 旧実装はクライアントがフィルターを直接書き換えていたが、ここでは材料枠のクリックをメニューボタンとしてサーバーへ送り、
 * サーバー側でマウスに持っている容器の液体をフィルターにする。フィルターは液体の数値 ID で同期する。
 */
public class RefineryMenu extends AbstractContainerMenu {
    private static final int PLAYER_INVENTORY_Y = 123;
    private static final int NO_FILTER = -1;

    private final RefineryBlockEntity refinery;
    private final ContainerData data;

    /** サーバー側。 */
    public RefineryMenu(int containerId, Inventory playerInventory, RefineryBlockEntity refinery) {
        this(containerId, playerInventory, refinery, new FilterData(refinery));
    }

    private RefineryMenu(int containerId, Inventory playerInventory, RefineryBlockEntity refinery, ContainerData data) {
        super(ModMenus.REFINERY.get(), containerId);
        this.refinery = refinery;
        this.data = data;
        PlayerInventorySlots.add(this::addSlot, playerInventory, PLAYER_INVENTORY_Y);
        addDataSlots(data);
    }

    /** クライアント側。 */
    public static RefineryMenu createClient(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        if (playerInventory.player.level().getBlockEntity(extraData.readBlockPos()) instanceof RefineryBlockEntity refinery) {
            return new RefineryMenu(containerId, playerInventory, refinery, new SimpleContainerData(RefineryBlockEntity.FILTER_COUNT));
        }
        throw new IllegalStateException("Refinery menu opened for a position without a refinery");
    }

    /** {@code index} 番目のフィルター (未設定なら {@link Fluids#EMPTY})。 */
    public Fluid getFilter(int index) {
        int id = data.get(index);
        return id == NO_FILTER ? Fluids.EMPTY : BuiltInRegistries.FLUID.byId(id);
    }

    /** 材料枠のクリック。{@code id} はフィルターの番号。 */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id < 0 || id >= RefineryBlockEntity.FILTER_COUNT) {
            return false;
        }
        Fluid fluid = FluidUtil.getFluidContained(getCarried()).map(FluidStack::getFluid).orElse(Fluids.EMPTY);
        refinery.setFilter(id, fluid);
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(refinery, player);
    }

    /** 機械側のスロットが無いため、シフトクリックでは何も移さない。 */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    private record FilterData(RefineryBlockEntity refinery) implements ContainerData {
        @Override
        public int get(int index) {
            Fluid filter = refinery.getFilter(index);
            return filter == Fluids.EMPTY ? NO_FILTER : BuiltInRegistries.FLUID.getId(filter);
        }

        @Override
        public void set(int index, int value) {
            // サーバー側の値は常にブロックエンティティから読む。
        }

        @Override
        public int getCount() {
            return RefineryBlockEntity.FILTER_COUNT;
        }
    }
}
