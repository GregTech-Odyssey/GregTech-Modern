package com.gregtechceu.gtceu.api.gui.widget;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.transfer.forge.ForgeStackAdapter;
import com.gregtechceu.gtceu.api.transfer.forge.MenuItemAdapter;
import com.gregtechceu.gtceu.api.transfer.key.KeyCodecs;
import com.gregtechceu.gtceu.api.transfer.key.KeyInventory;
import com.gregtechceu.gtceu.api.transfer.key.Keys;
import com.gregtechceu.gtceu.api.transfer.key.StackInventory;
import com.gregtechceu.gtceu.uipro.data.RPC;
import com.gregtechceu.gtceu.uipro.data.UIChannel;
import com.gregtechceu.gtceu.utils.GTUtil;

import com.lowdragmc.lowdraglib.gui.editor.annotation.ConfigSetter;
import com.lowdragmc.lowdraglib.gui.editor.annotation.Configurable;
import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.editor.annotation.NumberRange;
import com.lowdragmc.lowdraglib.gui.ingredient.IGhostIngredientTarget;
import com.lowdragmc.lowdraglib.gui.ingredient.Target;
import com.lowdragmc.lowdraglib.side.item.IItemTransfer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Unit;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.items.IItemHandlerModifiable;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import com.google.common.collect.Lists;
import com.gto.datasynclib.datastream.codec.ByteStreamCodec;
import com.gto.datasynclib.datastream.codec.StreamCodec;
import com.mojang.blaze3d.platform.InputConstants;
import dev.emi.emi.api.stack.EmiStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

import javax.annotation.Nonnull;

@LDLRegister(name = "gtm_phantom_item_slot", group = "widget.gtm_container", priority = 50)
public class PhantomSlotWidget extends SlotWidget implements IGhostIngredientTarget, UIChannel.Host {

    private static final StreamCodec<FriendlyByteBuf, Drop> DROP_CODEC = StreamCodec.composite(
            KeyCodecs.GENERIC_STACK_STREAM_CODEC, Drop::stack,
            ByteStreamCodec.BOOLEAN_CODEC, Drop::shift,
            Drop::new);

    private boolean clearSlotOnRightClick;

    @Configurable
    @NumberRange(range = { 0, 64 })
    private int maxStackSize = 64;

    private Predicate<ItemStack> validator = GTUtil.FAVORABLE;
    @Nullable
    private IItemHandlerModifiable boundHandler;
    private final UIChannel channel = new UIChannel(this);
    private final RPC<Drop> dropRequest = channel.addRPC(DROP_CODEC, this::serverDrop)
            .validate(drop -> drop.stack() != null && drop.stack().what() instanceof AEItemKey && drop.stack().amount() > 0);
    private final RPC<Unit> clearRequest = channel.addRPC(this::serverClear);

    public PhantomSlotWidget() {
        super();
    }

    public PhantomSlotWidget(IItemHandlerModifiable itemHandler, int slotIndex, int xPosition, int yPosition) {
        super(itemHandler, slotIndex, xPosition, yPosition, true, true);
    }

    public PhantomSlotWidget(IItemHandlerModifiable itemHandler, int slotIndex, int xPosition, int yPosition,
                             Predicate<ItemStack> validator) {
        super(itemHandler, slotIndex, xPosition, yPosition, true, true);
        this.validator = validator;
    }

    public PhantomSlotWidget(KeyInventory<AEItemKey> inventory, int slotIndex, int xPosition, int yPosition) {
        this(new MenuItemAdapter(inventory), slotIndex, xPosition, yPosition);
    }

    public PhantomSlotWidget(KeyInventory<AEItemKey> inventory, int slotIndex, int xPosition, int yPosition,
                             Predicate<ItemStack> validator) {
        this(new MenuItemAdapter(inventory), slotIndex, xPosition, yPosition, validator);
    }

    public PhantomSlotWidget(StackInventory inventory, int slotIndex, int xPosition, int yPosition) {
        this(new ForgeStackAdapter(inventory), slotIndex, xPosition, yPosition);
    }

    public PhantomSlotWidget(StackInventory inventory, int slotIndex, int xPosition, int yPosition,
                             Predicate<ItemStack> validator) {
        this(new ForgeStackAdapter(inventory), slotIndex, xPosition, yPosition, validator);
    }

    @Override
    public UIChannel getChannel() {
        return channel;
    }

    @Override
    public SlotWidget setHandlerSlot(IItemHandlerModifiable itemHandler, int slotIndex) {
        this.boundHandler = itemHandler;
        return super.setHandlerSlot(itemHandler, slotIndex);
    }

