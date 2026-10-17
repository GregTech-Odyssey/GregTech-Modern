package com.gregtechceu.gtceu.uipro.elements.ae;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.transfer.key.KeyCodecs;
import com.gregtechceu.gtceu.uipro.data.RPC;
import com.gregtechceu.gtceu.uipro.render.UIDraw;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;

import com.lowdragmc.lowdraglib.gui.ingredient.Target;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fluids.FluidStack;

import appeng.api.behaviors.ContainerItemStrategies;
import appeng.api.behaviors.EmptyingAction;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.core.localization.ButtonToolTips;
import appeng.helpers.InventoryAction;
import appeng.helpers.externalstorage.GenericStackInv;
import appeng.integration.modules.emi.EmiStackHelper;
import appeng.menu.slot.FakeSlot;
import appeng.util.ConfigInventory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * AE2 FakeSlot 的 UIPRO 版本：只修改配置，不消耗手持物品或容器内容。
 * 直接复用 FakeSlot 与 ConfigMenuInventory 的转换/增减逻辑；支持 CONFIG_TYPES 和 CONFIG_STACKS。
 * 左键设置（空手清空），右键增减；右键持有容器时设置容器内容，拖过多个槽时批量设置。
 * {@link #xeiPhantom()} 开启从 EMI 拖入，禁用状态在客户端和服务端均生效。
 */
public class PhantomAEKeySlot extends AEKeySlot {

    private final GenericStackInv inventory;
    private final FakeSlot fakeSlot;
    private final RPC<GenericStack> drop;
    private boolean xeiPhantom;
    private final ArrayList<PhantomAEKeySlot> dragSlots = new ArrayList<>(UISizes.SLOTS_PER_ROW);
    @Nullable
    private InventoryAction pendingClick;

    protected PhantomAEKeySlot(GenericStackInv inventory, int index) {
        super(() -> inventory.getKey(index), () -> inventory.getAmount(index));
        this.inventory = inventory;
        this.fakeSlot = new FakeSlot(inventory.createMenuWrapper(), index);
        setShowAmount(inventory.getMode() != GenericStackInv.Mode.CONFIG_TYPES);
        setInteraction(this::serverAction);
        drop = addRPC(KeyCodecs.GENERIC_STACK_STREAM_CODEC, (player, stack) -> {
            if (player != null && !player.isSpectator()) fakeSlot.set(GenericStack.wrapInItemStack(stack));
        }).validate(stack -> xeiPhantom && stack != null && stack.amount() >= 0 && inventory.isAllowed(stack.what()));
        listenUIClose();
    }

    public static PhantomAEKeySlot of(GenericStackInv inventory, int index) {
        if (inventory.getMode() == GenericStackInv.Mode.STORAGE) throw new IllegalArgumentException("Phantom AEKey slots require a configuration inventory");
        return new PhantomAEKeySlot(inventory, index);
    }

    public static PhantomAEKeySlot of(Supplier<AEKey> getter, Consumer<AEKey> setter, Predicate<AEKey> validator) {
        return of(new Bound(getter, setter, validator), 0);
    }

    public PhantomAEKeySlot xeiPhantom() {
        xeiPhantom = true;
        return this;
    }

    public boolean isXeiPhantom() {
        return xeiPhantom && !isDisabled();
    }

    @Override
    protected boolean acceptsAction(InventoryAction action) {
        return switch (action) {
            case PICKUP_OR_SET_DOWN, SPLIT_OR_PLACE_SINGLE, PLACE_SINGLE, EMPTY_ITEM -> true;
            default -> false;
        };
    }

    @Override
    @Nullable
    protected EmptyingAction getEmptyingAction(ItemStack carried) {
        var action = ContainerItemStrategies.getEmptyingAction(carried);
        return action != null && inventory.isAllowed(action.what()) ? action : null;
    }

    private void serverAction(Player player, InventoryAction action) {
        var hand = player.containerMenu.getCarried();
        switch (action) {
            case PICKUP_OR_SET_DOWN -> fakeSlot.increase(hand);
            case PLACE_SINGLE -> {
                if (!hand.isEmpty()) fakeSlot.increase(hand.copyWithCount(1));
            }
            case SPLIT_OR_PLACE_SINGLE -> {
                if (!fakeSlot.getItem().isEmpty()) fakeSlot.decrease(hand);
                else if (!hand.isEmpty()) fakeSlot.set(hand.copyWithCount(1));
            }
            case EMPTY_ITEM -> {
                var emptying = getEmptyingAction(hand);
                if (emptying != null) fakeSlot.set(GenericStack.wrapInItemStack(emptying.what(), emptying.maxAmount()));
            }
            default -> {}
        }
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    protected InventoryAction clickAction(int button) {
        if (button == 1 && gui != null && getEmptyingAction(gui.getModularUIContainer().getCarried()) != null) return InventoryAction.EMPTY_ITEM;
        return button == 1 ? InventoryAction.SPLIT_OR_PLACE_SINGLE : InventoryAction.PICKUP_OR_SET_DOWN;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button < 0 || button > 2 || !isMouseOverElement(mouseX, mouseY)) return false;
        if (isDisabled()) return true;
        pendingClick = clickAction(button);
        dragSlots.clear();
        dragSlots.add(this);
        capturePointer(button);
        return true;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (pendingClick == null || gui == null) return false;
        if (isDisabled()) return true;
        if (gui.getModularUIContainer().getCarried().isEmpty()) return true;
        var minecraft = Minecraft.getInstance();
        var window = minecraft.getWindow();
        double x = minecraft.mouseHandler.xpos() * window.getGuiScaledWidth() / window.getScreenWidth();
        double y = minecraft.mouseHandler.ypos() * window.getGuiScaledHeight() / window.getScreenHeight();
        var hovered = gui.mainGroup.getHoverElement(x, y);
        if (hovered instanceof PhantomAEKeySlot slot && !slot.isDisabled() && !dragSlots.contains(slot)) {
            var action = button == 0 ? InventoryAction.PICKUP_OR_SET_DOWN : InventoryAction.PLACE_SINGLE;
            if (dragSlots.size() == 1) sendAction(action);
            dragSlots.add(slot);
            slot.sendAction(action);
        }
        return true;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (pendingClick == null) return false;
        if (dragSlots.size() == 1) sendAction(pendingClick);
        pendingClick = null;
        dragSlots.clear();
        releasePointer();
        return true;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public boolean mouseWheelMove(double mouseX, double mouseY, double wheelDelta) {
        return false;
    }

    @Override
    protected void onUIClosed() {
        pendingClick = null;
        dragSlots.clear();
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    protected void drawSlotMark(GuiGraphics graphics, int x, int y, boolean darkSlot) {
        if (isXeiPhantom()) UIDraw.xeiPhantomMark(graphics, x, y, SIZE, SIZE, darkSlot);
    }

    @Override
    protected ButtonToolTips emptyingTooltipAction() {
        return ButtonToolTips.SetAction;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public List<Target> getPhantomTargets(Object ingredient) {
        var stack = phantomOf(ingredient);
        if (!isXeiPhantom() || stack == null || !inventory.isAllowed(stack.what())) return Collections.emptyList();
        return Collections.singletonList(new Target() {

            @Override
            public @NotNull Rect2i getArea() {
                return toRectangleBox();
            }

            @Override
            public void accept(@NotNull Object ingredient) {
                var stack = phantomOf(ingredient);
                if (isXeiPhantom() && stack != null && inventory.isAllowed(stack.what())) drop.send(stack);
            }
        });
    }

    @Nullable
    @OnlyIn(Dist.CLIENT)
    private static GenericStack phantomOf(Object ingredient) {
        if (ingredient instanceof GenericStack stack) return stack;
        if (ingredient instanceof AEKey key) return new GenericStack(key, 1);
        if (ingredient instanceof ItemStack stack) return GenericStack.fromItemStack(stack);
        if (ingredient instanceof FluidStack stack) return GenericStack.fromFluidStack(stack);
        return GTCEu.Mods.isEMILoaded() ? EmiCompat.phantomOf(ingredient) : null;
    }

    private static final class EmiCompat {

        @Nullable
        private static GenericStack phantomOf(Object ingredient) {
            if (ingredient instanceof EmiStack stack) {
                if (stack.isEmpty()) return null;
                if (stack.getKey() instanceof AEKey key) return new GenericStack(key, Math.max(1, stack.getAmount()));
                return EmiStackHelper.toGenericStack(stack);
            }
            if (ingredient instanceof EmiIngredient stacks && !stacks.getEmiStacks().isEmpty()) return phantomOf(stacks.getEmiStacks().get(0));
            return null;
        }
    }

    private static final class Bound extends ConfigInventory {

        private final Supplier<AEKey> getter;
        private final Consumer<AEKey> setter;
        @Nullable
        private GenericStack snapshot;

        private Bound(Supplier<AEKey> getter, Consumer<AEKey> setter, Predicate<AEKey> validator) {
            super(validator::test, Mode.CONFIG_TYPES, 1, null, false);
            this.getter = getter;
            this.setter = setter;
        }

        @Override
        public @Nullable AEKey getKey(int slot) {
            return getter.get();
        }

        @Override
        public long getAmount(int slot) {
            return 0;
        }

        @Override
        public @Nullable GenericStack getStack(int slot) {
            var key = getter.get();
            if (key == null) snapshot = null;
            else if (snapshot == null || snapshot.what() != key) snapshot = new GenericStack(key, 0);
            return snapshot;
        }

        @Override
        public void setStack(int slot, @Nullable GenericStack stack) {
            if (stack == null || isAllowed(stack.what())) setter.accept(stack == null ? null : stack.what());
        }
    }
}
