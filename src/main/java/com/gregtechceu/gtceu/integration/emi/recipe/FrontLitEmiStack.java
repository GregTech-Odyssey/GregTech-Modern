package com.gregtechceu.gtceu.integration.emi.recipe;

import com.gregtechceu.gtceu.client.renderer.machine.MachineRenderer;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

import dev.emi.emi.api.stack.ItemEmiStack;

public class FrontLitEmiStack extends ItemEmiStack {

    public FrontLitEmiStack(ItemStack stack) {
        super(stack);
    }

    @Override
    public void render(GuiGraphics draw, int x, int y, float delta, int flags) {
        MachineRenderer.frontLitGui = true;
        try {
            super.render(draw, x, y, delta, flags);
        } finally {
            MachineRenderer.frontLitGui = false;
        }
    }
}
