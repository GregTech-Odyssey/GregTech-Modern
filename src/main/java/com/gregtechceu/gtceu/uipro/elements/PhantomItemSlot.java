package com.gregtechceu.gtceu.uipro.elements;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.uipro.ElementState;
import com.gregtechceu.gtceu.uipro.data.UIChannel;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;

import com.lowdragmc.lowdraglib.gui.ingredient.Target;
import com.lowdragmc.lowdraglib.gui.widget.PhantomSlotWidget;
import com.lowdragmc.lowdraglib.side.item.IItemTransfer;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 标准虚拟物品槽（设置用：只记录"是哪种物品"，不存放真实物品，如黑名单、过滤、标记），18 见方，底图 {@link UITheme#ITEM_SLOT}。
 * 拿着物品点击记录一份、右键/中键清空（具体规则由子类覆写 {@code slotClickPhantom}）。
 * <p>
 * 交互状态（见 {@link ElementState}）：<b>选中</b>（{@link #setSelected}）、<b>禁用</b>（{@link #disabled}，或上级禁用；
 * 点击、EMI 拖入都无效，服务端也拦），以及 LDLib2 同名的 {@link #xeiPhantom()}：开启后可从 EMI 拖入，槽里画下箭头标记
 * （{@link UIDraw#xeiPhantomMark}），玩家不拖也知道这格能拖；禁用时不接受拖入、也不画标记。
 */
public class PhantomItemSlot extends PhantomSlotWidget implements ElementState.Host<PhantomItemSlot>, UIChannel.Host {

    public static final int SIZE = UISizes.SLOT_SIZE;

    private final SlotState slotState = new SlotState(this);
    private boolean xeiPhantom;

    public static PhantomItemSlot of(IItemTransfer handler, int index) {
        return new PhantomItemSlot(handler, index);
    }

    protected PhantomItemSlot(IItemTransfer handler, int index) {
        super(handler, index, 0, 0);
        if (GTCEu.isClientThread()) setBackgroundTexture(slotState.background(UITheme.ITEM_SLOT, false, this::isXeiPhantom));
    }

    @Override
    public ElementState getState() {
        return slotState.state;
    }

    @Override
    public UIChannel getChannel() {
        return slotState.channel;
    }

    @Override
    public void initWidget() {
        super.initWidget();
        slotState.prime();
    }

    /** 可从 EMI 拖入（LDLib2 {@code ItemSlot.xeiPhantom()}）。 */
    public PhantomItemSlot xeiPhantom() {
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

    /// 服务端的两条入口（容器点击、客户端请求）都再判一次禁用：客户端可以伪造请求
    @Override
    public ItemStack slotClick(int dragType, ClickType clickTypeIn, Player player) {
        if (isDisabled()) return ItemStack.EMPTY;
        return super.slotClick(dragType, clickTypeIn, player);
    }

    @Override
    public void handleClientAction(int id, FriendlyByteBuf buffer) {
        if (slotState.handleClientAction(id, buffer)) return;
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

    /** 选中框在前景层画；空的禁用槽悬停时单独显示"禁止操作 + 原因"。 */
    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInForeground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        slotState.drawSelection(graphics);
        if (slotReference != null && slotReference.getItem().isEmpty() && ElementState.showDisabledTooltip(this, mouseX, mouseY, tooltipTexts)) return;
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
        slotState.detectAndSendChanges();
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
