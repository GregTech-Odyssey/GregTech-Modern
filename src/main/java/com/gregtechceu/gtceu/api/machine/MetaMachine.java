package com.gregtechceu.gtceu.api.machine;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityWatch;
import com.gregtechceu.gtceu.api.blockentity.IPaintable;
import com.gregtechceu.gtceu.api.blockentity.ISync;
import com.gregtechceu.gtceu.api.blockentity.ITickSubscription;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.capability.IControllable;
import com.gregtechceu.gtceu.api.capability.ICoverable;
import com.gregtechceu.gtceu.api.cover.CoverBehavior;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyTooltip;
import com.gregtechceu.gtceu.api.item.tool.GTToolType;
import com.gregtechceu.gtceu.api.machine.feature.*;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.trait.ICapabilityTrait;
import com.gregtechceu.gtceu.api.machine.trait.MachineTrait;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableInventory;
import com.gregtechceu.gtceu.api.pattern.util.RelativeDirection;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.transfer.forge.ForgeAdapters;
import com.gregtechceu.gtceu.api.transfer.key.IKeyHandler;
import com.gregtechceu.gtceu.api.transfer.key.KeyHandlerList;
import com.gregtechceu.gtceu.api.transfer.key.KeyIOView;
import com.gregtechceu.gtceu.api.transfer.key.KeyInventory;
import com.gregtechceu.gtceu.api.transfer.key.MachineKeyStorage;
import com.gregtechceu.gtceu.api.transfer.key.StackInventory;
import com.gregtechceu.gtceu.common.cover.FluidFilterCover;
import com.gregtechceu.gtceu.common.cover.ItemFilterCover;
import com.gregtechceu.gtceu.common.item.tool.behavior.ToolModeSwitchBehavior;
import com.gregtechceu.gtceu.common.machine.owner.MachineOwner;
import com.gregtechceu.gtceu.common.machine.owner.PlayerOwner;
import com.gregtechceu.gtceu.core.Iblock;
import com.gregtechceu.gtceu.uiwidgets.icon.WidgetIcons;
import com.gregtechceu.gtceu.utils.cache.DirectionCache;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.storage.AEKeyFilter;
import appeng.api.storage.MEStorage;
import com.gto.datasynclib.FieldDataManager;
import com.gto.datasynclib.LazyFieldDataManager;
import com.gto.datasynclib.LogicalSide;
import com.gto.datasynclib.annotations.SaveToDisk;
import com.gto.datasynclib.annotations.SyncToClient;
import com.mojang.datafixers.util.Pair;
import lombok.Getter;
import org.jetbrains.annotations.MustBeInvokedByOverriders;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import javax.annotation.ParametersAreNonnullByDefault;

import static com.gregtechceu.gtceu.api.item.tool.ToolHelper.getBehaviorsTag;

