package com.gregtechceu.gtceu.common.machine.storage;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.capability.IControllable;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.widget.LongInputWidget;
import com.gregtechceu.gtceu.api.gui.widget.PhantomSlotWidget;
import com.gregtechceu.gtceu.api.gui.widget.SlotWidget;
import com.gregtechceu.gtceu.api.gui.widget.ToggleButtonWidget;
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
import com.gregtechceu.gtceu.api.transfer.forge.MenuItemAdapter;
import com.gregtechceu.gtceu.api.transfer.key.IKeyHandler;
import com.gregtechceu.gtceu.api.transfer.key.KeyInventory;
import com.gregtechceu.gtceu.api.transfer.key.KeyTransfer;
import com.gregtechceu.gtceu.api.transfer.key.Keys;
import com.gregtechceu.gtceu.api.transfer.key.StackInventory;
import com.gregtechceu.gtceu.common.data.GTTickTimeMonitors;
import com.gregtechceu.gtceu.utils.*;

import com.lowdragmc.lowdraglib.gui.editor.Icons;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.ResourceBorderTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;
import com.lowdragmc.lowdraglib.gui.widget.*;

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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.items.ItemHandlerHelper;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.storage.StorageAccess;
import com.gto.datasynclib.annotations.AdditionalHolder;
import com.gto.datasynclib.annotations.SaveToDisk;
import com.gto.datasynclib.annotations.SyncToClient;
import com.gto.fastcollection.fastutil.O2LOpenCacheHashMap;
import com.mojang.blaze3d.MethodsReturnNonnullByDefault;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class QuantumChestMachine extends TieredMachine implements IAutoOutputItem, IInteractedMachine, IControllable, IDropSaveMachine, IFancyUIMachine {

    /**
     * Sourced from FunctionalStorage's
     * <a
     * href=https://github.com/Buuz135/FunctionalStorage/blob/1.21/src/main/java/com/buuz135/functionalstorage/block/tile/ItemControllableDrawerTile.java>
     * ItemControllerDrawerTile</a>
     */
    public static final O2LOpenCacheHashMap<UUID> INTERACTION_LOGGER = new O2LOpenCacheHashMap<>();
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
    @Setter
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
        if (hit.getDirection() == getFrontFacing() && !isRemote()) {
            // Check to see if the hit is within the glass frame of the chest
            var aabb = new AABB(hit.getBlockPos()).deflate(0.12);
            var hitVector = hit.getLocation().relative(getFrontFacing(), -0.5);
            if (!aabb.contains(hitVector)) return InteractionResult.PASS;
            var held = player.getMainHandItem();
            var heldKey = AEItemKey.of(held);
            if (heldKey != null && cache.canInsert(heldKey)) {
                // push
                held.shrink((int) cache.insert(0, heldKey, held.getCount(), false));
                return InteractionResult.SUCCESS;
            } else if (isDoubleHit(player.getUUID())) {
                for (var stack : player.getInventory().items) {
                    var key = AEItemKey.of(stack);
                    if (key != null && cache.canInsert(key)) {
                        stack.shrink((int) cache.insert(0, key, stack.getCount(), false));
                    }
                }
            }
            INTERACTION_LOGGER.put(player.getUUID(), System.currentTimeMillis());
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    private static boolean isDoubleHit(UUID uuid) {
        return (System.currentTimeMillis() - INTERACTION_LOGGER.getLong(uuid)) < 300;
    }

    @Override
    public boolean onLeftClick(Player player, Level world, InteractionHand hand, BlockPos pos, Direction direction) {
        if (direction == getFrontFacing() && !isRemote()) {
            if (player.getItemInHand(hand).is(GTToolType.WRENCH.itemTags.getFirst())) return false;
            var key = cache.storage.keyAt(0);
            if (key != null) {
                // pull
                long drained = cache.extract(0, key, player.isShiftKeyDown() ? key.getMaxStackSize() : 1, false);
                if (drained > 0) {
                    var stack = Keys.toStack(key, drained);
                    if (!player.addItem(stack)) {
                        Block.popResourceFromFace(world, getPos(), getFrontFacing(), stack);
                    }
                }
            }
        }
        return IInteractedMachine.super.onLeftClick(player, world, hand, pos, direction);
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
        var group = new WidgetGroup(0, 0, 109, 87);
        var importItems = createImportItems(group);
        group.addWidget(new ImageWidget(4, 4, 82, 55, GuiTextures.DISPLAY)).addWidget(new LabelWidget(8, 8, "gtceu.machine.quantum_chest.items_stored")).addWidget(new LabelWidget(8, 18, () -> FormattingUtil.formatNumbers(getStoredAmount())).setTextColor(-1).setDropShadow(true)).addWidget(new SlotWidget(new MenuItemAdapter(importItems), 0, 87, 4, false, true).setBackgroundTexture(new GuiTextureGroup(GuiTextures.SLOT, GuiTextures.IN_SLOT_OVERLAY))).addWidget(new SlotWidget(new MenuItemAdapter(display), 0, 87, 22, false, false).setBackgroundTexture(GuiTextures.SLOT)).addWidget(new ButtonWidget(87, 41, 18, 18, new GuiTextureGroup(ResourceBorderTexture.BUTTON_COMMON, Icons.DOWN.scale(0.7F)), cd -> {
            if (!cd.isRemote) {
                var key = cache.storage.keyAt(0);
                if (key != null) {
                    long extracted = cache.extract(0, key, key.getMaxStackSize(), false);
                    if (extracted > 0) {
                        var player = group.getGui().entityPlayer;
                        var stack = Keys.toStack(key, extracted);
                        if (!player.addItem(stack)) {
                            Block.popResource(player.level(), player.getOnPos(), stack);
                        }
                    }
                }
            }
        })).addWidget(new PhantomSlotWidget(lockedItem, 0, 58, 41, this::canLockTo).setMaxStackSize(1)).addWidget(new ToggleButtonWidget(4, 41, 18, 18, GuiTextures.BUTTON_ITEM_OUTPUT, this::isAutoOutputItems, this::setAutoOutputItems).setShouldUseBaseBackground().setTooltipText("gtceu.gui.item_auto_output.tooltip")).addWidget(new ToggleButtonWidget(22, 41, 18, 18, GuiTextures.BUTTON_LOCK, this::isLocked, this::setLocked).setShouldUseBaseBackground().setTooltipText("gtceu.gui.item_lock.tooltip")).addWidget(new ToggleButtonWidget(40, 41, 18, 18, GuiTextures.BUTTON_VOID, () -> isVoiding, b -> isVoiding = b).setShouldUseBaseBackground().setTooltipText("gtceu.gui.item_voiding_partial.tooltip"));
        group.addWidget(new LongInputWidget(4, 62, 101, 20, this::getMaxAmount, this::setMaxAmount).setMax(max).setMin(1L).setHoverTooltips(Component.translatable("ldlib.gui.editor.name.maxCount")));
        group.setBackground(GuiTextures.BACKGROUND_INVERSE);
        return group;
    }

    private KeyInventory<AEItemKey> createImportItems(WidgetGroup group) {
        var importItems = KeyInventory.items(1);
        importItems.setFilter(key -> key instanceof AEItemKey itemKey && cache.canInsert(itemKey));
        importItems.setOnChanged(() -> {
            if (isRemote()) return;
            var key = importItems.keyAt(0);
            if (key == null) return;
            long amount = importItems.amountAt(0);
            importItems.set(0, null, 0);
            long left = amount - cache.insert(0, key, amount, false);
            if (left > 0 && group.getGui() != null) {
                ItemHandlerHelper.giveItemToPlayer(group.getGui().entityPlayer, Keys.toStack(key, left));
            }
        });
        return importItems;
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
