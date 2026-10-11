package com.gregtechceu.gtceu.uipro;

import com.gregtechceu.gtceu.GTCEu;

import net.minecraft.core.registries.Registries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.integration.modules.emi.EmiStackHelper;
import dev.emi.emi.EmiUtil;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.EmiStackInteraction;
import dev.emi.emi.api.stack.TagEmiIngredient;
import org.jetbrains.annotations.Nullable;

public final class UIIngredient {

    private UIIngredient() {}

    @Nullable
    public static Object of(@Nullable Object ingredient) {
        if (ingredient == null || !GTCEu.Mods.isEMILoaded()) return ingredient;
        return EmiCompat.of(ingredient);
    }

    @Nullable
    public static Object of(@Nullable AEKey key, long amount) {
        return key == null ? null : of(new GenericStack(key, amount));
    }

    @Nullable
    public static Object ofTag(TagKey<?> tag, long amount) {
        return GTCEu.Mods.isEMILoaded() ? EmiCompat.tag(tag, amount) : null;
    }

    public static Component tagName(TagKey<?> tag) {
        return GTCEu.Mods.isEMILoaded() ? EmiCompat.tagName(tag) : Component.literal("#" + tag.location());
    }

    private static final class EmiCompat {

        private static Object tag(TagKey<?> tag, long amount) {
            return new TagEmiIngredient(tag, amount);
        }

        private static Component tagName(TagKey<?> tag) {
            String key = null;
            if (tag.isFor(Registries.ITEM)) key = translated("tag.item.", tag.location());
            else if (tag.isFor(Registries.FLUID)) key = translated("tag.fluid.", tag.location());
            if (key == null) key = translated("tag.", tag.location());
            return key == null ? Component.literal("#" + tag.location()) : Component.translatable(key);
        }

        @Nullable
        private static String translated(String prefix, ResourceLocation id) {
            var language = Language.getInstance();
            var key = EmiUtil.translateId(prefix, id);
            if (language.has(key)) return key;
            if (!"forge".equals(id.getNamespace())) return null;
            key = EmiUtil.translateId(prefix, new ResourceLocation("c", id.getPath()));
            return language.has(key) ? key : null;
        }

        @Nullable
        private static Object of(Object ingredient) {
            if (ingredient instanceof EmiIngredient || ingredient instanceof EmiStackInteraction) return ingredient;
            if (ingredient instanceof ItemStack stack) return stack.isEmpty() ? null : EmiStack.of(stack);
            if (ingredient instanceof FluidStack stack) return stack.isEmpty() ? null : EmiStack.of(stack.getFluid(), stack.getTag(), stack.getAmount());
            if (ingredient instanceof GenericStack stack) return EmiStackHelper.toEmiStack(stack);
            return ingredient;
        }
    }
}
