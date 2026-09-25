package com.gregtechceu.gtceu.common.cover;

import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.capability.IControllable;
import com.gregtechceu.gtceu.api.capability.ICoverable;
import com.gregtechceu.gtceu.api.cover.CoverBehavior;
import com.gregtechceu.gtceu.api.cover.CoverDefinition;
import com.gregtechceu.gtceu.api.cover.IUICover;
import com.gregtechceu.gtceu.common.cover.data.ControllerMode;
import com.gregtechceu.gtceu.uipro.LayoutStyle;
import com.gregtechceu.gtceu.uipro.elements.ButtonGroup;
import com.gregtechceu.gtceu.uipro.elements.NumberField;
import com.gregtechceu.gtceu.uipro.elements.StatusLine;
import com.gregtechceu.gtceu.uipro.elements.StatusPanel;
import com.gregtechceu.gtceu.uipro.elements.Switch;
import com.gregtechceu.gtceu.uiwidgets.cover.CoverUIs;

import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import com.gto.datasynclib.annotations.SaveToDisk;
import com.gto.datasynclib.annotations.SyncToClient;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class MachineControllerCover extends CoverBehavior implements IUICover {

    @Getter
    @SaveToDisk
    private boolean isInverted = false;
    @Getter
    @SaveToDisk
    private int minRedstoneStrength = 1;
    @SaveToDisk
    @SyncToClient
    @Nullable
    private ControllerMode controllerMode = ControllerMode.MACHINE;

    public MachineControllerCover(CoverDefinition definition, ICoverable coverHolder, Direction attachedSide) {
        super(definition, coverHolder, attachedSide);
    }

    @Override
    public boolean canAttach() {
        return super.canAttach() && !getAllowedModes().isEmpty();
    }

    @Override
    public void onAttached(ItemStack itemStack, ServerPlayer player) {
        super.onAttached(itemStack, player);
        var allowedModes = getAllowedModes();
        setControllerMode(allowedModes.isEmpty() ? null : allowedModes.getFirst());
    }

    @Override
    public void onRemoved() {
        super.onRemoved();
        resetCurrentControllable();
    }

    @Override
    public boolean canConnectRedstone() {
        return true;
    }

    @Override
    public void onNeighborChanged(Block block, BlockPos fromPos, boolean isMoving) {
        super.onNeighborChanged(block, fromPos, isMoving);
        updateInput();
    }

    public void setControllerMode(@Nullable ControllerMode controllerMode) {
        resetCurrentControllable();
        this.controllerMode = controllerMode;
        updateInput();
    }

    public void setMinRedstoneStrength(int minRedstoneStrength) {
        this.minRedstoneStrength = minRedstoneStrength;
        coverHolder.onChanged();
        updateInput();
    }

    public void setInverted(boolean inverted) {
        isInverted = inverted;
        coverHolder.onChanged();
        updateInput();
    }

    ///////////////////////////////////////////////////
    // *********** CONTROLLER LOGIC ***********//
    ///////////////////////////////////////////////////
    @Nullable
    private IControllable getControllable(@Nullable Direction side) {
        if (side == null) {
            return GTCapabilityHelper.getControllable(coverHolder.holder(), null);
        }
        if (coverHolder.getCoverAtSide(side) instanceof IControllable cover) {
            return cover;
        } else {
            return null;
        }
    }

    private void updateInput() {
        if (controllerMode == null) return;
        IControllable controllable = getControllable(controllerMode.side);
        if (controllable != null) {
            controllable.setWorkingEnabled(shouldAllowWorking() && doOthersAllowWorking());
        }
    }

    private void resetCurrentControllable() {
        if (controllerMode == null) return;
        IControllable controllable = getControllable(controllerMode.side);
        if (controllable != null) {
            controllable.setWorkingEnabled(doOthersAllowWorking());
        }
    }

    private boolean shouldAllowWorking() {
        boolean shouldAllowWorking = getInputSignal() < minRedstoneStrength;
        return isInverted != shouldAllowWorking;
    }

    private boolean doOthersAllowWorking() {
        return coverHolder.getCovers().stream().filter(cover -> this.attachedSide != cover.attachedSide).filter(cover -> cover instanceof MachineControllerCover).filter(cover -> ((MachineControllerCover) cover).controllerMode == this.controllerMode).allMatch(cover -> ((MachineControllerCover) cover).shouldAllowWorking());
    }

    public List<ControllerMode> getAllowedModes() {
        return Arrays.stream(ControllerMode.values()).filter(mode -> mode.side != this.attachedSide).filter(mode -> getControllable(mode.side) != null).collect(Collectors.toList());
    }

    private int getInputSignal() {
        Level level = coverHolder.getLevel();
        BlockPos sourcePos = coverHolder.getPos().relative(attachedSide);
        return level.getSignal(sourcePos, attachedSide);
    }

    //////////////////////////////////////
    // *********** GUI ***********//
    //////////////////////////////////////
    @Override
    public Widget createUIWidget() {
        if (!coverHolder.isRemote() && controllerMode != null && getControllable(controllerMode.side) == null) {
            setControllerMode(null);
        }
        var status = new StatusPanel();
        var targetIcon = new ItemStack[] { ItemStack.EMPTY };
        status.addLine("cover.machine_controller.status.target", this::targetName)
                .level(this::targetLevel)
                .icon(() -> {
                    var stack = targetItem();
                    if (!ItemStack.isSameItemSameTags(stack, targetIcon[0])) targetIcon[0] = stack;
                    return targetIcon[0];
                });
        status.addLine("cover.machine_controller.status.signal", () -> Component.literal(Integer.toString(getInputSignal())));
        status.addLine("cover.machine_controller.status.working", this::workingState).level(this::workingLevel);
        var modes = Arrays.stream(ControllerMode.values()).filter(mode -> mode.side != this.attachedSide).toList();
        var modeGroup = ButtonGroup.single(modes.size(), i -> Component.translatable(targetKey(modes.get(i))),
                () -> modes.indexOf(controllerMode), i -> {
                    var mode = modes.get(i);
                    if (getControllable(mode.side) != null) setControllerMode(mode);
                }).optionDisabled(i -> getControllable(modes.get(i).side) == null, "cover.machine_controller.target_unavailable");
        var target = CoverUIs.section("cover.machine_controller.section.target").addChildren(status, modeGroup);
        var signal = CoverUIs.section("cover.machine_controller.section.signal").addChildren(
                CoverUIs.numberRow("cover.machine_controller.min_strength", NumberField.of(LayoutStyle.AUTO,
                        this::getMinRedstoneStrength, value -> setMinRedstoneStrength((int) value), 1, 15)),
                CoverUIs.controlRow("cover.machine_controller.inverted", Switch.of(this::isInverted, this::setInverted),
                        "cover.machine_controller.inverted.tooltip"));
        return CoverUIs.page().addChildren(target, signal);
    }

    private static String targetKey(ControllerMode mode) {
        return switch (mode) {
            case MACHINE -> "cover.machine_controller.target.machine";
            case COVER_UP -> "cover.machine_controller.target.cover_up";
            case COVER_DOWN -> "cover.machine_controller.target.cover_down";
            case COVER_NORTH -> "cover.machine_controller.target.cover_north";
            case COVER_EAST -> "cover.machine_controller.target.cover_east";
            case COVER_SOUTH -> "cover.machine_controller.target.cover_south";
            case COVER_WEST -> "cover.machine_controller.target.cover_west";
        };
    }

    private ItemStack targetItem() {
        if (controllerMode == null || getControllable(controllerMode.side) == null) return ItemStack.EMPTY;
        if (controllerMode.side == null) {
            return new ItemStack(coverHolder.getLevel().getBlockState(coverHolder.getPos()).getBlock());
        }
        var cover = coverHolder.getCoverAtSide(controllerMode.side);
        return cover == null ? ItemStack.EMPTY : cover.getAttachItem();
    }

    private Component targetName() {
        if (controllerMode == null) return Component.translatable("cover.machine_controller.status.none");
        if (getControllable(controllerMode.side) == null) return Component.translatable("cover.machine_controller.status.missing");
        var stack = targetItem();
        return stack.isEmpty() ? Component.translatable(targetKey(controllerMode)) : stack.getHoverName();
    }

    private StatusLine.Level targetLevel() {
        if (controllerMode == null) return StatusLine.Level.WARNING;
        return getControllable(controllerMode.side) == null ? StatusLine.Level.ERROR : StatusLine.Level.GOOD;
    }

    @Nullable
    private IControllable currentControllable() {
        return controllerMode == null ? null : getControllable(controllerMode.side);
    }

    private Component workingState() {
        var controllable = currentControllable();
        if (controllable == null) return Component.literal("—");
        return Component.translatable(controllable.isWorkingEnabled() ? "cover.machine_controller.status.enabled" : "cover.machine_controller.status.paused");
    }

    private StatusLine.Level workingLevel() {
        var controllable = currentControllable();
        if (controllable == null) return StatusLine.Level.NORMAL;
        return controllable.isWorkingEnabled() ? StatusLine.Level.GOOD : StatusLine.Level.WARNING;
    }

    @Nullable
    public ControllerMode getControllerMode() {
        return this.controllerMode;
    }
}
