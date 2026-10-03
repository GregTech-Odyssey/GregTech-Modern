package com.gregtechceu.gtceu.common.machine.storage;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.capability.IControllable;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.widget.LongInputWidget;
import com.gregtechceu.gtceu.api.gui.widget.PhantomFluidWidget;
import com.gregtechceu.gtceu.api.gui.widget.TankWidget;
import com.gregtechceu.gtceu.api.gui.widget.ToggleButtonWidget;
import com.gregtechceu.gtceu.api.item.tool.GTToolType;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.TieredMachine;
import com.gregtechceu.gtceu.api.machine.feature.IAutoOutputFluid;
import com.gregtechceu.gtceu.api.machine.feature.IDropSaveMachine;
import com.gregtechceu.gtceu.api.machine.feature.IFancyUIMachine;
import com.gregtechceu.gtceu.api.machine.feature.IInteractedMachine;
import com.gregtechceu.gtceu.api.machine.trait.ICapabilityTrait;
import com.gregtechceu.gtceu.api.machine.trait.MachineTrait;
import com.gregtechceu.gtceu.api.misc.TickTimeMonitor;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.transfer.forge.ForgeFluidAdapter;
import com.gregtechceu.gtceu.api.transfer.key.IKeyHandler;
import com.gregtechceu.gtceu.api.transfer.key.KeyInventory;
import com.gregtechceu.gtceu.api.transfer.key.KeyTransfer;
import com.gregtechceu.gtceu.api.transfer.key.Keys;
import com.gregtechceu.gtceu.common.data.GTTickTimeMonitors;
import com.gregtechceu.gtceu.utils.*;

import com.lowdragmc.lowdraglib.gui.editor.ColorPattern;
import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;
import com.lowdragmc.lowdraglib.gui.widget.ImageWidget;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;

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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKeyType;
import com.gto.datasynclib.annotations.AdditionalHolder;
import com.gto.datasynclib.annotations.SaveToDisk;
import com.gto.datasynclib.annotations.SyncToClient;
import com.mojang.blaze3d.MethodsReturnNonnullByDefault;
import it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class QuantumTankMachine extends TieredMachine implements IAutoOutputFluid, IInteractedMachine, IControllable, IDropSaveMachine, IFancyUIMachine {

    private static final int LOCK_MARKER = 1000;

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
    @SaveToDisk(defaultValue = "false")
    private boolean isVoiding;

    @Getter
    private final long max;

    @SaveToDisk(defaultValueGetter = "getMax")
    @Getter
    @Setter
    private long maxAmount;
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
        this.maxAmount = maxAmount;
        this.max = maxAmount;
        this.cache = createCacheFluidHandler(args);
        this.lockedFluid = KeyInventory.fluids(1, Long.MAX_VALUE);
        this.lockedFluid.setOnChanged(this::applyLock);
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
        return cache.storage.amountAt(0) > 0;
    }

    @Override
    public void saveCustomPersistedData(CompoundTag tag, boolean forDrop) {
        super.saveCustomPersistedData(tag, forDrop);
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
        if ((isAutoOutputFluids() && cache.storage.amountAt(0) > 0) && outputFacing != null && holder.blockEntityDirectionCache.hasAdjacentFluidHandler(getLevel(), getPos(), outputFacing)) {
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
        if (hit.getDirection() == getFrontFacing() && !isRemote()) {
            if (FluidUtil.interactWithFluidHandler(player, hand, new ForgeFluidAdapter(cache))) {
                return InteractionResult.SUCCESS;
            }
        }
        return IInteractedMachine.super.onUse(state, world, pos, player, hand, hit);
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
        var group = new WidgetGroup(0, 0, 109, 87);
        group.addWidget(new ImageWidget(4, 4, 101, 55, GuiTextures.DISPLAY))
                .addWidget(new LabelWidget(8, 8, "gtceu.gui.fluid_amount"))
                .addWidget(new LabelWidget(8, 18, () -> FormattingUtil.formatBuckets(getStoredAmount())).setTextColor(-1).setDropShadow(false))
                .addWidget(new TankWidget(new ForgeFluidAdapter(cache), 0, 87, 23, true, true).setShowAmount(false).setBackground(GuiTextures.FLUID_SLOT))
                .addWidget(new PhantomFluidWidget(new ForgeFluidAdapter(lockedFluid), 0, 87, 41, 18, 18, this::getLockedFluid, this::setLocked).setShowAmount(false).setBackground(ColorPattern.T_GRAY.rectTexture()))
                .addWidget(new ToggleButtonWidget(4, 41, 18, 18, GuiTextures.BUTTON_FLUID_OUTPUT, this::isAutoOutputFluids, this::setAutoOutputFluids).setShouldUseBaseBackground().setTooltipText("gtceu.gui.fluid_auto_output.tooltip"))
                .addWidget(new ToggleButtonWidget(22, 41, 18, 18, GuiTextures.BUTTON_LOCK, this::isLocked, this::setLocked).setShouldUseBaseBackground().setTooltipText("gtceu.gui.fluid_lock.tooltip"))
                .addWidget(new ToggleButtonWidget(40, 41, 18, 18, GuiTextures.BUTTON_VOID, () -> isVoiding, b -> isVoiding = b).setShouldUseBaseBackground().setTooltipText("gtceu.gui.fluid_voiding_partial.tooltip"));
        group.addWidget(new LongInputWidget(4, 62, 101, 20, this::getMaxAmount, this::setMaxAmount).setMax(max).setMin(1L).setHoverTooltips(Component.translatable("ldlib.gui.editor.name.maxCount")));
        group.setBackground(GuiTextures.BACKGROUND_INVERSE);
        return group;
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
        public AEKeyType keyType() {
            return AEKeyType.fluids();
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
            return maxAmount;
        }

        @Override
        public long insert(int slot, AEFluidKey key, long amount, boolean simulate) {
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

        protected long exportLimit() {
            return Long.MAX_VALUE;
        }

        public void exportToNearby(Direction... facings) {
            var key = storage.keyAt(0);
            if (key == null) return;
            var level = getMachine().getLevel();
            var pos = getMachine().getPos();
            for (Direction facing : facings) {
                var filter = getMachine().getKeyCapFilter(facing, IO.OUT, AEKeyType.fluids());
                if (filter != null && !filter.matches(key)) continue;
                if (holder.blockEntityDirectionCache.getAdjacentKeyHandler(level, pos, facing, AEKeyType.fluids()) instanceof IKeyHandler<?> target) {
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
