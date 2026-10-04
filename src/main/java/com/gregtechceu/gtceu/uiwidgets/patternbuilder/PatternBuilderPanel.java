package com.gregtechceu.gtceu.uiwidgets.patternbuilder;

import com.gregtechceu.gtceu.uipro.Horizontal;
import com.gregtechceu.gtceu.uipro.ILocalUI;
import com.gregtechceu.gtceu.uipro.LayoutStyle;
import com.gregtechceu.gtceu.uipro.Level;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.elements.Button;
import com.gregtechceu.gtceu.uipro.elements.ItemCell;
import com.gregtechceu.gtceu.uipro.elements.ItemView;
import com.gregtechceu.gtceu.uipro.elements.NumberField;
import com.gregtechceu.gtceu.uipro.elements.ScrollerView;
import com.gregtechceu.gtceu.uipro.elements.Switch;
import com.gregtechceu.gtceu.uipro.elements.TextLine;
import com.gregtechceu.gtceu.uipro.elements.VirtualList;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;
import com.gregtechceu.gtceu.uipro.utils.UIPreferences;
import com.gregtechceu.gtceu.uipro.window.MachineWindow;
import com.gregtechceu.gtceu.uipro.window.Popup;
import com.gregtechceu.gtceu.uiwidgets.icon.WidgetIcons;
import com.gregtechceu.gtceu.utils.FormattingUtil;

import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.ChatFormatting;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import dev.vfyjxf.taffy.style.AlignContent;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

@OnlyIn(Dist.CLIENT)
public class PatternBuilderPanel extends UIElement implements ILocalUI {

    public static final String TITLE = "gtceu.pattern_builder.title";
    public static final String BLOCKS = "gtceu.pattern_builder.blocks";
    public static final String INCLUDE = "gtceu.pattern_builder.include";
    public static final String CHANGE_BLOCK = "gtceu.pattern_builder.change_block";
    public static final String SELECT = "gtceu.pattern_builder.select";
    public static final String SELECTED = "gtceu.pattern_builder.selected";
    public static final String STOCK = "gtceu.pattern_builder.stock";
    public static final String CARRIED = "gtceu.pattern_builder.carried";
    public static final String CRAFTABLE = "gtceu.pattern_builder.craftable";
    public static final String OPTIONAL = "gtceu.pattern_builder.optional";
    public static final String INPUTS = "gtceu.pattern_builder.inputs";
    public static final String WRITE = "gtceu.pattern_builder.write";
    public static final String CANCEL = "gtceu.pattern_builder.cancel";
    public static final String CANNOT_WRITE = "gtceu.pattern_builder.cannot_write";
    public static final String SORT_PREFERENCE = "pattern_builder.sort";
    public static final String BACK = "gtceu.pattern_builder.back";
    public static final String EXTRAS = "gtceu.pattern_builder.extras";
    public static final String FAVORITE = "gtceu.pattern_builder.favorite";
    public static final String TOTAL = "gtceu.pattern_builder.total";
    public static final String NO_HATCHES_ON = "gtceu.pattern_builder.no_hatches.on";
    public static final String NO_HATCHES_OFF = "gtceu.pattern_builder.no_hatches.off";
    public static final String NO_HATCHES_DESC = "gtceu.pattern_builder.no_hatches.desc";
    private static final String NO_HATCHES_PREFERENCE = "pattern_builder.no_hatches";

    public record Footer(String titleKey, String confirmKey, String includeKey, String blockedKey, boolean showInputs, boolean requireComplete,
                         @Nullable Runnable back, boolean hatchToggle) {

        public static final Footer ENCODE = new Footer(TITLE, WRITE, INCLUDE, CANNOT_WRITE, true, false, null, true);

        public Footer(String titleKey, String confirmKey, String includeKey, String blockedKey, boolean showInputs, boolean requireComplete,
                      @Nullable Runnable back) {
            this(titleKey, confirmKey, includeKey, blockedKey, showInputs, requireComplete, back, false);
        }
    }

