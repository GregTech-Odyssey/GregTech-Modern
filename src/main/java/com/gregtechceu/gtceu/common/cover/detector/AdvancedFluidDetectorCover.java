package com.gregtechceu.gtceu.common.cover.detector;

import com.gregtechceu.gtceu.api.capability.ICoverable;
import com.gregtechceu.gtceu.api.cover.CoverDefinition;
import com.gregtechceu.gtceu.api.cover.IUICover;
import com.gregtechceu.gtceu.api.cover.filter.FilterHandler;
import com.gregtechceu.gtceu.api.cover.filter.FilterHandlers;
import com.gregtechceu.gtceu.api.cover.filter.FluidFilter;
import com.gregtechceu.gtceu.uipro.LayoutStyle;
import com.gregtechceu.gtceu.uipro.elements.NumberField;
import com.gregtechceu.gtceu.uipro.elements.Switch;
import com.gregtechceu.gtceu.uiwidgets.cover.CoverUIs;

import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import com.gto.datasynclib.annotations.SaveToDisk;
import com.gto.datasynclib.annotations.SyncToClient;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

import static com.gregtechceu.gtceu.utils.RedstoneUtil.computeLatchedRedstoneBetweenValues;
import static com.gregtechceu.gtceu.utils.RedstoneUtil.computeRedstoneBetweenValues;

@Getter
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class AdvancedFluidDetectorCover extends FluidDetectorCover implements IUICover {

    private static final int DEFAULT_MIN = 64;
    private static final int DEFAULT_MAX = 512;
    @SaveToDisk
    private int minValue;
    @SaveToDisk
    private int maxValue;
    @Setter
    @SaveToDisk
    @SyncToClient
    private boolean isLatched;
    @SaveToDisk
    @SyncToClient
    protected final FilterHandler<FluidStack, FluidFilter> filterHandler;

    public AdvancedFluidDetectorCover(CoverDefinition definition, ICoverable coverHolder, Direction attachedSide) {
        super(definition, coverHolder, attachedSide);
        this.minValue = DEFAULT_MIN;
        this.maxValue = DEFAULT_MAX;
        filterHandler = FilterHandlers.fluid(this);
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
    protected void update() {
        FluidFilter filter = filterHandler.getFilter();
        IFluidHandler fluidHandler = getFluidHandler();
        if (fluidHandler == null) return;
        long storedFluid = 0;
        for (int tank = 0; tank < fluidHandler.getTanks(); tank++) {
            FluidStack content = fluidHandler.getFluidInTank(tank);
            if (!content.isEmpty() && filter.test(content)) storedFluid += content.getAmount();
        }
        if (isLatched) {
            setRedstoneSignalOutput(computeLatchedRedstoneBetweenValues(storedFluid, maxValue, minValue, isInverted(), redstoneSignalOutput));
        } else {
            setRedstoneSignalOutput(computeRedstoneBetweenValues(storedFluid, maxValue, minValue, isInverted()));
        }
    }

    public void setMinValue(int minValue) {
        this.minValue = Mth.clamp(minValue, 0, maxValue - 1);
        coverHolder.onChanged();
    }

    public void setMaxValue(int maxValue) {
        this.maxValue = Math.max(maxValue, 0);
        coverHolder.onChanged();
    }

    //////////////////////////////////////
    // *********** GUI ***********//
    //////////////////////////////////////
    @Override
    public Widget createUIWidget() {
        var output = CoverUIs.section("cover.advanced_detector.output").addChildren(
                CoverUIs.controlRow("cover.advanced_detector.inverted", Switch.of(this::isInverted, this::setInverted),
                        "cover.advanced_detector.inverted.tooltip"),
                CoverUIs.controlRow("cover.advanced_detector.latched", Switch.of(this::isLatched, this::setLatched),
                        "cover.advanced_detector.latched.tooltip"));
        var thresholds = CoverUIs.section("cover.advanced_detector.thresholds").addChildren(
                CoverUIs.numberRow("cover.advanced_fluid_detector.min", new NumberField(LayoutStyle.AUTO, this::getMinValue,
                        value -> setMinValue((int) value), () -> 0, () -> Math.max(0, maxValue - 1))),
                CoverUIs.numberRow("cover.advanced_fluid_detector.max", new NumberField(LayoutStyle.AUTO, this::getMaxValue,
                        value -> setMaxValue((int) value), () -> 0, () -> Integer.MAX_VALUE)));
        return CoverUIs.page().addChildren(output, thresholds, CoverUIs.filterSection(filterHandler));
    }
}
