package com.gregtechceu.gtceu.common.cover.detector;

import com.gregtechceu.gtceu.api.capability.ICoverable;
import com.gregtechceu.gtceu.api.capability.IEnergyInfoProvider;
import com.gregtechceu.gtceu.api.cover.CoverDefinition;
import com.gregtechceu.gtceu.api.cover.IUICover;
import com.gregtechceu.gtceu.uipro.LayoutStyle;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.elements.ButtonGroup;
import com.gregtechceu.gtceu.uipro.elements.NumberField;
import com.gregtechceu.gtceu.uipro.elements.Switch;
import com.gregtechceu.gtceu.uipro.elements.TextLine;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;
import com.gregtechceu.gtceu.uiwidgets.cover.CoverUIs;
import com.gregtechceu.gtceu.utils.GTMath;

import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

import com.gto.datasynclib.annotations.SaveToDisk;
import lombok.Getter;

import java.math.BigInteger;
import java.util.function.Supplier;

import javax.annotation.ParametersAreNonnullByDefault;

import static com.gregtechceu.gtceu.utils.RedstoneUtil.computeLatchedRedstoneBetweenValues;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class AdvancedEnergyDetectorCover extends EnergyDetectorCover implements IUICover {

    private static final int DEFAULT_MIN_PERCENT = 33;
    private static final int DEFAULT_MAX_PERCENT = 66;
    @Getter
    @SaveToDisk
    public long minValue;
    @Getter
    @SaveToDisk
    public long maxValue;
    @Getter
    @SaveToDisk
    private boolean usePercent;

    public AdvancedEnergyDetectorCover(CoverDefinition definition, ICoverable coverHolder, Direction attachedSide) {
        super(definition, coverHolder, attachedSide);
        this.minValue = DEFAULT_MIN_PERCENT;
        this.maxValue = DEFAULT_MAX_PERCENT;
        this.usePercent = true;
    }

    @Override
    protected void update() {
        IEnergyInfoProvider energyInfoProvider = getEnergyInfoProvider();
        if (energyInfoProvider == null) return;

        IEnergyInfoProvider.EnergyInfo energyInfo = energyInfoProvider.getEnergyInfo();
        boolean isBigInt = energyInfoProvider.supportsBigIntEnergyValues();

        if (isBigInt) {
            if (usePercent) {
                if (energyInfo.capacity().compareTo(BigInteger.ZERO) > 0) {
                    float ratio = GTMath.ratio(energyInfo.stored(), energyInfo.capacity());
                    setRedstoneSignalOutput(computeLatchedRedstoneBetweenValues(ratio * 100, maxValue,
                            minValue, isInverted(), redstoneSignalOutput));
                } else {
                    setRedstoneSignalOutput(isInverted() ? 15 : 0);
                }
            } else {
                setRedstoneSignalOutput(computeLatchedRedstoneBetweenValues(energyInfo.stored(), BigInteger.valueOf(this.maxValue), BigInteger.valueOf(this.minValue), isInverted(), redstoneSignalOutput));
            }
        } else {
            if (usePercent) {
                if (energyInfo.capacity().longValue() > 0) {
                    float ratio = energyInfo.stored().floatValue() / energyInfo.capacity().floatValue();
                    setRedstoneSignalOutput(computeLatchedRedstoneBetweenValues(ratio * 100, maxValue,
                            minValue, isInverted(), redstoneSignalOutput));
                } else {
                    setRedstoneSignalOutput(isInverted() ? 15 : 0);
                }
            } else {
                setRedstoneSignalOutput(computeLatchedRedstoneBetweenValues(energyInfo.stored().longValue(), this.maxValue, this.minValue, isInverted(), redstoneSignalOutput));
            }
        }
    }

    public void setUsePercent(boolean usePercent) {
        if (this.usePercent == usePercent) return;
        this.usePercent = usePercent;
        coverHolder.onChanged();
        IEnergyInfoProvider energyInfoProvider = getEnergyInfoProvider();
        if (energyInfoProvider == null) return;
        long energyCapacity = energyCapacity(energyInfoProvider);
        if (usePercent) {
            minValue = GTMath.clamp((long) (((double) minValue / energyCapacity) * 100), 0, 100);
            maxValue = GTMath.clamp((long) (((double) maxValue / energyCapacity) * 100), 0, 100);
        } else {
            minValue = GTMath.clamp((long) ((minValue / 100.0) * energyCapacity), 0, energyCapacity);
            maxValue = GTMath.clamp((long) ((maxValue / 100.0) * energyCapacity), 0, energyCapacity);
        }
    }

    public void setMinValue(long minValue) {
        this.minValue = minValue;
        coverHolder.onChanged();
    }

    public void setMaxValue(long maxValue) {
        this.maxValue = maxValue;
        coverHolder.onChanged();
    }

    private static long energyCapacity(IEnergyInfoProvider energyInfoProvider) {
        try {
            return energyInfoProvider.getEnergyInfo().capacity().longValueExact();
        } catch (ArithmeticException e) {
            return Long.MAX_VALUE;
        }
    }

    private long maxThreshold() {
        if (usePercent) return 100;
        IEnergyInfoProvider energyInfoProvider = getEnergyInfoProvider();
        return energyInfoProvider == null ? Long.MAX_VALUE : energyCapacity(energyInfoProvider);
    }

    //////////////////////////////////////
    // *********** GUI ***********//
    //////////////////////////////////////
    @Override
    public Widget createUIWidget() {
        var output = CoverUIs.section("cover.advanced_detector.output").addChildren(
                CoverUIs.controlRow("cover.advanced_detector.inverted", Switch.of(this::isInverted, this::setInverted),
                        "cover.advanced_energy_detector.inverted.tooltip"),
                modeRow());
        var thresholds = CoverUIs.section("cover.advanced_detector.thresholds").addChildren(
                thresholdRow(() -> Component.translatable(usePercent ? "cover.advanced_energy_detector.min.percent" : "cover.advanced_energy_detector.min.eu"),
                        new NumberField(LayoutStyle.AUTO, this::getMinValue, this::setMinValue, () -> 0, this::maxThreshold)),
                thresholdRow(() -> Component.translatable(usePercent ? "cover.advanced_energy_detector.max.percent" : "cover.advanced_energy_detector.max.eu"),
                        new NumberField(LayoutStyle.AUTO, this::getMaxValue, this::setMaxValue, () -> 0, this::maxThreshold)));
        return CoverUIs.page().addChildren(output, thresholds);
    }

    private UIElement modeRow() {
        var label = TextLine.translatable(LayoutStyle.AUTO, "cover.advanced_energy_detector.mode").setColor(UITheme.PANEL_TEXT);
        label.setHoverTooltips("cover.advanced_energy_detector.mode.tooltip");
        var modes = ButtonGroup.single(2,
                i -> Component.translatable(i == 1 ? "cover.advanced_energy_detector.mode.percent" : "cover.advanced_energy_detector.mode.eu"),
                () -> usePercent ? 1 : 0, i -> setUsePercent(i == 1)).horizontal();
        return UIElement.column(LayoutStyle.AUTO).layout(l -> l.gapAll(UISizes.GAP)).addChildren(label, modes);
    }

    private static UIElement thresholdRow(Supplier<Component> label, NumberField field) {
        return UIElement.column(LayoutStyle.AUTO).layout(l -> l.gapAll(UISizes.GAP))
                .addChildren(TextLine.of(LayoutStyle.AUTO, label).setColor(UITheme.PANEL_TEXT), field);
    }
}
