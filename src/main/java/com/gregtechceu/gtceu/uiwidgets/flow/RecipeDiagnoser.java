package com.gregtechceu.gtceu.uiwidgets.flow;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.api.recipe.handler.IRecipeHandlerHolder;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.ObjLongConsumer;

public final class RecipeDiagnoser {

    private final IRecipeHandlerHolder holder;
    private final long eut;
    private final int tier;
    private final FluidIngredient[] inputs;
    private final FluidStack[] inputStacks;
    private final long[] need;
    private final long[] available;
    private final long[] scratch;
    private final Component[] inputNames;
    private final Component[] inputAmounts;
    private final RecipeIssue[] inputIssues;
    private final List<List<Component>> inputDetails;
    private final FluidStack[] outputStacks;
    private final Component[] outputNames;
    private final Component[] outputAmounts;
    private final RecipeIssue[] outputIssues;
    private final List<List<Component>> outputDetails;
    private final ObjLongConsumer<FluidStack> collector;
    private RecipeIssue energyIssue = RecipeIssue.IDLE;
    private List<Component> energyDetail = Collections.emptyList();
    private long stored, capacity, power;
    private int energyTier = -1;

    public RecipeDiagnoser(IRecipeHandlerHolder holder, GTRecipeDefinition recipe) {
        this.holder = holder;
        this.eut = recipe.getInputEUt();
        this.tier = GTUtil.getTierByVoltage(eut);
        int count = recipe.fluidInputs.size();
        this.inputs = new FluidIngredient[count];
        this.inputStacks = new FluidStack[count];
        this.need = new long[count];
        this.available = new long[count];
        this.scratch = new long[count];
        this.inputNames = new Component[count];
        this.inputAmounts = new Component[count];
        this.inputIssues = new RecipeIssue[count];
        for (int i = 0; i < count; i++) {
            var content = recipe.fluidInputs.get(i);
            inputs[i] = content.inner;
            need[i] = content.amount;
            inputStacks[i] = content.inner.getFluidStack().copy();
            inputNames[i] = inputStacks[i].getDisplayName();
            inputAmounts[i] = amount(content.amount);
        }
        Arrays.fill(inputIssues, RecipeIssue.IDLE);
        this.inputDetails = new ArrayList<>(Collections.nCopies(count, Collections.emptyList()));
        int outputs = recipe.fluidOutputs.size();
        this.outputStacks = new FluidStack[outputs];
        this.outputNames = new Component[outputs];
        this.outputAmounts = new Component[outputs];
        this.outputIssues = new RecipeIssue[outputs];
        for (int i = 0; i < outputs; i++) {
            var content = recipe.fluidOutputs.get(i);
            outputStacks[i] = content.inner.getFluidStack().copy();
            outputStacks[i].setAmount((int) Math.min(Integer.MAX_VALUE, content.amount));
            outputNames[i] = outputStacks[i].getDisplayName();
            outputAmounts[i] = amount(content.amount);
        }
        Arrays.fill(outputIssues, RecipeIssue.IDLE);
        this.outputDetails = new ArrayList<>(Collections.nCopies(outputs, Collections.emptyList()));
        this.collector = (stack, amount) -> {
            var fluid = stack.getFluid();
            for (int i = 0; i < inputs.length; i++) {
                if (inputs[i].testFluid(fluid)) {
                    long sum = scratch[i] + amount;
                    scratch[i] = sum < 0 ? Long.MAX_VALUE : sum;
                    return;
                }
            }
        };
    }

    public static Component amount(long amount) {
        return Component.translatable("gtceu.flow.amount", FormattingUtil.formatNumbers(amount));
    }

    public long getEUt() {
        return eut;
    }

    public int getTier() {
        return tier;
    }

    public int inputCount() {
        return inputs.length;
    }

