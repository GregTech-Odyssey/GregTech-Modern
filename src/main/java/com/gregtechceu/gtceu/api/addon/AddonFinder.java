package com.gregtechceu.gtceu.api.addon;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.List;

public final class AddonFinder {

    private static final List<IGTAddon> cache = new ObjectArrayList<>();

    public static void add(IGTAddon addon) {
        cache.add(addon);
    }

    public static List<IGTAddon> getAddons() {
        return cache;
    }
}
