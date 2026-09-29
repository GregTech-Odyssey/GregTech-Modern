package com.gregtechceu.gtceu.api.machine.multiblockpro;

import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.Nullable;

public final class ParamKey {

    private final String translationKey;
    @Nullable
    private final String descriptionKey;

    private ParamKey(String translationKey, @Nullable String descriptionKey) {
        this.translationKey = translationKey;
        this.descriptionKey = descriptionKey;
    }

    public static ParamKey of(String translationKey) {
        return new ParamKey(translationKey, null);
    }

    public static ParamKey of(String translationKey, String descriptionKey) {
        return new ParamKey(translationKey, descriptionKey);
    }

    public String getTranslationKey() {
        return translationKey;
    }

    @Nullable
    public String getDescriptionKey() {
        return descriptionKey;
    }

    public Component getName() {
        return Component.translatable(translationKey);
    }

    @Override
    public String toString() {
        return translationKey;
    }
}
