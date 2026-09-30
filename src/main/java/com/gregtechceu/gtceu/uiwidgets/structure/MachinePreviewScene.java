package com.gregtechceu.gtceu.uiwidgets.structure;

import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

@OnlyIn(Dist.CLIENT)
public final class MachinePreviewScene {

    private static final float ZOOM = 1.8f;

    private MachinePreviewScene() {}

    public static Widget create(Level level, BlockPos pos, int size) {
        var scene = new StructureScene("machine_preview", size, size, false);
        scene.setAutoOrbit(true);
        scene.setSelectable(false);
        var blocks = new Long2ObjectOpenHashMap<BlockState>();
        blocks.put(BlockPos.ZERO.asLong(), level.getBlockState(pos));
        scene.show(blocks, StructureScene.ALL_LAYERS, ZOOM);
        return scene;
    }
}
