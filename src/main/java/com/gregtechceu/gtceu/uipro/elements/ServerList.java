package com.gregtechceu.gtceu.uipro.elements;

import com.gregtechceu.gtceu.uipro.LayoutStyle;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.data.UICodecs;
import com.gregtechceu.gtceu.uipro.data.UIStructure;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;

import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.network.FriendlyByteBuf;

import com.gto.datasynclib.datastream.codec.ByteBufCodecs;
import com.gto.datasynclib.datastream.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * 由服务端决定内容的列表：行的 key 列表是服务端权威的结构状态，按 key 复用、增删、重排行，旧结构上的请求按纪元丢弃。
 */
public class ServerList<K> extends UIElement {

    public static final int MAX_ROWS = 1024;
    private static final int DEFAULT_RESCAN_TICKS = 20;

    private record Entry<K>(K key, boolean shown) {}

    private final Function<K, ? extends Widget> rowFactory;
    private final Supplier<List<K>> source;
    private final UIElement body;
    private final Rows rows;
    @Nullable
    private IntSupplier version;
    private int rescanTicks = DEFAULT_RESCAN_TICKS;
    private boolean keepHidden;
    private int rowHeight = UISizes.CONTROL_HEIGHT;
    private int maxRows = Integer.MAX_VALUE;
    @Nullable
    private TextLine emptyLine;
    @Nullable
    private ScrollerView scroller;

    protected ServerList(StreamCodec<? super FriendlyByteBuf, K> codec, Supplier<List<K>> source, Function<K, ? extends Widget> rowFactory) {
        this.source = source;
        this.rowFactory = rowFactory;
        layout(l -> l.column());
        this.rows = new Rows(codec);
        this.body = new UIElement().layout(l -> l.column()).addChild(rows);
        addChild(body);
    }

    public static <K> ServerList<K> of(StreamCodec<? super FriendlyByteBuf, K> codec, Supplier<List<K>> source, Function<K, ? extends Widget> rowFactory) {
        return new ServerList<>(codec, source, rowFactory);
    }

    public ServerList<K> version(IntSupplier version) {
        this.version = version;
        this.rescanTicks = 0;
        return this;
    }

    public ServerList<K> rescanEvery(int ticks) {
        this.rescanTicks = Math.max(0, ticks);
        return this;
    }

    public ServerList<K> keepHidden() {
        this.keepHidden = true;
        return this;
    }

    public ServerList<K> emptyText(String key) {
        if (emptyLine == null) {
            emptyLine = TextLine.translatable(LayoutStyle.AUTO, key).bindClientColor(UITheme::textSecondary);
            body.addChild(emptyLine);
            emptyLine.setDisplay(rows.shownCount == 0);
        }
        return this;
    }

    public ServerList<K> rowsLayout(Consumer<LayoutStyle> layout) {
        rows.layout(layout);
        return this;
    }

    public ServerList<K> rowHeight(int rowHeight) {
        this.rowHeight = rowHeight;
        updateScrollerHeight();
        return this;
    }

    public ServerList<K> maxRows(int maxRows) {
        this.maxRows = Math.max(1, maxRows);
        updateScrollerHeight();
        return this;
    }

    public ServerList<K> scroll(String id, int width) {
        if (scroller != null) return this;
        removeWidget(body);
        scroller = new ScrollerView(id, width, ScrollerView.heightFor(1, rowHeight, UISizes.GAP));
        scroller.addScrollViewChild(body);
        addChild(scroller);
        updateScrollerHeight();
        return this;
    }

    @Nullable
    public ScrollerView getScroller() {
        return scroller;
    }

    public List<K> getKeys() {
        var keys = new ArrayList<K>(rows.entries.size());
        for (var entry : rows.entries) {
            if (entry.shown()) keys.add(entry.key());
        }
        return keys;
    }

    private void updateScrollerHeight() {
        if (scroller != null && maxRows != Integer.MAX_VALUE) scroller.setAdaptiveHeight(ScrollerView.heightFor(maxRows, rowHeight, UISizes.GAP));
    }

    private List<Entry<K>> scan() {
        var latest = source.get();
        var result = new ArrayList<Entry<K>>(Math.min(MAX_ROWS, latest.size() + (keepHidden ? rows.entries.size() : 0)));
        var seen = new LinkedHashSet<K>();
        for (var key : latest) {
            if (result.size() >= MAX_ROWS) break;
            if (seen.add(key)) result.add(new Entry<>(key, true));
        }
        if (keepHidden) {
            for (var entry : rows.entries) {
                if (result.size() >= MAX_ROWS) break;
                if (!seen.contains(entry.key())) result.add(new Entry<>(entry.key(), false));
            }
        }
        return List.copyOf(result);
    }

    private final class Rows extends UIElement {

        private final UIStructure<List<Entry<K>>> state;
        private List<Entry<K>> entries = List.of();
        private Map<K, Widget> widgetsByKey = new HashMap<>();
        private int shownCount;
        private boolean built;
        private int builtVersion;
        private int ticks;

        private Rows(StreamCodec<? super FriendlyByteBuf, K> codec) {
            layout(l -> l.column().gapAll(UISizes.GAP));
            StreamCodec<FriendlyByteBuf, Entry<K>> entryCodec = StreamCodec.composite(
                    codec, Entry::key,
                    ByteBufCodecs.BOOL, Entry::shown,
                    Entry::new);
            state = addStructure(UICodecs.list(entryCodec, MAX_ROWS), () -> entries).serverOnly().apply(this::apply);
        }

        @Override
        public void initWidget() {
            if (!isRemote() && !built) {
                built = true;
                if (version != null) builtVersion = version.getAsInt();
                apply(scan());
            }
            super.initWidget();
        }

        @Override
        public void detectAndSendChanges() {
            if (!isRemote() && built && shouldScan()) state.request(scan());
            super.detectAndSendChanges();
        }

        private boolean shouldScan() {
            boolean changed = false;
            if (version != null) {
                int current = version.getAsInt();
                if (current != builtVersion) {
                    builtVersion = current;
                    changed = true;
                }
            }
            if (rescanTicks > 0 && ++ticks >= rescanTicks) {
                ticks = 0;
                changed = true;
            }
            return changed;
        }

        private void apply(List<Entry<K>> next) {
            var used = new HashMap<K, Widget>(next.size() * 2);
            var ordered = new ArrayList<Widget>(next.size());
            for (var entry : next) {
                var widget = widgetsByKey.get(entry.key());
                if (widget == null) widget = rowFactory.apply(entry.key());
                used.put(entry.key(), widget);
                ordered.add(widget);
            }
            for (var old : widgetsByKey.entrySet()) {
                if (!used.containsKey(old.getKey())) removeWidget(old.getValue());
            }
            for (int i = 0; i < ordered.size(); i++) {
                var widget = ordered.get(i);
                if (i < widgets.size() && widgets.get(i) == widget) continue;
                removeWidget(widget);
                addWidget(i, widget);
            }
            int shown = 0;
            for (int i = 0; i < ordered.size(); i++) {
                boolean visible = next.get(i).shown();
                if (visible) shown++;
                var widget = ordered.get(i);
                if (widget instanceof UIElement element) {
                    element.setDisplay(visible);
                } else {
                    widget.setVisible(visible);
                    widget.setActive(visible);
                }
            }
            entries = next;
            widgetsByKey = used;
            shownCount = shown;
            if (emptyLine != null) emptyLine.setDisplay(shown == 0);
        }
    }
}
