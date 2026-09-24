package com.gregtechceu.gtceu.uipro.elements;

import com.gregtechceu.gtceu.uipro.ElementState;
import com.gregtechceu.gtceu.uipro.ILayoutItem;
import com.gregtechceu.gtceu.uipro.ILocalUI;
import com.gregtechceu.gtceu.uipro.LayoutStyle;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.data.SyncValue;
import com.gregtechceu.gtceu.uipro.data.SyncValueHost;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.util.ClickData;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Ore UI 风格按钮，对应 LDLib2 {@code Button}：浅灰凸起底图、深色字无阴影，悬停/按下换深一档；禁用（{@link #disabled}）时保持原色、叠统一斜纹；
 * {@link #setVariant} 可切成绿色（确认）或红色（危险操作）。
 * <p>
 * 标准尺寸：高 {@link #HEIGHT}；文字按钮宽 {@link #WIDTH}，图标/箭头按钮 {@link #ICON_SIZE} 见方；
 * 宽度传 {@link LayoutStyle#AUTO} 时由布局决定（被父元素拉伸，整行按钮用）。
 * 点击：{@link #setOnServerClick} 只在服务端执行，{@link #setOnClientClick} 只在客户端执行
 * （LDLib1 的 {@code ButtonWidget} 两端都会回调，这里按 {@code ClickData.isRemote} 分派）；没有服务端的界面（{@link ILocalUI}）里两者都在本端执行。
 */
public class Button extends ButtonWidget implements ILayoutItem, ElementState.Host {

    public static final int HEIGHT = UISizes.CONTROL_HEIGHT;
    public static final int WIDTH = UISizes.BUTTON_WIDTH;
    public static final int ICON_SIZE = UISizes.ICON_BUTTON;

    private final SyncValueHost syncValues = new SyncValueHost(this);
    private final LayoutStyle layoutStyle;
    @Nullable
    private final Supplier<String> text;
    @Nullable
    private final IGuiTexture icon;
    private final ElementState state = new ElementState(this, this::addSyncValue);
    private Supplier<UITheme.ButtonVariant> variant = () -> UITheme.ButtonVariant.DEFAULT;
    @Nullable
    private Consumer<ClickData> onServerClick;
    @Nullable
    private Consumer<ClickData> onClientClick;

    protected Button(int width, int height, @Nullable Supplier<String> text, @Nullable IGuiTexture icon) {
        super(0, 0, Math.max(0, width), height, IGuiTexture.EMPTY, null);
        this.layoutStyle = LayoutStyle.fixed(width, height, () -> UIElement.markLayoutDirty(this));
        this.text = text;
        this.icon = icon;
        setOnPressCallback(this::dispatchClick);
    }

    /** 布局（对应 LDLib2 {@code layout}）：例如宽度传 {@link LayoutStyle#AUTO} 后被父元素拉伸，或 {@code flexGrow(1)} 吃满剩余宽度。 */
    public Button layout(Consumer<LayoutStyle> layout) {
        layout.accept(layoutStyle);
        return this;
    }

    @Override
    public LayoutStyle getLayoutStyle() {
        return layoutStyle;
    }

    /** 文字按钮，文字在客户端渲染时取值，放不下时截断加省略号。 */
    public static Button text(int width, Supplier<String> text) {
        return new Button(width, HEIGHT, text, null);
    }

    public static Button translatable(int width, String key) {
        return text(width, () -> Component.translatable(key).getString());
    }

    /** 方形图标按钮。 */
    public static Button icon(IGuiTexture icon) {
        return new Button(ICON_SIZE, HEIGHT, null, icon);
    }

    /** 指定边长的方形图标按钮（如页面切换里的页面图标）；图标画在按钮面中央，比按钮面小 4 像素。 */
    public static Button icon(IGuiTexture icon, int size) {
        return new Button(size, size, null, icon);
    }

    /** 方形单字符按钮（箭头、叉号等）。 */
    public static Button glyph(String glyph) {
        return new Button(ICON_SIZE, HEIGHT, () -> glyph, null);
    }

    public Button setOnServerClick(Consumer<ClickData> onServerClick) {
        this.onServerClick = onServerClick;
        return this;
    }

    public Button setOnServerClick(Runnable onServerClick) {
        return setOnServerClick(clickData -> onServerClick.run());
    }

    public Button setOnClientClick(Runnable onClientClick) {
        this.onClientClick = clickData -> onClientClick.run();
        return this;
    }

    /** 只在客户端执行，需要点击信息（Shift / Ctrl、哪个键）时用这个。 */
    public Button setOnClientClick(Consumer<ClickData> onClientClick) {
        this.onClientClick = onClientClick;
        return this;
    }

    /** 两端各执行一次（客户端点击时先执行，服务端收到点击后再执行），用于两端都要同步改动的界面结构。 */
    public Button setOnClick(Consumer<ClickData> onClick) {
        this.onServerClick = onClick;
        this.onClientClick = onClick;
        return this;
    }

    public Button setVariant(UITheme.ButtonVariant variant) {
        this.variant = () -> variant;
        return this;
    }

    /** 配色随状态变化（客户端绘制时取值，只应依赖已同步到客户端的值），例如同一个按钮在"加入 / 断开"之间切换。 */
    public Button setVariant(Supplier<UITheme.ButtonVariant> variant) {
        this.variant = variant;
        return this;
    }

    @Override
    public ElementState getState() {
        return state;
    }

    /** 选中（LDLib2 {@code setSelected}）：条件为真时画统一选中框；客户端每帧判定，只应依赖本端界面状态。 */
    public Button setSelected(@Nullable BooleanSupplier selected) {
        state.setSelected(selected);
        return this;
    }

    /**
     * 按服务端条件禁用（LDLib2 {@code disabled()}）：禁用时叠统一斜纹、点击无效（服务端也拦），
     * 悬停提示先"禁止操作"再原因 {@code reasonKey}（翻译键，可为 null）。上级元素禁用时本按钮也禁用。建界面时两端都要调用。
     */
    public Button disabled(BooleanSupplier serverCondition, @Nullable String reasonKey) {
        state.setDisabled(serverCondition, reasonKey);
        return this;
    }

    /** 悬浮提示由服务端计算后下发，适合依赖服务端独有状态的提示。 */
    public Button bindTooltip(Supplier<Component> tooltip) {
        syncValues.add(SyncValue.ofComponent(tooltip).onChanged(this::applyTooltip));
        applyTooltip(tooltip.get());
        return this;
    }

    private void applyTooltip(Component tooltip) {
        setHoverTooltips(List.of(tooltip));
    }

    /** 按钮上显示的文字，为 null 时不画字。 */
    @Nullable
    protected String getDisplayText() {
        return text == null ? null : text.get();
    }

    protected int getTextColor(boolean hovered, boolean enabled) {
        return variant.get().textColor(enabled);
    }

    protected <T> SyncValue<T> addSyncValue(SyncValue<T> value) {
        return syncValues.add(value);
    }

    private void dispatchClick(ClickData clickData) {
        // 服务端再判一次：客户端的点击请求可以伪造
        if (!clickData.isRemote && isDisabled()) return;
        var handler = clickData.isRemote ? onClientClick : onServerClick;
        if (handler != null) handler.accept(clickData);
        // 没有服务端的界面（ILocalUI，如 EMI 配方页）里，"服务端"那份也在本端执行；两端同一个回调（setOnClick）时只执行一次
        if (clickData.isRemote && onServerClick != null && onServerClick != onClientClick && ILocalUI.isLocal(this)) {
            onServerClick.accept(clickData);
        }
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || isDisabled()) return false;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int x = getPositionX(), y = getPositionY(), w = getSizeWidth(), h = getSizeHeight();
        // 禁用不换灰色贴图（看起来像一直按着），保持原色、叠统一斜纹
        boolean disabled = isDisabled();
        boolean hovered = !disabled && isMouseOverElement(mouseX, mouseY);
        UITheme.drawButton(graphics, x, y, w, h, variant.get(), hovered, hovered && isClicked, true);
        // 内容画在底部台阶以上的区域
        int faceHeight = h - UITheme.BUTTON_LIP;
        if (icon != null) {
            int size = Math.min(w, faceHeight) - 4;
            icon.draw(graphics, mouseX, mouseY, x + (w - size) / 2f, y + (faceHeight - size) / 2f, size, size);
        }
        var label = getDisplayText();
        if (label != null) {
            // 方形按钮只放一个字符，不留两侧空白，否则 "×" 这类宽字符会被截成省略号
            int maxTextWidth = w <= ICON_SIZE ? w - 2 : w - 2 * UISizes.TEXT_PADDING;
            UITheme.drawCenteredText(graphics, label, x + w / 2, y + (faceHeight - 8) / 2, maxTextWidth, getTextColor(hovered, true), false);
        }
        // 斜纹只画在按钮面上（黑边以内、底部台阶以上）
        if (disabled) UITheme.drawDisabled(graphics, x, y, w, h - UITheme.BUTTON_LIP);
    }

    /** 禁用时悬停提示末尾先"禁止操作"再原因。 */
    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (isSelected()) UITheme.drawSelection(graphics, getPositionX(), getPositionY(), getSizeWidth(), getSizeHeight());
        if (ElementState.drawDisabledTooltip(this, mouseX, mouseY, tooltipTexts)) return;
        super.drawInForeground(graphics, mouseX, mouseY, partialTicks);
    }

    @Override
    public void writeInitialData(FriendlyByteBuf buffer) {
        super.writeInitialData(buffer);
        syncValues.writeInitialData(buffer);
    }

    @Override
    public void readInitialData(FriendlyByteBuf buffer) {
        super.readInitialData(buffer);
        syncValues.readInitialData(buffer);
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        syncValues.detectAndSendChanges(this::writeUpdateInfo);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void readUpdateInfo(int id, FriendlyByteBuf buffer) {
        if (!syncValues.readUpdateInfo(id, buffer)) super.readUpdateInfo(id, buffer);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void updateScreen() {
        super.updateScreen();
        syncValues.pollClient();
    }
}
