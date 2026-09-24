package com.gregtechceu.gtceu.uipro.elements;

import com.gregtechceu.gtceu.uipro.ElementState;
import com.gregtechceu.gtceu.uipro.ILayoutItem;
import com.gregtechceu.gtceu.uipro.LayoutStyle;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;

import com.lowdragmc.lowdraglib.gui.widget.TextFieldWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.client.Minecraft;
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
 * 与服务端字符串双向绑定的输入框，对应 LDLib2 {@code TextField.bind(DataBindingBuilder.string(getter, setter))}。
 * <p>
 * 外观与物品槽同为内凹框、白字，获得焦点时内圈加亮。标准高 {@link #HEIGHT}；
 * 宽度可固定，也可 {@code layout(l -> l.flexGrow(1))} 在横向行里吃满剩余宽度。
 * 同步走 LDLib1 {@code TextFieldWidget} 自带机制：服务端 getter 变化时下发，客户端输入后上行调用 setter。
 */
public class TextField extends UIElement {

    public static final int HEIGHT = UISizes.CONTROL_HEIGHT;

    private final Input input;

    public TextField(int width, Supplier<String> getter, Consumer<String> setter) {
        this.input = new Input(width, HEIGHT, getter, setter);
        addChild(input);
        layout(l -> l.column().size(width, HEIGHT));
    }

    /** 右键清空输入框（清空后同样上行给 setter）。 */
    public TextField setRightClickClear(boolean rightClickClear) {
        input.rightClickClear = rightClickClear;
        return this;
    }

    /** 输入为空且未获得焦点时显示的灰色提示文字（客户端取值）。 */
    public TextField setPlaceholder(Supplier<Component> placeholder) {
        input.placeholder = placeholder;
        return this;
    }

    /**
     * 按服务端条件禁用（LDLib2 {@code disabled()}）：禁用时叠统一斜纹，点击、键盘输入无效，服务端也不接受上行的文字；
     * 悬停提示先"禁止操作"再原因。上级元素禁用时输入框也禁用。
     */
    @Override
    public TextField disabled(BooleanSupplier serverCondition, @Nullable String reasonKey) {
        super.disabled(serverCondition, reasonKey);
        return this;
    }

    public TextFieldWidget getInput() {
        return input;
    }

    /** 提示挂在内部输入框上：LDLib1 只显示鼠标下最内层控件的提示。 */
    @Override
    public Widget setHoverTooltips(Component... tooltipText) {
        input.setHoverTooltips(tooltipText);
        return this;
    }

    /// 输入框铺满外框：宽度随外框拉伸，高度 flexGrow 吃满
    private static final class Input extends TextFieldWidget implements ILayoutItem {

        private final LayoutStyle layoutStyle = LayoutStyle.fixed(LayoutStyle.AUTO, LayoutStyle.AUTO, () -> UIElement.markLayoutDirty(this)).flexGrow(1);
        private boolean rightClickClear;
        @Nullable
        private Supplier<Component> placeholder;

        private Input(int width, int height, Supplier<String> getter, Consumer<String> setter) {
            super(0, 0, width, height, getter, setter);
            setBordered(false);
            setTextColor(UITheme.FIELD_TEXT);
        }

        @Override
        public LayoutStyle getLayoutStyle() {
            return layoutStyle;
        }

        @Override
        @OnlyIn(Dist.CLIENT)
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!isEnabled()) return false;
            if (rightClickClear && button == 1 && isMouseOverElement(mouseX, mouseY)) {
                textField.setValue("");
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        /// 已获得焦点时变为不可编辑（如样板被取走），键盘输入也要拦下
        @Override
        @OnlyIn(Dist.CLIENT)
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            return isEnabled() && super.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        @OnlyIn(Dist.CLIENT)
        public boolean charTyped(char codePoint, int modifiers) {
            return isEnabled() && super.charTyped(codePoint, modifiers);
        }

        private boolean isEnabled() {
            return !ElementState.isDisabled(this);
        }

        /// 服务端再判一次：禁用时不接受客户端上行的文字（客户端可以伪造请求）
        @Override
        public void handleClientAction(int id, FriendlyByteBuf buffer) {
            if (!isEnabled()) return;
            super.handleClientAction(id, buffer);
        }

        /** 禁用时悬停提示末尾先"禁止操作"再原因。 */
        @Override
        @OnlyIn(Dist.CLIENT)
        public void drawInForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
            if (ElementState.drawDisabledTooltip(this, mouseX, mouseY, tooltipTexts)) return;
            super.drawInForeground(graphics, mouseX, mouseY, partialTicks);
        }

        @Override
        @OnlyIn(Dist.CLIENT)
        public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
            boolean active = isEnabled();
            int x = getPositionX(), y = getPositionY(), w = getSizeWidth(), h = getSizeHeight();
            UITheme.drawInset(graphics, x, y, w, h, active && isFocus());
            super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
            if (placeholder != null && !isFocus() && getCurrentString().isEmpty()) {
                var font = Minecraft.getInstance().font;
                var text = UITheme.clip(font, placeholder.get().getString(), getSizeWidth() - 4);
                graphics.drawString(font, text, getPositionX() + 2, getPositionY() + (getSizeHeight() - 8) / 2, UITheme.PLACEHOLDER_TEXT, false);
            }
            if (!active) UITheme.drawDisabled(graphics, x, y, w, h);
        }
    }
}
