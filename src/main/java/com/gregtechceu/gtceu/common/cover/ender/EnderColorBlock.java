package com.gregtechceu.gtceu.common.cover.ender;

import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.data.SyncValue;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

final class EnderColorBlock extends UIElement {

    public static final int SIZE = UISizes.CONTROL_HEIGHT;
    private static final int OPAQUE = 0xFF000000;

    private final SyncValue<Integer> color;

    private EnderColorBlock(Supplier<Integer> color, int initial) {
        layout(l -> l.size(SIZE, SIZE));
        this.color = addSyncValue(SyncValue.ofInt(color, initial));
    }

    static EnderColorBlock of(IntSupplier serverColor) {
        return new EnderColorBlock(serverColor::getAsInt, -1);
    }

    static EnderColorBlock constant(int color) {
        return new EnderColorBlock(() -> color, color);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int x = getPositionX(), y = getPositionY(), width = getSizeWidth(), height = getSizeHeight();
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, color.getValue() | OPAQUE);
        UITheme.drawOutline(graphics, x, y, width, height, UITheme.STATUS_LAMP_OUTLINE);
        super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
    }
}
