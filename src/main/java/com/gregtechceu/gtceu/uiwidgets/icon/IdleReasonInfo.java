package com.gregtechceu.gtceu.uiwidgets.icon;

import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.handler.ActionResult;
import com.gregtechceu.gtceu.uipro.Level;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 机器停机原因登记表：按原因的翻译键登记说明键与严重度，两端都可读取；图标另见 {@link IdleReasonIcons}（仅客户端）。
 * 同时负责标题栏提示、多方块显示屏与 Jade 共用的"标题 + 说明"排版。
 */
public final class IdleReasonInfo {

    public static final Component NONE = Component.empty();

    private static final Map<String, Entry> ENTRIES = new ConcurrentHashMap<>();

    static {
        register("gtceu.recipe_logic.no_recipe_found", "gtceu.recipe_logic.no_recipe_found.desc", Severity.INFO);
        register("gtceu.recipe_logic.no_capabilities", "gtceu.recipe_logic.no_capabilities.desc", Severity.BLOCKING);
        register("gtceu.recipe_logic.insufficient_tier", "gtceu.recipe_logic.insufficient_tier.desc", Severity.BLOCKING);
        register("gtceu.recipe_logic.condition_fails", "gtceu.recipe_logic.condition_fails.desc", Severity.BLOCKING);
        register("gtceu.recipe_logic.insufficient_in", "gtceu.recipe_logic.insufficient_in.desc", Severity.BLOCKING);
        register("gtceu.recipe_logic.amount_not_enough", "gtceu.recipe_logic.amount_not_enough.desc", Severity.BLOCKING);
        register("gtceu.recipe_logic.unable_handle", "gtceu.recipe_logic.unable_handle.desc", Severity.BLOCKING);
        register("gtceu.recipe_logic.ordered_item", "gtceu.recipe_logic.ordered_item.desc", Severity.BLOCKING);
        register("gtceu.recipe_logic.ordered_fluid", "gtceu.recipe_logic.ordered_fluid.desc", Severity.BLOCKING);
        register("gtceu.recipe_logic.insufficient_out", "gtceu.recipe_logic.insufficient_out.desc", Severity.BLOCKING);
        register("gtceu.recipe_logic.insufficient_fuel", "gtceu.recipe_logic.insufficient_fuel.desc", Severity.BLOCKING);
        register("gtceu.top.maintenance_broken", "gtceu.issue.maintenance.desc", Severity.BLOCKING);
        register("gtceu.multiblock.universal.muffler_obstructed", "gtceu.issue.muffler_obstructed.desc", Severity.BLOCKING);
        register("gtceu.multiblock.universal.muffler_insufficient", "gtceu.issue.muffler_insufficient.desc", Severity.BLOCKING);
        register("gtceu.multiblock.universal.rotor_obstructed", "gtceu.issue.rotor_obstructed.desc", Severity.BLOCKING);
        register("gtceu.multiblock.computation.not_enough_computation", "gtceu.multiblock.computation.not_enough_computation.desc", Severity.BLOCKING);
        register("behavior.prospector.not_enough_energy", "gtceu.issue.no_energy.desc", Severity.BLOCKING);
        register("gtceu.issue.eu_short", "gtceu.issue.eu_short.desc", true, Severity.BLOCKING);
        register("gtceu.issue.rotor_missing", "gtceu.issue.rotor_missing.desc", Severity.BLOCKING);
        register("gtceu.issue.output_power_low", "gtceu.issue.output_power_low.desc", true, Severity.BLOCKING);
        register("gtceu.issue.start_energy_short", "gtceu.issue.start_energy_short.desc", true, Severity.BLOCKING);
        register("gtceu.issue.start_energy_capacity", "gtceu.issue.start_energy_capacity.desc", true, Severity.BLOCKING);
        register("gtceu.issue.intake_obstructed", "gtceu.issue.intake_obstructed.desc", Severity.BLOCKING);
        register("gtceu.issue.no_lubricant", "gtceu.issue.no_lubricant.desc", Severity.BLOCKING);
        register("gtceu.issue.vent_blocked", "gtceu.issue.vent_blocked.desc", Severity.BLOCKING);
        register("gtceu.issue.steam_short", "gtceu.issue.steam_short.desc", Severity.BLOCKING);
        register("gtceu.issue.no_sunlight", "gtceu.issue.no_sunlight.desc", Severity.INFO);
        register("gtceu.issue.not_applicable", "gtceu.issue.not_applicable.desc", Severity.INFO);
        register("gtceu.issue.no_energy_hatch", "gtceu.issue.no_energy_hatch.desc", Severity.BLOCKING);
        register("gtceu.issue.miner_done", "gtceu.issue.miner_done.desc", Severity.INFO);
        register("gtceu.issue.low_power", "gtceu.issue.low_power.desc", true, Severity.BLOCKING);
    }

