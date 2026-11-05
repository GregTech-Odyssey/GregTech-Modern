package com.gregtechceu.gtceu.integration.map.cache.fluid;

import com.gregtechceu.gtceu.api.gui.misc.ProspectorMode;
import com.gregtechceu.gtceu.integration.map.GroupingMapRenderer;
import com.gregtechceu.gtceu.integration.map.layer.builtin.FluidRenderLayer;
import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import com.gto.fastcollection.fastutil.O2OOpenCacheHashMap;
import com.gto.fastcollection.map.NestedMap;

public class FluidCache {

    private final NestedMap<ResourceKey<Level>, ChunkPos, ProspectorMode.FluidInfo> fluidCache = NestedMap.createIdentity(O2OOpenCacheHashMap::new);

    /**
     * Records the fluid vein found in a chunk. A later prospection of the same chunk replaces the cached entry: only
     * the first result used to be kept, which left the map showing a fluid that an update had since changed.
     */
    public void addFluid(ResourceKey<Level> dim, int chunkX, int chunkZ, ProspectorMode.FluidInfo fluid) {
        ChunkPos pos = new ChunkPos(chunkX, chunkZ);
        ProspectorMode.FluidInfo previous = fluidCache.put(dim, pos, fluid);
        addMarker(pos, fluid, dim);
        // The map caches the colour of every highlighted region; a different fluid needs those hashes dropped.
        if (previous != null && previous.fluid() != fluid.fluid()) {
            GroupingMapRenderer.getInstance().refreshMarkers();
        }
    }

    private static void addMarker(ChunkPos pos, ProspectorMode.FluidInfo fluid, ResourceKey<Level> dim) {
        GroupingMapRenderer.getInstance().addMarker(FluidRenderLayer.getName(fluid).getString(),
                FluidRenderLayer.getId(fluid, pos), dim, pos, fluid);
    }

    public void fromNbt(CompoundTag nbt) {
        var fluidList = nbt.getList("fluids", Tag.TAG_COMPOUND);
        for (var fluidTagRaw : fluidList) {
            if (fluidTagRaw instanceof CompoundTag fluidTag) {
                ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION,
                        GTUtil.getResourceLocation(fluidTag.getString("dim")));
                ChunkPos pos = new ChunkPos(fluidTag.getLong("pos"));
                var fluid = ProspectorMode.FluidInfo.fromNbt(fluidTag);
                if (fluid == null) continue;
                fluidCache.put(dim, pos, fluid);
                addMarker(pos, fluid, dim);
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
