package com.gregtechceu.gtceu.common.machine.multiblock.electric;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.fancy.FancyMachineUIWidget;
import com.gregtechceu.gtceu.api.machine.ConditionalSubscriptionHandler;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IExplosionMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.INotifiableTrait;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableEnergyContainer;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.misc.EnergyContainerInfoList;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.utils.FormattingUtil;

import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.widget.*;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import static com.gregtechceu.gtceu.api.pattern.Predicates.abilities;

public class ActiveTransformerMachine extends WorkableElectricMultiblockMachine
                                      implements IExplosionMachine {

    private EnergyContainerInfoList powerOutput;
    private EnergyContainerInfoList powerInput;
    protected final ConditionalSubscriptionHandler converterSubscription;

    public ActiveTransformerMachine(MetaMachineBlockEntity holder) {
        super(holder);
        this.powerOutput = EnergyContainerInfoList.EMPTY;
        this.powerInput = EnergyContainerInfoList.EMPTY;

        this.converterSubscription = new ConditionalSubscriptionHandler(this, this::convertEnergyTick, 0, this::isSubscriptionActive);
    }

    public void convertEnergyTick() {
        if (isWorkingEnabled()) {
            getRecipeLogic().setStatus(isSubscriptionActive() ? RecipeLogic.WORKING : RecipeLogic.SUSPEND);
        }
        if (isWorkingEnabled()) {
            long canDrain = powerInput.getEnergyStored();
            long totalDrained = powerOutput.changeEnergy(canDrain);
            powerInput.removeEnergy(totalDrained);
        }
        converterSubscription.updateSubscription();
    }

    @SuppressWarnings("RedundantIfStatement") // It is cleaner to have the final return true separate.
    protected boolean isSubscriptionActive() {
        if (!isFormed()) return false;

        if (powerInput == null || powerInput.getEnergyStored() <= 0) return false;
        if (powerOutput == null) return false;
        if (powerOutput.getEnergyStored() >= powerOutput.getEnergyCapacity()) return false;

        return true;
    }

    @Override
    public boolean hasBatchConfig() {
        return false;
    }

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        // capture all energy containers
        List<IEnergyContainer> powerInput = new ArrayList<>();
        List<IEnergyContainer> powerOutput = new ArrayList<>();
        for (var part : getPrioritySortedParts()) {
            for (var trait : part.self().getTraits()) {
                if (trait instanceof NotifiableEnergyContainer container) {
                    if (container.getHandlerIO() == IO.IN) {
                        powerInput.add(container);
                    } else if (container.getHandlerIO() == IO.OUT) {
                        powerOutput.add(container);
                    }
                    INotifiableTrait.addListener(trait, converterSubscription::updateSubscription, traitSubscriptions::add);
                }
            }
        }

        // Invalidate the structure if there is not at least one output and one input
        if (powerInput.isEmpty() || powerOutput.isEmpty()) {
            this.onStructureInvalid();
        }

        this.powerOutput = new EnergyContainerInfoList(this, powerOutput);
        this.powerInput = new EnergyContainerInfoList(this, powerInput);
        this.powerOutput.onMachineLoad();
        this.powerInput.onMachineLoad();
        converterSubscription.updateSubscription();
    }

    @NotNull
    private List<IMultiPart> getPrioritySortedParts() {
        return Arrays.stream(getParts()).sorted(Comparator.comparingInt(part -> {
            if (part instanceof MetaMachine partMachine) {
                Block partBlock = partMachine.getBlockState().getBlock();

                if (PartAbility.OUTPUT_ENERGY.isApplicable(partBlock))
                    return 1;

                if (PartAbility.SUBSTATION_OUTPUT_ENERGY.isApplicable(partBlock))
                    return 2;

                if (PartAbility.OUTPUT_LASER.isApplicable(partBlock))
                    return 3;
            }

            return 4;
        })).toList();
    }

    @Override
    public void onStructureInvalid() {
        if ((isWorkingEnabled() && recipeLogic.getStatus() == RecipeLogic.WORKING) &&
                !ConfigHolder.INSTANCE.machines.harmlessActiveTransformers) {
            doExplosion(6f + getTier());
        }
        super.onStructureInvalid();
        this.powerOutput.onMachineUnLoad();
        this.powerInput.onMachineUnLoad();
        this.powerOutput = EnergyContainerInfoList.EMPTY;
        this.powerInput = EnergyContainerInfoList.EMPTY;
        getRecipeLogic().setStatus(RecipeLogic.SUSPEND);
        converterSubscription.unsubscribe();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        this.powerOutput.onMachineLoad();
        this.powerInput.onMachineLoad();
    }

    @Override
    public void onUnload() {
        super.onUnload();
        this.powerOutput.onMachineUnLoad();
        this.powerInput.onMachineUnLoad();
    }

    public static TraceabilityPredicate getHatchPredicates() {
        return abilities(PartAbility.INPUT_ENERGY).setPreviewCount(1)
                .or(abilities(PartAbility.OUTPUT_ENERGY).setPreviewCount(2))
                .or(abilities(PartAbility.SUBSTATION_INPUT_ENERGY).setPreviewCount(1))
                .or(abilities(PartAbility.SUBSTATION_OUTPUT_ENERGY).setPreviewCount(1))
                .or(abilities(PartAbility.INPUT_LASER).setPreviewCount(1))
                .or(abilities(PartAbility.OUTPUT_LASER).setPreviewCount(1));
    }

    @Override
    public void addDisplayText(List<Component> textList) {
        // super.addDisplayText(textList); idek what it does stop doing what you do for a minute pls
        // Assume That the Structure is ALWAYS formed, and has at least 1 In and 1 Out, there is never a case where this
        // does not occur.
        if (isFormed()) {
            if (!isWorkingEnabled()) {
                textList.add(Component.translatable("gtceu.multiblock.work_paused"));
            } else if (isActive()) {
                textList.add(Component.translatable("gtceu.multiblock.running"));
                textList.add(Component
                        .translatable("gtceu.multiblock.active_transformer.max_input",
                                FormattingUtil.formatNumbers(
                                        Math.abs(powerInput.getInputVoltage() * powerInput.getInputAmperage()))));
                textList.add(Component
                        .translatable("gtceu.multiblock.active_transformer.max_output",
                                FormattingUtil.formatNumbers(
                                        Math.abs(powerOutput.getOutputVoltage() * powerOutput.getOutputAmperage()))));
                textList.add(Component
                        .translatable("gtceu.multiblock.active_transformer.average_in",
                                FormattingUtil.formatNumbers(Math.abs(powerInput.getInputPerSec() / 20))));
                textList.add(Component
                        .translatable("gtceu.multiblock.active_transformer.average_out",
                                FormattingUtil.formatNumbers(Math.abs(powerOutput.getOutputPerSec() / 20))));
                if (!ConfigHolder.INSTANCE.machines.harmlessActiveTransformers) {
                    textList.add(Component
                            .translatable("gtceu.multiblock.active_transformer.danger_enabled"));
                }
            } else {
                textList.add(Component.translatable("gtceu.multiblock.idling"));
            }
        }
    }

    @Override
    public @NotNull Widget createUIWidget() {
        var group = new WidgetGroup(0, 0, 182 + 8, 117 + 8);
        group.addWidget(new DraggableScrollableWidgetGroup(4, 4, 182, 117).setBackground(getScreenTexture())
                .addWidget(new LabelWidget(4, 5, self().getBlockState().getBlock().getDescriptionId()))
                .addWidget(new ComponentPanelWidget(4, 17, this::addDisplayText)
                        .setMaxWidthLimit(150)
                        .clickHandler(this::handleDisplayClick)));
        group.setBackground(GuiTextures.BACKGROUND_INVERSE);
        return group;
    }

    @Override
    public @NotNull ModularUI createUI(@NotNull Player entityPlayer) {
        return new ModularUI(198, 208, this, entityPlayer).widget(new FancyMachineUIWidget(this, 198, 208));
    }
}