    private IdleReasonInfo() {}

    public static Entry register(String key, @Nullable String descKey, Severity severity) {
        return register(key, descKey, false, severity);
    }

    public static Entry register(String key, @Nullable String descKey, boolean descArgs, Severity severity) {
        var entry = new Entry(key, descKey, descArgs, severity);
        ENTRIES.put(key, entry);
        return entry;
    }

    @Nullable
    public static Entry get(@Nullable String key) {
        return key == null ? null : ENTRIES.get(key);
    }

    @Nullable
    public static String keyOf(@Nullable Component reason) {
        return reason != null && reason.getContents() instanceof TranslatableContents contents ? contents.getKey() : null;
    }

    @Nullable
    public static Entry lookup(@Nullable Component reason) {
        return get(keyOf(reason));
    }

    public static boolean isNone(@Nullable Component reason) {
        return reason == null || reason == NONE || (reason.getContents() == ComponentContents.EMPTY && reason.getSiblings().isEmpty());
    }

    @Nullable
    public static Component reasonOf(RecipeLogic logic) {
        int status = logic.getStatus();
        if (status == RecipeLogic.WORKING || status == RecipeLogic.SUSPEND || !logic.showFancyTooltip() || !available(logic)) return null;
        return logic.getIdleReason();
    }

    public static boolean available(RecipeLogic logic) {
        var machine = logic.machine;
        if (machine instanceof IMultiController controller && !controller.isFormed()) return false;
        return machine.isRecipeLogicAvailable();
    }

    public static boolean isEnergyShort(@Nullable Component reason) {
        return "gtceu.recipe_logic.insufficient_in".equals(keyOf(reason)) && mentionsEnergy(reason);
    }

    private static boolean mentionsEnergy(Component component) {
        for (var sibling : component.getSiblings()) {
            if (sibling.getContents() instanceof TranslatableContents contents && "recipe.capability.eu.name".equals(contents.getKey())) return true;
            if (mentionsEnergy(sibling)) return true;
        }
        return false;
    }

    public static boolean visible(int status) {
        return status != RecipeLogic.WORKING;
    }

    public static MutableComponent title(Component reason) {
        return title(reason, lookup(reason));
    }

    public static MutableComponent title(Component reason, @Nullable Entry entry) {
        var title = Component.empty().append(reason);
        return entry == null ? title : title.withStyle(entry.severity.color());
    }

    @Nullable
    public static Component description(Component reason) {
        return description(reason, lookup(reason));
    }

    @Nullable
    public static Component description(Component reason, @Nullable Entry entry) {
        if (entry == null || entry.descKey == null) return null;
        if (isEnergyShort(reason)) return Component.translatable("gtceu.issue.no_energy.desc").withStyle(ChatFormatting.GRAY);
        var descKey = descKey(reason, entry);
        if (descKey == null) return null;
        return Component.translatable(descKey, args(reason)).withStyle(ChatFormatting.GRAY);
    }

    @Nullable
    public static String descKey(Component reason, @Nullable Entry entry) {
        if (entry == null || entry.descKey == null) return null;
        if (entry.descArgs && args(reason).length == 0) return null;
        return entry.descKey;
    }

