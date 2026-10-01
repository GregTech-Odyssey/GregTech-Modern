package com.gregtechceu.gtceu.uipro.render;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Arrays;
import java.util.function.Supplier;

/**
 * 按 {@link UIStates} 状态位挑贴图（仿 ModernUI {@code StateListDrawable}）：按添加顺序取第一条"必含位全在、排除位全不在"的贴图。
 */
public class StateTexture implements IGuiTexture {

    private final int[] required;
    private final int[] excluded;
    private final IGuiTexture[] textures;

    protected StateTexture(int[] required, int[] excluded, IGuiTexture[] textures) {
        this.required = required;
        this.excluded = excluded;
        this.textures = textures;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static StateTexture dynamic(Supplier<StateTexture> current) {
        return new Dynamic(current);
    }

    public static IGuiTexture layers(IGuiTexture... layers) {
        return new Layers(layers);
    }

    public IGuiTexture select(int states) {
        for (int i = 0; i < textures.length; i++) {
            if ((states & required[i]) == required[i] && (states & excluded[i]) == 0) return textures[i];
        }
        return IGuiTexture.EMPTY;
    }

    @OnlyIn(Dist.CLIENT)
    public void draw(GuiGraphics graphics, int states, int x, int y, int width, int height) {
        select(states).draw(graphics, 0, 0, x, y, width, height);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void draw(GuiGraphics graphics, int mouseX, int mouseY, float x, float y, int width, int height) {
        select(UIStates.NONE).draw(graphics, mouseX, mouseY, x, y, width, height);
    }

    public static final class Builder {

        private int size;
        private int[] required = new int[4];
        private int[] excluded = new int[4];
        private IGuiTexture[] textures = new IGuiTexture[4];

        private Builder() {}

        public Builder add(int required, IGuiTexture texture) {
            return add(required, UIStates.NONE, texture);
        }

        public Builder add(int required, int excluded, IGuiTexture texture) {
            if (size == textures.length) {
                this.required = Arrays.copyOf(this.required, size * 2);
                this.excluded = Arrays.copyOf(this.excluded, size * 2);
                this.textures = Arrays.copyOf(this.textures, size * 2);
            }
            this.required[size] = required;
            this.excluded[size] = excluded;
            this.textures[size] = texture;
            size++;
            return this;
        }

        public StateTexture build(IGuiTexture fallback) {
            add(UIStates.NONE, fallback);
            return new StateTexture(Arrays.copyOf(required, size), Arrays.copyOf(excluded, size), Arrays.copyOf(textures, size));
        }
    }

    private static final class Dynamic extends StateTexture {

        private final Supplier<StateTexture> current;

        private Dynamic(Supplier<StateTexture> current) {
            super(new int[0], new int[0], new IGuiTexture[0]);
            this.current = current;
        }

        @Override
        public IGuiTexture select(int states) {
            return current.get().select(states);
        }
    }

    private record Layers(IGuiTexture[] layers) implements IGuiTexture {

        @Override
        @OnlyIn(Dist.CLIENT)
        public void draw(GuiGraphics graphics, int mouseX, int mouseY, float x, float y, int width, int height) {
            for (var layer : layers) layer.draw(graphics, mouseX, mouseY, x, y, width, height);
        }
    }
}
