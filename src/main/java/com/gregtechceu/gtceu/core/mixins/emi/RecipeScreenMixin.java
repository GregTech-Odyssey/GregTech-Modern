package com.gregtechceu.gtceu.core.mixins.emi;

import com.gregtechceu.gtceu.integration.emi.multipage.IRecipeScreenReturn;
import com.gregtechceu.gtceu.integration.emi.multipage.MultiblockInfoEmiCategory;

import dev.emi.emi.screen.RecipeScreen;
import dev.emi.emi.screen.RecipeTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(value = RecipeScreen.class, remap = false)
public abstract class RecipeScreenMixin implements IRecipeScreenReturn {

    @Shadow
    private int tabPage;
    @Shadow
    private int tab;
    @Shadow
    private List<RecipeTab> tabs;

    @Unique
    private int gtceu$returnTab = -1;

    @Shadow
    public abstract void setPage(int tp, int t, int p);

    @Inject(method = "setPage", at = @At("HEAD"), require = 0)
    private void gtceu$rememberTab(int tp, int t, int p, CallbackInfo ci) {
        if (tab >= 0 && tab < tabs.size() && !gtceu$isStructureTab(tab)) gtceu$returnTab = tab;
    }

    @Unique
    private boolean gtceu$isStructureTab(int index) {
        return tabs.get(index).category.getId().equals(MultiblockInfoEmiCategory.CATEGORY.getId());
    }

    @Override
    public boolean gtceu$returnToPreviousTab() {
        if (gtceu$returnTab < 0 || gtceu$returnTab >= tabs.size() || gtceu$isStructureTab(gtceu$returnTab)) return false;
        setPage(tabPage, gtceu$returnTab, 0);
        return true;
    }
}
