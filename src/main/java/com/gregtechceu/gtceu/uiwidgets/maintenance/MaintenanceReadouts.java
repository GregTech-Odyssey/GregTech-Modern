package com.gregtechceu.gtceu.uiwidgets.maintenance;

import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMaintenanceMachine;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.uipro.Level;
import com.gregtechceu.gtceu.uipro.elements.ProgressBar;
import com.gregtechceu.gtceu.uipro.elements.StatusLine;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;
import com.gregtechceu.gtceu.uiwidgets.display.MachineScreen;
import com.gregtechceu.gtceu.uiwidgets.multiblock.MultiblockPage;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.math.BigDecimal;
import java.util.function.DoubleSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * 维护仓显示屏读数：维护状态、待修问题、维护损耗进度、处理耗时与维护损耗速度。
 * GTM 维护仓与 GTOCore 各类自动维护仓共用。
 */
public final class MaintenanceReadouts {

    public static final String STATE = "gtceu.maintenance.state";
    public static final String PROBLEMS = "gtceu.maintenance.problems";
    public static final String WEAR = "gtceu.maintenance.wear";
    public static final String DURATION = "gtceu.maintenance.duration";
    public static final String WEAR_RATE = "gtceu.maintenance.wear_rate";
    public static final String REPAIR = "gtceu.maintenance.repair";
    public static final String REPAIR_TOOLTIP = "gtceu.maintenance.repair.tooltip";
    public static final String NO_PROBLEMS = "gtceu.maintenance.no_problems";
    public static final String DURATION_TOOLTIP = "gtceu.maintenance.duration.tooltip";

    private static final int STATE_OK = 0;
    private static final int STATE_BROKEN = 1;
    private static final int STATE_AUTO = 2;
    private static final int STATE_DISABLED = 3;
    private static final Component[] STATE_TEXT = {
            Component.translatable("gtceu.maintenance.state.ok"),
            Component.translatable("gtceu.maintenance.state.broken"),
            Component.translatable("gtceu.maintenance.state.auto"),
            Component.translatable("gtceu.maintenance.state.disabled") };
    private static final Level[] STATE_LEVEL = { Level.GOOD, Level.ERROR, Level.GOOD, Level.NORMAL };
    private static final Component[] TOOL_NAMES = {
            Component.translatable("gtceu.maintenance.tool.wrench"),
            Component.translatable("gtceu.maintenance.tool.screwdriver"),
            Component.translatable("gtceu.maintenance.tool.soft_mallet"),
            Component.translatable("gtceu.maintenance.tool.hard_hammer"),
            Component.translatable("gtceu.maintenance.tool.wire_cutter"),
            Component.translatable("gtceu.maintenance.tool.crowbar") };
    private static final Component TOOL_SEPARATOR = Component.translatable("gtceu.maintenance.tool_separator");
    private static final String WEAR_DETAIL = "gtceu.maintenance.wear.detail";
    private static final String TIMES = "gtceu.maintenance.times";
    private static final String PROBLEM_COUNT = "gtceu.maintenance.problems.count";
    private static final String TOOLS_DETAIL = "gtceu.maintenance.tools";
    private static final Component NONE = Component.translatable("gtceu.maintenance.problems.none");

    private MaintenanceReadouts() {}

    public static StatusLine addState(MachineScreen screen, IMaintenanceMachine machine) {
        return screen.addLine(STATE, MultiblockPage.cached(() -> state(machine), code -> STATE_TEXT[(int) code]))
                .bindLevel(() -> STATE_LEVEL[state(machine)]);
    }

    public static StatusLine addProblems(MachineScreen screen, IMaintenanceMachine machine) {
        LongSupplier problems = () -> machine.hasMaintenanceProblems() ? machine.getMaintenanceProblems() & IMaintenanceMachine.NO_PROBLEMS : IMaintenanceMachine.NO_PROBLEMS;
        return screen.addLine(PROBLEMS, MultiblockPage.cached(problems, MaintenanceReadouts::countText))
                .bindDetail(MultiblockPage.cached(problems, MaintenanceReadouts::toolsText));
    }

    public static ProgressBar addWear(MachineScreen screen, IMaintenanceMachine machine) {
        long onset = Math.round(machine.getFailureOnset() * 100);
        Component detail = Component.translatable(WEAR_DETAIL, onset + "%");
        return screen.addBar(WEAR, UITheme::barProgress, new WearProgress(machine)).percent().bindDetail(() -> detail);
    }

    public static StatusLine addDuration(MachineScreen screen, DoubleSupplier duration) {
        return screen.addLine(DURATION, timesText(duration));
    }

    public static StatusLine addWearRate(MachineScreen screen, DoubleSupplier rate) {
        return screen.addLine(WEAR_RATE, timesText(rate)).bindLevel(() -> {
            double value = rate.getAsDouble();
            return value > 1 ? Level.WARNING : value < 1 ? Level.GOOD : Level.NORMAL;
        });
    }

    private static int state(IMaintenanceMachine machine) {
        if (!ConfigHolder.INSTANCE.machines.enableMaintenance) return STATE_DISABLED;
        if (machine.isFullAuto()) return STATE_AUTO;
        return machine.hasMaintenanceProblems() ? STATE_BROKEN : STATE_OK;
    }

    private static Component countText(long problems) {
        int count = 6 - Long.bitCount(problems & IMaintenanceMachine.NO_PROBLEMS);
        return count == 0 ? NONE : Component.translatable(PROBLEM_COUNT, count);
    }

    private static Component toolsText(long problems) {
        MutableComponent text = null;
        for (int i = 0; i < TOOL_NAMES.length; i++) {
            if (((problems >> i) & 1) != 0) continue;
            if (text == null) text = Component.empty();
            else text.append(TOOL_SEPARATOR);
            text.append(TOOL_NAMES[i]);
        }
        return text == null ? Component.empty() : Component.translatable(TOOLS_DETAIL, text);
    }

    private static Supplier<Component> timesText(DoubleSupplier value) {
        return MultiblockPage.cached(() -> Math.round(value.getAsDouble() * 100),
                hundredths -> Component.translatable(TIMES, BigDecimal.valueOf(hundredths, 2).toPlainString()));
    }

    private static final class WearProgress implements Supplier<ProgressBar.Progress> {

        private final IMaintenanceMachine machine;
        private long shown = -1;
        private ProgressBar.Progress progress = ProgressBar.Progress.EMPTY;

        private WearProgress(IMaintenanceMachine machine) {
            this.machine = machine;
        }

        @Override
        public ProgressBar.Progress get() {
            long active = ConfigHolder.INSTANCE.machines.enableMaintenance ? Math.max(0, machine.getTimeActive()) : 0;
            if (active != shown) {
                shown = active;
                progress = new ProgressBar.Progress(Math.min(active, IMaintenanceMachine.MINIMUM_MAINTENANCE_TIME),
                        IMaintenanceMachine.MINIMUM_MAINTENANCE_TIME, 0);
            }
            return progress;
        }
    }
}
