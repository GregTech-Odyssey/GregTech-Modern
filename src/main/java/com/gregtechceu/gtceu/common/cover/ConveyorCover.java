package com.gregtechceu.gtceu.common.cover;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.IControllable;
import com.gregtechceu.gtceu.api.capability.ICoverable;
import com.gregtechceu.gtceu.api.cover.CoverBehavior;
import com.gregtechceu.gtceu.api.cover.CoverDefinition;
import com.gregtechceu.gtceu.api.cover.IUICover;
import com.gregtechceu.gtceu.api.cover.filter.FilterHandler;
import com.gregtechceu.gtceu.api.cover.filter.FilterHandlers;
import com.gregtechceu.gtceu.api.cover.filter.ItemFilter;
import com.gregtechceu.gtceu.api.machine.ConditionalSubscriptionHandler;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.transfer.key.IKeyHandler;
import com.gregtechceu.gtceu.api.transfer.key.KeyHandlerView;
import com.gregtechceu.gtceu.api.transfer.key.KeyTransfer;
import com.gregtechceu.gtceu.api.transfer.key.Keys;
import com.gregtechceu.gtceu.common.blockentity.ItemPipeBlockEntity;
import com.gregtechceu.gtceu.common.cover.data.DistributionMode;
import com.gregtechceu.gtceu.common.cover.data.ManualIOMode;
import com.gregtechceu.gtceu.uipro.LayoutStyle;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.elements.Form;
import com.gregtechceu.gtceu.uipro.elements.NumberField;
import com.gregtechceu.gtceu.uiwidgets.cover.CoverUIs;

