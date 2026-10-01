package com.gregtechceu.gtceu.integration.emi.recipe;

import com.gregtechceu.gtceu.integration.xei.widgets.GTRecipeWidget.PageFrame;

import com.lowdragmc.lowdraglib.utils.Size;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.widget.WidgetHolder;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.function.Function;
import java.util.function.IntSupplier;

/**
 * EMI 配方页的尺寸骨架：紧凑尺寸与翻页尺寸（按右侧按钮数分别缓存）、翻页排版时报给 EMI 的宽高、建页时取用的外框。
 */
public final class EmiPageSizes {

    private static final int CACHED_BUTTONS = 6;

    private final Function<PageFrame, Size> measure;
    private final IntSupplier maxHeight;
    private final Size[] paged = new Size[CACHED_BUTTONS];
    private int pagedMaxHeight = -1;
    @Nullable
    private Size compact;
    private int pagedButtons = -1;

    public EmiPageSizes(Function<PageFrame, Size> measure) {
        this(measure, () -> 0);
    }

    public EmiPageSizes(Function<PageFrame, Size> measure, IntSupplier maxHeight) {
        this.measure = measure;
        this.maxHeight = maxHeight;
    }

    public PageFrame pagedFrame(int fillHeight, int buttons) {
        return new PageFrame(EmiPageLayout.minPageWidth(), fillHeight, buttons, true, maxHeight.getAsInt());
    }

    public Size getPagedSize(int buttons) {
        int max = maxHeight.getAsInt();
        if (max != pagedMaxHeight) {
            pagedMaxHeight = max;
            Arrays.fill(paged, null);
        }
        if (buttons >= paged.length) return measure.apply(pagedFrame(0, buttons));
        var size = paged[buttons];
        if (size == null) paged[buttons] = size = measure.apply(pagedFrame(0, buttons));
        return size;
    }

    public Size getCompactSize() {
        var size = compact;
        if (size == null) compact = size = measure.apply(PageFrame.COMPACT);
        return size;
    }

    public int getPagedWidth(EmiRecipe recipe) {
        pagedButtons = EmiPageLayout.sideButtons(recipe);
        return pagedDisplayWidth(pagedButtons);
    }

    public int getPagedHeight() {
        return getPagedSize(Math.max(pagedButtons, 0)).height;
    }

    public PageFrame frameFor(WidgetHolder widgets) {
        if (pagedButtons >= 0 && EmiPageLayout.claimPagedGroup(widgets, pagedDisplayWidth(pagedButtons))) {
            return pagedFrame(widgets.getHeight(), pagedButtons);
        }
        return PageFrame.COMPACT;
    }

    private int pagedDisplayWidth(int buttons) {
        return EmiPageLayout.displayWidth(getPagedSize(buttons).width, buttons);
    }
}
