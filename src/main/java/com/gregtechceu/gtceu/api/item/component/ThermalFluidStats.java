package com.gregtechceu.gtceu.api.item.component;

import com.gregtechceu.gtceu.api.data.chemical.material.properties.FluidPipeProperties;
import com.gregtechceu.gtceu.api.item.component.forge.IComponentCapability;
import com.gregtechceu.gtceu.api.misc.forge.SimpleThermalFluidHandlerItemStack;
import com.gregtechceu.gtceu.api.misc.forge.ThermalFluidHandlerItemStack;
import com.gregtechceu.gtceu.client.TooltipsHandler;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidActionResult;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.IFluidBlock;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ThermalFluidStats implements IComponentCapability, IAddInformation, IInteractionItem {

    public final int capacity;
    public final boolean gasProof;
    public final boolean plasmaProof;
    public final boolean allowPartialFill;

    protected ThermalFluidStats(int capacity, boolean gasProof, boolean plasmaProof, boolean allowPartialFill) {
        this.capacity = capacity;
        this.gasProof = gasProof;
        this.plasmaProof = plasmaProof;
        this.allowPartialFill = allowPartialFill;
    }

    public static ThermalFluidStats create(int capacity, boolean gasProof, boolean plasmaProof, boolean allowPartialFill) {
        return new ThermalFluidStats(capacity, gasProof, plasmaProof,
                allowPartialFill);
    }

    public static ThermalFluidStats create(int capacity, @NotNull FluidPipeProperties properties,
                                           boolean allowPartialFill) {
        return new ThermalFluidStats(capacity, properties.isGasProof(), properties.isPlasmaProof(), allowPartialFill);
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(ItemStack itemStack, @NotNull Capability<T> cap) {
        if (cap == ForgeCapabilities.FLUID_HANDLER_ITEM) {
            return ForgeCapabilities.FLUID_HANDLER_ITEM.orEmpty(cap, LazyOptional.of(() -> {
                if (allowPartialFill) {
                    return new ThermalFluidHandlerItemStack(itemStack, capacity, gasProof,
                            plasmaProof);
                }
                return new SimpleThermalFluidHandlerItemStack(itemStack, capacity, gasProof,
                        plasmaProof);
            }));
        }
        return LazyOptional.empty();
    }

    @Override
    public void appendTooltips(ItemStack stack, @Nullable Level level, List<Component> tooltipComponents,
                               TooltipFlag isAdvanced) {
        if (stack.hasTag()) {
            FluidUtil.getFluidContained(stack).ifPresent(tank -> {
                tooltipComponents
                        .add(Component.translatable("gtceu.universal.tooltip.fluid_stored", tank.getDisplayName(),
                                tank.getAmount()));
                TooltipsHandler.appendFluidTooltips(tank, tooltipComponents::add, null);
            });
        } else {
            tooltipComponents.add(Component.translatable("gtceu.universal.tooltip.fluid_storage_capacity",
                    FormattingUtil.formatNumbers(capacity)));
        }
        if (GTUtil.isShiftDown()) {
            if (gasProof) tooltipComponents.add(Component.translatable("gtceu.fluid_pipe.gas_proof"));
            else tooltipComponents.add(Component.translatable("gtceu.fluid_pipe.not_gas_proof"));
            if (plasmaProof) tooltipComponents.add(Component.translatable("gtceu.fluid_pipe.plasma_proof"));
        } else if (gasProof || plasmaProof) {
            tooltipComponents.add(Component.translatable("gtceu.tooltip.fluid_pipe_hold_shift"));
        }
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(Item item, @NotNull Level level, @NotNull Player player, InteractionHand usedHand) {
        ItemStack itemStack = player.getItemInHand(usedHand);
        if (level.isClientSide()) return InteractionResultHolder.consume(itemStack);

        IFluidHandlerItem fluidHandler = FluidUtil.getFluidHandler(itemStack).resolve().orElse(null);
        if (fluidHandler == null) {
            return InteractionResultHolder.pass(itemStack);
        }
        BlockHitResult blockhitresult = IInteractionItem.getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
        if (blockhitresult.getType() == HitResult.Type.MISS) {
            return InteractionResultHolder.pass(itemStack);
        } else if (blockhitresult.getType() != HitResult.Type.BLOCK) {
            return InteractionResultHolder.pass(itemStack);
        }

        BlockPos blockpos = blockhitresult.getBlockPos();
        if (level.mayInteract(player, blockpos)) {
            BlockState blockState = level.getBlockState(blockpos);
            if (blockState.getBlock() instanceof BucketPickup || blockState.getBlock() instanceof IFluidBlock) {
                ItemStack itemStack1 = itemStack.copyWithCount(1);
                FluidActionResult pickUpResult = FluidUtil.tryPickUpFluid(itemStack1, player, level, blockpos, blockhitresult.getDirection());
                if (pickUpResult.isSuccess()) {
                    ItemStack itemStack2 = ItemUtils.createFilledResult(itemStack, player, pickUpResult.getResult());
                    return InteractionResultHolder.success(itemStack2);
                }
            }
        }
        return IInteractionItem.super.use(item, level, player, usedHand);
    }
}
