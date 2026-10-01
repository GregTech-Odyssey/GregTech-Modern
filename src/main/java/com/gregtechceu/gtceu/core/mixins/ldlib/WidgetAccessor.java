package com.gregtechceu.gtceu.core.mixins.ldlib;

import com.lowdragmc.lowdraglib.gui.modular.WidgetUIAccess;
import com.lowdragmc.lowdraglib.gui.widget.Widget;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = Widget.class, remap = false)
public interface WidgetAccessor {

    @Accessor("uiAccess")
    WidgetUIAccess gtceu$getUiAccess();
}
