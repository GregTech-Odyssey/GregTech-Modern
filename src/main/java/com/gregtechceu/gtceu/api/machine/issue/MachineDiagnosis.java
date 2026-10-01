package com.gregtechceu.gtceu.api.machine.issue;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IComputationContainerMachine;
import com.gregtechceu.gtceu.api.machine.feature.IElectricMachine;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.feature.ITieredMachine;
import com.gregtechceu.gtceu.api.machine.feature.IVoidable;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.recipe.handler.IRecipeHandler;
import com.gregtechceu.gtceu.api.recipe.handler.IRecipeHandlerHolder;
import com.gregtechceu.gtceu.api.recipe.handler.RecipeHandlerUnit;
import com.gregtechceu.gtceu.api.recipe.info.CWURecipeInfo;
import com.gregtechceu.gtceu.api.recipe.info.EURecipeInfo;
import com.gregtechceu.gtceu.api.recipe.info.FluidRecipeInfo;
import com.gregtechceu.gtceu.api.recipe.info.ItemRecipeInfo;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.api.recipe.ingredient.ItemIngredient;
import com.gregtechceu.gtceu.common.data.GTRecipeDataKeys;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.ObjLongConsumer;

/**
 * 第二级诊断：按需、节流地对目标配方逐项只读检查（只用处理器分组的模拟接口，不调机器钩子与配方修饰），并合并机器级常驻问题与第一级主因。
 */
public final class MachineDiagnosis {

    public static final int INTERVAL = 20;
    public static final int CHANGED_INTERVAL = 5;
    public static final int SCORE_LIMIT = 64;
    public static final int EU_VOLTAGE = 0;
    public static final int EU_POWER = 1;
    public static final int EU_BUFFER = 2;
    public static final int EU_OUTPUT = 3;
    public static final int TIER_FROM_VOLTAGE = Integer.MIN_VALUE;

    private MachineDiagnosis() {}

    public static DiagnosisResult of(IRecipeLogicMachine machine) {
        var logic = machine.getRecipeLogic();
        var cached = logic.getDiagnosisCache();
        var self = machine.self();
        if (self.isRemote()) return cached == null ? DiagnosisResult.EMPTY : cached;
        int now = self.getOffsetTimer();
        if (cached != null) {
            int age = now - cached.time();
            int interval = cached.basis() == logic.getIssueSnapshot().version() ? INTERVAL : CHANGED_INTERVAL;
            if (age >= 0 && age < interval) return cached;
        }
        DiagnosisResult result;
        try {
            result = compute(machine, now);
        } catch (RuntimeException e) {
            GTCEu.LOGGER.error("Failed to diagnose machine {} at {}", self.getDefinition().getId(), self.getPos(), e);
            var snapshot = logic.getIssueSnapshot();
            result = new DiagnosisResult(snapshot, null, DiagnosisTarget.NONE, Collections.emptyList(), Collections.emptyList(), snapshot.primary(), now);
        }
        if (result.equals(cached)) {
            cached.restamp(now, result.basis());
            return cached;
        }
        logic.setDiagnosisCache(result);
        return result;
    }

