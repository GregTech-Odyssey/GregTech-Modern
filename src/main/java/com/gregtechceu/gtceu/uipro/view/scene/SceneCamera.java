package com.gregtechceu.gtceu.uipro.view.scene;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import com.mojang.blaze3d.platform.InputConstants;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

@OnlyIn(Dist.CLIENT)
public final class SceneCamera {

    private static final float DEFAULT_YAW = 25;
    private static final float DEFAULT_PITCH = -135;
    private static final float DEFAULT_ZOOM = 5;
    private static final float FLY_SPEED = 32.76f;
    private static final float SPEED_STEP = 1.25f;
    private static final int MAX_SPEED_LEVEL = 15;
    private static int speedLevel;
    private static final float SPRINT_MULTIPLIER = 2f;
    private static final double TURN_DEGREES_PER_PIXEL = 1.2;
    private static final double MAX_YAW = 89.9;
    private static final float WHEEL_RATIO = 0.15f;
    private static final float MIN_WHEEL_STEP = 0.5f;
    private static final int BUTTON_STEPS = 3;
    private static final float ORBIT_WHEEL_RATIO = 0.85f;
    private static final float ORBIT_MIN_RATIO = 0.5f;
    private static final float ORBIT_MAX_RATIO = 3f;

    private Vector3f center = new Vector3f();
    private float yaw = DEFAULT_YAW;
    private float pitch = DEFAULT_PITCH;
    private float zoom = DEFAULT_ZOOM;
    private float baseZoom = -1;
    @Nullable
    private Vector3f home;
    private boolean recenter;
    private boolean oriented;
    private boolean orbit;
    @Nullable
    private SceneView.Camera restore;

    public void setOrbit(boolean orbit) {
        this.orbit = orbit;
    }

    public boolean isOrbit() {
        return orbit;
    }

    public void orbitZoom(double delta) {
        if (baseZoom <= 0) return;
        float next = delta > 0 ? zoom * ORBIT_WHEEL_RATIO : zoom / ORBIT_WHEEL_RATIO;
        zoom = Mth.clamp(next, baseZoom * ORBIT_MIN_RATIO, baseZoom * ORBIT_MAX_RATIO);
    }

    public boolean hasHome() {
        return home != null;
    }

