package com.gregtechceu.gtceu.uiwidgets.multiblock;

import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.uipro.LayoutStyle;
import com.gregtechceu.gtceu.uipro.Level;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.elements.ProgressBar;
import com.gregtechceu.gtceu.uipro.elements.RichText;
import com.gregtechceu.gtceu.uipro.elements.ScrollerView;
import com.gregtechceu.gtceu.uipro.elements.StatusLine;
import com.gregtechceu.gtceu.uipro.elements.StatusPanel;
import com.gregtechceu.gtceu.uipro.styletemplate.MachineEra;
import com.gregtechceu.gtceu.uipro.styletemplate.ScreenSprite;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;
import com.gregtechceu.gtceu.uiwidgets.icon.IdleReasonInfo;
import com.gregtechceu.gtceu.uiwidgets.recipe.RecipeIOList;
import com.gregtechceu.gtceu.utils.FormattingUtil;

import com.lowdragmc.lowdraglib.gui.util.ClickData;
import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.ChatFormatting;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.LongFunction;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * 标准多方块主页：一块机器显示屏（状态、进度、机器自己的读数、当前配方清单，底图按机器年代区分）+ 屏下的操作区块，按屏幕高度滚动。
 * 完整的逐行文字留在"详细信息"页签。
 */
public final class MultiblockPage {

    public static final String STATE = "gtceu.gui.multiblock.state";
    public static final String STATE_UNFORMED = "gtceu.gui.multiblock.state.unformed";
    public static final String STATE_PAUSED = "gtceu.gui.multiblock.state.paused";
    public static final String STATE_RUNNING = "gtceu.gui.multiblock.state.running";
    public static final String STATE_WAITING = "gtceu.gui.multiblock.state.waiting";
    public static final String STATE_IDLE = "gtceu.gui.multiblock.state.idle";
    public static final String RECIPE_TYPE = "gtceu.gui.multiblock.recipe_type";
    public static final String PROGRESS = "gtceu.gui.multiblock.progress";
    public static final String PARALLEL = "gtceu.gui.multiblock.parallel";
    public static final String ON = "gtceu.gui.multiblock.on";
    public static final String OFF = "gtceu.gui.multiblock.off";
    public static final Component NO_VALUE = Component.literal("-");
    private static final ThreadLocal<Boolean> COLLECTING_SCREEN_TEXT = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static final int SCREEN_PADDING = 9;

    private static final Component UNFORMED = Component.translatable(STATE_UNFORMED);
    private static final Component PAUSED = Component.translatable(STATE_PAUSED);
    private static final Component RUNNING = Component.translatable(STATE_RUNNING);
    private static final Component IDLE = Component.translatable(STATE_IDLE);
    private static final Component NONE = Component.empty();
    private static final Component ON_TEXT = Component.translatable(ON);
    private static final Component OFF_TEXT = Component.translatable(OFF);

    private final WorkableMultiblockMachine machine;
    private final RecipeIOList recipeList;
    private final StatusPanel screen = new StatusPanel(LayoutStyle.AUTO);
    private final List<Widget> sections = new ArrayList<>();
    @Nullable
    private ControlPanel controls;
    @Nullable
    private BooleanSupplier alert;
    private Component alertText = NONE;
    @Nullable
    private ReasonView reasonView;

    private MultiblockPage(WorkableMultiblockMachine machine) {
        this.machine = machine;
        this.recipeList = RecipeIOList.of(machine.getRecipeLogic());
        screen.setBackground(MachineEra.CLASSIC.getScreen());
        screen.layout(l -> l.paddingAll(SCREEN_PADDING).gapAll(2));
        addStateLine();
        addProgressBar();
        if (machine.getAvailableRecipeTypes().length > 1) addLine(RECIPE_TYPE, recipeTypeText(machine, null));
    }

    public static MultiblockPage of(WorkableMultiblockMachine machine) {
        return new MultiblockPage(machine);
    }

    public RecipeIOList getRecipeList() {
        return recipeList;
    }

    public MultiblockPage setScreen(ScreenSprite sprite) {
        screen.setBackground(sprite);
        return this;
    }

    public StatusLine addLine(String labelKey, Supplier<Component> value) {
        return screen.addLine(StatusLine.of(LayoutStyle.AUTO, labelKey, value).screenStyle());
    }

    public StatusLine addReading(String templateKey, Supplier<Component> value) {
        String label = machine.isRemote() ? clientReadingLabel(templateKey) : null;
        if (!machine.isRemote() || label != null) {
            return screen.addLine(StatusLine.of(LayoutStyle.AUTO, label == null ? NONE : Component.literal(label), value).screenStyle());
        }
        return screen.addLine(StatusLine.sentence(LayoutStyle.AUTO, value).screenStyle().setFormatter(v -> Component.translatable(templateKey, v)));
    }

