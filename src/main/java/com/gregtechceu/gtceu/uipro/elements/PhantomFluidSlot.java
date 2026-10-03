package com.gregtechceu.gtceu.uipro.elements;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.gui.widget.PhantomFluidWidget;
import com.gregtechceu.gtceu.uipro.ElementState;
import com.gregtechceu.gtceu.uipro.data.UIChannel;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;

import com.lowdragmc.lowdraglib.gui.ingredient.Target;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 标准虚拟流体槽（设置用：只记录"是哪种流体"，如黑名单、过滤），18 见方，底图 {@link UITheme#FLUID_SLOT}，不显示数量。
 * 拿着装有流体的容器点击记录、空手点击清空；记录规则由 {@code setter} 决定。
 * <p>
 * 与 {@link PhantomItemSlot} 一样有<b>选中</b>、<b>禁用</b>（见 {@link ElementState}）和 {@link #xeiPhantom()}（可从 EMI 拖入，槽里画下箭头标记）。
 */
public class PhantomFluidSlot extends PhantomFluidWidget implements ElementState.Host<PhantomFluidSlot>, UIChannel.Host {

    public static final int SIZE = UISizes.SLOT_SIZE;

    private final SlotState slotState = new SlotState(this);
    private boolean xeiPhantom;

    public static PhantomFluidSlot of(@Nullable IFluidHandler handler, int tank, Supplier<FluidStack> getter, Consumer<FluidStack> setter) {
        return new PhantomFluidSlot(handler, tank, getter, setter);
    }

    protected PhantomFluidSlot(@Nullable IFluidHandler handler, int tank, Supplier<FluidStack> getter, Consumer<FluidStack> setter) {
        super(handler, tank, 0, 0, SIZE, SIZE, getter, setter);
        if (GTCEu.isClientThread()) setBackground(slotState.background(UITheme.FLUID_SLOT, true, this::isXeiPhantom));
        setShowAmount(false);
    }

    @Override
    public ElementState getState() {
        return slotState.state;
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

    @Override
    protected boolean acceptsServerInput() {
        return !isDisabled();
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
}
