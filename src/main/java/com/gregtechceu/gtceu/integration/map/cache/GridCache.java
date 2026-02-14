package com.gregtechceu.gtceu.integration.map.cache;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.data.worldgen.ores.GeneratedVeinMetadata;

import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

import com.fast.fastcollection.OpenCacheHashSet;
import lombok.Getter;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

@Getter
public class GridCache {

    private final Set<GeneratedVeinMetadata> veins = new OpenCacheHashSet<>();

    public boolean addVein(GeneratedVeinMetadata vein) {
        return veins.add(vein);
    }

    public ListTag toNBT(boolean isClient) {
        ListTag result = new ListTag();
        for (GeneratedVeinMetadata pos : veins) {
            result.add((isClient ? GeneratedVeinMetadata.CLIENT_CODEC : GeneratedVeinMetadata.CODEC).encodeStart(NbtOps.INSTANCE, pos).getOrThrow(false, GTCEu.LOGGER::error));
        }
        return result;
    }

    public void fromNBT(ListTag tag, boolean isClient) {
        for (Tag veinTag : tag) {
            GeneratedVeinMetadata vein = (isClient ? GeneratedVeinMetadata.CLIENT_CODEC : GeneratedVeinMetadata.CODEC).parse(NbtOps.INSTANCE, veinTag).getOrThrow(false, GTCEu.LOGGER::error);
            veins.add(vein);
        }
    }

    public List<GeneratedVeinMetadata> getVeinsMatching(Predicate<GeneratedVeinMetadata> predicate) {
        return veins.stream().filter(predicate).toList();
    }

    public void removeVeinsMatching(Predicate<GeneratedVeinMetadata> predicate) {
        veins.removeIf(predicate);
    }
}
