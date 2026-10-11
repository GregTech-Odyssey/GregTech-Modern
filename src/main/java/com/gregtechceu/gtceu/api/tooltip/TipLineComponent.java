package com.gregtechceu.gtceu.api.tooltip;

import net.minecraft.world.inventory.tooltip.TooltipComponent;

public record TipLineComponent(TipContents contents, int padTop, int maxWidth) implements TooltipComponent {}