    private static final int COUNT_FIELD_WIDTH = 2 * UISizes.VALUE_WIDTH + 3 * UISizes.GAP + UISizes.SECTION_GAP;
    private static final int TALLY_WIDTH = 2 * UISizes.VALUE_WIDTH;
    private static final long[] COUNT_STEPS = { 1, 8 };
    private static final int ROLE_POPUP_WIDTH = UISizes.POPUP_CONTENT_WIDTH + UISizes.ICON_BUTTON_SIZE + UISizes.GAP;
    private static final int FILL_POPUP_WIDTH = ROLE_POPUP_WIDTH + UISizes.BUTTON_WIDTH + UISizes.GAP;

    public interface PopupSlot {

        PopupSlot NONE = new PopupSlot() {

            @Override
            public void toggle(String key, Supplier<Popup> factory) {}

            @Override
            public boolean isOpen(String key) {
                return false;
            }

            @Override
            public void rebuild() {}
        };

        void toggle(String key, Supplier<Popup> factory);

        boolean isOpen(String key);

        void rebuild();
    }

    private final PatternBuilderModel model;
    private final int inputLimit;
    private final Footer footer;
    private PopupSlot popups = PopupSlot.NONE;
    private PatternBuilderModel.Sort sort;
    private int stockVersion = CarriedStock.version();

    public PatternBuilderPanel(PatternBuilderModel model, ItemStack icon, Component title, int inputLimit, int maxHeight, Runnable onWrite,
                               Runnable onClose) {
        this(model, icon, title, inputLimit, maxHeight, onWrite, onClose, Footer.ENCODE);
    }

    public PatternBuilderPanel(PatternBuilderModel model, ItemStack icon, Component title, int inputLimit, int maxHeight, Runnable onWrite,
                               Runnable onClose, Footer footer) {
        this.model = model;
        this.inputLimit = inputLimit;
        this.footer = footer;
        this.sort = UIPreferences.get(SORT_PREFERENCE, PatternBuilderModel.Sort.STOCK);
        model.sort(sort);
        if (footer.hatchToggle() && isNoHatches()) model.clearHatches();
        setClientSideWidget();
        layout(l -> l.column().gapAll(UISizes.SECTION_GAP).paddingAll(UISizes.POPUP_PADDING).paddingBottom(UISizes.POPUP_PADDING_BOTTOM));
        setBackground(UITheme.WINDOW);

        var close = Button.glyph("×").setOnClientClick(onClose);
        close.tooltips(MachineWindow.POPUP_CLOSE);
        var titleRow = UIElement.centeredRow(UISizes.CONTROL_HEIGHT);
        if (!icon.isEmpty()) titleRow.addChild(ItemView.of(icon));
        titleRow.addChildren(TextLine.constant(LayoutStyle.AUTO, Component.translatable(footer.titleKey(), title)).layout(l -> l.flex(1)), close);

        var content = new UIElement().layout(l -> l.column().widthAuto().minWidth(UISizes.POPUP_CONTENT_WIDTH).gapAll(UISizes.SECTION_GAP));
        if (!model.getFixed().isEmpty() || !model.getGroups().isEmpty()) content.addChild(blocksSection());
        var roles = model.getRoles();
        var sections = model.getSections();
        if (sections.size() > 1) {
            for (int i = 0; i < sections.size(); i++) content.addChild(sectionCard(sections.getInt(i)));
        } else {
            for (int i = 0; i < roles.size(); i++) content.addChild(roleCard(i, roles.get(i)));
        }

        var back = footer.back();
        var actions = UIElement.centeredRow(UISizes.CONTROL_HEIGHT).addChildren(
                back == null ? Button.translatable(UISizes.BUTTON_WIDTH, CANCEL).setOnClientClick(onClose) :
                        Button.translatable(UISizes.BUTTON_WIDTH, BACK).setOnClientClick(back),
                UIElement.flexSpacer(),
                Button.translatable(UISizes.BUTTON_WIDTH, footer.confirmKey()).setVariant(UITheme.ButtonVariant.CONFIRM)
                        .disabled(() -> !canWrite(), footer.blockedKey())
                        .setOnServerClick(onWrite));
        var counter = footer.showInputs() ?
                TextLine.of(LayoutStyle.AUTO, () -> Component.translatable(INPUTS).append(" " + model.getInputs().size() + " / " + inputLimit))
                        .setTextAlign(Horizontal.RIGHT).bindLevel(() -> model.getInputs().size() <= inputLimit ? Level.NORMAL : Level.ERROR) :
                null;

        int chrome = UISizes.POPUP_PADDING + 2 * UISizes.CONTROL_HEIGHT + 2 * UISizes.SECTION_GAP + UISizes.POPUP_PADDING_BOTTOM +
                (counter == null ? 0 : UISizes.CONTROL_HEIGHT + UISizes.SECTION_GAP);
        var scroller = new ScrollerView("pattern_builder", UISizes.POPUP_CONTENT_WIDTH, UISizes.SLOT_SIZE).adaptiveWidth();
        scroller.addScrollViewChild(content);
        scroller.setAdaptiveHeight(Math.max(UISizes.SLOT_SIZE, maxHeight - chrome));
        addChildren(titleRow, scroller);
        if (counter != null) addChild(counter);
        addChild(actions);
    }