import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.AEKeyFilter;
import com.gto.datasynclib.annotations.SaveToDisk;
import com.gto.datasynclib.annotations.SyncToClient;
import it.unimi.dsi.fastutil.ints.Int2IntFunction;
import it.unimi.dsi.fastutil.objects.Reference2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2LongMap;
import it.unimi.dsi.fastutil.objects.Reference2LongMaps;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class ConveyorCover extends CoverBehavior implements IUICover, IControllable {

    // 8 32 128 512 1024
    public static final Int2IntFunction CONVEYOR_SCALING = tier -> 2 * (int) Math.pow(4, Math.min(tier, GTValues.LuV));
    public final int tier;
    public final int maxItemTransferRate;
    @Getter
    @SaveToDisk
    protected int transferRate;
    @Getter
    @SaveToDisk
    @SyncToClient(scheduleUpdate = true)
    protected IO io;
    @Getter
    @SaveToDisk
    @SyncToClient
    protected DistributionMode distributionMode;
    @Getter
    @SaveToDisk
    @SyncToClient
    protected ManualIOMode manualIOMode = ManualIOMode.DISABLED;
    @Getter
    @SaveToDisk
    @SyncToClient
    protected boolean isWorkingEnabled = true;
    @Getter
    @SaveToDisk
    @SyncToClient
    protected final FilterHandler<ItemStack, ItemFilter> filterHandler;
    protected final ConditionalSubscriptionHandler subscriptionHandler;
    protected final AEKeyFilter itemKeyFilter = this::matchesFilter;
    private final KeyCounter countScratch = new KeyCounter();
    @Nullable
    private Reference2LongLinkedOpenHashMap<AEItemKey> countsA;
    @Nullable
    private Reference2LongLinkedOpenHashMap<AEItemKey> countsB;
    private boolean countFlip;

    public ConveyorCover(CoverDefinition definition, ICoverable coverHolder, Direction attachedSide, int tier, int maxTransferRate) {
        super(definition, coverHolder, attachedSide);
        this.tier = tier;
        this.maxItemTransferRate = maxTransferRate;
        this.transferRate = maxItemTransferRate;
        this.io = IO.OUT;
        this.distributionMode = DistributionMode.INSERT_FIRST;
        // 不用 tick 耗时监控：同一个方块实体上可能挂多个 cover，按 entry 缓存的监控器会互相顶掉（先注册的
        // task 生效），这里保持各自的 Runnable。
        subscriptionHandler = new ConditionalSubscriptionHandler(coverHolder, this::update, 20, this::isSubscriptionActive);
        filterHandler = FilterHandlers.item(this).onFilterLoaded(f -> configureFilter()).onFilterUpdated(f -> configureFilter()).onFilterRemoved(f -> configureFilter());
    }

    public ConveyorCover(CoverDefinition definition, ICoverable coverHolder, Direction attachedSide, int tier) {
        this(definition, coverHolder, attachedSide, tier, CONVEYOR_SCALING.applyAsInt(tier));
    }

    protected boolean isSubscriptionActive() {
        return isWorkingEnabled() && getAdjacentItemHandler() != null;
    }

    @Nullable
    protected IKeyHandler<AEItemKey> getOwnItemHandler() {
        return coverHolder.getItemHandlerCap(attachedSide, false);
    }

    @Nullable
    @SuppressWarnings("unchecked")
    protected IKeyHandler<AEItemKey> getAdjacentItemHandler() {
        return (IKeyHandler<AEItemKey>) coverHolder.getBlockEntityDirectionCache().getAdjacentKeyHandler(coverHolder.getLevel(), coverHolder.getPos(), attachedSide, AEKeyTypes.ITEMS, io.neighbourAccess());
    }

    //////////////////////////////////////
    // ***** Initialization ******//
    //////////////////////////////////////

    @Override
    public boolean canAttach() {
        return super.canAttach() && getOwnItemHandler() != null;
    }

    public void setTransferRate(int transferRate) {
        if (transferRate <= maxItemTransferRate) {
            this.transferRate = transferRate;
            coverHolder.onChanged();
        }
    }

    public void setIo(IO io) {
        if (io == IO.IN || io == IO.OUT) {
            this.io = io;
        }
        subscriptionHandler.updateSubscription();
        coverHolder.onChanged();
    }

    public void setDistributionMode(DistributionMode distributionMode) {
        this.distributionMode = distributionMode;
        coverHolder.onChanged();
    }

    protected void setManualIOMode(ManualIOMode manualIOMode) {
        this.manualIOMode = manualIOMode;
        coverHolder.onChanged();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        subscriptionHandler.initialize(coverHolder.getLevel());
    }

    @Override
    public void onRemoved() {
        super.onRemoved();
        subscriptionHandler.unsubscribe();
    }

    @Override
    public List<ItemStack> getAdditionalDrops() {
        var list = super.getAdditionalDrops();
        if (!filterHandler.getFilterItem().isEmpty()) {
            list.add(filterHandler.getFilterItem());
        }
        return list;
    }

    //////////////////////////////////////
    // ***** Transfer Logic *****//
    //////////////////////////////////////
    @Override
    public void onNeighborChanged(Block block, BlockPos fromPos, boolean isMoving) {
        subscriptionHandler.updateSubscription();
    }

    @Override
    public void setWorkingEnabled(boolean isWorkingAllowed) {
        if (this.isWorkingEnabled != isWorkingAllowed) {
            this.isWorkingEnabled = isWorkingAllowed;
            subscriptionHandler.updateSubscription();
        }
    }

    protected void update() {
        var adjacent = this.getAdjacentItemHandler();
        var self = this.getOwnItemHandler();
        if (adjacent != null && self != null) {
            switch (this.io) {
                case IN -> this.doTransferItems(adjacent, self, this.transferRate);
                case OUT -> this.doTransferItems(self, adjacent, this.transferRate);
            }
        }
        this.subscriptionHandler.updateSubscription();
    }

    protected int doTransferItems(IKeyHandler<AEItemKey> sourceInventory, IKeyHandler<AEItemKey> targetInventory, int maxTransferAmount) {
        return moveInventoryItems(sourceInventory, targetInventory, maxTransferAmount);
    }

    protected int moveInventoryItems(IKeyHandler<AEItemKey> sourceInventory, IKeyHandler<AEItemKey> targetInventory, int maxTransferAmount) {
        return (int) KeyTransfer.transfer(sourceInventory, targetInventory, maxTransferAmount, itemKeyFilter);
    }

    protected static int moveInventoryItemsExact(IKeyHandler<AEItemKey> sourceInventory, IKeyHandler<AEItemKey> targetInventory, AEItemKey key, long total) {
        if (total <= 0 || sourceInventory.extract(key, total, true) != total || targetInventory.insert(key, total, true) != total) {
            return 0;
        }
        long extracted = sourceInventory.extract(key, total, false);
        if (extracted != total) {
            if (extracted > 0) sourceInventory.unrestricted().insert(key, extracted, false);
            return 0;
        }
        long inserted = targetInventory.insert(key, total, false);
        if (inserted < total) sourceInventory.unrestricted().insert(key, total - inserted, false);
        return (int) inserted;
    }

    protected int moveInventoryItems(IKeyHandler<AEItemKey> sourceInventory, IKeyHandler<AEItemKey> targetInventory, Reference2LongMap<AEItemKey> itemAmounts, int maxTransferAmount) {
        long left = maxTransferAmount;
        for (var it = Reference2LongMaps.fastIterator(itemAmounts); it.hasNext() && left > 0;) {
            var entry = it.next();
            left -= KeyTransfer.transferKey(sourceInventory, targetInventory, entry.getKey(), Math.min(entry.getLongValue(), left));
        }
        return (int) (maxTransferAmount - left);
    }

    protected Reference2LongLinkedOpenHashMap<AEItemKey> countInventoryItems(IKeyHandler<AEItemKey> inventory) {
        var result = (countFlip = !countFlip) ? countsA : countsB;
        if (result == null) {
            result = new Reference2LongLinkedOpenHashMap<>();
            if (countFlip) countsA = result;
            else countsB = result;
        } else {
            result.clear();
        }
        var counter = countScratch;
        inventory.getAvailableStacks(counter);
        for (var entry : counter) {
            long amount = entry.getLongValue();
            if (amount > 0 && entry.getKey() instanceof AEItemKey key && itemKeyFilter.matches(key)) {
                result.put(key, Keys.add(result.getLong(key), amount));
            }
        }
        counter.clear();
        return result;
    }

    //////////////////////////////////////
    // *********** GUI ***********//
    //////////////////////////////////////
    private static final Component TRANSFER_SECTION = Component.translatable("cover.ui.transfer");
    private static final Component TRANSFER_RATE = Component.translatable("cover.conveyor.ui.transfer_rate");
    private static final Component IO_TITLE = Component.translatable("cover.ui.io");
    private static final Component IO_TOOLTIP = Component.translatable("cover.conveyor.ui.io.tooltip");
    private static final Component DISTRIBUTION = Component.translatable("cover.conveyor.ui.distribution");
    private static final Component DISTRIBUTION_NO_PIPE = Component.translatable("cover.conveyor.ui.distribution_no_pipe");
    private static final Component MODES_SECTION = Component.translatable("cover.ui.modes");
    private static final Component MANUAL_IO_TITLE = Component.translatable("cover.ui.manual_io");
    private static final Component[] MANUAL_IO_TOOLTIPS = {
            Component.translatable("cover.universal.manual_import_export.mode.description.0"),
            Component.translatable("cover.universal.manual_import_export.mode.description.1"),
            Component.translatable("cover.universal.manual_import_export.mode.description.2")
    };

    @Override
    public Widget createUIWidget() {
        var transfer = Form.section(TRANSFER_SECTION).addChildren(
                Form.numberRow(TRANSFER_RATE,
                        NumberField.ofInt(LayoutStyle.AUTO, () -> transferRate, this::setTransferRate, 1, maxItemTransferRate)),
                CoverUIs.enumRow(IO_TITLE, List.of(IO.IN, IO.OUT), this::getIo, this::setIo, IO_TOOLTIP));
        buildAdditionalUI(transfer);
        var distribution = CoverUIs.enumRow(DISTRIBUTION, List.of(DistributionMode.VALUES),
                this::getDistributionMode, this::setDistributionMode)
                .disabled(() -> !shouldDisplayDistributionMode(), DISTRIBUTION_NO_PIPE);
        var modes = Form.section(MODES_SECTION).addChildren(distribution,
                CoverUIs.enumRow(MANUAL_IO_TITLE, List.of(ManualIOMode.VALUES), this::getManualIOMode, this::setManualIOMode,
                        MANUAL_IO_TOOLTIPS));
        return Form.page().addChildren(transfer, modes, CoverUIs.filterSection(filterHandler));
    }

    private boolean shouldDisplayDistributionMode() {
        return coverHolder.holder() instanceof ItemPipeBlockEntity || getNeighbor() instanceof ItemPipeBlockEntity;
    }

    protected void buildAdditionalUI(UIElement section) {}

    protected void configureFilter() {}

    /////////////////////////////////////
    // *** CAPABILITY OVERRIDE ***//
    /////////////////////////////////////
    private CoverableItemHandlerWrapper itemHandlerWrapper;

    @Nullable
    @Override
    public IKeyHandler<AEItemKey> getItemHandlerCap(@Nullable IKeyHandler<AEItemKey> defaultValue) {
        if (defaultValue == null) {
            return null;
        }
        if (itemHandlerWrapper == null || itemHandlerWrapper.getDelegate() != defaultValue) {
            this.itemHandlerWrapper = new CoverableItemHandlerWrapper(defaultValue);
        }
        return itemHandlerWrapper;
    }

    private class CoverableItemHandlerWrapper extends KeyHandlerView<AEItemKey> {

        public CoverableItemHandlerWrapper(IKeyHandler<AEItemKey> delegate) {
            super(delegate);
        }

        @Override
        protected boolean canInsert(AEItemKey key) {
            if (io == IO.OUT && manualIOMode == ManualIOMode.DISABLED) {
                return false;
            }
            return manualIOMode != ManualIOMode.FILTERED || filterHandler.test(key.getReadOnlyStack());
        }

        @Override
        protected boolean canExtract(AEItemKey key) {
            if (io == IO.IN && manualIOMode == ManualIOMode.DISABLED) {
                return false;
            }
            return manualIOMode != ManualIOMode.FILTERED || filterHandler.test(key.getReadOnlyStack());
        }
    }

    private boolean matchesFilter(AEKey key) {
        return key instanceof AEItemKey k && filterHandler.test(k.getReadOnlyStack());
    }
}
