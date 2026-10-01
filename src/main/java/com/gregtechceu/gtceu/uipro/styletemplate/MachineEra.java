package com.gregtechceu.gtceu.uipro.styletemplate;

import com.gregtechceu.gtceu.GTCEu;

import net.minecraft.resources.ResourceLocation;

import org.jetbrains.annotations.Nullable;

public enum MachineEra {

    CLASSIC(null, "textures/gui/uipro/screen_classic.png"),
    BRONZE(GTCEu.id("steam_bronze"), "textures/gui/uipro/screen_bronze.png"),
    STEEL(GTCEu.id("steam_steel"), "textures/gui/uipro/screen_steel.png");

    @Nullable
    private final ResourceLocation skin;
    private final ScreenSprite screen;

    MachineEra(@Nullable ResourceLocation skin, String screenTexture) {
        this.skin = skin;
        this.screen = new ScreenSprite(GTCEu.id(screenTexture), 36, 36, 8, false);
    }

    public static MachineEra steam(boolean steel) {
        return steel ? STEEL : BRONZE;
    }

    @Nullable
    public ResourceLocation getSkin() {
        return skin;
    }

    public ScreenSprite getScreen() {
        return screen;
    }
}
