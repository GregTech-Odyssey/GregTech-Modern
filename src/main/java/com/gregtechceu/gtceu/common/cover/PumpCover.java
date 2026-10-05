package com.gregtechceu.gtceu.common.cover;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.IControllable;
import com.gregtechceu.gtceu.api.capability.ICoverable;
import com.gregtechceu.gtceu.api.cover.CoverBehavior;
import com.gregtechceu.gtceu.api.cover.CoverDefinition;
import com.gregtechceu.gtceu.api.cover.IUICover;
import com.gregtechceu.gtceu.api.cover.filter.FilterHandler;
import com.gregtechceu.gtceu.api.cover.filter.FilterHandlers;
import com.gregtechceu.gtceu.api.cover.filter.FluidFilter;
import com.gregtechceu.gtceu.api.machine.ConditionalSubscriptionHandler;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.transfer.key.IKeyHandler;
import com.gregtechceu.gtceu.api.transfer.key.KeyHandlerView;
import com.gregtechceu.gtceu.api.transfer.key.KeyTransfer;
import com.gregtechceu.gtceu.api.transfer.key.Keys;
import com.gregtechceu.gtceu.common.cover.data.BucketMode;
import com.gregtechceu.gtceu.common.cover.data.ManualIOMode;
import com.gregtechceu.gtceu.uipro.LayoutStyle;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.elements.Form;
import com.gregtechceu.gtceu.uipro.elements.NumberField;
import com.gregtechceu.gtceu.uipro.elements.TextLine;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;
import com.gregtechceu.gtceu.uiwidgets.cover.CoverUIs;

