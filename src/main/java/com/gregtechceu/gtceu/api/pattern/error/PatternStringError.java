package com.gregtechceu.gtceu.api.pattern.error;

import net.minecraft.network.chat.Component;

public class PatternStringError extends PatternError {

    public final Component info;

    public PatternStringError(String translateKey) {
        this.info = Component.translatable(translateKey);
    }

    public PatternStringError(Component info) {
        this.info = info;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof PatternStringError error) {
            return error.info.equals(info);
        }
        return false;
    }

    @Override
    public PatternStringError copy() {
        return new PatternStringError(info);
    }

    @Override
    public Component getErrorInfo() {
        return info.copy().append("-").append(pos.toShortString());
    }
}