    public void setPopupSlot(PopupSlot popups) {
        this.popups = popups;
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        int current = CarriedStock.version();
        if (current == stockVersion) return;
        stockVersion = current;
        if (sort == PatternBuilderModel.Sort.NONE) return;
        model.sort(sort);
        popups.rebuild();
    }

    public boolean canWrite() {
        int inputs = model.getInputs().size();
        return inputs > 0 && (!footer.showInputs() || inputs <= inputLimit) && !model.hasOverflow() && (!footer.requireComplete() || model.isComplete());
    }

    public boolean hasHatchToggle() {
        return footer.hatchToggle();
    }

    public static boolean isNoHatches() {
        return Boolean.parseBoolean(UIPreferences.get(NO_HATCHES_PREFERENCE, "false"));
    }

    public void setNoHatches(boolean noHatches) {
        UIPreferences.put(NO_HATCHES_PREFERENCE, String.valueOf(noHatches));
        if (noHatches) model.clearHatches();
        else model.selectMinimum();
        popups.rebuild();
    }

    public PatternBuilderModel.Sort getSort() {
        return sort;
    }

    public void setSort(PatternBuilderModel.Sort sort) {
        if (this.sort == sort) return;
        this.sort = sort;
        UIPreferences.put(SORT_PREFERENCE, sort);
        model.sort(sort);
        popups.rebuild();
    }

    private Button selectButton(String key, Supplier<Popup> factory) {
        var button = Button.icon(UITheme.ARROW_RIGHT).setOnClientClick(() -> popups.toggle(key, factory));
        button.setSelected(() -> popups.isOpen(key));
        button.tooltips(SELECT);
        return button;
    }

    private UIElement blocksSection() {
        var section = UIElement.section();
        section.addChild(TextLine.translatable(LayoutStyle.AUTO, BLOCKS));
        boolean choices = !model.getGroups().isEmpty();
        var fixed = model.getFixed();
        if (!fixed.isEmpty()) {
            section.addChild(new VirtualList(1, UISizes.SLOT_SIZE, UISizes.GAP, fixed::size, index -> {
                var entry = fixed.get(index);
                var row = itemRow(Switch.of(entry::isIncluded, entry::setIncluded), ItemCell.constant(entry.getStack()), entry::getStack,
                        entry::getCount, countText(entry::getCount));
                if (choices) row.addChild(UIElement.spacer(UISizes.ICON_BUTTON_SIZE, UISizes.ICON_BUTTON_SIZE));
                return row;
            }));
        }
        var groups = model.getGroups();
        for (int i = 0; i < groups.size(); i++) {
            var group = groups.get(i);
            var row = itemRow(Switch.of(group::isIncluded, group::setIncluded), ItemCell.of(group::getFillStack),
                    group::getFillStack, group::getFillCount, countText(group::getFillCount));
            row.addChild(selectButton("group." + i, () -> fillPopup(group)));
            section.addChild(row);
        }
        return section;
    }

    private UIElement roleCard(int index, PatternBuilderModel.Role role) {
        var section = UIElement.section();
        section.addChild(UIElement.centeredRow(UISizes.CONTROL_HEIGHT).addChildren(
                TextLine.constant(LayoutStyle.AUTO, role.getName()).layout(l -> l.flex(1)),
                TextLine.of(TALLY_WIDTH, () -> tallyText(role)).setTextAlign(Horizontal.RIGHT).bindLevel(() -> roleLevel(role)),
                selectButton("role." + index, () -> rolePopup(role))));
        return section;
    }

