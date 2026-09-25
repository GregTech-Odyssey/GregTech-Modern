package com.gregtechceu.gtceu.uiwidgets.patternbuilder;

import com.gregtechceu.gtceu.uipro.ILocalUI;
import com.gregtechceu.gtceu.uipro.LayoutStyle;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.elements.Button;
import com.gregtechceu.gtceu.uipro.elements.ItemCell;
import com.gregtechceu.gtceu.uipro.elements.ItemView;
import com.gregtechceu.gtceu.uipro.elements.NumberField;
import com.gregtechceu.gtceu.uipro.elements.ScrollerView;
import com.gregtechceu.gtceu.uipro.elements.StatusLine;
import com.gregtechceu.gtceu.uipro.elements.Switch;
import com.gregtechceu.gtceu.uipro.elements.TextLine;
import com.gregtechceu.gtceu.uipro.elements.VirtualList;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;
import com.gregtechceu.gtceu.uipro.utils.UIPreferences;
import com.gregtechceu.gtceu.uipro.window.MachineWindow;
import com.gregtechceu.gtceu.uipro.window.Popup;
import com.gregtechceu.gtceu.utils.FormattingUtil;

import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.ChatFormatting;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import dev.vfyjxf.taffy.style.AlignContent;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

public class PatternBuilderPanel extends UIElement implements ILocalUI {

    public static final String TITLE = "gtceu.pattern_builder.title";
    public static final String BLOCKS = "gtceu.pattern_builder.blocks";
    public static final String INCLUDE = "gtceu.pattern_builder.include";
    public static final String CHANGE_BLOCK = "gtceu.pattern_builder.change_block";
    public static final String SELECT = "gtceu.pattern_builder.select";
    public static final String SELECTED = "gtceu.pattern_builder.selected";
    public static final String STOCK = "gtceu.pattern_builder.stock";
    public static final String CRAFTABLE = "gtceu.pattern_builder.craftable";
    public static final String OPTIONAL = "gtceu.pattern_builder.optional";
    public static final String INPUTS = "gtceu.pattern_builder.inputs";
    public static final String WRITE = "gtceu.pattern_builder.write";
    public static final String CANCEL = "gtceu.pattern_builder.cancel";
    public static final String CANNOT_WRITE = "gtceu.pattern_builder.cannot_write";
    public static final String SORT_PREFERENCE = "pattern_builder.sort";

    private static final int COUNT_FIELD_WIDTH = 2 * UISizes.VALUE_WIDTH + 3 * UISizes.GAP + UISizes.SECTION_GAP;
    private static final int TALLY_WIDTH = 2 * UISizes.VALUE_WIDTH;
    private static final long[] COUNT_STEPS = { 1, 8 };

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
    private PopupSlot popups = PopupSlot.NONE;
    private PatternBuilderModel.Sort sort;

