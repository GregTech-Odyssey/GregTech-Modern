package com.gregtechceu.gtceu.uipro.elements;

import com.gregtechceu.gtceu.api.gui.widget.SlotWidget;
import com.gregtechceu.gtceu.api.transfer.item.ICustomItemStackHandler;
import com.gregtechceu.gtceu.uipro.ElementState;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;

import com.lowdragmc.lowdraglib.jei.IngredientIO;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * 标准物品槽，18 见方（{@link UISizes#SLOT}），底图 {@link UITheme#ITEM_SLOT}，对应 LDLib2 {@code ItemSlot}。
 * 交互状态（见 {@link ElementState}）：
 * <ul>
 * <li><b>选中</b>（{@link #setSelected}）：统一选中框。客户端每帧判定，只应依赖本端界面状态（如弹出面板是否打开）。</li>
 * <li><b>禁用</b>（{@link #disabled}，或上级禁用）：统一斜纹，不压暗；放入、取出、悬停高亮全部停用，悬停提示先"禁止操作"再原因。
 * 条件在服务端判定、下发到客户端，客户端改不了。</li>
 * </ul>
 * 只作展示的槽（如由步进器设置的电路槽）用 {@link #display}。设置用、可从 EMI 拖入的虚拟槽用 {@link PhantomItemSlot}。
 */
public class ItemSlot extends SlotWidget implements ElementState.Host {

    public static final int SIZE = UISizes.SLOT;

    private final SlotState slotState = new SlotState(this);
    /// 调用方要求的悬停高亮（如 EMI 配方页把槽交给 EMI 画高亮时关掉）；实际还要未禁用
    private boolean hoverOverlay = true;

    public ItemSlot(ICustomItemStackHandler handler, int index, boolean canTakeItems, boolean canPutItems) {
        super(handler, index, 0, 0, canTakeItems, canPutItems);
        setBackgroundTexture(UITheme.ITEM_SLOT);
    }

    /**
     * 暂未绑定库存的槽（绑定前是空槽），用于先搭好控件树、之后再 {@code setHandlerSlot} 的模板，
     * 如配方界面：同一个布局在机器里绑定机器库存，在配方查看器里绑定配方内容。
     */
    public static ItemSlot unbound() {
        return new ItemSlot(ICustomItemStackHandler.EMPTY, 0, false, false);
    }

    /** 可取可放的物品槽。 */
    public static ItemSlot of(ICustomItemStackHandler handler, int index) {
        return new ItemSlot(handler, index, true, true);
    }

    /** 只作展示的槽：不可取放、始终禁用，EMI 里只作展示；{@code reasonKey} 说明原因（如"由步进器设置"），可为 null。 */
    public static ItemSlot display(ICustomItemStackHandler handler, int index, @Nullable String reasonKey) {
        var slot = new ItemSlot(handler, index, false, false);
        slot.setIngredientIO(IngredientIO.RENDER_ONLY);
        slot.disabled(() -> true, reasonKey);
        return slot;
    }

    @Override
    public ElementState getState() {
        return slotState.state;
    }

    /** 选中（LDLib2 {@code setSelected}），客户端每帧判定；传 null 取消。 */
    public ItemSlot setSelected(@Nullable BooleanSupplier selected) {
        slotState.state.setSelected(selected);
        return this;
    }

    /** 按服务端条件禁用（LDLib2 {@code disabled()}）；{@code reasonKey} 为原因翻译键（可为 null）。建界面时两端都要调用。 */
    public ItemSlot disabled(BooleanSupplier serverCondition, @Nullable String reasonKey) {
        slotState.state.setDisabled(serverCondition, reasonKey);
        return this;
    }

    @Override
    public ItemSlot setDrawHoverOverlay(boolean drawHoverOverlay) {
        this.hoverOverlay = drawHoverOverlay;
        super.setDrawHoverOverlay(drawHoverOverlay);
        return this;
    }

    @Override
    public boolean canPutStack(ItemStack stack) {
        return !isDisabled() && super.canPutStack(stack);
    }

    @Override
    public boolean canTakeStack(Player player) {
        return !isDisabled() && super.canTakeStack(player);
    }

    @Override
    public boolean canMergeSlot(ItemStack stack) {
        return !isDisabled() && super.canMergeSlot(stack);
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

    /** 选中框在前景层画；空的禁用槽悬停时单独显示"禁止操作 + 原因"（有物品时附在物品提示末尾）。 */
    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInForeground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        slotState.drawSelection(graphics);
        if (slotReference != null && slotReference.getItem().isEmpty() && ElementState.drawDisabledTooltip(this, mouseX, mouseY, tooltipTexts)) return;
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
