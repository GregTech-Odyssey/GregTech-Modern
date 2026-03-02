package com.gregtechceu.gtceu.api.machine.multiblock;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.capability.IOpticalComputationProvider;
import com.gregtechceu.gtceu.api.capability.IParallelHatch;
import com.gregtechceu.gtceu.api.capability.recipe.CWURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.IRecipeHandler;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.fancy.*;
import com.gregtechceu.gtceu.api.gui.widget.CustomComponentPanelWidget;
import com.gregtechceu.gtceu.api.machine.feature.*;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IDisplayUIMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.misc.ComputationProviderList;
import com.gregtechceu.gtceu.api.misc.EnergyContainerList;
import com.gregtechceu.gtceu.api.recipe.IdleReason;
import com.gregtechceu.gtceu.utils.GTUtil;

import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.widget.DraggableScrollableWidgetGroup;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class WorkableElectricMultiblockMachine extends WorkableMultiblockMachine implements IFancyUIMachine, IDisplayUIMachine, ITieredMachine, IOverclockMachine, IComputationContainerMachine, IElectricMachine {

    // runtime
    @Getter
    @NotNull
    protected EnergyContainerList energyContainer = EnergyContainerList.EMPTY;
    @NotNull
    protected ComputationProviderList computationProviderList = ComputationProviderList.EMPTY;
    @Getter
    protected int tier;
    @Getter
    @Persisted
    protected boolean batchEnabled;

    public WorkableElectricMultiblockMachine(MetaMachineBlockEntity holder, Object... args) {
        super(holder, args);
    }

    //////////////////////////////////////
    // *** Multiblock Lifecycle ***//
    //////////////////////////////////////
    @Override
    public void onStructureInvalid() {
        super.onStructureInvalid();
        this.energyContainer = EnergyContainerList.EMPTY;
        this.computationProviderList = ComputationProviderList.EMPTY;
        this.tier = 0;
    }

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        List<IEnergyContainer> containers = new ArrayList<>();
        List<IOpticalComputationProvider> providers = new ArrayList<>();
        var handlers = getCapabilitiesFlat(IO.IN, EURecipeCapability.CAP);
        if (handlers.isEmpty()) handlers = getCapabilitiesFlat(IO.OUT, EURecipeCapability.CAP);
        for (IRecipeHandler<?> handler : handlers) {
            if (handler instanceof IEnergyContainer container) {
                containers.add(container);
            }
        }
        for (IRecipeHandler<?> handler : getCapabilitiesFlat(IO.IN, CWURecipeCapability.CAP)) {
            if (handler instanceof IOpticalComputationProvider provider) {
                providers.add(provider);
            }
        }
        if (!containers.isEmpty()) energyContainer = new EnergyContainerList(containers);
        if (!providers.isEmpty()) computationProviderList = new ComputationProviderList(providers);
        this.tier = GTUtil.getFloorTierByVoltage(getMaxVoltage());
    }

    //////////////////////////////////////
    // ********** GUI ***********//
    //////////////////////////////////////
    @Override
    public void addDisplayText(List<Component> textList) {
        int numParallels;
        long batchParallels;
        boolean exact = false;
        if (recipeLogic.isActive() && recipeLogic.getLastRecipe() != null) {
            numParallels = (int) recipeLogic.getLastRecipe().parallels;
            batchParallels = recipeLogic.getLastRecipe().batchParallels;
            exact = true;
        } else {
            numParallels = Optional.ofNullable(getParallelHatch()).map(IParallelHatch::getCurrentParallel).orElse(0);
            batchParallels = 0;
        }
        MultiblockDisplayText.builder(textList, isFormed()).setWorkingStatus(recipeLogic.isWorkingEnabled(), recipeLogic.isActive()).addEnergyUsageLine(energyContainer).addEnergyTierLine(tier).addMachineModeLine(getRecipeType(), getRecipeTypes().length > 1).addParallelsLine(numParallels, exact).addBatchModeLine(isBatchEnabled(), batchParallels).addWorkingStatusLine().addProgressLine(recipeLogic.getProgress(), recipeLogic.getMaxProgress(), recipeLogic.getProgressPercent()).addOutputLines(recipeLogic.getLastRecipe());
        getDefinition().getAdditionalDisplay().accept(this, textList);
        IDisplayUIMachine.super.addDisplayText(textList);
    }

    @Override
    public Widget createUIWidget() {
        var group = new WidgetGroup(0, 0, 182 + 8, 117 + 8);
        group.addWidget(new DraggableScrollableWidgetGroup(4, 4, 182, 117).setBackground(getScreenTexture()).addWidget(new LabelWidget(4, 5, self().getBlockState().getBlock().getDescriptionId())).addWidget(new CustomComponentPanelWidget(4, 17)
                .setTextDataReader(this::readClientTextData).setTextDataWriter(this::writeClientTextData).textSupplier(this.getLevel().isClientSide ? null : this::addDisplayText).setMaxWidthLimit(200).clickHandler(this::handleDisplayClick)));
        group.setBackground(GuiTextures.BACKGROUND_INVERSE);
        return group;
    }

    @Override
    public ModularUI createUI(Player entityPlayer) {
        return new ModularUI(198, 208, this, entityPlayer).widget(new FancyMachineUIWidget(this, 198, 208));
    }

    @Override
    public List<IFancyUIProvider> getSubTabs() {
        return Arrays.stream(getParts()).filter(Objects::nonNull).map(IFancyUIProvider.class::cast).toList();
    }

    @Override
    public void attachConfigurators(ConfiguratorPanel configuratorPanel) {
        if (hasBatchConfig()) configuratorPanel.attachConfigurators(new IFancyConfiguratorButton.Toggle(GuiTextures.BUTTON_BATCH.getSubTexture(0, 0, 1, 0.5), GuiTextures.BUTTON_BATCH.getSubTexture(0, 0.5, 1, 0.5), this::isBatchEnabled, (cd, p) -> batchEnabled = p).setTooltipsSupplier(p -> List.of(Component.translatable("gtceu.machine.batch_" + (p ? "enabled" : "disabled")))));
        IFancyUIMachine.super.attachConfigurators(configuratorPanel);
    }

    @Override
    public void attachTooltips(TooltipsPanel tooltipsPanel) {
        for (IMultiPart part : getParts()) {
            part.attachFancyTooltipsToController(this, tooltipsPanel);
        }
    }

    //////////////////////////////////////
    // ******** OVERCLOCK *********//
    //////////////////////////////////////
    @Override
    public int getOverclockTier() {
        return getTier();
    }

    @Override
    public int getMaxOverclockTier() {
        return getTier();
    }

    @Override
    public int getMinOverclockTier() {
        return getTier();
    }

    @Override
    public void setOverclockTier(int tier) {}

    @Override
    public long getOverclockVoltage() {
        return energyContainer.getOverclockVoltage();
    }

    @Override
    public long getMaxVoltage() {
        return energyContainer.getMaxVoltage();
    }

    /**
     * Is this multiblock a generator?
     * Used for max voltage calculations.
     */
    public boolean isGenerator() {
        return getDefinition().isGenerator();
    }

    @Override
    public IOpticalComputationProvider getComputationProvider() {
        return computationProviderList;
    }

    @Override
    public boolean checkTier(int tier) {
        if (tier > this.getTier()) {
            setIdleReason(IdleReason.INSUFFICIENT_VOLTAGE_TIER);
            return false;
        }
        return true;
    }
}
