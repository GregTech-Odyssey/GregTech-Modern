package com.gregtechceu.gtceu.api.machine.steam;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.item.tool.GTToolType;
import com.gregtechceu.gtceu.api.machine.feature.ICleanroomProvider;
import com.gregtechceu.gtceu.api.machine.feature.IMachineLife;
import com.gregtechceu.gtceu.api.machine.feature.IMufflableMachine;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.trait.IRecipeHandlerTrait;
import com.gregtechceu.gtceu.api.machine.trait.MachineTrait;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.recipe.handler.IRecipeHandler;
import com.gregtechceu.gtceu.api.recipe.handler.RecipeHandlerUnit;

import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;
import com.lowdragmc.lowdraglib.syncdata.ISubscription;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import com.gto.datasynclib.annotations.SyncToClient;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public abstract class SteamWorkableMachine extends SteamMachine implements IRecipeLogicMachine, IMufflableMachine, IMachineLife {

    @Nullable
    private ICleanroomProvider cleanroom;
    @Getter
    @Persisted
    @DescSynced
    public final RecipeLogic recipeLogic;
    @Getter
    public final GTRecipeType[] recipeTypes;
    @Getter
    public int activeRecipeType;
    @Getter
    @Persisted
    @SyncToClient(notifyUpdate = true)
    protected Direction outputFacing;
    @Getter
    @Setter
    @Persisted
    @SyncToClient
    protected boolean isMuffled;
    @Getter
    protected final Map<IO, List<RecipeHandlerUnit>> capabilitiesProxy;
    @Getter
    protected final Map<IO, List<IRecipeHandler>> capabilitiesFlat;
    protected final List<ISubscription> traitSubscriptions;

    public SteamWorkableMachine(MetaMachineBlockEntity holder, boolean isHighPressure, Object... args) {
        super(holder, isHighPressure, args);
        this.recipeTypes = getDefinition().getRecipeTypes();
        this.activeRecipeType = 0;
        this.recipeLogic = createRecipeLogic(args);
        this.capabilitiesProxy = new EnumMap<>(IO.class);
        this.capabilitiesFlat = new EnumMap<>(IO.class);
        this.traitSubscriptions = new ArrayList<>();
        this.outputFacing = hasFrontFacing() ? getFrontFacing().getOpposite() : Direction.UP;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        // attach self traits
        Map<IO, List<IRecipeHandler>> ioTraits = new EnumMap<>(IO.class);
        for (MachineTrait trait : getTraits()) {
            if (trait instanceof IRecipeHandlerTrait handlerTrait && handlerTrait.isAvailable() && handlerTrait.getHandlerIO() != IO.NONE) {
                ioTraits.computeIfAbsent(handlerTrait.getHandlerIO(), i -> new ArrayList<>()).add(handlerTrait);
            }
        }
        for (var entry : ioTraits.entrySet()) {
            var handlerList = RecipeHandlerUnit.of(entry.getKey(), entry.getValue());
            this.addHandlerList(handlerList);
            traitSubscriptions.add(handlerList.subscribe(recipeLogic::updateTickSubscription));
        }
    }

    @Override
    public void onUnload() {
        super.onUnload();
        traitSubscriptions.forEach(ISubscription::unsubscribe);
        traitSubscriptions.clear();
        capabilitiesProxy.clear();
        capabilitiesFlat.clear();
    }

    /**
     * @param outputFacing the facing to set
     */
    public void setOutputFacing(Direction outputFacing) {
        if (!hasFrontFacing() || this.outputFacing != getFrontFacing()) {
            this.outputFacing = outputFacing;
        }
    }

    @Override
    protected InteractionResult onWrenchClick(Player playerIn, InteractionHand hand, Direction gridSide, BlockHitResult hitResult) {
        if (!playerIn.isShiftKeyDown() && !isRemote()) {
            if (hasFrontFacing() && gridSide == getFrontFacing()) return InteractionResult.PASS;
            setOutputFacing(gridSide);
            return InteractionResult.CONSUME;
        }
        return super.onWrenchClick(playerIn, hand, gridSide, hitResult);
    }

    //////////////////////////////////////
    // ******* Rendering ********//
    //////////////////////////////////////
    @Override
    public ResourceTexture sideTips(Player player, BlockPos pos, BlockState state, Set<GTToolType> toolTypes, Direction side) {
        if (toolTypes.contains(GTToolType.WRENCH)) {
            if (!player.isShiftKeyDown()) {
                if (!hasFrontFacing() || side != getFrontFacing()) {
                    return GuiTextures.TOOL_IO_FACING_ROTATION;
                }
            }
        }
        return super.sideTips(player, pos, state, toolTypes, side);
    }

    @Nullable
    public ICleanroomProvider getCleanroom() {
        return this.cleanroom;
    }

    public void setCleanroom(@Nullable final ICleanroomProvider cleanroom) {
        this.cleanroom = cleanroom;
        getRecipeLogic().markLastRecipeDirty();
        getRecipeLogic().updateTickSubscription();
    }

    public void setActiveRecipeType(final int activeRecipeType) {
        if (this.activeRecipeType != activeRecipeType) {
            getRecipeLogic().markLastRecipeDirty();
            getRecipeLogic().updateTickSubscription();
        }
    }
}
