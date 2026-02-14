package com.gregtechceu.gtceu.integration.map.cache.fluid;

import com.gregtechceu.gtceu.api.gui.misc.ProspectorMode;
import com.gregtechceu.gtceu.integration.map.GroupingMapRenderer;
import com.gregtechceu.gtceu.integration.map.layer.builtin.FluidRenderLayer;
import com.gregtechceu.gtceu.utils.GTUtil;
import com.gregtechceu.gtceu.utils.collection.NestedMap;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;

import java.util.HashMap;

public class FluidCache {

    private final NestedMap<ResourceKey<Level>, ChunkPos, ProspectorMode.FluidInfo> fluidCache = NestedMap.create(new Reference2ReferenceOpenHashMap<>(), HashMap::new);

    public void addFluid(ResourceKey<Level> dim, int chunkX, int chunkZ, ProspectorMode.FluidInfo fluid) {
        ChunkPos pos = new ChunkPos(chunkX, chunkZ);
        fluidCache.computeIfAbsent(dim, pos, k -> {
            GroupingMapRenderer.getInstance().addMarker(FluidRenderLayer.getName(fluid).getString(),
                    FluidRenderLayer.getId(fluid, pos), dim, pos, fluid);
            return fluid;
        });
    }

    public void fromNbt(CompoundTag nbt) {
        var fluidList = nbt.getList("fluids", Tag.TAG_COMPOUND);
        for (var fluidTagRaw : fluidList) {
            if (fluidTagRaw instanceof CompoundTag fluidTag) {
                ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION,
                        GTUtil.getResourceLocation(fluidTag.getString("dim")));
                ChunkPos pos = new ChunkPos(fluidTag.getLong("pos"));
                var fluid = ProspectorMode.FluidInfo.fromNbt(fluidTag);
                fluidCache.put(dim, pos, fluid);

                GroupingMapRenderer.getInstance().addMarker(FluidRenderLayer.getName(fluid).getString(),
                        FluidRenderLayer.getId(fluid, pos), dim, pos, fluid);
            }
        }
    }

    public CompoundTag toNbt() {
        var result = new CompoundTag();
        var fluidList = new ListTag();
        for (var dimensions : fluidCache.getMap().entrySet()) {
            for (var entry : dimensions.getValue().entrySet()) {
                CompoundTag tag = entry.getValue().toNbt();
                tag.putLong("pos", entry.getKey().toLong());
                tag.putString("dim", dimensions.getKey().location().toString());
                fluidList.add(tag);
            }
        }
        result.put("fluids", fluidList);
        return result;
    }

    public void clear() {
        fluidCache.clear();
    }
}
