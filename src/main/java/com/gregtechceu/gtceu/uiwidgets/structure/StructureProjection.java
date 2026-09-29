package com.gregtechceu.gtceu.uiwidgets.structure;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.multiblockpro.Layout;
import com.gregtechceu.gtceu.api.machine.multiblockpro.StructureBlocks;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;

import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.jetbrains.annotations.Nullable;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Vector3f;

@OnlyIn(Dist.CLIENT)
public final class StructureProjection {

    private static final float BLOCK_SCALE = 0.8f;
    private static final int CHECK_INTERVAL = 10;

    @Nullable
    private static StructureRenderer renderer;
    @Nullable
    private static PreviewLevel preview;
    @Nullable
    private static Level world;
    @Nullable
    private static BlockPos anchor;
    @Nullable
    private static MultiblockMachineDefinition definition;
    private static int remaining = -1;
    private static int ticks;

    private StructureProjection() {}

    public static void project(IMultiController controller, Layout layout, Item[] items) {
        var machine = controller.self();
        var blocks = StructureBlocks.worldBlocks(layout, items, machine.getPos(), machine.getFrontFacing(), machine.getUpwardsFacing(),
                machine.isFlipped());
        show(machine.getPos(), machine.getDefinition(), blocks, -1);
    }

    public static void show(BlockPos pos, @Nullable MultiblockMachineDefinition owner, Long2ObjectOpenHashMap<BlockState> blocks, int duration) {
        clear();
        var level = Minecraft.getInstance().level;
        if (level == null || blocks.isEmpty()) return;
        world = level;
        anchor = pos.immutable();
        definition = owner;
        remaining = duration;
        preview = new PreviewLevel(level);
        preview.setBlocks(blocks);
        renderer = new StructureRenderer(BLOCK_SCALE);
        renderer.show(preview.view(), true, new Vector3f(pos.getX() + 0.5f, pos.getY() + 0.5f, pos.getZ() + 0.5f));
    }

    public static boolean isActive() {
        return renderer != null;
    }

    public static boolean isProjecting(BlockPos pos) {
        return renderer != null && pos.equals(anchor);
    }

    public static void clear() {
        if (renderer != null) renderer.dispose();
        if (preview != null) preview.clear();
        renderer = null;
        preview = null;
        world = null;
        anchor = null;
        definition = null;
        remaining = -1;
    }

    public static void tick() {
        if (renderer == null) return;
        var level = Minecraft.getInstance().level;
        if (level != world || anchor == null) {
            clear();
            return;
        }
        if (remaining > 0 && --remaining == 0) {
            clear();
            return;
        }
        if (definition == null || ++ticks % CHECK_INTERVAL != 0 || !level.isLoaded(anchor)) return;
        if (!(MetaMachine.getMachine(level, anchor) instanceof IMultiController controller) ||
                controller.self().getDefinition() != definition) {
            clear();
        }
    }

    public static void render(RenderLevelStageEvent event) {
        var renderer = StructureProjection.renderer;
        if (renderer == null) return;
        renderer.upload();
        var camera = event.getCamera().getPosition();
        var pose = event.getPoseStack().last().pose();
        var modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.mulPoseMatrix(pose);
        RenderSystem.applyModelViewMatrix();
        try {
            var frustum = new FrustumIntersection(new Matrix4f(event.getProjectionMatrix()).mul(pose));
            renderer.render(frustum, new Vector3f((float) camera.x, (float) camera.y, (float) camera.z), event.getPartialTick(),
                    camera.x, camera.y, camera.z);
        } finally {
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.disableBlend();
        }
    }
}
