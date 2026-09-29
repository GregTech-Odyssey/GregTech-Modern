package com.gregtechceu.gtceu.uiwidgets.patternbuilder;

import com.gregtechceu.gtceu.uipro.utils.UIPreferences;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import it.unimi.dsi.fastutil.objects.ReferenceLinkedOpenHashSet;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class PatternFavorites {

    private static final String KEY = "pattern_builder.favorites";
    @Nullable
    private static ReferenceLinkedOpenHashSet<Item> favorites;

    private PatternFavorites() {}

    private static ReferenceLinkedOpenHashSet<Item> favorites() {
        if (favorites != null) return favorites;
        favorites = new ReferenceLinkedOpenHashSet<>();
        for (var id : UIPreferences.get(KEY, "").split(",")) {
            var location = ResourceLocation.tryParse(id.strip());
            if (location == null) continue;
            var item = BuiltInRegistries.ITEM.get(location);
            if (item != Items.AIR) favorites.add(item);
        }
        return favorites;
    }

    public static boolean isFavorite(Item item) {
        return favorites().contains(item);
    }

    public static void toggle(Item item) {
        var set = favorites();
        if (!set.remove(item)) set.add(item);
        UIPreferences.put(KEY, set.stream().map(i -> BuiltInRegistries.ITEM.getKey(i).toString()).collect(Collectors.joining(",")));
    }

    public static <T> int preferred(List<T> options, Function<T, Item> item) {
        for (int i = 0; i < options.size(); i++) {
            if (isFavorite(item.apply(options.get(i)))) return i;
        }
        return -1;
    }
}