    private static Object[] args(Component reason) {
        return reason.getContents() instanceof TranslatableContents contents ? contents.getArgs() : TranslatableContents.NO_ARGS;
    }

    public static boolean showsDescription(Component reason) {
        return showsDescription(reason, lookup(reason));
    }

    public static boolean showsDescription(Component reason, @Nullable Entry entry) {
        if (entry == null) return false;
        return entry.severity == Severity.BLOCKING || (reason.getContents() instanceof TranslatableContents contents && contents.getArgs().length > 0);
    }

    public static Component headline(int status, @Nullable Component reason) {
        return headline(status, reason, lookup(reason));
    }

    public static Component headline(int status, @Nullable Component reason, @Nullable Entry entry) {
        if (status == RecipeLogic.SUSPEND) return Component.translatable("gtceu.issue.paused").withStyle(Severity.INFO.color());
        if (reason == null || isNone(reason)) {
            if (status == RecipeLogic.WAITING) return Component.translatable("gtceu.issue.ui.waiting.title").withStyle(ChatFormatting.GOLD);
            reason = ActionResult.FAIL_NO_RECIPE_FOUND.reason();
            entry = lookup(reason);
        }
        if (status == RecipeLogic.WAITING) return Component.translatable("gtceu.issue.ui.waiting", title(reason, entry)).withStyle(ChatFormatting.GOLD);
        return title(reason, entry);
    }

    @Nullable
    public static Component detail(int status, @Nullable Component reason) {
        return detail(status, reason, lookup(reason));
    }

    @Nullable
    public static Component detail(int status, @Nullable Component reason, @Nullable Entry entry) {
        if (status == RecipeLogic.SUSPEND) return Component.translatable("gtceu.issue.paused.desc").withStyle(ChatFormatting.GRAY);
        if (reason == null || isNone(reason)) {
            if (status == RecipeLogic.WAITING) return Component.translatable("gtceu.issue.ui.waiting.desc").withStyle(ChatFormatting.GRAY);
            reason = ActionResult.FAIL_NO_RECIPE_FOUND.reason();
            entry = lookup(reason);
        }
        return description(reason, entry);
    }

    public static boolean showsDetail(int status, @Nullable Component reason) {
        return showsDetail(status, reason, lookup(reason));
    }

    public static boolean showsDetail(int status, @Nullable Component reason, @Nullable Entry entry) {
        if (status == RecipeLogic.SUSPEND) return false;
        if (reason == null || isNone(reason)) return status == RecipeLogic.WAITING;
        return showsDescription(reason, entry);
    }

    public static List<Component> tooltip(int status, @Nullable Component reason) {
        if (!visible(status)) return Collections.emptyList();
        var entry = lookup(reason);
        var headline = headline(status, reason, entry);
        var detail = detail(status, reason, entry);
        if (detail == null) return Collections.singletonList(headline);
        var lines = new ArrayList<Component>(2);
        lines.add(headline);
        lines.add(detail);
        return lines;
    }

    public static Level level(int status, @Nullable Component reason) {
        if (status == RecipeLogic.WORKING || status == RecipeLogic.SUSPEND) return Level.NORMAL;
        if (reason == null || isNone(reason)) return status == RecipeLogic.WAITING ? Level.WARNING : Level.NORMAL;
        var entry = lookup(reason);
        if (entry != null) return entry.severity.level();
        return status == RecipeLogic.WAITING ? Level.WARNING : Level.NORMAL;
    }

    public enum Severity {

        INFO,
        WARNING,
        BLOCKING;

        public ChatFormatting color() {
            return switch (this) {
                case INFO -> ChatFormatting.WHITE;
                case WARNING -> ChatFormatting.GOLD;
                case BLOCKING -> ChatFormatting.RED;
            };
        }

        public Level level() {
            return switch (this) {
                case INFO -> Level.NORMAL;
                case WARNING -> Level.WARNING;
                case BLOCKING -> Level.ERROR;
            };
        }
    }

    public record Entry(String key, @Nullable String descKey, boolean descArgs, Severity severity) {}
}
