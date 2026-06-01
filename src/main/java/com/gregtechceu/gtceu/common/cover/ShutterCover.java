package com.gregtechceu.gtceu.common.cover;

import com.gregtechceu.gtceu.api.capability.IControllable;
import com.gregtechceu.gtceu.api.capability.ICoverable;
import com.gregtechceu.gtceu.api.cover.CoverBehavior;
import com.gregtechceu.gtceu.api.cover.CoverDefinition;
import com.gregtechceu.gtceu.api.transfer.fluid.ICustomFluidStackHandler;
import com.gregtechceu.gtceu.api.transfer.item.ICustomItemStackHandler;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;

import com.gto.datasynclib.annotations.SaveToDisk;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;

import javax.annotation.ParametersAreNonnullByDefault;

@Getter
@Setter
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class ShutterCover extends CoverBehavior implements IControllable {

    @SaveToDisk
    private boolean workingEnabled = true;

    public ShutterCover(CoverDefinition definition, ICoverable coverableView, Direction attachedSide) {
        super(definition, coverableView, attachedSide);
    }

    @Override
    public InteractionResult onScrewdriverClick(Player playerIn, InteractionHand hand, BlockHitResult hitResult) {
        return InteractionResult.FAIL;
    }

    @Override
    public boolean canPipePassThrough() {
        return !workingEnabled;
    }

    @Override
    public InteractionResult onSoftMalletClick(Player playerIn, InteractionHand hand, BlockHitResult hitResult) {
        this.workingEnabled = !this.workingEnabled;
        if (!playerIn.level().isClientSide) {
            playerIn.sendSystemMessage(Component.translatable(isWorkingEnabled() ? "cover.shutter.message.enabled" : "cover.shutter.message.disabled"));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    @Nullable
    public ICustomItemStackHandler getItemHandlerCap(ICustomItemStackHandler defaultValue) {
        return isWorkingEnabled() ? null : super.getItemHandlerCap(defaultValue);
    }

    @Override
    @Nullable
    public ICustomFluidStackHandler getFluidHandlerCap(ICustomFluidStackHandler defaultValue) {
        return isWorkingEnabled() ? null : super.getFluidHandlerCap(defaultValue);
    }
}
