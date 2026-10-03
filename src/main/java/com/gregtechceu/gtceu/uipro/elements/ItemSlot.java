package com.gregtechceu.gtceu.uipro.elements;

import com.gregtechceu.gtceu.api.gui.widget.SlotWidget;
import com.gregtechceu.gtceu.api.transfer.forge.ForgeStackAdapter;
import com.gregtechceu.gtceu.api.transfer.forge.MenuItemAdapter;
import com.gregtechceu.gtceu.api.transfer.key.KeyInventory;
import com.gregtechceu.gtceu.api.transfer.key.StackInventory;
import com.gregtechceu.gtceu.uipro.ElementState;
import com.gregtechceu.gtceu.uipro.animation.UIClock;
import com.gregtechceu.gtceu.uipro.data.UIChannel;
import com.gregtechceu.gtceu.uipro.render.UIDraw;
import com.gregtechceu.gtceu.uipro.render.UILayers;
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
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.wrapper.EmptyHandler;

import appeng.api.stacks.AEItemKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 标准物品槽，18 见方（{@link UISizes#SLOT_SIZE}），底图 {@link UITheme#ITEM_SLOT}，对应 LDLib2 {@code ItemSlot}。
 * 交互状态（见 {@link ElementState}）：
 * <ul>
 * <li><b>选中</b>（{@link #setSelected}）：统一选中框。客户端每帧判定，只应依赖本端界面状态（如弹出面板是否打开）。</li>
 * <li><b>禁用</b>（{@link #disabled}，或上级禁用）：统一斜纹，不压暗；放入、取出、悬停高亮全部停用，悬停提示先"禁止操作"再原因。
 * 条件在服务端判定、下发到客户端，客户端改不了。</li>
 * </ul>
 * 只作展示的槽（如由步进器设置的电路槽）用 {@link #display}。设置用、可从 EMI 拖入的虚拟槽用 {@link PhantomItemSlot}。
 */
public class ItemSlot extends SlotWidget implements ElementState.Host<ItemSlot>, UIChannel.Host {

    public static final int SIZE = UISizes.SLOT_SIZE;

    private final SlotState slotState = new SlotState(this);
    /// 调用方要求的悬停高亮（如 EMI 配方页把槽交给 EMI 画高亮时关掉）；实际还要未禁用
    private boolean hoverOverlay = true;
    private ItemStack[] ghosts = NO_GHOSTS;
    private static final ItemStack[] NO_GHOSTS = new ItemStack[0];

    protected ItemSlot(IItemHandlerModifiable handler, int index, boolean canTakeItems, boolean canPutItems) {
        super(handler, index, 0, 0, canTakeItems, canPutItems);
        setBackgroundTexture(UITheme.ITEM_SLOT);
    }

    /**
     * 暂未绑定库存的槽（绑定前是空槽），用于先搭好控件树、之后再 {@code setHandlerSlot} 的模板，
     * 如配方界面：同一个布局在机器里绑定机器库存，在配方查看器里绑定配方内容。
     */
    public static ItemSlot unbound() {
        return new ItemSlot((IItemHandlerModifiable) EmptyHandler.INSTANCE, 0, false, false);
    }

    /** 可取可放的物品槽。 */
    public static ItemSlot of(IItemHandlerModifiable handler, int index) {
        return new ItemSlot(handler, index, true, true);
    }

    public static ItemSlot of(IItemHandlerModifiable handler, int index, boolean canTakeItems, boolean canPutItems) {
        return new ItemSlot(handler, index, canTakeItems, canPutItems);
    }

    public static ItemSlot of(KeyInventory<AEItemKey> inventory, int index) {
        return new ItemSlot(new MenuItemAdapter(inventory), index, true, true);
    }

    public static ItemSlot of(KeyInventory<AEItemKey> inventory, int index, boolean canTakeItems, boolean canPutItems) {
        return new ItemSlot(new MenuItemAdapter(inventory), index, canTakeItems, canPutItems);
    }

    public static ItemSlot of(StackInventory inventory, int index) {
        return new ItemSlot(new ForgeStackAdapter(inventory), index, true, true);
    }

    public static ItemSlot of(StackInventory inventory, int index, boolean canTakeItems, boolean canPutItems) {
        return new ItemSlot(new ForgeStackAdapter(inventory), index, canTakeItems, canPutItems);
    }

    /** 只作展示的槽：不可取放、始终禁用，EMI 里只作展示；{@code reasonKey} 说明原因（如"由步进器设置"），可为 null。 */
    public static ItemSlot display(KeyInventory<AEItemKey> inventory, int index, @Nullable String reasonKey) {
        return display(new MenuItemAdapter(inventory), index, reasonKey);
    }

    public static ItemSlot display(IItemHandlerModifiable handler, int index, @Nullable String reasonKey) {
        var slot = new ItemSlot(handler, index, false, false);
        slot.setIngredientIO(IngredientIO.RENDER_ONLY);
        slot.disabled(() -> true, reasonKey);
        return slot;
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

    public ItemSlot setGhosts(ItemStack... ghosts) {
        this.ghosts = ghosts;
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
        if (ghosts.length > 0 && slotReference != null && slotReference.getItem().isEmpty()) drawGhost(graphics);
        slotState.drawDisabled(graphics);
    }

    @OnlyIn(Dist.CLIENT)
    private void drawGhost(GuiGraphics graphics) {
        var ghost = ghosts[(int) (UIClock.millis() / 1000 % ghosts.length)];
        int x = getPositionX() + 1, y = getPositionY() + 1;
        graphics.renderItem(ghost, x, y);
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(0, 0, UILayers.ITEM_OVERLAY);
        UIDraw.fillRect(graphics, x, y, 16, 16, UITheme.SLOT_GHOST_VEIL);
        pose.popPose();
    }

    /** 选中框在前景层画；空的禁用槽悬停时单独显示"禁止操作 + 原因"（有物品时附在物品提示末尾）。 */
    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInForeground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        slotState.drawSelection(graphics);
        if (slotReference != null && slotReference.getItem().isEmpty() && ElementState.showDisabledTooltip(this, mouseX, mouseY, tooltipTexts)) return;
        super.drawInForeground(graphics, mouseX, mouseY, partialTicks);
    }

    @Override
    public void handleClientAction(int id, FriendlyByteBuf buffer) {
        if (!slotState.handleClientAction(id, buffer)) super.handleClientAction(id, buffer);
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
