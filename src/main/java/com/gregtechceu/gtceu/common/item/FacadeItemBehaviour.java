package com.gregtechceu.gtceu.common.item;

import com.gregtechceu.gtceu.api.item.component.ICustomDescriptionId;
import com.gregtechceu.gtceu.api.item.component.ICustomRenderer;
import com.gregtechceu.gtceu.client.renderer.cover.FacadeCoverRenderer;

import com.lowdragmc.lowdraglib.client.renderer.IRenderer;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class FacadeItemBehaviour implements ICustomDescriptionId, ICustomRenderer {

    @NotNull
    @Override
    public IRenderer getRenderer() {
        return FacadeCoverRenderer.INSTANCE;
    }

    @Override
    public @Nullable Component getItemName(ItemStack stack) {
        ItemStack facadeStack = getFacadeStack(stack);
        return Component.translatable(stack.getDescriptionId(), facadeStack.getHoverName());
    }

    public static void setFacadeStack(ItemStack itemStack, ItemStack facadeStack) {
        facadeStack = facadeStack.copy();
        facadeStack.setCount(1);
        if (!isValidFacade(facadeStack)) {
            facadeStack = new ItemStack(Blocks.STONE);
        }
        if (!itemStack.hasTag()) {
            itemStack.setTag(new CompoundTag());
        }
        var tagCompound = Objects.requireNonNull(itemStack.getTag());
        tagCompound.put("Facade", facadeStack.save(new CompoundTag()));
    }

    public static boolean isValidFacade(ItemStack itemStack) {
        if (!(itemStack.getItem() instanceof BlockItem blockItem)) {
            return false;
        }
        var rawBlockState = blockItem.getBlock().defaultBlockState();
        return !rawBlockState.hasBlockEntity() && rawBlockState.getRenderShape() == RenderShape.MODEL;
    }

    public static ItemStack getFacadeStack(ItemStack itemStack) {
        ItemStack unsafeStack = getFacadeStackUnsafe(itemStack);
        if (unsafeStack == null) {
            return new ItemStack(Blocks.STONE);
        }
        return unsafeStack;
    }

    @Nullable
    private static ItemStack getFacadeStackUnsafe(ItemStack itemStack) {
        var tagCompound = itemStack.getTag();
        if (tagCompound == null || !tagCompound.contains("Facade", Tag.TAG_COMPOUND)) {
            return null;
        }
        ItemStack facadeStack = ItemStack.of(tagCompound.getCompound("Facade"));
        if (facadeStack.isEmpty() || !isValidFacade(facadeStack)) {
            return null;
        }
        return facadeStack;
    }
}
