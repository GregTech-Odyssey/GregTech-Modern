package com.gregtechceu.gtceu.uiwidgets.flow;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.machine.feature.IElectricMachine;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.feature.ITieredMachine;
import com.gregtechceu.gtceu.api.machine.feature.IVoidable;
import com.gregtechceu.gtceu.api.machine.issue.DiagnosisEntry;
import com.gregtechceu.gtceu.api.machine.issue.DiagnosisState;
import com.gregtechceu.gtceu.api.machine.issue.MachineDiagnosis;
import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.recipe.handler.IRecipeHandlerHolder;
import com.gregtechceu.gtceu.api.recipe.handler.RecipeHandlerUnit;
import com.gregtechceu.gtceu.api.recipe.info.FluidRecipeInfo;
import com.gregtechceu.gtceu.api.recipe.info.ItemRecipeInfo;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.common.data.GTRecipeDataKeys;
import com.gregtechceu.gtceu.uipro.Level;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 流程图节点的配方诊断适配层：对固定配方调用第二级诊断（{@link MachineDiagnosis}），再按节点语境（是否成型、是否运行中）映射成 {@link RecipeIssue}。
 */
public final class RecipeDiagnoser {

    private final IRecipeHandlerHolder holder;
    private final GTRecipeDefinition recipe;
    private final long eut;
    private final int tier;
    private final long cwut;
    private final FluidIngredient[] inputs;
    private final FluidStack[] inputStacks;
    private final long[] need;
    private final long[] available;
    private final Component[] inputNames;
    private final Component[] inputAmounts;
    private final RecipeIssue[] inputIssues;
    private final List<List<Component>> inputDetails;
    private final FluidStack[] outputStacks;
    private final Component[] outputNames;
    private final Component[] outputAmounts;
    private final RecipeIssue[] outputIssues;
    private final List<List<Component>> outputDetails;
    private final ItemStack[] itemInputStacks;
    private final long[] itemNeed;
    private final long[] itemAvailable;
    private final Component[] itemInputNames;
    private final Component[] itemInputAmounts;
    private final RecipeIssue[] itemInputIssues;
    private final List<List<Component>> itemInputDetails;
    private final ItemStack[] itemOutputStacks;
    private final Component[] itemOutputNames;
    private final Component[] itemOutputAmounts;
    private final RecipeIssue[] itemOutputIssues;
    private final List<List<Component>> itemOutputDetails;
    private final RecipeIssue[] conditionIssues;
    private final List<List<Component>> conditionDetails;
    private RecipeHandlerUnit unit = RecipeHandlerUnit.NO_DATA;
    private List<DiagnosisEntry> materialEntries = Collections.emptyList();
    private List<DiagnosisEntry> conditionEntries = Collections.emptyList();
    private List<DiagnosisEntry> computationEntries = Collections.emptyList();
    private List<DiagnosisEntry> energyEntries = Collections.emptyList();
    private RecipeIssue energyIssue = RecipeIssue.IDLE;
    private List<Component> energyDetail = Collections.emptyList();
    private RecipeIssue computationIssue = RecipeIssue.IDLE;
    private List<Component> computationDetail = Collections.emptyList();
    private long stored, capacity, power, computation;
    private int energyTier = -1;
    private Level voltageLevel = Level.NORMAL;

