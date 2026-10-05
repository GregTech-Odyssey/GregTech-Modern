package com.gregtechceu.gtceu.core.mixins;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityWatch;
import com.gregtechceu.gtceu.core.IBlockEntity;

import net.minecraft.world.level.block.entity.BlockEntity;

import com.gto.datasynclib.datastream.DataComponentMap;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntity.class)
public abstract class BlockEntityMixin implements IBlockEntity {

    @Unique
    @Nullable
    private DataComponentMap gtceu$data;

    @Override
    public @Nullable DataComponentMap gtceu$getData() {
        return gtceu$data;
    }

    @Override
    public DataComponentMap gtceu$getOrCreateData() {
        var data = gtceu$data;
        if (data == null) gtceu$data = data = new DataComponentMap();
        return data;
    }

    @Inject(method = "setRemoved", at = @At("TAIL"))
    private void gtceu$watchRemoved(CallbackInfo ci) {
        BlockEntityWatch.changed((BlockEntity) (Object) this);
    }

    @Inject(method = "clearRemoved", at = @At("TAIL"))
    private void gtceu$watchAppeared(CallbackInfo ci) {
        BlockEntityWatch.changed((BlockEntity) (Object) this);
    }

    @Inject(method = "setBlockState", at = @At("TAIL"))
    private void gtceu$watchStateChanged(CallbackInfo ci) {
        BlockEntityWatch.changed((BlockEntity) (Object) this);
    }
}