    public static DiagnosisResult compute(IRecipeLogicMachine machine, int time) {
        var logic = machine.getRecipeLogic();
        var snapshot = logic.getIssueSnapshot();
        GTRecipe running = null;
        GTRecipeDefinition recipe = null;
        var target = DiagnosisTarget.NONE;
        var last = logic.getLastRecipe();
        if (logic.isActive() && last != null) {
            running = last;
            recipe = last.definition;
            target = DiagnosisTarget.RUNNING;
        } else if (logic.isRecipeLocked() && logic.getLockedRecipe() != null) {
            recipe = logic.getLockedRecipe();
            target = DiagnosisTarget.LOCKED;
        } else if (snapshot.primary() != null && snapshot.primary().recipe() != null) {
            recipe = snapshot.primary().recipe();
            target = DiagnosisTarget.REPORTED;
        } else {
            var provided = machine.getDiagnosisRecipe();
            if (provided != null) {
                recipe = provided;
                target = DiagnosisTarget.MACHINE;
            } else if (logic.getLastRunRecipe() != null) {
                recipe = logic.getLastRunRecipe();
                target = DiagnosisTarget.LAST;
            }
        }
        List<DiagnosisEntry> entries = recipe == null ? Collections.emptyList() : diagnoseRecipe(machine, recipe, running, logic.getLastOriginUnit());
        var issues = new ArrayList<MachineIssue>();
        var reported = snapshot.primary();
        if (reported != null) issues.add(reported);
        collectIssues(machine.self(), issue -> {
            if (reported != null && issue.type() == reported.type()) return;
            if (!issues.contains(issue)) issues.add(issue);
        });
        issues.sort((x, y) -> y.severity().ordinal() - x.severity().ordinal());
        var primary = choosePrimary(snapshot, issues, entries, recipe);
        return new DiagnosisResult(snapshot, recipe, target, entries, issues, primary, time);
    }

    @Nullable
    private static MachineIssue choosePrimary(IssueSnapshot snapshot, List<MachineIssue> issues, List<DiagnosisEntry> entries, @Nullable GTRecipeDefinition recipe) {
        var reported = snapshot.primary();
        if (reported != null && !snapshot.isWorking() && reported.type() != GTIssues.NO_RECIPE) return reported;
        for (var issue : issues) if (issue.isBlocking()) return issue;
        if (!snapshot.isWorking()) {
            for (var entry : entries) {
                if (!entry.isProblem()) continue;
                var issue = entry.toIssue(recipe);
                if (issue != null) return issue;
            }
        }
        for (var issue : issues) if (issue.severity() == IssueSeverity.WARNING) return issue;
        if (!snapshot.isWorking()) {
            for (var entry : entries) {
                if (!entry.isWarning()) continue;
                var issue = entry.toIssue(recipe);
                if (issue != null) return issue;
            }
        }
        return snapshot.isWorking() || issues.isEmpty() ? null : issues.getFirst();
    }

    public static List<DiagnosisEntry> diagnoseRecipe(IRecipeHandlerHolder holder, GTRecipeDefinition recipe, @Nullable GTRecipe running, @Nullable RecipeHandlerUnit preferred) {
        var inputs = new ArrayList<DiagnosisEntry>();
        var unit = running != null ? diagnoseInputs(holder, running.itemInputs, running.fluidInputs, preferred, true, inputs) :
                diagnoseInputs(holder, recipe.itemInputs, recipe.fluidInputs, preferred, false, inputs);
        var runtime = running != null ? running : runtime(holder, recipe, unit);
        if (running == null) confirmInputs(unit, runtime, inputs);
        else markNextRound(inputs);
        long eut = running != null ? running.eut : recipe.eut;
        long cwut = running != null ? running.getInputCWUt() : recipe.data.getLong(GTRecipeDataKeys.CWUT);
        var entries = new ArrayList<DiagnosisEntry>();
        diagnoseTier(holder, recipe, eut, entries);
        diagnoseConditions(holder, unit, recipe, entries);
        diagnoseEnergy(holder, recipe, eut, entries);
        diagnoseComputation(holder, cwut, entries);
        entries.addAll(inputs);
        diagnoseOutputs(holder, runtime, entries);
        return entries;
    }

    public static GTRecipe runtime(IRecipeHandlerHolder holder, GTRecipeDefinition recipe, RecipeHandlerUnit unit) {
        var runtime = recipe.toRuntime();
        if (unit.color != -1) runtime.outputColor = unit.color;
        if (holder instanceof IVoidable voidable) RecipeHelper.trimRecipeOutputs(runtime, voidable.getOutputLimits());
        return runtime;
    }