    public RecipeDiagnoser(IRecipeHandlerHolder holder, GTRecipeDefinition recipe) {
        this.holder = holder;
        this.recipe = recipe;
        this.eut = recipe.getInputEUt();
        this.tier = GTUtil.getTierByVoltage(eut);
        this.cwut = recipe.data.getLong(GTRecipeDataKeys.CWUT);
        int count = recipe.fluidInputs.size();
        this.inputs = new FluidIngredient[count];
        this.inputStacks = new FluidStack[count];
        this.need = new long[count];
        this.available = new long[count];
        this.inputNames = new Component[count];
        this.inputAmounts = new Component[count];
        this.inputIssues = filled(count);
        for (int i = 0; i < count; i++) {
            var content = recipe.fluidInputs.get(i);
            inputs[i] = content.inner;
            need[i] = content.amount;
            inputStacks[i] = content.inner.getFluidStack().copy();
            inputNames[i] = inputStacks[i].getDisplayName();
            inputAmounts[i] = amount(content.amount);
        }
        this.inputDetails = details(count);
        int outputs = recipe.fluidOutputs.size();
        this.outputStacks = new FluidStack[outputs];
        this.outputNames = new Component[outputs];
        this.outputAmounts = new Component[outputs];
        this.outputIssues = filled(outputs);
        for (int i = 0; i < outputs; i++) {
            var content = recipe.fluidOutputs.get(i);
            outputStacks[i] = content.inner.getFluidStack().copy();
            outputStacks[i].setAmount((int) Math.min(Integer.MAX_VALUE, content.amount));
            outputNames[i] = outputStacks[i].getDisplayName();
            outputAmounts[i] = amount(content.amount);
        }
        this.outputDetails = details(outputs);
        int items = recipe.itemInputs.size();
        this.itemInputStacks = new ItemStack[items];
        this.itemNeed = new long[items];
        this.itemAvailable = new long[items];
        this.itemInputNames = new Component[items];
        this.itemInputAmounts = new Component[items];
        this.itemInputIssues = filled(items);
        for (int i = 0; i < items; i++) {
            var content = recipe.itemInputs.get(i);
            itemInputStacks[i] = displayStack(content.inner.getInnerItemStack(), content.amount);
            itemNeed[i] = content.amount;
            itemInputNames[i] = content.inner.getName();
            itemInputAmounts[i] = count(content.amount);
        }
        this.itemInputDetails = details(items);
        int itemOutputs = recipe.itemOutputs.size();
        this.itemOutputStacks = new ItemStack[itemOutputs];
        this.itemOutputNames = new Component[itemOutputs];
        this.itemOutputAmounts = new Component[itemOutputs];
        this.itemOutputIssues = filled(itemOutputs);
        for (int i = 0; i < itemOutputs; i++) {
            var content = recipe.itemOutputs.get(i);
            itemOutputStacks[i] = displayStack(content.inner.getInnerItemStack(), content.amount);
            itemOutputNames[i] = content.inner.getName();
            itemOutputAmounts[i] = count(content.amount);
        }
        this.itemOutputDetails = details(itemOutputs);
        int conditions = recipe.conditions.length;
        this.conditionIssues = filled(conditions);
        this.conditionDetails = details(conditions);
    }

    private static RecipeIssue[] filled(int count) {
        var array = new RecipeIssue[count];
        Arrays.fill(array, RecipeIssue.IDLE);
        return array;
    }

    private static List<List<Component>> details(int count) {
        return new ArrayList<>(Collections.nCopies(count, Collections.emptyList()));
    }

    private static ItemStack displayStack(ItemStack stack, long amount) {
        var copy = stack.copy();
        if (!copy.isEmpty()) copy.setCount((int) Math.max(1, Math.min(copy.getMaxStackSize(), amount)));
        return copy;
    }

    public static Component amount(long amount) {
        return Component.translatable("gtceu.flow.amount", FormattingUtil.formatNumbers(amount));
    }

    public static Component count(long count) {
        return Component.translatable("gtceu.flow.count", FormattingUtil.formatNumbers(count));
    }

    public GTRecipeDefinition getRecipe() {
        return recipe;
    }

    public long getEUt() {
        return eut;
    }

    public int getTier() {
        return tier;
    }

    public long getCWUt() {
        return cwut;
    }

    public int inputCount() {
        return inputs.length;
    }

    public int outputCount() {
        return outputStacks.length;
    }

    public int itemInputCount() {
        return itemInputStacks.length;
    }

    public int itemOutputCount() {
        return itemOutputStacks.length;
    }

    public int conditionCount() {
        return conditionIssues.length;
    }

    public int findInput(FluidStack fluid) {
        for (int i = 0; i < inputs.length; i++) if (inputs[i].testFluid(fluid.getFluid())) return i;
        return -1;
    }

    public FluidStack inputStack(int index) {
        return inputStacks[index].copy();
    }

    public FluidStack outputStack(int index) {
        return outputStacks[index].copy();
    }

    public ItemStack itemInputStack(int index) {
        return itemInputStacks[index].copy();
    }

    public ItemStack itemOutputStack(int index) {
        return itemOutputStacks[index].copy();
    }

    public Component inputName(int index) {
        return inputNames[index];
    }

