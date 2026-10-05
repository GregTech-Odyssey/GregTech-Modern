package com.gregtechceu.gtceu.core.mixins;

import com.gregtechceu.gtceu.api.recipe.content.KeyIngredient;

import net.minecraft.nbt.CompoundTag;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = { AEItemKey.class, AEFluidKey.class }, remap = false)
public abstract class AEKeyIngredientMixin implements KeyIngredient {

    @Shadow(remap = false)
    @Final
    public int uid;

    @Shadow(remap = false)
    @Final
    @Nullable
    private CompoundTag internedTag;

    @Override
    public byte kind() {
        return internedTag == null ? BASE : EXACT;
    }

    @Override
    public AEKey key() {
        return (AEKey) (Object) this;
    }

    @Override
    public int uid() {
        return uid;
    }

    @Override
    public boolean test(AEKey k) {
        return internedTag == null ? k.getUid() == uid : (Object) k == this;
    }
}