import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.fluids.FluidStack;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.storage.AEKeyFilter;
import com.gto.datasynclib.annotations.SaveToDisk;
import com.gto.datasynclib.annotations.SyncToClient;
import it.unimi.dsi.fastutil.ints.Int2IntFunction;
import it.unimi.dsi.fastutil.objects.Reference2LongLinkedOpenHashMap;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class PumpCover extends CoverBehavior implements IUICover, IControllable {

    // .5b 2b 8b
    public static final Int2IntFunction PUMP_SCALING = tier -> (int) (64D * Math.pow(4, Math.min(tier - 1, GTValues.IV)));
    public final int tier;
    public final int maxFluidTransferRate;
    @Getter
    @SaveToDisk
    @SyncToClient
    protected int transferRate;
    @Getter
    @SaveToDisk
    @SyncToClient(scheduleUpdate = true)
    protected IO io = IO.OUT;
    @Getter
    @SaveToDisk
    @SyncToClient
    protected BucketMode bucketMode = BucketMode.MILLI_BUCKET;
    @Getter
    @SaveToDisk
    @SyncToClient
    protected ManualIOMode manualIOMode = ManualIOMode.DISABLED;
    @Getter
    @SaveToDisk
    @SyncToClient
    protected boolean isWorkingEnabled = true;
    @SaveToDisk
    @SyncToClient
    protected final FilterHandler<FluidStack, FluidFilter> filterHandler;
    protected final ConditionalSubscriptionHandler subscriptionHandler;
    protected final AEKeyFilter fluidKeyFilter = this::matchesFilter;
    @Nullable
    private Reference2LongLinkedOpenHashMap<AEFluidKey> fluidsA;
    @Nullable
    private Reference2LongLinkedOpenHashMap<AEFluidKey> fluidsB;
    private boolean fluidFlip;

    public PumpCover(CoverDefinition definition, ICoverable coverHolder, Direction attachedSide, int tier, int maxTransferRate) {
        super(definition, coverHolder, attachedSide);
        this.tier = tier;
        this.maxFluidTransferRate = maxTransferRate;
        this.transferRate = maxFluidTransferRate;
        // 不用 tick 耗时监控：同一个方块实体上可能挂多个 cover，按 entry 缓存的监控器会互相顶掉（先注册的
        // task 生效），这里保持各自的 Runnable。
        subscriptionHandler = new ConditionalSubscriptionHandler(coverHolder, this::update, 20, this::isSubscriptionActive);
        filterHandler = FilterHandlers.fluid(this).onFilterLoaded(f -> configureFilter()).onFilterUpdated(f -> configureFilter()).onFilterRemoved(f -> configureFilter());
    }

    public PumpCover(CoverDefinition definition, ICoverable coverHolder, Direction attachedSide, int tier) {
        this(definition, coverHolder, attachedSide, tier, PUMP_SCALING.applyAsInt(tier));
    }

    protected boolean isSubscriptionActive() {
        return isWorkingEnabled() && getAdjacentFluidHandler() != null;
    }

    @Nullable
    protected IKeyHandler<AEFluidKey> getOwnFluidHandler() {
        return coverHolder.getFluidHandlerCap(attachedSide, false);
    }

    @Nullable
    @SuppressWarnings("unchecked")
    protected IKeyHandler<AEFluidKey> getAdjacentFluidHandler() {
        return (IKeyHandler<AEFluidKey>) coverHolder.getBlockEntityDirectionCache().getAdjacentKeyHandler(coverHolder.getLevel(), coverHolder.getPos(), attachedSide, AEKeyTypes.FLUIDS, io.neighbourAccess());
    }

    //////////////////////////////////////
    // ***** Initialization ******//
    //////////////////////////////////////

    @Override
    public boolean canAttach() {
        return super.canAttach() && getOwnFluidHandler() != null;
    }

    public void setIo(IO io) {
        if (io == IO.IN || io == IO.OUT) {
            this.io = io;
        }
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

    //////////////////////////////////////
    // ***** Transfer Logic *****//
    //////////////////////////////////////
    public void setTransferRate(int milliBucketsPerTick) {
        this.transferRate = Math.min(Math.max(milliBucketsPerTick, 0), maxFluidTransferRate);
    }

    public void setBucketMode(BucketMode bucketMode) {
        this.bucketMode = bucketMode;
    }

    protected void setManualIOMode(ManualIOMode manualIOMode) {
        this.manualIOMode = manualIOMode;
        coverHolder.onChanged();
    }

    protected void update() {
        doTransferFluids(transferRate * 20);
        subscriptionHandler.updateSubscription();
    }

    private int doTransferFluids(int platformTransferLimit) {
        var adjacent = getAdjacentFluidHandler();
        var ownFluidHandler = getOwnFluidHandler();
        if (adjacent != null && ownFluidHandler != null) {
            return switch (io) {
                case IN -> doTransferFluidsInternal(adjacent, ownFluidHandler, platformTransferLimit);
                case OUT -> doTransferFluidsInternal(ownFluidHandler, adjacent, platformTransferLimit);
                default -> 0;
            };
        }
        return 0;
    }

    protected int doTransferFluidsInternal(IKeyHandler<AEFluidKey> source, IKeyHandler<AEFluidKey> destination, int platformTransferLimit) {
        return transferAny(source, destination, platformTransferLimit);
    }

    protected int transferAny(IKeyHandler<AEFluidKey> source, IKeyHandler<AEFluidKey> destination, int platformTransferLimit) {
        return (int) KeyTransfer.transfer(source, destination, platformTransferLimit, fluidKeyFilter);
    }

    protected Reference2LongLinkedOpenHashMap<AEFluidKey> enumerateDistinctFluids(IKeyHandler<AEFluidKey> fluidHandler) {
        var summedFluids = (fluidFlip = !fluidFlip) ? fluidsA : fluidsB;
        if (summedFluids == null) {
            summedFluids = new Reference2LongLinkedOpenHashMap<>();
            if (fluidFlip) fluidsA = summedFluids;
            else fluidsB = summedFluids;
        } else {
            summedFluids.clear();
        }
        int size = fluidHandler.size();
        for (int tank = 0; tank < size; tank++) {
            long amount = fluidHandler.amountAt(tank);
            if (amount <= 0) continue;
            var key = fluidHandler.keyAt(tank);
            summedFluids.put(key, Keys.add(summedFluids.getLong(key), amount));
        }
        return summedFluids;
    }

    //////////////////////////////////////
    // *********** GUI ***********//
    //////////////////////////////////////
    @Override
    public Widget createUIWidget() {
        var page = Form.page().addChildren(createTransferSection(), createModeSection());
        buildAdditionalUI(page);
        return hasFilterUI() ? page.addChild(CoverUIs.filterSection(filterHandler)) : page;
    }

    protected boolean hasFilterUI() {
        return true;
    }

    private UIElement createTransferSection() {
        var modes = Arrays.stream(BucketMode.values()).filter(m -> m.multiplier <= maxFluidTransferRate).toList();
        var field = NumberField.ofInt(LayoutStyle.AUTO, this::getCurrentBucketModeTransferRate, this::setCurrentBucketModeTransferRate,
                () -> 0, () -> maxFluidTransferRate / bucketMode.multiplier);
        return Form.section("cover.ui.transfer").addChild(fluidAmountRow(
                () -> Component.translatable("cover.pump.ui.transfer_rate", Component.translatable(bucketMode.getTooltip())),
                modes, this::getBucketMode, this::setBucketMode, field));
    }

    private UIElement createModeSection() {
        return Form.section("cover.ui.modes").addChildren(
                CoverUIs.enumRow("cover.ui.io", List.of(IO.IN, IO.OUT), this::getIo, this::setIo),
                CoverUIs.enumRow("cover.ui.manual_io", List.of(ManualIOMode.VALUES), this::getManualIOMode, this::setManualIOMode,
                        "cover.universal.manual_import_export.mode.description.0",
                        "cover.universal.manual_import_export.mode.description.1",
                        "cover.universal.manual_import_export.mode.description.2"));
    }

    protected static UIElement fluidAmountRow(Supplier<Component> label, List<BucketMode> modes, Supplier<BucketMode> current,
                                              Consumer<BucketMode> set, NumberField field, String... tooltipKeys) {
        var text = TextLine.of(0, label).bindClientColor(UITheme::panelText);
        text.layout(l -> l.flex(1));
        if (tooltipKeys.length > 0) text.tooltips(tooltipKeys);
        boolean selectable = modes.size() > 1;
        var head = UIElement.row(selectable ? UISizes.SLOT_SIZE : UISizes.TEXT_HEIGHT).layout(l -> l.gapAll(UISizes.GAP).alignCenter()).addChild(text);
        if (selectable) head.addChild(CoverUIs.enumIcons(modes, current, set));
        return UIElement.column(LayoutStyle.AUTO).layout(l -> l.gapAll(UISizes.GAP)).addChildren(head, field);
    }

    private int getCurrentBucketModeTransferRate() {
        return this.transferRate / this.bucketMode.multiplier;
    }

    private void setCurrentBucketModeTransferRate(int transferRate) {
        this.setTransferRate(transferRate * this.bucketMode.multiplier);
    }

    protected void buildAdditionalUI(UIElement page) {
        // Do nothing in the base implementation. This is intended to be overridden by subclasses.
    }

    protected void configureFilter() {
        // Do nothing in the base implementation. This is intended to be overridden by subclasses.
    }

    /////////////////////////////////////
    // *** CAPABILITY OVERRIDE ***//
    /////////////////////////////////////
    private CoverableFluidHandlerWrapper fluidHandlerWrapper;

    @Nullable
    @Override
    public IKeyHandler<AEFluidKey> getFluidHandlerCap(@Nullable IKeyHandler<AEFluidKey> defaultValue) {
        if (defaultValue == null) {
            return null;
        }
        if (fluidHandlerWrapper == null || fluidHandlerWrapper.getDelegate() != defaultValue) {
            this.fluidHandlerWrapper = new CoverableFluidHandlerWrapper(defaultValue);
        }
        return fluidHandlerWrapper;
    }

    private class CoverableFluidHandlerWrapper extends KeyHandlerView<AEFluidKey> {

        public CoverableFluidHandlerWrapper(IKeyHandler<AEFluidKey> delegate) {
            super(delegate);
        }

        @Override
        protected boolean canInsert(AEFluidKey key) {
            if (io == IO.OUT && manualIOMode == ManualIOMode.DISABLED) {
                return false;
            }
            return manualIOMode != ManualIOMode.FILTERED || filterHandler.test(key.getReadOnlyStack());
        }

        @Override
        protected boolean canExtract(AEFluidKey key) {
            if (io == IO.IN && manualIOMode == ManualIOMode.DISABLED) {
                return false;
            }
            return manualIOMode != ManualIOMode.FILTERED || filterHandler.test(key.getReadOnlyStack());
        }
    }

    private boolean matchesFilter(AEKey key) {
        return key instanceof AEFluidKey k && filterHandler.test(k.getReadOnlyStack());
    }
}
