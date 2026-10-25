package com.gregtechceu.gtceu.common.machine.storage;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.capability.IControllable;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.item.tool.GTToolType;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.TieredMachine;
import com.gregtechceu.gtceu.api.machine.feature.IAutoOutputItem;
import com.gregtechceu.gtceu.api.machine.feature.IDropSaveMachine;
import com.gregtechceu.gtceu.api.machine.feature.IFancyUIMachine;
import com.gregtechceu.gtceu.api.machine.feature.IInteractedMachine;
import com.gregtechceu.gtceu.api.machine.trait.ICapabilityTrait;
import com.gregtechceu.gtceu.api.machine.trait.MachineTrait;
import com.gregtechceu.gtceu.api.misc.TickTimeMonitor;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.transfer.key.IKeyHandler;
import com.gregtechceu.gtceu.api.transfer.key.KeyInventory;
import com.gregtechceu.gtceu.api.transfer.key.KeyTransfer;
import com.gregtechceu.gtceu.api.transfer.key.Keys;
import com.gregtechceu.gtceu.api.transfer.key.MEStorageProvider;
import com.gregtechceu.gtceu.api.transfer.key.MEStorageProviderIndex;
import com.gregtechceu.gtceu.api.transfer.key.StackInventory;
import com.gregtechceu.gtceu.common.data.GTTickTimeMonitors;
import com.gregtechceu.gtceu.uipro.LayoutStyle;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.elements.Button;
import com.gregtechceu.gtceu.uipro.elements.Form;
import com.gregtechceu.gtceu.uipro.elements.IconToggle;
import com.gregtechceu.gtceu.uipro.elements.ItemSlot;
import com.gregtechceu.gtceu.uipro.elements.NumberField;
import com.gregtechceu.gtceu.uipro.elements.PhantomItemSlot;
import com.gregtechceu.gtceu.uipro.elements.StatusPanel;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;
import com.gregtechceu.gtceu.uiwidgets.icon.WidgetIcons;
import com.gregtechceu.gtceu.uiwidgets.inventory.HatchViews;
import com.gregtechceu.gtceu.utils.*;

import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;
import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.items.ItemHandlerHelper;

