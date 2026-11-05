package com.gregtechceu.gtceu.integration.map.cache.bedrockore;

import com.gregtechceu.gtceu.api.gui.misc.ProspectorMode;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.integration.map.GroupingMapRenderer;
import com.gregtechceu.gtceu.integration.map.layer.builtin.BedrockOreRenderLayer;
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

public class BedrockOreCache {

    private final NestedMap<ResourceKey<Level>, ChunkPos, ProspectorMode.OreInfo[]> bedrockOreCache = NestedMap.createIdentity(O2OOpenCacheHashMap::new);

    /**
     * Records the bedrock ore vein found in a chunk. A later prospection of the same chunk replaces the cached
     * entry: only the first result used to be kept, which left the map showing the materials an update had since
     * changed.
     */
    public void addBedrockOre(ResourceKey<Level> dim, int chunkX, int chunkZ, ProspectorMode.OreInfo[] ores) {
        ChunkPos pos = new ChunkPos(chunkX, chunkZ);
        ProspectorMode.OreInfo[] previous = bedrockOreCache.put(dim, pos, ores);
        addMarker(pos, ores, dim);
        // The map caches the colour of every highlighted region; different materials need those hashes dropped.
        if (previous != null && !sameMaterials(previous, ores)) {
            GroupingMapRenderer.markRefreshPending();
        }
    }

    private static boolean sameMaterials(ProspectorMode.OreInfo[] a, ProspectorMode.OreInfo[] b) {
        if (a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) {
            if (a[i].material() != b[i].material() || a[i].weight() != b[i].weight()) return false;
        }
        return true;
    }

    private static void addMarker(ChunkPos pos, ProspectorMode.OreInfo[] ores, ResourceKey<Level> dim) {
        GroupingMapRenderer.getInstance().addMarker(BedrockOreRenderLayer.getName(ores).getString(),
                BedrockOreRenderLayer.getId(ores, pos), dim, pos, ores);
    }

    /**
     * Restores the cached veins, resolving each one's materials from its current definition. Entries that cannot be
     * tied to a currently registered vein are dropped: their materials were derived by an older version and may no
     * longer describe the chunk.
     */
    public void fromNbt(CompoundTag nbt) {
        var bedrockOreList = nbt.getList("bedrock_ore_veins", Tag.TAG_COMPOUND);
        for (var bedrockOreTagRaw : bedrockOreList) {
            if (!(bedrockOreTagRaw instanceof CompoundTag tag)) continue;
            if (!tag.contains("vein")) continue;
            var vein = GTUtil.getResourceLocation(tag.getString("vein"));
            var definition = GTRegistries.BEDROCK_ORE_DEFINITIONS.get(vein);
            if (definition == null) continue;
            ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION,
                    GTUtil.getResourceLocation(tag.getString("dim")));
            ChunkPos pos = new ChunkPos(tag.getLong("pos"));
            var ores = ProspectorMode.OreInfo.entriesOf(definition, vein, tag.getInt("yield"), tag.getInt("left"));
            bedrockOreCache.put(dim, pos, ores);
            addMarker(pos, ores, dim);
        }
    }

    public CompoundTag toNbt() {
        var result = new CompoundTag();
        var bedrockOreList = new ListTag();
        for (var dimensions : bedrockOreCache.getMap().entrySet()) {
            for (var entry : dimensions.getValue().entrySet()) {
                var ores = entry.getValue();
                if (ores.length == 0) continue;
                CompoundTag tag = new CompoundTag();
                tag.putLong("pos", entry.getKey().toLong());
                tag.putString("dim", dimensions.getKey().location().toString());
                tag.putString("vein", ores[0].vein().toString());
                tag.putInt("yield", ores[0].yield());
                tag.putInt("left", ores[0].left());
                bedrockOreList.add(tag);
            }
        }
        result.put("bedrock_ore_veins", bedrockOreList);
        return result;
    }

    public void clear() {
        bedrockOreCache.clear();
    }
}