    public void place(Vector3f home, float baseZoom) {
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
            if (!oriented) {
                yaw = DEFAULT_YAW;
                pitch = DEFAULT_PITCH;
            }
            zoom = baseZoom;
        }
        oriented = false;
        recenter = false;
        restore = null;
    }

    public SceneView.Camera snapshot() {
        return new SceneView.Camera(new Vector3f(center), yaw, pitch, zoom);
    }

    public void recenter(@Nullable SceneView.Camera camera) {
        recenter = true;
        restore = camera;
    }

    public Vector3f center() {
        return center;
    }

    public float zoom() {
        return zoom;
    }

    public Vector3f eye() {
        return backward().mul(zoom).add(center);
    }

    private Vector3f backward() {
        double p = Math.toRadians(pitch), y = Math.toRadians(yaw);
        return new Vector3f((float) Math.cos(p), (float) Math.tan(y), (float) Math.sin(p)).normalize();
    }

    private void translate(Vector3f offset) {
        center = new Vector3f(center).add(offset);
    }

    public String percentText() {
        if (baseZoom <= 0 || home == null) return "100%";
        return Math.round(baseZoom / Math.max(eye().distance(home), MIN_WHEEL_STEP) * 100) + "%";
    }

    public void resetZoom() {
        if (home == null || baseZoom <= 0) return;
        center = new Vector3f(home);
        zoom = baseZoom;
    }

    public void resetView() {
        yaw = DEFAULT_YAW;
        pitch = DEFAULT_PITCH;
        if (home != null) center = new Vector3f(home);
        if (baseZoom > 0) zoom = baseZoom;
    }

    public void orbit(float pitchDegrees, float yawDegrees) {
        if (home != null) center = new Vector3f(home);
        pitch = pitchDegrees % 360;
        yaw = (float) Mth.clamp(yawDegrees, -MAX_YAW, MAX_YAW);
    }

    public void lookFrom(float x, float y, float z) {
        pitch = (float) ((Math.toDegrees(Math.atan2(z, x)) + 360) % 360);
        yaw = (float) Mth.clamp(Math.toDegrees(Math.atan2(y, Math.sqrt(x * x + z * z))), -MAX_YAW, MAX_YAW);
        if (home == null) oriented = true;
        else center = new Vector3f(home);
    }

    public void moveTo(float x, float z) {
        var eye = eye();
        translate(new Vector3f(x - eye.x(), 0, z - eye.z()));
    }

    public static void adjustSpeed(double delta) {
        speedLevel = Mth.clamp(speedLevel + (delta > 0 ? 1 : -1), -MAX_SPEED_LEVEL, MAX_SPEED_LEVEL);
    }

    public static float speedScale() {
        return (float) Math.pow(SPEED_STEP, speedLevel);
    }

    public void zoomStep(int direction, float reach) {
        dolly(direction * wheelStep(reach) * BUTTON_STEPS);
    }

    private static float wheelStep(float reach) {
        return Math.max(MIN_WHEEL_STEP, reach * WHEEL_RATIO);
    }

    private void dolly(float distance) {
        translate(backward().mul(-distance));
    }

    public void rotate(double dragX, double dragY) {
        var minecraft = Minecraft.getInstance();
        double sensitivity = minecraft.options.sensitivity().get() * 0.6 + 0.2;
        double degrees = sensitivity * sensitivity * sensitivity * TURN_DEGREES_PER_PIXEL * minecraft.getWindow().getGuiScale();
        if (minecraft.options.invertYMouse().get()) dragY = -dragY;
        var eye = eye();
        pitch = (float) ((pitch + dragX * degrees + 360) % 360);
        yaw = (float) Mth.clamp(yaw + dragY * degrees, -MAX_YAW, MAX_YAW);
        if (!orbit) center = eye.sub(backward().mul(zoom));
    }

    public void move(long window, float seconds) {
        var options = Minecraft.getInstance().options;
        float forward = axis(window, options.keyUp, options.keyDown);
        float strafe = axis(window, options.keyRight, options.keyLeft);
        float lift = axis(window, options.keyJump, options.keyShift);
        if (forward == 0 && strafe == 0 && lift == 0) return;
        float step = FLY_SPEED * speedScale() * (down(window, options.keySprint) ? SPRINT_MULTIPLIER : 1) * seconds;
        double p = Math.toRadians(pitch);
        var horizontal = new Vector3f((float) (-Math.cos(p) * forward + Math.sin(p) * strafe), 0,
                (float) (-Math.sin(p) * forward - Math.cos(p) * strafe));
        if (horizontal.lengthSquared() > 1) horizontal.normalize();
        translate(horizontal.add(0, lift, 0).mul(step));
    }

    public static boolean moving(long window) {
        var options = Minecraft.getInstance().options;
        return down(window, options.keyUp) || down(window, options.keyDown) || down(window, options.keyLeft) ||
                down(window, options.keyRight) || down(window, options.keyJump) || down(window, options.keyShift);
    }

    private static float axis(long window, KeyMapping positive, KeyMapping negative) {
        float value = 0;
        if (down(window, positive)) value += 1;
        if (down(window, negative)) value -= 1;
        return value;
    }

    private static boolean down(long window, KeyMapping mapping) {
        var key = mapping.getKey();
        if (key.getType() == InputConstants.Type.MOUSE) return GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
        return key.getType() == InputConstants.Type.KEYSYM && key.getValue() != InputConstants.UNKNOWN.getValue() &&
                InputConstants.isKeyDown(window, key.getValue());
    }
}
