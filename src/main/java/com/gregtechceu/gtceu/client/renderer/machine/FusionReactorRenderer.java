package com.gregtechceu.gtceu.client.renderer.machine;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.pattern.util.RelativeDirection;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.FusionReactorMachine;
import com.gregtechceu.gtceu.renderpro.EffectClock;
import com.gregtechceu.gtceu.renderpro.EffectPalette;
import com.gregtechceu.gtceu.renderpro.ParticleRing;
import com.gregtechceu.gtceu.renderpro.RenderProFrame;
import com.gregtechceu.gtceu.renderpro.RingStyle;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import com.mojang.blaze3d.vertex.PoseStack;

public class FusionReactorRenderer extends WorkableCasingMachineRenderer {

    public static final float FADEOUT = 60;
    private static final float RISE = 30;
    private static final int FALLBACK_COLOR = 0xFFA040;
    private static final float RING_RADIUS = 6;
    private static final float TUBE_RADIUS = 0.45F;
    private static final int[] COUNTS = { 720, 960, 1200 };
    private static final ParticleRing[] RINGS = new ParticleRing[COUNTS.length];
    private static final RingStyle STYLE = new RingStyle(2.2F, 150, 0.95F, 70, 0.35F, 120);

    public FusionReactorRenderer(ResourceLocation baseCasing, ResourceLocation workableModel) {
        super(baseCasing, workableModel);
    }

    @OnlyIn(Dist.CLIENT)
    private static ParticleRing ring(int tier) {
        int index = Mth.clamp(tier - GTValues.LuV, 0, COUNTS.length - 1);
        var ring = RINGS[index];
        if (ring == null) {
            float boost = 1 + 0.25F * index;
            ring = new ParticleRing(RING_RADIUS, TUBE_RADIUS, COUNTS[index], 0.14F * boost, 0.34F * boost, 0x5EED0000L + index);
            RINGS[index] = ring;
        }
        return ring;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void render(BlockEntity blockEntity, float partialTicks, PoseStack stack, MultiBufferSource buffer,
                       int combinedLight, int combinedOverlay) {
        if (!(blockEntity instanceof MetaMachineBlockEntity machineBlockEntity) ||
                !(machineBlockEntity.getMetaMachine() instanceof FusionReactorMachine machine)) {
            return;
        }
        var clock = EffectClock.of(machine, RISE, FADEOUT);
        clock.update(machine.recipeLogic.isWorking(), machine.getOffsetTimer() + partialTicks);
        if (!clock.visible()) return;
        var front = machine.getFrontFacing();
        var upwards = machine.getUpwardsFacing();
        var flipped = machine.isFlipped();
        var back = RelativeDirection.BACK.getRelative(front, upwards, flipped);
        var axis = RelativeDirection.UP.getRelative(front, upwards, flipped).getAxis();
        if (!RenderProFrame.collecting(blockEntity.getLevel())) return;
        var pos = machine.getPos();
        int base = clock.color(machine.getColor(), FALLBACK_COLOR);
        RenderProFrame.ring(ring(machine.getTier()), STYLE, pos.getX() + back.getStepX() * 7 + 0.5, pos.getY() + back.getStepY() * 7 + 0.5,
                pos.getZ() + back.getStepZ() * 7 + 0.5, axis, clock.time(), clock.intensity(), base, EffectPalette.shades(base));
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public boolean hasTESR(BlockEntity blockEntity) {
        return true;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public boolean isGlobalRenderer(BlockEntity blockEntity) {
        return true;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public int getViewDistance() {
        return 64;
    }
}
