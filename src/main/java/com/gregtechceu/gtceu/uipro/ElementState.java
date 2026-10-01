package com.gregtechceu.gtceu.uipro;

import com.gregtechceu.gtceu.uipro.data.SyncValue;
import com.gregtechceu.gtceu.uipro.data.UIChannel;
import com.gregtechceu.gtceu.uipro.data.UICodecs;
import com.gregtechceu.gtceu.uipro.render.UIStates;

import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import com.gto.datasynclib.util.StreamCodecs;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * 元素的交互状态，概念与命名照搬 LDLib2 {@code UIElement}：
 * <ul>
 * <li><b>禁用</b>（LDLib2 {@code disabled()} / {@code isActive()}）：与 LDLib2 一样向下继承——元素禁用时下面所有元素都视为禁用，
 * 一整行、整个区块设一次即可。可操作的控件叠统一斜纹，点击、键盘、EMI 拖入全部无效，悬停提示末尾先红字"禁止操作"、再灰字原因。</li>
 * <li><b>选中</b>（LDLib2 {@code Tab.setSelected} / {@code isSelected}）：统一选中框。</li>
 * </ul>
 * （槽的"可从 EMI 拖入"即 LDLib2 {@code ItemSlot.xeiPhantom()}，只有虚拟槽有，放在槽类里。）
 * <p>
 * 与 LDLib2 的不同，都是 LDLib1 上的必要补充：
 * <ul>
 * <li>LDLib1 自带的 {@code setActive(false)} 会停掉控件的同步（{@code detectAndSendChanges}），禁用后就收不到重新启用的数据，
 * 所以禁用单独实现，不用 LDLib1 的 active。</li>
 * <li>LDLib2 由客户端直接设布尔值；这里设的是条件：禁用条件在<b>服务端</b>判定、经元素自己的同步项下发（客户端改不了，
 * 服务端收到操作请求时也用同一判定再拦一次）；选中是"这个界面"的状态，在客户端判定、不同步，不要放进机器字段。</li>
 * </ul>
 * 带状态的元素实现 {@link Host}：{@link UIElement} 以及不继承它的标准控件（按钮、各种槽）。
 */
public final class ElementState {

    /// 禁用提示的第一行：先告诉玩家"斜纹 = 禁止操作"，再给具体原因
    private static final String DISABLED_TITLE = "gtceu.uipro.disabled";
    private static final int MAX_TOOLTIP_LINES = 64;
    private static final Component[] NO_TOOLTIPS = new Component[0];

    private final Widget owner;
    private final UIChannel.Host channel;
    @Nullable
    private BooleanSupplier selected;
    @Nullable
    private BooleanSupplier disabledCondition;
    @Nullable
    private SyncValue<Boolean> disabledValue;
    @Nullable
    private Component disabledReason;
    @Nullable
    private BooleanSupplier clientDisabled;

    public ElementState(Widget owner, UIChannel.Host channel) {
        this.owner = owner;
        this.channel = channel;
    }

    /** 选中条件（客户端每帧判定）；传 null 取消。 */
    public void setSelected(@Nullable BooleanSupplier selected) {
        this.selected = selected;
    }

    /** 本元素是否选中。 */
    public boolean isSelected() {
        return selected != null && selected.getAsBoolean();
    }

    /**
     * 禁用条件（服务端判定、下发）与原因翻译键（可为 null）。登记了同步项：建界面时两端都要调用，每个元素只能设一次。
     */
    public void setDisabled(BooleanSupplier serverCondition, @Nullable String reasonKey) {
        if (disabledValue != null) throw new IllegalStateException("disabled can only be set once per element");
        this.disabledCondition = serverCondition;
        this.disabledValue = channel.addSyncValue(SyncValue.ofBool(serverCondition));
        this.disabledReason = reasonKey == null ? null : Component.translatable(reasonKey);
    }

    public void setClientDisabled(@Nullable BooleanSupplier clientDisabled) {
        this.clientDisabled = clientDisabled;
    }

    public void setTooltips(Component... tooltips) {
        owner.setHoverTooltips(tooltips);
    }

    public void setTooltips(String... translationKeys) {
        var tooltips = new Component[translationKeys.length];
        for (int i = 0; i < translationKeys.length; i++) tooltips[i] = Component.translatable(translationKeys[i]);
        owner.setHoverTooltips(tooltips);
    }

    public void bindTooltip(Supplier<Component> tooltip) {
        channel.addSyncValue(SyncValue.ofComponent(tooltip, Component.empty()).onChanged(this::applyTooltip));
    }

    public void bindTooltips(Supplier<List<Component>> tooltips) {
        channel.addSyncValue(SyncValue.of(tooltips, UICodecs.list(StreamCodecs.COMPONENT_CODEC, MAX_TOOLTIP_LINES), Collections.emptyList())
                .onChanged(this::applyTooltips));
    }

    private void applyTooltip(Component tooltip) {
        owner.setHoverTooltips(tooltip.getString().isEmpty() ? NO_TOOLTIPS : new Component[] { tooltip });
    }

