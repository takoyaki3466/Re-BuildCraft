package com.takoy3466.re_buildcraft.energy;

import com.takoy3466.re_buildcraft.Config;
import com.takoy3466.re_buildcraft.ReBuildcraft;
import com.takoy3466.re_buildcraft.api.engine.EnergyStage;
import com.takoy3466.re_buildcraft.api.engine.EngineBlockEntity;
import com.takoy3466.re_buildcraft.api.engine.EngineFuel;
import com.takoy3466.re_buildcraft.api.engine.EngineLogic;
import com.takoy3466.re_buildcraft.registry.ModMenus;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidActionResult;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/**
 * 鉄エンジン (Combustion Engine)。液体燃料を燃やし、水で冷却する。
 * 元実装: {@code buildcraft.energy.EngineIron}。
 */
public class CombustionEngineLogic extends EngineLogic {
    public static final int MAX_LIQUID = 10000;
    public static final int DATA_FUEL_AMOUNT = 0;
    public static final int DATA_FUEL_ID = 1;
    public static final int DATA_COOLANT_AMOUNT = 2;
    public static final int DATA_COOLANT_ID = 3;
    public static final int DATA_COUNT = 4;
    public static final int FUEL_SLOT_X = 52;

    private static final ResourceLocation TEXTURE = ReBuildcraft.id("textures/block/engine/base_iron.png");
    private static final String TITLE_KEY = "container.re_buildcraft.combustion_engine";
    private static final String TAG_FUEL = "fuel";
    private static final String TAG_COOLANT = "coolant";
    private static final String TAG_BURN_TIME = "burnTime";
    private static final String TAG_HEAT = "heat";
    private static final String TAG_PENALTY_COOLING = "penaltyCooling";
    private static final int MAX_HEAT = 100000;
    private static final int COOLANT_THRESHOLD = 49000;
    private static final int MAX_ENERGY = 100000;
    private static final int MAX_ENERGY_EXTRACTED = 500;
    private static final int MAX_ENERGY_RECEIVED = 2000;
    private static final int EXPLOSION_RANGE = 8;
    private static final int BASE_POWER_MULTIPLIER = 1;
    private static final int POWERED_UP_MULTIPLIER = 3;
    private static final int PASSIVE_COOLING_PER_TICK = 10;
    /** 稼働中にレッドストーン入力が切れた後、再始動できるようになるまでの冷却 tick 数。 */
    private static final int SHUTDOWN_PENALTY_TICKS = 600;
    private static final float[] PISTON_SPEED_BY_STAGE = {0.04F, 0.05F, 0.06F, 0.07F};

    private final FluidTank fuelTank = new FluidTank(MAX_LIQUID, stack -> EngineFuel.of(stack.getFluid()) != null);
    private final FluidTank coolantTank = new FluidTank(MAX_LIQUID, stack -> stack.getFluid().isSame(Fluids.WATER));
    private final IFluidHandler fluidHandler = new CombustionEngineFluidHandler(fuelTank, coolantTank);

    /** 最後に燃やした燃料。タンクが空になった後も、残りの燃焼時間はこの性能で燃え続ける。 */
    private EngineFuel activeFuel;
    private int burnTime;
    private int heat;
    private int penaltyCooling;
    private boolean lastPowered;

    private final ContainerData containerData = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_FUEL_AMOUNT -> fuelTank.getFluidAmount();
                case DATA_FUEL_ID -> BuiltInRegistries.FLUID.getId(fuelTank.getFluid().getFluid());
                case DATA_COOLANT_AMOUNT -> coolantTank.getFluidAmount();
                case DATA_COOLANT_ID -> BuiltInRegistries.FLUID.getId(coolantTank.getFluid().getFluid());
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            // 表示専用の値で、クライアントからサーバーへ書き戻すことはない。
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public CombustionEngineLogic(EngineBlockEntity tile) {
        super(tile);
        this.maxEnergy = MAX_ENERGY;
        this.maxEnergyExtracted = MAX_ENERGY_EXTRACTED;
    }

    @Override
    public ResourceLocation getTexture() {
        return TEXTURE;
    }

    @Override
    public int explosionRange() {
        return EXPLOSION_RANGE;
    }

    @Override
    public int maxEnergyReceived() {
        return MAX_ENERGY_RECEIVED;
    }

    @Override
    public float getPistonSpeed() {
        int stage = getEnergyStage().ordinal();
        return stage < PISTON_SPEED_BY_STAGE.length ? PISTON_SPEED_BY_STAGE[stage] : 0.0F;
    }

    @Override
    public boolean isBurning() {
        return !fuelTank.isEmpty() && penaltyCooling == 0 && tile.isRedstonePowered();
    }