import appeng.api.stacks.*;
import appeng.api.storage.StorageAccess;
import com.gto.datasynclib.annotations.AdditionalHolder;
import com.gto.datasynclib.annotations.SaveToDisk;
import com.gto.datasynclib.annotations.SyncToClient;
import com.mojang.blaze3d.MethodsReturnNonnullByDefault;
import dev.vfyjxf.taffy.style.AlignItems;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.function.Supplier;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class QuantumChestMachine extends TieredMachine implements IAutoOutputItem, IInteractedMachine, IControllable, IDropSaveMachine, IFancyUIMachine, MEStorageProvider {

    private final StorageInteraction interaction = new StorageInteraction();
    @Getter
    @SaveToDisk
    @SyncToClient(scheduleUpdate = true)
    protected Direction outputFacingItems;
    @Getter
    @SaveToDisk(defaultValue = "false")
    @SyncToClient(scheduleUpdate = true)
    protected boolean autoOutputItems;
    @Getter
    @SaveToDisk(defaultValue = "false")
    protected boolean allowInputFromOutputSideItems;
    @SaveToDisk(defaultValue = "false")
    private boolean isVoiding;

    @Getter
    private final long max;

    @SaveToDisk(defaultValueGetter = "getMax")
    @Getter
    private long maxAmount;
    @AdditionalHolder
    protected final ItemCache cache;
    @SyncToClient
    private final KeyInventory<AEItemKey> lockedItem;
    @Nullable
    @SyncToClient
    protected AEItemKey storedKey;
    @Getter
    @SyncToClient
    protected long storedAmount;
    private final KeyInventory<AEItemKey> display = KeyInventory.items(1);
    @Nullable
    protected TickableSubscription autoOutputSubs;
    protected final TickTimeMonitor autoOutputMonitor = holder.monitorTick(GTTickTimeMonitors.AUTO_OUTPUT, this::checkAutoOutput);

    public QuantumChestMachine(MetaMachineBlockEntity holder, int tier, long maxAmount, Object... args) {
        super(holder, tier);
        this.outputFacingItems = getFrontFacing().getOpposite();
        this.maxAmount = maxAmount;
        this.max = maxAmount;
        this.cache = createCacheItemHandler(args);
        this.lockedItem = KeyInventory.items(1);
        this.lockedItem.setOnChanged(this::applyLock);
    }

    //////////////////////////////////////
    // ***** Initialization ******//
    //////////////////////////////////////

    protected ItemCache createCacheItemHandler(Object... args) {
        return new ItemCache(this);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (getLevel() instanceof ServerLevel serverLevel) {
            TaskHandler.enqueueTask(serverLevel, this::updateAutoOutputSubscription, 0);
        }
        MEStorageProviderIndex.add(getLevel(), this);
    }

    @Override
    public void onUnload() {
        MEStorageProviderIndex.remove(getLevel(), this);
        super.onUnload();
    }

    protected void onItemChanged() {
        if (!isRemote()) {
            syncStored();
            if (getLevel() != null) {
                onChanged();
                updateAutoOutputSubscription();
                requestSync();
            }
        }
    }

    private void syncStored() {
        var key = cache.storage.keyAt(0);
        long amount = cache.storage.amountAt(0);
        storedKey = key;
        storedAmount = amount;
        display.set(0, key, key == null ? 0 : Math.min(amount, key.getMaxStackSize()));
    }

    public ItemStack getStored() {
        return Keys.displayStack(storedKey);
    }

    protected void loadStored(@Nullable AEItemKey key, long amount) {
        cache.storage.set(0, key, amount);
    }

    @Override
    public boolean savePickClone() {
        return false;
    }

    @Override
    public boolean saveBreak() {
        return cache.storage.amountAt(0) > 0;
    }

    @Override
    public void saveCustomPersistedData(CompoundTag tag, boolean forDrop) {
        super.saveCustomPersistedData(tag, forDrop);
        if (!forDrop) {
            var locked = new StackInventory(1);
            var lockedKey = lockedItem.keyAt(0);
            if (lockedKey != null) locked.setStackInSlot(0, lockedKey.toStack(1));
            tag.put("lockedItem", locked.serializeNBT());
        }
        var key = cache.storage.keyAt(0);
        tag.put("stored", (key == null ? ItemStack.EMPTY : key.toStack(1)).serializeNBT());
        tag.putLong("storedAmount", cache.storage.amountAt(0));
    }

    @Override
    public void loadCustomPersistedData(CompoundTag tag) {
        super.loadCustomPersistedData(tag);
        if (tag.contains("lockedItem")) {
            var locked = new StackInventory(1);
            locked.deserializeNBT(tag.get("lockedItem"));
            var lockedStack = locked.getStackInSlot(0);
            lockedItem.set(0, AEItemKey.of(lockedStack), lockedStack.isEmpty() ? 0 : 1);
        }
        var stored = ItemStack.of(tag.getCompound("stored"));
        loadStored(AEItemKey.of(stored), tag.getLong("storedAmount"));
    }

    //////////////////////////////////////
    // ****** Capability ********//
    //////////////////////////////////////
    @Override
    @Nullable
    public IKeyHandler<AEItemKey> getItemHandlerCap(@Nullable Direction side, boolean useCoverCapability) {
        if (side == getFrontFacing()) {
            return null;
        }
        return super.getItemHandlerCap(side, useCoverCapability);
    }

    @Override
    @Nullable
    public IKeyHandler<AEFluidKey> getFluidHandlerCap(@Nullable Direction side, boolean useCoverCapability) {
        return null;
    }

    //////////////////////////////////////
    // ******* Auto Output *******//
    //////////////////////////////////////
    @Override
    public void setAutoOutputItems(boolean allow) {
        this.autoOutputItems = allow;
        updateAutoOutputSubscription();
    }

    @Override
    public void setOutputFacingItems(@Nullable Direction outputFacing) {
        clearDirectionCache();
        this.outputFacingItems = outputFacing;
        updateAutoOutputSubscription();
    }

    @Override
    public void onNeighborChanged(Block block, BlockPos fromPos, boolean isMoving) {
        super.onNeighborChanged(block, fromPos, isMoving);
        updateAutoOutputSubscription();
    }

    @Override
    public boolean isWorkingEnabled() {
        return isAutoOutputItems();
    }

    @Override
    public void setWorkingEnabled(boolean isWorkingAllowed) {
        setAutoOutputItems(isWorkingAllowed);
    }

    protected void updateAutoOutputSubscription() {
        var outputFacing = getOutputFacingItems();
        if ((isAutoOutputItems() && cache.storage.amountAt(0) > 0) && outputFacing != null && holder.blockEntityDirectionCache.hasAdjacentTarget(getLevel(), getPos(), outputFacing, AEKeyTypes.ITEMS, StorageAccess.INSERT)) {
            autoOutputSubs = subscribeServerTick(autoOutputSubs, autoOutputMonitor, getTicksPerCycle());
        } else if (autoOutputSubs != null) {
            autoOutputSubs.unsubscribe();
            autoOutputSubs = null;
        }
    }

    protected int getTicksPerCycle() {
        return 20;
    }

    protected void checkAutoOutput() {
        if (isAutoOutputItems() && getOutputFacingItems() != null) {
            cache.exportToNearby(getOutputFacingItems());
        }
        updateAutoOutputSubscription();
    }

    //////////////////////////////////////
    // ******* Interaction *******//
    //////////////////////////////////////
    @Override
    public void saveToItem(CompoundTag tag) {
        saveCustomPersistedData(tag, true);
    }

    @Override
    public void loadFromItem(CompoundTag tag) {
        loadCustomPersistedData(tag);
    }

    @Override
    public boolean isFacingValid(Direction facing) {
        if (facing == outputFacingItems) return false;
        return super.isFacingValid(facing);
    }

    @Override
    public InteractionResult onUse(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!StorageInteraction.isDisplayHit(getPos(), getFrontFacing(), hit)) return InteractionResult.PASS;
        if (!world.isClientSide) {
            boolean repeated = interaction.recordUse(player.getUUID(), hand, world.getGameTime());
            var held = player.getItemInHand(hand);
            var key = AEItemKey.of(held);
            if (key != null) {
                held.shrink((int) cache.insert(0, key, held.getCount(), false));
            }
            if (repeated && cache.storage.keyAt(0) != null) {
                var stored = cache.storage.keyAt(0);
                for (var stack : player.getInventory().items) {
                    key = AEItemKey.of(stack);
                    if (key == stored) {
                        stack.shrink((int) cache.insert(0, key, stack.getCount(), false));
                    }
                }
            }
            player.getInventory().setChanged();
        }
        return InteractionResult.sidedSuccess(world.isClientSide);
    }

    @Override
    public boolean onLeftClick(Player player, Level world, InteractionHand hand, BlockPos pos, Direction direction) {
        if (direction != getFrontFacing() || player.getItemInHand(hand).is(GTToolType.WRENCH.itemTags.getFirst())) return false;
        if (!(player.pick(player.getBlockReach(), 0, false) instanceof BlockHitResult hit) ||
                !StorageInteraction.isDisplayHit(getPos(), getFrontFacing(), hit))
            return false;
        if (!world.isClientSide && interaction.claimExtraction(world.getGameTime())) {
            var key = cache.storage.keyAt(0);
            if (key != null) {
                long drained = cache.extract(0, key, player.isShiftKeyDown() ? key.getMaxStackSize() : 1, false);
                if (drained > 0) {
                    ItemHandlerHelper.giveItemToPlayer(player, Keys.toStack(key, drained));
                }
            }
        }
        // Cancel on both sides: vanilla retries START while held, without progressing block damage.
        return true;
    }

    @Override
    protected InteractionResult onWrenchClick(Player playerIn, InteractionHand hand, Direction gridSide, BlockHitResult hitResult) {
        if (!playerIn.isShiftKeyDown() && !isRemote()) {
            var tool = playerIn.getItemInHand(hand);
            if (tool.getDamageValue() >= tool.getMaxDamage()) return InteractionResult.PASS;
            if (hasFrontFacing() && gridSide == getFrontFacing()) return InteractionResult.PASS;
            if (gridSide != getOutputFacingItems()) {
                setOutputFacingItems(gridSide);
            } else {
                setOutputFacingItems(null);
            }
            return InteractionResult.sidedSuccess(playerIn.level().isClientSide);
        }
        return super.onWrenchClick(playerIn, hand, gridSide, hitResult);
    }

    @Override
    protected InteractionResult onScrewdriverClick(Player playerIn, InteractionHand hand, Direction gridSide, BlockHitResult hitResult) {
        if (!isRemote()) {
            if (gridSide == getOutputFacingItems()) {
                if (isAllowInputFromOutputSideItems()) {
                    setAllowInputFromOutputSideItems(false);
                    playerIn.sendSystemMessage(Component.translatable("gtceu.machine.basic.input_from_output_side.disallow").append(Component.translatable("gtceu.creative.chest.item")));
                } else {
                    setAllowInputFromOutputSideItems(true);
                    playerIn.sendSystemMessage(Component.translatable("gtceu.machine.basic.input_from_output_side.allow").append(Component.translatable("gtceu.creative.chest.item")));
                }
            }
            return InteractionResult.SUCCESS;
        }
        return super.onScrewdriverClick(playerIn, hand, gridSide, hitResult);
    }

    public boolean isLocked() {
        return lockedItem.amountAt(0) > 0;
    }

    protected void setLocked(boolean locked) {
        var key = cache.storage.keyAt(0);
        if (key != null && locked) {
            lockedItem.set(0, key, 1);
        } else if (!locked) {
            lockedItem.set(0, null, 0);
        }
    }

    public ItemStack getLockedItem() {
        return Keys.displayStack(lockedItem.keyAt(0));
    }

    private void applyLock() {
        cache.storage.setLocked(0, lockedItem.keyAt(0));
    }

    private boolean canLockTo(ItemStack stack) {
        var key = storedKey;
        return key == null || AEItemKey.of(stack) == key;
    }

    //////////////////////////////////////
    // *********** GUI ***********//
    //////////////////////////////////////
    public Widget createUIWidget() {
        var importItems = KeyInventory.items(1);
        var importSlot = ItemSlot.of(importItems, 0, false, true);
        configureImportItems(importItems, () -> importSlot.getGui() == null ? null : importSlot.getGui().entityPlayer);
        importSlot.setBackgroundTexture(new GuiTextureGroup(UITheme.ITEM_SLOT, GuiTextures.IN_SLOT_OVERLAY));
        var storedSlot = ItemSlot.of(display, 0, false, false);
        var extract = Button.icon(UISizes.SLOT_SIZE, UITheme.ARROW_DOWN).tooltips(HatchViews.EXTRACT_STACK);
        extract.setOnServerClick(() -> extractStack(extract.getGui() == null ? null : extract.getGui().entityPlayer));
        var lock = IconToggle.of(WidgetIcons.ACCESS_PRIVATE, this::isLocked, this::setLocked)
                .onOffTooltips("gtceu.gui.item_lock.tooltip.enabled", "gtceu.gui.item_lock.tooltip.disabled");
        var lockSlot = PhantomItemSlot.of(this::getLockedItem, this::setLockedItem, this::canLockTo).xeiPhantom();
        var output = IconToggle.of(WidgetIcons.EXPORT, this::isAutoOutputItems, this::setAutoOutputItems)
                .onOffTooltips("gtceu.gui.item_auto_output.tooltip.enabled", "gtceu.gui.item_auto_output.tooltip.disabled");
        var voiding = IconToggle.of(WidgetIcons.VOID_OVERFLOW, this::isVoiding, this::setVoiding)
                .onOffTooltips("gtceu.gui.item_voiding_partial.tooltip.enabled", "gtceu.gui.item_voiding_partial.tooltip.disabled");
        var limit = Form.numberRow(HatchViews.CAPACITY_LIMIT,
                NumberField.ofLong(UISizes.CONTENT_WIDTH, this::getMaxAmount, this::setMaxAmount, 1, max), HatchViews.CAPACITY_LIMIT_TIP);
        limit.layout(l -> l.alignSelf(AlignItems.STRETCH));
        var status = new StatusPanel();
        status.addLine(HatchViews.ITEM, HatchViews.keyName(() -> storedKey, HatchViews.EMPTY));
        status.addLine(HatchViews.STORED, HatchViews.amountText(this::getStoredAmount, this::getMaxAmount, null));
        status.addLine(HatchViews.LOCKED_ITEM, HatchViews.keyName(() -> lockedItem.keyAt(0), HatchViews.NOT_LOCKED));
        var operations = UIElement.column(LayoutStyle.AUTO).layout(l -> l.gapAll(UISizes.SECTION_GAP).alignCenter().alignSelf(AlignItems.STRETCH)).addChildren(
                HatchViews.operations(importSlot, HatchViews.group(storedSlot, extract), HatchViews.group(lock, lockSlot), HatchViews.group(output, voiding)),
                limit);
        return HatchViews.page(operations, status);
    }

    public boolean isVoiding() {
        return isVoiding;
    }

    public void setVoiding(boolean voiding) {
        isVoiding = voiding;
        onChanged();
    }

    public void setMaxAmount(long maxAmount) {
        this.maxAmount = Math.max(1, Math.min(max, maxAmount));
        onChanged();
    }

    private void extractStack(@Nullable Player player) {
        if (player == null) return;
        var key = cache.storage.keyAt(0);
        if (key == null) return;
        long extracted = cache.extract(0, key, key.getMaxStackSize(), false);
        if (extracted <= 0) return;
        var stack = Keys.toStack(key, extracted);
        player.getInventory().add(stack);
        if (!stack.isEmpty()) player.drop(stack, false);
    }

    private void configureImportItems(KeyInventory<AEItemKey> importItems, Supplier<Player> player) {
        importItems.setFilter(key -> key instanceof AEItemKey itemKey && cache.canInsert(itemKey));
        importItems.setOnChanged(() -> {
            if (isRemote()) return;
            var key = importItems.keyAt(0);
            if (key == null) return;
            long amount = importItems.amountAt(0);
            importItems.set(0, null, 0);
            long left = amount - cache.insert(0, key, amount, false);
            Player target = player.get();
            if (left > 0 && target != null) {
                ItemHandlerHelper.giveItemToPlayer(target, Keys.toStack(key, left));
            }
        });
    }

    private void setLockedItem(ItemStack stack) {
        if (stack.isEmpty()) {
            lockedItem.set(0, null, 0);
        } else if (canLockTo(stack)) {
            lockedItem.set(0, AEItemKey.of(stack), 1);
        }
    }

    //////////////////////////////////////
    // ******* Rendering ********//
    //////////////////////////////////////
    @Override
    public ResourceTexture sideTips(Player player, BlockPos pos, BlockState state, Set<GTToolType> toolTypes, Direction side) {
        if (toolTypes.contains(GTToolType.WRENCH)) {
            if (!player.isShiftKeyDown()) {
                if (!hasFrontFacing() || side != getFrontFacing()) {
                    return GuiTextures.TOOL_IO_FACING_ROTATION;
                }
            }
        } else if (toolTypes.contains(GTToolType.SCREWDRIVER)) {
            if (side == getOutputFacingItems()) {
                return GuiTextures.TOOL_ALLOW_INPUT;
            }
        } else if (toolTypes.contains(GTToolType.SOFT_MALLET)) {
            if (side == getFrontFacing()) return null;
        }
        return super.sideTips(player, pos, state, toolTypes, side);
    }

    protected class ItemCache extends MachineTrait implements IKeyHandler<AEItemKey>, ICapabilityTrait {

        protected final KeyInventory<AEItemKey> storage = KeyInventory.items(1, Long.MAX_VALUE, false);

        public ItemCache(MetaMachine holder) {
            super(holder);
            storage.setOnChanged(QuantumChestMachine.this::onItemChanged);
        }

        @Override
        public AEKeyType keyType() {
            return AEKeyTypes.ITEMS;
        }

        @Override
        public int size() {
            return 1;
        }

        @Override
        public @Nullable AEItemKey keyAt(int slot) {
            return storage.keyAt(slot);
        }

        @Override
        public long amountAt(int slot) {
            return storage.amountAt(slot);
        }

        @Override
        public long slotLimit(int slot) {
            return maxAmount;
        }

        @Override
        public long insert(int slot, AEItemKey key, long amount, boolean simulate) {
            if (slot != 0 || amount <= 0) return 0;
            var lock = storage.lockedAt(0);
            if (lock != null && lock != key) return 0;
            long stored = storage.amountAt(0);
            if (stored != 0 ? storage.rawKeyAt(0) != key : !storage.acceptsEmpty(0, key)) return 0;
            long space = maxAmount - stored;
            long n = space > 0 ? storage.insert(0, key, Math.min(amount, space), simulate) : 0;
            return isVoiding ? amount : n;
        }

        @Override
        public long extract(int slot, AEItemKey key, long amount, boolean simulate) {
            return slot == 0 ? storage.extract(0, key, amount, simulate) : 0;
        }

        @Override
        public long insert(AEItemKey key, long amount, boolean simulate) {
            return insert(0, key, amount, simulate);
        }

        @Override
        public long extract(AEItemKey key, long amount, boolean simulate) {
            return extract(0, key, amount, simulate);
        }

        @Override
        public KeyCounter getAvailableStacks() {
            return storage.getAvailableStacks();
        }

        protected long exportLimit() {
            return Long.MAX_VALUE;
        }

        public void exportToNearby(Direction... facings) {
            var key = storage.keyAt(0);
            if (key == null) return;
            var m = getMachine();
            var level = m.getLevel();
            var pos = m.getPos();
            var cache = holder.blockEntityDirectionCache;
            for (Direction facing : facings) {
                var filter = m.getKeyCapFilter(facing, IO.OUT, AEKeyTypes.ITEMS);
                if (filter != null && !filter.matches(key)) continue;
                if (cache.getAdjacentKeyHandler(level, pos, facing, AEKeyTypes.ITEMS, StorageAccess.INSERT) instanceof IKeyHandler<?> target) {
                    @SuppressWarnings("unchecked")
                    var to = (IKeyHandler<AEItemKey>) target;
                    KeyTransfer.transferKey(this, to, key, exportLimit());
                }
            }
        }

        public boolean canInsert(AEItemKey key) {
            return insert(0, key, 1, true) > 0;
        }
    }

    public void setAllowInputFromOutputSideItems(final boolean allowInputFromOutputSideItems) {
        clearDirectionCache();
        this.allowInputFromOutputSideItems = allowInputFromOutputSideItems;
    }
}