    @Nullable
    private static String clientReadingLabel(String templateKey) {
        String template = Language.getInstance().getOrDefault(templateKey).stripTrailing();
        int end;
        if (template.endsWith("%1$s") || template.endsWith("%1$d")) end = template.length() - 4;
        else if (template.endsWith("%s") || template.endsWith("%d")) end = template.length() - 2;
        else return null;
        String prefix = ChatFormatting.stripFormatting(template.substring(0, end));
        if (prefix == null || prefix.indexOf('%') >= 0) return null;
        int cut = prefix.length();
        while (cut > 0 && (Character.isWhitespace(prefix.charAt(cut - 1)) || prefix.charAt(cut - 1) == ':' || prefix.charAt(cut - 1) == '：')) cut--;
        return cut == 0 ? null : prefix.substring(0, cut);
    }

    public StatusLine addNumber(String labelKey, LongSupplier value, String unit) {
        return addLine(labelKey, numberText(value, unit));
    }

    public StatusLine addDecimal(String labelKey, DoubleSupplier value, String unit) {
        return addLine(labelKey, decimalText(value, unit));
    }

    public StatusLine addOnOff(String labelKey, BooleanSupplier value) {
        return addLine(labelKey, () -> value.getAsBoolean() ? ON_TEXT : OFF_TEXT);
    }

    public ControlPanel getControls() {
        if (controls == null) controls = ControlPanel.of(machine);
        return controls;
    }

    public static boolean isScreenText() {
        return COLLECTING_SCREEN_TEXT.get();
    }

    public MultiblockPage addText(Consumer<List<Component>> text, BiConsumer<String, ClickData> click) {
        var richText = new RichText().darkBackground().collapseEmpty();
        richText.textSupplier(machine.isRemote() ? null : list -> {
            COLLECTING_SCREEN_TEXT.set(Boolean.TRUE);
            try {
                text.accept(list);
            } finally {
                COLLECTING_SCREEN_TEXT.set(Boolean.FALSE);
            }
        }).clickHandler(click);
        screen.addChild(richText);
        return this;
    }

    public ProgressBar addBar(String labelKey, int color, Supplier<ProgressBar.Progress> progress) {
        return addGauge(ProgressBar.of(LayoutStyle.AUTO, Component.translatable(labelKey), color, progress));
    }

    public ProgressBar addBar(String labelKey, IntSupplier color, Supplier<ProgressBar.Progress> progress) {
        return addGauge(ProgressBar.of(LayoutStyle.AUTO, Component.translatable(labelKey), 0, progress).bindClientColor(color));
    }

    private ProgressBar addGauge(ProgressBar bar) {
        var gauge = new UIElement().layout(l -> l.width(LayoutStyle.AUTO).paddingAll(1));
        gauge.setBackground(UITheme.SCREEN_GAUGE_FRAME);
        gauge.addChild(bar);
        screen.addChild(gauge);
        return bar;
    }

    public MultiblockPage bindAlert(BooleanSupplier condition, String textKey) {
        this.alert = condition;
        this.alertText = Component.translatable(textKey);
        return this;
    }

    public MultiblockPage addSection(Widget section) {
        sections.add(section);
        return this;
    }

    public UIElement build() {
        var content = UIElement.column(UISizes.CONTENT_WIDTH).layout(l -> l.gapAll(UISizes.SECTION_GAP));
        screen.addChild(recipeList);
        content.addChild(screen);
        if (controls != null && !controls.isEmpty()) content.addChild(controls.build());
        for (var section : sections) content.addChild(section);
        var scroller = ScrollerView.page("multiblock.page", UISizes.CONTENT_WIDTH).adaptiveWidth();
        scroller.addScrollViewChild(content);
        return UIElement.column(LayoutStyle.AUTO).addChild(scroller);
    }

    private void addProgressBar() {
        var logic = machine.getRecipeLogic();
        addBar(PROGRESS, UITheme::barProgress,
                () -> logic.isIdle() || logic.getLastRecipe() == null ? ProgressBar.Progress.EMPTY : new ProgressBar.Progress(logic.getProgress(), logic.getDuration(), 0)).ticks();
    }

    public static Supplier<Component> numberText(LongSupplier value, String unit) {
        return cached(value, unit.isEmpty() ? v -> Component.literal(FormattingUtil.formatNumbers(v)) :
                v -> Component.literal(FormattingUtil.formatNumbers(v) + " " + unit));
    }

    public static Supplier<Component> decimalText(DoubleSupplier value, String unit) {
        return cached(() -> Double.doubleToLongBits(value.getAsDouble()), bits -> {
            var text = FormattingUtil.formatNumber2Places(Double.longBitsToDouble(bits));
            return Component.literal(unit.isEmpty() ? text : text + " " + unit);
        });
    }

