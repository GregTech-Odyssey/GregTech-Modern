package com.gregtechceu.gtceu.uipro.view;

import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

@OnlyIn(Dist.CLIENT)
public final class ViewKeys {

    private ViewKeys() {}

    public static float axis(long window, KeyMapping positive, KeyMapping negative) {
        float value = 0;
        if (down(window, positive)) value += 1;
        if (down(window, negative)) value -= 1;
        return value;
    }

    public static boolean down(long window, KeyMapping mapping) {
        var key = mapping.getKey();
        if (key.getType() == InputConstants.Type.MOUSE) return GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
        return key.getType() == InputConstants.Type.KEYSYM && key.getValue() != InputConstants.UNKNOWN.getValue() &&
                InputConstants.isKeyDown(window, key.getValue());
    }
}
