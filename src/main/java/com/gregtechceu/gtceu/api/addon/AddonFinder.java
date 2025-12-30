package com.gregtechceu.gtceu.api.addon;

import java.util.ArrayList;
import java.util.List;

public final class AddonFinder {

    private static final List<IGTAddon> cache = new ArrayList<>();

    public static void add(IGTAddon addon) {
        cache.add(addon);
    }

    public static List<IGTAddon> getAddons() {
        return cache;
    }
}
