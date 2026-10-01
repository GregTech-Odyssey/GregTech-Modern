package com.gregtechceu.gtceu.uipro.elements;

import com.gregtechceu.gtceu.uipro.ElementState;
import com.gregtechceu.gtceu.uipro.data.UIChannel;
import com.gregtechceu.gtceu.uipro.render.UIDraw;

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
    final UIChannel channel;
    final ElementState state;

    <T extends Widget & UIChannel.Host> SlotState(T owner) {
        this.owner = owner;
        this.channel = new UIChannel(owner);
        this.state = new ElementState(owner, owner);
    }

    boolean isDisabled() {
        return ElementState.isDisabled(owner);
    }

    /**
     * 槽底图：{@code base} 之上、内容之下按条件画"可从 EMI 拖入"标记（LDLib2 {@code xeiPhantom}）。
     * {@code darkSlot} 为深色槽（流体槽）时用浅色标记。
     */
    @OnlyIn(Dist.CLIENT)
    IGuiTexture background(IGuiTexture base, boolean darkSlot, BooleanSupplier xeiPhantom) {
        return new XeiPhantomBackground(base, darkSlot, xeiPhantom);
    }

    private record XeiPhantomBackground(IGuiTexture base, boolean darkSlot, BooleanSupplier xeiPhantom) implements IGuiTexture {

        @Override
        @OnlyIn(Dist.CLIENT)
        public void draw(GuiGraphics graphics, int mouseX, int mouseY, float x, float y, int width, int height) {
            base.draw(graphics, mouseX, mouseY, x, y, width, height);
            if (xeiPhantom.getAsBoolean()) UIDraw.xeiPhantomMark(graphics, (int) x, (int) y, width, height, darkSlot);
        }
    }

    /** 在槽的内容画完后调用：禁用时叠统一斜纹。 */
    @OnlyIn(Dist.CLIENT)
    void drawDisabled(GuiGraphics graphics) {
        if (isDisabled()) UIDraw.disabledHatch(graphics, owner.getPositionX(), owner.getPositionY(), owner.getSizeWidth(), owner.getSizeHeight());
    }

    /** 在前景层调用：选中时画统一选中框。 */
    @OnlyIn(Dist.CLIENT)
    void drawSelection(GuiGraphics graphics) {
        if (state.isSelected()) UIDraw.selectionFrame(graphics, owner.getPositionX(), owner.getPositionY(), owner.getSizeWidth(), owner.getSizeHeight());
    }

    void writeInitialData(FriendlyByteBuf buffer) {
        channel.writeInitialData(buffer);
    }

    void readInitialData(FriendlyByteBuf buffer) {
        channel.readInitialData(buffer);
    }

    void detectAndSendChanges() {
        channel.detectAndSendChanges();
    }

    boolean readUpdateInfo(int id, FriendlyByteBuf buffer) {
        return channel.readUpdateInfo(id, buffer);
    }

    boolean handleClientAction(int id, FriendlyByteBuf buffer) {
        return channel.handleClientAction(id, buffer);
    }

    void prime() {
        channel.prime();
    }

    /** 客户端每帧调用（纯客户端界面靠它取本端值）。 */
    void pollClient() {
        channel.pollClient();
    }
}
