package com.gregtechceu.gtceu.common.cover;

import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.capability.IControllable;
import com.gregtechceu.gtceu.api.capability.ICoverable;
import com.gregtechceu.gtceu.api.cover.CoverBehavior;
import com.gregtechceu.gtceu.api.cover.CoverDefinition;
import com.gregtechceu.gtceu.api.cover.IUICover;
import com.gregtechceu.gtceu.api.tooltip.TipRole;
import com.gregtechceu.gtceu.api.tooltip.TipSpec;
import com.gregtechceu.gtceu.common.cover.data.ControllerMode;
import com.gregtechceu.gtceu.uipro.LayoutStyle;
import com.gregtechceu.gtceu.uipro.elements.ButtonGroup;
import com.gregtechceu.gtceu.uipro.elements.Form;
import com.gregtechceu.gtceu.uipro.elements.NumberField;
import com.gregtechceu.gtceu.uipro.elements.StatusPanel;
import com.gregtechceu.gtceu.uipro.elements.Switch;

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

    public static final TipSpec TIPS = TipSpec.builder().lines(l -> l
            .line(TipRole.SUMMARY, Component.translatable("item.gtceu.machine_controller_cover.tip.summary"))
            .feature(Component.translatable("item.gtceu.machine_controller_cover.tip.normal"))
            .feature(Component.translatable("item.gtceu.machine_controller_cover.tip.inverted"))
            .attention(Component.translatable("item.gtceu.machine_controller_cover.tip.multiple"))
            .detail(Component.translatable("item.gtceu.machine_controller_cover.tip.pause"))
            .rightClick(Component.translatable("item.gtceu.machine_controller_cover.tip.open"))).build();

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

    public boolean shouldAllowWorking() {
        boolean shouldAllowWorking = getInputSignal() < minRedstoneStrength;
        return isInverted != shouldAllowWorking;
    }

    private boolean doOthersAllowWorking() {
        for (var cover : coverHolder.getCovers()) {
            if (this.attachedSide != cover.attachedSide && cover instanceof MachineControllerCover controllerCover && controllerCover.controllerMode == this.controllerMode && !controllerCover.shouldAllowWorking()) {
                return false;
            }
        }
        return true;
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
    private static final Component STATUS_TARGET = Component.translatable("cover.machine_controller.status.target");
    private static final Component STATUS_SIGNAL = Component.translatable("cover.machine_controller.status.signal");
    private static final Component STATUS_WORKING = Component.translatable("cover.machine_controller.status.working");
    private static final Component STATUS_NONE = Component.translatable("cover.machine_controller.status.none");
    private static final Component STATUS_MISSING = Component.translatable("cover.machine_controller.status.missing");
    private static final Component STATUS_ENABLED = Component.translatable("cover.machine_controller.status.enabled");
    private static final Component STATUS_PAUSED = Component.translatable("cover.machine_controller.status.paused");
    private static final Component TARGET_UNAVAILABLE = Component.translatable("cover.machine_controller.target_unavailable");
    private static final Component SECTION_TARGET = Component.translatable("cover.machine_controller.section.target");
    private static final Component SECTION_SIGNAL = Component.translatable("cover.machine_controller.section.signal");
    private static final Component MIN_STRENGTH = Component.translatable("cover.machine_controller.min_strength");
    private static final Component INVERTED = Component.translatable("cover.machine_controller.inverted");
    private static final Component INVERTED_TOOLTIP = Component.translatable("cover.machine_controller.inverted.tooltip");

    private static final Component TARGET_MACHINE = Component.translatable("cover.machine_controller.target.machine");
    private static final Component TARGET_COVER_UP = Component.translatable("cover.machine_controller.target.cover_up");
    private static final Component TARGET_COVER_DOWN = Component.translatable("cover.machine_controller.target.cover_down");
    private static final Component TARGET_COVER_NORTH = Component.translatable("cover.machine_controller.target.cover_north");
    private static final Component TARGET_COVER_EAST = Component.translatable("cover.machine_controller.target.cover_east");
    private static final Component TARGET_COVER_SOUTH = Component.translatable("cover.machine_controller.target.cover_south");
    private static final Component TARGET_COVER_WEST = Component.translatable("cover.machine_controller.target.cover_west");

    @Override
    public Widget createUIWidget() {
        if (!coverHolder.isRemote() && controllerMode != null && getControllable(controllerMode.side) == null) {
            setControllerMode(null);
        }
        var status = new StatusPanel();
        var targetIcon = new ItemStack[] { ItemStack.EMPTY };
        status.addLine(STATUS_TARGET, this::targetName)
                .bindLevel(this::targetLevel)
                .bindIcon(() -> {
                    var stack = targetItem();
                    if (!ItemStack.isSameItemSameTags(stack, targetIcon[0])) targetIcon[0] = stack;
                    return targetIcon[0];
                });
        status.addLine(STATUS_SIGNAL, () -> Component.literal(Integer.toString(getInputSignal())));
        status.addLine(STATUS_WORKING, this::workingState).bindLevel(this::workingLevel);
        var modes = Arrays.stream(ControllerMode.values()).filter(mode -> mode.side != this.attachedSide).toList();
        var modeGroup = ButtonGroup.single(modes.size(), i -> targetComponent(modes.get(i)),
                () -> modes.indexOf(controllerMode), i -> {
                    var mode = modes.get(i);
                    if (getControllable(mode.side) != null) setControllerMode(mode);
                }).optionDisabled(i -> getControllable(modes.get(i).side) == null, TARGET_UNAVAILABLE);
        var target = Form.section(SECTION_TARGET).addChildren(status, modeGroup);
        var signal = Form.section(SECTION_SIGNAL).addChildren(
                Form.numberRow(MIN_STRENGTH, NumberField.ofInt(LayoutStyle.AUTO,
                        this::getMinRedstoneStrength, this::setMinRedstoneStrength, 1, 15)),
                Form.controlRow(INVERTED, Switch.of(this::isInverted, this::setInverted),
                        INVERTED_TOOLTIP));
        return Form.page().addChildren(target, signal);
    }

    private static Component targetComponent(ControllerMode mode) {
        return switch (mode) {
            case MACHINE -> TARGET_MACHINE;
            case COVER_UP -> TARGET_COVER_UP;
            case COVER_DOWN -> TARGET_COVER_DOWN;
            case COVER_NORTH -> TARGET_COVER_NORTH;
            case COVER_EAST -> TARGET_COVER_EAST;
            case COVER_SOUTH -> TARGET_COVER_SOUTH;
            case COVER_WEST -> TARGET_COVER_WEST;
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
        if (controllerMode == null) return STATUS_NONE;
        if (getControllable(controllerMode.side) == null) return STATUS_MISSING;
        var stack = targetItem();
        return stack.isEmpty() ? targetComponent(controllerMode) : stack.getHoverName();
    }

    private com.gregtechceu.gtceu.uipro.Level targetLevel() {
        if (controllerMode == null) return com.gregtechceu.gtceu.uipro.Level.WARNING;
        return getControllable(controllerMode.side) == null ? com.gregtechceu.gtceu.uipro.Level.ERROR : com.gregtechceu.gtceu.uipro.Level.GOOD;
    }

    @Nullable
    private IControllable currentControllable() {
        return controllerMode == null ? null : getControllable(controllerMode.side);
    }

    private Component workingState() {
        var controllable = currentControllable();
        if (controllable == null) return Component.literal("—");
        return controllable.isWorkingEnabled() ? STATUS_ENABLED : STATUS_PAUSED;
    }

    private com.gregtechceu.gtceu.uipro.Level workingLevel() {
        var controllable = currentControllable();
        if (controllable == null) return com.gregtechceu.gtceu.uipro.Level.NORMAL;
        return controllable.isWorkingEnabled() ? com.gregtechceu.gtceu.uipro.Level.GOOD : com.gregtechceu.gtceu.uipro.Level.WARNING;
    }

    @Nullable
    public ControllerMode getControllerMode() {
        return this.controllerMode;
    }
}
