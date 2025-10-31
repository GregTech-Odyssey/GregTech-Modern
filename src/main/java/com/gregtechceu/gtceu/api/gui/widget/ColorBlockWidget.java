package com.gregtechceu.gtceu.api.gui.widget;

import com.lowdragmc.lowdraglib.gui.util.DrawerHelper;
import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.function.IntSupplier;

public class ColorBlockWidget extends Widget {

    private IntSupplier colorSupplier;
    @Getter
    private int currentColor;
    private static boolean isShowAlpha = false;

    public ColorBlockWidget(int x, int y, int width, int height) {
        super(x, y, width, height);
        this.currentColor = -1;
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (isClientSideWidget && colorSupplier != null) {
            currentColor = colorSupplier.getAsInt();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isMouseOverElement(mouseX, mouseY)) {
            playButtonClickSound();
            isShowAlpha = !isShowAlpha;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void drawInBackground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int x = getPosition().x + 1;
        int y = getPosition().y + 1;
        int width = getSize().width - 2;
        int height = getSize().height - 2;
        if (colorSupplier != null) {
            currentColor = colorSupplier.getAsInt();
        }
        final int BORDER_COLOR = -16777216;
        int opaqueColor = isShowAlpha ? currentColor : currentColor | -16777216;
        graphics.fill(x, y, x + width, y + height, opaqueColor);
        DrawerHelper.drawBorder(graphics, x, y, width, height, BORDER_COLOR, 1);
    }

    /**
     * @return {@code this}.
     */
    public ColorBlockWidget setColorSupplier(final IntSupplier colorSupplier) {
        this.colorSupplier = colorSupplier;
        return this;
    }

    /**
     * @return {@code this}.
     */
    public ColorBlockWidget setCurrentColor(final int currentColor) {
        this.currentColor = currentColor;
        return this;
    }
}
