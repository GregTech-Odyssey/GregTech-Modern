package com.gregtechceu.gtceu.api.data.worldgen.bedrock;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.utils.GTMath;
import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.saveddata.SavedData;

import com.gto.datasynclib.datastream.codec.ByteBufCodecs;
import com.gto.datasynclib.datastream.codec.StreamCodec;
import com.gto.datasynclib.util.ByteBufCodecExtends;
import com.gto.datasynclib.util.FileUtil;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Per-level storage of one {@link BedrockVeinKind}: the only place where bedrock veins are generated, read, depleted
 * and
 * restored. Server thread only.
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public final class BedrockVeinSavedData<D extends BedrockVeinDefinition> extends SavedData {

    private static final String PAYLOAD = "payload";
    private static final int VERSION = 1;
    private static final StreamCodec<FriendlyByteBuf, Stored> CODEC = StreamCodec.composite(
            ByteBufCodecs.<FriendlyByteBuf, ResourceLocation>list(ByteBufCodecExtends.RESOURCE_LOCATION_CODEC), Stored::palette,
            ByteBufCodecs.VAR_LONG_ARRAY, Stored::positions,
            ByteBufCodecs.VAR_INT_ARRAY, Stored::veins,
            ByteBufCodecs.VAR_INT_ARRAY, Stored::yields,
            ByteBufCodecs.VAR_INT_ARRAY, Stored::operations,
            Stored::new);

    private final BedrockVeinKind<D> kind;
    private final ServerLevel level;
    private final Long2ObjectOpenHashMap<BedrockVeinEntry<D>> veins = new Long2ObjectOpenHashMap<>();
    private final Reference2IntOpenHashMap<Holder<Biome>> totalWeights = new Reference2IntOpenHashMap<>();
    private final Long2ObjectOpenHashMap<D> origins = new Long2ObjectOpenHashMap<>();

    private record Stored(List<ResourceLocation> palette, long[] positions, int[] veins, int[] yields, int[] operations) {}

    BedrockVeinSavedData(BedrockVeinKind<D> kind, ServerLevel level) {
        this.kind = kind;
        this.level = level;
    }

    BedrockVeinSavedData(BedrockVeinKind<D> kind, ServerLevel level, CompoundTag nbt) {
        this(kind, level);
        if (!nbt.contains(PAYLOAD)) {
            readLegacy(nbt);
            return;
        }
        var stored = FileUtil.readVersioned(Unpooled.wrappedBuffer(nbt.getByteArray(PAYLOAD)), CODEC);
        var registry = kind.registry();
        var palette = new ObjectArrayList<D>(stored.palette().size());
        for (var id : stored.palette()) palette.add(registry.containsKey(id) ? registry.get(id) : null);
        for (int i = 0; i < stored.positions().length; i++) {
            var definition = palette.get(stored.veins()[i]);
            if (definition != null) veins.put(stored.positions()[i], new BedrockVeinEntry<>(definition, stored.yields()[i], stored.operations()[i]));
        }
    }

    @Override
    public CompoundTag save(CompoundTag nbt) {
        int size = 0;
        for (var it = veins.values().iterator(); it.hasNext();) {
            if (it.next().definition() != null) size++;
        }
        var palette = new ObjectArrayList<ResourceLocation>();
        var index = new Reference2IntOpenHashMap<D>();
        index.defaultReturnValue(-1);
        var stored = new Stored(palette, new long[size], new int[size], new int[size], new int[size]);
        int i = 0;
        for (var it = veins.long2ObjectEntrySet().fastIterator(); it.hasNext();) {
            var entry = it.next();
            var vein = entry.getValue();
            var definition = vein.definition();
            if (definition == null) continue;
            int id = index.getInt(definition);
            if (id < 0) {
                id = palette.size();
                index.put(definition, id);
                palette.add(kind.registry().getKey(definition));
            }
            stored.positions()[i] = entry.getLongKey();
            stored.veins()[i] = id;
            stored.yields()[i] = vein.yield();
            stored.operations()[i] = vein.operationsRemaining();
            i++;
        }
        var buf = Unpooled.buffer();
        FileUtil.writeVersioned(buf, VERSION, CODEC, stored);
        var bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        nbt.putByteArray(PAYLOAD, bytes);
        return nbt;
    }

    @Deprecated(since = "0.6.0", forRemoval = true)
    @ApiStatus.ScheduledForRemoval(inVersion = "0.7.0")
    private void readLegacy(CompoundTag nbt) {
        var list = nbt.getList("veinInfo", Tag.TAG_COMPOUND);
        var registry = kind.registry();
        for (int i = 0, size = list.size(); i < size; i++) {
            var tag = list.getCompound(i);
            var data = tag.getCompound(kind.legacyDataKey());
            if (!data.contains("vein")) continue;
            var id = GTUtil.getResourceLocation(data.getString("vein"));
            if (!registry.containsKey(id)) continue;
            veins.put(tag.getLong(kind.legacyPosKey()), new BedrockVeinEntry<>(registry.get(id), data.getInt(kind.legacyYieldKey()), data.getInt("operationsRemaining")));
        }
        setDirty();
    }

    public BedrockVeinKind<D> kind() {
        return kind;
    }

    public BedrockVeinEntry<D> entry(int chunkX, int chunkZ) {
        long pos = ChunkPos.asLong(chunkX, chunkZ);
        var entry = veins.get(pos);
        if (entry != null) return entry;
        kind.layout().generate(this, chunkX, chunkZ);
        entry = veins.get(pos);
        if (entry == null) {
            entry = BedrockVeinEntry.empty();
            veins.put(pos, entry);
        }
        return entry;
    }

    @Nullable
    public D vein(int chunkX, int chunkZ) {
        return entry(chunkX, chunkZ).definition();
    }

    /**
     * One drilling operation finished: deplete the vein by its own chance and amount.
     */
    public void deplete(int chunkX, int chunkZ) {
        var entry = entry(chunkX, chunkZ);
        var definition = entry.definition();
        if (definition == null || definition.depletionChance() == 0) return;
        if (definition.depletionChance() == 100 || GTValues.RNG.nextInt(100) < definition.depletionChance()) {
            if (entry.changeOperations(-definition.depletionAmount())) setDirty();
        }
    }

    /**
     * Add operations back to a vein whose remaining operations are below the limit.
     */
    public void restore(int chunkX, int chunkZ, int amount, float limit) {
        var entry = entry(chunkX, chunkZ);
        if (entry.definition() != null && entry.operationsRemaining() < limit && entry.changeOperations(amount)) setDirty();
    }

    long seed() {
        return level.getSeed();
    }

    @Nullable
    D origin(int chunkX, int chunkZ, int groupSize) {
        long pos = ChunkPos.asLong(chunkX, chunkZ);
        var definition = origins.get(pos);
        if (definition == null && !origins.containsKey(pos)) {
            definition = selectDefinition(Math.floorDiv(chunkX, groupSize), Math.floorDiv(chunkZ, groupSize), chunkX, chunkZ);
            origins.put(pos, definition);
        }
        return definition;
    }

    void put(int chunkX, int chunkZ, D definition, int yield) {
        veins.put(ChunkPos.asLong(chunkX, chunkZ), new BedrockVeinEntry<>(definition, yield, BedrockVeinEntry.MAX_OPERATIONS));
        setDirty();
    }

    @Nullable
    D selectDefinition(int groupX, int groupZ, int chunkX, int chunkZ) {
        var biome = level.getBiome(new BlockPos(chunkX << 4, 64, chunkZ << 4));
        int total = totalWeight(biome);
        if (total <= 0) return null;
        int weight = Math.abs(RandomSource.create(GTMath.hashLongs(level.getSeed(), groupX, groupZ)).nextInt() % total);
        var dimension = level.dimension();
        for (var definition : kind.registry()) {
            int veinWeight = definition.weightIn(biome);
            if (veinWeight > 0 && definition.generatesIn(dimension)) {
                weight -= veinWeight;
                if (weight < 0) return definition;
            }
        }
        return null;
    }

    private int totalWeight(Holder<Biome> biome) {
        if (totalWeights.containsKey(biome)) return totalWeights.getInt(biome);
        int total = 0;
        var dimension = level.dimension();
        for (var definition : kind.registry()) {
            if (definition.generatesIn(dimension)) total += definition.weightIn(biome);
        }
        totalWeights.put(biome, total);
        return total;
    }
}
