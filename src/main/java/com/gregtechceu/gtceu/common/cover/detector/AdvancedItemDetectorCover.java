package com.gregtechceu.gtceu.common.cover.detector;

import com.gregtechceu.gtceu.api.capability.ICoverable;
import com.gregtechceu.gtceu.api.cover.CoverDefinition;
import com.gregtechceu.gtceu.api.cover.IUICover;
import com.gregtechceu.gtceu.api.cover.filter.FilterHandler;
import com.gregtechceu.gtceu.api.cover.filter.FilterHandlers;
import com.gregtechceu.gtceu.api.cover.filter.ItemFilter;
import com.gregtechceu.gtceu.api.transfer.key.IKeyHandler;
import com.gregtechceu.gtceu.api.transfer.key.Keys;
import com.gregtechceu.gtceu.uipro.LayoutStyle;
import com.gregtechceu.gtceu.uipro.elements.Form;
import com.gregtechceu.gtceu.uipro.elements.NumberField;
import com.gregtechceu.gtceu.uipro.elements.Switch;
import com.gregtechceu.gtceu.uiwidgets.cover.CoverUIs;
import com.gregtechceu.gtceu.utils.RedstoneUtil;

import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import appeng.api.stacks.AEItemKey;
import com.gto.datasynclib.annotations.SaveToDisk;
import com.gto.datasynclib.annotations.SyncToClient;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

@Getter
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class AdvancedItemDetectorCover extends ItemDetectorCover implements IUICover {

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
    protected final FilterHandler<ItemStack, ItemFilter> filterHandler;

    public AdvancedItemDetectorCover(CoverDefinition definition, ICoverable coverHolder, Direction attachedSide) {
        super(definition, coverHolder, attachedSide);
        this.minValue = DEFAULT_MIN;
        this.maxValue = DEFAULT_MAX;
        filterHandler = FilterHandlers.item(this);
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
        ItemFilter filter = filterHandler.getFilter();
        IKeyHandler<AEItemKey> handler = getItemHandler();
        if (handler == null) return;
        long storedItems = 0;
        int size = handler.size();
        for (int i = 0; i < size; i++) {
            long amount = handler.amountAt(i);
            if (amount <= 0) continue;
            var key = handler.keyAt(i);
            if (key != null && filter.test(key.getReadOnlyStack())) storedItems = Keys.add(storedItems, amount);
        }
        if (isLatched) {
            setRedstoneSignalOutput(RedstoneUtil.computeLatchedRedstoneBetweenValues(storedItems, maxValue, minValue, isInverted(), redstoneSignalOutput));
        } else {
            setRedstoneSignalOutput(RedstoneUtil.computeRedstoneBetweenValues(storedItems, maxValue, minValue, isInverted()));
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
    private static final Component OUTPUT_SECTION = Component.translatable("cover.advanced_detector.output");
    private static final Component INVERTED_TITLE = Component.translatable("cover.advanced_detector.inverted");
    private static final Component INVERTED_TOOLTIP = Component.translatable("cover.advanced_detector.inverted.tooltip");
    private static final Component LATCHED_TITLE = Component.translatable("cover.advanced_detector.latched");
    private static final Component LATCHED_TOOLTIP = Component.translatable("cover.advanced_detector.latched.tooltip");
    private static final Component THRESHOLDS_SECTION = Component.translatable("cover.advanced_detector.thresholds");
    private static final Component MIN_TITLE = Component.translatable("cover.advanced_item_detector.min");
    private static final Component MAX_TITLE = Component.translatable("cover.advanced_item_detector.max");

    @Override
    public Widget createUIWidget() {
        var output = Form.section(OUTPUT_SECTION).addChildren(
                Form.controlRow(INVERTED_TITLE, Switch.of(this::isInverted, this::setInverted),
                        INVERTED_TOOLTIP),
                Form.controlRow(LATCHED_TITLE, Switch.of(this::isLatched, this::setLatched),
                        LATCHED_TOOLTIP));
        var thresholds = Form.section(THRESHOLDS_SECTION).addChildren(
                Form.numberRow(MIN_TITLE, NumberField.ofInt(LayoutStyle.AUTO, this::getMinValue,
                        this::setMinValue, () -> 0, () -> Math.max(0, maxValue - 1))),
                Form.numberRow(MAX_TITLE, NumberField.ofInt(LayoutStyle.AUTO, this::getMaxValue,
                        this::setMaxValue, 0, Integer.MAX_VALUE)));
        return Form.page().addChildren(output, thresholds, CoverUIs.filterSection(filterHandler));
    }
}
