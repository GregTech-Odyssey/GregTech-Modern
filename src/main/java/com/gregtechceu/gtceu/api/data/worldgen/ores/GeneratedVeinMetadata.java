package com.gregtechceu.gtceu.api.data.worldgen.ores;

import com.gregtechceu.gtceu.api.data.worldgen.GTOreDefinition;
import com.gregtechceu.gtceu.api.registry.GTRegistries;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public final class GeneratedVeinMetadata {

    public static final Codec<ChunkPos> CHUNK_POS_CODEC = Codec.LONG.xmap(ChunkPos::new, ChunkPos::toLong);
    public static final Codec<GTOreDefinition> CLIENT_DEFINITION_CODEC = ResourceLocation.CODEC.flatXmap(rl -> Optional.ofNullable(GTRegistries.ORE_VEINS.get(rl)).map(DataResult::success).orElseGet(() -> DataResult.error(() -> "Unknown registry key in client ore veins: " + rl)), obj -> Optional.ofNullable(GTRegistries.ORE_VEINS.getKey(obj)).map(DataResult::success).orElseGet(() -> DataResult.error(() -> "Unknown registry element in client ore veins: " + obj)));
    public static final Codec<GeneratedVeinMetadata> CODEC = RecordCodecBuilder.create(instance -> instance.group(ResourceLocation.CODEC.fieldOf("id").forGetter(GeneratedVeinMetadata::id), CHUNK_POS_CODEC.fieldOf("origin_chunk").forGetter(GeneratedVeinMetadata::originChunk), BlockPos.CODEC.fieldOf("center").forGetter(GeneratedVeinMetadata::center), GTRegistries.ORE_VEINS.codec().fieldOf("definition").forGetter(GeneratedVeinMetadata::definition), Codec.BOOL.optionalFieldOf("depleted", false).forGetter(GeneratedVeinMetadata::depleted)).apply(instance, GeneratedVeinMetadata::new));
    public static final Codec<GeneratedVeinMetadata> CLIENT_CODEC = RecordCodecBuilder.create(instance -> instance.group(ResourceLocation.CODEC.fieldOf("id").forGetter(GeneratedVeinMetadata::id), CHUNK_POS_CODEC.fieldOf("origin_chunk").forGetter(GeneratedVeinMetadata::originChunk), BlockPos.CODEC.fieldOf("center").forGetter(GeneratedVeinMetadata::center), CLIENT_DEFINITION_CODEC.fieldOf("definition").forGetter(GeneratedVeinMetadata::definition), Codec.BOOL.optionalFieldOf("depleted", false).forGetter(GeneratedVeinMetadata::depleted)).apply(instance, GeneratedVeinMetadata::new));
    @NotNull
    private final ResourceLocation id;
    @NotNull
    private final ChunkPos originChunk;
    @NotNull
    private final BlockPos center;
    @NotNull
    private GTOreDefinition definition;
    private boolean depleted;

    public GeneratedVeinMetadata(@NotNull ResourceLocation id, @NotNull ChunkPos originChunk, @NotNull BlockPos center, @NotNull GTOreDefinition definition) {
        this(id, originChunk, center, definition, false);
    }

    public GeneratedVeinMetadata(@NotNull ResourceLocation id, @NotNull ChunkPos originChunk, @NotNull BlockPos center, @NotNull GTOreDefinition definition, boolean depleted) {
        this.id = id;
        this.originChunk = originChunk;
        this.center = center;
        this.definition = definition;
        this.depleted = depleted;
    }

    public static GeneratedVeinMetadata readFromPacket(FriendlyByteBuf buf) {
        ResourceLocation id = buf.readResourceLocation();
        ChunkPos origin = new ChunkPos(buf.readVarLong());
        BlockPos center = BlockPos.of(buf.readVarLong());
        GTOreDefinition def = GTRegistries.ORE_VEINS.get(buf.readResourceLocation());
        return new GeneratedVeinMetadata(id, origin, center, def, false);
    }

    public void writeToPacket(FriendlyByteBuf buf) {
        buf.writeResourceLocation(this.id);
        buf.writeVarLong(this.originChunk.toLong());
        buf.writeVarLong(this.center.asLong());
        buf.writeResourceLocation(GTRegistries.ORE_VEINS.getKey(this.definition));
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof GeneratedVeinMetadata that)) return false;
        return id.equals(that.id) && originChunk.equals(that.originChunk) && center.equals(that.center) && definition.equals(that.definition);
    }

    @Override
    public int hashCode() {
        int result = id.hashCode();
        result = 31 * result + originChunk.hashCode();
        result = 31 * result + center.hashCode();
        result = 31 * result + definition.hashCode();
        return result;
    }

    @NotNull
    public ResourceLocation id() {
        return this.id;
    }

    @NotNull
    public ChunkPos originChunk() {
        return this.originChunk;
    }

    @NotNull
    public BlockPos center() {
        return this.center;
    }

    @NotNull
    public GTOreDefinition definition() {
        return this.definition;
    }

    /**
     * @return {@code this}.
     */
    public GeneratedVeinMetadata definition(@NotNull final GTOreDefinition definition) {
        if (definition == null) {
            throw new NullPointerException("definition is marked non-null but is null");
        }
        this.definition = definition;
        return this;
    }

    public boolean depleted() {
        return this.depleted;
    }

    /**
     * @return {@code this}.
     */
    public GeneratedVeinMetadata depleted(final boolean depleted) {
        this.depleted = depleted;
        return this;
    }
}
