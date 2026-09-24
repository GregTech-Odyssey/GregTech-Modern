package com.gregtechceu.gtceu.uipro.elements;

import com.gregtechceu.gtceu.uipro.ElementState;
import com.gregtechceu.gtceu.uipro.data.SyncValueHost;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.function.BooleanSupplier;

/**
 * 标准槽（{@link ItemSlot}、{@link FluidSlot}、{@link PhantomItemSlot}、{@link PhantomFluidSlot}）共用的部分：
 * 交互状态 {@link ElementState}（选中、禁用）及其同步项，和两种状态的画法。槽各自继承不同的 LDLib/GTM 控件，所以放在这里由槽转调。
 */
final class SlotState {

    private final Widget owner;
    private final SyncValueHost syncValues;
    final ElementState state;

    SlotState(Widget owner) {
        this.owner = owner;
        this.syncValues = new SyncValueHost(owner);
        this.state = new ElementState(owner, syncValues::add);
    }

    boolean isDisabled() {
        return ElementState.isDisabled(owner);
    }

    /**
     * 槽底图：{@code base} 之上、内容之下按条件画"可从 EMI 拖入"标记（LDLib2 {@code xeiPhantom}）。
     * {@code darkSlot} 为深色槽（流体槽）时用浅色标记。
     */
    IGuiTexture background(IGuiTexture base, boolean darkSlot, BooleanSupplier xeiPhantom) {
        return (graphics, mouseX, mouseY, x, y, width, height) -> {
            base.draw(graphics, mouseX, mouseY, x, y, width, height);
            if (xeiPhantom.getAsBoolean()) UITheme.drawXeiPhantom(graphics, (int) x, (int) y, width, height, darkSlot);
        };
    }

    /** 在槽的内容画完后调用：禁用时叠统一斜纹。 */
    @OnlyIn(Dist.CLIENT)
    void drawDisabled(GuiGraphics graphics) {
        if (isDisabled()) UITheme.drawDisabled(graphics, owner.getPositionX(), owner.getPositionY(), owner.getSizeWidth(), owner.getSizeHeight());
    }

    /** 在前景层调用：选中时画统一选中框。 */
    @OnlyIn(Dist.CLIENT)
    void drawSelection(GuiGraphics graphics) {
        if (state.isSelected()) UITheme.drawSelection(graphics, owner.getPositionX(), owner.getPositionY(), owner.getSizeWidth(), owner.getSizeHeight());
    }

    void writeInitialData(FriendlyByteBuf buffer) {
        syncValues.writeInitialData(buffer);
    }

    void readInitialData(FriendlyByteBuf buffer) {
        syncValues.readInitialData(buffer);
    }

    void detectAndSendChanges(SyncValueHost.UpdateSender sender) {
        syncValues.detectAndSendChanges(sender);
    }

    boolean readUpdateInfo(int id, FriendlyByteBuf buffer) {
        return syncValues.readUpdateInfo(id, buffer);
    }
}