    private UIElement sectionCard(int section) {
        var card = UIElement.section();
        card.addChild(TextLine.constant(LayoutStyle.AUTO, model.getSectionTitle(section)));
        var roles = model.getRoles();
        for (int i = 0; i < roles.size(); i++) {
            var role = roles.get(i);
            if (role.getCandidates(section).isEmpty()) continue;
            var tally = TextLine.of(TALLY_WIDTH, () -> tallyText(role, section)).setTextAlign(Horizontal.RIGHT).bindLevel(() -> roleLevel(role));
            tally.tooltips(Component.translatable(TOTAL, tallyText(role)));
            card.addChild(UIElement.centeredRow(UISizes.CONTROL_HEIGHT).addChildren(
                    TextLine.constant(LayoutStyle.AUTO, role.getName()).layout(l -> l.flex(1)), tally,
                    selectButton("role." + i + "." + section, () -> rolePopup(role, section))));
        }
        return card;
    }

    private Popup rolePopup(PatternBuilderModel.Role role) {
        return rolePopup(role, -1);
    }

    private Popup rolePopup(PatternBuilderModel.Role role, int part) {
        Supplier<List<PatternBuilderModel.Candidate>> list = () -> part < 0 ? role.getCandidates() : role.getCandidates(part);
        return Popup.of(() -> Component.empty().append(role.getName()).append("  ").append(part < 0 ? tallyText(role) : tallyText(role, part)), column -> {
            var section = UIElement.section();
            section.layout(l -> l.minWidth(ROLE_POPUP_WIDTH));
            section.addChild(new VirtualList(1, UISizes.SLOT_SIZE, UISizes.GAP, () -> list.get().size(), index -> {
                var candidate = list.get().get(index);
                var field = NumberField.ofInt(COUNT_FIELD_WIDTH, candidate::getSelected, candidate::setSelected, () -> 0, candidate::getMax).setSteps(COUNT_STEPS);
                var trailing = UIElement.centeredRow(UISizes.SLOT_SIZE).addChildren(favorite(candidate.getStack()), field);
                return itemRow(null, ItemCell.constant(candidate.getStack()), candidate::getStack,
                        () -> Math.max(candidate.getSelected(), 1), trailing);
            }));
            column.addChild(section);
        });
    }

    private static Button favorite(ItemStack stack) {
        var item = stack.getItem();
        var star = Button.icon(UITheme.switching(() -> PatternFavorites.isFavorite(item), WidgetIcons.FAVORITE_OFF, WidgetIcons.FAVORITE_ON))
                .setOnClientClick(() -> PatternFavorites.toggle(item));
        star.tooltips(FAVORITE);
        return star;
    }

    private Popup fillPopup(PatternBuilderModel.Group group) {
        return Popup.of(() -> Component.translatable(CHANGE_BLOCK), column -> {
            var section = UIElement.section();
            section.layout(l -> l.minWidth(FILL_POPUP_WIDTH));
            var fills = group.getSortedFills();
            section.addChild(new VirtualList(1, UISizes.SLOT_SIZE, UISizes.GAP, fills::size, index -> {
                var stack = fills.get(index);
                var candidate = group.getFillCandidate(stack);
                var choose = Button.of(UISizes.BUTTON_WIDTH).bindClientText(() -> I18n.get(group.getFillStack() == stack ? SELECTED : SELECT))
                        .bindClientVariant(() -> group.getFillStack() == stack ? UITheme.ButtonVariant.CONFIRM : UITheme.ButtonVariant.DEFAULT)
                        .setOnServerClick(() -> {
                            group.setFill(stack);
                            popups.rebuild();
                        });
                var trailing = UIElement.centeredRow(UISizes.SLOT_SIZE);
                trailing.addChild(favorite(stack));
                if (group.getFillStack() == stack || candidate == null) {
                    trailing.addChild(TextLine.of(COUNT_FIELD_WIDTH, () -> Component.literal("×" + group.getFillCount())).setTextAlign(Horizontal.RIGHT));
                } else {
                    trailing.addChild(NumberField.ofInt(COUNT_FIELD_WIDTH, candidate::getSelected, candidate::setSelected, () -> 0, candidate::getMax).setSteps(COUNT_STEPS));
                }
                trailing.addChild(choose);
                IntSupplier need = group.getFillStack() == stack || candidate == null ? group::getFillCount : () -> Math.max(candidate.getSelected(), 1);
                return itemRow(null, ItemCell.constant(stack), () -> stack, need, trailing);
            }));
            column.addChild(section);
            var extras = group.getExtras();
            if (extras.isEmpty()) return;
            var extraSection = UIElement.section();
            extraSection.addChild(TextLine.translatable(LayoutStyle.AUTO, EXTRAS));
            extraSection.addChild(new VirtualList(1, UISizes.SLOT_SIZE, UISizes.GAP, extras::size, index -> {
                var candidate = extras.get(index);
                var field = NumberField.ofInt(COUNT_FIELD_WIDTH, candidate::getSelected, candidate::setSelected, () -> 0, candidate::getMax).setSteps(COUNT_STEPS);
                return itemRow(null, ItemCell.constant(candidate.getStack()), candidate::getStack,
                        () -> Math.max(candidate.getSelected(), 1), field);
            }));
            column.addChild(extraSection);
        });
    }

