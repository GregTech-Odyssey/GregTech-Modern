package com.gregtechceu.gtceu.client.renderer.machine;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.item.StorageUpgradeItem;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IStorageAppearance;
import com.gregtechceu.gtceu.api.machine.trait.StorageUpgrades;
import com.gregtechceu.gtceu.utils.GTMatrixUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Retextures casing quads and draws installed upgrades along the front's bottom edge. */
public class StorageMachineRenderer extends TieredHullMachineRenderer {

    private static final Quaternionf[] UPGRADE_ROTATIONS = new Quaternionf[24];
    private static final Quaternionf UPGRADE_ITEM_ROTATION = new Quaternionf().rotationY((float) Math.PI);
    private static final Int2ObjectOpenHashMap<ItemStack> UPGRADE_STACKS = new Int2ObjectOpenHashMap<>(5);

    @OnlyIn(Dist.CLIENT)
    private ConcurrentHashMap<Block, ConcurrentHashMap<BakedQuad, BakedQuad>> appearanceQuads;

    public StorageMachineRenderer(int tier, ResourceLocation modelLocation) {
        super(tier, modelLocation);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void render(BlockEntity blockEntity, float partialTicks, PoseStack poseStack, MultiBufferSource buffer,
                       int combinedLight, int combinedOverlay) {
        if (!(blockEntity instanceof MetaMachineBlockEntity holder)) return;
        var machine = holder.getMetaMachine();
        var upgrades = machine.getTraits(StorageUpgrades.class);
        if (upgrades.length == 0) return;
        var tiers = upgrades[0].getTiers();
        int first = 0;
        while (first < tiers.length && tiers[first] == 0) first++;
        if (first == tiers.length) return;

        var front = machine.getFrontFacing();
        var upward = machine.getUpwardsFacing();
        int index = front.ordinal() * 4 + upward.get2DDataValue();
        var rotation = UPGRADE_ROTATIONS[index];
        if (rotation == null) {
            rotation = new Quaternionf().setFromNormalized(GTMatrixUtils.createRotationState(front, upward).getRotation().getMatrix());
            UPGRADE_ROTATIONS[index] = rotation;
        }
        var minecraft = Minecraft.getInstance();
        poseStack.pushPose();
        poseStack.translate(.5, .5, .5);
        poseStack.mulPose(rotation);
        poseStack.translate(-.5, -.5, -.5);
        for (int slot = first; slot < tiers.length; slot++) {
            int tier = tiers[slot];
            if (tier == 0) continue;
            var stack = UPGRADE_STACKS.get(tier);
            if (stack == null) {
                stack = new ItemStack(StorageUpgradeItem.byTier(tier));
                UPGRADE_STACKS.put(tier, stack);
            }
            poseStack.pushPose();
            // One icon per casing pixel, left to right along the outer bottom edge of the front.
            poseStack.translate(1 - (slot + .5F) / 16, 1F / 32, -1F / 1024);
            poseStack.scale(1F / 16, 1F / 16, 1F / 1024);
            poseStack.mulPose(UPGRADE_ITEM_ROTATION);
            minecraft.getItemRenderer().renderStatic(stack, ItemDisplayContext.GUI, 0xf000f0,
                    OverlayTexture.NO_OVERLAY, poseStack, buffer, minecraft.level, 0);
            poseStack.popPose();
        }
        poseStack.popPose();
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
        var cache = appearanceQuads.computeIfAbsent(block, k -> new ConcurrentHashMap<>());
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
