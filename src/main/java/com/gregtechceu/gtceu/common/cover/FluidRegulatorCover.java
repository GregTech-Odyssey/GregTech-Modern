package com.gregtechceu.gtceu.common.cover;

import com.gregtechceu.gtceu.api.capability.ICoverable;
import com.gregtechceu.gtceu.api.cover.CoverDefinition;
import com.gregtechceu.gtceu.api.cover.filter.FluidFilter;
import com.gregtechceu.gtceu.api.cover.filter.SimpleFluidFilter;
import com.gregtechceu.gtceu.api.transfer.fluid.ICustomFluidStackHandler;
import com.gregtechceu.gtceu.common.cover.data.BucketMode;
import com.gregtechceu.gtceu.common.cover.data.TransferMode;
import com.gregtechceu.gtceu.uipro.LayoutStyle;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.elements.NumberField;
import com.gregtechceu.gtceu.uiwidgets.cover.CoverUIs;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;

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
    protected int doTransferFluidsInternal(ICustomFluidStackHandler source, ICustomFluidStackHandler destination, int platformTransferLimit) {
        return switch (transferMode) {
            case TRANSFER_ANY -> transferAny(source, destination, platformTransferLimit);
            case TRANSFER_EXACT -> transferExact(source, destination, platformTransferLimit);
            case KEEP_EXACT -> keepExact(source, destination, platformTransferLimit);
        };
    }

    private int transferExact(IFluidHandler source, IFluidHandler destination, int platformTransferLimit) {
        int fluidLeftToTransfer = platformTransferLimit;
        for (int slot = 0; slot < source.getTanks(); slot++) {
            if (fluidLeftToTransfer <= 0) break;
            FluidStack sourceFluid = source.getFluidInTank(slot).copy();
            int supplyAmount = getFilteredFluidAmount(sourceFluid);
            // If the remaining transferrable amount in this operation is not enough to transfer the full stack size,
            // the remaining amount for this operation will be buffered and added to the next operation's maximum.
            if (fluidLeftToTransfer + fluidTransferBuffered < supplyAmount) {
                this.fluidTransferBuffered += fluidLeftToTransfer;
                fluidLeftToTransfer = 0;
                break;
            }
            if (sourceFluid.isEmpty() || supplyAmount <= 0) continue;
            if (supplyAmount != sourceFluid.getAmount()) {
                sourceFluid = sourceFluid.copy();
                sourceFluid.setAmount(supplyAmount);
            }
            FluidStack drained = source.drain(sourceFluid, FluidAction.SIMULATE);
            if (drained.isEmpty() || drained.getAmount() < supplyAmount) continue;
            int insertableAmount = destination.fill(drained.copy(), FluidAction.SIMULATE);
            if (insertableAmount <= 0) continue;
            drained.setAmount(insertableAmount);
            drained = source.drain(drained, FluidAction.EXECUTE);
            if (!drained.isEmpty()) {
                destination.fill(drained, FluidAction.EXECUTE);
                fluidLeftToTransfer -= (drained.getAmount() - fluidTransferBuffered);
            }
            fluidTransferBuffered = 0;
        }
        return platformTransferLimit - fluidLeftToTransfer;
    }

    private int keepExact(ICustomFluidStackHandler source, ICustomFluidStackHandler destination, int platformTransferLimit) {
        int fluidLeftToTransfer = platformTransferLimit;
        var sourceAmounts = enumerateDistinctFluids(source, TransferDirection.EXTRACT);
        var destinationAmounts = enumerateDistinctFluids(destination, TransferDirection.INSERT);
        for (FluidStack fluidStack : sourceAmounts.keySet()) {
            if (fluidLeftToTransfer <= 0) break;
            int amountToKeep = getFilteredFluidAmount(fluidStack);
            long amountInDest = destinationAmounts.getOrDefault(fluidStack, 0);
            if (amountInDest >= amountToKeep) continue;
            FluidStack fluidToMove = fluidStack.copy();
            fluidToMove.setAmount(Math.min(fluidLeftToTransfer, (int) (amountToKeep - amountInDest)));
            if (fluidToMove.getAmount() <= 0) continue;
            FluidStack drained = source.drain(fluidToMove, FluidAction.SIMULATE);
            int fillableAmount = destination.fill(drained, FluidAction.SIMULATE);
            if (fillableAmount <= 0) continue;
            fluidToMove.setAmount(Math.min(fluidToMove.getAmount(), fillableAmount));
            drained = source.drain(fluidToMove, FluidAction.EXECUTE);
            int movedAmount = destination.fill(drained, FluidAction.EXECUTE);
            fluidLeftToTransfer -= movedAmount;
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
        var field = new NumberField(LayoutStyle.AUTO, this::getCurrentBucketModeTransferSize, value -> setCurrentBucketModeTransferSize((int) value),
                () -> 0, () -> MAX_STACK_SIZE / transferBucketMode.multiplier);
        var amount = fluidAmountRow(this::getTransferSizeLabel, List.of(BucketMode.values()), this::getTransferBucketMode,
                this::setTransferBucketMode, field)
                .disabled(this::isTransferSizeFromFilter, "cover.fluid_regulator.ui.amount_from_filter");
        var amountRow = UIElement.column(LayoutStyle.AUTO).addChild(amount)
                .disabled(() -> transferMode == TransferMode.TRANSFER_ANY, "cover.fluid_regulator.ui.amount_unused");
        page.addChild(CoverUIs.section("cover.fluid_regulator.ui.regulation").addChildren(
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
