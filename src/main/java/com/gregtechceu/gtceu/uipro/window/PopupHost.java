package com.gregtechceu.gtceu.uipro.window;

import com.gregtechceu.gtceu.uipro.LayoutStyle;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.data.UICodecs;
import com.gregtechceu.gtceu.uipro.data.UIStructure;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import com.gto.datasynclib.datastream.codec.ByteStreamCodec;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.FlexWrap;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

/**
 * {@link MachineWindow} 右侧的弹出面板容器。
 * <p>
 * 页面按需注册任意种面板（可以一种都没有）；每种面板（一个键）同时至多打开一个，用不同参数再次打开时原地替换；
 * 不同种的面板可以同时打开，按打开顺序从上往下排，放不下主窗口高度时另起一列往右排。
 * 没有打开任何面板时容器尺寸为 0，不占位置。
 * <p>
 * 打开的面板列表是服务端权威的结构状态（{@link UIStructure}）：客户端只发"想要的列表"，服务端逐项校验后先通知客户端、再构建自己的那份，
 * 两端按同样的差异增删，子控件顺序即打开顺序；旧面板上的过期请求按纪元丢弃。切换页面时两端各自 {@link #reset()}，不发包。
 * <p>
 * 容器本身只由 {@link MachineWindow} 使用；对外只公开 {@link #standalonePanel}，供不在 MachineWindow 里的控件复用同一面板外观。
 */
public final class PopupHost extends UIElement {

    private static final int MAX_KEY_LENGTH = 64;
    private static final int MAX_OPEN = 16;
    private static final ByteStreamCodec<OpenPopup> ENTRY = ByteStreamCodec.composite(
            ByteStreamCodec.STRING_CODEC, OpenPopup::key,
            ByteStreamCodec.INT_CODEC, OpenPopup::argument,
            OpenPopup::new);

    private final Map<String, IntFunction<Popup>> factories = new HashMap<>();
    /** 已打开的面板，与子控件一一对应、顺序相同。 */
    private final List<OpenPopup> opened = new ArrayList<>();
    private final UIStructure<List<OpenPopup>> state;
    private int maxHeight = Integer.MAX_VALUE;

    private record OpenPopup(String key, int argument) {}

    /// 面板按打开顺序纵向排，超过高度上限换到右边一列（flex-wrap），各面板保持自身宽度；尺寸变化经 {@link com.gregtechceu.gtceu.uipro.ILayoutHost} 通知窗口
    PopupHost() {
        layout(l -> l.column().flexWrap(FlexWrap.WRAP).alignStart().alignContent(AlignContent.FLEX_START)
                .rowGap(UISizes.SECTION_GAP).columnGap(UISizes.POPUP_GAP));
        state = addStructure(UICodecs.list(ENTRY, MAX_OPEN), () -> List.copyOf(opened))
                .prepare(this::prepare)
                .apply(this::apply);
    }

    void register(String key, IntFunction<Popup> factory) {
        if (key.length() > MAX_KEY_LENGTH) throw new IllegalArgumentException("Popup key too long: " + key);
        factories.put(key, factory);
    }

    /** 每个面板的高度上限（客户端按屏幕高度设定，见 {@link UISizes#SCREEN_MARGIN}；服务端不限），一列排满这个高度后另起一列。 */
    void setMaxHeight(int maxHeight) {
        if (this.maxHeight == maxHeight) return;
        this.maxHeight = maxHeight;
        for (var widget : widgets) {
            if (widget instanceof PopupCard panel) panel.setMaxHeight(maxHeight);
        }
        layout(l -> l.maxHeight(maxHeight == Integer.MAX_VALUE ? LayoutStyle.AUTO : maxHeight));
    }

    /** 本端清空注册表并关闭所有面板（切换页面时两端各自调用）。 */
    void reset() {
        factories.clear();
        opened.clear();
        clearAllWidgets();
        getChannel().advanceEpoch();
    }

    boolean isOpen(String key, int argument) {
        int index = indexOf(key);
        return index >= 0 && opened.get(index).argument() == argument;
    }

