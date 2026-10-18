package com.gregtechceu.gtceu.uipro.elements.ae;

import com.gregtechceu.gtceu.api.transfer.key.IKeyHandler;
import com.gregtechceu.gtceu.uipro.ElementState;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.UIIngredient;
import com.gregtechceu.gtceu.uipro.data.RPC;
import com.gregtechceu.gtceu.uipro.data.SyncValue;
import com.gregtechceu.gtceu.uipro.data.UICodecs;
import com.gregtechceu.gtceu.uipro.render.UIDraw;
import com.gregtechceu.gtceu.uipro.render.UIText;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;
import com.gregtechceu.gtceu.utils.FormattingUtil;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import appeng.api.behaviors.ContainerItemStrategies;
import appeng.api.behaviors.EmptyingAction;
import appeng.api.behaviors.GenericInternalInventory;
import appeng.api.client.AEKeyRendering;
import appeng.api.config.Actionable;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AmountFormat;
import appeng.core.localization.ButtonToolTips;
import appeng.core.localization.Tooltips;
import appeng.helpers.InventoryAction;
import appeng.helpers.externalstorage.GenericStackInv;
import appeng.util.ReadableNumberConverter;
import com.gto.datasynclib.datastream.codec.StreamCodec;
import com.mojang.blaze3d.platform.InputConstants;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * 通用 AEKey 槽，使用 AE2 注册的渲染器，支持物品、流体和扩展 key 类型。
 * {@link #of(GenericInternalInventory, int)} / {@link #of(IKeyHandler, int)} 绑定真实库存；
 * {@link #display(Supplier, LongSupplier)} 只展示。选中、禁用与其他 UIPRO 槽一致。
 * 流体数量以 mB 为单位：槽内缩写显示，悬停提示显示精确数量与 mB 单位。
 * <p>
 * 操作沿用 AE2 的资源槽：左键取放一组，右键取半组/放一个，Shift 左键移入背包、Shift 右键取一个，
 * 中键创造复制，滚轮上滚放一个/下滚取一个，空格点击连续移入背包。非物品 key 左键填充手持容器、右键倒入库存。
 * 自动合成、跨槽存入和从网络领取空桶属于终端功能，不由单槽提供。
 */
public class AEKeySlot extends UIElement {

    public static final int SIZE = UISizes.SLOT_SIZE;
    private static final StreamCodec<FriendlyByteBuf, AEKey> OPTIONAL_KEY = StreamCodec.of(AEKey::writeOptionalKey, AEKey::readOptionalKey);

    private final SyncValue<AEKey> key;
    private final SyncValue<Long> amount;
    @Nullable
    private Access access;
    @Nullable
    private RPC<InventoryAction> interaction;
    private boolean showAmount = true;
    private boolean hoverOverlay = true;
    @Nullable
    private AEKey formattedKey;
    private long formattedAmount = Long.MIN_VALUE;
    private String amountText = "";

    protected AEKeySlot(Supplier<AEKey> key, LongSupplier amount) {
        layout(l -> l.size(SIZE, SIZE));
        this.key = addSyncValue(SyncValue.of(key, OPTIONAL_KEY, null));
        this.amount = addSyncValue(SyncValue.ofLong(amount::getAsLong, 0));
    }

    public static AEKeySlot of(GenericInternalInventory inventory, int index) {
        return of(inventory, index, true, true);
    }

    public static AEKeySlot of(GenericInternalInventory inventory, int index, boolean canTake, boolean canPut) {
        if (inventory instanceof GenericStackInv generic && generic.getMode() != GenericStackInv.Mode.STORAGE) throw new IllegalArgumentException("Use PhantomAEKeySlot for configuration inventories");
        var slot = new AEKeySlot(() -> inventory.getKey(index), () -> inventory.getAmount(index));
        slot.access = new GenericAccess(inventory, index, canTake, canPut);
        slot.setInteraction(slot::serverAction);
        return slot;
    }

    public static <K extends AEKey> AEKeySlot of(IKeyHandler<K> inventory, int index) {
        return of(inventory, index, true, true);
    }

    public static <K extends AEKey> AEKeySlot of(IKeyHandler<K> inventory, int index, boolean canTake, boolean canPut) {
        var slot = new AEKeySlot(() -> inventory.keyAt(index), () -> inventory.amountAt(index));
        slot.access = new KeyAccess<>(inventory, index, canTake, canPut);
        slot.setInteraction(slot::serverAction);
        return slot;
    }

    public static AEKeySlot display(Supplier<AEKey> key, LongSupplier amount) {
        return new AEKeySlot(key, amount);
    }

    public static AEKeySlot display(Supplier<AEKey> key) {
        return display(key, () -> 1).setShowAmount(false);
    }

    public @Nullable AEKey getKey() {
        return key.getValue();
    }

    public long getAmount() {
        return amount.getValue();
    }

    public AEKeySlot setShowAmount(boolean showAmount) {
        this.showAmount = showAmount;
        return this;
    }

    public AEKeySlot setDrawHoverOverlay(boolean hoverOverlay) {
        this.hoverOverlay = hoverOverlay;
        return this;
    }

    protected final void setInteraction(BiConsumer<Player, InventoryAction> handler) {
        interaction = addRPC(UICodecs.enumOf(InventoryAction.class), (player, action) -> {
            if (player != null && !player.isSpectator()) handler.accept(player, action);
        }).validate(this::acceptsAction);
    }

    protected boolean acceptsAction(InventoryAction action) {
        return switch (action) {
            case PICKUP_OR_SET_DOWN, SPLIT_OR_PLACE_SINGLE, SHIFT_CLICK, PICKUP_SINGLE, CREATIVE_DUPLICATE, MOVE_REGION, ROLL_UP, ROLL_DOWN, FILL_ITEM, EMPTY_ITEM -> true;
            default -> false;
        };
    }

    protected final void sendAction(InventoryAction action) {
        if (interaction != null && !isDisabled()) interaction.send(action);
    }

    @OnlyIn(Dist.CLIENT)
    protected InventoryAction clickAction(int button) {
        var minecraft = Minecraft.getInstance();
        if (getKey() == null && button != 2) return button == 1 ? InventoryAction.SPLIT_OR_PLACE_SINGLE : InventoryAction.PICKUP_OR_SET_DOWN;
        if (button == 1 && gui != null && getEmptyingAction(gui.getModularUIContainer().getCarried()) != null) return InventoryAction.EMPTY_ITEM;
        if (InputConstants.isKeyDown(minecraft.getWindow().getWindow(), GLFW.GLFW_KEY_SPACE)) return InventoryAction.MOVE_REGION;
        if (button == 2) return InventoryAction.CREATIVE_DUPLICATE;
        if (Screen.hasShiftDown()) return button == 1 ? InventoryAction.PICKUP_SINGLE : InventoryAction.SHIFT_CLICK;
        return button == 1 ? InventoryAction.SPLIT_OR_PLACE_SINGLE : InventoryAction.PICKUP_OR_SET_DOWN;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (interaction == null || button < 0 || button > 2 || !isMouseOverElement(mouseX, mouseY)) return super.mouseClicked(mouseX, mouseY, button);
        if (!isDisabled()) sendAction(clickAction(button));
        return true;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public boolean mouseWheelMove(double mouseX, double mouseY, double wheelDelta) {
        if (interaction == null || wheelDelta == 0 || !isMouseOverElement(mouseX, mouseY)) return super.mouseWheelMove(mouseX, mouseY, wheelDelta);
        if (!isDisabled()) {
            for (int i = 0, times = (int) Math.abs(wheelDelta); i < times; i++) sendAction(wheelDelta > 0 ? InventoryAction.ROLL_DOWN : InventoryAction.ROLL_UP);
        }
        return true;
    }

    @Nullable
    protected EmptyingAction getEmptyingAction(ItemStack carried) {
        var action = ContainerItemStrategies.getEmptyingAction(carried);
        return action != null && access != null && access.isAllowed(action.what()) ? action : null;
    }

    private void serverAction(Player player, InventoryAction action) {
        var menu = player.containerMenu;
        var carried = menu.getCarried();
        var clickedKey = access.key();
        // AE2 优先把右键容器解释为倒出，即使点击的是空槽或另一种资源。
        if (action == InventoryAction.SPLIT_OR_PLACE_SINGLE && getEmptyingAction(carried) != null) action = InventoryAction.EMPTY_ITEM;
        if (action == InventoryAction.PICKUP_OR_SET_DOWN && ContainerItemStrategies.isKeySupported(clickedKey)) action = InventoryAction.FILL_ITEM;
        if (action == InventoryAction.EMPTY_ITEM) {
            emptyContainer(player);
            return;
        }
        if (action == InventoryAction.FILL_ITEM || action == InventoryAction.SHIFT_CLICK && ContainerItemStrategies.isKeySupported(clickedKey)) {
            fillContainer(player, clickedKey, action == InventoryAction.SHIFT_CLICK);
            return;
        }
        if (action == InventoryAction.ROLL_DOWN ||
                (action == InventoryAction.PICKUP_OR_SET_DOWN || action == InventoryAction.SPLIT_OR_PLACE_SINGLE) && !carried.isEmpty()) {
            var item = AEItemKey.of(carried);
            if (item != null) carried.shrink((int) access.insert(item, action == InventoryAction.PICKUP_OR_SET_DOWN ? carried.getCount() : 1, false));
            return;
        }
        if (!(clickedKey instanceof AEItemKey item)) return;
        switch (action) {
            case PICKUP_OR_SET_DOWN -> menu.setCarried(item.toStack((int) access.extract(item, item.getMaxStackSize(), false)));
            case SPLIT_OR_PLACE_SINGLE -> {
                long available = access.extract(item, item.getMaxStackSize(), true);
                menu.setCarried(item.toStack((int) access.extract(item, (available + 1) / 2, false)));
            }
            case PICKUP_SINGLE, ROLL_UP -> {
                if (!carried.isEmpty() && (!item.matches(carried) || carried.getCount() >= carried.getMaxStackSize())) return;
                int extracted = (int) access.extract(item, 1, false);
                if (extracted > 0) {
                    if (carried.isEmpty()) menu.setCarried(item.toStack(extracted));
                    else carried.grow(extracted);
                }
            }
            case SHIFT_CLICK -> moveToPlayer(player, item);
            case MOVE_REGION -> {
                for (int i = 0; i < player.getInventory().items.size(); i++) {
                    if (!moveToPlayer(player, item)) break;
                }
            }
            case CREATIVE_DUPLICATE -> {
                if (player.getAbilities().instabuild) menu.setCarried(item.toStack(item.getMaxStackSize()));
            }
            default -> {}
        }
    }

    private boolean moveToPlayer(Player player, AEItemKey key) {
        var inventory = player.getInventory();
        int slot = inventory.getSlotWithRemainingSpace(key.getReadOnlyStack());
        if (slot < 0) slot = inventory.getFreeSlot();
        if (slot < 0) return false;
        var existing = inventory.getItem(slot);
        int space = key.getMaxStackSize() - existing.getCount();
        if (space <= 0) return false;
        int extracted = (int) access.extract(key, space, false);
        if (extracted <= 0) return false;
        if (existing.isEmpty()) inventory.setItem(slot, key.toStack(extracted));
        else existing.grow(extracted);
        inventory.setChanged();
        return true;
    }

    private void emptyContainer(Player player) {
        var context = ContainerItemStrategies.findCarriedContext(null, player, player.containerMenu);
        if (context == null) return;
        var content = context.getExtractableContent();
        if (content == null) return;
        long accepted = access.insert(content.what(), content.amount(), true);
        if (accepted <= 0) return;
        long extracted = context.extract(content.what(), accepted, Actionable.MODULATE);
        if (extracted <= 0) return;
        access.insert(content.what(), extracted, false);
        context.playEmptySound(player, content.what());
    }

    private void fillContainer(Player player, @Nullable AEKey key, boolean moveToPlayer) {
        if (key == null) return;
        var menu = player.containerMenu;
        var context = ContainerItemStrategies.findCarriedContextForKey(key, player, menu);
        if (context == null) return;
        long available = access.extract(key, Long.MAX_VALUE, true);
        if (available <= 0) return;
        long accepted = context.insert(key, available, Actionable.SIMULATE);
        if (accepted <= 0) return;
        var before = menu.getCarried().getItem();
        long extracted = access.extract(key, accepted, false);
        if (extracted <= 0) return;
        context.insert(key, extracted, Actionable.MODULATE);
        context.playFillSound(player, key);
        if (moveToPlayer && !menu.getCarried().is(before) && player.addItem(menu.getCarried())) menu.setCarried(ItemStack.EMPTY);
    }

    @Override
    public @Nullable Object getXEIIngredientOverMouse(double mouseX, double mouseY) {
        return isMouseOverElement(mouseX, mouseY) ? UIIngredient.of(getKey(), Math.max(1, getAmount())) : null;
    }

    @Override
    public boolean hasOwnTooltip(int mouseX, int mouseY) {
        return getKey() != null || isDisabled() || super.hasOwnTooltip(mouseX, mouseY);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int x = getPositionX(), y = getPositionY();
        var key = getKey();
        (key instanceof AEFluidKey ? UITheme.FLUID_SLOT : UITheme.ITEM_SLOT).draw(graphics, mouseX, mouseY, x, y, SIZE, SIZE);
        drawSlotMark(graphics, x, y, key instanceof AEFluidKey);
        if (key != null) {
            AEKeyRendering.drawInGui(Minecraft.getInstance(), graphics, x + 1, y + 1, key);
            if (showAmount) {
                long amount = getAmount();
                if (formattedKey != key || formattedAmount != amount) {
                    formattedKey = key;
                    formattedAmount = amount;
                    amountText = key instanceof AEFluidKey ? ReadableNumberConverter.format(amount, 3) : key.formatAmount(amount, AmountFormat.SLOT_LARGE_FONT);
                }
                UIText.drawItemCount(graphics, amountText, x + 1, y + 1);
            }
        }
        if (isDisabled()) UIDraw.disabledHatch(graphics, x, y, SIZE, SIZE);
        else if (hoverOverlay && isMouseOverElement(mouseX, mouseY)) UIDraw.hoverOverlay(graphics, x, y, SIZE, SIZE);
        super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
    }

    @OnlyIn(Dist.CLIENT)
    protected void drawSlotMark(GuiGraphics graphics, int x, int y, boolean darkSlot) {}

    @Override
    @OnlyIn(Dist.CLIENT)
    protected void drawTooltipTexts(int mouseX, int mouseY) {
        // 资源提示、调用方提示和禁用原因一起绘制。
    }

    protected ButtonToolTips emptyingTooltipAction() {
        return ButtonToolTips.StoreAction;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInForeground(graphics, mouseX, mouseY, partialTicks);
        if (gui == null || gui.getModularUIGui() == null || !ElementState.isHovered(this, mouseX, mouseY)) return;
        var carried = gui.getModularUIContainer().getCarried();
        var action = getEmptyingAction(carried);
        var key = getKey();
        List<Component> lines;
        if (action != null) lines = new ArrayList<>(Tooltips.getEmptyingTooltip(emptyingTooltipAction(), carried, action));
        else if (!carried.isEmpty()) lines = new ArrayList<>();
        else if (key != null) {
            lines = AEKeyRendering.getTooltip(key);
            if (showAmount) {
                if (key instanceof AEFluidKey) {
                    var amountText = FormattingUtil.formatNumbers(getAmount()) + " mB";
                    lines.add(ButtonToolTips.StoredAmount.text(amountText).withStyle(Tooltips.MUTED_COLOR));
                } else if (Tooltips.shouldShowAmountTooltip(key, getAmount())) lines.add(Tooltips.getAmountTooltip(ButtonToolTips.StoredAmount, key, getAmount()));
            }
        } else lines = new ArrayList<>();
        lines.addAll(tooltipTexts);
        if (ElementState.showDisabledTooltip(this, mouseX, mouseY, lines)) return;
        if (!lines.isEmpty()) gui.getModularUIGui().setHoverTooltip(lines, key instanceof AEItemKey item ? item.getReadOnlyStack() : ItemStack.EMPTY, null, null);
    }

    private interface Access {

        @Nullable
        AEKey key();

        boolean isAllowed(AEKey key);

        long insert(AEKey key, long amount, boolean simulate);

        long extract(AEKey key, long amount, boolean simulate);
    }

    private record GenericAccess(GenericInternalInventory inventory, int index, boolean canTake, boolean canPut) implements Access {

        public @Nullable AEKey key() {
            return inventory.getKey(index);
        }

        public boolean isAllowed(AEKey key) {
            return canPut && inventory.canInsert() && inventory.isAllowed(key);
        }

        public long insert(AEKey key, long amount, boolean simulate) {
            return canPut && amount > 0 ? inventory.insert(index, key, amount, simulate ? Actionable.SIMULATE : Actionable.MODULATE) : 0;
        }

        public long extract(AEKey key, long amount, boolean simulate) {
            return canTake && amount > 0 ? inventory.extract(index, key, amount, simulate ? Actionable.SIMULATE : Actionable.MODULATE) : 0;
        }
    }

    private record KeyAccess<K extends AEKey>(IKeyHandler<K> inventory, int index, boolean canTake, boolean canPut) implements Access {

        public @Nullable AEKey key() {
            return inventory.keyAt(index);
        }

        public boolean isAllowed(AEKey key) {
            return canPut && key.getType() == inventory.keyType();
        }

        @SuppressWarnings("unchecked")
        public long insert(AEKey key, long amount, boolean simulate) {
            return isAllowed(key) && amount > 0 ? inventory.insert(index, (K) key, amount, simulate) : 0;
        }

        @SuppressWarnings("unchecked")
        public long extract(AEKey key, long amount, boolean simulate) {
            return canTake && key.getType() == inventory.keyType() && amount > 0 ? inventory.extract(index, (K) key, amount, simulate) : 0;
        }
    }
}
