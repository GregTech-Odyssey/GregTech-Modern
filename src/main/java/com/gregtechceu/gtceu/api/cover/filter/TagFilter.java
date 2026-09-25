package com.gregtechceu.gtceu.api.cover.filter;

import com.gregtechceu.gtceu.data.lang.LangHandler;
import com.gregtechceu.gtceu.uipro.LayoutStyle;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.data.SyncValue;
import com.gregtechceu.gtceu.uipro.elements.InfoIcon;
import com.gregtechceu.gtceu.uipro.elements.RichText;
import com.gregtechceu.gtceu.uipro.elements.ScrollerView;
import com.gregtechceu.gtceu.uipro.elements.StatusLine;
import com.gregtechceu.gtceu.uipro.elements.TextField;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;
import com.gregtechceu.gtceu.utils.TagExprFilter;

import com.lowdragmc.lowdraglib.gui.util.ClickData;
import com.lowdragmc.lowdraglib.gui.widget.*;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.tags.TagKey;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public abstract class TagFilter<T, S extends Filter<T, S>> implements Filter<T, S> {

    private static final Pattern DOUBLE_WILDCARD = Pattern.compile("\\*{2,}");
    private static final Pattern DOUBLE_AND = Pattern.compile("&{2,}");
    private static final Pattern DOUBLE_OR = Pattern.compile("\\|{2,}");
    private static final Pattern DOUBLE_NOT = Pattern.compile("!{2,}");
    private static final Pattern DOUBLE_XOR = Pattern.compile("\\^{2,}");
    private static final Pattern DOUBLE_SPACE = Pattern.compile(" {2,}");
    private static final int TAG_LIST_MAX_LINES = 6;
    @Getter
    protected String oreDictFilterExpression = "";
    protected Consumer<S> itemWriter = filter -> {};
    protected Consumer<S> onUpdated = filter -> itemWriter.accept(filter);
    protected TagExprFilter.TagExprParser.MatchExpr matchExpr = null;

    protected TagFilter() {}

    @Override
    public boolean isBlank() {
        return oreDictFilterExpression.isBlank();
    }

    public CompoundTag saveFilter() {
        if (isBlank()) {
            return null;
        }
        var tag = new CompoundTag();
        tag.putString("oreDict", oreDictFilterExpression);
        return tag;
    }

    public void setOreDict(String oreDict) {
        this.oreDictFilterExpression = oreDict;
        matchExpr = TagExprFilter.parseExpression(oreDictFilterExpression);
        onUpdated.accept((S) this);
    }

    private static String normalizeExpression(String input) {
        input = DOUBLE_WILDCARD.matcher(input).replaceAll("*");
        input = DOUBLE_AND.matcher(input).replaceAll("&");
        input = DOUBLE_OR.matcher(input).replaceAll("|");
        input = DOUBLE_NOT.matcher(input).replaceAll("!");
        input = DOUBLE_XOR.matcher(input).replaceAll("^");
        input = DOUBLE_SPACE.matcher(input).replaceAll(" ");
        StringBuilder builder = new StringBuilder();
        int unclosed = 0;
        char last = ' ';
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == ' ') {
                if (last != '(') builder.append(" ");
                continue;
            }
            if (c == '(') unclosed++;
            else if (c == ')') {
                unclosed--;
                if (last == '&' || last == '|' || last == '^') {
                    int l = builder.lastIndexOf(" " + last);
                    int l2 = builder.lastIndexOf(String.valueOf(last));
                    builder.insert(l == l2 - 1 ? l : l2, ")");
                    continue;
                }
                if (i > 0 && builder.charAt(builder.length() - 1) == ' ') {
                    builder.deleteCharAt(builder.length() - 1);
                }
            } else if ((c == '&' || c == '|' || c == '^') && last == '(') {
                builder.deleteCharAt(builder.lastIndexOf("("));
                builder.append(c).append(" (");
                continue;
            }
            builder.append(c);
            last = c;
        }
        if (unclosed > 0) {
            builder.append(")".repeat(unclosed));
        } else if (unclosed < 0) {
            unclosed = -unclosed;
            for (int i = 0; i < unclosed; i++) {
                builder.insert(0, "(");
            }
        }
        input = builder.toString();
        input = DOUBLE_SPACE.matcher(input).replaceAll(" ");
        return input;
    }

    @Override
    public Widget createConfigUI() {
        var field = new TextField(0, () -> oreDictFilterExpression, this::setOreDict);
        field.getInput().setMaxStringLength(64).setValidator(TagFilter::normalizeExpression);
        field.layout(l -> l.flexGrow(1));
        var info = new InfoIcon(InfoIcon.Kind.INFO, LangHandler.getMultiLang("cover.tag_filter.info").toArray(new Component[0]));
        var query = new TagQuery();
        var inputRow = UIElement.row(UISizes.SLOT).layout(l -> l.gapAll(UISizes.GAP).alignCenter())
                .addChildren(field, info, createQuerySlot(query));

        var text = new RichText();
        text.textSupplier(lines -> {
            if (!text.isRemote()) query.appendLines(lines);
        });
        text.clickHandler((tag, click) -> onTagClicked(query, tag, click));
        var scroller = new ScrollerView("cover.tag_filter.tags", UISizes.CONTENT_WIDTH - 2 * UITheme.PANEL_PADDING, StatusLine.HEIGHT)
                .adaptiveHeight(TAG_LIST_MAX_LINES * StatusLine.HEIGHT + 2 * UITheme.PANEL_PADDING)
                .layoutContent(l -> l.paddingAll(UITheme.PANEL_PADDING));
        scroller.setBackground(UITheme.PANEL);
        scroller.addScrollViewChild(text);
        var tagList = UIElement.column(LayoutStyle.AUTO).addChild(scroller);
        tagList.setDisplay(false);

        var root = UIElement.column(LayoutStyle.AUTO).layout(l -> l.gapAll(UISizes.GAP));
        root.addSyncValue(SyncValue.of(query::hasTags, SyncValue.BOOLEAN, false).onChanged(tagList::setDisplay));
        return root.addChildren(inputRow, tagList);
    }

    abstract Widget createQuerySlot(TagQuery query);

    private void onTagClicked(TagQuery query, String tag, ClickData click) {
        if (click.isRemote) {
            if (click.button == 1) copyToClipboard(tag);
        } else if (click.button == 0 && query.contains(tag)) {
            setOreDict(normalizeExpression(tag));
        }
    }

    @OnlyIn(Dist.CLIENT)
    private static void copyToClipboard(String text) {
        Minecraft.getInstance().keyboardHandler.setClipboard(text);
    }

    static final class TagQuery {

        private Supplier<Object> key = () -> null;
        private Supplier<Stream<TagKey<?>>> source = Stream::empty;
        private boolean loaded;
        private Object lastKey;
        private List<String> tags = Collections.emptyList();
        private List<Component> lines = Collections.emptyList();

        void bind(Supplier<Object> key, Supplier<Stream<TagKey<?>>> source) {
            this.key = key;
            this.source = source;
            this.loaded = false;
        }

        private void refresh() {
            var current = key.get();
            if (loaded && current == lastKey) return;
            loaded = true;
            lastKey = current;
            var names = source.get().map(tag -> tag.location().toString()).toList();
            var hover = new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("cover.tag_filter.tag_entry.tooltip"));
            var newLines = new ArrayList<Component>(names.size());
            for (var name : names) {
                newLines.add(ComponentPanelWidget.withButton(Component.literal(name), name).copy().withStyle(s -> s.withHoverEvent(hover)));
            }
            tags = names;
            lines = newLines;
        }

        boolean hasTags() {
            refresh();
            return !tags.isEmpty();
        }

        boolean contains(String tag) {
            refresh();
            return tags.contains(tag);
        }

        void appendLines(List<Component> out) {
            refresh();
            out.addAll(lines);
        }
    }

    @Override
    public void setOnUpdated(Consumer<S> onUpdated) {
        this.onUpdated = filter -> {
            this.itemWriter.accept(filter);
            onUpdated.accept(filter);
        };
    }
}
