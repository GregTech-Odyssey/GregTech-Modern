package com.gregtechceu.gtceu.client.renderer.machine;

import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IStorageAppearance;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Retextures casing quads while retaining the storage geometry and display overlays. */
public class StorageMachineRenderer extends TieredHullMachineRenderer {

    @OnlyIn(Dist.CLIENT)
    private ConcurrentHashMap<Block, ConcurrentHashMap<BakedQuad, BakedQuad>> appearanceQuads;

    public StorageMachineRenderer(int tier, ResourceLocation modelLocation) {
        super(tier, modelLocation);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void renderBaseModel(List<BakedQuad> quads, MachineDefinition definition, @Nullable MetaMachine machine,
                                ModelState modelState, @Nullable Direction side, RandomSource random) {
        var base = getRotatedModel(modelState).getQuads(definition.defaultBlockState(), side, random);
        Block block = machine instanceof IStorageAppearance storage ? storage.getStorageAppearance().getBlock() : Blocks.AIR;
        if (block == Blocks.AIR) {
            quads.addAll(base);
            return;
        }
        if (appearanceQuads == null) appearanceQuads = new ConcurrentHashMap<>();
        var cache = appearanceQuads.get(block);
        if (cache == null) {
            cache = new ConcurrentHashMap<>();
            appearanceQuads.put(block, cache);
        }
        for (var quad : base) {
            var texture = quad.getSprite().contents().name();
            if (!texture.equals(override.get("side")) && !texture.equals(override.get("top")) && !texture.equals(override.get("bottom"))) {
                quads.add(quad);
                continue;
            }
            var replacement = cache.get(quad);
            if (replacement == null) {
                replacement = retexture(quad, block, random);
                cache.put(quad, replacement);
            }
            quads.add(replacement);
        }
    }

    @OnlyIn(Dist.CLIENT)
    private static BakedQuad retexture(BakedQuad quad, Block block, RandomSource random) {
        var minecraft = Minecraft.getInstance();
        var state = block.defaultBlockState();
        var model = minecraft.getBlockRenderer().getBlockModel(state);
        var faces = model.getQuads(state, quad.getDirection(), random);
        if (faces.isEmpty()) faces = model.getQuads(state, null, random);
        BakedQuad source = null;
        for (var face : faces) {
            if (face.getDirection() == quad.getDirection()) {
                source = face;
                break;
            }
        }
        var sprite = source == null ? model.getParticleIcon() : source.getSprite();
        int color = source != null && source.isTinted() ? minecraft.getBlockColors().getColor(state, null, null, source.getTintIndex()) : -1;
        int abgr = 0xff000000 | (color & 255) << 16 | color & 0xff00 | (color >> 16 & 255);
        var vertices = quad.getVertices().clone();
        int stride = vertices.length / 4;
        var original = quad.getSprite();
        for (int i = 0; i < 4; i++) {
            int offset = i * stride;
            vertices[offset + 3] = abgr;
            vertices[offset + 4] = Float.floatToRawIntBits(sprite.getU(original.getUOffset(Float.intBitsToFloat(vertices[offset + 4]))));
            vertices[offset + 5] = Float.floatToRawIntBits(sprite.getV(original.getVOffset(Float.intBitsToFloat(vertices[offset + 5]))));
        }
        return new BakedQuad(vertices, -1, quad.getDirection(), sprite, quad.isShade(), quad.hasAmbientOcclusion());
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void onPrepareTextureAtlas(ResourceLocation atlasName, Consumer<ResourceLocation> register) {
        super.onPrepareTextureAtlas(atlasName, register);
        if (atlasName.equals(TextureAtlas.LOCATION_BLOCKS) && appearanceQuads != null) appearanceQuads.clear();
    }
}