    public Component inputAmount(int index) {
        return inputAmounts[index];
    }

    public Component outputName(int index) {
        return outputNames[index];
    }

    public Component outputAmount(int index) {
        return outputAmounts[index];
    }

    public Component itemInputName(int index) {
        return itemInputNames[index];
    }

    public Component itemInputAmount(int index) {
        return itemInputAmounts[index];
    }

    public Component itemOutputName(int index) {
        return itemOutputNames[index];
    }

    public Component itemOutputAmount(int index) {
        return itemOutputAmounts[index];
    }

    public long need(int index) {
        return need[index];
    }

    public long available(int index) {
        return available[index];
    }

    public long itemNeed(int index) {
        return itemNeed[index];
    }

    public long itemAvailable(int index) {
        return itemAvailable[index];
    }

    public RecipeIssue inputIssue(int index) {
        return inputIssues[index];
    }

    public List<Component> inputDetail(int index) {
        return inputDetails.get(index);
    }

    public RecipeIssue outputIssue(int index) {
        return outputIssues[index];
    }

    public List<Component> outputDetail(int index) {
        return outputDetails.get(index);
    }

    public RecipeIssue itemInputIssue(int index) {
        return itemInputIssues[index];
    }

    public List<Component> itemInputDetail(int index) {
        return itemInputDetails.get(index);
    }

    public RecipeIssue itemOutputIssue(int index) {
        return itemOutputIssues[index];
    }

    public List<Component> itemOutputDetail(int index) {
        return itemOutputDetails.get(index);
    }

    public RecipeIssue conditionIssue(int index) {
        return conditionIssues[index];
    }

    public List<Component> conditionDetail(int index) {
        return conditionDetails.get(index);
    }

    public RecipeIssue energyIssue() {
        return energyIssue;
    }

    public List<Component> energyDetail() {
        return energyDetail;
    }

    public RecipeIssue computationIssue() {
        return computationIssue;
    }

    public List<Component> computationDetail() {
        return computationDetail;
    }

    public long stored() {
        return stored;
    }

    public long capacity() {
        return capacity;
    }

    public long power() {
        return power;
    }

    public long computation() {
        return computation;
    }

    public int energyTier() {
        return energyTier;
    }

    public Level voltageLevel() {
        return voltageLevel;
    }

    public List<DiagnosisEntry> entries() {
        var list = new ArrayList<DiagnosisEntry>(conditionEntries.size() + energyEntries.size() + computationEntries.size() + materialEntries.size());
        list.addAll(conditionEntries);
        list.addAll(energyEntries);
        list.addAll(computationEntries);
        list.addAll(materialEntries);
        return list;
    }

    public boolean inputsSatisfied() {
        for (int i = 0; i < need.length; i++) if (available[i] < need[i]) return false;
        for (int i = 0; i < itemNeed.length; i++) if (itemAvailable[i] < itemNeed[i]) return false;
        return true;
    }

    public boolean outputsFit() {
        for (var issue : outputIssues) if (issue.isProblem()) return false;
        for (var issue : itemOutputIssues) if (issue.isProblem()) return false;
        return true;
    }

    public boolean energySatisfied() {
        return !energyIssue.isProblem();
    }

    public boolean computationSatisfied() {
        return !computationIssue.isProblem();
    }

    public boolean conditionsSatisfied() {
        for (var issue : conditionIssues) if (issue.isProblem()) return false;
        return true;
    }

    public boolean satisfied() {
        return conditionsSatisfied() && energySatisfied() && computationSatisfied() && inputsSatisfied() && outputsFit();
    }

    public void diagnose(boolean formed, boolean running) {
        diagnoseFluids(formed, running);
        diagnoseConditions(formed);
        diagnoseComputation(formed, running);
        if (recipe.eut == 0) {
            energyEntries = Collections.emptyList();
            voltageLevel = Level.NORMAL;
            energyIssue = formed ? RecipeIssue.IDLE : RecipeIssue.OFFLINE;
            energyDetail = Collections.emptyList();
            return;
        }
        var list = new ArrayList<DiagnosisEntry>(3);
        if (formed) MachineDiagnosis.diagnoseEnergy(holder, recipe, recipe.eut, list);
        var container = holder instanceof IElectricMachine electric ? electric.getEnergyContainer() : null;
        applyEnergy(formed, running, container, list, holder instanceof ITieredMachine tiered ? tiered.getTier() : -1);
    }

