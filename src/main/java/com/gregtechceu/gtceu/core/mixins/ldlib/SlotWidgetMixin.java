package com.gregtechceu.gtceu.core.mixins.ldlib;

import com.gregtechceu.gtceu.api.gui.ISlotWidget;

import com.lowdragmc.lowdraglib.gui.util.DrawerHelper;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.utils.Position;
import com.lowdragmc.lowdraglib.utils.Size;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(SlotWidget.class)
public abstract class SlotWidgetMixin extends Widget implements ISlotWidget {

    @Unique
    private AEStylePredicate gtm$aeStylePredicate = stack -> false;

    @Shadow(remap = false)
    public abstract void drawInBackground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks);

    @Shadow(remap = false)
    @javax.annotation.Nullable
    protected Slot slotReference;

    public SlotWidgetMixin(Position selfPosition, Size size) {
        super(selfPosition, size);
    }

    public SlotWidgetMixin(int x, int y, int width, int height) {
        super(x, y, width, height);
    }

    @OnlyIn(Dist.CLIENT)
    @Redirect(method = "drawInBackground",
              remap = false,
              at = @At(value = "INVOKE", target = "Lcom/lowdragmc/lowdraglib/gui/util/DrawerHelper;drawItemStack(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/item/ItemStack;IIILjava/lang/String;)V"))
    private void redirectDrawItemStack(GuiGraphics graphics, ItemStack stack, int x, int y, int size, @Nullable String altText) {
        if (gtm$aeStylePredicate.shouldUseAEStyle(stack)) {
            ISlotWidget.drawBackgroundAEStyle(graphics, stack, new Position(x, y));
        } else {
            DrawerHelper.drawItemStack(graphics, stack, x, y, size, altText);
        }
    }

    @Override
    public AEStylePredicate gtm$getAEStylePredicate() {
        return gtm$aeStylePredicate;
    }

    @Override
    public void gtm$setAEStylePredicate(AEStylePredicate predicate) {
        gtm$aeStylePredicate = predicate;
    }
}