    public static void collectIssues(MetaMachine machine, IssueSink sink) {
        collectFrom(machine, sink);
        if (machine instanceof IMultiController controller && controller.isFormed()) {
            for (var part : controller.getParts()) {
                var partMachine = part.self();
                if (partMachine != machine) collectFrom(partMachine, sink);
            }
        }
    }

    private static void collectFrom(MetaMachine machine, IssueSink sink) {
        if (machine instanceof IIssueProvider provider) provider.collectIssues(sink);
        for (var trait : machine.getTraits()) {
            if (trait instanceof IIssueProvider provider) provider.collectIssues(sink);
        }
    }

    public static void diagnoseTier(IRecipeHandlerHolder holder, GTRecipeDefinition recipe, long eut, List<DiagnosisEntry> entries) {
        int tier = recipe.tier;
        if (tier <= 0 || !(holder instanceof ITieredMachine tiered)) return;
        if (eut > 0 && holder instanceof IElectricMachine) return;
        int have = tiered.getRecipeTier();
        entries.add(DiagnosisEntry.of(IO.NONE, null, -1, tier <= have ? DiagnosisState.OK : DiagnosisState.BLOCKED, tier, have, GTIssues.LOW_TIER));
    }

    public static void diagnoseConditions(IRecipeHandlerHolder holder, RecipeHandlerUnit unit, GTRecipeDefinition recipe, List<DiagnosisEntry> entries) {
        var conditions = recipe.conditions;
        if (conditions.length == 0) return;
        var passed = new boolean[conditions.length];
        var checked = new boolean[conditions.length];
        for (int i = 0; i < conditions.length; i++) {
            var condition = conditions[i];
            if (!condition.isDiagnosable()) continue;
            checked[i] = true;
            passed[i] = condition.check(holder, unit, recipe);
        }
        for (int i = 0; i < conditions.length; i++) {
            var condition = conditions[i];
            var type = condition.getIssueType();
            long group = condition.isOr() ? 1 : 0;
            if (!checked[i]) {
                entries.add(DiagnosisEntry.of(IO.NONE, null, i, DiagnosisState.SKIPPED, group, 0, type).withLabel(condition.getTooltips()));
                continue;
            }
            boolean ok = passed[i];
            if (!ok && condition.isOr()) {
                for (int j = 0; j < conditions.length; j++) {
                    if (checked[j] && passed[j] && conditions[j].isOr() && conditions[j].getClass() == condition.getClass()) {
                        ok = true;
                        break;
                    }
                }
            }
            entries.add(DiagnosisEntry.of(IO.NONE, null, i, ok ? DiagnosisState.OK : DiagnosisState.BLOCKED, group, 0, type)
                    .withLabel(condition.getTooltips()).withDetail(condition.describeCurrent(holder, unit, recipe)));
        }
    }

    public static void diagnoseEnergy(IRecipeHandlerHolder holder, GTRecipeDefinition recipe, long eut, List<DiagnosisEntry> entries) {
        if (eut == 0) return;
        if (!(holder instanceof IElectricMachine electric)) {
            entries.add(DiagnosisEntry.of(eut > 0 ? IO.IN : IO.OUT, EURecipeInfo.INSTANCE, -1, DiagnosisState.MISSING_HANDLER, Math.abs(eut), 0, GTIssues.NO_ENERGY_HATCH));
            return;
        }
        diagnoseEnergy(holder, electric.getEnergyContainer(), electric.getRecipeTier(), recipe, eut, entries);
    }

    public static void diagnoseEnergy(IRecipeHandlerHolder holder, IEnergyContainer container, int recipeTier, GTRecipeDefinition recipe, long eut, List<DiagnosisEntry> entries) {
        long buffer = 0;
        boolean powerGate = false;
        if (holder instanceof IRecipeLogicMachine machine) {
            buffer = machine.getIssueEnergyBuffer();
            powerGate = machine.isPowerGated();
        }
        if (recipe.tier > 0) {
            diagnoseEnergy(container, eut, recipe.tier, recipeTier, true, powerGate, buffer, entries);
        } else {
            diagnoseEnergy(container, eut, GTUtil.getTierByVoltage(Math.abs(eut)), TIER_FROM_VOLTAGE, false, powerGate, buffer, entries);
        }
    }