    public void diagnoseFluids(boolean formed, boolean running) {
        List<DiagnosisEntry> list;
        if (formed) {
            list = new ArrayList<>();
            var preferred = holder instanceof IRecipeLogicMachine machine ? machine.getRecipeLogic().getLastOriginUnit() : null;
            unit = MachineDiagnosis.diagnoseInputs(holder, recipe.itemInputs, recipe.fluidInputs, preferred, false, list);
            var runtime = MachineDiagnosis.runtime(holder, recipe, unit);
            MachineDiagnosis.confirmInputs(unit, runtime, list);
            MachineDiagnosis.diagnoseOutputs(holder, runtime, list);
        } else {
            list = Collections.emptyList();
            unit = RecipeHandlerUnit.NO_DATA;
        }
        materialEntries = list;
        Arrays.fill(available, 0);
        Arrays.fill(itemAvailable, 0);
        var fluidIn = new DiagnosisEntry[inputs.length];
        var itemIn = new DiagnosisEntry[itemInputStacks.length];
        var fluidOut = new DiagnosisEntry[outputStacks.length];
        var itemOut = new DiagnosisEntry[itemOutputStacks.length];
        for (var entry : list) {
            int index = entry.subject().index();
            if (index < 0) continue;
            DiagnosisEntry[] target = null;
            if (entry.is(IO.IN, FluidRecipeInfo.INSTANCE)) target = fluidIn;
            else if (entry.is(IO.IN, ItemRecipeInfo.INSTANCE)) target = itemIn;
            else if (entry.is(IO.OUT, FluidRecipeInfo.INSTANCE)) target = fluidOut;
            else if (entry.is(IO.OUT, ItemRecipeInfo.INSTANCE)) target = itemOut;
            if (target != null && index < target.length) target[index] = entry;
        }
        for (int i = 0; i < fluidIn.length; i++) {
            if (fluidIn[i] != null) available[i] = supplied(fluidIn[i]);
            var issue = inputIssue(formed, running, fluidIn[i]);
            inputIssues[i] = issue;
            inputDetails.set(i, List.of(inputNames[i],
                    Component.translatable("gtceu.flow.detail.required", inputAmounts[i]).withStyle(ChatFormatting.GRAY),
                    Component.translatable("gtceu.flow.detail.available", amount(available[i])).withStyle(ChatFormatting.GRAY),
                    Component.translatable(issue.descriptionKey()).withStyle(style(issue))));
        }
        for (int i = 0; i < itemIn.length; i++) {
            if (itemIn[i] != null) itemAvailable[i] = supplied(itemIn[i]);
            var issue = inputIssue(formed, running, itemIn[i]);
            itemInputIssues[i] = issue;
            itemInputDetails.set(i, List.of(itemInputNames[i],
                    Component.translatable("gtceu.flow.detail.required", itemInputAmounts[i]).withStyle(ChatFormatting.GRAY),
                    Component.translatable("gtceu.flow.detail.available", count(itemAvailable[i])).withStyle(ChatFormatting.GRAY),
                    Component.translatable(issue.descriptionKey()).withStyle(style(issue))));
        }
        boolean voidFluids = holder instanceof IVoidable voidable && voidable.canVoidRecipeOutputs(FluidRecipeInfo.INSTANCE);
        boolean voidItems = holder instanceof IVoidable voidable && voidable.canVoidRecipeOutputs(ItemRecipeInfo.INSTANCE);
        for (int i = 0; i < fluidOut.length; i++) {
            var issue = outputIssue(formed, running, voidFluids, fluidOut[i]);
            outputIssues[i] = issue;
            outputDetails.set(i, List.of(outputNames[i],
                    Component.translatable("gtceu.flow.detail.produced", outputAmounts[i]).withStyle(ChatFormatting.GRAY),
                    Component.translatable(issue.descriptionKey()).withStyle(style(issue))));
        }
        for (int i = 0; i < itemOut.length; i++) {
            var issue = outputIssue(formed, running, voidItems, itemOut[i]);
            itemOutputIssues[i] = issue;
            itemOutputDetails.set(i, List.of(itemOutputNames[i],
                    Component.translatable("gtceu.flow.detail.produced", itemOutputAmounts[i]).withStyle(ChatFormatting.GRAY),
                    Component.translatable(issue.descriptionKey()).withStyle(style(issue))));
        }
    }

