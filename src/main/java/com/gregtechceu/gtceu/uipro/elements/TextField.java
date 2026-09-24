package com.gregtechceu.gtceu.uipro.elements;

import com.gregtechceu.gtceu.uipro.ElementState;
import com.gregtechceu.gtceu.uipro.ILayoutItem;
import com.gregtechceu.gtceu.uipro.LayoutStyle;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.data.ClientActions;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;

import com.lowdragmc.lowdraglib.gui.modular.ModularUIGuiContainer;
import com.lowdragmc.lowdraglib.gui.widget.TextFieldWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * 与服务端字符串双向绑定的输入框，对应 LDLib2 {@code TextField.bind(DataBindingBuilder.string(getter, setter))}。
 * <p>
 * 外观与物品槽同为内凹框、白字，获得焦点时内圈加亮。标准高 {@link #HEIGHT}；
 * 宽度可固定，也可 {@code layout(l -> l.flexGrow(1))} 在横向行里吃满剩余宽度。
 * 同步走 LDLib1 {@code TextFieldWidget} 自带机制：服务端 getter 变化时下发，客户端输入后上行调用 setter。
 * 默认每改一个字就上行；数值类输入用 {@link #commitOnSubmit}（确认后提交），见其说明。
 */
public class TextField extends UIElement {

    public static final int HEIGHT = UISizes.CONTROL_HEIGHT;

    private final Input input;

    public TextField(int width, Supplier<String> getter, Consumer<String> setter) {
        this.input = new Input(width, HEIGHT, getter, setter);
        addChild(input);
        layout(l -> l.column().size(width, HEIGHT));
    }

    /** 右键清空输入框（清空后同样上行给 setter）；确认后提交模式下不生效（空串会被当作放弃）。 */
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

    /**
     * 确认后提交（两端建界面时都要调用）：玩家改动输入框后只改本地草稿，不校验、不上行，也不接受服务端回显；
     * 按回车、点在输入框外或焦点移走时才把草稿上行，Esc 放弃草稿恢复服务端的值。
     * <ul>
     * <li>"改过"按文字判断：框里文字与最后一次写入的服务端文字不同才算，拖动、空粘贴、改了又改回都不算；
     * 没改过的输入框照常接收服务端回显，失焦也不发。LDLib 输入框自带的数值拖动、滚轮在此模式下关闭。</li>
     * <li>清空后提交视为放弃（数值校验会把空串当成下限）；输入框被禁用或所在面板被隐藏时草稿自动放弃。</li>
     * <li>服务端处理完（包括禁用时拒收）总会回显一次：草稿被夹到边界、数值没变时客户端也会被纠正。</li>
     * <li>点在输入框外（包括收起所在面板）即提交；关闭界面时未提交的草稿丢弃。右键清空在此模式下不生效。</li>
     * </ul>
     * {@code draftFilter} 限制玩家能输入的文字（整段判断，逐字输入的中间态也要放行）；服务端回显不受它限制。
     * <p>
     * 数值输入要用它：逐字上行时，中间态（如下限 10 时先输入的 "1"）会被校验夹到边界、写进机器、再回显覆盖正在输入的文字。
     */
    public TextField commitOnSubmit(Predicate<String> draftFilter) {
        input.commitOnSubmit = true;
        input.disableNumberDrag();
        if (input.isRemote()) input.setDraftFilter(draftFilter);
        return this;
    }

    /**
     * 客户端：立即提交草稿（没改过就什么也不发）并失去焦点。同一元素里不经点击的操作（如调节器的滚轮）先调它，保证"先提交草稿再操作"；
     * 点击类操作由 {@code UIClientEvents} 在分发前统一提交。不标 {@code @OnlyIn}：调用方可能以方法引用传入，两端都会创建。
     */
    public void commitDraft() {
        input.commitNow();
    }

    /**
     * 客户端：界面收到鼠标点击、分发给控件之前调用（{@code UIClientEvents}）。确认后提交的输入框有焦点时：
     * 所在面板已被隐藏就放弃草稿；点击最上层命中的不是它（点在别处，或盖在它上面的弹窗、滚出视口的部分）就先失焦提交。
     * LDLib 按子控件倒序分发点击、遇到处理了的就停，点击常常到不了输入框，它自己不会失焦。
     */
    @OnlyIn(Dist.CLIENT)
    public static void beforeGuiClick(ModularUIGuiContainer screen, double mouseX, double mouseY) {
        if (!(screen.lastFocus instanceof Input input) || !input.commitOnSubmit) return;
        for (Widget widget = input; widget != null; widget = widget.getParent()) {
            if (!widget.isVisible()) {
                input.cancel();
                return;
            }
        }
        if (screen.modularUI.mainGroup.getHoverElement(mouseX, mouseY) != input) input.commitNow();
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
        /// 确认后提交（见 {@link TextField#commitOnSubmit}），两端都设
        private boolean commitOnSubmit;
        /// 客户端：最后一次程序写入 EditBox 的文字（服务端文字、放弃草稿、已提交的草稿）。框里文字与它不同才算"玩家改过"
        private String appliedText = "";
        @Nullable
        private Predicate<String> draftFilter;

        private Input(int width, int height, Supplier<String> getter, Consumer<String> setter) {
            super(0, 0, width, height, getter, setter);
            setBordered(false);
            setTextColor(UITheme.FIELD_TEXT);
        }

        @Override
        public LayoutStyle getLayoutStyle() {
            return layoutStyle;
        }

        @OnlyIn(Dist.CLIENT)
        private void setDraftFilter(Predicate<String> filter) {
            draftFilter = filter;
            textField.setFilter(filter);
        }

        /// 确认后提交：草稿留在 EditBox 里，不校验、不上行（改没改过按文字判断，见 isEditing）
        @Override
        protected void onTextChanged(String newTextString) {
            if (!commitOnSubmit) super.onTextChanged(newTextString);
        }

        /** 客户端：玩家改过草稿（框里文字与最后一次写入的不同）。 */
        @OnlyIn(Dist.CLIENT)
        private boolean isEditing() {
            return !textField.getValue().equals(appliedText);
        }

        /// LDLib 按住拖动、滚轮会用 numberInstance 直接改值（绕过草稿），确认后提交模式下关掉：它为 null 时这两条路径都不走
        private void disableNumberDrag() {
            numberInstance = null;
        }

        @Override
        public TextFieldWidget setWheelDur(float wheelDur) {
            super.setWheelDur(wheelDur);
            if (commitOnSubmit) disableNumberDrag();
            return this;
        }

        @Override
        public TextFieldWidget setWheelDur(int digits, float wheelDur) {
            super.setWheelDur(digits, wheelDur);
            if (commitOnSubmit) disableNumberDrag();
            return this;
        }

        /// 确认后提交的客户端：服务端文字直接写进 EditBox，绕开草稿过滤（回显可能超出玩家能输入的写法，被拒就不更新了）
        @Override
        public TextFieldWidget setCurrentString(Object currentString) {
            if (!commitOnSubmit || !isRemote()) return super.setCurrentString(currentString);
            this.currentString = currentString.toString();
            apply(this.currentString);
            return this;
        }

        /** 客户端：程序写入文字，绕开草稿过滤，不算玩家编辑。 */
        @OnlyIn(Dist.CLIENT)
        private void apply(String text) {
            appliedText = text;
            if (textField.getValue().equals(text)) return;
            textField.setFilter(value -> true);
            textField.setValue(text);
            if (draftFilter != null) textField.setFilter(draftFilter);
        }

        /** 客户端：放弃草稿，恢复服务端的值并失去焦点。 */
        @OnlyIn(Dist.CLIENT)
        private void cancel() {
            apply(getCurrentString());
            if (isFocus()) setFocus(false);
        }

        /** 客户端：立即提交并失去焦点（失焦时提交）。 */
        @OnlyIn(Dist.CLIENT)
        private void commitNow() {
            if (isFocus()) setFocus(false);
            else commit();
        }

        /** 客户端：玩家改过才上行草稿；空串、禁用时改为放弃。 */
        @OnlyIn(Dist.CLIENT)
        private void commit() {
            if (!isEditing()) return;
            String draft = textField.getValue();
            // 清空视为放弃（数值校验会把空串当成下限）；禁用时服务端也会拒收
            if (draft.isBlank() || !isEnabled()) {
                apply(getCurrentString());
                return;
            }
            // 已提交：不再算"改过"，服务端回显到达时照常写入
            appliedText = draft;
            if (!draft.equals(getCurrentString())) {
                Consumer<FriendlyByteBuf> writer = buffer -> buffer.writeUtf(draft);
                if (!ClientActions.handleLocally(this, 1, writer)) writeClientAction(1, writer);
            }
        }

        /// setFocus(false) 不管原来有没有焦点都会回调这里，所以提交只看"改过没有"（commit 里判断）
        @Override
        @OnlyIn(Dist.CLIENT)
        public void onFocusChanged(@Nullable Widget lastFocus, Widget focus) {
            super.onFocusChanged(lastFocus, focus);
            if (commitOnSubmit && !isFocus()) commit();
        }

        /// 确认后提交：玩家改过草稿时，服务端文字只记下，不覆盖草稿（提交后以服务端回显为准）
        @Override
        @OnlyIn(Dist.CLIENT)
        public void readUpdateInfo(int id, FriendlyByteBuf buffer) {
            if (commitOnSubmit && id == 1 && isEditing()) {
                currentString = buffer.readUtf();
                return;
            }
            super.readUpdateInfo(id, buffer);
        }

        @Override
        @OnlyIn(Dist.CLIENT)
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!isEnabled()) return false;
            if (rightClickClear && !commitOnSubmit && button == 1 && isMouseOverElement(mouseX, mouseY)) {
                textField.setValue("");
                return true;
            }
            boolean handled = super.mouseClicked(mouseX, mouseY, button);
            // 无边框时 EditBox 比控件往下偏了几像素，点在那条带里 EditBox 会自己获得焦点而控件没有：以控件的焦点为准
            if (commitOnSubmit) textField.setFocused(isFocus());
            return handled;
        }

        /// 已获得焦点时变为不可编辑（如样板被取走），键盘输入也要拦下
        @Override
        @OnlyIn(Dist.CLIENT)
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (commitOnSubmit && isFocus() && isEnabled()) {
                // 回车确认（失焦时提交），Esc 放弃草稿；再按一次 Esc 才关界面
                if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                    setFocus(false);
                    return true;
                }
                if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                    cancel();
                    return true;
                }
            }
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
            if (isEnabled()) super.handleClientAction(id, buffer);
            // 确认后提交：处理完（包括禁用时拒收）总回显一次，草稿被夹到边界、数值没变或被拒时客户端也要被纠正
            if (commitOnSubmit && id == 1) echo();
        }

        /** 服务端：把当前值回显给客户端。 */
        private void echo() {
            if (textSupplier == null) return;
            String value = textSupplier.get();
            setCurrentString(value);
            writeUpdateInfo(1, out -> out.writeUtf(value));
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
            // 编辑中被禁用（如转子开始转动）：草稿作废，恢复服务端的值
            if (commitOnSubmit && !active && (isEditing() || isFocus())) cancel();
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
