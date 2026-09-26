package com.gregtechceu.gtceu.uipro.elements;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.gui.widget.PhantomFluidWidget;
import com.gregtechceu.gtceu.uipro.ElementState;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;

import com.lowdragmc.lowdraglib.gui.ingredient.Target;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 标准虚拟流体槽（设置用：只记录"是哪种流体"，如黑名单、过滤），18 见方，底图 {@link UITheme#FLUID_SLOT}，不显示数量。
 * 拿着装有流体的容器点击记录、空手点击清空；记录规则由 {@code setter} 决定。
 * <p>
 * 与 {@link PhantomItemSlot} 一样有<b>选中</b>、<b>禁用</b>（见 {@link ElementState}）和 {@link #xeiPhantom()}（可从 EMI 拖入，槽里画下箭头标记）。
 */
public class PhantomFluidSlot extends PhantomFluidWidget implements ElementState.Host {

    public static final int SIZE = UISizes.SLOT;

    private final SlotState slotState = new SlotState(this);
    private boolean xeiPhantom;

    public PhantomFluidSlot(@Nullable IFluidHandler handler, int tank, Supplier<FluidStack> getter, Consumer<FluidStack> setter) {
        super(handler, tank, 0, 0, SIZE, SIZE, getter, setter);
        if (GTCEu.isClientThread()) setBackground(slotState.background(UITheme.FLUID_SLOT, true, this::isXeiPhantom));
        setShowAmount(false);
    }

    @Override
    public ElementState getState() {
        return slotState.state;
    }

    /** 选中（LDLib2 {@code setSelected}），客户端每帧判定；传 null 取消。 */
    public PhantomFluidSlot setSelected(@Nullable BooleanSupplier selected) {
        slotState.state.setSelected(selected);
        return this;
    }

    /** 按服务端条件禁用（LDLib2 {@code disabled()}）；{@code reasonKey} 为原因翻译键（可为 null）。建界面时两端都要调用。 */
    public PhantomFluidSlot disabled(BooleanSupplier serverCondition, @Nullable String reasonKey) {
        slotState.state.setDisabled(serverCondition, reasonKey);
        return this;
    }

    /** 可从 EMI 拖入（LDLib2 {@code FluidSlot.xeiPhantom()}）。 */
    public PhantomFluidSlot xeiPhantom() {
        this.xeiPhantom = true;
        return this;
    }

    /** 此刻能否从 EMI 拖入：开启了 {@link #xeiPhantom()} 且未禁用。 */
    public boolean isXeiPhantom() {
        return xeiPhantom && !isDisabled();
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isDisabled()) return false;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /// 服务端再判一次禁用：客户端的点击、拖入请求可以伪造
    @Override
    public void handleClientAction(int id, FriendlyByteBuf buffer) {
        if (isDisabled()) return;
        super.handleClientAction(id, buffer);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public List<Target> getPhantomTargets(Object ingredient) {
        return isXeiPhantom() ? super.getPhantomTargets(ingredient) : List.of();
    }

    @Override
    public List<Component> getFullTooltipTexts() {
        return ElementState.withDisabledLines(this, super.getFullTooltipTexts());
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInBackground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        drawHoverOverlay = !isDisabled();
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
