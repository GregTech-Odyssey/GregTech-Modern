package com.gregtechceu.gtceu.uipro.elements;

import com.gregtechceu.gtceu.uipro.ElementState;
import com.gregtechceu.gtceu.uipro.ILayoutItem;
import com.gregtechceu.gtceu.uipro.ILocalUI;
import com.gregtechceu.gtceu.uipro.ITooltipOwner;
import com.gregtechceu.gtceu.uipro.LayoutStyle;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.data.RPC;
import com.gregtechceu.gtceu.uipro.data.SyncValue;
import com.gregtechceu.gtceu.uipro.data.UIChannel;
import com.gregtechceu.gtceu.uipro.data.UICodecs;
import com.gregtechceu.gtceu.uipro.render.FittedText;
import com.gregtechceu.gtceu.uipro.render.UIDraw;
import com.gregtechceu.gtceu.uipro.render.UIPixels;
import com.gregtechceu.gtceu.uipro.render.UIStates;
import com.gregtechceu.gtceu.uipro.render.UIText;
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
 * （点击经 {@link com.gregtechceu.gtceu.uipro.data.RPC} 上行，服务端统一校验禁用与限流）；没有服务端的界面（{@link ILocalUI}）里两者都在本端执行。
 */
public class Button extends ButtonWidget implements ILayoutItem, ElementState.Host<Button>, UIChannel.Host, ITooltipOwner {

    public static final int HEIGHT = UISizes.CONTROL_HEIGHT;
    public static final int WIDTH = UISizes.BUTTON_WIDTH;
    public static final int ICON_SIZE = UISizes.ICON_BUTTON_SIZE;

    private final UIChannel channel = new UIChannel(this);
    private final LayoutStyle layoutStyle;
    @Nullable
    private final Component text;
    @Nullable
    private Supplier<String> clientText;
    @Nullable
    private final IGuiTexture icon;
    private final ElementState state = new ElementState(this, this);
    private Supplier<UITheme.ButtonVariant> variant = () -> UITheme.ButtonVariant.DEFAULT;
    @Nullable
    private Consumer<ClickData> onServerClick;
    @Nullable
    private Consumer<ClickData> onClientClick;
    private final RPC<ClickData> click;
    @Nullable
    private FittedText fittedLabel;

    protected Button(int width, int height, @Nullable Component text, @Nullable IGuiTexture icon) {
        super(0, 0, Math.max(0, width), height, IGuiTexture.EMPTY, null);
        this.layoutStyle = LayoutStyle.fixed(width, height, () -> UIElement.markLayoutDirty(this));
        this.text = text;
        this.icon = icon;
        this.click = channel.addRPC(UICodecs.CLICK, (player, clickData) -> {
            if (onServerClick != null) onServerClick.accept(clickData);
        });
    }

    public Button layout(Consumer<LayoutStyle> layout) {
        layout.accept(layoutStyle);
        return this;
    }

    @Override
    public LayoutStyle getLayoutStyle() {
        return layoutStyle;
    }

    public static Button of(int width) {
        return new Button(width, HEIGHT, null, null);
    }

    public static Button of(int width, int height) {
        return new Button(width, height, null, null);
    }

    public static Button text(int width, Component text) {
        return new Button(width, HEIGHT, text, null);
    }

    public static Button text(int width, int height, Component text) {
        return new Button(width, height, text, null);
    }

    public static Button translatable(int width, String key) {
        return text(width, Component.translatable(key));
    }

    public static Button icon(IGuiTexture icon) {
        return new Button(ICON_SIZE, HEIGHT, null, icon);
    }

    public static Button icon(int size, IGuiTexture icon) {
        return new Button(size, size, null, icon);
    }

    public static Button glyph(String glyph) {
        return new Button(ICON_SIZE, HEIGHT, Component.literal(glyph), null);
    }