    public static Supplier<Component> percentText(LongSupplier value) {
        return cached(value, v -> Component.literal(FormattingUtil.formatNumbers(v) + "%"));
    }

    public static Supplier<Component> fractionText(LongSupplier current, LongSupplier max, String unit) {
        return new Supplier<>() {

            private long lastCurrent = Long.MIN_VALUE;
            private long lastMax = Long.MIN_VALUE;
            private Component text = NONE;

            @Override
            public Component get() {
                long c = current.getAsLong(), m = max.getAsLong();
                if (c != lastCurrent || m != lastMax) {
                    lastCurrent = c;
                    lastMax = m;
                    var value = FormattingUtil.formatNumbers(c) + " / " + FormattingUtil.formatNumbers(m);
                    text = Component.literal(unit.isEmpty() ? value : value + " " + unit);
                }
                return text;
            }
        };
    }

    public static Supplier<Component> cached(LongSupplier value, LongFunction<Component> format) {
        return new Supplier<>() {

            private long last = Long.MIN_VALUE;
            private Component text = NONE;

            @Override
            public Component get() {
                long current = value.getAsLong();
                if (current != last) {
                    last = current;
                    text = format.apply(current);
                }
                return text;
            }
        };
    }

    public static <T> Supplier<Component> cachedRef(Supplier<T> value, Function<T, Component> format) {
        return new Supplier<>() {

            private boolean computed;
            @Nullable
            private T last;
            private Component text = NONE;

            @Override
            public Component get() {
                var current = value.get();
                if (!computed || current != last) {
                    computed = true;
                    last = current;
                    text = format.apply(current);
                }
                return text;
            }
        };
    }

    public static Supplier<Component> recipeTypeText(IRecipeLogicMachine machine, @Nullable String emptyKey) {
        return cachedRef(machine::getRecipeType, type -> {
            if (type == null) return NONE;
            if (emptyKey != null && type == GTRecipeTypes.DUMMY_RECIPES) return Component.translatable(emptyKey);
            return Component.translatable(type.registryName.toLanguageKey());
        });
    }

    private void addStateLine() {
        addLine(STATE, this::stateText).bindLevel(this::stateLevel).bindDetail(() -> reasonView().hover);
        var detail = new RichText().darkBackground().collapseEmpty();
        detail.textSupplier(machine.isRemote() ? null : list -> {
            var view = reasonView();
            if (view.line != null) list.add(view.line);
        });
        screen.addChild(detail);
    }

    private Component stateText() {
        var logic = machine.getRecipeLogic();
        if (!machine.isFormed()) return UNFORMED;
        if (!logic.isWorkingEnabled()) return PAUSED;
        if (logic.isWorking()) return alert != null && alert.getAsBoolean() ? alertText : RUNNING;
        var view = reasonView();
        return view.shown ? view.headline : IDLE;
    }

    private Level stateLevel() {
        var logic = machine.getRecipeLogic();
        if (!machine.isFormed()) return Level.ERROR;
        if (!logic.isWorkingEnabled()) return Level.WARNING;
        if (logic.isWorking()) return alert != null && alert.getAsBoolean() ? Level.WARNING : Level.GOOD;
        return reasonView().level;
    }

    private ReasonView reasonView() {
        var logic = machine.getRecipeLogic();
        int status = logic.getStatus();
        var reason = machine.isFormed() && logic.isWorkingEnabled() ? IdleReasonInfo.reasonOf(logic) : null;
        if (reasonView == null || reasonView.status != status || reasonView.reason != reason) reasonView = ReasonView.of(status, reason);
        return reasonView;
    }

    private record ReasonView(int status, @Nullable Component reason, boolean shown, Component headline, Level level,
                              @Nullable Component line, Component hover) {

        private static ReasonView of(int status, @Nullable Component reason) {
            boolean shown = reason != null || status == RecipeLogic.WAITING;
            if (!shown) return new ReasonView(status, null, false, IDLE, Level.NORMAL, null, NONE);
            var entry = IdleReasonInfo.lookup(reason);
            var headline = IdleReasonInfo.headline(status, reason, entry);
            var level = IdleReasonInfo.level(status, reason);
            var detail = IdleReasonInfo.detail(status, reason, entry);
            if (detail == null) return new ReasonView(status, reason, true, headline, level, null, NONE);
            if (IdleReasonInfo.showsDetail(status, reason, entry)) return new ReasonView(status, reason, true, headline, level, detail, NONE);
            return new ReasonView(status, reason, true, headline, level, null, detail);
        }
    }
}