    boolean isOpen(String key) {
        return indexOf(key) >= 0;
    }

    int argumentOf(String key) {
        int index = indexOf(key);
        return index < 0 ? -1 : opened.get(index).argument();
    }

    void open(String key, int argument) {
        var target = new ArrayList<>(state.getTarget());
        var entry = new OpenPopup(key, argument);
        int index = -1;
        for (int i = 0; i < target.size(); i++) {
            if (target.get(i).key().equals(key)) index = i;
        }
        if (index >= 0) target.set(index, entry);
        else target.add(entry);
        request(target);
    }

    void close(String key) {
        var target = new ArrayList<>(state.getTarget());
        target.removeIf(popup -> popup.key().equals(key));
        request(target);
    }

    void closeAll() {
        request(List.of());
    }

    private void request(List<OpenPopup> target) {
        state.request(List.copyOf(target));
    }

    @Nullable
    private Map<OpenPopup, Popup> prepare(List<OpenPopup> target) {
        var prepared = new HashMap<OpenPopup, Popup>();
        for (int i = 0; i < target.size(); i++) {
            var entry = target.get(i);
            for (int j = 0; j < i; j++) {
                if (target.get(j).key().equals(entry.key())) return null;
            }
            if (opened.contains(entry)) continue;
            var popup = create(entry);
            if (popup == null) return null;
            prepared.put(entry, popup);
        }
        return prepared;
    }

    private void apply(List<OpenPopup> target, @Nullable Map<OpenPopup, Popup> prepared) {
        for (int i = opened.size() - 1; i >= 0; i--) {
            var key = opened.get(i).key();
            if (target.stream().noneMatch(entry -> entry.key().equals(key))) {
                opened.remove(i);
                removeWidget(widgets.get(i));
            }
        }
        for (var entry : target) {
            int index = indexOf(entry.key());
            if (index >= 0 && opened.get(index).argument() == entry.argument()) continue;
            var popup = prepared != null && prepared.containsKey(entry) ? prepared.get(entry) : create(entry);
            if (popup == null) {
                if (index >= 0) {
                    opened.remove(index);
                    removeWidget(widgets.get(index));
                }
                continue;
            }
            var key = entry.key();
            var panel = new PopupCard("popup." + key, popup, maxHeight, () -> close(key));
            if (index >= 0) {
                removeWidget(widgets.get(index));
                opened.set(index, entry);
                addWidget(index, panel);
            } else {
                opened.add(entry);
                addWidget(panel);
            }
        }
    }

    private Popup create(OpenPopup entry) {
        var factory = factories.get(entry.key());
        return factory == null ? null : factory.apply(entry.argument());
    }

    private int indexOf(String key) {
        for (int i = 0; i < opened.size(); i++) {
            if (opened.get(i).key().equals(key)) return i;
        }
        return -1;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void readUpdateInfo(int id, FriendlyByteBuf buffer) {
        if ((id == 1 || id == 2) && !hasChild(buffer)) return;
        super.readUpdateInfo(id, buffer);
    }

    private boolean hasChild(FriendlyByteBuf buffer) {
        buffer.markReaderIndex();
        int index = buffer.readVarInt();
        buffer.resetReaderIndex();
        return index >= 0 && index < widgets.size();
    }

    /**
     * 单独的一个面板，外观与右侧弹出面板完全相同（Ore 窗口外框、标题行与 {@code [×]}、高度随内容的滚动区、Shift+点击优先），
     * 给不在 {@link MachineWindow} 里、没有面板容器可用的控件做退路。面板挂到哪里、何时开关、两端怎样保持一致都由调用方负责；
     * {@code close} 在客户端点 {@code [×]} 时执行。高度不设上限（没有窗口可参照屏幕高度）。
     */
    public static UIElement standalonePanel(String key, Popup popup, Runnable close) {
        return new PopupCard("popup." + key, popup, Integer.MAX_VALUE, close);
    }
}