    public Button bindClientText(Supplier<String> text) {
        this.clientText = text;
        return this;
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

    public Button setVariant(UITheme.ButtonVariant variant) {
        this.variant = () -> variant;
        return this;
    }

    public Button bindClientVariant(Supplier<UITheme.ButtonVariant> variant) {
        this.variant = variant;
        return this;
    }

    @Override
    public boolean hasOwnTooltip(int mouseX, int mouseY) {
        return !tooltipTexts.isEmpty() || isDisabled();
    }

    @Override
    public ElementState getState() {
        return state;
    }

    @Override
    public Button setSelected(@Nullable BooleanSupplier selected) {
        return ElementState.Host.super.setSelected(selected);
    }

    @Override
    public Button disabled(BooleanSupplier serverCondition, @Nullable String reasonKey) {
        return ElementState.Host.super.disabled(serverCondition, reasonKey);
    }

    @Nullable
    protected Component getDisplayText() {
        return text;
    }

    protected int getTextColor(boolean hovered, boolean enabled) {
        return variant.get().textColor(enabled);
    }

    @Override
    public <T> SyncValue<T> addSyncValue(SyncValue<T> value) {
        return channel.addSyncValue(value);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || isDisabled() || !isMouseOverElement(mouseX, mouseY)) return false;
        isClicked = true;
        var clickData = new ClickData();
        if (onClientClick != null) onClientClick.accept(clickData);
        if (onServerClick != null) click.send(clickData);
        playButtonClickSound();
        return true;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int x = getPositionX(), y = getPositionY(), w = getSizeWidth(), h = getSizeHeight();
        // 禁用不换灰色贴图（看起来像一直按着），保持原色、叠统一斜纹
        int states = ElementState.resolve(this, mouseX, mouseY);
        boolean hovered = UIStates.has(states, UIStates.HOVERED);
        if (hovered && isClicked) states |= UIStates.PRESSED;
        variant.get().texture().draw(graphics, states, x, y, w, h);
        // 内容画在底部台阶以上的区域
        int faceHeight = h - UISizes.BUTTON_LIP_HEIGHT;
        if (icon != null) {
            int size = Math.min(w, faceHeight) - 4;
            icon.draw(graphics, mouseX, mouseY, UIPixels.center(x, w, size), UIPixels.center(y, faceHeight, size), size, size);
        }
        var label = clientText == null ? getDisplayText() : null;
        if (clientText != null || label != null) {
            // 方形按钮只放一个字符，不留两侧空白，否则 "×" 这类宽字符会被截成省略号
            int maxTextWidth = w <= ICON_SIZE ? w - 2 : w - 2 * UISizes.TEXT_PADDING;
            if (fittedLabel == null) fittedLabel = new FittedText();
            var shown = clientText != null ? fittedLabel.fit(clientText.get(), maxTextWidth) : fittedLabel.fit(label, maxTextWidth);
            UIText.drawLeft(graphics, shown, x + w / 2 - fittedLabel.width() / 2, UIText.centerY(y, faceHeight), getTextColor(hovered, true));
        }
        // 斜纹只画在按钮面上（黑边以内、底部台阶以上）
        if (UIStates.has(states, UIStates.DISABLED)) UIDraw.disabledHatch(graphics, x, y, w, faceHeight);
    }

    /** 禁用时悬停提示末尾先"禁止操作"再原因。 */
    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (isSelected()) UIDraw.selectionFrame(graphics, getPositionX(), getPositionY(), getSizeWidth(), getSizeHeight());
        if (ElementState.showDisabledTooltip(this, mouseX, mouseY, tooltipTexts)) return;
        super.drawInForeground(graphics, mouseX, mouseY, partialTicks);
    }

    @Override
    public void writeInitialData(FriendlyByteBuf buffer) {
        super.writeInitialData(buffer);
        channel.writeInitialData(buffer);
    }

    @Override
    public void readInitialData(FriendlyByteBuf buffer) {
        super.readInitialData(buffer);
        channel.readInitialData(buffer);
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        channel.detectAndSendChanges();
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void readUpdateInfo(int id, FriendlyByteBuf buffer) {
        if (!channel.readUpdateInfo(id, buffer)) super.readUpdateInfo(id, buffer);
    }

    @Override
    public void handleClientAction(int id, FriendlyByteBuf buffer) {
        channel.handleClientAction(id, buffer);
    }

    @Override
    public void initWidget() {
        super.initWidget();
        channel.prime();
    }

    @Override
    public UIChannel getChannel() {
        return channel;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void updateScreen() {
        super.updateScreen();
        channel.pollClient();
    }
}