    @Override
    public SlotWidget setHandlerSlot(IItemTransfer itemHandler, int slotIndex) {
        this.boundHandler = null;
        return super.setHandlerSlot(itemHandler, slotIndex);
    }

    @Override
    public SlotWidget setContainerSlot(Container inventory, int slotIndex) {
        this.boundHandler = null;
        return super.setContainerSlot(inventory, slotIndex);
    }

    public PhantomSlotWidget setClearSlotOnRightClick(boolean clearSlotOnRightClick) {
        this.clearSlotOnRightClick = clearSlotOnRightClick;
        return this;
    }

    @ConfigSetter(field = "canTakeItems")
    public PhantomSlotWidget setCanTakeItems(boolean v) {
        // you cant modify it
        return this;
    }

    @ConfigSetter(field = "canPutItems")
    public PhantomSlotWidget setCanPutItems(boolean v) {
        // you cant modify it
        return this;
    }

    public PhantomSlotWidget setMaxStackSize(int stackSize) {
        maxStackSize = stackSize;
        return this;
    }

    @Override
    public void initWidget() {
        super.initWidget();
        channel.prime();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (slotReference != null && isMouseOverElement(mouseX, mouseY) && gui != null) {
            if (isClientSideWidget && !gui.getModularUIContainer().getCarried().isEmpty()) {
                slotReference.set(gui.getModularUIContainer().getCarried());
            } else if (button == 1 && clearSlotOnRightClick && !slotReference.getItem().isEmpty()) {
                slotReference.set(ItemStack.EMPTY);
                clearRequest.send(Unit.INSTANCE);
            } else {
                HOVER_SLOT = slotReference;
                gui.getModularUIGui().superMouseClicked(mouseX, mouseY, button);
                HOVER_SLOT = null;
            }
            return true;
        }
        return false;
    }

