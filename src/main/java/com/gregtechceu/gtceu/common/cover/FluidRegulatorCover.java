package com.gregtechceu.gtceu.common.cover;

import com.gregtechceu.gtceu.api.capability.ICoverable;
import com.gregtechceu.gtceu.api.cover.CoverDefinition;
import com.gregtechceu.gtceu.api.cover.filter.FluidFilter;
import com.gregtechceu.gtceu.api.cover.filter.SimpleFluidFilter;
import com.gregtechceu.gtceu.api.transfer.key.IKeyHandler;
import com.gregtechceu.gtceu.api.transfer.key.KeyTransfer;
import com.gregtechceu.gtceu.api.transfer.key.Keys;
import com.gregtechceu.gtceu.common.cover.data.BucketMode;
import com.gregtechceu.gtceu.common.cover.data.TransferMode;
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
import lombok.Getter;

import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class FluidRegulatorCover extends PumpCover {

    private static final int MAX_STACK_SIZE = 2048000000; // Capacity of quantum tank IX
    @Getter
    @SaveToDisk
    @SyncToClient
    private TransferMode transferMode = TransferMode.TRANSFER_ANY;
    @Getter
    @SaveToDisk
    @SyncToClient
    private BucketMode transferBucketMode = BucketMode.MILLI_BUCKET;
    @Getter
    @SaveToDisk
    @SyncToClient
    protected int globalTransferLimit;
    protected int fluidTransferBuffered = 0;

    public FluidRegulatorCover(CoverDefinition definition, ICoverable coverHolder, Direction attachedSide, int tier, int maxTransferRate) {
        super(definition, coverHolder, attachedSide, tier, maxTransferRate);
    }

    public FluidRegulatorCover(CoverDefinition definition, ICoverable coverHolder, Direction attachedSide, int tier) {
        this(definition, coverHolder, attachedSide, tier, PUMP_SCALING.applyAsInt(tier));
    }

    //////////////////////////////////////
    // ***** Transfer Logic ******//
    //////////////////////////////////////
    @Override
    protected int doTransferFluidsInternal(IKeyHandler<AEFluidKey> source, IKeyHandler<AEFluidKey> destination, int platformTransferLimit) {
        return switch (transferMode) {
            case TRANSFER_ANY -> transferAny(source, destination, platformTransferLimit);
            case TRANSFER_EXACT -> transferExact(source, destination, platformTransferLimit);
            case KEEP_EXACT -> keepExact(source, destination, platformTransferLimit);
        };
    }

    private int transferExact(IKeyHandler<AEFluidKey> source, IKeyHandler<AEFluidKey> destination, int platformTransferLimit) {
        int fluidLeftToTransfer = platformTransferLimit;
        int size = source.size();
        for (int slot = 0; slot < size; slot++) {
            if (fluidLeftToTransfer <= 0) break;
            AEFluidKey key = source.keyAt(slot);
            int supplyAmount = getFilteredFluidAmount(Keys.displayFluid(key));
            // If the remaining transferrable amount in this operation is not enough to transfer the full stack size,
            // the remaining amount for this operation will be buffered and added to the next operation's maximum.
            if (fluidLeftToTransfer + fluidTransferBuffered < supplyAmount) {
                this.fluidTransferBuffered += fluidLeftToTransfer;
                fluidLeftToTransfer = 0;
                break;
            }
            if (key == null || supplyAmount <= 0) continue;
            if (source.extract(key, supplyAmount, true) < supplyAmount) continue;
            long moved = KeyTransfer.transferKey(source, destination, key, supplyAmount);
            if (moved > 0) {
                fluidLeftToTransfer -= (int) (moved - fluidTransferBuffered);
            }
            fluidTransferBuffered = 0;
        }
        return platformTransferLimit - fluidLeftToTransfer;
    }

    private int keepExact(IKeyHandler<AEFluidKey> source, IKeyHandler<AEFluidKey> destination, int platformTransferLimit) {
        int fluidLeftToTransfer = platformTransferLimit;
        var sourceAmounts = enumerateDistinctFluids(source);
        var destinationAmounts = enumerateDistinctFluids(destination);
        for (AEFluidKey key : sourceAmounts.keySet()) {
            if (fluidLeftToTransfer <= 0) break;
            int amountToKeep = getFilteredFluidAmount(key.getReadOnlyStack());
            long amountInDest = destinationAmounts.getLong(key);
            if (amountInDest >= amountToKeep) continue;
            long toMove = Math.min(fluidLeftToTransfer, amountToKeep - amountInDest);
            if (toMove <= 0) continue;
            fluidLeftToTransfer -= (int) KeyTransfer.transferKey(source, destination, key, toMove);
        }
        return platformTransferLimit - fluidLeftToTransfer;
    }

    private void setTransferBucketMode(BucketMode transferBucketMode) {
        this.transferBucketMode = transferBucketMode;
    }

    private void setTransferMode(TransferMode transferMode) {
        this.transferMode = transferMode;
        if (!this.isRemote()) {
            configureFilter();
        }
    }

    @Override
    protected void configureFilter() {
        if (filterHandler.getFilter() instanceof SimpleFluidFilter filter) {
            filter.setMaxStackSize(transferMode == TransferMode.TRANSFER_ANY ? 1 : MAX_STACK_SIZE);
        }
    }

    public int getFilteredFluidAmount(FluidStack fluidStack) {
        if (!filterHandler.isFilterPresent()) return globalTransferLimit;
        FluidFilter filter = filterHandler.getFilter();
        return (filter.supportsAmounts() ? filter.testFluidAmount(fluidStack) : globalTransferLimit);
    }

    ///////////////////////////
    // ***** GUI ******//
    ///////////////////////////
    @Override
    protected void buildAdditionalUI(UIElement page) {
        var field = NumberField.ofInt(LayoutStyle.AUTO, this::getCurrentBucketModeTransferSize, this::setCurrentBucketModeTransferSize,
                () -> 0, () -> MAX_STACK_SIZE / transferBucketMode.multiplier);
        var amount = fluidAmountRow(this::getTransferSizeLabel, List.of(BucketMode.values()), this::getTransferBucketMode,
                this::setTransferBucketMode, field)
                .disabled(this::isTransferSizeFromFilter, "cover.fluid_regulator.ui.amount_from_filter");
        var amountRow = UIElement.column(LayoutStyle.AUTO).addChild(amount)
                .disabled(() -> transferMode == TransferMode.TRANSFER_ANY, "cover.fluid_regulator.ui.amount_unused");
        page.addChild(Form.section("cover.fluid_regulator.ui.regulation").addChildren(
                CoverUIs.enumRow("cover.fluid_regulator.ui.transfer_mode", List.of(TransferMode.values()), this::getTransferMode, this::setTransferMode,
                        "cover.fluid_regulator.transfer_mode.description.0",
                        "cover.fluid_regulator.transfer_mode.description.1",
                        "cover.fluid_regulator.transfer_mode.description.2"),
                amountRow));
    }

    private Component getTransferSizeLabel() {
        var unit = Component.translatable(transferBucketMode.getTooltip());
        return transferMode == TransferMode.KEEP_EXACT ? Component.translatable("cover.fluid_regulator.ui.keep_amount", unit) :
                Component.translatable("cover.fluid_regulator.ui.supply_amount", unit);
    }

    private int getCurrentBucketModeTransferSize() {
        return this.globalTransferLimit / this.transferBucketMode.multiplier;
    }

    private void setCurrentBucketModeTransferSize(int transferSize) {
        this.globalTransferLimit = Math.min(Math.max(transferSize * this.transferBucketMode.multiplier, 0), MAX_STACK_SIZE);
    }

    private boolean isTransferSizeFromFilter() {
        return this.filterHandler.isFilterPresent() && this.filterHandler.getFilter().supportsAmounts();
    }
}