    private UIElement itemRow(Switch include, ItemCell cell, Supplier<ItemStack> stack, IntSupplier need, Widget trailing) {
        var names = new UIElement().layout(l -> l.column().flex(1).minWidth(0).justifyContent(AlignContent.CENTER))
                .addChildren(TextLine.of(LayoutStyle.AUTO, () -> plainName(stack.get())),
                        TextLine.of(LayoutStyle.AUTO, () -> stockText(stack.get())).styled()
                                .bindLevel(() -> stockLevel(stack.get(), need.getAsInt())));
        var row = UIElement.centeredRow(UISizes.SLOT_SIZE);
        if (include != null) {
            include.tooltips(footer.includeKey());
            row.addChild(include);
        }
        return row.addChildren(cell, names, trailing);
    }

    private static TextLine countText(IntSupplier count) {
        return TextLine.of(UISizes.VALUE_WIDTH, () -> Component.literal("×" + count.getAsInt())).setTextAlign(Horizontal.RIGHT);
    }

    private static Component plainName(ItemStack stack) {
        return Component.literal(ChatFormatting.stripFormatting(stack.getHoverName().getString()));
    }

    private Component stockText(ItemStack stack) {
        var stock = model.getStock(stack);
        if (stock == null) return Component.empty();
        var text = Component.empty();
        if (stock.stored() >= 0) text.append(Component.translatable(STOCK, FormattingUtil.formatNumberReadable(stock.stored())));
        if (stock.carried() >= 0) {
            if (stock.stored() >= 0) text.append(" · ");
            text.append(Component.translatable(CARRIED, FormattingUtil.formatNumberReadable(stock.carried())));
        }
        if (stock.craftable()) {
            text.append(" · ").append(Component.translatable(CRAFTABLE).withStyle(style -> style.withColor(UITheme.STATUS_TEXT_WARNING & 0xFFFFFF)));
        }
        return text;
    }

    private Level stockLevel(ItemStack stack, int need) {
        var stock = model.getStock(stack);
        if (stock == null) return Level.NORMAL;
        return stock.total() >= need ? Level.GOOD : Level.ERROR;
    }

    private static Component tallyText(PatternBuilderModel.Role role) {
        var requirement = requirement(role);
        return Component.literal(role.getTally() + " / ").append(requirement.isEmpty() ? Component.translatable(OPTIONAL) : Component.literal(requirement));
    }

    private static Component tallyText(PatternBuilderModel.Role role, int section) {
        var requirement = requirement(role);
        return Component.literal(role.getTally(section) + " / ").append(requirement.isEmpty() ? Component.translatable(OPTIONAL) : Component.literal(requirement));
    }

    private static Level roleLevel(PatternBuilderModel.Role role) {
        if (role.isRequired()) return role.isSatisfied() ? Level.GOOD : Level.ERROR;
        return role.isSatisfied() ? Level.NORMAL : Level.ERROR;
    }

    private static String requirement(PatternBuilderModel.Role role) {
        int min = role.getMin(), max = role.getMaxCount();
        if (min > 0 && max >= 0) return min == max ? String.valueOf(min) : min + "~" + max;
        if (min > 0) return "≥" + min;
        if (max >= 0) return "≤" + max;
        return "";
    }
}
