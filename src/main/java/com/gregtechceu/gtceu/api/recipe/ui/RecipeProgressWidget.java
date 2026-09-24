package com.gregtechceu.gtceu.api.recipe.ui;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.recipe.category.GTRecipeCategory;
import com.gregtechceu.gtceu.integration.emi.recipe.GTRecipeEMICategory;

import com.lowdragmc.lowdraglib.gui.texture.ProgressTexture;
import com.lowdragmc.lowdraglib.gui.widget.ProgressWidget;

import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import dev.emi.emi.api.EmiApi;
import org.jetbrains.annotations.Nullable;

import java.util.function.DoubleSupplier;

/**
 * 配方界面的进度条（箭头、锤子、蒸馏塔……），id 固定为 {@link #ID}，由配方界面统一绑定进度。
 * <p>
 * 一张底图拆成几段依次播放时（蒸馏塔先线圈后塔身），每段设 {@link #setRange 进度区间}：
 * 总进度在区间内时本段从空到满，区间之前为空、之后为满。
 * <p>
 * 在机器界面里点击进度条打开该配方类型的 EMI 配方页（{@link #setOpenCategory}）。
 */
public class RecipeProgressWidget extends ProgressWidget {

    /** 配方进度条的控件 id，配方界面按它找到所有进度条并绑定同一个进度。 */
    public static final String ID = "progress";

    private double from = 0;
    private double to = 1;
    /// 绑定的原始进度（未按区间换算），换区间时据此重新换算
    private DoubleSupplier source = ProgressWidget.JEIProgress;
    @Nullable
    private GTRecipeCategory openCategory;

    public RecipeProgressWidget(int width, int height, ProgressTexture texture) {
        super(ProgressWidget.JEIProgress, 0, 0, width, height, texture);
        setId(ID);
    }

    /** 本段只在总进度 {@code from}～{@code to} 之间播放。 */
    public RecipeProgressWidget setRange(double from, double to) {
        this.from = from;
        this.to = to;
        setProgressSupplier(source);
        return this;
    }

    @Override
    public ProgressWidget setProgressSupplier(DoubleSupplier progress) {
        this.source = progress;
        if (from <= 0 && to >= 1) return super.setProgressSupplier(progress);
        double start = from, length = to - from;
        return super.setProgressSupplier(() -> Mth.clamp((progress.getAsDouble() - start) / length, 0, 1));
    }

    /** 点击时打开 {@code category} 的 EMI 配方页（只在装了 EMI 时生效），并加上悬停提示。 */
    public RecipeProgressWidget setOpenCategory(@Nullable GTRecipeCategory category) {
        this.openCategory = GTCEu.Mods.isEMILoaded() ? category : null;
        if (openCategory != null) setHoverTooltips("gtceu.recipe_type.show_recipes");
        return this;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (openCategory != null && button == 0 && isMouseOverElement(mouseX, mouseY)) {
            EmiApi.displayRecipeCategory(GTRecipeEMICategory.machineCategory(openCategory));
            playButtonClickSound();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