    public int outputCount() {
        return outputStacks.length;
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

    public long need(int index) {
        return need[index];
    }

    public long available(int index) {
        return available[index];
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

    public RecipeIssue energyIssue() {
        return energyIssue;
    }

    public List<Component> energyDetail() {
        return energyDetail;
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

    public int energyTier() {
        return energyTier;
    }

    public boolean inputsSatisfied() {
        for (int i = 0; i < need.length; i++) if (available[i] < need[i]) return false;
        return true;
    }

    public boolean outputsFit() {
        for (var issue : outputIssues) if (issue.isProblem()) return false;
        return true;
    }

    public boolean energySatisfied() {
        return !energyIssue.isProblem();
    }

    public void diagnoseFluids(boolean formed, boolean running) {
        Arrays.fill(available, 0);
        boolean inputHatch = false;
        if (formed) {
            int best = -1;
            var units = holder.getInputUnits();
            for (int u = 0; u < units.size(); u++) {
                var unit = units.get(u);
                if (unit.fluidHandlers.length == 0) continue;
                inputHatch = true;
                Arrays.fill(scratch, 0);
                unit.fastForEachFluids(true, collector);
                int satisfied = 0;
                for (int i = 0; i < need.length; i++) if (scratch[i] >= need[i]) satisfied++;
                if (satisfied > best) {
                    best = satisfied;
                    System.arraycopy(scratch, 0, available, 0, available.length);
                }
            }
        }
        for (int i = 0; i < need.length; i++) {
            boolean ok = available[i] >= need[i];
            RecipeIssue issue;
            if (!formed) issue = RecipeIssue.OFFLINE;
            else if (!inputHatch) issue = RecipeIssue.NO_INPUT_HATCH;
            else if (running) issue = ok ? RecipeIssue.INPUT_NEXT_OK : RecipeIssue.NEXT_SHORT;
            else issue = ok ? RecipeIssue.INPUT_STOCKED : RecipeIssue.INPUT_SHORT;
            inputIssues[i] = issue;
            inputDetails.set(i, List.of(inputNames[i],
                    Component.translatable("gtceu.flow.detail.required", inputAmounts[i]).withStyle(ChatFormatting.GRAY),
                    Component.translatable("gtceu.flow.detail.available", amount(available[i])).withStyle(ChatFormatting.GRAY),
                    Component.translatable(issue.descriptionKey()).withStyle(style(issue))));
        }
        var outputUnits = holder.getOutputUnits();
        boolean outputHatch = false;
        for (int u = 0; u < outputUnits.size() && !outputHatch; u++) outputHatch = outputUnits.get(u).fluidHandlers.length > 0;
        for (int i = 0; i < outputStacks.length; i++) {
            var stack = outputStacks[i];
            boolean fits = false;
            if (formed && outputHatch) {
                for (int u = 0; u < outputUnits.size() && !fits; u++) {
                    var unit = outputUnits.get(u);
                    if (unit.fluidHandlers.length > 0 && unit.simulateOutputFluid(stack.getFluid(), stack.getAmount())) fits = true;
                }
            }
            RecipeIssue issue;
            if (!formed) issue = RecipeIssue.OFFLINE;
            else if (!outputHatch) issue = RecipeIssue.NO_OUTPUT_HATCH;
            else if (!fits) issue = RecipeIssue.OUTPUT_FULL;
            else issue = running ? RecipeIssue.OUTPUT_ACTIVE : RecipeIssue.OUTPUT_CLEAR;
            outputIssues[i] = issue;
            outputDetails.set(i, List.of(outputNames[i],
                    Component.translatable("gtceu.flow.detail.produced", outputAmounts[i]).withStyle(ChatFormatting.GRAY),
                    Component.translatable(issue.descriptionKey()).withStyle(style(issue))));
        }
    }

    public void diagnoseEnergy(boolean formed, boolean running, IEnergyContainer container, long maxVoltage, int machineTier) {
        stored = container.getEnergyStored();
        capacity = container.getEnergyCapacity();
        power = container.getInputVoltage();
        energyTier = formed && capacity > 0 ? machineTier : -1;
        if (!formed) energyIssue = RecipeIssue.OFFLINE;
        else if (capacity <= 0) energyIssue = RecipeIssue.NO_ENERGY_HATCH;
        else if (maxVoltage < GTValues.V[tier]) energyIssue = RecipeIssue.LOW_VOLTAGE;
        else if (power < eut) energyIssue = RecipeIssue.LOW_POWER;
        else if (stored < eut) energyIssue = RecipeIssue.LOW_BUFFER;
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