    private static long supplied(DiagnosisEntry entry) {
        return entry.isOk() && entry.have() < 0 ? entry.need() : Math.max(0, entry.have());
    }

    private static RecipeIssue inputIssue(boolean formed, boolean running, @Nullable DiagnosisEntry entry) {
        if (!formed || entry == null) return RecipeIssue.OFFLINE;
        return switch (entry.state()) {
            case MISSING_HANDLER -> RecipeIssue.NO_INPUT_HATCH;
            case OK -> running ? RecipeIssue.INPUT_NEXT_OK : RecipeIssue.INPUT_STOCKED;
            default -> running ? RecipeIssue.NEXT_SHORT : RecipeIssue.INPUT_SHORT;
        };
    }

    private static RecipeIssue outputIssue(boolean formed, boolean running, boolean voiding, @Nullable DiagnosisEntry entry) {
        if (!formed || entry == null) return RecipeIssue.OFFLINE;
        return switch (entry.state()) {
            case VOIDED -> RecipeIssue.OUTPUT_VOIDED;
            case MISSING_HANDLER -> RecipeIssue.NO_OUTPUT_HATCH;
            case BLOCKED, INSUFFICIENT -> RecipeIssue.OUTPUT_FULL;
            case SKIPPED -> RecipeIssue.IDLE;
            default -> voiding ? running ? RecipeIssue.OUTPUT_VOID_OVERFLOW_ACTIVE : RecipeIssue.OUTPUT_VOID_OVERFLOW :
                    running ? RecipeIssue.OUTPUT_ACTIVE : RecipeIssue.OUTPUT_CLEAR;
        };
    }

    public void diagnoseConditions(boolean formed) {
        if (conditionIssues.length == 0) return;
        List<DiagnosisEntry> list;
        if (formed) {
            list = new ArrayList<>(conditionIssues.length);
            var conditionUnit = unit;
            if (conditionUnit == RecipeHandlerUnit.NO_DATA && !holder.getInputUnits().isEmpty()) conditionUnit = holder.getInputUnits().getFirst();
            MachineDiagnosis.diagnoseConditions(holder, conditionUnit, recipe, list);
        } else {
            list = Collections.emptyList();
        }
        conditionEntries = list;
        Arrays.fill(conditionIssues, formed ? RecipeIssue.IDLE : RecipeIssue.OFFLINE);
        for (int i = 0; i < conditionIssues.length; i++) conditionDetails.set(i, Collections.emptyList());
        for (var entry : list) {
            int index = entry.subject().index();
            if (index < 0 || index >= conditionIssues.length) continue;
            var issue = switch (entry.state()) {
                case OK -> RecipeIssue.OK;
                case SKIPPED -> RecipeIssue.IDLE;
                default -> RecipeIssue.CONDITION;
            };
            conditionIssues[index] = issue;
            var lines = new ArrayList<Component>(3);
            if (entry.label() != null) lines.add(entry.label());
            if (entry.detail() != null) lines.add(entry.detail().copy().withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable(issue.descriptionKey()).withStyle(style(issue)));
            conditionDetails.set(index, lines);
        }
    }

    public void diagnoseComputation(boolean formed, boolean running) {
        if (cwut < 1) return;
        List<DiagnosisEntry> list;
        if (formed) {
            list = new ArrayList<>(1);
            MachineDiagnosis.diagnoseComputation(holder, cwut, list);
        } else {
            list = Collections.emptyList();
        }
        computationEntries = list;
        var entry = list.isEmpty() ? null : list.getFirst();
        computation = entry == null ? 0 : Math.max(0, entry.have());
        if (!formed || entry == null) computationIssue = RecipeIssue.OFFLINE;
        else if (entry.isProblem()) computationIssue = RecipeIssue.NO_CWU;
        else computationIssue = running ? RecipeIssue.RUNNING : RecipeIssue.COMPUTATION_READY;
        computationDetail = List.of(Component.translatable("gtceu.flow.detail.cwu", FormattingUtil.formatNumbers(computation), FormattingUtil.formatNumbers(cwut)).withStyle(ChatFormatting.GRAY),
                Component.translatable(computationIssue.descriptionKey()).withStyle(style(computationIssue)));
    }

