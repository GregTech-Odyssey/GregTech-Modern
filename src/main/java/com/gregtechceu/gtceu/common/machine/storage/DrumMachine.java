package com.gregtechceu.gtceu.common.machine.storage;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.PropertyKey;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.item.tool.GTToolType;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.IAutoOutputFluid;
import com.gregtechceu.gtceu.api.machine.feature.IDropSaveMachine;
import com.gregtechceu.gtceu.api.machine.feature.IInteractedMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableFluidTank;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.utils.TaskHandler;

import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;
import com.lowdragmc.lowdraglib.syncdata.ISubscription;
import com.lowdragmc.lowdraglib.syncdata.annotation.DropSaved;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;

import com.gto.datasynclib.annotations.SaveToDisk;
import com.gto.datasynclib.annotations.SyncToClient;
import com.mojang.blaze3d.MethodsReturnNonnullByDefault;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class DrumMachine extends MetaMachine implements IAutoOutputFluid, IDropSaveMachine, IInteractedMachine {

    @Getter
    @SaveToDisk(defaultValue = "false")
    @SyncToClient(notifyUpdate = true)
    protected boolean autoOutputFluids;
    @Getter
    private final int maxStoredFluids;
    @SaveToDisk
    protected final NotifiableFluidTank cache;
    @Nullable
    protected TickableSubscription autoOutputSubs;
    @Nullable
    protected ISubscription exportFluidSubs;
    // rename "Fluid" for Item capability
    @Getter
    @SaveToDisk(key = "Fluid", defaultValueGetter = "getDefaultStored")
    @SyncToClient
    @DropSaved
    protected FluidStack stored = FluidStack.EMPTY;
    @Getter
    protected final Material material;

    public DrumMachine(MetaMachineBlockEntity holder, Material material, int maxStoredFluids, Object... args) {
        super(holder);
        this.material = material;
        this.maxStoredFluids = maxStoredFluids;
        this.cache = createCacheFluidHandler(args);
    }

    private FluidStack getDefaultStored() {
        return FluidStack.EMPTY;
    }

    //////////////////////////////////////
    // ***** Initialization *****//
    //////////////////////////////////////

    protected NotifiableFluidTank createCacheFluidHandler(Object... args) {
        return new NotifiableFluidTank(this, 1, maxStoredFluids, IO.BOTH).setFilter(material.getProperty(PropertyKey.FLUID_PIPE));
    }

    @Override
    public void onLoad() {
        super.onLoad();
        updateStoredFluidFromCache();
        if (getLevel() instanceof ServerLevel serverLevel) {
            TaskHandler.enqueueTask(serverLevel, this::updateAutoOutputSubscription, 0);
            this.exportFluidSubs = cache.addChangedListener(this::onFluidChanged);
        }
    }

    private void onFluidChanged() {
        if (!isRemote()) {
            updateStoredFluidFromCache();
            updateAutoOutputSubscription();
        }
    }

    private void updateStoredFluidFromCache() {
        FluidStack cachedFluid = cache.getFluidInTank(0);
        this.stored = cachedFluid.isEmpty() ? FluidStack.EMPTY : cachedFluid;
    }

    @Override
    public void onUnload() {
        super.onUnload();
        if (exportFluidSubs != null) {
            exportFluidSubs.unsubscribe();
            exportFluidSubs = null;
        }
    }

    //////////////////////////////////////
    // ****** Fluid Logic *******//
    //////////////////////////////////////
    @Override
    public void saveToItem(CompoundTag tag) {
        if (!stored.isEmpty()) {
            tag.put("Fluid", stored.writeToNBT(new CompoundTag()));
        }
    }

    @Override
    public void loadFromItem(CompoundTag tag) {
        if (!tag.contains("Fluid")) {
            stored = FluidStack.EMPTY;
        } else {
            stored = FluidStack.loadFluidStackFromNBT(tag.getCompound("Fluid"));
        }
        // "stored" may not be same as cache (due to item's fluid cap). we should update it.
        cache.getStorages()[0].setFluid(stored.copy());
    }

    @Override
    public boolean savePickClone() {
        return false;
    }

    @Override
    public void setAutoOutputFluids(boolean allow) {
        this.autoOutputFluids = allow;
        updateAutoOutputSubscription();
    }

    @Override
    public boolean isAllowInputFromOutputSideFluids() {
        return false;
    }

    // always is facing down, and can never accept fluids from output side
    @Override
    public void setAllowInputFromOutputSideFluids(boolean allow) {}

    @Override
    public void setOutputFacingFluids(@Nullable Direction outputFacing) {
        clearDirectionCache();
        updateAutoOutputSubscription();
    }

    @Override
    @Nullable
    public Direction getOutputFacingFluids() {
        return Direction.DOWN;
    }

    @Override
    public void onNeighborChanged(Block block, BlockPos fromPos, boolean isMoving) {
        super.onNeighborChanged(block, fromPos, isMoving);
        updateAutoOutputSubscription();
    }

    protected void updateAutoOutputSubscription() {
        var outputFacing = getOutputFacingFluids();
        if ((isAutoOutputFluids() && !cache.isEmpty()) && outputFacing != null && holder.blockEntityDirectionCache.hasAdjacentFluidHandler(getLevel(), getPos(), outputFacing)) {
            autoOutputSubs = subscribeServerTick(autoOutputSubs, this::checkAutoOutput, 20);
        } else if (autoOutputSubs != null) {
            autoOutputSubs.unsubscribe();
            autoOutputSubs = null;
        }
    }

    protected void checkAutoOutput() {
        if (isAutoOutputFluids() && getOutputFacingFluids() != null) {
            cache.exportToNearby(getOutputFacingFluids());
        }
        updateAutoOutputSubscription();
    }

    @Override
    public InteractionResult onUse(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!isRemote()) {
            if (FluidUtil.interactWithFluidHandler(player, hand, cache)) {
                return InteractionResult.SUCCESS;
            }
        }
        return world.isClientSide ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    @Override
    public boolean saveBreak() {
        return !stored.isEmpty();
    }

    @Override
    protected InteractionResult onScrewdriverClick(Player playerIn, InteractionHand hand, Direction gridSide, BlockHitResult hitResult) {
        if (!isRemote()) {
            if (!playerIn.isShiftKeyDown()) {
                setAutoOutputFluids(!isAutoOutputFluids());
                playerIn.sendSystemMessage(Component.translatable("gtceu.machine.drum." + (autoOutputFluids ? "enable" : "disable") + "_output"));
                return InteractionResult.SUCCESS;
            }
        }
        return super.onScrewdriverClick(playerIn, hand, gridSide, hitResult);
    }

    //////////////////////////////////////
    // ******* Rendering ********//
    //////////////////////////////////////
    @Override
    public ResourceTexture sideTips(Player player, BlockPos pos, BlockState state, Set<GTToolType> toolTypes, Direction side) {
        if (toolTypes.contains(GTToolType.SCREWDRIVER)) {
            if (side == getOutputFacingFluids()) {
                return isAutoOutputFluids() ? GuiTextures.TOOL_DISABLE_AUTO_OUTPUT : GuiTextures.TOOL_AUTO_OUTPUT;
            }
        }
        return super.sideTips(player, pos, state, toolTypes, side);
    }
}
