package com.gregtechceu.gtceu.api.pattern.error;

import net.minecraft.network.chat.Component;

public class PatternStringError extends PatternError {

    public final String translateKey;

    public PatternStringError(String translateKey) {
        this.translateKey = translateKey;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof PatternStringError error) {
            return error.translateKey.equals(translateKey);
        }
        return false;
    }

    @Override
    public PatternStringError copy() {
        return new PatternStringError(translateKey);
    }

    @Override
    public Component getErrorInfo() {
        return Component.translatable(translateKey).append("-").append(pos.toShortString());
    }
}
