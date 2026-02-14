package com.gregtechceu.gtceu.integration.map.cache.bedrockore;

import com.gregtechceu.gtceu.api.gui.misc.ProspectorMode;
import com.gregtechceu.gtceu.integration.map.GroupingMapRenderer;
import com.gregtechceu.gtceu.integration.map.layer.builtin.BedrockOreRenderLayer;
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

public class BedrockOreCache {

    private final NestedMap<ResourceKey<Level>, ChunkPos, ProspectorMode.OreInfo[]> bedrockOreCache = NestedMap.create(new Reference2ReferenceOpenHashMap<>(), HashMap::new);

    public void addBedrockOre(ResourceKey<Level> dim, int chunkX, int chunkZ, ProspectorMode.OreInfo[] ores) {
        ChunkPos pos = new ChunkPos(chunkX, chunkZ);
        bedrockOreCache.computeIfAbsent(dim, pos, k -> {
            GroupingMapRenderer.getInstance().addMarker(BedrockOreRenderLayer.getName(ores).getString(),
                    BedrockOreRenderLayer.getId(ores, pos), dim, pos, ores);
            return ores;
        });
    }

    public void fromNbt(CompoundTag nbt) {
        var bedrockOreList = nbt.getList("bedrock_ore_veins", Tag.TAG_COMPOUND);
        for (var bedrockOreTagRaw : bedrockOreList) {
            if (bedrockOreTagRaw instanceof CompoundTag bedrockOreTag) {
                ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION,
                        GTUtil.getResourceLocation(bedrockOreTag.getString("dim")));
                ChunkPos pos = new ChunkPos(bedrockOreTag.getLong("pos"));

                var oresListTag = bedrockOreTag.getList("ores", Tag.TAG_COMPOUND);
                ProspectorMode.OreInfo[] ores = new ProspectorMode.OreInfo[oresListTag.size()];
                for (int i = 0; i < oresListTag.size(); i++) {
                    ores[i] = ProspectorMode.BEDROCK_ORE.deserialize(
                            new net.minecraft.network.FriendlyByteBuf(
                                    io.netty.buffer.Unpooled.wrappedBuffer(
                                            oresListTag.getCompound(i).getByteArray("data"))));
                }
                bedrockOreCache.put(dim, pos, ores);

                GroupingMapRenderer.getInstance().addMarker(BedrockOreRenderLayer.getName(ores).getString(),
                        BedrockOreRenderLayer.getId(ores, pos), dim, pos, ores);
            }
        }
    }

    public CompoundTag toNbt() {
        var result = new CompoundTag();
        var bedrockOreList = new ListTag();
        for (var dimensions : bedrockOreCache.getMap().entrySet()) {
            for (var entry : dimensions.getValue().entrySet()) {
                CompoundTag tag = new CompoundTag();
                tag.putLong("pos", entry.getKey().toLong());
                tag.putString("dim", dimensions.getKey().location().toString());

                var oresListTag = new ListTag();
                for (var ore : entry.getValue()) {
                    var buf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
                    ProspectorMode.BEDROCK_ORE.serialize(ore, buf);
                    byte[] data = new byte[buf.readableBytes()];
                    buf.readBytes(data);
                    buf.release();

                    CompoundTag oreTag = new CompoundTag();
                    oreTag.putByteArray("data", data);
                    oresListTag.add(oreTag);
                }
                tag.put("ores", oresListTag);
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
