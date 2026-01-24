package com.gregtechceu.gtceu.integration.map.cache.bedrockore;

import com.gregtechceu.gtceu.api.gui.misc.ProspectorMode;
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

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

public class BedrockOreCache {

    private final Table<ResourceKey<Level>, ChunkPos, ProspectorMode.OreInfo[]> bedrockOreCache = HashBasedTable.create();

    public void addBedrockOre(ResourceKey<Level> dim, int chunkX, int chunkZ, ProspectorMode.OreInfo[] ores) {
        ChunkPos pos = new ChunkPos(chunkX, chunkZ);
        if (!bedrockOreCache.contains(dim, pos)) {
            bedrockOreCache.put(dim, pos, ores);
            GroupingMapRenderer.getInstance().addMarker(BedrockOreRenderLayer.getName(ores).getString(),
                    BedrockOreRenderLayer.getId(ores, pos), dim, pos, ores);
        }
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
        for (var dimensions : bedrockOreCache.rowMap().entrySet()) {
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
