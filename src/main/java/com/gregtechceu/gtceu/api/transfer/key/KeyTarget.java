package com.gregtechceu.gtceu.api.transfer.key;

import com.gregtechceu.gtceu.api.transfer.forge.ForgeFluidView;
import com.gregtechceu.gtceu.api.transfer.forge.ForgeItemView;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageAccess;
import appeng.api.storage.StorageTargetResolver;
import appeng.api.storage.StorageTargetResolver.Tier;
import org.jetbrains.annotations.Nullable;

/**
 * GT 消费者对某个面的取用目标：查找顺序只由 {@link StorageTargetResolver} 决定，这里按命中层与原始能力对象的身份复用 GT 视图。
 */
public final class KeyTarget {

    private final Slot items = new Slot();
    private final Slot fluids = new Slot();

    public @Nullable IKeyHandler<?> find(@Nullable BlockEntity be, @Nullable Direction side, AEKeyType type, StorageAccess access) {
        var s = type == AEKeyTypes.ITEMS ? items : type == AEKeyTypes.FLUIDS ? fluids : null;
        return s == null ? resolve(be, side, type, access) : s.find(be, side, type, access);
    }

    public void clear() {
        items.clear();
        fluids.clear();
    }

    public boolean has(@Nullable BlockEntity be, @Nullable Direction side, AEKeyType type, StorageAccess access) {
        var s = type == AEKeyTypes.ITEMS ? items : type == AEKeyTypes.FLUIDS ? fluids : null;
        var r = s == null ? new StorageTargetResolver() : s.resolver;
        return r.resolve(be, side, type, access) != Tier.NONE;
    }

    public static @Nullable IKeyHandler<?> resolve(@Nullable BlockEntity be, @Nullable Direction side, AEKeyType type, StorageAccess access) {
        var r = new StorageTargetResolver();
        var tier = r.resolve(be, side, type, access);
        if (tier == Tier.NONE) return null;
        return view(tier, r.raw(), type);
    }

    private static IKeyHandler<?> view(Tier tier, Object raw, AEKeyType type) {
        if (tier == Tier.STORAGE) return raw instanceof IKeyHandler<?> handler ? handler : new MEStorageKeyView<>((MEStorage) raw, type);
        if (type == AEKeyTypes.ITEMS) return ForgeItemView.of((IItemHandler) raw);
        return ForgeFluidView.of((IFluidHandler) raw);
    }

    private static final class Slot {

        final StorageTargetResolver resolver = new StorageTargetResolver();
        @Nullable
        Object raw;
        @Nullable
        Tier tier;
        @Nullable
        IKeyHandler<?> view;

        void clear() {
            resolver.resolve(null, null, AEKeyTypes.ITEMS, StorageAccess.INSERT);
            raw = null;
            tier = null;
            view = null;
        }

        @Nullable
        IKeyHandler<?> find(@Nullable BlockEntity be, @Nullable Direction side, AEKeyType type, StorageAccess access) {
            var r = resolver;
            var tier = r.resolve(be, side, type, access);
            if (tier == Tier.NONE) return null;
            var raw = r.raw();
            if (raw == this.raw && tier == this.tier) return view;
            var v = KeyTarget.view(tier, raw, type);
            this.raw = raw;
            this.tier = tier;
            this.view = v;
            return v;
        }
    }
}