    public void diagnoseEnergy(boolean formed, boolean running, IEnergyContainer container, long maxVoltage, int machineTier) {
        var list = new ArrayList<DiagnosisEntry>(3);
        if (formed && eut > 0) {
            int recipeTier = holder instanceof IElectricMachine electric ? electric.getRecipeTier() : maxVoltage <= 0 ? -1 : GTUtil.getFloorTierByVoltage(maxVoltage);
            MachineDiagnosis.diagnoseEnergy(holder, container, recipeTier, recipe, eut, list);
        }
        applyEnergy(formed, running, container, list, machineTier);
    }

    private void applyEnergy(boolean formed, boolean running, @Nullable IEnergyContainer container, List<DiagnosisEntry> list, int machineTier) {
        energyEntries = list;
        stored = container == null ? 0 : container.getEnergyStored();
        capacity = container == null ? 0 : container.getEnergyCapacity();
        power = container == null ? 0 : MachineDiagnosis.saturatedMultiply(container.getInputVoltage(), Math.max(1, container.getInputAmperage()));
        energyTier = formed && capacity > 0 ? machineTier : -1;
        DiagnosisEntry voltage = null, powerEntry = null, buffer = null, missing = null, output = null;
        for (var entry : list) {
            if (entry.state() == DiagnosisState.MISSING_HANDLER) missing = entry;
            else if (entry.subject().index() == MachineDiagnosis.EU_VOLTAGE) voltage = entry;
            else if (entry.subject().index() == MachineDiagnosis.EU_POWER) powerEntry = entry;
            else if (entry.subject().index() == MachineDiagnosis.EU_BUFFER) buffer = entry;
            else if (entry.subject().index() == MachineDiagnosis.EU_OUTPUT) output = entry;
        }
        voltageLevel = !formed || voltage == null ? Level.NORMAL : voltage.isOk() ? Level.GOOD : voltage.severity().level();
        if (!formed) energyIssue = RecipeIssue.OFFLINE;
        else if (missing != null || capacity <= 0) energyIssue = RecipeIssue.NO_ENERGY_HATCH;
        else if (voltage != null && voltage.isProblem()) energyIssue = RecipeIssue.LOW_VOLTAGE;
        else if (powerEntry != null && powerEntry.isProblem()) energyIssue = RecipeIssue.LOW_POWER;
        else if (buffer != null && buffer.isProblem()) energyIssue = RecipeIssue.LOW_BUFFER;
        else if (output != null && output.isProblem()) energyIssue = RecipeIssue.ENERGY_FULL;
        else if (powerEntry != null && powerEntry.isWarning()) energyIssue = RecipeIssue.POWER_LIMITED;
        else if (voltage != null && voltage.isWarning()) energyIssue = RecipeIssue.VOLTAGE_LIMITED;
        else energyIssue = running ? RecipeIssue.ENERGY_ACTIVE : RecipeIssue.ENERGY_READY;
        energyDetail = List.of(Component.translatable("gtceu.flow.detail.usage", FormattingUtil.formatNumbers(eut)).withStyle(ChatFormatting.GRAY),
                Component.translatable("gtceu.flow.detail.tier", tierName(energyTier), GTValues.VNF[tier]).withStyle(ChatFormatting.GRAY),
                Component.translatable("gtceu.flow.detail.power", FormattingUtil.formatNumbers(power)).withStyle(ChatFormatting.GRAY),
                Component.translatable("gtceu.flow.detail.buffer", FormattingUtil.formatNumbers(stored), FormattingUtil.formatNumbers(capacity)).withStyle(ChatFormatting.GRAY),
                Component.translatable(energyIssue.descriptionKey()).withStyle(style(energyIssue)));
    }

    public static String tierName(int tier) {
        return tier < 0 || tier >= GTValues.VNF.length ? "—" : GTValues.VNF[tier];
    }

    public static ChatFormatting style(RecipeIssue issue) {
        return switch (issue.state()) {
            case ACTIVE, READY -> ChatFormatting.GREEN;
            case WARNING -> ChatFormatting.GOLD;
            case MISSING -> ChatFormatting.RED;
            default -> ChatFormatting.GRAY;
        };
    }
}
