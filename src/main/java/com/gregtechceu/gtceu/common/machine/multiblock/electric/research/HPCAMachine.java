package com.gregtechceu.gtceu.common.machine.multiblock.electric.research;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.capability.IHPCAComponentHatch;
import com.gregtechceu.gtceu.api.capability.IHPCAComputationProvider;
import com.gregtechceu.gtceu.api.capability.IHPCACoolantProvider;
import com.gregtechceu.gtceu.api.capability.IOpticalComputationProvider;
import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.util.TimedProgressSupplier;
import com.gregtechceu.gtceu.api.gui.widget.ExtendedProgressWidget;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IWorkableMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockDisplayText;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.misc.EnergyContainerList;
import com.gregtechceu.gtceu.api.pattern.util.RelativeDirection;
import com.gregtechceu.gtceu.api.transfer.fluid.FluidHandlerList;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.gregtechceu.gtceu.utils.GTTransferUtils;
import com.gregtechceu.gtceu.utils.GTUtil;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ProgressTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;
import com.lowdragmc.lowdraglib.gui.widget.ImageWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;

import net.minecraft.ChatFormatting;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import com.gto.datasynclib.annotations.AdditionalHolder;
import com.gto.datasynclib.annotations.SyncToClient;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import javax.annotation.ParametersAreNonnullByDefault;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public class HPCAMachine extends WorkableElectricMultiblockMachine implements IOpticalComputationProvider {

    private static final double IDLE_TEMPERATURE = 200;
    private static final double DAMAGE_TEMPERATURE = 1000;
    private IFluidHandler coolantHandler;

    @AdditionalHolder
    private final HPCAGridHandler hpcaHandler;
    @Persisted
    private double temperature = IDLE_TEMPERATURE; // start at idle temperature
    private final TimedProgressSupplier progressSupplier;
    @Nullable
    protected TickableSubscription tickSubs;

    public HPCAMachine(MetaMachineBlockEntity holder, Object... args) {
        super(holder, args);
        this.energyContainer = EnergyContainerList.EMPTY;
        this.progressSupplier = new TimedProgressSupplier(200, 47, false);
        this.hpcaHandler = new HPCAGridHandler();
    }

    @Override
    public boolean hasBatchConfig() {
        return false;
    }

    @Override
    protected void onStructureFormedAfter() {
        super.onStructureFormedAfter();
        updateTickSubscription();
    }

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        List<IFluidHandler> coolantContainers = new ArrayList<>();
        List<IHPCAComponentHatch> componentHatches = new ArrayList<>();
        for (IMultiPart part : getParts()) {
            if (part instanceof IHPCAComponentHatch componentHatch) {
                componentHatches.add(componentHatch);
            } else if (part instanceof IWorkableMultiPart workableMultiPart) {
                for (var handlerList : workableMultiPart.getRecipeHandlers()) {
                    handlerList.getCapability(FluidRecipeCapability.CAP).stream().filter(IFluidHandler.class::isInstance).map(IFluidHandler.class::cast).forEach(coolantContainers::add);
                }
            }
        }
        this.coolantHandler = new FluidHandlerList(coolantContainers);
        this.hpcaHandler.onStructureForm(componentHatches);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (getLevel() instanceof ServerLevel serverLevel) {
            serverLevel.getServer().tell(new TickTask(0, this::updateTickSubscription));
        }
    }

    @Override
    public void onUnload() {
        super.onUnload();
        if (tickSubs != null) {
            tickSubs.unsubscribe();
            tickSubs = null;
        }
    }

    protected void updateTickSubscription() {
        if (isFormed) {
            tickSubs = subscribeServerTick(tickSubs, this::tick);
        } else if (tickSubs != null) {
            tickSubs.unsubscribe();
            tickSubs = null;
        }
    }

    @Override
    public void onStructureInvalid() {
        super.onStructureInvalid();
        updateTickSubscription();
        this.hpcaHandler.reset();
    }

    @Override
    public long requestCWU(long cwu, boolean simulate) {
        if (isWorkingEnabled() && getRecipeLogic().isWorking()) {
            return hpcaHandler.allocateCWUt(cwu, simulate);
        }
        return 0;
    }

    @Override
    public long getMaxCWU() {
        if (isWorkingEnabled() && getRecipeLogic().isWorking()) {
            return hpcaHandler.maxCWUt - hpcaHandler.cachedCWUt;
        }
        return 0;
    }

    @Override
    public boolean canBridge() {
        return !isFormed() || hpcaHandler.numBridges > 0;
    }

    public void tick() {
        if (isWorkingEnabled()) {
            long energyToConsume = hpcaHandler.getCurrentEUt();
            if (this.energyContainer.removeEnergy(energyToConsume) >= energyToConsume) {
                getRecipeLogic().setStatus(RecipeLogic.WORKING);
            } else {
                getRecipeLogic().setWaiting(Component.translatable("gtceu.recipe_logic.insufficient_in").append(": ").append(EURecipeCapability.CAP.getName()));
            }
            // forcibly use active coolers at full rate if temperature is half-way to damaging temperature
            double midpoint = (DAMAGE_TEMPERATURE - IDLE_TEMPERATURE) / 2;
            double temperatureChange = hpcaHandler.calculateTemperatureChange(coolantHandler, temperature >= midpoint) / 2.0;
            if (temperature + temperatureChange <= IDLE_TEMPERATURE) {
                temperature = IDLE_TEMPERATURE;
            } else {
                temperature += temperatureChange;
            }
            if (temperature >= DAMAGE_TEMPERATURE) {
                hpcaHandler.attemptDamageHPCA();
            }
            hpcaHandler.tick();
        } else {
            hpcaHandler.clearComputationCache();
            // passively cool (slowly) if not active
            temperature = Math.max(IDLE_TEMPERATURE, temperature - 0.25);
        }
    }

    @Override
    public Widget createUIWidget() {
        WidgetGroup builder = (WidgetGroup) super.createUIWidget();
        // Create the hover grid
        builder.addWidget(new ExtendedProgressWidget(() -> hpcaHandler.cachedCWUt > 0 ? progressSupplier.getAsDouble() : 0, 74, 57, 47, 47, GuiTextures.HPCA_COMPONENT_OUTLINE).setServerTooltipSupplier(hpcaHandler::addInfo).setFillDirection(ProgressTexture.FillDirection.LEFT_TO_RIGHT));
        int startX = 76;
        int startY = 59;
        // we need to know what components we have on the client
        if (getLevel().isClientSide) {
            if (isFormed) {
                hpcaHandler.tryGatherClientComponents(this.getLevel(), this.getPos(), this.getFrontFacing(), this.getUpwardsFacing(), this.isFlipped);
            } else {
                hpcaHandler.clearClientComponents();
            }
        }
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                final int index = i * 3 + j;
                Supplier<IGuiTexture> textureSupplier = () -> hpcaHandler.getComponentTexture(index);
                builder.addWidget(new ImageWidget(startX + (15 * j), startY + (15 * i), 13, 13, textureSupplier));
            }
        }
        return builder;
    }

    @Override
    public void addDisplayText(List<Component> textList) {
        // transform into two-state system for
        // display
        // Energy Usage
        // Provided Computation
        MultiblockDisplayText.builder(textList, isFormed()).setWorkingStatus(true, hpcaHandler.cachedCWUt > 0).setWorkingStatusKeys("gtceu.multiblock.idling", "gtceu.multiblock.idling", "gtceu.multiblock.data_bank.providing").addCustom(tl -> {
            if (isFormed()) {
                tl.add(Component.translatable("gtceu.multiblock.hpca.energy", FormattingUtil.formatNumbers(hpcaHandler.cachedEUt), FormattingUtil.formatNumbers(hpcaHandler.maximumEUt), GTValues.VNF[GTUtil.getTierByVoltage(hpcaHandler.maximumEUt)]).withStyle(ChatFormatting.GRAY));
                Component cwutInfo = Component.literal(hpcaHandler.cachedCWUt + " / " + hpcaHandler.maxCWUt + " CWU/t").withStyle(ChatFormatting.AQUA);
                tl.add(Component.translatable("gtceu.multiblock.hpca.computation", cwutInfo).withStyle(ChatFormatting.GRAY));
            }
        }).addWorkingStatusLine();
    }

    public static class HPCAGridHandler {

        // structure info
        private final List<IHPCAComponentHatch> components = new ArrayList<>();
        private final Set<IHPCACoolantProvider> coolantProviders = new ReferenceOpenHashSet<>();
        private final Set<IHPCAComputationProvider> computationProviders = new ReferenceOpenHashSet<>();
        private int numBridges;
        // transaction info
        /**
         * How much CWU/t is currently allocated for this tick.
         */
        private long allocatedCWUt;
        private long maxCWUt;
        private long upkeepEUt;
        private long maximumEUt;
        // cached gui info
        // holding these values past the computation clear because GUI is too "late" to read the state in time
        @SyncToClient
        private long cachedEUt;
        @SyncToClient
        private long cachedCWUt;

        public HPCAGridHandler() {}

        public void onStructureForm(Collection<IHPCAComponentHatch> components) {
            reset();
            for (var component : components) {
                this.components.add(component);
                if (component instanceof IHPCACoolantProvider coolantProvider) {
                    this.coolantProviders.add(coolantProvider);
                }
                if (component instanceof IHPCAComputationProvider computationProvider) {
                    this.computationProviders.add(computationProvider);
                    maxCWUt += computationProvider.getCWUPerTick();
                }
                if (component.isBridge()) {
                    this.numBridges++;
                }
                upkeepEUt += component.getUpkeepEUt();
                maximumEUt += component.getMaxEUt();
            }
        }

        private void reset() {
            clearComputationCache();
            components.clear();
            coolantProviders.clear();
            computationProviders.clear();
            numBridges = 0;
            cachedCWUt = 0;
            maxCWUt = 0;
            cachedEUt = 0;
            upkeepEUt = 0;
            maximumEUt = 0;
        }

        private void clearComputationCache() {
            allocatedCWUt = 0;
        }

        public void tick() {
            if (cachedCWUt != allocatedCWUt) {
                cachedCWUt = allocatedCWUt;
            }
            cachedEUt = getCurrentEUt();
            allocatedCWUt = 0;
        }

        /**
         * Calculate the temperature differential this tick given active computation and consume coolant.
         *
         * @param coolantTank         The tank to drain coolant from.
         * @param forceCoolWithActive Whether active coolers should forcibly cool even if temperature is already
         *                            decreasing due to passive coolers. Used when the HPCA is running very hot.
         * @return The temperature change, can be positive or negative.
         */
        public double calculateTemperatureChange(IFluidHandler coolantTank, boolean forceCoolWithActive) {
            // calculate temperature increase
            long maxCWUt = Math.max(1, this.maxCWUt); // avoids dividing by 0 and the behavior is no different
            int maxCoolingDemand = getMaxCoolingDemand();
            // temperature increase is proportional to the amount of actively used computation
            // a * (b / c)
            int temperatureIncrease = (int) Math.round(1.0 * maxCoolingDemand * allocatedCWUt / maxCWUt);
            // calculate temperature decrease
            long maxPassiveCooling = 0;
            long maxActiveCooling = 0;
            int maxCoolantDrain = 0;
            for (var coolantProvider : coolantProviders) {
                if (coolantProvider.isActiveCooler()) {
                    maxActiveCooling += coolantProvider.getCoolingAmount();
                    maxCoolantDrain += coolantProvider.getMaxCoolantPerTick();
                } else {
                    maxPassiveCooling += coolantProvider.getCoolingAmount();
                }
            }
            double temperatureChange = temperatureIncrease - maxPassiveCooling;
            // quick exit if no active cooling/coolant drain is present
            if (maxActiveCooling == 0 && maxCoolantDrain == 0) {
                return temperatureChange;
            }
            if (forceCoolWithActive || maxActiveCooling <= temperatureChange) {
                // try to fully utilize active coolers
                FluidStack coolantStack = GTTransferUtils.drainFluidAccountNotifiableList(coolantTank, getCoolantStack(maxCoolantDrain), IFluidHandler.FluidAction.EXECUTE);
                if (!coolantStack.isEmpty()) {
                    long coolantDrained = coolantStack.getAmount();
                    if (coolantDrained == maxCoolantDrain) {
                        // coolant requirement was fully met
                        temperatureChange -= maxActiveCooling;
                    } else {
                        // coolant requirement was only partially met, cool proportional to fluid amount drained
                        // a * (b / c)
                        temperatureChange -= maxActiveCooling * (1.0 * coolantDrained / maxCoolantDrain);
                    }
                }
            } else if (temperatureChange > 0) {
                // try to partially utilize active coolers to stabilize to zero
                double temperatureToDecrease = Math.min(temperatureChange, maxActiveCooling);
                int coolantToDrain = Math.max(1, (int) (maxCoolantDrain * (temperatureToDecrease / maxActiveCooling)));
                FluidStack coolantStack = GTTransferUtils.drainFluidAccountNotifiableList(coolantTank, getCoolantStack(coolantToDrain), IFluidHandler.FluidAction.EXECUTE);
                if (!coolantStack.isEmpty()) {
                    int coolantDrained = coolantStack.getAmount();
                    if (coolantDrained == coolantToDrain) {
                        // successfully stabilized to zero
                        return 0;
                    } else {
                        // coolant requirement was only partially met, cool proportional to fluid amount drained
                        // a * (b / c)
                        temperatureChange -= temperatureToDecrease * (1.0 * coolantDrained / coolantToDrain);
                    }
                }
            }
            return temperatureChange;
        }

        /**
         * Get the coolant stack for this HPCA. Eventually this could be made more diverse with different
         * coolants from different Active Cooler components, but currently it is just a fixed Fluid.
         */
        public FluidStack getCoolantStack(int amount) {
            return new FluidStack(getCoolant(), amount);
        }

        private Fluid getCoolant() {
            return GTMaterials.PCBCoolant.getFluid();
        }

        /**
         * Roll a 1/200 chance to damage a HPCA component marked as damageable. Randomly selects the component.
         * If called every tick, this succeeds on average once every 10 seconds.
         */
        public void attemptDamageHPCA() {
            // 1% chance each tick to damage a component if running too hot
            if (GTValues.RNG.nextInt(200) == 0) {
                // randomize which component is actually damaged
                List<IHPCAComponentHatch> candidates = new ArrayList<>();
                for (var component : components) {
                    if (component.canBeDamaged()) {
                        candidates.add(component);
                    }
                }
                if (!candidates.isEmpty()) {
                    candidates.get(GTValues.RNG.nextInt(candidates.size())).setDamaged(true);
                }
            }
        }

        /**
         * Allocate computation on a given request. Allocates for one tick.
         */
        public long allocateCWUt(long cwu, boolean simulate) {
            long toAllocate = Math.min(cwu, maxCWUt - allocatedCWUt);
            if (!simulate) {
                this.allocatedCWUt += toAllocate;
            }
            return toAllocate;
        }

        /**
         * The current EU/t this HPCA should use, considering passive drain, current computation, etc..
         */
        public long getCurrentEUt() {
            long maximumCWUt = Math.max(1, maxCWUt); // behavior is no different setting this to 1 if it is 0
            long maximumEUt = this.maximumEUt;
            long upkeepEUt = this.upkeepEUt;
            if (maximumEUt == upkeepEUt) {
                return maximumEUt;
            }
            // energy draw is proportional to the amount of actively used computation
            // a + c(b - a) / d
            return upkeepEUt + ((maximumEUt - upkeepEUt) * allocatedCWUt / maximumCWUt);
        }

        /**
         * How much cooling this HPCA can provide. NOT related to coolant fluid consumption.
         */
        public int getMaxCoolingAmount() {
            int maxCooling = 0;
            for (var coolantProvider : coolantProviders) {
                maxCooling += coolantProvider.getCoolingAmount();
            }
            return maxCooling;
        }

        /**
         * How much cooling this HPCA can require. NOT related to coolant fluid consumption.
         */
        public int getMaxCoolingDemand() {
            int maxCooling = 0;
            for (var computationProvider : computationProviders) {
                maxCooling += computationProvider.getCoolingPerTick();
            }
            return maxCooling;
        }

        /**
         * How much coolant this HPCA can consume in a tick, in mB/t.
         */
        public int getMaxCoolantDemand() {
            int maxCoolant = 0;
            for (var coolantProvider : coolantProviders) {
                maxCoolant += coolantProvider.getMaxCoolantPerTick();
            }
            return maxCoolant;
        }

        public void addInfo(List<Component> textList) {
            // Max Computation
            MutableComponent data = Component.literal(Long.toString(maxCWUt)).withStyle(ChatFormatting.AQUA);
            textList.add(Component.translatable("gtceu.multiblock.hpca.info_max_computation", data).withStyle(ChatFormatting.GRAY));
            // Cooling
            ChatFormatting coolingColor = getMaxCoolingAmount() < getMaxCoolingDemand() ? ChatFormatting.RED : ChatFormatting.GREEN;
            data = Component.literal(Integer.toString(getMaxCoolingDemand())).withStyle(coolingColor);
            textList.add(Component.translatable("gtceu.multiblock.hpca.info_max_cooling_demand", data).withStyle(ChatFormatting.GRAY));
            data = Component.literal(Integer.toString(getMaxCoolingAmount())).withStyle(coolingColor);
            textList.add(Component.translatable("gtceu.multiblock.hpca.info_max_cooling_available", data).withStyle(ChatFormatting.GRAY));
            // Coolant Required
            if (getMaxCoolantDemand() > 0) {
                data = Component.translatable("gtceu.universal.liters", getMaxCoolantDemand()).withStyle(ChatFormatting.YELLOW).append(" ");
                Component coolantName = Component.translatable("gtceu.multiblock.hpca.info_coolant_name").withStyle(ChatFormatting.YELLOW);
                data.append(coolantName);
            } else {
                data = Component.literal("0").withStyle(ChatFormatting.GREEN);
            }
            textList.add(Component.translatable("gtceu.multiblock.hpca.info_max_coolant_required", data).withStyle(ChatFormatting.GRAY));
            // Bridging
            if (numBridges > 0) {
                textList.add(Component.translatable("gtceu.multiblock.hpca.info_bridging_enabled").withStyle(ChatFormatting.GREEN));
            } else {
                textList.add(Component.translatable("gtceu.multiblock.hpca.info_bridging_disabled").withStyle(ChatFormatting.RED));
            }
        }

        public ResourceTexture getComponentTexture(int index) {
            if (components.size() <= index) {
                return GuiTextures.BLANK_TRANSPARENT;
            }
            return components.get(index).getComponentIcon();
        }

        public void tryGatherClientComponents(Level world, BlockPos pos, Direction frontFacing, Direction upwardsFacing, boolean flip) {
            Direction relativeUp = RelativeDirection.UP.getRelative(frontFacing, upwardsFacing, flip);
            if (components.isEmpty()) {
                BlockPos testPos = pos.relative(frontFacing.getOpposite(), 3).relative(relativeUp, 3);
                for (int i = 0; i < 3; i++) {
                    for (int j = 0; j < 3; j++) {
                        BlockPos tempPos = testPos.relative(frontFacing, j).relative(relativeUp.getOpposite(), i);
                        BlockEntity be = world.getBlockEntity(tempPos);
                        if (be instanceof IHPCAComponentHatch hatch) {
                            components.add(hatch);
                        } else if (be instanceof MetaMachineBlockEntity machineBE) {
                            MetaMachine machine = machineBE.getMetaMachine();
                            if (machine instanceof IHPCAComponentHatch hatch) {
                                components.add(hatch);
                            }
                        }
                        // if here without a hatch, something went wrong, better to skip than add a null into the mix.
                    }
                }
            }
        }

        public void clearClientComponents() {
            components.clear();
        }
    }
}
