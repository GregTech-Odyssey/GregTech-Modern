package com.gregtechceu.gtceu.api.data.chemical.material.stack;

import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public record MaterialEntry(@NotNull TagPrefix tagPrefix, @NotNull Material material) {

    public MaterialEntry {
        Objects.requireNonNull(tagPrefix, "MaterialEntry cannot have a null TagPrefix");
        Objects.requireNonNull(material, "MaterialEntry cannot have a null Material");
    }

    public boolean isIgnored() {
        return tagPrefix().isIgnored(material());
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof MaterialEntry(TagPrefix prefix, Material mat)) {
            return mat == material && prefix == tagPrefix;
        }
        return false;
    }

    @Override
    public String toString() {
        var tags = tagPrefix.getItemTags(material);
        if (tags.length == 0) {
            return tagPrefix.name + "/" + material.getName();
        }
        return tags[0].location().toString();
    }
}
