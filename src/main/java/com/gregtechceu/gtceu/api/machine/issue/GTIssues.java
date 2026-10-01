package com.gregtechceu.gtceu.api.machine.issue;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.common.recipe.condition.CleanroomCondition;
import com.gregtechceu.gtceu.common.recipe.condition.ResearchCondition;

import net.minecraft.network.chat.Component;

public final class GTIssues {

    public static final IssueType NO_RECIPE = IssueType.builder(GTCEu.id("no_recipe"))
            .category(IssueCategory.RECIPE).severity(IssueSeverity.INFO).stage(IssueStage.SEARCH)
            .keys("gtceu.issue.no_recipe", "gtceu.issue.no_recipe.desc").register();
    public static final IssueType NOT_APPLICABLE = IssueType.builder(GTCEu.id("not_applicable"))
            .category(IssueCategory.NOT_APPLICABLE).severity(IssueSeverity.INFO).stage(IssueStage.SEARCH)
            .keys("gtceu.issue.not_applicable", "gtceu.issue.not_applicable.desc").register();
    public static final IssueType NO_CAPABILITIES = IssueType.builder(GTCEu.id("no_capabilities"))
            .category(IssueCategory.PART).stage(IssueStage.SEARCH)
            .keys("gtceu.issue.no_capabilities", "gtceu.issue.no_capabilities.desc").register();
    public static final IssueType LOW_TIER = IssueType.builder(GTCEu.id("low_tier"))
            .category(IssueCategory.TIER).stage(IssueStage.TIER)
            .keys("gtceu.issue.low_tier", "gtceu.issue.low_tier.desc")
            .args(i -> new Object[] { IssueText.tierName(i.a()), IssueText.tierName(i.b()) }).register();
    public static final IssueType CONDITION = IssueType.builder(GTCEu.id("condition"))
            .category(IssueCategory.CONDITION).stage(IssueStage.CONDITION)
            .keys("gtceu.issue.condition", "gtceu.issue.condition.desc")
            .args(i -> new Object[] { IssueText.conditionText(i) }).register();
    public static final IssueType INSUFFICIENT_TEMPERATURE = IssueType.builder(GTCEu.id("insufficient_temperature"))
            .category(IssueCategory.CONDITION).stage(IssueStage.CONDITION)
            .keys("gtceu.issue.insufficient_temperature", "gtceu.issue.insufficient_temperature.desc")
            .args(i -> new Object[] { IssueText.number(i.a()), IssueText.number(i.b()) }).register();
    public static final IssueType MAINTENANCE = IssueType.builder(GTCEu.id("maintenance"))
            .category(IssueCategory.MACHINE).stage(IssueStage.MODIFIER)
            .keys("gtceu.issue.maintenance", "gtceu.issue.maintenance.desc").register();
    public static final IssueType MUFFLER_OBSTRUCTED = IssueType.builder(GTCEu.id("muffler_obstructed"))
            .category(IssueCategory.PART).stage(IssueStage.MODIFIER)
            .keys("gtceu.issue.muffler_obstructed", "gtceu.issue.muffler_obstructed.desc").register();
    public static final IssueType MUFFLER_INSUFFICIENT = IssueType.builder(GTCEu.id("muffler_insufficient"))
            .category(IssueCategory.PART).stage(IssueStage.MODIFIER)
            .keys("gtceu.issue.muffler_insufficient", "gtceu.issue.muffler_insufficient.desc").register();
    public static final IssueType ROTOR_OBSTRUCTED = IssueType.builder(GTCEu.id("rotor_obstructed"))
            .category(IssueCategory.PART).stage(IssueStage.MODIFIER)
            .keys("gtceu.issue.rotor_obstructed", "gtceu.issue.rotor_obstructed.desc").register();
    public static final IssueType ROTOR_MISSING = IssueType.builder(GTCEu.id("rotor_missing"))
            .category(IssueCategory.PART).stage(IssueStage.MODIFIER)
            .keys("gtceu.issue.rotor_missing", "gtceu.issue.rotor_missing.desc").register();
    public static final IssueType LOW_VOLTAGE = IssueType.builder(GTCEu.id("low_voltage"))
            .category(IssueCategory.ENERGY).stage(IssueStage.ENERGY)
            .keys("gtceu.issue.low_voltage", "gtceu.issue.low_voltage.desc")
            .args(i -> new Object[] { IssueText.tierName(i.a()), IssueText.tierName(i.b()) }).register();
    public static final IssueType LOW_POWER = IssueType.builder(GTCEu.id("low_power"))
            .category(IssueCategory.ENERGY).stage(IssueStage.ENERGY)
            .keys("gtceu.issue.low_power", "gtceu.issue.low_power.desc")
            .args(i -> new Object[] { IssueText.number(i.a()), IssueText.number(i.b()) }).register();
    public static final IssueType EU_SHORT = IssueType.builder(GTCEu.id("eu_short"))
            .category(IssueCategory.ENERGY).stage(IssueStage.ENERGY)
            .keys("gtceu.issue.eu_short", "gtceu.issue.eu_short.desc")
            .args(i -> new Object[] { IssueText.number(i.a()) }).register();
    public static final IssueType ENERGY_FULL = IssueType.builder(GTCEu.id("energy_full"))
            .category(IssueCategory.OUTPUT).stage(IssueStage.ENERGY)
            .keys("gtceu.issue.energy_full", "gtceu.issue.energy_full.desc")
            .args(i -> new Object[] { IssueText.number(i.a()) }).register();
    public static final IssueType NO_CWU = IssueType.builder(GTCEu.id("no_cwu"))
            .category(IssueCategory.COMPUTATION).stage(IssueStage.ENERGY)
            .keys("gtceu.issue.no_cwu", "gtceu.issue.no_cwu.desc")
            .args(i -> new Object[] { IssueText.number(i.a()), IssueText.number(i.b()) }).register();
    public static final IssueType INPUT_SHORT = IssueType.builder(GTCEu.id("input_short"))
            .category(IssueCategory.INPUT).stage(IssueStage.INPUT)
            .keys("gtceu.issue.input_short", "gtceu.issue.input_short.desc")
            .args(i -> new Object[] { inputName(i), inputAmount(i) }).register();
    public static final IssueType INSUFFICIENT_FUEL = IssueType.builder(GTCEu.id("insufficient_fuel"))
            .category(IssueCategory.INPUT).stage(IssueStage.INPUT)
            .keys("gtceu.issue.insufficient_fuel", "gtceu.issue.insufficient_fuel.desc").register();
    public static final IssueType ORDERED_INPUT = IssueType.builder(GTCEu.id("ordered_input"))
            .category(IssueCategory.INPUT).stage(IssueStage.INPUT)
            .keys("gtceu.issue.ordered_input", "gtceu.issue.ordered_input.desc")
            .args(i -> new Object[] { IssueText.capabilityName(i) }).register();
    public static final IssueType OUTPUT_FULL = IssueType.builder(GTCEu.id("output_full"))
            .category(IssueCategory.OUTPUT).stage(IssueStage.OUTPUT)
            .keys("gtceu.issue.output_full", "gtceu.issue.output_full.desc")
            .args(i -> new Object[] { IssueText.capabilityName(i) }).register();
    public static final IssueType OUTPUT_VOIDED = IssueType.builder(GTCEu.id("output_voided"))
            .category(IssueCategory.OUTPUT).severity(IssueSeverity.WARNING).stage(IssueStage.OUTPUT)
            .keys("gtceu.issue.output_voided", "gtceu.issue.output_voided.desc")
            .args(i -> new Object[] { IssueText.capabilityName(i) }).register();
    public static final IssueType NO_INPUT_HATCH = IssueType.builder(GTCEu.id("no_input_hatch"))
            .category(IssueCategory.PART).stage(IssueStage.INPUT)
            .keys("gtceu.issue.no_input_hatch", "gtceu.issue.no_input_hatch.desc")
            .args(i -> new Object[] { IssueText.capabilityName(i) }).register();
    public static final IssueType NO_OUTPUT_HATCH = IssueType.builder(GTCEu.id("no_output_hatch"))
            .category(IssueCategory.PART).stage(IssueStage.OUTPUT)
            .keys("gtceu.issue.no_output_hatch", "gtceu.issue.no_output_hatch.desc")
            .args(i -> new Object[] { IssueText.capabilityName(i) }).register();
    public static final IssueType NO_ENERGY_HATCH = IssueType.builder(GTCEu.id("no_energy_hatch"))
            .category(IssueCategory.PART).stage(IssueStage.ENERGY)
            .keys("gtceu.issue.no_energy_hatch", "gtceu.issue.no_energy_hatch.desc").register();
    public static final IssueType OUTPUT_LOST = IssueType.builder(GTCEu.id("output_lost"))
            .category(IssueCategory.OUTPUT).severity(IssueSeverity.WARNING).stage(IssueStage.OUTPUT)
            .keys("gtceu.issue.output_lost", "gtceu.issue.output_lost.desc").register();
    public static final IssueType EXTENSION_UNMET = IssueType.builder(GTCEu.id("extension_unmet"))
            .category(IssueCategory.MACHINE).stage(IssueStage.INPUT)
            .keys("gtceu.issue.extension_unmet", "gtceu.issue.extension_unmet.desc").register();
    public static final IssueType START_ENERGY_SHORT = IssueType.builder(GTCEu.id("start_energy_short"))
            .category(IssueCategory.ENERGY).stage(IssueStage.MODIFIER)
            .keys("gtceu.issue.start_energy_short", "gtceu.issue.start_energy_short.desc")
            .args(i -> new Object[] { IssueText.number(i.a()), IssueText.number(i.b()) }).register();
    public static final IssueType START_ENERGY_CAPACITY = IssueType.builder(GTCEu.id("start_energy_capacity"))
            .category(IssueCategory.ENERGY).stage(IssueStage.MODIFIER)
            .keys("gtceu.issue.start_energy_capacity", "gtceu.issue.start_energy_capacity.desc")
            .args(i -> new Object[] { IssueText.number(i.a()), IssueText.number(i.b()) }).register();
    public static final IssueType OUTPUT_POWER_LOW = IssueType.builder(GTCEu.id("output_power_low"))
            .category(IssueCategory.ENERGY).stage(IssueStage.MODIFIER)
            .keys("gtceu.issue.output_power_low", "gtceu.issue.output_power_low.desc")
            .args(i -> new Object[] { IssueText.number(i.a()), IssueText.number(i.b()) }).register();
    public static final IssueType INTAKE_OBSTRUCTED = IssueType.builder(GTCEu.id("intake_obstructed"))
            .category(IssueCategory.PART).stage(IssueStage.MODIFIER)
            .keys("gtceu.issue.intake_obstructed", "gtceu.issue.intake_obstructed.desc").register();
    public static final IssueType NO_LUBRICANT = IssueType.builder(GTCEu.id("no_lubricant"))
            .category(IssueCategory.INPUT).stage(IssueStage.INPUT)
            .keys("gtceu.issue.no_lubricant", "gtceu.issue.no_lubricant.desc").register();
    public static final IssueType VENT_BLOCKED = IssueType.builder(GTCEu.id("vent_blocked"))
            .category(IssueCategory.ENVIRONMENT).stage(IssueStage.CONDITION)
            .keys("gtceu.issue.vent_blocked", "gtceu.issue.vent_blocked.desc").register();
    public static final IssueType NO_SUNLIGHT = IssueType.builder(GTCEu.id("no_sunlight"))
            .category(IssueCategory.ENVIRONMENT).severity(IssueSeverity.INFO).stage(IssueStage.CONDITION)
            .keys("gtceu.issue.no_sunlight", "gtceu.issue.no_sunlight.desc").register();
    public static final IssueType STEAM_SHORT = IssueType.builder(GTCEu.id("steam_short"))
            .category(IssueCategory.ENERGY).stage(IssueStage.ENERGY)
            .keys("gtceu.issue.steam_short", "gtceu.issue.steam_short.desc").register();
    public static final IssueType MINER_DONE = IssueType.builder(GTCEu.id("miner_done"))
            .category(IssueCategory.MACHINE).severity(IssueSeverity.INFO).stage(IssueStage.MACHINE)
            .keys("gtceu.issue.miner_done", "gtceu.issue.miner_done.desc").register();
    public static final IssueType NO_VEIN = IssueType.builder(GTCEu.id("no_vein"))
            .category(IssueCategory.ENVIRONMENT).stage(IssueStage.CONDITION)
            .keys("gtceu.issue.no_vein", "gtceu.issue.no_vein.desc").register();
    public static final IssueType CLEANROOM = IssueType.builder(GTCEu.id("cleanroom"))
            .category(IssueCategory.CONDITION).stage(IssueStage.CONDITION)
            .keys("gtceu.issue.cleanroom", "gtceu.issue.cleanroom.desc")
            .args(i -> new Object[] { CleanroomCondition.reasonText(i) }).register();
    public static final IssueType RESEARCH = IssueType.builder(GTCEu.id("research"))
            .category(IssueCategory.CONDITION).stage(IssueStage.CONDITION)
            .keys("gtceu.issue.research", "gtceu.issue.research.desc")
            .args(i -> new Object[] { ResearchCondition.reasonText(i.b()) }).register();
    public static final IssueType DIMENSION = IssueType.builder(GTCEu.id("dimension"))
            .category(IssueCategory.CONDITION).stage(IssueStage.CONDITION)
            .keys("gtceu.issue.dimension", "gtceu.issue.dimension.desc")
            .args(i -> new Object[] { IssueText.conditionText(i) }).register();
    public static final IssueType UNFORMED = IssueType.builder(GTCEu.id("unformed"))
            .category(IssueCategory.STRUCTURE).stage(IssueStage.MACHINE)
            .keys("gtceu.issue.unformed", "gtceu.issue.unformed.desc").register();
    public static final IssueType PAUSED = IssueType.builder(GTCEu.id("paused"))
            .category(IssueCategory.CONTROL).severity(IssueSeverity.INFO).stage(IssueStage.MACHINE)
            .keys("gtceu.issue.paused", "gtceu.issue.paused.desc").register();
    public static final IssueType DISABLED = IssueType.builder(GTCEu.id("disabled"))
            .category(IssueCategory.CONTROL).severity(IssueSeverity.WARNING).stage(IssueStage.MACHINE)
            .keys("gtceu.issue.disabled", "gtceu.issue.disabled.desc").register();
    public static final IssueType CUSTOM = IssueType.builder(GTCEu.id("custom"))
            .category(IssueCategory.MACHINE).stage(IssueStage.SEARCH)
            .keys("gtceu.issue.custom", "gtceu.issue.custom.desc").register();

    private GTIssues() {}

    public static void init() {}

    private static Component inputName(MachineIssue issue) {
        var content = IssueText.content(issue.recipe(), issue.subject());
        if (content != null) return content.inner.getName();
        return issue.subject().capability() == null ? Component.translatable("gtceu.issue.subject.inputs") : IssueText.capabilityName(issue);
    }

    private static Component inputAmount(MachineIssue issue) {
        if (issue.a() < 0) return Component.empty();
        return Component.translatable("gtceu.issue.input_short.amount", IssueText.number(issue.a()), IssueText.number(issue.b()));
    }
}
