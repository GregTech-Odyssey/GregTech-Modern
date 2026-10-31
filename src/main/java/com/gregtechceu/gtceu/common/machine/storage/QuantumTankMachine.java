package com.gregtechceu.gtceu.common.machine.storage;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.capability.IControllable;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.item.tool.GTToolType;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.TieredMachine;
import com.gregtechceu.gtceu.api.machine.feature.IAutoOutputFluid;
import com.gregtechceu.gtceu.api.machine.feature.IDropSaveMachine;
import com.gregtechceu.gtceu.api.machine.feature.IInteractedMachine;
import com.gregtechceu.gtceu.api.machine.feature.IStorageAppearance;
import com.gregtechceu.gtceu.api.machine.trait.ICapabilityTrait;
import com.gregtechceu.gtceu.api.machine.trait.MachineTrait;
import com.gregtechceu.gtceu.api.machine.trait.StorageAppearance;
import com.gregtechceu.gtceu.api.machine.trait.StorageUpgrades;
import com.gregtechceu.gtceu.api.misc.TickTimeMonitor;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.transfer.forge.ForgeFluidAdapter;
import com.gregtechceu.gtceu.api.transfer.key.*;
import com.gregtechceu.gtceu.common.data.GTTickTimeMonitors;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.uipro.LayoutStyle;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.elements.FluidSlot;
import com.gregtechceu.gtceu.uipro.elements.Form;
import com.gregtechceu.gtceu.uipro.elements.IconToggle;
import com.gregtechceu.gtceu.uipro.elements.NumberField;
import com.gregtechceu.gtceu.uipro.elements.PhantomFluidSlot;
import com.gregtechceu.gtceu.uipro.elements.StatusPanel;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uiwidgets.icon.WidgetIcons;
import com.gregtechceu.gtceu.uiwidgets.inventory.HatchViews;
import com.gregtechceu.gtceu.utils.*;

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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;