    @Override
    public ItemStack slotClick(int dragType, ClickType clickTypeIn, Player player) {
        if (slotReference != null && gui != null) {
            ItemStack stackHeld = gui.getModularUIContainer().getCarried();
            return slotClickPhantom(slotReference, dragType, clickTypeIn, stackHeld);
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canMergeSlot(ItemStack stack) {
        return false;
    }

    @Override
    public boolean canTakeStack(Player player) {
        return false;
    }

    @Override
    public boolean canPutStack(ItemStack stack) {
        return false;
    }

    @Nullable
    @OnlyIn(Dist.CLIENT)
    private static GenericStack phantomOf(Object ingredient) {
        if (GTCEu.Mods.isEMILoaded() && ingredient instanceof EmiStack emiStack) {
            Item item = emiStack.getKeyOfType(Item.class);
            if (item == null) return null;
            var stack = new ItemStack(item);
            stack.setTag(emiStack.getNbt());
            var key = AEItemKey.of(stack);
            return key == null ? null : new GenericStack(key, Math.max(1, emiStack.getAmount()));
        }
        if (ingredient instanceof ItemStack stack) {
            var key = AEItemKey.of(stack);
            return key == null ? null : new GenericStack(key, Math.max(1, stack.getCount()));
        }
        return null;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public List<Target> getPhantomTargets(Object ingredient) {
        if (phantomOf(ingredient) == null) {
            return Collections.emptyList();
        }

        Rect2i rectangle = toRectangleBox();
        return Lists.newArrayList(new Target() {

            @Nonnull
            @Override
            public Rect2i getArea() {
                return rectangle;
            }

            @Override
            public void accept(@NotNull Object ingredient) {
                var stack = phantomOf(ingredient);
                if (slotReference != null && stack != null) {
                    long id = Minecraft.getInstance().getWindow().getWindow();
                    boolean shiftDown = InputConstants.isKeyDown(id, GLFW.GLFW_KEY_LEFT_SHIFT);
                    ClickType clickType = shiftDown ? ClickType.QUICK_MOVE : ClickType.PICKUP;
                    slotClickPhantom(slotReference, 0, clickType, Keys.toStack((AEItemKey) stack.what(), stack.amount()));
                    dropRequest.send(new Drop(stack, shiftDown));
                }
            }
        });
    }

    private static boolean canInteract(@Nullable Player player) {
        return player == null || !player.isSpectator();
    }

    private boolean isSlotInRange() {
        var slot = slotReference;
        if (slot == null) return false;
        var handler = boundHandler;
        int index = slot.getContainerSlot();
        return handler == null || index >= 0 && index < handler.getSlots();
    }

    private void serverDrop(@Nullable Player player, Drop drop) {
        if (!canInteract(player) || !isSlotInRange()) return;
        var stack = drop.stack();
        ClickType clickType = drop.shift() ? ClickType.QUICK_MOVE : ClickType.PICKUP;
        slotClickPhantom(slotReference, 0, clickType, Keys.toStack((AEItemKey) stack.what(), stack.amount()));
    }

    private void serverClear(@Nullable Player player) {
        if (!clearSlotOnRightClick || !canInteract(player) || !isSlotInRange()) return;
        slotReference.set(ItemStack.EMPTY);
    }

    @Override
    public void handleClientAction(int id, FriendlyByteBuf buffer) {
        if (!channel.handleClientAction(id, buffer)) super.handleClientAction(id, buffer);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void readUpdateInfo(int id, FriendlyByteBuf buffer) {
        if (!channel.readUpdateInfo(id, buffer)) super.readUpdateInfo(id, buffer);
    }

    @Override
    public void writeInitialData(FriendlyByteBuf buffer) {
        super.writeInitialData(buffer);
        channel.writeInitialData(buffer);
    }

    @Override
    public void readInitialData(FriendlyByteBuf buffer) {
        super.readInitialData(buffer);
        channel.readInitialData(buffer);
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        channel.detectAndSendChanges();
    }

    public ItemStack slotClickPhantom(Slot slot, int mouseButton, ClickType clickTypeIn, ItemStack stackHeld) {
        ItemStack stack = ItemStack.EMPTY;

        ItemStack stackSlot = slot.getItem();
        if (!stackSlot.isEmpty()) {
            stack = stackSlot.copy();
        }

        if (mouseButton == 2) {
            fillPhantomSlot(slot, ItemStack.EMPTY, mouseButton);
        } else if (mouseButton == 0 || mouseButton == 1) {

            if (stackSlot.isEmpty()) {
                if (!stackHeld.isEmpty()) {
                    fillPhantomSlot(slot, stackHeld, mouseButton);
                }
            } else if (stackHeld.isEmpty()) {
                adjustPhantomSlot(slot, mouseButton, clickTypeIn);
            } else {
                if (!areItemsEqual(stackSlot, stackHeld)) {
                    adjustPhantomSlot(slot, mouseButton, clickTypeIn);
                }
                fillPhantomSlot(slot, stackHeld, mouseButton);
            }
        } else if (mouseButton == 5) {
            if (!slot.hasItem()) {
                fillPhantomSlot(slot, stackHeld, mouseButton);
            }
        }
        return stack;
    }

    private void adjustPhantomSlot(Slot slot, int mouseButton, ClickType clickTypeIn) {
        ItemStack stackSlot = slot.getItem().copy();
        int stackSize;
        if (clickTypeIn == ClickType.QUICK_MOVE) {
            stackSize = mouseButton == 0 ? (stackSlot.getCount() + 1) / 2 : stackSlot.getCount() * 2;
        } else {
            stackSize = mouseButton == 0 ? stackSlot.getCount() - 1 : stackSlot.getCount() + 1;
        }

        if (stackSize > slot.getMaxStackSize()) {
            stackSize = slot.getMaxStackSize();
        }

        stackSlot.setCount(Math.min(maxStackSize, stackSize));

        slot.set(stackSlot);
    }

    private void fillPhantomSlot(Slot slot, ItemStack stackHeld, int mouseButton) {
        if (stackHeld.isEmpty()) {
            slot.set(ItemStack.EMPTY);
            return;
        }

        int stackSize = mouseButton == 0 ? stackHeld.getCount() : 1;
        if (stackSize > slot.getMaxStackSize()) {
            stackSize = slot.getMaxStackSize();
        }
        ItemStack phantomStack = stackHeld.copy();
        phantomStack.setCount(Math.min(maxStackSize, stackSize));
        if (validator.test(phantomStack) && passesFilter(phantomStack)) slot.set(phantomStack);
    }

    private boolean passesFilter(ItemStack stack) {
        if (boundHandler instanceof MenuItemAdapter adapter) {
            var filter = adapter.getInventory().getFilter();
            if (filter == null) return true;
            var key = AEItemKey.of(stack);
            return key != null && filter.matches(key);
        }
        if (boundHandler instanceof ForgeStackAdapter adapter) {
            return adapter.getInventory().getFilter().test(stack);
        }
        return true;
    }

    public boolean areItemsEqual(ItemStack itemStack1, ItemStack itemStack2) {
        return ItemStack.matches(itemStack1, itemStack2);
    }

    private record Drop(GenericStack stack, boolean shift) {}
}
