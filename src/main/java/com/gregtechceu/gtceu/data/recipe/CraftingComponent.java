package com.gregtechceu.gtceu.data.recipe;

import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.chemical.material.stack.MaterialEntry;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import org.jetbrains.annotations.NotNull;

import static com.gregtechceu.gtceu.api.GTValues.V;

public class CraftingComponent {

    public static final CraftingComponent EMPTY = CraftingComponent.of(Items.AIR);
    private final Object[] values = new Object[V.length];
    @NotNull
    private Object fallback;

    protected CraftingComponent(@NotNull Object fallback) {
        checkType(fallback);
        this.fallback = fallback;
    }

    public static CraftingComponent of(@NotNull Object fallback) {
        return new CraftingComponent(fallback);
    }

    public static CraftingComponent of(@NotNull TagPrefix prefix, @NotNull Material material) {
        return of(new MaterialEntry(prefix, material));
    }

    @NotNull
    public Object get(int tier) {
        if (this == EMPTY) return Items.AIR;
        if (tier < 0 || tier >= values.length) throw new IllegalArgumentException("Tier out of range of ULV-MAX, tier: " + tier);
        var val = values[tier];
        return val == null ? fallback : val;
    }

    @NotNull
    public CraftingComponent add(int tier, @NotNull Object value) {
        if (this == EMPTY) return this;
        checkType(value);
        values[tier] = value;
        return this;
    }

    @NotNull
    public CraftingComponent add(int tier, @NotNull TagPrefix prefix, @NotNull Material material) {
        return add(tier, new MaterialEntry(prefix, material));
    }

    public void remove(int tier) {
        if (this == EMPTY) return;
        if (tier < 0 || tier >= values.length) throw new IllegalArgumentException("Tier out of range of ULV-MAX, tier: " + tier);
        values[tier] = null;
    }

    private void checkType(@NotNull Object o) {
        if ((o instanceof TagKey<?> tag)) {
            if (!tag.isFor(BuiltInRegistries.ITEM.key())) {
                throw new IllegalArgumentException("TagKey must be of type TagKey<Item>");
            }
        } else if (!(o instanceof Item || o instanceof MaterialEntry)) {
            throw new IllegalArgumentException("Object is not of type Item, MaterialEntry or TagKey<Item>");
        }
    }

    public void setFallback(@NotNull final Object fallback) {
        this.fallback = fallback;
    }
}