    public static void diagnoseEnergy(IEnergyContainer container, long eut, int needTier, int haveTier, boolean tierGate, long buffer, List<DiagnosisEntry> entries) {
        diagnoseEnergy(container, eut, needTier, haveTier, tierGate, false, buffer, entries);
    }

    public static void diagnoseEnergy(IEnergyContainer container, long eut, int needTier, int haveTier, boolean tierGate, boolean powerGate, long buffer, List<DiagnosisEntry> entries) {
        if (eut == 0) return;
        long capacity = container.getEnergyCapacity();
        long stored = container.getEnergyStored();
        if (eut < 0) {
            long produce = -eut;
            long space = Math.max(0, capacity - stored);
            boolean ok = ConfigHolder.GENERATE_ENERGY_NO_MATCH || space >= produce;
            entries.add(DiagnosisEntry.of(IO.OUT, EURecipeInfo.INSTANCE, EU_OUTPUT, ok ? DiagnosisState.OK : DiagnosisState.BLOCKED, produce, space, GTIssues.ENERGY_FULL));
            return;
        }
        if (capacity <= 0) {
            entries.add(DiagnosisEntry.of(IO.IN, EURecipeInfo.INSTANCE, -1, DiagnosisState.MISSING_HANDLER, eut, 0, GTIssues.NO_ENERGY_HATCH));
            return;
        }
        long voltage = container.getInputVoltage();
        int have = haveTier == TIER_FROM_VOLTAGE ? (voltage > 0 ? GTUtil.getFloorTierByVoltage(voltage) : -1) : haveTier;
        var voltageState = have >= needTier ? DiagnosisState.OK : tierGate ? DiagnosisState.BLOCKED : DiagnosisState.LIMITED;
        entries.add(DiagnosisEntry.of(IO.IN, EURecipeInfo.INSTANCE, EU_VOLTAGE, voltageState, needTier, have, GTIssues.LOW_VOLTAGE));
        long power = saturatedMultiply(voltage, Math.max(1, container.getInputAmperage()));
        entries.add(DiagnosisEntry.of(IO.IN, EURecipeInfo.INSTANCE, EU_POWER, power >= eut ? DiagnosisState.OK : powerGate ? DiagnosisState.BLOCKED : DiagnosisState.LIMITED, eut, power, GTIssues.LOW_POWER));
        long available = saturatedAdd(stored, Math.max(0, buffer));
        entries.add(DiagnosisEntry.of(IO.IN, EURecipeInfo.INSTANCE, EU_BUFFER, available >= eut ? DiagnosisState.OK : DiagnosisState.INSUFFICIENT, eut, available, GTIssues.EU_SHORT));
    }

    public static void diagnoseComputation(IRecipeHandlerHolder holder, long cwut, List<DiagnosisEntry> entries) {
        if (cwut < 1) return;
        if (!(holder instanceof IComputationContainerMachine computation)) {
            entries.add(DiagnosisEntry.of(IO.IN, CWURecipeInfo.INSTANCE, -1, DiagnosisState.MISSING_HANDLER, cwut, 0, GTIssues.NO_CWU));
            return;
        }
        long available = computation.requestCWU(Long.MAX_VALUE, true);
        entries.add(DiagnosisEntry.of(IO.IN, CWURecipeInfo.INSTANCE, -1, available >= cwut ? DiagnosisState.OK : DiagnosisState.INSUFFICIENT, cwut, available, GTIssues.NO_CWU));
    }

