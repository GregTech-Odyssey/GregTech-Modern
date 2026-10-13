package com.gregtechceu.gtceu.client.renderer.machine;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IAssemblyLine;
import com.gregtechceu.gtceu.api.pattern.util.RelativeDirection;
import com.gregtechceu.gtceu.client.renderer.GTRenderTypes;
import com.gregtechceu.gtceu.client.renderer.IRenderBoundingBox;
import com.gregtechceu.gtceu.config.ConfigHolder;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;

/** Adapts the upstream AssemblyLineRender to the fork's casing renderer API. */
public class AssemblyLineRenderer extends WorkableCasingMachineRenderer implements IRenderBoundingBox {

    private static final int[] CROSS_A = { 0, -1, 0, 1 };
    private static final int[] CROSS_B = { 1, 0, -1, 0 };
    private String configuredColor = "#1FA1FC";
    private int laserColor = 0x1FA1FC;

    public AssemblyLineRenderer(ResourceLocation baseCasing, ResourceLocation workableModel) {
        super(baseCasing, workableModel);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void render(BlockEntity blockEntity, float partialTick, PoseStack stack, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        if (!(blockEntity instanceof MetaMachineBlockEntity holder) ||
                !(holder.getMetaMachine() instanceof IAssemblyLine machine) ||
                !machine.isFormed() || !machine.getRecipeLogic().isWorking())
            return;

        var color = ConfigHolder.INSTANCE.client.renderer.assemblyLineLaser;
        if (!configuredColor.equals(color)) {
            configuredColor = color;
            laserColor = Integer.decode(color) & 0xFFFFFF;
        }
        var front = machine.self().getFrontFacing();
        var upwards = machine.self().getUpwardsFacing();
        boolean flipped = machine.self().isFlipped();
        var down = RelativeDirection.DOWN.getRelative(front, upwards, flipped);
        var back = RelativeDirection.BACK.getRelative(front, upwards, flipped);
        var right = RelativeDirection.RIGHT.getRelative(front, upwards, flipped);
        float progress = machine.getRendererLineCount(partialTick);
        int complete = (int) progress;
        var buffer = buffers.getBuffer(GTRenderTypes.assemblyLine());
        var pose = stack.last().pose();
        for (int i = 0; i < complete; i++) {
            renderPair(buffer, pose, down, back, right, i, laserColor | 0xFF000000);
        }
        int alpha = (int) ((progress - complete) * 255);
        if (alpha > 0) renderPair(buffer, pose, down, back, right, complete, laserColor | alpha << 24);
    }

    @OnlyIn(Dist.CLIENT)
    private static void renderPair(VertexConsumer buffer, Matrix4f pose, Direction down, Direction back,
                                   Direction right, int section, int color) {
        float x = down.getStepX() + right.getStepX() * section + 0.5F;
        float y = down.getStepY() + right.getStepY() * section;
        float z = down.getStepZ() + right.getStepZ() * section + 0.5F;
        renderLine(buffer, pose, x, y, z, down, color);
        renderLine(buffer, pose, x + back.getStepX() * 2, y + back.getStepY() * 2,
                z + back.getStepZ() * 2, down, color);
    }

    @OnlyIn(Dist.CLIENT)
    private static void renderLine(VertexConsumer buffer, Matrix4f pose, float x, float y, float z,
                                   Direction down, int color) {
        // Scalar vertices avoid allocating positions and vectors for every laser in every frame.
        boolean vertical = down.getAxis() == Direction.Axis.Y;
        float ax = -down.getStepZ() * 0.03F;
        float az = (vertical ? -down.getStepY() : down.getStepX()) * 0.03F;
        float bx = vertical ? -0.03F : 0;
        float by = vertical ? 0 : -0.03F;
        float tx = x - down.getStepX();
        float ty = y - down.getStepY();
        float tz = z - down.getStepZ();
        // The same two perpendiculars and diamond cross-section as upstream RenderBufferHelper.renderLine.
        for (int i = 0; i < 4; i++) {
            vertex(buffer, pose, color, x + ax * CROSS_A[i] + bx * CROSS_B[i],
                    y + by * CROSS_B[i], z + az * CROSS_A[i]);
        }
        for (int i = 3; i >= 0; i--) {
            vertex(buffer, pose, color, tx + ax * CROSS_A[i] + bx * CROSS_B[i],
                    ty + by * CROSS_B[i], tz + az * CROSS_A[i]);
        }
        for (int i = 0; i < 4; i++) {
            int next = (i + 1) & 3;
            float ox = ax * CROSS_A[i] + bx * CROSS_B[i];
            float oy = by * CROSS_B[i];
            float oz = az * CROSS_A[i];
            float nx = ax * CROSS_A[next] + bx * CROSS_B[next];
            float ny = by * CROSS_B[next];
            float nz = az * CROSS_A[next];
            vertex(buffer, pose, color, x + ox, y + oy, z + oz);
            vertex(buffer, pose, color, tx + ox, ty + oy, tz + oz);
            vertex(buffer, pose, color, tx + nx, ty + ny, tz + nz);
            vertex(buffer, pose, color, x + nx, y + ny, z + nz);
        }
    }

    @OnlyIn(Dist.CLIENT)
    private static void vertex(VertexConsumer buffer, Matrix4f pose, int color, float x, float y, float z) {
        buffer.vertex(pose, x, y, z).color(color).endVertex();
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public boolean hasTESR(BlockEntity blockEntity) {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(BlockEntity blockEntity) {
        if (!(blockEntity instanceof MetaMachineBlockEntity holder) || !(holder.getMetaMachine() instanceof IAssemblyLine machine)) return null;
        var front = machine.self().getFrontFacing();
        var upwards = machine.self().getUpwardsFacing();
        boolean flipped = machine.self().isFlipped();
        var down = RelativeDirection.DOWN.getRelative(front, upwards, flipped);
        var back = RelativeDirection.BACK.getRelative(front, upwards, flipped);
        var right = RelativeDirection.RIGHT.getRelative(front, upwards, flipped);
        return new AABB(machine.self().getPos())
                .expandTowards(down.getStepX() << 1, down.getStepY() << 1, down.getStepZ() << 1)
                .expandTowards(-down.getStepX(), -down.getStepY(), -down.getStepZ())
                .expandTowards(back.getStepX() << 1, back.getStepY() << 1, back.getStepZ() << 1)
                .expandTowards(right.getStepX() * 17, right.getStepY() * 17, right.getStepZ() * 17);
    }
}
