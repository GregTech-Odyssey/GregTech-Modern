package com.gregtechceu.gtceu.uiwidgets.structure;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import com.mojang.blaze3d.platform.NativeImage;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

@OnlyIn(Dist.CLIENT)
public final class StructureMinimap extends UIElement {

    private static final int BORDER = 2;
    private static final int MARKER = 0xFFFF3030;
    private static final int EMPTY = 0x00000000;
    private static int counter;

    private final StructureScene scene;
    private final ResourceLocation location = GTCEu.id("structure_minimap/" + counter++);
    @Nullable
    private DynamicTexture texture;
    private int[] colors = new int[0];
    private int minX, minZ, mapWidth, mapDepth;
    private boolean dirty;

    private boolean dragging;

    public StructureMinimap(StructureScene scene, int size) {
        this.scene = scene;
        layout(l -> l.size(size, size));
        setClientSideWidget();
    }

    void setBlocks(Long2ObjectOpenHashMap<BlockState> blocks, PreviewBounds bounds) {
        if (bounds.empty) {
            mapWidth = mapDepth = 0;
            colors = new int[0];
            dirty = true;
            return;
        }
        minX = bounds.minX;
        minZ = bounds.minZ;
        mapWidth = bounds.width();
        mapDepth = bounds.depth();
        colors = new int[mapWidth * mapDepth];
        var tops = new int[colors.length];
        Arrays.fill(tops, Integer.MIN_VALUE);
        var cursor = new BlockPos.MutableBlockPos();
        for (var it = blocks.long2ObjectEntrySet().fastIterator(); it.hasNext();) {
            var entry = it.next();
            long pos = entry.getLongKey();
            int y = BlockPos.getY(pos);
            int index = (BlockPos.getZ(pos) - minZ) * mapWidth + (BlockPos.getX(pos) - minX);
            if (y < tops[index]) continue;
            tops[index] = y;
            int rgb = entry.getValue().getMapColor(EmptyBlockGetter.INSTANCE, cursor.set(pos)).col;
            colors[index] = 0xFF000000 | rgb;
        }
        dirty = true;
    }

    public boolean hasMap() {
        return mapWidth > 0;
    }

    private void upload() {
        dispose();
        dirty = false;
        if (mapWidth == 0) return;
        var image = new NativeImage(mapWidth, mapDepth, false);
        for (int z = 0; z < mapDepth; z++) {
            for (int x = 0; x < mapWidth; x++) {
                int argb = colors[z * mapWidth + x];
                int abgr = argb == EMPTY ? 0 : (argb & 0xFF00FF00) | (argb & 0xFF) << 16 | (argb >> 16 & 0xFF);
                image.setPixelRGBA(x, z, abgr);
            }
        }
        texture = new DynamicTexture(image);
        Minecraft.getInstance().getTextureManager().register(location, texture);
    }

    public void dispose() {
        if (texture == null) return;
        Minecraft.getInstance().getTextureManager().release(location);
        texture = null;
        dirty = true;
    }

    private float scale() {
        int inner = getSizeWidth() - 2 * BORDER;
        return Math.min(inner / (float) mapWidth, (getSizeHeight() - 2 * BORDER) / (float) mapDepth);
    }

    private int originX() {
        return getPositionX() + (getSizeWidth() - Math.round(mapWidth * scale())) / 2;
    }

    private int originY() {
        return getPositionY() + (getSizeHeight() - Math.round(mapDepth * scale())) / 2;
    }

    @Override
    public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
        UITheme.PANEL.draw(graphics, mouseX, mouseY, getPositionX(), getPositionY(), getSizeWidth(), getSizeHeight());
        if (dirty) upload();
        if (texture == null) return;
        float scale = scale();
        int x = originX(), y = originY();
        int w = Math.round(mapWidth * scale), h = Math.round(mapDepth * scale);
        graphics.blit(location, x, y, w, h, 0, 0, mapWidth, mapDepth, mapWidth, mapDepth);
        var center = scene.getCenter();
        if (center == null) return;
        int mx = x + Math.round((center.x() - minX) * scale), my = y + Math.round((center.z() - minZ) * scale);
        graphics.fill(mx - 2, my - 2, mx + 3, my + 3, 0xFF000000);
        graphics.fill(mx - 1, my - 1, mx + 2, my + 2, MARKER);
    }

    private void focusAt(double mouseX, double mouseY) {
        float scale = scale();
        float x = (float) (mouseX - originX()) / scale + minX;
        float z = (float) (mouseY - originY()) / scale + minZ;
        scene.focus(x, z);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isVisible() || !isMouseOverElement(mouseX, mouseY)) return false;
        if (mapWidth == 0 || button != 0) return true;
        dragging = true;
        capturePointer(0);
        focusAt(mouseX, mouseY);
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!dragging) return false;
        focusAt(mouseX, mouseY);
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!dragging) return false;
        dragging = false;
        releasePointer();
        return true;
    }
}
