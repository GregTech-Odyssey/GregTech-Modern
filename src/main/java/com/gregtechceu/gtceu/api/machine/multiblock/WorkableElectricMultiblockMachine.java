package com.gregtechceu.gtceu.api.machine.multiblock;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.capability.GTCapability;
import com.gregtechceu.gtceu.api.capability.IOpticalComputationProvider;
import com.gregtechceu.gtceu.api.capability.IParallelHatch;
import com.gregtechceu.gtceu.api.gui.fancy.*;
import com.gregtechceu.gtceu.api.machine.feature.*;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiblockFancyUIMachine;
import com.gregtechceu.gtceu.api.misc.ComputationProviderList;
import com.gregtechceu.gtceu.api.misc.EnergyContainerList;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.elements.ProgressBar;
import com.gregtechceu.gtceu.uipro.styletemplate.MachineEra;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;
import com.gregtechceu.gtceu.uiwidgets.multiblock.MultiblockPage;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.network.chat.Component;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class WorkableElectricMultiblockMachine extends WorkableMultiblockMachine implements IMultiblockFancyUIMachine, IOverclockMachine, IComputationContainerMachine, IElectricMachine {

    // runtime
    @Getter
    @NotNull
    protected EnergyContainerList energyContainer = EnergyContainerList.EMPTY;
    @NotNull
    protected ComputationProviderList computationProviderList = ComputationProviderList.EMPTY;
    @Getter
    protected int tier;

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
        var containers = getCapabilitiesFlat(IO.IN, GTCapability.ENERGY_CONTAINER);
        if (containers.isEmpty()) containers = getCapabilitiesFlat(IO.OUT, GTCapability.ENERGY_CONTAINER);
        List<IOpticalComputationProvider> providers = getCapabilitiesFlat(IO.IN, GTCapability.COMPUTATION_PROVIDER);
        if (!containers.isEmpty()) energyContainer = new EnergyContainerList(containers);
        if (!providers.isEmpty()) computationProviderList = new ComputationProviderList(providers);
        this.tier = GTUtil.getFloorTierByVoltage(getMaxVoltage());
    }

    //////////////////////////////////////
    // ********** GUI ***********//
    //////////////////////////////////////
    @Override
    public void addDisplayText(List<Component> textList) {
        long numParallels;
        long batchParallels;
        boolean exact = false;
        if (recipeLogic.isActive() && recipeLogic.getLastRecipe() != null) {
            numParallels = (int) recipeLogic.getLastRecipe().parallels;
            batchParallels = recipeLogic.getLastRecipe().batchParallels;
            exact = true;
        } else {
            numParallels = Optional.ofNullable(getParallelHatch()).map(IParallelHatch::getCurrentParallel).orElse(0L);
            batchParallels = 0;
        }
        MultiblockDisplayText.builder(textList, isFormed()).setWorkingStatus(recipeLogic.isWorkingEnabled(), recipeLogic.isActive()).addEnergyUsageLine(energyContainer).addEnergyTierLine(tier).addMachineModeLine(getRecipeType(), getAvailableRecipeTypes().length > 1).addParallelsLine(numParallels, exact).addBatchModeLine(isBatchEnabled(), batchParallels).addReasonLines(recipeLogic).addProgressLine(recipeLogic.getProgress(), recipeLogic.getMaxProgress(), recipeLogic.getProgressPercent()).addOutputLines(recipeLogic.getLastRecipe());
        getDefinition().getAdditionalDisplay().accept(this, textList);
        IMultiblockFancyUIMachine.super.addDisplayText(textList);
    }

    public static final String LANG_MAX_POWER = "gtceu.gui.multiblock.max_power";
    public static final String LANG_ENERGY_USAGE = "gtceu.gui.multiblock.energy_usage";
    public static final String LANG_ENERGY_OUTPUT = "gtceu.gui.multiblock.energy_output";
    public static final String LANG_ENERGY = "gtceu.gui.multiblock.energy";

    @Override
    public UIElement createUIWidget() {
        var page = MultiblockPage.of(this).setScreen(MachineEra.CLASSIC.getScreen());
        addPageContent(page);
        return page.build();
    }

    protected void addPageContent(MultiblockPage page) {
        page.addLine(LANG_MAX_POWER, MultiblockPage.cached(this::getMaxVoltage,
                voltage -> Component.literal(FormattingUtil.formatNumbers(voltage) + " EU/t (" + GTValues.VNF[GTUtil.getFloorTierByVoltage(voltage)] + "§r)")));
        boolean generator = isGenerator();
        page.addLine(generator ? LANG_ENERGY_OUTPUT : LANG_ENERGY_USAGE, MultiblockPage.cached(() -> getScreenEnergyRate(generator),
                usage -> Component.literal(FormattingUtil.formatNumbers(usage) + " EU/t")));
        page.addLine(MultiblockPage.PARALLEL, MultiblockPage.cached(() -> {
            var recipe = recipeLogic.isIdle() ? null : recipeLogic.getLastRecipe();
            return recipe == null ? 0 : recipe.parallels;
        }, parallels -> Component.literal(FormattingUtil.formatNumbers(parallels))));
        page.addBar(LANG_ENERGY, UITheme::barEnergy, () -> new ProgressBar.Progress(energyContainer.getEnergyStored(), Math.max(1, energyContainer.getEnergyCapacity()), 0)).percent()
                .bindDetail(MultiblockPage.cached(energyContainer::getEnergyStored, stored -> Component.literal(FormattingUtil.formatNumbers(stored) + " EU")));
        addScreenReadouts(page);
        page.addText(this::addScreenText, this::handleDisplayClick);
        addControls(page.getControls());
    }

    protected long getScreenEnergyRate(boolean generator) {
        var recipe = recipeLogic.isWorking() ? recipeLogic.getLastRecipe() : null;
        return recipe == null ? 0 : generator ? recipe.getOutputEUt() : recipe.getInputEUt();
    }

    protected void addScreenText(List<Component> textList) {}

    @Override
    public void attachConfigurators(ConfiguratorPanel configuratorPanel) {
        attachRecipeConfigurators(configuratorPanel);
        attachPowerConfigurator(configuratorPanel);
        attachCoverConfigurators(configuratorPanel);
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
}