    public PatternBuilderPanel(PatternBuilderModel model, ItemStack icon, Component title, int inputLimit, int maxHeight, Runnable onWrite,
                               Runnable onClose) {
        this.model = model;
        this.inputLimit = inputLimit;
        this.sort = UIPreferences.get(SORT_PREFERENCE, PatternBuilderModel.Sort.STOCK);
        model.sort(sort);
        setClientSideWidget();
        layout(l -> l.column().gapAll(UISizes.SECTION_GAP).paddingAll(UISizes.POPUP_PADDING).paddingBottom(UISizes.POPUP_PADDING_BOTTOM));
        setBackground(UITheme.WINDOW);

        var close = Button.glyph("×").setOnClientClick(onClose);
        close.setHoverTooltips(MachineWindow.POPUP_CLOSE);
        var titleRow = UIElement.row(UISizes.CONTROL_HEIGHT).layout(l -> l.gapAll(UISizes.GAP).alignCenter());
        if (!icon.isEmpty()) titleRow.addChild(ItemView.of(icon));
        titleRow.addChildren(TextLine.constant(LayoutStyle.AUTO, Component.translatable(TITLE, title)).layout(l -> l.flex(1)), close);

        var content = new UIElement().layout(l -> l.column().widthAuto().minWidth(UISizes.POPUP_CONTENT_WIDTH).gapAll(UISizes.SECTION_GAP));
        if (!model.getFixed().isEmpty() || !model.getGroups().isEmpty()) content.addChild(blocksSection());
        var roles = model.getRoles();
        for (int i = 0; i < roles.size(); i++) content.addChild(roleCard(i, roles.get(i)));

        var actions = UIElement.row(UISizes.CONTROL_HEIGHT).layout(l -> l.gapAll(UISizes.GAP).alignCenter()).addChildren(
                Button.translatable(UISizes.BUTTON_WIDTH, CANCEL).setOnClientClick(onClose),
                TextLine.of(LayoutStyle.AUTO, () -> Component.translatable(INPUTS).append(" " + model.getInputs().size() + " / " + inputLimit))
                        .alignCenter().level(() -> model.getInputs().size() <= inputLimit ? StatusLine.Level.NORMAL : StatusLine.Level.ERROR)
                        .layout(l -> l.flex(1)),
                Button.translatable(UISizes.BUTTON_WIDTH, WRITE).setVariant(UITheme.ButtonVariant.CONFIRM)
                        .disabled(() -> !canWrite(), CANNOT_WRITE)
                        .setOnServerClick(onWrite));

        int chrome = UISizes.POPUP_PADDING + 2 * UISizes.CONTROL_HEIGHT + 2 * UISizes.SECTION_GAP + UISizes.POPUP_PADDING_BOTTOM;
        var scroller = new ScrollerView("pattern_builder", UISizes.POPUP_CONTENT_WIDTH, UISizes.SLOT).adaptiveWidth();
        scroller.addScrollViewChild(content);
        scroller.adaptiveHeight(Math.max(UISizes.SLOT, maxHeight - chrome));
        addChildren(titleRow, scroller, actions);
    }

    public void setPopupSlot(PopupSlot popups) {
        this.popups = popups;
    }

    public boolean canWrite() {
        int inputs = model.getInputs().size();
        return inputs > 0 && inputs <= inputLimit && !model.hasOverflow();
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
        button.setHoverTooltips(SELECT);
        return button;
    }

