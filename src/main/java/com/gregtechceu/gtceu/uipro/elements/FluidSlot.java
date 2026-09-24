package com.gregtechceu.gtceu.uipro.elements;

import com.gregtechceu.gtceu.api.gui.widget.TankWidget;
import com.gregtechceu.gtceu.uipro.ElementState;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fluids.capability.IFluidHandler;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * 标准流体槽，18 见方（{@link UISizes#SLOT}），底图 {@link UITheme#FLUID_SLOT}（比物品槽深一档，二者一眼能分开），对应 LDLib2 {@code FluidSlot}。
 * 与 {@link ItemSlot} 一样有<b>选中</b>（{@link #setSelected}）和<b>禁用</b>（{@link #disabled}，或上级禁用；
 * 禁用时拿容器点击装入/取出、悬停高亮都停用）两个状态（见 {@link ElementState}）。
 * 设置用、可从 EMI 拖入的虚拟流体槽用 {@link PhantomFluidSlot}。
 */
public class FluidSlot extends TankWidget implements ElementState.Host {

    public static final int SIZE = UISizes.SLOT;
    /// TankWidget 的"拿容器点击"客户端请求 ID
    private static final int CLICK_CONTAINER_ID = 1;

    private final SlotState slotState = new SlotState(this);
    /// 调用方要求的悬停高亮（如 EMI 配方页把槽交给 EMI 画高亮时关掉）；实际还要未禁用
    private boolean hoverOverlay = true;

    public FluidSlot(@Nullable IFluidHandler handler, int tank, boolean allowClickFilled, boolean allowClickDrained) {
        super(handler, tank, 0, 0, SIZE, SIZE, allowClickFilled, allowClickDrained);
        setBackground(UITheme.FLUID_SLOT);
    }

    /** 暂未绑定储罐的槽，之后再 {@code setFluidTank}，用法同 {@link ItemSlot#unbound()}。 */
    public static FluidSlot unbound() {
        return new FluidSlot(null, 0, false, false);
    }

    /** 可拿容器装入、取出的流体槽（{@code handler} 的第 0 号储罐）。 */
    public static FluidSlot of(IFluidHandler handler) {
        return new FluidSlot(handler, 0, true, true);
    }

    @Override
    public ElementState getState() {
        return slotState.state;
    }

    /** 选中（LDLib2 {@code setSelected}），客户端每帧判定；传 null 取消。 */
    public FluidSlot setSelected(@Nullable BooleanSupplier selected) {
        slotState.state.setSelected(selected);
        return this;
    }

    /** 按服务端条件禁用（LDLib2 {@code disabled()}）；{@code reasonKey} 为原因翻译键（可为 null）。建界面时两端都要调用。 */
    public FluidSlot disabled(BooleanSupplier serverCondition, @Nullable String reasonKey) {
        slotState.state.setDisabled(serverCondition, reasonKey);
        return this;
    }

    @Override
    public FluidSlot setDrawHoverOverlay(boolean drawHoverOverlay) {
        this.hoverOverlay = drawHoverOverlay;
        super.setDrawHoverOverlay(drawHoverOverlay);
        return this;
    }

    @Override
    public void handleClientAction(int id, FriendlyByteBuf buffer) {
        // 服务端再判一次：客户端的点击请求可以伪造
        if (id == CLICK_CONTAINER_ID && isDisabled()) return;
        super.handleClientAction(id, buffer);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isDisabled()) return false;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public List<Component> getFullTooltipTexts() {
        return ElementState.withDisabledLines(this, super.getFullTooltipTexts());
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInBackground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        drawHoverOverlay = hoverOverlay && !isDisabled();
        super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
        slotState.drawDisabled(graphics);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInForeground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        slotState.drawSelection(graphics);
        super.drawInForeground(graphics, mouseX, mouseY, partialTicks);
    }

    @Override
    public void writeInitialData(FriendlyByteBuf buffer) {
        super.writeInitialData(buffer);
        slotState.writeInitialData(buffer);
    }

    @Override
    public void readInitialData(FriendlyByteBuf buffer) {
        super.readInitialData(buffer);
        slotState.readInitialData(buffer);
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        slotState.detectAndSendChanges(this::writeUpdateInfo);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void readUpdateInfo(int id, FriendlyByteBuf buffer) {
        if (!slotState.readUpdateInfo(id, buffer)) super.readUpdateInfo(id, buffer);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void updateScreen() {
        super.updateScreen();
        slotState.pollClient();
    }
}
