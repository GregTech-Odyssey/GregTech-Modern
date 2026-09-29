package com.gregtechceu.gtceu.integration.emi.multipage;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblockpro.Structure;
import com.gregtechceu.gtceu.integration.emi.recipe.Ae2PatternTerminalHandler;
import com.gregtechceu.gtceu.uiwidgets.structure.StructurePreviewScreen;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import appeng.menu.me.items.PatternEncodingTermMenu;
import dev.emi.emi.screen.RecipeScreen;

@OnlyIn(Dist.CLIENT)
public final class StructurePreviewTrigger {

    private static boolean pending;

    private StructurePreviewTrigger() {}

    public static void onShown(MultiblockMachineDefinition definition, Structure structure) {
        var minecraft = Minecraft.getInstance();
        if (pending || !(minecraft.screen instanceof RecipeScreen recipes)) return;
        pending = true;
        StructurePreviewScreen.Action encode = null;
        if (recipes.old != null && recipes.old.getMenu() instanceof PatternEncodingTermMenu menu) {
            encode = Ae2PatternTerminalHandler.encodeAction(menu, definition, recipes.old);
        }
        StructurePreviewScreen.open(definition, structure, previous -> () -> {
            boolean back = ((IRecipeScreenReturn) recipes).gtceu$returnToPreviousTab();
            minecraft.setScreen(recipes);
            if (!back) recipes.onClose();
        }, encode);
        minecraft.tell(() -> pending = false);
    }
}
