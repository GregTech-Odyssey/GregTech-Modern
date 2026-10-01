package com.gregtechceu.gtceu.uipro.render;

import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class FittedText {

    private Component component = Component.empty();
    private String plain = "";
    private int plainGeneration = -1;
    private String source = "";
    private int maxWidth = -1;
    private int generation = -1;
    private String fitted = "";
    private int width;

    public String fit(String text, int maxWidth) {
        if (maxWidth != this.maxWidth || generation != UIText.generation() || !text.equals(source)) {
            source = text;
            this.maxWidth = maxWidth;
            generation = UIText.generation();
            fitted = UIText.fit(text, maxWidth);
            width = UIText.width(fitted);
        }
        return fitted;
    }

    public String fit(Component text, int maxWidth) {
        if (text != component || plainGeneration != UIText.generation()) {
            component = text;
            plain = text.getString();
            plainGeneration = UIText.generation();
        }
        return fit(plain, maxWidth);
    }

    public int width() {
        return width;
    }

    public boolean isTruncated() {
        return fitted != source;
    }
}
