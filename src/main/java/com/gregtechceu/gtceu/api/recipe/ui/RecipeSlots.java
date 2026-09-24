package com.gregtechceu.gtceu.api.recipe.ui;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.recipe.info.ContentRecipeInfo;
import com.gregtechceu.gtceu.api.recipe.info.ItemRecipeInfo;
import com.gregtechceu.gtceu.api.recipe.info.RecipeInfo;

import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ProgressTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;
import com.lowdragmc.lowdraglib.gui.widget.Widget;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

/**
 * 交给 {@link RecipeSlotLayout} 的建材：按配方类型创建槽位（带 id 和底图）与进度条，排布只管摆放。
 * <p>
 * 槽位 id 为 {@code 种类_方向_序号}（如 {@code item_in_3}），{@link GTRecipeTypeUI} 排布完成后按 id 绑定机器库存或配方内容；
 * 进度条 id 为 {@link RecipeProgressWidget#ID}，全部绑定同一个进度。
 */
public final class RecipeSlots {

    /** 默认进度箭头的边长。 */
    public static final int PROGRESS_SIZE = 20;

    private final GTRecipeTypeUI ui;
    private final boolean steam;
    private final boolean highPressure;
    private final BiPredicate<Boolean, RecipeInfo> doRenderSlot;

    RecipeSlots(GTRecipeTypeUI ui, boolean steam, boolean highPressure, BiPredicate<Boolean, RecipeInfo> doRenderSlot) {
        this.ui = ui;
        this.steam = steam;
        this.highPressure = highPressure;
        this.doRenderSlot = doRenderSlot;
    }

    public GTRecipeType recipeType() {
        return ui.getRecipeType();
    }

    public boolean isSteam() {
        return steam;
    }

    public boolean isHighPressure() {
        return highPressure;
    }

    /** 该方向要显示槽位的内容种类，按 {@link RecipeInfo#COMPARATOR} 排序；机器界面可以按需滤掉某些种类（如发电机不显示输出）。 */
    public List<ContentRecipeInfo<?, ?>> capabilities(IO io) {
        var max = io == IO.IN ? recipeType().maxInputs : recipeType().maxOutputs;
        var result = new ArrayList<ContentRecipeInfo<?, ?>>(max.size());
        for (var entry : max.object2IntEntrySet()) {
            if (entry.getIntValue() > 0 && entry.getKey() instanceof ContentRecipeInfo<?, ?> cap && cap.doRenderSlot &&
                    cap.getWidgetClass() != null && doRenderSlot.test(io == IO.OUT, cap)) {
                result.add(cap);
            }
        }
        return result;
    }

    /** 该方向、该种类的槽位数（不显示时为 0）。 */
    public int count(IO io, ContentRecipeInfo<?, ?> cap) {
        if (!cap.doRenderSlot || cap.getWidgetClass() == null || !doRenderSlot.test(io == IO.OUT, cap)) return 0;
        return io == IO.IN ? recipeType().getMaxInputs(cap) : recipeType().getMaxOutputs(cap);
    }

    /** 一个槽位，底图为该种类的标准槽（蒸汽机为蒸汽版物品槽），叠配方类型设置的角标（{@code setSlotOverlay}）。 */
    public Widget slot(IO io, ContentRecipeInfo<?, ?> cap, int index) {
        return slot(io, cap, index, ui.getSlotOverlay(io == IO.OUT, cap, index == count(io, cap) - 1));
    }

    /** 一个槽位，叠指定角标（专用排布里给个别槽换图标，如蒸馏塔各层的烧杯）。 */
    public Widget slot(IO io, ContentRecipeInfo<?, ?> cap, int index, @Nullable IGuiTexture overlay) {
        var slot = cap.createWidget();
        if (slot == null) throw new IllegalStateException("Recipe info " + cap.name + " has no slot widget");
        slot.setId(cap.slotName(io, index));
        // 蒸汽机保留自己的铜 / 钢皮肤
        IGuiTexture base = steam && cap == ItemRecipeInfo.INSTANCE ? GuiTextures.SLOT_STEAM.get(highPressure) : slot.getBackgroundTexture();
        slot.setBackground(overlay == null ? base : new GuiTextureGroup(base, overlay));
        return slot;
    }

    /** 该方向、该种类的全部槽位。 */
    public List<Widget> slots(IO io, ContentRecipeInfo<?, ?> cap) {
        int count = count(io, cap);
        var result = new ArrayList<Widget>(count);
        for (int i = 0; i < count; i++) result.add(slot(io, cap, i));
        return result;
    }

    /** 配方类型的默认进度箭头（{@link #PROGRESS_SIZE} 见方；蒸汽机用蒸汽版底图）。 */
    public RecipeProgressWidget progress() {
        return new RecipeProgressWidget(PROGRESS_SIZE, PROGRESS_SIZE, ui.progressTexture(steam, highPressure));
    }

    /** 专用底图的进度条：{@code texture} 上半张为空条、下半张为满条。 */
    public RecipeProgressWidget progress(ResourceTexture texture, ProgressTexture.FillDirection direction, int width, int height) {
        var bar = new ProgressTexture(texture.getSubTexture(0, 0, 1, 0.5), texture.getSubTexture(0, 0.5, 1, 0.5));
        bar.setFillDirection(direction);
        return new RecipeProgressWidget(width, height, bar);
    }
}
