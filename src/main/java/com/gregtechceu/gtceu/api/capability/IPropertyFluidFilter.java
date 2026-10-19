package com.gregtechceu.gtceu.api.capability;

import com.gregtechceu.gtceu.api.fluids.FluidState;
import com.gregtechceu.gtceu.api.fluids.GTFluid;
import com.gregtechceu.gtceu.api.tooltip.Tips;
import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.network.chat.Component;
import net.minecraft.world.level.material.Fluid;

import appeng.api.stacks.AEFluidKey;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Predicate;

public interface IPropertyFluidFilter extends Predicate<AEFluidKey> {

    @Override
    default boolean test(@NotNull AEFluidKey stack) {
        Fluid fluid = stack.getFluid();
        if (fluid instanceof GTFluid gtFluid) {
            return canContain(gtFluid.getState());
        } else {
            return !fluid.getFluidType().isLighterThanAir() || isGasProof();
        }
    }

    /**
     * @param state the state to check
     * @return if the state can be contained
     */
    boolean canContain(@NotNull FluidState state);

    /**
     * Append tooltips about containment info
     *
     * @param tooltip             the tooltip to append to
     * @param showToolsInfo       if the "hold shift" line should mention tool info
     * @param showTemperatureInfo if the temperature information should be displayed
     */
    default void appendTooltips(@NotNull List<Component> tooltip, boolean showToolsInfo, boolean showTemperatureInfo) {
        if (GTUtil.isShiftDown()) {
            if (showTemperatureInfo)
                if (isGasProof()) tooltip.add(Tips.supported(Component.translatable("gtceu.fluid_pipe.gas_proof")));
                else tooltip.add(Tips.warning(Component.translatable("gtceu.fluid_pipe.not_gas_proof")));
            if (isPlasmaProof()) tooltip.add(Tips.supported(Component.translatable("gtceu.fluid_pipe.plasma_proof")));
        } else if (isGasProof() || isPlasmaProof()) {
            if (showToolsInfo) {
                tooltip.add(Tips.control(Component.translatable("gtceu.tooltip.tool_fluid_hold_shift")));
            } else {
                tooltip.add(Tips.control(Component.translatable("gtceu.tooltip.fluid_pipe_hold_shift")));
            }
        }
    }

    /**
     * @return whether this filter allows gases
     */
    boolean isGasProof();

    /**
     * @return whether this filter allows plasmas
     */
    boolean isPlasmaProof();
}
