package com.gregtechceu.gtceu.uipro.render;

import net.minecraft.resources.ResourceLocation;

public record ImageRef(ResourceLocation location, boolean isSprite) {

    private static final String PNG_SUFFIX = ".png";

    public static ImageRef file(ResourceLocation location) {
        return new ImageRef(location, false);
    }

    public static ImageRef sprite(ResourceLocation location) {
        return new ImageRef(location, true);
    }

    public static ImageRef of(ResourceLocation location) {
        return new ImageRef(location, !location.getPath().endsWith(PNG_SUFFIX));
    }

    public static ImageRef of(String id) {
        return of(new ResourceLocation(id));
    }
}