    private UIElement blocksSection() {
        var section = UIElement.section();
        section.addChild(TextLine.translatable(LayoutStyle.AUTO, BLOCKS));
        boolean choices = !model.getGroups().isEmpty();
        var fixed = model.getFixed();
        if (!fixed.isEmpty()) {
            section.addChild(new VirtualList(1, UISizes.SLOT, UISizes.GAP, fixed::size, index -> {
                var entry = fixed.get(index);
                var row = itemRow(Switch.of(entry::isIncluded, entry::setIncluded), ItemCell.constant(entry.getStack()), entry::getStack,
                        entry::getCount, countText(entry::getCount));
                if (choices) row.addChild(UIElement.spacer(UISizes.ICON_BUTTON, UISizes.ICON_BUTTON));
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
        section.addChild(UIElement.row(UISizes.CONTROL_HEIGHT).layout(l -> l.gapAll(UISizes.GAP).alignCenter()).addChildren(
                TextLine.constant(LayoutStyle.AUTO, role.getName()).layout(l -> l.flex(1)),
                TextLine.of(TALLY_WIDTH, () -> tallyText(role)).alignRight().level(() -> roleLevel(role)),
                selectButton("role." + index, () -> rolePopup(role))));
        return section;
    }

    private Popup rolePopup(PatternBuilderModel.Role role) {
        return Popup.of(() -> Component.empty().append(role.getName()).append("  ").append(tallyText(role)), column -> {
            var section = UIElement.section();
            section.addChild(new VirtualList(1, UISizes.SLOT, UISizes.GAP, () -> role.getCandidates().size(), index -> {
                var candidate = role.getCandidates().get(index);
                var field = new NumberField(COUNT_FIELD_WIDTH, candidate::getSelected, value -> candidate.setSelected((int) value),
                        () -> 0, candidate::getMax, COUNT_STEPS);
                return itemRow(null, ItemCell.constant(candidate.getStack()), candidate::getStack,
                        () -> Math.max(candidate.getSelected(), 1), field);
            }));
            column.addChild(section);
        });
    }

    private Popup fillPopup(PatternBuilderModel.Group group) {
        return Popup.of(() -> Component.translatable(CHANGE_BLOCK), column -> {
            var section = UIElement.section();
            var fills = group.getSortedFills();
            section.addChild(new VirtualList(1, UISizes.SLOT, UISizes.GAP, fills::size, index -> {
                var stack = fills.get(index);
                var choose = Button.text(UISizes.BUTTON_WIDTH, () -> I18n.get(group.getFillStack() == stack ? SELECTED : SELECT))
                        .setVariant(() -> group.getFillStack() == stack ? UITheme.ButtonVariant.CONFIRM : UITheme.ButtonVariant.DEFAULT)
                        .setOnServerClick(() -> group.setFill(stack));
                return itemRow(null, ItemCell.constant(stack), () -> stack, group::getFillCount, choose);
            }));
            column.addChild(section);
        });
    }

    private UIElement itemRow(Switch include, ItemCell cell, Supplier<ItemStack> stack, IntSupplier need, Widget trailing) {
        var names = new UIElement().layout(l -> l.column().flex(1).minWidth(0).justifyContent(AlignContent.CENTER))
                .addChildren(TextLine.of(LayoutStyle.AUTO, () -> plainName(stack.get())),
                        TextLine.of(LayoutStyle.AUTO, () -> stockText(stack.get())).setSmall().styled()
                                .level(() -> stockLevel(stack.get(), need.getAsInt())));
        var row = UIElement.row(UISizes.SLOT).layout(l -> l.gapAll(UISizes.GAP).alignCenter());
        if (include != null) {
            include.setHoverTooltips(INCLUDE);
            row.addChild(include);
        }
        return row.addChildren(cell, names, trailing);
    }

    private static TextLine countText(IntSupplier count) {
        return TextLine.of(UISizes.VALUE_WIDTH, () -> Component.literal("×" + count.getAsInt())).alignRight();
    }

    private static Component plainName(ItemStack stack) {
        return Component.literal(ChatFormatting.stripFormatting(stack.getHoverName().getString()));
    }

    private Component stockText(ItemStack stack) {
        var stock = model.getStock(stack);
        if (stock == null) return Component.empty();
        var text = Component.translatable(STOCK, FormattingUtil.formatNumberReadable(stock.stored()));
        if (stock.craftable()) {
            text.append(" · ").append(Component.translatable(CRAFTABLE).withStyle(style -> style.withColor(UITheme.STATUS_TEXT_WARNING & 0xFFFFFF)));
        }
        return text;
    }

    private StatusLine.Level stockLevel(ItemStack stack, int need) {
        var stock = model.getStock(stack);
        if (stock == null) return StatusLine.Level.NORMAL;
        return stock.stored() >= need ? StatusLine.Level.GOOD : StatusLine.Level.ERROR;
    }

    private static Component tallyText(PatternBuilderModel.Role role) {
        var requirement = requirement(role);
        return Component.literal(role.getTally() + " / ").append(requirement.isEmpty() ? Component.translatable(OPTIONAL) : Component.literal(requirement));
    }

    private static StatusLine.Level roleLevel(PatternBuilderModel.Role role) {
        if (role.isRequired()) return role.isSatisfied() ? StatusLine.Level.GOOD : StatusLine.Level.ERROR;
        return role.isSatisfied() ? StatusLine.Level.NORMAL : StatusLine.Level.ERROR;
    }

    private static String requirement(PatternBuilderModel.Role role) {
        int min = role.getMin(), max = role.getMaxCount();
        if (min > 0 && max >= 0) return min == max ? String.valueOf(min) : min + "~" + max;
        if (min > 0) return "≥" + min;
        if (max >= 0) return "≤" + max;
        return "";
    }
}