/**
 * an abstract layer of gregtech machine.
 * Because I have to implement BlockEntities for both fabric and forge platform.
 * All fundamental features will be implemented here.
 * To add additional features, you can see {@link IMachineFeature}
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class MetaMachine implements ISync, ITickSubscription, IFancyTooltip, IPaintable, IRedstoneSignalMachine {

    private final LazyFieldDataManager fieldDataManager = new LazyFieldDataManager(this);

    @Getter
    public final MachineDefinition definition;
    @SaveToDisk
    @SyncToClient
    @Nullable
    private UUID ownerUUID;
    @Getter
    public final MetaMachineBlockEntity holder;
    @Getter
    @SyncToClient
    @SaveToDisk(key = "cover")
    public final MachineCoverContainer coverContainer;
    @Getter
    @SaveToDisk(defaultValue = "-1")
    @SyncToClient(scheduleUpdate = true)
    private int paintingColor = -1;
    @Getter
    protected final List<MachineTrait> traits = new ArrayList<>();

    protected final DirectionCache<IKeyHandler<AEItemKey>> itemHandlerModifiableCache = DirectionCache.create();
    protected final DirectionCache<IKeyHandler<AEFluidKey>> fluidHandlerModifiableCache = DirectionCache.create();
    protected final DirectionCache<IKeyHandler<AEItemKey>> itemHandlerModifiableCoverCache = DirectionCache.create();
    protected final DirectionCache<IKeyHandler<AEFluidKey>> fluidHandlerModifiableCoverCache = DirectionCache.create();

    private final DirectionCache<SideExposure> sideExposures = DirectionCache.create();
    private int storageEpoch;

    protected final DirectionCache<FluidState> fluidStateDirectionCache = DirectionCache.create();

    protected Direction frontFacing;

    public MetaMachine(MetaMachineBlockEntity holder) {
        this.definition = holder.definition;
        this.holder = holder;
        this.coverContainer = new MachineCoverContainer(this);
        if (this instanceof IMultiPart part && !part.canShared() && holder.getBlockState().getBlock() instanceof Iblock block) {
            block.gtceu$setMultiShared(false);
        }
    }

    //////////////////////////////////////
    // ***** Initialization ******//
    //////////////////////////////////////

    @Override
    public FieldDataManager getFieldDataManager() {
        return fieldDataManager.get();
    }

    public void onChanged() {
        holder.setChanged();
    }

    public int getOffsetTimer() {
        return holder.getOffsetTimer();
    }

    @Nullable
    public Level getLevel() {
        return holder.level();
    }

    public BlockPos getPos() {
        return holder.pos();
    }

    public BlockState getBlockState() {
        return holder.getBlockState();
    }

    public boolean isRemote() {
        var level = holder.level();
        return level == null ? GTCEu.isClientThread() : level.isClientSide;
    }

    public void notifyNeighborsUpdate() {
        holder.notifyNeighborsUpdate();
    }

    public void scheduleRenderUpdate() {
        holder.scheduleRenderUpdate();
    }

    public void scheduleNeighborShapeUpdate() {
        Level level = getLevel();
        BlockPos pos = getPos();
        if (level == null) return;
        level.getBlockState(pos).updateNeighbourShapes(level, pos, Block.UPDATE_ALL);
    }

    public void setPaintingColor(int color) {
        this.paintingColor = color;
        this.onPaintingColorChanged(color);
    }

    public void onPaintingColorChanged(int color) {}

    public boolean isRemoved() {
        return holder.isRemoved();
    }

    @MustBeInvokedByOverriders
    public void onUnload() {
        traits.forEach(MachineTrait::onMachineUnLoad);
        coverContainer.onUnload();
        clearDirectionCache();
        fluidStateDirectionCache.clearCache();
    }

    @MustBeInvokedByOverriders
    public void onLoad() {
        clearDirectionCache();
        fluidStateDirectionCache.clearCache();
        traits.forEach(MachineTrait::onMachineLoad);
        coverContainer.onLoad();
    }

    /**
     * Use for data not able to be saved with the SyncData system, like optional mod compatiblity in internal machines.
     * 
     * @param tag     the CompoundTag to load data from
     * @param forDrop if the save is done for dropping the machine as an item.
     */
    public void saveCustomPersistedData(CompoundTag tag, boolean forDrop) {
        for (MachineTrait trait : this.getTraits()) {
            trait.saveCustomPersistedData(tag, forDrop);
        }
    }

    public void loadCustomPersistedData(CompoundTag tag) {
        for (MachineTrait trait : this.getTraits()) {
            trait.loadCustomPersistedData(tag);
        }
    }

    //////////////////////////////////////
    // ******* Interaction *******//
    //////////////////////////////////////
    /**
     * Called when a player clicks this meta tile entity with a tool
     *
     * @return SUCCESS / CONSUME (will damage tool) / FAIL if something happened, so tools will get damaged and
     *         animations will be played
     */

    public Pair<GTToolType, InteractionResult> onToolClick(Set<@NotNull GTToolType> toolType, ItemStack itemStack, UseOnContext context) {
        // the side hit from the machine grid
        var playerIn = context.getPlayer();
        if (playerIn == null) return Pair.of(null, InteractionResult.PASS);
        var hand = context.getHand();
        var hitResult = new BlockHitResult(context.getClickLocation(), context.getClickedFace(), context.getClickedPos(), false);
        Direction gridSide = ICoverable.determineGridSideHit(hitResult);
        CoverBehavior coverBehavior = gridSide == null ? null : coverContainer.getCoverAtSide(gridSide);
        if (gridSide == null) gridSide = hitResult.getDirection();
        // Prioritize covers where they apply (Screwdriver, Soft Mallet)
        if (toolType.isEmpty() && playerIn.isShiftKeyDown()) {
            if (coverBehavior != null) {
                return Pair.of(null, coverBehavior.onScrewdriverClick(playerIn, hand, hitResult));
            }
        }
        if (toolType.contains(GTToolType.SCREWDRIVER)) {
            if (coverBehavior != null) {
                return Pair.of(GTToolType.SCREWDRIVER, coverBehavior.onScrewdriverClick(playerIn, hand, hitResult));
            } else return Pair.of(GTToolType.SCREWDRIVER, onScrewdriverClick(playerIn, hand, gridSide, hitResult));
        } else if (toolType.contains(GTToolType.SOFT_MALLET)) {
            if (coverBehavior != null) {
                var result = coverBehavior.onSoftMalletClick(playerIn, hand, hitResult);
                if (result != InteractionResult.PASS) return Pair.of(GTToolType.SOFT_MALLET, result);
            }
            return Pair.of(GTToolType.SOFT_MALLET, onSoftMalletClick(playerIn, hand, gridSide, hitResult));
        } else if (toolType.contains(GTToolType.WRENCH)) {
            return Pair.of(GTToolType.WRENCH, onWrenchClick(playerIn, hand, gridSide, hitResult));
        } else if (toolType.contains(GTToolType.CROWBAR)) {
            if (coverBehavior != null) {
                if (!isRemote()) {
                    getCoverContainer().removeCover(gridSide, playerIn);
                }
                return Pair.of(GTToolType.CROWBAR, InteractionResult.CONSUME);
            }
            return Pair.of(GTToolType.CROWBAR, onCrowbarClick(playerIn, hand, gridSide, hitResult));
        } else if (toolType.contains(GTToolType.HARD_HAMMER)) {
            return Pair.of(GTToolType.HARD_HAMMER, onHardHammerClick(playerIn, hand, gridSide, hitResult));
        }
        return Pair.of(null, InteractionResult.PASS);
    }

    protected InteractionResult onHardHammerClick(Player playerIn, InteractionHand hand, Direction gridSide, BlockHitResult hitResult) {
        if (this instanceof IMufflableMachine mufflableMachine) {
            if (!isRemote()) {
                mufflableMachine.setMuffled(!mufflableMachine.isMuffled());
                playerIn.sendSystemMessage(Component.translatable(mufflableMachine.isMuffled() ? "gtceu.machine.muffle.on" : "gtceu.machine.muffle.off"));
            }
            return InteractionResult.sidedSuccess(playerIn.level().isClientSide);
        }
        return InteractionResult.PASS;
    }

    protected InteractionResult onCrowbarClick(Player playerIn, InteractionHand hand, Direction gridSide, BlockHitResult hitResult) {
        return InteractionResult.PASS;
    }

    protected InteractionResult onWrenchClick(Player playerIn, InteractionHand hand, Direction gridSide, BlockHitResult hitResult) {
        if (gridSide == getFrontFacing() && allowExtendedFacing()) {
            setUpwardsFacing(playerIn.isShiftKeyDown() ? getUpwardsFacing().getCounterClockWise() : getUpwardsFacing().getClockWise());
            return InteractionResult.sidedSuccess(isRemote());
        }
        if (playerIn.isShiftKeyDown()) {
            if (gridSide == getFrontFacing() || !isFacingValid(gridSide)) {
                return InteractionResult.FAIL;
            }
            if (!isRemote()) {
                setFrontFacing(gridSide);
            }
        } else {
            if (isRemote()) return InteractionResult.SUCCESS;
            var itemStack = playerIn.getItemInHand(hand);
            var tagCompound = getBehaviorsTag(itemStack);
            ToolModeSwitchBehavior.WrenchModeType type = ToolModeSwitchBehavior.WrenchModeType.values()[tagCompound.getByte("Mode")];
            if (type.isItem()) {
                if (this instanceof IAutoOutputItem autoOutputItem && (!hasFrontFacing() || gridSide != getFrontFacing())) {
                    autoOutputItem.setOutputFacingItems(gridSide);
                }
            }
            if (type.isFluid()) {
                if (this instanceof IAutoOutputFluid autoOutputFluid && (!hasFrontFacing() || gridSide != getFrontFacing())) {
                    autoOutputFluid.setOutputFacingFluids(gridSide);
                }
            }
        }
        return InteractionResult.sidedSuccess(isRemote());
    }

    protected InteractionResult onSoftMalletClick(Player playerIn, InteractionHand hand, Direction gridSide, BlockHitResult hitResult) {
        var controllable = GTCapabilityHelper.getControllable(holder, gridSide);
        if (controllable == null) return InteractionResult.PASS;
        if (!isRemote()) {
            if (!playerIn.isShiftKeyDown() || !controllable.isWorkingEnabled()) {
                controllable.setWorkingEnabled(!controllable.isWorkingEnabled());
                playerIn.sendSystemMessage(Component.translatable(controllable.isWorkingEnabled() ? "behaviour.soft_hammer.enabled" : "behaviour.soft_hammer.disabled"));
            } else {
                controllable.setSuspendAfterFinish(true);
                playerIn.sendSystemMessage(Component.translatable("behaviour.soft_hammer.idle_after_cycle"));
            }
        }
        return InteractionResult.sidedSuccess(playerIn.level().isClientSide);
    }

    protected InteractionResult onScrewdriverClick(Player playerIn, InteractionHand hand, Direction gridSide, BlockHitResult hitResult) {
        if (isRemote()) return InteractionResult.SUCCESS;
        if (playerIn.isShiftKeyDown()) {
            boolean changed = false;
            if (this instanceof IAutoOutputItem autoOutputItem) {
                if (autoOutputItem.getOutputFacingItems() == gridSide) {
                    autoOutputItem.setAllowInputFromOutputSideItems(!autoOutputItem.isAllowInputFromOutputSideItems());
                    playerIn.displayClientMessage(Component.translatable("gtceu.machine.basic.input_from_output_side." + (autoOutputItem.isAllowInputFromOutputSideItems() ? "allow" : "disallow")).append(Component.translatable("gtceu.creative.chest.item")), true);
                    changed = true;
                }
            }
            if (this instanceof IAutoOutputFluid autoOutputFluid) {
                if (autoOutputFluid.getOutputFacingFluids() == gridSide) {
                    autoOutputFluid.setAllowInputFromOutputSideFluids(!autoOutputFluid.isAllowInputFromOutputSideFluids());
                    playerIn.displayClientMessage(Component.translatable("gtceu.machine.basic.input_from_output_side." + (autoOutputFluid.isAllowInputFromOutputSideFluids() ? "allow" : "disallow")).append(Component.translatable("gtceu.creative.tank.fluid")), true);
                    changed = true;
                }
            }
            if (changed) {
                return InteractionResult.sidedSuccess(playerIn.level().isClientSide);
            }
        } else {
            boolean changed = false;
            if (this instanceof IAutoOutputItem autoOutputItem) {
                if (autoOutputItem.getOutputFacingItems() == gridSide) {
                    autoOutputItem.setAutoOutputItems(!autoOutputItem.isAutoOutputItems());
                    changed = true;
                }
            }
            if (this instanceof IAutoOutputFluid autoOutputFluid) {
                if (autoOutputFluid.getOutputFacingFluids() == gridSide) {
                    autoOutputFluid.setAutoOutputFluids(!autoOutputFluid.isAutoOutputFluids());
                    changed = true;
                }
            }
            if (changed) {
                return InteractionResult.sidedSuccess(playerIn.level().isClientSide);
            }
        }
        return InteractionResult.PASS;
    }

    //////////////////////////////////////
    // ********** MISC ***********//
    //////////////////////////////////////
    @Nullable
    public static MetaMachine getMachine(@Nullable BlockGetter level, BlockPos pos) {
        if (level != null && level.getBlockEntity(pos) instanceof MetaMachineBlockEntity machineBlockEntity) {
            return machineBlockEntity.metaMachine;
        }
        return null;
    }

    @Nullable
    public static MetaMachine getMachine(@Nullable BlockEntity blockEntity) {
        if (blockEntity instanceof MetaMachineBlockEntity machineBlockEntity) {
            return machineBlockEntity.metaMachine;
        }
        return null;
    }

    /**
     * All traits should be initialized while MetaMachine is creating. you cannot add them on the fly.
     */
    public void attachTraits(MachineTrait trait) {
        traits.add(trait);
        clearDirectionCache();
    }

    public void clearDirectionCache() {
        itemHandlerModifiableCache.clearCache();
        itemHandlerModifiableCoverCache.clearCache();
        fluidHandlerModifiableCache.clearCache();
        fluidHandlerModifiableCoverCache.clearCache();
        // invalidate handed-out LazyOptionals so mods caching them (Pipez etc.) re-query
        sideExposures.clearCache(SideExposure::invalidate);
        bumpStorageEpoch();
    }

    public void notifyExposureChanged() {
        clearDirectionCache();
        if (!isRemote() && !isRemoved()) notifyNeighborsUpdate();
    }

    public final int storageEpoch() {
        return storageEpoch;
    }

    private void bumpStorageEpoch() {
        storageEpoch = storageEpoch + 1 & Integer.MAX_VALUE;
        BlockEntityWatch.changed(holder);
    }

    public void clearInventory(StackInventory inventory) {
        for (int i = 0; i < inventory.getSlots(); i++) {
            ItemStack stackInSlot = inventory.getStackInSlot(i);
            if (!stackInSlot.isEmpty()) {
                inventory.setStackInSlot(i, ItemStack.EMPTY);
                Block.popResource(getLevel(), getPos(), stackInSlot);
            }
        }
    }

    public void clearInventory(KeyInventory<?> inventory) {
        if (inventory.keyType() != AEKeyTypes.ITEMS) return;
        int size = inventory.size();
        for (int i = 0; i < size; i++) {
            long amount = inventory.amountAt(i);
            if (amount <= 0) continue;
            var key = (AEItemKey) inventory.rawKeyAt(i);
            inventory.set(i, null, 0);
            int max = Math.max(1, key.getMaxStackSize());
            while (amount > 0) {
                int n = (int) Math.min(amount, max);
                Block.popResource(getLevel(), getPos(), key.toStack(n));
                amount -= n;
            }
        }
    }

    public void clearInventory(NotifiableInventory<?> inventory) {
        clearInventory(inventory.storage);
    }

    public boolean shouldRenderGrid(Player player, BlockPos pos, BlockState state, ItemStack held, Set<GTToolType> toolTypes) {
        if (toolTypes.contains(GTToolType.WRENCH)) return true;
        if (toolTypes.contains(GTToolType.SCREWDRIVER) && (this instanceof IAutoOutputItem || this instanceof IAutoOutputFluid)) return true;
        for (CoverBehavior cover : coverContainer.getCovers()) {
            if (cover.shouldRenderGrid(player, pos, state, held, toolTypes)) return true;
        }
        return false;
    }

    public ResourceTexture sideTips(Player player, BlockPos pos, BlockState state, Set<GTToolType> toolTypes, Direction side) {
        var cover = coverContainer.getCoverAtSide(side);
        if (cover != null) {
            var tips = cover.sideTips(player, pos, state, toolTypes, side);
            if (tips != null) return tips;
        }
        if (toolTypes.contains(GTToolType.WRENCH)) {
            if (player.isShiftKeyDown()) {
                if (isFacingValid(side)) {
                    return GuiTextures.TOOL_FRONT_FACING_ROTATION;
                }
            }
        } else if (toolTypes.contains(GTToolType.SOFT_MALLET)) {
            if (this instanceof IControllable controllable) {
                return controllable.isWorkingEnabled() ? GuiTextures.TOOL_START : GuiTextures.TOOL_PAUSE;
            }
        } else if (toolTypes.contains(GTToolType.HARD_HAMMER)) {
            if (this instanceof IMufflableMachine mufflableMachine) {
                return mufflableMachine.isMuffled() ? GuiTextures.TOOL_SOUND : GuiTextures.TOOL_MUTE;
            }
        }
        return null;
    }

    /**
     * Called to obtain list of AxisAlignedBB used for collision testing, highlight rendering
     * and ray tracing this meta tile entity's block in world
     */
    public void addCollisionBoundingBox(List<VoxelShape> collisionList) {
        collisionList.add(Shapes.block());
    }

    public boolean canSetIoOnSide(@Nullable Direction direction) {
        return !hasFrontFacing() || getFrontFacing() != direction;
    }

    public static Direction getFrontFacing(@Nullable MetaMachine machine) {
        return machine == null ? Direction.NORTH : machine.getFrontFacing();
    }

    public Direction getFrontFacing() {
        if (frontFacing == null) {
            var blockState = getBlockState();
            if (blockState.getBlock() instanceof MetaMachineBlock machineBlock) {
                return frontFacing = machineBlock.getFrontFacing(blockState);
            }
            return frontFacing = Direction.NORTH;
        }
        return frontFacing;
    }

    public final boolean hasFrontFacing() {
        var blockState = getBlockState();
        if (blockState.getBlock() instanceof MetaMachineBlock machineBlock) {
            return machineBlock.getRotationState() != RotationState.NONE;
        }
        return false;
    }

    public static boolean isFacingValid(MetaMachineBlock machineBlock, BlockState blockState, Direction facing) {
        if (machineBlock.getRotationState() != RotationState.NONE && facing == machineBlock.getFrontFacing(blockState)) return false;
        return machineBlock.rotationState.test(facing);
    }

    public boolean isFacingValid(Direction facing) {
        if (hasFrontFacing() && facing == getFrontFacing()) return false;
        var coverContainer = getCoverContainer();
        if (coverContainer.hasCover(facing)) {
            // noinspection DataFlowIssue
            var coverDefinition = coverContainer.getCoverAtSide(facing).coverDefinition;
            var behaviour = coverDefinition.createCoverBehavior(coverContainer, getFrontFacing());
            if (!behaviour.canAttach()) {
                return false;
            }
        }
        var blockState = getBlockState();
        if (blockState.getBlock() instanceof MetaMachineBlock metaMachineBlock) {
            return metaMachineBlock.rotationState.test(facing);
        }
        return false;
    }

    public void setFrontFacing(Direction facing) {
        var oldFacing = getFrontFacing();
        if (allowExtendedFacing()) {
            var newUpwardsFacing = RelativeDirection.simulateAxisRotation(facing, oldFacing, getUpwardsFacing());
            setUpwardsFacing(newUpwardsFacing);
        }
        var blockState = getBlockState();
        if (blockState.getBlock() instanceof MetaMachineBlock metaMachineBlock && isFacingValid(facing)) {
            getLevel().setBlockAndUpdate(getPos(), blockState.setValue(metaMachineBlock.rotationState.property, facing));
        }
        if (getLevel() != null && !getLevel().isClientSide) {
            notifyNeighborsUpdate();
            onChanged();
        }
    }

    public static Direction getUpwardFacing(@Nullable MetaMachine machine) {
        return machine == null || !machine.allowExtendedFacing() ? Direction.NORTH : machine.getBlockState().getValue(MetaMachineBlock.UPWARDS_FACING_PROPERTY);
    }

    public Direction getUpwardsFacing() {
        return this.allowExtendedFacing() ? this.getBlockState().getValue(MetaMachineBlock.UPWARDS_FACING_PROPERTY) : Direction.NORTH;
    }

    public void setUpwardsFacing(Direction upwardsFacing) {
        if (!getDefinition().isAllowExtendedFacing()) {
            return;
        }
        if (upwardsFacing.getAxis() == Direction.Axis.Y) {
            GTCEu.LOGGER.error("Tried to set upwards facing to invalid facing {}! Skipping", upwardsFacing);
            return;
        }
        var blockState = getBlockState();
        if (blockState.getBlock() instanceof MetaMachineBlock && blockState.getValue(MetaMachineBlock.UPWARDS_FACING_PROPERTY) != upwardsFacing) {
            getLevel().setBlockAndUpdate(getPos(), blockState.setValue(MetaMachineBlock.UPWARDS_FACING_PROPERTY, upwardsFacing));
            if (getLevel() != null && !getLevel().isClientSide) {
                notifyNeighborsUpdate();
                onChanged();
            }
        }
    }

    public void onBlockStateChanged() {
        frontFacing = null;
    }

    public void onRotated(Direction oldFacing, Direction newFacing) {
        clearDirectionCache();
        for (var trait : traits) {
            trait.onMachineRotated(oldFacing, newFacing);
        }
        frontFacing = null;
    }

    public boolean allowExtendedFacing() {
        return getDefinition().isAllowExtendedFacing();
    }

    public int tintColor(int index) {
        // index < -100 => emission if shimmer is installed.
        if (index == 1 || index == -111) {
            return getRealColor();
        }
        return -1;
    }

    public BlockState getNeighborBlockState(Direction facing) {
        return holder.blockStateDirectionCache.getOrSet(facing, () -> getLevel().getBlockState(getPos().relative(facing)));
    }

    public FluidState getNeighborFluidState(Direction facing) {
        return fluidStateDirectionCache.getOrSet(facing, () -> getLevel().getFluidState(getPos().relative(facing)));
    }

    public @Nullable MetaMachine getNeighborMachine(Direction facing) {
        if (holder.getNeighborBlockEntity(facing) instanceof MetaMachineBlockEntity entity) {
            return entity.metaMachine;
        }
        return null;
    }

    public void onNeighborChanged(Block block, BlockPos fromPos, boolean isMoving) {
        holder.onNeighborChanged();
        coverContainer.onNeighborChanged(block, fromPos, isMoving);
        fluidStateDirectionCache.clearCache();
    }

    public void animateTick(RandomSource random) {}

    public BlockState getBlockAppearance(BlockState state, BlockAndTintGetter level, BlockPos pos, Direction side, BlockState sourceState, BlockPos sourcePos) {
        var appearance = getCoverContainer().getBlockAppearance(state, level, pos, side, sourceState, sourcePos);
        if (appearance != null) return appearance;
        if (this instanceof IMultiPart part && part.isFormed()) {
            appearance = part.getFormedAppearance(sourceState, sourcePos, side);
            if (appearance != null) return appearance;
        }
        return getDefinition().getAppearance().get();
    }

    @Override
    public int getOutputSignal(@Nullable Direction side) {
        if (side == null) return 0;
        // For some reason, Minecraft requests the output signal from the opposite side...
        CoverBehavior cover = getCoverContainer().getCoverAtSide(side.getOpposite());
        if (cover == null) return 0;
        return cover.getRedstoneSignalOutput();
    }

    @Override
    public boolean canConnectRedstone(Direction side) {
        CoverBehavior cover = getCoverContainer().getCoverAtSide(side);
        if (cover == null) return false;
        return cover.canConnectRedstone();
    }

    //////////////////////////////////////
    // ****** Ownership ********//
    //////////////////////////////////////
    @Nullable
    public MachineOwner getOwner() {
        return MachineOwner.getOwner(ownerUUID);
    }

    @Nullable
    public PlayerOwner getPlayerOwner() {
        return MachineOwner.getPlayerOwner(ownerUUID);
    }

    //////////////////////////////////////
    // ****** Capability ********//
    //////////////////////////////////////
    private SideExposure exposure(@Nullable Direction side) {
        var cached = sideExposures.getCache(side);
        if (cached != null) return (SideExposure) cached;
        var exposure = new SideExposure();
        sideExposures.setCache(side, exposure);
        return exposure;
    }

    @Nullable
    public AEKeyFilter getKeyCapFilter(@Nullable Direction side, IO io, AEKeyType type) {
        if (side == null) return null;
        boolean items = type == AEKeyTypes.ITEMS;
        if (io != IO.IN && io != IO.OUT) return coverFilter(getCoverContainer().getCoverAtSide(side), io, items);
        var e = exposure(side);
        if (!e.filtersResolved) {
            var cover = getCoverContainer().getCoverAtSide(side);
            e.itemIn = coverFilter(cover, IO.IN, true);
            e.itemOut = coverFilter(cover, IO.OUT, true);
            e.fluidIn = coverFilter(cover, IO.IN, false);
            e.fluidOut = coverFilter(cover, IO.OUT, false);
            e.filtersResolved = true;
        }
        if (items) return io == IO.IN ? e.itemIn : e.itemOut;
        return io == IO.IN ? e.fluidIn : e.fluidOut;
    }

    private static @Nullable AEKeyFilter coverFilter(@Nullable CoverBehavior cover, IO io, boolean items) {
        if (items) return cover instanceof ItemFilterCover filterCover ? new ItemCoverFilter(filterCover, io) : null;
        return cover instanceof FluidFilterCover filterCover ? new FluidCoverFilter(filterCover, io) : null;
    }

    @SuppressWarnings("unchecked")
    private <K extends AEKey> List<IKeyHandler<K>> keyTraits(@Nullable Direction side, AEKeyType type) {
        var list = new ArrayList<IKeyHandler<K>>();
        for (var trait : traits) {
            if (trait instanceof ICapabilityTrait capabilityTrait && trait instanceof IKeyHandler<?> handler && handler.keyType() == type && capabilityTrait.hasCapability(side)) {
                list.add((IKeyHandler<K>) handler);
            }
        }
        return list;
    }

    private <K extends AEKey> @Nullable IKeyHandler<K> buildKeyCap(@Nullable Direction side, AEKeyType type, boolean outputOnly) {
        List<IKeyHandler<K>> handlers = keyTraits(side, type);
        if (handlers.isEmpty()) return null;
        var inf = getKeyCapFilter(side, IO.IN, type);
        var outf = getKeyCapFilter(side, IO.OUT, type);
        IKeyHandler<K> base = handlers.size() == 1 ? handlers.get(0) : new KeyHandlerList<>(type, handlers);
        if (!outputOnly && inf == null && outf == null) return base;
        return new KeyIOView<>(base, !outputOnly, true, inf, outf);
    }

    public @Nullable MEStorage getStorageCap(@Nullable Direction side) {
        if (side == null) return null;
        var items = getItemHandlerCap(side, true);
        var fluids = getFluidHandlerCap(side, true);
        if (items == null) return fluids;
        if (fluids == null) return items;
        return new MachineKeyStorage(items, fluids, Component.translatable(getDefinition().getDescriptionId()));
    }

    public final @Nullable MEStorage getExposedStorage(@Nullable Direction side) {
        var e = exposure(side);
        if (!e.storageResolved) {
            e.storage = getStorageCap(side);
            e.storageResolved = true;
        }
        return e.storage;
    }

    public final LazyOptional<MEStorage> getStorageCapability(@Nullable Direction side) {
        var e = exposure(side);
        var cap = e.storageCap;
        if (cap == null) {
            var storage = getExposedStorage(side);
            e.storageCap = cap = storage == null ? LazyOptional.empty() : LazyOptional.of(() -> storage);
        }
        return cap;
    }

    public final LazyOptional<IItemHandler> getItemCapability(@Nullable Direction side) {
        var e = exposure(side);
        var cap = e.items;
        if (cap == null) {
            var handler = getItemHandlerCap(side, true);
            if (handler == null) {
                cap = LazyOptional.empty();
            } else {
                IItemHandler adapter = ForgeAdapters.items(handler);
                cap = LazyOptional.of(() -> adapter);
            }
            e.items = cap;
        }
        return cap;
    }

    public final LazyOptional<IFluidHandler> getFluidCapability(@Nullable Direction side) {
        var e = exposure(side);
        var cap = e.fluids;
        if (cap == null) {
            var handler = getFluidHandlerCap(side, true);
            if (handler == null) {
                cap = LazyOptional.empty();
            } else {
                IFluidHandler adapter = ForgeAdapters.fluids(handler);
                cap = LazyOptional.of(() -> adapter);
            }
            e.fluids = cap;
        }
        return cap;
    }

    public @Nullable IKeyHandler<AEItemKey> getItemHandlerCap(@Nullable Direction side, boolean useCoverCapability) {
        var cache = useCoverCapability ? itemHandlerModifiableCoverCache : itemHandlerModifiableCache;
        if (cache.has(side)) return cache.get(side);
        return cache.getOrSet(side, () -> {
            boolean outputOnly = side != null && this instanceof IAutoOutputItem autoOutput && autoOutput.getOutputFacingItems() == side && !autoOutput.isAllowInputFromOutputSideItems();
            IKeyHandler<AEItemKey> handler = buildKeyCap(side, AEKeyTypes.ITEMS, outputOnly);
            if (handler == null || !useCoverCapability || side == null) return handler;
            CoverBehavior cover = getCoverContainer().getCoverAtSide(side);
            return cover != null ? cover.getItemHandlerCap(handler) : handler;
        });
    }

    public @Nullable IKeyHandler<AEFluidKey> getFluidHandlerCap(@Nullable Direction side, boolean useCoverCapability) {
        var cache = useCoverCapability ? fluidHandlerModifiableCoverCache : fluidHandlerModifiableCache;
        if (cache.has(side)) return cache.get(side);
        return cache.getOrSet(side, () -> {
            boolean outputOnly = side != null && this instanceof IAutoOutputFluid autoOutput && autoOutput.getOutputFacingFluids() == side && !autoOutput.isAllowInputFromOutputSideFluids();
            IKeyHandler<AEFluidKey> handler = buildKeyCap(side, AEKeyTypes.FLUIDS, outputOnly);
            if (handler == null || !useCoverCapability || side == null) return handler;
            CoverBehavior cover = getCoverContainer().getCoverAtSide(side);
            return cover != null ? cover.getFluidHandlerCap(handler) : handler;
        });
    }

    //////////////////////////////////////
    // ******** GUI *********//
    //////////////////////////////////////
    @Override
    public IGuiTexture getFancyTooltipIcon() {
        return WidgetIcons.STATUS_INFO;
    }

    @Override
    public final List<Component> getFancyTooltip() {
        var tooltips = new ArrayList<Component>();
        onAddFancyInformationTooltip(tooltips);
        return tooltips;
    }

    @Override
    public boolean showFancyTooltip() {
        return !getFancyTooltip().isEmpty();
    }

    public void onAddFancyInformationTooltip(List<Component> tooltips) {
        getDefinition().getTooltipBuilder().accept(getDefinition().asStack(), tooltips);
        String mainKey = String.format("%s.machine.%s.tooltip", getDefinition().getId().getNamespace(), getDefinition().getId().getPath());
        if (Language.getInstance().has(mainKey)) {
            tooltips.addFirst(Component.translatable(mainKey));
        }
    }

    @Override
    public int getDefaultPaintingColor() {
        return getDefinition().getDefaultPaintingColor();
    }

    public void requestSync() {
        holder.sync = true;
    }

    public void onCoverUpdate(@Nullable CoverBehavior coverBehavior, Direction side) {
        requestSync();
        itemHandlerModifiableCache.remove(side);
        itemHandlerModifiableCoverCache.remove(side);
        fluidHandlerModifiableCache.remove(side);
        fluidHandlerModifiableCoverCache.remove(side);
        sideExposures.remove(side, SideExposure::invalidate);
        bumpStorageEpoch();
    }

    public void setOwnerUUID(@Nullable final UUID ownerUUID) {
        this.ownerUUID = ownerUUID;
    }

    @Nullable
    public UUID getOwnerUUID() {
        return this.ownerUUID;
    }

    @Override
    public MetaMachine self() {
        return this;
    }

    @Override
    public void scheduleUpdate(LogicalSide side) {
        holder.scheduleUpdate(side);
    }

    private static final class SideExposure {

        @Nullable
        LazyOptional<IItemHandler> items;
        @Nullable
        LazyOptional<IFluidHandler> fluids;
        @Nullable
        LazyOptional<MEStorage> storageCap;
        @Nullable
        MEStorage storage;
        boolean storageResolved;
        @Nullable
        AEKeyFilter itemIn;
        @Nullable
        AEKeyFilter itemOut;
        @Nullable
        AEKeyFilter fluidIn;
        @Nullable
        AEKeyFilter fluidOut;
        boolean filtersResolved;

        void invalidate() {
            if (items != null) items.invalidate();
            if (fluids != null) fluids.invalidate();
            if (storageCap != null) storageCap.invalidate();
        }
    }

    private record ItemCoverFilter(ItemFilterCover cover, IO io) implements AEKeyFilter {

        @Override
        public boolean matches(AEKey key) {
            return !cover.getFilterMode().filters(io) || key instanceof AEItemKey itemKey && cover.getItemFilter().test(itemKey.getReadOnlyStack());
        }
    }

    private record FluidCoverFilter(FluidFilterCover cover, IO io) implements AEKeyFilter {

        @Override
        public boolean matches(AEKey key) {
            return !cover.getFilterMode().filters(io) || key instanceof AEFluidKey fluidKey && cover.getFluidFilter().test(fluidKey.getReadOnlyStack());
        }
    }
}
