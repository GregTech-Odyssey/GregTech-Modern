package com.gregtechceu.gtceu.uiwidgets.structure;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import com.mojang.blaze3d.platform.InputConstants;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

@OnlyIn(Dist.CLIENT)
final class SceneCamera {

    private static final float DEFAULT_YAW = 25;
    private static final float DEFAULT_PITCH = -135;
    private static final float DEFAULT_ZOOM = 5;
    private static final float MOVE_SPEED = 0.8f;
    private static final float MIN_MOVE_STEP = 2f;
    private static final double ROTATE_SPEED = 0.5;
    private static final double PAN_SPEED = 0.1;
    private static final double MAX_YAW = 89.9;
    private static final float ZOOM_STEP = 1.25f;
    private static final float MIN_STEP_ZOOM = 1f;
    private static final float WHEEL_RATIO = 0.08f;
    private static final float MIN_WHEEL_STEP = 0.5f;
    private static final float MIN_WHEEL_ZOOM = 0.1f;
    private static final float MAX_WHEEL_ZOOM = 999f;
    private static final float MIN_PIVOT_DEPTH = 0.5f;

    private Vector3f center = new Vector3f();
    private float yaw = DEFAULT_YAW;
    private float pitch = DEFAULT_PITCH;
    private float zoom = DEFAULT_ZOOM;
    private float baseZoom = -1;
    @Nullable
    private Vector3f home;
    private boolean recenter;
    @Nullable
    private StructureScene.Camera restore;

    boolean hasHome() {
        return home != null;
    }

    void place(Vector3f home, float baseZoom) {
        boolean first = this.home == null || recenter;
        this.home = home;
        this.baseZoom = baseZoom;
        if (first && restore != null) {
            center = new Vector3f(restore.center());
            yaw = restore.yaw();
            pitch = restore.pitch();
            zoom = restore.zoom();
        } else if (first) {
            center = new Vector3f(home);
            yaw = DEFAULT_YAW;
            pitch = DEFAULT_PITCH;
            zoom = baseZoom;
        }
        recenter = false;
        restore = null;
    }

    StructureScene.Camera snapshot() {
        return new StructureScene.Camera(new Vector3f(center), yaw, pitch, zoom);
    }

    void recenter(@Nullable StructureScene.Camera camera) {
        recenter = true;
        restore = camera;
    }

    Vector3f center() {
        return center;
    }

    float zoom() {
        return zoom;
    }

    Vector3f eye() {
        double p = Math.toRadians(pitch), y = Math.toRadians(yaw);
        return new Vector3f((float) Math.cos(p), (float) Math.tan(y), (float) Math.sin(p)).normalize().mul(zoom).add(center);
    }

    void zoomStep(int direction) {
        zoom = Math.max(MIN_STEP_ZOOM, zoom * (direction > 0 ? 1 / ZOOM_STEP : ZOOM_STEP));
    }

    String percentText() {
        if (baseZoom <= 0) return "100%";
        return Math.round(baseZoom / zoom * 100) + "%";
    }

    void resetZoom() {
        if (baseZoom > 0) zoom = baseZoom;
    }

    void resetView() {
        yaw = DEFAULT_YAW;
        pitch = DEFAULT_PITCH;
        if (home != null) center = new Vector3f(home);
        resetZoom();
    }

    void focus(float x, float z) {
        center = new Vector3f(x, center.y(), z);
    }

    void wheel(double delta) {
        float step = Math.max(MIN_WHEEL_STEP, zoom * WHEEL_RATIO);
        zoom = Mth.clamp(zoom + (delta < 0 ? step : -step), MIN_WHEEL_ZOOM, MAX_WHEEL_ZOOM);
    }

    void pan(double dragX, double dragY) {
        dragX *= PAN_SPEED;
        dragY *= PAN_SPEED;
        double p = Math.toRadians(pitch), y = Math.toRadians(yaw);
        float moveX = -(float) (dragY * Math.sin(y) * Math.cos(p) + dragX * Math.sin(p));
        float moveY = (float) (dragY * Math.cos(y));
        float moveZ = (float) (-dragY * Math.sin(y) * Math.sin(p) + dragX * Math.cos(p));
        center = new Vector3f(center).add(moveX, moveY, moveZ);
    }

    void rotate(double dragX, double dragY) {
        pitch = (float) ((pitch + dragX * ROTATE_SPEED + 360) % 360);
        yaw = (float) Mth.clamp(yaw + dragY * ROTATE_SPEED, -MAX_YAW, MAX_YAW);
    }

    void pivotAt(@Nullable Vec3 point) {
        if (point == null) return;
        var eye = eye();
        var forward = new Vector3f(center).sub(eye).normalize();
        float depth = (float) ((point.x - eye.x()) * forward.x() + (point.y - eye.y()) * forward.y() + (point.z - eye.z()) * forward.z());
        if (depth < MIN_PIVOT_DEPTH) return;
        center = new Vector3f(eye).add(forward.mul(depth));
        zoom = depth;
    }

    void move(long window, float seconds) {
        float forward = axis(window, GLFW.GLFW_KEY_W, GLFW.GLFW_KEY_S);
        float right = axis(window, GLFW.GLFW_KEY_D, GLFW.GLFW_KEY_A);
        float up = axis(window, GLFW.GLFW_KEY_SPACE, GLFW.GLFW_KEY_LEFT_SHIFT);
        if (forward == 0 && right == 0 && up == 0) return;
        float step = Math.max(MIN_MOVE_STEP, zoom * MOVE_SPEED) * seconds;
        double p = Math.toRadians(pitch);
        float fx = (float) -Math.cos(p), fz = (float) -Math.sin(p);
        float rx = (float) Math.sin(p), rz = (float) -Math.cos(p);
        center = new Vector3f(center).add((fx * forward + rx * right) * step, up * step, (fz * forward + rz * right) * step);
    }

    private static float axis(long window, int positive, int negative) {
        float value = 0;
        if (InputConstants.isKeyDown(window, positive)) value += 1;
        if (InputConstants.isKeyDown(window, negative)) value -= 1;
        return value;
    }
}
