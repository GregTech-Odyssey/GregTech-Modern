package com.gregtechceu.gtceu.uiwidgets.filter;

import com.gregtechceu.gtceu.api.transfer.fluid.CustomFluidTank;
import com.gregtechceu.gtceu.uipro.LayoutStyle;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.data.SyncValue;
import com.gregtechceu.gtceu.uipro.elements.PhantomFluidSlot;
import com.gregtechceu.gtceu.uipro.elements.PhantomItemSlot;
import com.gregtechceu.gtceu.uipro.elements.RichText;
import com.gregtechceu.gtceu.uipro.elements.StatusLine;
import com.gregtechceu.gtceu.uipro.elements.TextField;
import com.gregtechceu.gtceu.uipro.elements.TextPane;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;

import com.lowdragmc.lowdraglib.gui.util.ClickData;
import com.lowdragmc.lowdraglib.gui.widget.ComponentPanelWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.misc.ItemStackTransfer;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.tags.TagKey;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * 标签输入与查询：一行输入框 + 查询用虚拟槽，槽里放入物品 / 流体后在下方列出它的全部标签（服务端算、下发），
 * 左键点标签交给 {@code onServerPick}，右键复制到剪贴板；没有标签时列表不占位置。
 */
public class TagLookupView extends UIElement {

    public static final int MAX_LINES = 6;
    private static final String TAG_ENTRY_TOOLTIP = "cover.tag_filter.tag_entry.tooltip";

    private final TextField field;
    private final UIElement inputRow;

    protected TagLookupView(String scrollerId, Supplier<String> getter, Consumer<String> setter, Consumer<String> onServerPick,
                            Widget lookupSlot, TagQuery query) {
        layout(l -> l.column().width(LayoutStyle.AUTO).gapAll(UISizes.GAP));
        field = TextField.of(0, getter, setter);
        field.layout(l -> l.flexGrow(1));
        inputRow = UIElement.centeredRow(UISizes.SLOT_SIZE).addChildren(field, lookupSlot);

        var text = new RichText();
        text.textSupplier(lines -> {
            if (!text.isRemote()) query.appendLines(lines);
        });
        text.clickHandler((tag, click) -> onTagClicked(query, onServerPick, tag, click));
        var scroller = TextPane.panel(scrollerId, UISizes.CONTENT_WIDTH - 2 * UISizes.PANEL_PADDING, StatusLine.HEIGHT, text)
                .setAdaptiveHeight(MAX_LINES * StatusLine.HEIGHT + 2 * UISizes.PANEL_PADDING);
        var tagList = UIElement.column(LayoutStyle.AUTO).addChild(scroller);
        tagList.setDisplay(false);
        addSyncValue(SyncValue.ofBool(query::hasTags).onChanged(tagList::setDisplay));
        addChildren(inputRow, tagList);
    }

    public static TagLookupView items(String scrollerId, Supplier<String> getter, Consumer<String> setter, Consumer<String> onServerPick,
                                      String... slotTooltipKeys) {
        var handler = new ItemStackTransfer(1);
        var query = new TagQuery(() -> handler.getStackInSlot(0).getItem(), () -> handler.getStackInSlot(0).getTags().map(t -> t));
        var slot = PhantomItemSlot.of(handler, 0).xeiPhantom();
        slot.setMaxStackSize(1);
        slot.tooltips(slotTooltipKeys);
        return new TagLookupView(scrollerId, getter, setter, onServerPick, slot, query);
    }

    public static TagLookupView fluids(String scrollerId, Supplier<String> getter, Consumer<String> setter, Consumer<String> onServerPick,
                                       String... slotTooltipKeys) {
        var tank = new CustomFluidTank(1);
        var query = new TagQuery(() -> tank.getFluid().getFluid(), () -> tank.getFluid().getFluid().defaultFluidState().getTags().map(t -> t));
        var slot = PhantomFluidSlot.of(tank, 0, tank::getFluid, tank::setFluid).xeiPhantom();
        slot.tooltips(slotTooltipKeys);
        return new TagLookupView(scrollerId, getter, setter, onServerPick, slot, query);
    }

    public TextField getField() {
        return field;
    }

    public TagLookupView addInputTool(Widget tool) {
        inputRow.addWidget(inputRow.widgets.size() - 1, tool);
        return this;
    }

    private static void onTagClicked(TagQuery query, Consumer<String> onServerPick, String tag, ClickData click) {
        if (click.isRemote) {
            if (click.button == 1) copyToClipboard(tag);
        } else if (click.button == 0 && query.contains(tag)) {
            onServerPick.accept(tag);
        }
    }

    @OnlyIn(Dist.CLIENT)
    private static void copyToClipboard(String text) {
        Minecraft.getInstance().keyboardHandler.setClipboard(text);
    }

    private static final class TagQuery {

        private final Supplier<Object> key;
        private final Supplier<Stream<TagKey<?>>> source;
        private boolean loaded;
        private Object lastKey;
        private List<String> tags = Collections.emptyList();
        private List<Component> lines = Collections.emptyList();

        private TagQuery(Supplier<Object> key, Supplier<Stream<TagKey<?>>> source) {
            this.key = key;
            this.source = source;
        }

        private void refresh() {
            var current = key.get();
            if (loaded && current == lastKey) return;
            loaded = true;
            lastKey = current;
            var names = source.get().map(tag -> tag.location().toString()).toList();
            var hover = new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable(TAG_ENTRY_TOOLTIP));
            var newLines = new ArrayList<Component>(names.size());
            for (var name : names) {
                newLines.add(ComponentPanelWidget.withButton(Component.literal(name), name).copy().withStyle(s -> s.withHoverEvent(hover)));
            }
            tags = names;
            lines = newLines;
        }

        private boolean hasTags() {
            refresh();
            return !tags.isEmpty();
        }

        private boolean contains(String tag) {
            refresh();
            return tags.contains(tag);
        }

        private void appendLines(List<Component> out) {
            refresh();
            out.addAll(lines);
        }
    }
}