    public static RecipeHandlerUnit diagnoseInputs(IRecipeHandlerHolder holder, List<Content<ItemIngredient>> items, List<Content<FluidIngredient>> fluids,
                                                   @Nullable RecipeHandlerUnit preferred, boolean forcePreferred, List<DiagnosisEntry> entries) {
        var units = holder.getInputUnits();
        var best = selectUnit(units, items, fluids, preferred, forcePreferred);
        int itemCount = items.size();
        int count = itemCount + fluids.size();
        if (count == 0) return best;
        boolean itemHandler = false, fluidHandler = false;
        for (var unit : units) {
            if (unit.itemHandlers.length > 0) itemHandler = true;
            if (unit.fluidHandlers.length > 0) fluidHandler = true;
        }
        var have = new long[count];
        if (itemCount > 0) allocateItems(Stock.items(best), items, have);
        if (count > itemCount) allocateFluids(Stock.fluids(best), fluids, have, itemCount);
        for (int i = 0; i < count; i++) {
            boolean item = i < itemCount;
            Content<?> content = item ? items.get(i) : fluids.get(i - itemCount);
            DiagnosisState state;
            IssueType type;
            if (!(item ? itemHandler : fluidHandler)) {
                state = DiagnosisState.MISSING_HANDLER;
                type = GTIssues.NO_INPUT_HATCH;
            } else {
                state = have[i] >= content.amount ? DiagnosisState.OK : DiagnosisState.INSUFFICIENT;
                type = GTIssues.INPUT_SHORT;
            }
            entries.add(DiagnosisEntry.of(IO.IN, item ? ItemRecipeInfo.INSTANCE : FluidRecipeInfo.INSTANCE, item ? i : i - itemCount, state, content.amount, have[i], type)
                    .withLabel(content.inner.getName()));
        }
        return best;
    }

