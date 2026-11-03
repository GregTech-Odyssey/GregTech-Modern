package com.gregtechceu.gtceu.core.mixins.emi;

import com.gregtechceu.gtceu.integration.emi.recipe.EmiPageLayout;

import com.gregtechceu.gtceu.integration.xei.widgets.GTRecipeWidget;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.widget.RecipeFillButtonWidget;
import dev.emi.emi.screen.RecipeDisplay;
import dev.emi.emi.screen.WidgetGroup;
import dev.emi.emi.widget.RecipeDefaultButtonWidget;
import dev.emi.emi.widget.RecipeTreeButtonWidget;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * EMI 配方界面翻页排版（只有 {@code RecipeTab} 为每个配方建 {@link RecipeDisplay}）时，GT 配方页用"整页"外框：
 * <ul>
 * <li>构造时报的尺寸：宽度让出右下角缺口里的按钮列；按钮排进页面按钮栏的配方不让；</li>
 * <li>{@link RecipeDisplay#getWidgets} 期间建的页面画卡片、挖缺口，按钮竖排到页面给出的位置，同页配方按 {@link EmiPageLayout#beginPage} 补高。</li>
 * </ul>
 * 其余地方（悬停预览、截图、生产规划图）仍取 {@code getDisplayWidth/Height} 的紧凑尺寸、用紧凑外框。
 */
@Mixin(value = RecipeDisplay.class, remap = false)
public class RecipeDisplayMixin {

    @Shadow
    private List<?> rightButtons;
    @Shadow
    private int rightWidth;
    @Shadow
    @Final
    public EmiRecipe recipe;
    @Unique
    private int gtceu$buttonIndex = -1;

    @Inject(method = "<init>(Ldev/emi/emi/api/recipe/EmiRecipe;)V", at = @At("RETURN"))
    private void gtceu$hideSideButtons(EmiRecipe recipe, CallbackInfo ci) {
        if (recipe instanceof EmiPageLayout.InlineButtons) rightWidth = 0;
        if (!(recipe instanceof EmiPageLayout.NoSideButtons)) return;
        rightButtons.clear();
        rightWidth = 0;
    }

    @Inject(method = "addButtons", at = @At("HEAD"))
    private void gtceu$beginButtons(WidgetGroup widgets, List<?> types, int x, int xOff, CallbackInfo ci) {
        gtceu$buttonIndex = types == rightButtons && recipe instanceof EmiPageLayout.InlineButtons inline && inline.getButtonX() >= 0 ? 0 : -1;
    }

    @Redirect(method = "addButtons", at = @At(value = "NEW", target = "(IILdev/emi/emi/api/recipe/EmiRecipe;)Ldev/emi/emi/api/widget/RecipeFillButtonWidget;"))
    private RecipeFillButtonWidget gtceu$fillButton(int x, int y, EmiRecipe recipe) {
        return new RecipeFillButtonWidget(gtceu$buttonX(x), gtceu$buttonY(y), recipe);
    }

    @Redirect(method = "addButtons", at = @At(value = "NEW", target = "(IILdev/emi/emi/api/recipe/EmiRecipe;)Ldev/emi/emi/widget/RecipeTreeButtonWidget;"))
    private RecipeTreeButtonWidget gtceu$treeButton(int x, int y, EmiRecipe recipe) {
        return new RecipeTreeButtonWidget(gtceu$buttonX(x), gtceu$buttonY(y), recipe);
    }

    @Redirect(method = "addButtons", at = @At(value = "NEW", target = "(IILdev/emi/emi/api/recipe/EmiRecipe;)Ldev/emi/emi/widget/RecipeDefaultButtonWidget;"))
    private RecipeDefaultButtonWidget gtceu$defaultButton(int x, int y, EmiRecipe recipe) {
        return new RecipeDefaultButtonWidget(gtceu$buttonX(x), gtceu$buttonY(y), recipe);
    }

    @Unique
    private int gtceu$buttonX(int x) {
        return gtceu$buttonIndex < 0 ? x : ((EmiPageLayout.InlineButtons) recipe).getButtonX();
    }

    @Unique
    private int gtceu$buttonY(int y) {
        if (gtceu$buttonIndex < 0) return y;
        int row = rightButtons.size() - 1 - gtceu$buttonIndex++;
        return ((EmiPageLayout.InlineButtons) recipe).getButtonY() + row * GTRecipeWidget.SIDE_BUTTON_PITCH;
    }

    @ModifyArg(method = "getWidgets", at = @At(value = "INVOKE", target = "Ldev/emi/emi/screen/WidgetGroup;<init>(Ldev/emi/emi/api/recipe/EmiRecipe;IIII)V"), index = 4)
    private int gtceu$filledHeight(int height) {
        return height + EmiPageLayout.currentExtra();
    }

    @Redirect(method = "<init>(Ldev/emi/emi/api/recipe/EmiRecipe;)V",
              at = @At(value = "INVOKE", target = "Ldev/emi/emi/api/recipe/EmiRecipe;getDisplayWidth()I"))
    private int gtceu$pagedWidth(EmiRecipe recipe) {
        return recipe instanceof EmiPageLayout.Paged paged ? paged.getPagedWidth() : recipe.getDisplayWidth();
    }

    @Redirect(method = "<init>(Ldev/emi/emi/api/recipe/EmiRecipe;)V",
              at = @At(value = "INVOKE", target = "Ldev/emi/emi/api/recipe/EmiRecipe;getDisplayHeight()I"))
    private int gtceu$pagedHeight(EmiRecipe recipe) {
        return recipe instanceof EmiPageLayout.Paged paged ? paged.getPagedHeight() : recipe.getDisplayHeight();
    }

    @Inject(method = "getWidgets", at = @At("HEAD"))
    private void gtceu$beginPagedBuild(int x, int y, int availableWidth, int availableHeight, CallbackInfoReturnable<WidgetGroup> cir) {
        EmiPageLayout.setPagedBuild(true);
    }

    @Inject(method = "getWidgets", at = @At("RETURN"))
    private void gtceu$endPagedBuild(int x, int y, int availableWidth, int availableHeight, CallbackInfoReturnable<WidgetGroup> cir) {
        EmiPageLayout.setPagedBuild(false);
    }
}
