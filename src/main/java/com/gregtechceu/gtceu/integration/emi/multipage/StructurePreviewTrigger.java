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

    private StructurePreviewTrigger() {}

    public static void open(MultiblockMachineDefinition definition, Structure structure) {
        var minecraft = Minecraft.getInstance();
        if (!(minecraft.screen instanceof RecipeScreen recipes)) {
            StructurePreviewScreen.open(definition, structure, null);
            return;
        }
        StructurePreviewScreen.Action encode = null;
        if (recipes.old != null && recipes.old.getMenu() instanceof PatternEncodingTermMenu menu) {
            encode = Ae2PatternTerminalHandler.encodeAction(menu, definition, recipes.old);
        }
        StructurePreviewScreen.open(definition, structure, previous -> () -> minecraft.setScreen(recipes), encode);
    }
}
