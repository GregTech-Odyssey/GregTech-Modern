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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.storage.AEKeyFilter;
import com.gto.datasynclib.annotations.SaveToDisk;
import com.gto.datasynclib.annotations.SyncToClient;
import it.unimi.dsi.fastutil.ints.Int2IntFunction;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectLinkedOpenHashMap;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

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
        return (IKeyHandler<AEItemKey>) coverHolder.getBlockEntityDirectionCache().getAdjacentKeyHandler(coverHolder.getLevel(), coverHolder.getPos(), attachedSide, AEKeyType.items());
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

    protected static int moveInventoryItemsExact(IKeyHandler<AEItemKey> sourceInventory, IKeyHandler<AEItemKey> targetInventory,
                                                 TypeItemInfo itemInfo) {
        AEItemKey key = itemInfo.key;
        long total = itemInfo.totalCount;
        IntList slots = itemInfo.slots;
        long left = total;
        for (int i = 0; i < slots.size() && left > 0; i++) {
            left -= sourceInventory.extract(slots.getInt(i), key, left, true);
        }
        if (left != 0 || targetInventory.insert(key, total, true) != total) {
            return 0;
        }
        left = total;
        for (int i = 0; i < slots.size() && left > 0; i++) {
            left -= sourceInventory.extract(slots.getInt(i), key, left, false);
        }
        long extracted = total - left;
        if (extracted != total) {
            if (extracted > 0) returnToSource(sourceInventory, slots.getInt(0), key, extracted);
            return 0;
        }
        long inserted = targetInventory.insert(key, total, false);
        if (inserted < total) returnToSource(sourceInventory, slots.getInt(0), key, total - inserted);
        return (int) inserted;
    }

    private static void returnToSource(IKeyHandler<AEItemKey> sourceInventory, int slot, AEItemKey key, long amount) {
        var source = sourceInventory.unrestricted();
        long back = source.insert(slot, key, amount, false);
        if (back < amount) source.insert(key, amount - back, false);
    }

    protected int moveInventoryItems(IKeyHandler<AEItemKey> sourceInventory, IKeyHandler<AEItemKey> targetInventory, Map<AEItemKey, GroupItemInfo> itemInfos, int maxTransferAmount) {
        long itemsLeftToTransfer = maxTransferAmount;
        int size = sourceInventory.size();
        for (int i = 0; i < size; i++) {
            if (sourceInventory.amountAt(i) <= 0) continue;
            AEItemKey key = sourceInventory.keyAt(i);
            if (key == null || !itemKeyFilter.matches(key)) continue;
            GroupItemInfo itemInfo = itemInfos.get(key);
            if (itemInfo == null) continue;
            long transferred = KeyTransfer.transferSlot(sourceInventory, i, targetInventory,
                    Math.min(itemInfo.totalCount, itemsLeftToTransfer), null);
            if (transferred > 0) {
                itemsLeftToTransfer -= transferred;
                itemInfo.totalCount -= transferred;
                if (itemInfo.totalCount == 0) {
                    itemInfos.remove(key);
                    if (itemInfos.isEmpty()) {
                        break;
                    }
                }
                if (itemsLeftToTransfer == 0) {
                    break;
                }
            }
        }
        return (int) (maxTransferAmount - itemsLeftToTransfer);
    }

    protected Reference2ObjectLinkedOpenHashMap<AEItemKey, TypeItemInfo> countInventoryItemsByType(IKeyHandler<AEItemKey> inventory) {
        var result = new Reference2ObjectLinkedOpenHashMap<AEItemKey, TypeItemInfo>();
        int size = inventory.size();
        for (int srcIndex = 0; srcIndex < size; srcIndex++) {
            long amount = inventory.amountAt(srcIndex);
            if (amount <= 0) continue;
            AEItemKey key = inventory.keyAt(srcIndex);
            if (key == null || !itemKeyFilter.matches(key)) continue;
            var itemInfo = result.get(key);
            if (itemInfo == null) {
                itemInfo = new TypeItemInfo(key, new IntArrayList(), 0);
                result.put(key, itemInfo);
            }
            itemInfo.totalCount = Keys.add(itemInfo.totalCount, amount);
            itemInfo.slots.add(srcIndex);
        }
        return result;
    }

    protected Reference2ObjectLinkedOpenHashMap<AEItemKey, GroupItemInfo> countInventoryItemsByMatchSlot(IKeyHandler<AEItemKey> inventory) {
        var result = new Reference2ObjectLinkedOpenHashMap<AEItemKey, GroupItemInfo>();
        int size = inventory.size();
        for (int srcIndex = 0; srcIndex < size; srcIndex++) {
            long amount = inventory.amountAt(srcIndex);
            if (amount <= 0) continue;
            AEItemKey key = inventory.keyAt(srcIndex);
            if (key == null || !itemKeyFilter.matches(key)) continue;
            var itemInfo = result.get(key);
            if (itemInfo == null) {
                itemInfo = new GroupItemInfo(key, 0);
                result.put(key, itemInfo);
            }
            itemInfo.totalCount = Keys.add(itemInfo.totalCount, amount);
        }
        return result;
    }

    protected static class TypeItemInfo {

        public final AEItemKey key;
        public final IntList slots;
        public long totalCount;

        public TypeItemInfo(final AEItemKey key, final IntList slots, final long totalCount) {
            this.key = key;
            this.slots = slots;
            this.totalCount = totalCount;
        }
    }

    protected static class GroupItemInfo {

        public final AEItemKey key;
        public long totalCount;

        public GroupItemInfo(final AEItemKey key, final long totalCount) {
            this.key = key;
            this.totalCount = totalCount;
        }
    }

    //////////////////////////////////////
    // *********** GUI ***********//
    //////////////////////////////////////
    @Override
    public Widget createUIWidget() {
        var transfer = Form.section("cover.ui.transfer").addChildren(
                Form.numberRow("cover.conveyor.ui.transfer_rate",
                        NumberField.ofInt(LayoutStyle.AUTO, () -> transferRate, this::setTransferRate, 1, maxItemTransferRate)),
                CoverUIs.enumRow("cover.ui.io", List.of(IO.IN, IO.OUT), this::getIo, this::setIo, "cover.conveyor.ui.io.tooltip"));
        buildAdditionalUI(transfer);
        var distribution = CoverUIs.enumRow("cover.conveyor.ui.distribution", List.of(DistributionMode.VALUES),
                this::getDistributionMode, this::setDistributionMode)
                .disabled(() -> !shouldDisplayDistributionMode(), "cover.conveyor.ui.distribution_no_pipe");
        var modes = Form.section("cover.ui.modes").addChildren(distribution,
                CoverUIs.enumRow("cover.ui.manual_io", List.of(ManualIOMode.VALUES), this::getManualIOMode, this::setManualIOMode,
                        "cover.universal.manual_import_export.mode.description.0",
                        "cover.universal.manual_import_export.mode.description.1",
                        "cover.universal.manual_import_export.mode.description.2"));
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