    private void applyTooltips(List<Component> tooltips) {
        owner.setHoverTooltips(tooltips.toArray(NO_TOOLTIPS));
    }

    private boolean isDisabledHere() {
        if (clientDisabled != null && owner.isRemote() && clientDisabled.getAsBoolean()) return true;
        if (disabledCondition == null || disabledValue == null) return false;
        return owner.getGui() != null && owner.isRemote() ? disabledValue.getValue() : disabledCondition.getAsBoolean();
    }

    // ==================== 禁用（向下继承） ====================

    /** 元素或它的任一上级被禁用。 */
    public static boolean isDisabled(Widget widget) {
        for (Widget w = widget; w != null; w = w.getParent()) {
            if (w instanceof Host host && host.getState().isDisabledHere()) return true;
        }
        return false;
    }

    /** 生效的禁用原因（从自身往上找第一个禁用且写了原因的），没有时为 null。 */
    @Nullable
    public static Component disabledReason(Widget widget) {
        for (Widget w = widget; w != null; w = w.getParent()) {
            if (w instanceof Host host && host.getState().isDisabledHere() && host.getState().disabledReason != null) {
                return host.getState().disabledReason;
            }
        }
        return null;
    }

    /** 禁用时在提示末尾加两行：先红字"禁止操作"（说明斜纹的含义），再灰字原因（没写原因时省略）；未禁用时原样返回。 */
    public static List<Component> withDisabledLines(Widget widget, List<Component> tooltips) {
        if (!isDisabled(widget)) return tooltips;
        var lines = new ArrayList<Component>(tooltips.size() + 2);
        lines.addAll(tooltips);
        appendDisabledLines(lines, disabledReason(widget));
        return lines;
    }

    /** 不走 {@link Host} 的控件（自己判定禁用、自己画斜纹）用：在提示末尾加"禁止操作 + 原因"两行，格式与标准控件一致。 */
    public static void appendDisabledLines(List<Component> tooltips, @Nullable Component reason) {
        tooltips.add(Component.translatable(DISABLED_TITLE).withStyle(ChatFormatting.RED));
        if (reason != null) tooltips.add(reason.copy().withStyle(ChatFormatting.GRAY));
    }

    /**
     * 禁用的控件被悬停时显示"原有提示 + 禁止操作 + 原因"，返回是否已显示（已显示则调用方不要再画自己的提示）。
     * 在控件的 {@code drawInForeground} 里调用。
     */
    @OnlyIn(Dist.CLIENT)
    public static boolean showDisabledTooltip(Widget widget, int mouseX, int mouseY, List<Component> tooltips) {
        var gui = widget.getGui();
        if (gui == null || !isHovered(widget, mouseX, mouseY)) return false;
        if (!isDisabled(widget)) return false;
        gui.getModularUIGui().setHoverTooltip(withDisabledLines(widget, tooltips), ItemStack.EMPTY, null, null);
        return true;
    }

    @OnlyIn(Dist.CLIENT)
    public static boolean isHovered(Widget widget, double mouseX, double mouseY) {
        return widget.isMouseOverElement(mouseX, mouseY) && widget.getHoverElement(mouseX, mouseY) == widget;
    }

    @OnlyIn(Dist.CLIENT)
    public static int resolve(Widget widget, int mouseX, int mouseY) {
        int states = UIStates.NONE;
        if (isDisabled(widget)) states |= UIStates.DISABLED;
        else if (isHovered(widget, mouseX, mouseY)) states |= UIStates.HOVERED;
        if (widget instanceof Host host && host.getState().isSelected()) states |= UIStates.SELECTED;
        return states;
    }

    /**
     * 带状态的元素，查询方法名同 LDLib2。实现类只需提供 {@link #getState()}，{@code T} 为实现类自身。
     */
    public interface Host<T extends Host<T>> {

        ElementState getState();

        @SuppressWarnings("unchecked")
        private T self() {
            return (T) this;
        }

        /** 自身或上级被禁用（LDLib2 {@code !isActive()}，含继承）。 */
        default boolean isDisabled() {
            return ElementState.isDisabled((Widget) this);
        }

        /** 本元素是否选中（LDLib2 {@code isSelected}）。 */
        default boolean isSelected() {
            return getState().isSelected();
        }

        default T setSelected(@Nullable BooleanSupplier selected) {
            getState().setSelected(selected);
            return self();
        }

        default T disabled(BooleanSupplier serverCondition, @Nullable String reasonKey) {
            getState().setDisabled(serverCondition, reasonKey);
            return self();
        }

        default T clientDisabled(BooleanSupplier disabled) {
            getState().setClientDisabled(disabled);
            return self();
        }

        default T tooltips(Component... tooltips) {
            getState().setTooltips(tooltips);
            return self();
        }

        default T tooltips(String... translationKeys) {
            getState().setTooltips(translationKeys);
            return self();
        }

        default T bindTooltip(Supplier<Component> tooltip) {
            getState().bindTooltip(tooltip);
            return self();
        }

        default T bindTooltips(Supplier<List<Component>> tooltips) {
            getState().bindTooltips(tooltips);
            return self();
        }
    }
}
