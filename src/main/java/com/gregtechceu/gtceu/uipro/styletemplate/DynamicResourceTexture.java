package com.gregtechceu.gtceu.uipro.styletemplate;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * A sub-texture whose resource location is looked up when it is drawn.
 * <p>
 * UI material packs may replace an atlas while a screen is open. LDLib's {@link ResourceTexture} stores its
 * location at construction time, so a small indirection here keeps already-created widgets in sync with the
 * selected material pack.
 */
public final class DynamicResourceTexture implements IGuiTexture {

    private final Supplier<ResourceLocation> location;
    private final double u, v, width, height;
    private ResourceLocation cachedLocation;
    private ResourceTexture cachedTexture;

    public DynamicResourceTexture(Supplier<ResourceLocation> location) {
        this(location, 0, 0, 1, 1);
    }

    public DynamicResourceTexture(Supplier<ResourceLocation> location, double u, double v, double width, double height) {
        this.location = Objects.requireNonNull(location);
        this.u = u;
        this.v = v;
        this.width = width;
        this.height = height;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void draw(GuiGraphics graphics, int mouseX, int mouseY, float x, float y, int width, int height) {
        texture().draw(graphics, mouseX, mouseY, x, y, width, height);
    }

    @OnlyIn(Dist.CLIENT)
    private ResourceTexture texture() {
        var next = location.get();
        if (!next.equals(cachedLocation)) {
            cachedLocation = next;
            cachedTexture = new ResourceTexture(next).getSubTexture(u, v, width, height);
        }
        return cachedTexture;
    }
}
