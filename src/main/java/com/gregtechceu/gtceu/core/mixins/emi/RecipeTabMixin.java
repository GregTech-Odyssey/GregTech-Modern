package com.gregtechceu.gtceu.core.mixins.emi;

import com.gregtechceu.gtceu.integration.emi.recipe.EmiPageLayout;

import dev.emi.emi.screen.RecipeDisplay;
import dev.emi.emi.screen.RecipeTab;
import dev.emi.emi.screen.WidgetGroup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * 一页放多张 GT 配方时，把页面剩下的高度分给各张配方（能等高就等高），页底不留空白。
 */
@Mixin(value = RecipeTab.class, remap = false)
public abstract class RecipeTabMixin {

    @Shadow
    private List<List<RecipeDisplay>> pages;

    @Shadow
    private int getVerticalRecipeSpace(int backgroundHeight) {
        throw new AssertionError();
    }

    @Inject(method = "constructWidgets", at = @At("HEAD"))
    private void gtceu$beginPage(int page, int x, int y, int backgroundWidth, int backgroundHeight, CallbackInfoReturnable<List<WidgetGroup>> cir) {
        if (page >= 0 && page < pages.size()) EmiPageLayout.beginPage(pages.get(page), getVerticalRecipeSpace(backgroundHeight));
    }

    @Redirect(method = "constructWidgets", at = @At(value = "INVOKE", target = "Ldev/emi/emi/screen/RecipeDisplay;getHeight()I"))
    private int gtceu$filledHeight(RecipeDisplay display) {
        return display.getHeight() + EmiPageLayout.takeExtra();
    }

    @Inject(method = "constructWidgets", at = @At("RETURN"))
    private void gtceu$endPage(int page, int x, int y, int backgroundWidth, int backgroundHeight, CallbackInfoReturnable<List<WidgetGroup>> cir) {
        EmiPageLayout.endPage();
    }
}
