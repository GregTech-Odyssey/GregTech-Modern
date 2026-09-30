package com.gregtechceu.gtceu.integration.emi.recipe;

import com.gregtechceu.gtceu.client.renderer.machine.MachineRenderer;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

import com.google.gson.JsonElement;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.ItemEmiStack;
import dev.emi.emi.api.stack.serializer.EmiIngredientSerializer;

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

    public static final class Serializer implements EmiIngredientSerializer<FrontLitEmiStack> {

        @Override
        public String getType() {
            return "gtceu_front_lit_item";
        }

        @Override
        public EmiIngredient deserialize(JsonElement element) {
            return EmiStack.EMPTY;
        }

        @Override
        public JsonElement serialize(FrontLitEmiStack stack) {
            return EmiIngredientSerializer.getSerialized(EmiStack.of(stack.getItemStack(), stack.getAmount()));
        }
    }
}