import appeng.api.stacks.*;
import appeng.api.storage.StorageAccess;
import com.gto.datasynclib.annotations.AdditionalHolder;
import com.gto.datasynclib.annotations.SaveToDisk;
import com.gto.datasynclib.annotations.SyncToClient;
import com.mojang.blaze3d.MethodsReturnNonnullByDefault;
import dev.vfyjxf.taffy.style.AlignItems;
import it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class QuantumTankMachine extends TieredMachine implements IAutoOutputFluid, IInteractedMachine, IControllable, IDropSaveMachine, IStorageAppearance, MEStorageProvider {

    private static final int LOCK_MARKER = 1000;

    private final StorageInteraction interaction = new StorageInteraction();
    private final ForgeFluidAdapter interactionTank;

    public static Reference2LongOpenHashMap<MachineDefinition> TANK_CAPACITY = new Reference2LongOpenHashMap<>();
    @Getter
    @SaveToDisk
    @SyncToClient(scheduleUpdate = true)
    protected Direction outputFacingFluids;
    @Getter
    @SaveToDisk(defaultValue = "false")
    @SyncToClient(scheduleUpdate = true)
    protected boolean autoOutputFluids;
    @Getter
    @SaveToDisk(defaultValue = "false")
    protected boolean allowInputFromOutputSideFluids;
    @Getter
    @SaveToDisk(defaultValue = "false")
    @SyncToClient(scheduleUpdate = true)
    private boolean isVoiding;

    @Getter
    @AdditionalHolder
    private final StorageAppearance storageAppearance = new StorageAppearance(this);

    private final long max;
    @Getter
    @AdditionalHolder
    private final StorageUpgrades storageUpgrades;

    @SaveToDisk(defaultValue = "-1")
    @SyncToClient
    private long maxAmount = -1;
    @AdditionalHolder
    protected final FluidCache cache;
    @SyncToClient
    private final KeyInventory<AEFluidKey> lockedFluid;
    @Nullable
    @SyncToClient
    protected AEFluidKey storedKey;
    @Getter
    @SyncToClient
    protected long storedAmount;
    @Nullable
    protected TickableSubscription autoOutputSubs;
    protected final TickTimeMonitor autoOutputMonitor = holder.monitorTick(GTTickTimeMonitors.AUTO_OUTPUT, this::checkAutoOutput);

    public QuantumTankMachine(MetaMachineBlockEntity holder, int tier, long maxAmount, Object... args) {
        super(holder, tier);
        this.outputFacingFluids = getFrontFacing().getOpposite();
        this.max = maxAmount;
        this.cache = createCacheFluidHandler(args);
        this.interactionTank = new ForgeFluidAdapter(cache);
        this.lockedFluid = KeyInventory.fluids(1, Long.MAX_VALUE);
        this.lockedFluid.setOnChanged(this::applyLock);
        this.storageUpgrades = new StorageUpgrades(this, this::onFluidChanged, !(this instanceof CreativeTankMachine));
    }

    protected FluidCache createCacheFluidHandler(Object... args) {
        return new FluidCache(this);
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

    protected void onFluidChanged() {
        if (!isRemote()) {
            storedKey = cache.storage.keyAt(0);
            storedAmount = cache.storage.amountAt(0);
            if (getLevel() != null) {
                onChanged();
                updateAutoOutputSubscription();
                requestSync();
            }
        }
    }

    public FluidStack getStored() {
        return Keys.displayFluid(storedKey);
    }

    protected void loadStored(@Nullable AEFluidKey key, long amount) {
        cache.storage.set(0, key, amount);
    }

    @Override
    public boolean savePickClone() {
        return false;
    }

    @Override
    public boolean saveBreak() {
        return cache.storage.amountAt(0) > 0 || storageAppearance.getBlock() != Blocks.AIR || !storageUpgrades.isEmpty();
    }

    @Override
    public void saveCustomPersistedData(CompoundTag tag, boolean forDrop) {
        super.saveCustomPersistedData(tag, forDrop);
        if (forDrop && maxAmount >= 0) tag.putLong(StorageUpgrades.LIMIT_KEY, maxAmount);
        if (!forDrop) {
            var lockedKey = lockedFluid.keyAt(0);
            var locked = lockedKey == null ? FluidStack.EMPTY : Keys.toFluidStack(lockedKey, lockedFluid.amountAt(0));
            tag.put("lockedFluid", locked.writeToNBT(new CompoundTag()));
        }
        var key = cache.storage.keyAt(0);
        var stored = key == null ? FluidStack.EMPTY : Keys.toFluidStack(key, 1000);
        tag.put("stored", stored.writeToNBT(new CompoundTag()));
        tag.putLong("storedAmount", cache.storage.amountAt(0));
    }

    @Override
    public void loadCustomPersistedData(CompoundTag tag) {
        super.loadCustomPersistedData(tag);
        if (tag.contains(StorageUpgrades.LIMIT_KEY)) maxAmount = tag.getLong(StorageUpgrades.LIMIT_KEY);
        var from = tag.contains("cache") ? tag.getCompound("cache") : tag;
        var locked = FluidStack.loadFluidStackFromNBT(from.getCompound("lockedFluid"));
        lockedFluid.set(0, Keys.fluid(locked), locked.getAmount());
        var stored = FluidStack.loadFluidStackFromNBT(tag.getCompound("stored"));
        long storedAmount = tag.contains("storedAmount") ? tag.getLong("storedAmount") : stored.getAmount();
        if (storedAmount == 0 && !stored.isEmpty()) storedAmount = stored.getAmount();
        loadStored(Keys.fluidType(stored), storedAmount);
    }

    //////////////////////////////////////
    // ****** Capability ********//
    //////////////////////////////////////
    @Override
    @Nullable
    public IKeyHandler<AEItemKey> getItemHandlerCap(@Nullable Direction side, boolean useCoverCapability) {
        return null;
    }

    @Override
    @Nullable
    public IKeyHandler<AEFluidKey> getFluidHandlerCap(@Nullable Direction side, boolean useCoverCapability) {
        if (side == getFrontFacing()) {
            return null;
        }
        return super.getFluidHandlerCap(side, useCoverCapability);
    }

    //////////////////////////////////////
    // ******* Auto Output *******//
    //////////////////////////////////////
    @Override
    public void setAutoOutputFluids(boolean allow) {
        this.autoOutputFluids = allow;
        updateAutoOutputSubscription();
    }

    @Override
    public void setOutputFacingFluids(@Nullable Direction outputFacing) {
        clearDirectionCache();
        this.outputFacingFluids = outputFacing;
        updateAutoOutputSubscription();
    }

    @Override
    public boolean isWorkingEnabled() {
        return isAutoOutputFluids();
    }

    @Override
    public void setWorkingEnabled(boolean isWorkingAllowed) {
        setAutoOutputFluids(isWorkingAllowed);
    }

    @Override
    public void onNeighborChanged(Block block, BlockPos fromPos, boolean isMoving) {
        super.onNeighborChanged(block, fromPos, isMoving);
        updateAutoOutputSubscription();
    }

    protected void updateAutoOutputSubscription() {
        var outputFacing = getOutputFacingFluids();
        if ((isAutoOutputFluids() && cache.storage.amountAt(0) > 0) && outputFacing != null && holder.blockEntityDirectionCache.hasAdjacentTarget(getLevel(), getPos(), outputFacing, AEKeyTypes.FLUIDS, StorageAccess.INSERT)) {
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
        if (isAutoOutputFluids() && getOutputFacingFluids() != null) {
            cache.exportToNearby(getOutputFacingFluids());
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
        if (facing == outputFacingFluids) return false;
        return super.isFacingValid(facing);
    }

    @Override
    public InteractionResult onUse(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (ConfigHolder.INSTANCE.machines.superStorageInteraction == ConfigHolder.MachineConfigs.SuperStorageInteraction.GTCEU) {
            if (hit.getDirection() == getFrontFacing() && !world.isClientSide &&
                    FluidUtil.interactWithFluidHandler(player, hand, interactionTank))
                return InteractionResult.SUCCESS;
            return IInteractedMachine.super.onUse(state, world, pos, player, hand, hit);
        }
        if (!StorageInteraction.isDisplayHit(getPos(), getFrontFacing(), hit)) return InteractionResult.PASS;
        if (!world.isClientSide) {
            boolean repeated = interaction.recordUse(player.getUUID(), hand, world.getGameTime());
            var inventory = player.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null);
            if (inventory != null) {
                var held = player.getItemInHand(hand);
                player.setItemInHand(hand, StorageInteraction.transferContainers(held, interactionTank, inventory, player,
                        repeated ? held.getCount() : 1, false));
                if (repeated && cache.storage.keyAt(0) != null) {
                    var items = player.getInventory().items;
                    for (int slot = 0; slot < items.size(); slot++) {
                        var stack = items.get(slot);
                        if (!stack.isEmpty()) {
                            items.set(slot, StorageInteraction.transferContainers(stack, interactionTank, inventory, player,
                                    stack.getCount(), false));
                        }
                    }
                }
                player.getInventory().setChanged();
            }
        }
        return InteractionResult.sidedSuccess(world.isClientSide);
    }

    @Override
    public boolean onLeftClick(Player player, Level world, InteractionHand hand, BlockPos pos, Direction direction) {
        if (ConfigHolder.INSTANCE.machines.superStorageInteraction == ConfigHolder.MachineConfigs.SuperStorageInteraction.GTCEU) {
            return IInteractedMachine.super.onLeftClick(player, world, hand, pos, direction);
        }
        var stack = player.getItemInHand(hand);
        if (direction != getFrontFacing() || stack.is(GTToolType.WRENCH.itemTags.getFirst()) ||
                !stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).isPresent())
            return false;
        if (!(player.pick(player.getBlockReach(), 1, false) instanceof BlockHitResult hit) ||
                !StorageInteraction.isDisplayHit(getPos(), getFrontFacing(), hit))
            return false;
        if (!world.isClientSide && interaction.claimExtraction(world.getGameTime())) {
            var inventory = player.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null);
            if (inventory != null) {
                int count = player.isShiftKeyDown() ? stack.getCount() : 1;
                player.setItemInHand(hand, StorageInteraction.transferContainers(stack, interactionTank, inventory, player,
                        count, true));
                player.getInventory().setChanged();
            }
        }
        return true;
    }

    @Override
    protected InteractionResult onWrenchClick(Player playerIn, InteractionHand hand, Direction gridSide, BlockHitResult hitResult) {
        if (!playerIn.isShiftKeyDown() && !isRemote()) {
            var tool = playerIn.getItemInHand(hand);
            if (tool.getDamageValue() >= tool.getMaxDamage()) return InteractionResult.PASS;
            if (hasFrontFacing() && gridSide == getFrontFacing()) return InteractionResult.PASS;
            if (gridSide != getOutputFacingFluids()) {
                setOutputFacingFluids(gridSide);
            } else {
                setOutputFacingFluids(null);
            }
            return InteractionResult.sidedSuccess(playerIn.level().isClientSide);
        }
        return super.onWrenchClick(playerIn, hand, gridSide, hitResult);
    }

    @Override
    protected InteractionResult onScrewdriverClick(Player playerIn, InteractionHand hand, Direction gridSide, BlockHitResult hitResult) {
        if (!isRemote()) {
            if (gridSide == getOutputFacingFluids()) {
                if (isAllowInputFromOutputSideFluids()) {
                    setAllowInputFromOutputSideFluids(false);
                    playerIn.sendSystemMessage(Component.translatable("gtceu.machine.basic.input_from_output_side.disallow").append(Component.translatable("gtceu.creative.tank.fluid")));
                } else {
                    setAllowInputFromOutputSideFluids(true);
                    playerIn.sendSystemMessage(Component.translatable("gtceu.machine.basic.input_from_output_side.allow").append(Component.translatable("gtceu.creative.tank.fluid")));
                }
            }
            return InteractionResult.SUCCESS;
        }
        return super.onScrewdriverClick(playerIn, hand, gridSide, hitResult);
    }

    public boolean isLocked() {
        return lockedFluid.amountAt(0) > 0;
    }

    protected void setLocked(boolean locked) {
        var key = cache.storage.keyAt(0);
        if (key != null && locked) {
            lockedFluid.set(0, key, LOCK_MARKER);
        } else if (!locked) {
            lockedFluid.set(0, null, 0);
        }
    }

    protected void setLocked(FluidStack fluid) {
        var key = Keys.fluidType(fluid);
        var stored = cache.storage.keyAt(0);
        if (key == null) setLocked(false);
        else if (stored == null) lockedFluid.set(0, key, LOCK_MARKER);
        else if (stored == key) setLocked(true);
    }

    public FluidStack getLockedFluid() {
        return Keys.displayFluid(lockedFluid.keyAt(0));
    }

    private void applyLock() {
        cache.storage.setLocked(0, lockedFluid.keyAt(0));
    }

    //////////////////////////////////////
    // *********** GUI ***********//
    //////////////////////////////////////
    public Widget createUIWidget() {
        var tank = FluidSlot.of(new ForgeFluidAdapter(cache), 0, true, true);
        var lock = IconToggle.of(WidgetIcons.ACCESS_PRIVATE, this::isLocked, this::setLocked)
                .onOffTooltips("gtceu.gui.fluid_lock.tooltip.enabled", "gtceu.gui.fluid_lock.tooltip.disabled");
        var lockSlot = PhantomFluidSlot.of(null, 0, this::getLockedFluid, this::setLocked).xeiPhantom();
        var output = IconToggle.of(WidgetIcons.EXPORT, this::isAutoOutputFluids, this::setAutoOutputFluids)
                .onOffTooltips("gtceu.gui.fluid_auto_output.tooltip.enabled", "gtceu.gui.fluid_auto_output.tooltip.disabled");
        var voiding = IconToggle.of(WidgetIcons.VOID_OVERFLOW, this::isVoiding, this::setVoiding)
                .onOffTooltips("gtceu.gui.fluid_voiding_partial.tooltip.enabled", "gtceu.gui.fluid_voiding_partial.tooltip.disabled");
        var limit = Form.numberRow(HatchViews.CAPACITY_LIMIT,
                NumberField.ofLong(UISizes.CONTENT_WIDTH, this::getMaxAmount, this::setMaxAmount, () -> 1, this::getMax), HatchViews.CAPACITY_LIMIT_TIP);
        limit.layout(l -> l.alignSelf(AlignItems.STRETCH));
        var status = new StatusPanel();
        status.addLine(HatchViews.FLUID, HatchViews.keyName(() -> storedKey, HatchViews.EMPTY));
        status.addLine(HatchViews.AMOUNT, HatchViews.amountText(this::getStoredAmount, this::getMaxAmount, "mB"));
        status.addLine(HatchViews.LOCKED, HatchViews.keyName(() -> lockedFluid.keyAt(0), HatchViews.NOT_LOCKED));
        var operations = UIElement.column(LayoutStyle.AUTO).layout(l -> l.gapAll(UISizes.SECTION_GAP).alignCenter().alignSelf(AlignItems.STRETCH)).addChildren(
                HatchViews.operations(tank, HatchViews.group(lock, lockSlot), HatchViews.group(output, voiding)), limit);
        return HatchViews.page(operations, UIElement.column(LayoutStyle.AUTO).addChildren(status, storageUpgrades.createSlots()));
    }

    public void setVoiding(boolean voiding) {
        isVoiding = voiding;
        onChanged();
        requestSync();
    }

    public long getMax() {
        return storageUpgrades.capacity(max);
    }

    public long getMaxAmount() {
        return maxAmount < 0 ? getMax() : Math.min(maxAmount, getMax());
    }

    public void setMaxAmount(long amount) {
        long maximum = getMax();
        maxAmount = amount >= maximum ? -1 : Math.clamp(amount, 1, maximum);
        onChanged();
        requestSync();
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
            if (side == getOutputFacingFluids()) {
                return GuiTextures.TOOL_ALLOW_INPUT;
            }
        } else if (toolTypes.contains(GTToolType.SOFT_MALLET)) {
            if (side == getFrontFacing()) return null;
        }
        return super.sideTips(player, pos, state, toolTypes, side);
    }

    protected class FluidCache extends MachineTrait implements IKeyHandler<AEFluidKey>, ICapabilityTrait {

        protected final KeyInventory<AEFluidKey> storage = KeyInventory.fluids(1, Long.MAX_VALUE);

        public FluidCache(MetaMachine holder) {
            super(holder);
            storage.setOnChanged(QuantumTankMachine.this::onFluidChanged);
        }

        @Override
        public AEKeyType<AEFluidKey> keyType() {
            return AEKeyTypes.FLUIDS;
        }

        @Override
        public int size() {
            return 1;
        }

        @Override
        public @Nullable AEFluidKey keyAt(int slot) {
            return storage.keyAt(slot);
        }

        @Override
        public long amountAt(int slot) {
            return storage.amountAt(slot);
        }

        @Override
        public long slotLimit(int slot) {
            return getMaxAmount();
        }

        @Override
        public long insert(int slot, AEFluidKey key, long amount, boolean simulate) {
            if (slot != 0 || amount <= 0) return 0;
            var lock = storage.lockedAt(0);
            if (lock != null && lock != key) return 0;
            long stored = storage.amountAt(0);
            if (stored != 0 ? storage.rawKeyAt(0) != key : !storage.acceptsEmpty(0, key)) return 0;
            long space = getMaxAmount() - stored;
            long n = space > 0 ? storage.insert(0, key, Math.min(amount, space), simulate) : 0;
            return isVoiding ? amount : n;
        }

        @Override
        public long extract(int slot, AEFluidKey key, long amount, boolean simulate) {
            return slot == 0 ? storage.extract(0, key, amount, simulate) : 0;
        }

        @Override
        public long insert(AEFluidKey key, long amount, boolean simulate) {
            return insert(0, key, amount, simulate);
        }

        @Override
        public long extract(AEFluidKey key, long amount, boolean simulate) {
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
                var filter = m.getKeyCapFilter(facing, IO.OUT, AEKeyTypes.FLUIDS);
                if (filter != null && !filter.matches(key)) continue;
                if (cache.getAdjacentKeyHandler(level, pos, facing, AEKeyTypes.FLUIDS, StorageAccess.INSERT) instanceof IKeyHandler<?> target) {
                    @SuppressWarnings("unchecked")
                    var to = (IKeyHandler<AEFluidKey>) target;
                    KeyTransfer.transferKey(this, to, key, exportLimit());
                }
            }
        }
    }

    public void setAllowInputFromOutputSideFluids(final boolean allowInputFromOutputSideFluids) {
        clearDirectionCache();
        this.allowInputFromOutputSideFluids = allowInputFromOutputSideFluids;
    }
}