    @Override
    public void burn() {
        EngineFuel tankFuel = EngineFuel.of(fuelTank.getFluid().getFluid());
        if (tankFuel != null) {
            activeFuel = tankFuel;
        }
        EngineFuel fuel = activeFuel;
        if (fuel == null) {
            return;
        }

        if (penaltyCooling <= 0 && tile.isRedstonePowered()) {
            lastPowered = true;
            if (burnTime > 0 || !fuelTank.isEmpty()) {
                if (burnTime > 0) {
                    burnTime--;
                } else {
                    fuelTank.drain(1, IFluidHandler.FluidAction.EXECUTE);
                    burnTime = fuel.totalBurningTime() / FluidType.BUCKET_VOLUME;
                }
                int multiplier = Config.ENGINE_POWER_UP.get() ? POWERED_UP_MULTIPLIER : BASE_POWER_MULTIPLIER;
                addEnergy(fuel.powerPerCycle() * multiplier);
                heat += fuel.powerPerCycle();
            }
        } else if (penaltyCooling <= 0 && lastPowered) {
            lastPowered = false;
            penaltyCooling = SHUTDOWN_PENALTY_TICKS;
        }
    }

    @Override
    public void update() {
        super.update();
        fillFromContainerItem();

        if (heat > COOLANT_THRESHOLD) {
            int excessHeat = heat - COOLANT_THRESHOLD;
            if (coolantTank.getFluidAmount() > excessHeat) {
                coolantTank.drain(excessHeat, IFluidHandler.FluidAction.EXECUTE);
                heat = COOLANT_THRESHOLD;
            } else {
                heat -= coolantTank.getFluidAmount();
                coolantTank.setFluid(FluidStack.EMPTY);
            }
        }

        if (heat > 0 && (penaltyCooling > 0 || !tile.isRedstonePowered())) {
            heat -= PASSIVE_COOLING_PER_TICK;
        }
        if (heat <= 0 && penaltyCooling > 0) {
            penaltyCooling--;
        }
    }

    /** スロットに入れられた液体入り容器 (バケツ等) からちょうど 1 バケツ分を取り込む。 */
    private void fillFromContainerItem() {
        ItemStack stack = tile.getFuelStack();
        if (stack.isEmpty()) {
            return;
        }
        FluidActionResult result = FluidUtil.tryEmptyContainer(
                stack.copyWithCount(1), fluidHandler, FluidType.BUCKET_VOLUME, null, true);
        if (!result.isSuccess()) {
            return;
        }
        stack.shrink(1);
        tile.getInventory().setStackInSlot(0, stack.isEmpty() ? ItemStack.EMPTY : stack);
        tile.ejectItem(result.getResult());
    }

    @Override
    protected void computeEnergyStage() {
        if (heat <= MAX_HEAT / 4) {
            energyStage = EnergyStage.BLUE;
        } else if (heat <= MAX_HEAT / 2) {
            energyStage = EnergyStage.GREEN;
        } else if (heat <= MAX_HEAT * 3.0F / 4.0F) {
            energyStage = EnergyStage.YELLOW;
        } else if (heat <= MAX_HEAT) {
            energyStage = EnergyStage.RED;
        } else {
            energyStage = EnergyStage.EXPLOSION;
        }
    }

    @Override
    public boolean isActive() {
        return penaltyCooling <= 0;
    }

    @Override
    public int getHeat() {
        return heat;
    }

    @Override
    public void dissipateHeat(int amount) {
        heat = Math.max(heat - Math.max(amount, 0), 0);
    }

    @Override
    public int getShutdownPenaltyTicks() {
        return penaltyCooling;
    }

    @Override
    public boolean usesInventory() {
        return true;
    }

    @Override
    public IFluidHandler getFluidHandler() {
        return fluidHandler;
    }

    @Override
    public MenuProvider getMenuProvider() {
        return new SimpleMenuProvider(
                (containerId, playerInventory, player) -> new EngineMenu(
                        ModMenus.COMBUSTION_ENGINE.get(), containerId, playerInventory, tile, containerData, FUEL_SLOT_X),
                Component.translatable(TITLE_KEY));
    }

    @Override
    public void save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put(TAG_FUEL, fuelTank.writeToNBT(registries, new CompoundTag()));
        tag.put(TAG_COOLANT, coolantTank.writeToNBT(registries, new CompoundTag()));
        tag.putInt(TAG_BURN_TIME, burnTime);
        tag.putInt(TAG_HEAT, heat);
        tag.putInt(TAG_PENALTY_COOLING, penaltyCooling);
    }

    @Override
    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        fuelTank.readFromNBT(registries, tag.getCompound(TAG_FUEL));
        coolantTank.readFromNBT(registries, tag.getCompound(TAG_COOLANT));
        burnTime = tag.getInt(TAG_BURN_TIME);
        heat = tag.getInt(TAG_HEAT);
        penaltyCooling = tag.getInt(TAG_PENALTY_COOLING);
    }
}
