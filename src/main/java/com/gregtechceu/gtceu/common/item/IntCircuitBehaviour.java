package com.gregtechceu.gtceu.common.item;

import com.gregtechceu.gtceu.api.item.component.IAddInformation;
import com.gregtechceu.gtceu.api.item.component.IItemUIFactory;
import com.gregtechceu.gtceu.api.recipe.ingredient.IntCircuitIngredient;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.uiwidgets.circuit.CircuitSelector;
import com.gregtechceu.gtceu.uiwidgets.item.HeldItemPage;

import com.lowdragmc.lowdraglib.gui.factory.HeldItemUIFactory;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.Nullable;

import java.util.List;

public class IntCircuitBehaviour implements IItemUIFactory, IAddInformation {

    public static final int CIRCUIT_MAX = 32;

    public static ItemStack stack(int configuration) {
        var stack = GTItems.PROGRAMMED_CIRCUIT.asStack();
        setCircuitConfiguration(stack, configuration);
        return stack;
    }

    public static void setCircuitConfiguration(HeldItemUIFactory.HeldItemHolder holder, int configuration) {
        setCircuitConfiguration(holder.getHeld(), configuration);
        holder.markAsDirty();
    }

    public static void setCircuitConfiguration(ItemStack itemStack, int configuration) {
        itemStack.getOrCreateTag().putInt(IntCircuitIngredient.Configuration, Math.max(0, configuration));
    }

    public static int getCircuitConfiguration(ItemStack itemStack) {
        if (!itemStack.is(IntCircuitIngredient.PROGRAMMED_CIRCUIT)) return 0;
        var tagCompound = itemStack.getTag();
        if (tagCompound != null && tagCompound.tags.get(IntCircuitIngredient.Configuration) instanceof IntTag intTag) {
            return intTag.getAsInt();
        }
        return 0;
    }

    public static boolean isIntegratedCircuit(ItemStack itemStack) {
        boolean isCircuit = itemStack.is(IntCircuitIngredient.PROGRAMMED_CIRCUIT);
        if (isCircuit && itemStack.getTag() == null) {
            var compound = new CompoundTag();
            compound.putInt(IntCircuitIngredient.Configuration, 0);
            itemStack.setTag(compound);
        }
        return isCircuit;
    }

    // deprecated, not needed (for now)
    @Deprecated
    public static void adjustConfiguration(HeldItemUIFactory.HeldItemHolder holder, int amount) {
        adjustConfiguration(holder.getHeld(), amount);
        holder.markAsDirty();
    }

    // deprecated, not needed (for now)
    @Deprecated
    public static void adjustConfiguration(ItemStack stack, int amount) {
        if (!isIntegratedCircuit(stack)) return;
        int configuration = getCircuitConfiguration(stack);
        configuration += amount;
        configuration = Mth.clamp(configuration, 0, CIRCUIT_MAX);
        setCircuitConfiguration(stack, configuration);
    }

    @Override
    public void appendTooltips(ItemStack stack, @Nullable Level level, List<Component> tooltipComponents,
                               TooltipFlag isAdvanced) {
        int configuration = getCircuitConfiguration(stack);
        tooltipComponents.add(Component.translatable("metaitem.int_circuit.configuration", configuration));
    }

    @Override
    public ModularUI createUI(HeldItemUIFactory.HeldItemHolder holder, Player entityPlayer) {
        return new HeldItemPage(holder, window -> CircuitSelector.grid(() -> getCircuitConfiguration(holder.getHeld()),
                circuit -> setCircuitConfiguration(holder, circuit))).noInventory().createUI(entityPlayer);
    }
}
