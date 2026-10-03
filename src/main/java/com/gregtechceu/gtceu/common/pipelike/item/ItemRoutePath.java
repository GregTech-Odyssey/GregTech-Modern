package com.gregtechceu.gtceu.common.pipelike.item;

import com.gregtechceu.gtceu.api.data.chemical.material.properties.ItemPipeProperties;
import com.gregtechceu.gtceu.api.pipenet.IRoutePath;
import com.gregtechceu.gtceu.api.transfer.key.IKeyHandler;
import com.gregtechceu.gtceu.api.transfer.key.Keys;
import com.gregtechceu.gtceu.common.blockentity.ItemPipeBlockEntity;
import com.gregtechceu.gtceu.utils.FacingPos;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKeyType;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

public final class ItemRoutePath implements IRoutePath<IKeyHandler<AEItemKey>> {

    @Getter
    private final ItemPipeBlockEntity targetPipe;
    @NotNull
    private final Direction targetFacing;
    @Getter
    private final int distance;
    @Getter
    private final ItemPipeProperties properties;
    private final Predicate<ItemStack> filters;

    public ItemRoutePath(ItemPipeBlockEntity targetPipe, @NotNull Direction facing, int distance, ItemPipeProperties properties, List<Predicate<ItemStack>> filters) {
        this.targetPipe = targetPipe;
        this.targetFacing = facing;
        this.distance = distance;
        this.properties = properties;
        this.filters = stack -> {
            for (Predicate<ItemStack> filter : filters) if (!filter.test(stack)) return false;
            return true;
        };
    }

    @Override
    @NotNull
    public BlockPos getTargetPipePos() {
        return targetPipe.getPipePos();
    }

    @Override
    @Nullable
    @SuppressWarnings("unchecked")
    public IKeyHandler<AEItemKey> getHandler(Level world) {
        return (IKeyHandler<AEItemKey>) targetPipe.blockEntityDirectionCache.getAdjacentKeyHandler(world, getTargetPipePos(), targetFacing, AEKeyType.items());
    }

    public boolean matchesFilters(AEItemKey key) {
        return filters.test(Keys.displayStack(key));
    }

    public FacingPos toFacingPos() {
        return new FacingPos(getTargetPipePos(), targetFacing);
    }

    @NotNull
    public Direction getTargetFacing() {
        return this.targetFacing;
    }
}