    public static void confirmInputs(RecipeHandlerUnit unit, GTRecipe runtime, List<DiagnosisEntry> entries) {
        if (unit == RecipeHandlerUnit.NO_DATA) return;
        boolean pending = false;
        for (var entry : entries) {
            if (entry.state() == DiagnosisState.MISSING_HANDLER) return;
            if (entry.state() == DiagnosisState.INSUFFICIENT) pending = true;
        }
        if (!pending) return;
        if (!unit.handleRecipeItem(IO.IN, runtime, RecipeHelper.copyContents(runtime.itemInputs, 1), true)) return;
        if (!unit.handleRecipeFluid(IO.IN, runtime, RecipeHelper.copyContents(runtime.fluidInputs, 1), true)) return;
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.get(i);
            if (entry.state() != DiagnosisState.INSUFFICIENT) continue;
            entries.set(i, new DiagnosisEntry(entry.subject(), DiagnosisState.OK, entry.need(), -1, entry.type(), entry.label(), entry.detail()));
        }
    }

    public static void markNextRound(List<DiagnosisEntry> entries) {
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.get(i);
            if (entry.state() != DiagnosisState.INSUFFICIENT || entry.subject().io() != IO.IN) continue;
            entries.set(i, new DiagnosisEntry(entry.subject(), DiagnosisState.LIMITED, entry.need(), entry.have(), entry.type(), entry.label(), entry.detail()));
        }
    }

    public static RecipeHandlerUnit selectUnit(List<RecipeHandlerUnit> units, List<Content<ItemIngredient>> items, List<Content<FluidIngredient>> fluids,
                                               @Nullable RecipeHandlerUnit preferred, boolean forcePreferred) {
        if (units.isEmpty()) return RecipeHandlerUnit.NO_DATA;
        boolean hasPreferred = preferred != null && units.contains(preferred);
        if (hasPreferred && forcePreferred) return preferred;
        if (units.size() == 1) return units.getFirst();
        if (items.isEmpty() && fluids.isEmpty()) return hasPreferred ? preferred : units.getFirst();
        var scorer = new Scorer(items, fluids);
        var best = units.getFirst();
        int bestScore = -1;
        if (hasPreferred) {
            best = preferred;
            bestScore = scorer.score(preferred);
        }
        int limit = Math.min(units.size(), SCORE_LIMIT);
        for (int u = 0; u < limit; u++) {
            var unit = units.get(u);
            if (unit == preferred) continue;
            int score = scorer.score(unit);
            if (score > bestScore) {
                bestScore = score;
                best = unit;
            }
        }
        return best;
    }

    private static void allocateItems(Stock stock, List<Content<ItemIngredient>> items, long[] have) {
        for (int i = 0; i < items.size(); i++) {
            var content = items.get(i);
            boolean keep = content.chance == 0;
            for (int e = 0; e < stock.size; e++) {
                stock.matched[e] = stock.amounts[e] > 0 && (keep || !stock.catalyst[e]) && content.inner.test((ItemStack) stock.stacks[e]);
            }
            have[i] = stock.take(content.amount, keep);
        }
    }

    private static void allocateFluids(Stock stock, List<Content<FluidIngredient>> fluids, long[] have, int offset) {
        for (int i = 0; i < fluids.size(); i++) {
            var content = fluids.get(i);
            boolean keep = content.chance == 0;
            for (int e = 0; e < stock.size; e++) {
                stock.matched[e] = stock.amounts[e] > 0 && (keep || !stock.catalyst[e]) && content.inner.test((FluidStack) stock.stacks[e]);
            }
            have[offset + i] = stock.take(content.amount, keep);
        }
    }

    public static void diagnoseOutputs(IRecipeHandlerHolder holder, GTRecipe runtime, List<DiagnosisEntry> entries) {
        var items = runtime.itemOutputs;
        var fluids = runtime.fluidOutputs;
        int definedItems = runtime.definition.itemOutputs.size();
        int definedFluids = runtime.definition.fluidOutputs.size();
        if (items.isEmpty() && fluids.isEmpty() && definedItems == 0 && definedFluids == 0) return;
        var units = holder.getOutputUnits(runtime);
        boolean voidItems = holder instanceof IVoidable voidable && voidable.canVoidRecipeOutputs(ItemRecipeInfo.INSTANCE);
        boolean voidFluids = holder instanceof IVoidable voidable && voidable.canVoidRecipeOutputs(FluidRecipeInfo.INSTANCE);
        boolean itemHandler = false, fluidHandler = false;
        for (var unit : units) {
            if (unit.itemHandlers.length > 0 || unit.isInfiniteItemCapacity) itemHandler = true;
            if (unit.fluidHandlers.length > 0 || unit.isInfiniteFluidCapacity) fluidHandler = true;
        }
        List<Content<ItemIngredient>> itemCheck = voidItems ? Collections.emptyList() : RecipeHelper.copyContents(items, 1);
        List<Content<FluidIngredient>> fluidCheck = voidFluids ? Collections.emptyList() : RecipeHelper.copyContents(fluids, 1);
        boolean fitsAll = itemCheck.isEmpty() && fluidCheck.isEmpty();
        for (int u = 0; u < units.size() && !fitsAll; u++) {
            var unit = units.get(u);
            fitsAll = unit.handleRecipeItem(IO.OUT, runtime, itemCheck, true) && unit.handleRecipeFluid(IO.OUT, runtime, fluidCheck, true);
        }
        var itemStates = new DiagnosisState[items.size()];
        var fluidStates = new DiagnosisState[fluids.size()];
        boolean anyBlocked = false;
        for (int i = 0; i < itemStates.length; i++) {
            var state = outputState(fitsAll, voidItems, itemHandler, units, runtime, Collections.singletonList(items.get(i)), Collections.emptyList());
            if (state == DiagnosisState.BLOCKED || state == DiagnosisState.MISSING_HANDLER) anyBlocked = true;
            itemStates[i] = state;
        }
        for (int i = 0; i < fluidStates.length; i++) {
            var state = outputState(fitsAll, voidFluids, fluidHandler, units, runtime, Collections.emptyList(), Collections.singletonList(fluids.get(i)));
            if (state == DiagnosisState.BLOCKED || state == DiagnosisState.MISSING_HANDLER) anyBlocked = true;
            fluidStates[i] = state;
        }
        if (!fitsAll && !anyBlocked) {
            for (int i = 0; i < itemStates.length; i++) if (!voidItems && itemStates[i] == DiagnosisState.OK) itemStates[i] = DiagnosisState.BLOCKED;
            for (int i = 0; i < fluidStates.length; i++) if (!voidFluids && fluidStates[i] == DiagnosisState.OK) fluidStates[i] = DiagnosisState.BLOCKED;
        }
        for (int i = 0; i < itemStates.length; i++) {
            var content = items.get(i);
            entries.add(DiagnosisEntry.of(IO.OUT, ItemRecipeInfo.INSTANCE, i, itemStates[i], content.amount, -1, outputType(itemStates[i])).withLabel(content.inner.getName()));
        }
        for (int i = itemStates.length; i < definedItems; i++) {
            entries.add(DiagnosisEntry.of(IO.OUT, ItemRecipeInfo.INSTANCE, i, DiagnosisState.SKIPPED, 0, -1, null).withLabel(runtime.definition.itemOutputs.get(i).inner.getName()));
        }
        for (int i = 0; i < fluidStates.length; i++) {
            var content = fluids.get(i);
            entries.add(DiagnosisEntry.of(IO.OUT, FluidRecipeInfo.INSTANCE, i, fluidStates[i], content.amount, -1, outputType(fluidStates[i])).withLabel(content.inner.getName()));
        }
        for (int i = fluidStates.length; i < definedFluids; i++) {
            entries.add(DiagnosisEntry.of(IO.OUT, FluidRecipeInfo.INSTANCE, i, DiagnosisState.SKIPPED, 0, -1, null).withLabel(runtime.definition.fluidOutputs.get(i).inner.getName()));
        }
    }

    private static DiagnosisState outputState(boolean fitsAll, boolean voiding, boolean handler, List<RecipeHandlerUnit> units, GTRecipe runtime,
                                              List<Content<ItemIngredient>> items, List<Content<FluidIngredient>> fluids) {
        if (fitsAll && !voiding) return DiagnosisState.OK;
        boolean fits = handler && fits(units, runtime, items, fluids);
        if (voiding) return fits ? DiagnosisState.OK : DiagnosisState.VOIDED;
        if (!handler) return DiagnosisState.MISSING_HANDLER;
        return fits ? DiagnosisState.OK : DiagnosisState.BLOCKED;
    }

    private static IssueType outputType(DiagnosisState state) {
        return switch (state) {
            case VOIDED -> GTIssues.OUTPUT_VOIDED;
            case MISSING_HANDLER -> GTIssues.NO_OUTPUT_HATCH;
            default -> GTIssues.OUTPUT_FULL;
        };
    }

    private static boolean fits(List<RecipeHandlerUnit> units, GTRecipe runtime, List<Content<ItemIngredient>> items, List<Content<FluidIngredient>> fluids) {
        if (items.isEmpty() && fluids.isEmpty()) return true;
        for (var unit : units) {
            if (unit.handleRecipeItem(IO.OUT, runtime, RecipeHelper.copyContents(items, 1), true) &&
                    unit.handleRecipeFluid(IO.OUT, runtime, RecipeHelper.copyContents(fluids, 1), true)) {
                return true;
            }
        }
        return false;
    }

    public static long saturatedAdd(long a, long b) {
        long sum = a + b;
        return ((a ^ sum) & (b ^ sum)) < 0 ? Long.MAX_VALUE : sum;
    }

    public static long saturatedMultiply(long a, long b) {
        long high = Math.multiplyHigh(a, b);
        long low = a * b;
        return (high == 0 && low >= 0) || (high == -1 && low < 0) ? low : Long.MAX_VALUE;
    }

    private static final class Stock {

        private Object[] stacks = new Object[16];
        private long[] amounts = new long[16];
        private boolean[] catalyst = new boolean[16];
        private boolean[] matched = new boolean[16];
        private int size;
        private boolean current;

        private static Stock items(RecipeHandlerUnit unit) {
            var stock = new Stock();
            ObjLongConsumer<ItemStack> sink = stock::add;
            for (IRecipeHandler handler : unit.itemHandlers) {
                stock.current = handler.isNotConsumable();
                handler.fastForEachItems(sink);
            }
            return stock;
        }

        private static Stock fluids(RecipeHandlerUnit unit) {
            var stock = new Stock();
            ObjLongConsumer<FluidStack> sink = stock::add;
            for (IRecipeHandler handler : unit.fluidHandlers) {
                stock.current = handler.isNotConsumable();
                handler.fastForEachFluids(sink);
            }
            return stock;
        }

        private void add(Object stack, long amount) {
            if (amount <= 0) return;
            if (size == stacks.length) {
                int grown = size << 1;
                stacks = Arrays.copyOf(stacks, grown);
                amounts = Arrays.copyOf(amounts, grown);
                catalyst = Arrays.copyOf(catalyst, grown);
                matched = Arrays.copyOf(matched, grown);
            }
            stacks[size] = stack;
            amounts[size] = amount;
            catalyst[size] = current;
            size++;
        }

        private long take(long need, boolean keep) {
            long total = 0;
            for (int e = 0; e < size; e++) if (matched[e]) total = saturatedAdd(total, amounts[e]);
            long left = Math.min(need, total);
            if (keep) left = drain(left, true);
            drain(left, false);
            return total;
        }

        private long drain(long left, boolean fromCatalyst) {
            for (int e = 0; e < size && left > 0; e++) {
                if (!matched[e] || catalyst[e] != fromCatalyst) continue;
                long used = Math.min(left, amounts[e]);
                amounts[e] -= used;
                left -= used;
            }
            return left;
        }
    }

    private static final class Scorer {

        private final List<Content<ItemIngredient>> items;
        private final List<Content<FluidIngredient>> fluids;
        private final long[] sums;
        private final ObjLongConsumer<ItemStack> itemSink = this::acceptItem;
        private final ObjLongConsumer<FluidStack> fluidSink = this::acceptFluid;

        private Scorer(List<Content<ItemIngredient>> items, List<Content<FluidIngredient>> fluids) {
            this.items = items;
            this.fluids = fluids;
            this.sums = new long[items.size() + fluids.size()];
        }

        private int score(RecipeHandlerUnit unit) {
            Arrays.fill(sums, 0);
            if (!items.isEmpty()) unit.fastForEachItems(false, itemSink);
            if (!fluids.isEmpty()) unit.fastForEachFluids(false, fluidSink);
            int satisfied = 0, present = 0;
            int itemCount = items.size();
            for (int i = 0; i < sums.length; i++) {
                long need = i < itemCount ? items.get(i).amount : fluids.get(i - itemCount).amount;
                if (sums[i] > 0) present++;
                if (sums[i] >= need) satisfied++;
            }
            return satisfied * (sums.length + 1) + present;
        }

        private void acceptItem(ItemStack stack, long amount) {
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i).inner.test(stack)) {
                    sums[i] = saturatedAdd(sums[i], amount);
                    return;
                }
            }
        }

        private void acceptFluid(FluidStack stack, long amount) {
            int offset = items.size();
            for (int i = 0; i < fluids.size(); i++) {
                if (fluids.get(i).inner.test(stack)) {
                    sums[offset + i] = saturatedAdd(sums[offset + i], amount);
                    return;
                }
            }
        }
    }
}
