package com.tterrag.registrate.util;

import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;

import com.tterrag.registrate.ICustomfCategoryFill;
import com.tterrag.registrate.util.entry.ItemEntry;
import org.jetbrains.annotations.ApiStatus;

import java.util.function.Consumer;

public final class CreativeModeTabModifier implements CreativeModeTab.Output {

    public static final Consumer<CreativeModeTabModifier> DEFAULT = k -> {};

    private final BuildCreativeModeTabContentsEvent event;

    @ApiStatus.Internal
    public CreativeModeTabModifier(BuildCreativeModeTabContentsEvent event) {
        this.event = event;
    }

    public FeatureFlagSet getFlags() {
        return event.getFlags();
    }

    public boolean hasPermissions() {
        return event.hasPermissions();
    }

    public <T extends Item> void acceptEntry(ItemEntry<T> entry) {
        accept(entry.asStack(), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
    }

    public <T extends Item> void acceptEntry(
                                             ItemEntry<T> entry, CreativeModeTab.TabVisibility visibility) {
        accept(entry.asStack(), visibility);
    }

    @Override
    public void accept(ItemStack stack, CreativeModeTab.TabVisibility visibility) {
        if (stack.getItem() instanceof ICustomfCategoryFill customfCategoryFill) {
            customfCategoryFill.fillItemCategory(i -> event.accept(i, visibility));
        } else {
            event.accept(stack, visibility);
        }
    }
}
