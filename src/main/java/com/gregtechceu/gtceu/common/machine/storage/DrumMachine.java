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
import com.gregtechceu.gtceu.api.machine.trait.NotifiableInventory;
import com.gregtechceu.gtceu.api.misc.TickTimeMonitor;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.transfer.forge.ForgeFluidAdapter;
import com.gregtechceu.gtceu.api.transfer.key.Keys;
import com.gregtechceu.gtceu.common.data.GTTickTimeMonitors;
import com.gregtechceu.gtceu.utils.TaskHandler;

import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;
import com.lowdragmc.lowdraglib.syncdata.ISubscription;

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

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.storage.StorageAccess;
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
    @SyncToClient(scheduleUpdate = true)
    protected boolean autoOutputFluids;
    @Getter
    private final int maxStoredFluids;
    @SaveToDisk
    protected final NotifiableInventory<AEFluidKey> cache;
    @Nullable
    protected TickableSubscription autoOutputSubs;
    protected final TickTimeMonitor autoOutputMonitor = holder.monitorTick(GTTickTimeMonitors.AUTO_OUTPUT, this::checkAutoOutput);
    @Nullable
    protected ISubscription exportFluidSubs;
    @Getter
    protected final Material material;

    public DrumMachine(MetaMachineBlockEntity holder, Material material, int maxStoredFluids, Object... args) {
        super(holder);
        this.material = material;
        this.maxStoredFluids = maxStoredFluids;
        this.cache = createCacheFluidHandler(args);
    }

    //////////////////////////////////////
    // ***** Initialization *****//
    //////////////////////////////////////

    protected NotifiableInventory<AEFluidKey> createCacheFluidHandler(Object... args) {
        var inventory = NotifiableInventory.fluids(this, 1, maxStoredFluids, IO.BOTH);
        var pipe = material.getProperty(PropertyKey.FLUID_PIPE);
        if (pipe != null) inventory.setFilter(key -> key instanceof AEFluidKey fluid && pipe.test(Keys.displayFluid(fluid)));
        return inventory;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (getLevel() instanceof ServerLevel serverLevel) {
            TaskHandler.enqueueTask(serverLevel, this::updateAutoOutputSubscription, 0);
            this.exportFluidSubs = cache.addChangedListener(this::onFluidChanged);
        }
    }

    private void onFluidChanged() {
        if (!isRemote()) {
            updateAutoOutputSubscription();
        }
    }

    @Override
    public void onUnload() {
        super.onUnload();
        if (exportFluidSubs != null) {
            exportFluidSubs.unsubscribe();
            exportFluidSubs = null;
        }
    }

    public FluidStack getStored() {
        var key = cache.storage.keyAt(0);
        return key == null ? FluidStack.EMPTY : Keys.toFluidStack(key, cache.storage.amountAt(0));
    }

    //////////////////////////////////////
    // ****** Fluid Logic *******//
    //////////////////////////////////////
    @Override
    public void saveToItem(CompoundTag tag) {
        var stored = getStored();
        if (!stored.isEmpty()) {
            tag.put("Fluid", stored.writeToNBT(new CompoundTag()));
        }
    }

    @Override
    public void loadFromItem(CompoundTag tag) {
        if (!tag.contains("Fluid")) {
            cache.storage.set(0, null, 0);
        } else {
            var stored = FluidStack.loadFluidStackFromNBT(tag.getCompound("Fluid"));
            cache.storage.set(0, Keys.fluid(stored), stored.getAmount());
        }
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
        if ((isAutoOutputFluids() && !cache.isEmpty()) && outputFacing != null && holder.blockEntityDirectionCache.hasAdjacentTarget(getLevel(), getPos(), outputFacing, AEKeyTypes.FLUIDS, StorageAccess.INSERT)) {
            autoOutputSubs = subscribeServerTick(autoOutputSubs, autoOutputMonitor, 20);
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
            if (FluidUtil.interactWithFluidHandler(player, hand, new ForgeFluidAdapter(cache))) {
                return InteractionResult.SUCCESS;
            }
        }
        return world.isClientSide ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    @Override
    public boolean saveBreak() {
        return !cache.storage.isEmpty();
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
