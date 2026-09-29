package com.gregtechceu.gtceu.uiwidgets.structure;

import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.List;

@OnlyIn(Dist.CLIENT)
final class SceneMarkers {

    private static final int HOVER_RADIUS_SQR = 49;
    private static final int RADIUS = 4;
    private static final int ACTIVE_RADIUS = 6;
    private static final int CORE_INSET = 3;
    private static final int RING_COLOR = 0xFF202020;
    private static final int HOVER_RING_COLOR = 0xFFFFFFFF;
    private static final int CORE_COLOR = 0xFFFFFFFF;
    private static final int FAR_ALPHA = 0xB0000000;
    private static final float HOVER_BLEND = 0.25f;
    private static final double PULSE_PERIOD_MS = 250.0;
    private static final float LAYER_Z = 200;
    private static final int HIDDEN = 0, FAR = 1, NEAR = 2;

    private List<StructureScene.Marker> markers = List.of();
    @Nullable
    private StructureScene.Marker hovered;
    private int[] points = new int[0];
    private boolean[] occluded = new boolean[0];
    @Nullable
    private PreviewLevel.View occludedView;
    private float eyeX = Float.NaN, eyeY = Float.NaN, eyeZ = Float.NaN;

    void set(List<StructureScene.Marker> markers) {
        this.markers = List.copyOf(markers);
        hovered = null;
        points = new int[this.markers.size() * 3];
        occluded = new boolean[this.markers.size()];
        occludedView = null;
        eyeX = Float.NaN;
    }

    boolean isEmpty() {
        return markers.isEmpty();
    }

    @Nullable
    StructureScene.Marker hovered() {
        return hovered;
    }

    void draw(GuiGraphics graphics, Matrix4f matrix, Vector3f eye, @Nullable PreviewLevel.View view, PreviewBounds bounds, int left, int top,
              int width, int height, boolean over, int mouseX, int mouseY) {
        hovered = null;
        if (markers.isEmpty()) return;
        updateOcclusion(eye, view, bounds);
        float best = Float.MAX_VALUE;
        var clip = new Vector4f();
        for (int i = 0; i < markers.size(); i++) {
            var marker = markers.get(i);
            points[i * 3 + 2] = HIDDEN;
            matrix.transform(clip.set(marker.pos().x(), marker.pos().y(), marker.pos().z(), 1));
            if (clip.w() <= 0) continue;
            float nx = clip.x() / clip.w(), ny = clip.y() / clip.w();
            if (nx < -1 || nx > 1 || ny < -1 || ny > 1) continue;
            int sx = Math.round(left + (nx + 1) / 2 * width), sy = Math.round(top + (1 - ny) / 2 * height);
            boolean near = marker.selected() || !occluded[i];
            points[i * 3] = sx;
            points[i * 3 + 1] = sy;
            points[i * 3 + 2] = near ? NEAR : FAR;
            if (!near) continue;
            float distance = (mouseX - sx) * (mouseX - sx) + (mouseY - sy) * (mouseY - sy);
            if (over && distance <= HOVER_RADIUS_SQR && distance < best) {
                best = distance;
                hovered = marker;
            }
        }
        float pulse = (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / PULSE_PERIOD_MS));
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(0, 0, LAYER_Z);
        for (int i = 0; i < markers.size(); i++) {
            var marker = markers.get(i);
            int mode = points[i * 3 + 2], sx = points[i * 3], sy = points[i * 3 + 1];
            if (mode == HIDDEN) continue;
            if (mode == FAR) {
                graphics.fill(sx - 1, sy - 1, sx + 2, sy + 2, FAR_ALPHA | (marker.color() & 0xFFFFFF));
                continue;
            }
            boolean isHovered = marker == hovered;
            int radius = isHovered || marker.selected() ? ACTIVE_RADIUS : RADIUS;
            int ring = marker.selected() ? UITheme.SELECTION_COLOR : isHovered ? HOVER_RING_COLOR : RING_COLOR;
            drawDiamond(graphics, sx, sy, radius + 1, ring);
            int fill = 0xFF000000 | (marker.color() & 0xFFFFFF);
            if (isHovered) fill = blend(fill, 0xFFFFFFFF, HOVER_BLEND + HOVER_BLEND * pulse);
            drawDiamond(graphics, sx, sy, radius, fill);
            drawDiamond(graphics, sx, sy, Math.max(1, radius - CORE_INSET), CORE_COLOR);
        }
        pose.popPose();
    }

    private void updateOcclusion(Vector3f eye, @Nullable PreviewLevel.View view, PreviewBounds bounds) {
        if (view == occludedView && eye.x() == eyeX && eye.y() == eyeY && eye.z() == eyeZ) return;
        occludedView = view;
        eyeX = eye.x();
        eyeY = eye.y();
        eyeZ = eye.z();
        for (int i = 0; i < markers.size(); i++) {
            occluded[i] = view != null && ScenePick.pick(view, bounds, eye, markers.get(i).pos()) != null;
        }
    }

    private static int blend(int a, int b, float t) {
        int ar = a >> 16 & 0xFF, ag = a >> 8 & 0xFF, ab = a & 0xFF;
        int br = b >> 16 & 0xFF, bg = b >> 8 & 0xFF, bb = b & 0xFF;
        return 0xFF000000 | Math.round(ar + (br - ar) * t) << 16 | Math.round(ag + (bg - ag) * t) << 8 | Math.round(ab + (bb - ab) * t);
    }

    private static void drawDiamond(GuiGraphics graphics, int cx, int cy, int radius, int color) {
        for (int dy = -radius; dy <= radius; dy++) {
            int half = radius - Math.abs(dy);
            graphics.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, color);
        }
    }
}
