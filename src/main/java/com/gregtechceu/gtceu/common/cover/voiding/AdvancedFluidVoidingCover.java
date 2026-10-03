package com.gregtechceu.gtceu.common.cover.voiding;

import com.gregtechceu.gtceu.api.capability.ICoverable;
import com.gregtechceu.gtceu.api.cover.CoverDefinition;
import com.gregtechceu.gtceu.api.cover.filter.FluidFilter;
import com.gregtechceu.gtceu.api.cover.filter.SimpleFluidFilter;
import com.gregtechceu.gtceu.api.transfer.key.IKeyHandler;
import com.gregtechceu.gtceu.common.cover.data.BucketMode;
import com.gregtechceu.gtceu.common.cover.data.VoidingMode;
import com.gregtechceu.gtceu.uipro.LayoutStyle;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.elements.Form;
import com.gregtechceu.gtceu.uipro.elements.NumberField;
import com.gregtechceu.gtceu.uiwidgets.cover.CoverUIs;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fluids.FluidStack;

import appeng.api.stacks.AEFluidKey;
import com.gto.datasynclib.annotations.SaveToDisk;
import com.gto.datasynclib.annotations.SyncToClient;
import it.unimi.dsi.fastutil.objects.Reference2LongMaps;

import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public class AdvancedFluidVoidingCover extends FluidVoidingCover {

    @SaveToDisk
    @SyncToClient
    private VoidingMode voidingMode = VoidingMode.VOID_ANY;
    @SaveToDisk
    @SyncToClient
    protected int globalTransferSizeMillibuckets = 1;
    @SaveToDisk
    @SyncToClient
    private BucketMode transferBucketMode = BucketMode.MILLI_BUCKET;

    public AdvancedFluidVoidingCover(CoverDefinition definition, ICoverable coverHolder, Direction attachedSide) {
        super(definition, coverHolder, attachedSide);
    }

    //////////////////////////////////////////////
    // *********** COVER LOGIC ***********//
    //////////////////////////////////////////////
    @Override
    protected void doVoidFluids() {
        IKeyHandler<AEFluidKey> fluidHandler = getOwnFluidHandler();
        if (fluidHandler == null) {
            return;
        }
        switch (voidingMode) {
            case VOID_ANY -> voidAny(fluidHandler);
            case VOID_OVERFLOW -> voidOverflow(fluidHandler);
        }
    }

    private void voidOverflow(IKeyHandler<AEFluidKey> fluidHandler) {
        var fluidAmounts = enumerateDistinctFluids(fluidHandler);
        for (var entry : Reference2LongMaps.fastIterable(fluidAmounts)) {
            var key = entry.getKey();
            long presentAmount = entry.getLongValue();
            int targetAmount = getFilteredFluidAmount(key.getReadOnlyStack());
            if (targetAmount <= 0L || targetAmount > presentAmount) continue;
            fluidHandler.extract(key, presentAmount - targetAmount, false);
        }
    }

    private int getFilteredFluidAmount(FluidStack fluidStack) {
        if (!filterHandler.isFilterPresent()) return globalTransferSizeMillibuckets;
        FluidFilter filter = filterHandler.getFilter();
        return filter.isBlackList() ? globalTransferSizeMillibuckets : filter.testFluidAmount(fluidStack);
    }

    public void setVoidingMode(VoidingMode voidingMode) {
        this.voidingMode = voidingMode;
        if (!this.isRemote()) {
            configureFilter();
        }
    }

    private void setTransferBucketMode(BucketMode transferBucketMode) {
        this.transferBucketMode = transferBucketMode;
    }

    //////////////////////////////////////
    // *********** GUI ***********//
    //////////////////////////////////////
    @Override
    protected void buildAdditionalUI(UIElement page) {
        var field = NumberField.ofInt(LayoutStyle.AUTO, this::getCurrentBucketModeTransferSize, this::setCurrentBucketModeTransferSize,
                () -> 1, () -> Integer.MAX_VALUE / transferBucketMode.multiplier);
        var amount = fluidAmountRow(this::getKeepAmountLabel, List.of(BucketMode.values()), () -> transferBucketMode,
                this::setTransferBucketMode, field)
                .disabled(this::isStackSizeFromFilter, "cover.fluid.voiding.ui.amount_from_filter");
        var amountRow = UIElement.column(LayoutStyle.AUTO).addChild(amount)
                .disabled(() -> voidingMode == VoidingMode.VOID_ANY, "cover.fluid.voiding.ui.amount_unused");
        page.addChild(Form.section("cover.fluid.voiding.ui.mode_settings").addChildren(
                CoverUIs.enumRow("cover.fluid.voiding.ui.mode", List.of(VoidingMode.values()), () -> voidingMode, this::setVoidingMode,
                        "cover.voiding.voiding_mode.description.0",
                        "cover.voiding.voiding_mode.description.1"),
                amountRow));
    }

    private Component getKeepAmountLabel() {
        return Component.translatable("cover.fluid.voiding.ui.keep_amount", Component.translatable(transferBucketMode.getTooltip()));
    }

    private int getCurrentBucketModeTransferSize() {
        return this.globalTransferSizeMillibuckets / this.transferBucketMode.multiplier;
    }

    private void setCurrentBucketModeTransferSize(int transferSize) {
        this.globalTransferSizeMillibuckets = Math.max(transferSize * this.transferBucketMode.multiplier, 0);
    }

    @Override
    protected void configureFilter() {
        if (filterHandler.getFilter() instanceof SimpleFluidFilter filter) {
            filter.setMaxStackSize(voidingMode == VoidingMode.VOID_ANY ? 1 : Integer.MAX_VALUE);
        }
    }

    private boolean isStackSizeFromFilter() {
        return this.filterHandler.isFilterPresent() && !this.filterHandler.getFilter().isBlackList();
    }
}
