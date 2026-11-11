package com.gregtechceu.gtceu.common.cover;

import com.gregtechceu.gtceu.api.capability.ICoverable;
import com.gregtechceu.gtceu.api.cover.CoverBehavior;
import com.gregtechceu.gtceu.api.cover.CoverDefinition;
import com.gregtechceu.gtceu.api.cover.IUICover;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IWorkableMultiPart;
import com.gregtechceu.gtceu.api.tooltip.TipRole;
import com.gregtechceu.gtceu.api.tooltip.TipSpec;
import com.gregtechceu.gtceu.uipro.LayoutStyle;
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

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * 配方处理控制覆盖板：装在多方块结构的总线/仓上，远程开关这个仓是否参与配方。
 * <p>
 * 覆盖板界面上的开关<b>就是</b>仓自己的开关（与仓界面上的按钮是同一个状态）：覆盖板直接改写仓的状态，
 * 因此界面按钮、状态行和配方逻辑看到的永远是同一个值；覆盖板被拆掉后仓保持最后的状态。
 * 关闭后仓仍然照常与管道/ME 收发，只是配方检索、匹配、扣料和产出都会跳过它。
 * <p>
 * 红石是附加条件：最小强度为 0 时忽略红石；大于 0 时按「信号达到强度」（可反相）决定是否放行。
 * 红石禁止时覆盖板把仓关掉并记住原状态，恢复放行时再还原。
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class RecipeHandlerControlCover extends CoverBehavior implements IUICover {

    public static final TipSpec TIPS = TipSpec.builder().lines(l -> l
            .line(TipRole.SUMMARY, Component.translatable("item.gtceu.recipe_handler_control_cover.tip.summary"))
            .feature(Component.translatable("item.gtceu.recipe_handler_control_cover.tip.hatch"))
            .feature(Component.translatable("item.gtceu.recipe_handler_control_cover.tip.switch"))
            .attention(Component.translatable("item.gtceu.recipe_handler_control_cover.tip.shared"))
            .detail(Component.translatable("item.gtceu.recipe_handler_control_cover.tip.ignore"))
            .detail(Component.translatable("item.gtceu.recipe_handler_control_cover.tip.automatic"))
            .detail(Component.translatable("item.gtceu.recipe_handler_control_cover.tip.redstone"))
            .rightClick(Component.translatable("item.gtceu.recipe_handler_control_cover.tip.open"))).build();

    /** 红石是否反相：普通模式信号达到强度才放行，反相则信号低于强度才放行。 */
    @Getter
    @SaveToDisk(defaultValue = "false")
    @SyncToClient
    private boolean inverted = false;
    /** 需要的最小红石强度，0 表示忽略红石。 */
    @Getter
    @SaveToDisk(defaultValue = "0")
    @SyncToClient
    private int minRedstoneStrength = 0;

    /** 红石当前是否正在禁止配方处理；禁止前的仓开关状态存在 {@link #restoreActive}。 */
    @SaveToDisk(defaultValue = "false")
    private boolean redstoneCut;
    /** 红石禁止之前的仓开关状态，恢复放行时还原。 */
    @SaveToDisk(defaultValue = "true")
    private boolean restoreActive = true;

    public RecipeHandlerControlCover(CoverDefinition definition, ICoverable coverHolder, Direction attachedSide) {
        super(definition, coverHolder, attachedSide);
    }

    /**
     * 只有带配方仓的多方块部件（物品总线、流体仓等）能装，覆盖板才有意义。
     */
    @Override
    public boolean canAttach() {
        return super.canAttach() && getTarget() != null;
    }

    @Override
    public void onAttached(ItemStack itemStack, ServerPlayer player) {
        super.onAttached(itemStack, player);
        updateTarget();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        updateTarget();
    }

    @Override
    public void onNeighborChanged(Block block, BlockPos fromPos, boolean isMoving) {
        super.onNeighborChanged(block, fromPos, isMoving);
        updateTarget();
    }

    @Override
    public boolean canConnectRedstone() {
        return true;
    }

    ///////////////////////////////////////////////////
    // *********** CONTROL LOGIC ***********//
    ///////////////////////////////////////////////////

    /**
     * 覆盖板挂着的仓；不是带配方仓的部件时为 {@code null}（例如被拆掉或换了方块）。
     */
    @Nullable
    public IWorkableMultiPart getTarget() {
        if (MetaMachine.getMachine(coverHolder.holder()) instanceof IWorkableMultiPart part && part.hasRecipeContentHandlers()) {
            return part;
        }
        return null;
    }

    /** 红石条件当前是否放行；最小强度为 0 时恒为 {@code true}。 */
    public boolean redstoneAllows() {
        if (minRedstoneStrength <= 0) return true;
        boolean reached = getInputSignal() >= minRedstoneStrength;
        return inverted != reached;
    }

    /**
     * 按红石条件改写目标仓：禁止时关掉并记住原状态，恢复放行时还原；
     * 忽略红石时什么都不做，仓的开关完全由玩家（仓按钮或覆盖板开关）决定。
     */
    public void updateTarget() {
        if (coverHolder.isRemote()) return;
        var target = getTarget();
        if (target == null) return;
        if (redstoneAllows()) {
            if (redstoneCut) {
                redstoneCut = false;
                target.setRecipeHandlingActive(restoreActive);
            }
        } else {
            if (!redstoneCut) {
                restoreActive = target.isRecipeHandlingActive();
                redstoneCut = true;
            }
            target.setRecipeHandlingActive(false);
        }
    }

    /** 覆盖板上的开关：直接读写目标仓的开关状态，所以仓按钮改了它也会跟着变。 */
    public boolean isTargetActive() {
        var target = getTarget();
        return target != null && target.isRecipeHandlingActive();
    }

    public void setTargetActive(boolean active) {
        var target = getTarget();
        if (target == null) return;
        if (redstoneCut) restoreActive = active;
        target.setRecipeHandlingActive(active);
    }

    public void setInverted(boolean inverted) {
        if (this.inverted == inverted) return;
        this.inverted = inverted;
        coverHolder.onChanged();
        updateTarget();
    }

    public void setMinRedstoneStrength(int minRedstoneStrength) {
        int clamped = Math.clamp(minRedstoneStrength, 0, 15);
        if (this.minRedstoneStrength == clamped) return;
        this.minRedstoneStrength = clamped;
        coverHolder.onChanged();
        updateTarget();
    }

    private int getInputSignal() {
        Level level = coverHolder.getLevel();
        BlockPos sourcePos = coverHolder.getPos().relative(attachedSide);
        return level.getSignal(sourcePos, attachedSide);
    }

    //////////////////////////////////////
    // *********** GUI ***********//
    //////////////////////////////////////
    private static final Component STATUS_TARGET = Component.translatable("cover.recipe_handler_control.status.target");
    private static final Component STATUS_STATE = Component.translatable("cover.recipe_handler_control.status.state");
    private static final Component STATUS_SIGNAL = Component.translatable("cover.recipe_handler_control.status.signal");
    private static final Component STATUS_ON = Component.translatable("cover.recipe_handler_control.status.on");
    private static final Component STATUS_OFF = Component.translatable("cover.recipe_handler_control.status.off");
    private static final Component STATUS_NONE = Component.translatable("cover.recipe_handler_control.status.none");
    private static final Component STATUS_MISSING = Component.translatable("cover.recipe_handler_control.status.missing");
    private static final Component SECTION_CONTROL = Component.translatable("cover.recipe_handler_control.section.control");
    private static final Component ENABLED = Component.translatable("cover.recipe_handler_control.enabled");
    private static final Component ENABLED_TOOLTIP = Component.translatable("cover.recipe_handler_control.enabled.tooltip");
    private static final Component INVERTED = Component.translatable("cover.recipe_handler_control.inverted");
    private static final Component INVERTED_TOOLTIP = Component.translatable("cover.recipe_handler_control.inverted.tooltip");
    private static final Component MIN_STRENGTH = Component.translatable("cover.recipe_handler_control.min_strength");
    private static final Component MIN_STRENGTH_TOOLTIP = Component.translatable("cover.recipe_handler_control.min_strength.tooltip");
    private static final Component REDSTONE_BLOCKED = Component.translatable("cover.recipe_handler_control.redstone_blocked");

    @Override
    public Widget createUIWidget() {
        // 服务端每次开界面时顺带按红石条件校正一次
        updateTarget();
        var status = new StatusPanel();
        status.addLine(STATUS_TARGET, this::targetName)
                .bindLevel(this::targetLevel);
        status.addLine(STATUS_STATE, this::stateName)
                .bindLevel(this::stateLevel);
        status.addLine(STATUS_SIGNAL, () -> Component.literal(Integer.toString(getInputSignal())));
        var toggle = Switch.of(this::isTargetActive, this::setTargetActive)
                .disabled(() -> !redstoneAllows(), REDSTONE_BLOCKED);
        var control = Form.section(SECTION_CONTROL).addChildren(
                Form.controlRow(ENABLED, toggle, ENABLED_TOOLTIP),
                Form.controlRow(INVERTED, Switch.of(this::isInverted, this::setInverted), INVERTED_TOOLTIP),
                Form.numberRow(MIN_STRENGTH, NumberField.ofInt(LayoutStyle.AUTO,
                        this::getMinRedstoneStrength, this::setMinRedstoneStrength, 0, 15), MIN_STRENGTH_TOOLTIP));
        return Form.page().addChildren(status, control);
    }

    private Component targetName() {
        var target = getTarget();
        if (target == null) return STATUS_NONE;
        return target.self().getBlockState().getBlock().getName();
    }

    private com.gregtechceu.gtceu.uipro.Level targetLevel() {
        return getTarget() == null ? com.gregtechceu.gtceu.uipro.Level.ERROR : com.gregtechceu.gtceu.uipro.Level.NORMAL;
    }

    private Component stateName() {
        var target = getTarget();
        if (target == null) return STATUS_MISSING;
        return target.isRecipeHandlingActive() ? STATUS_ON : STATUS_OFF;
    }

    private com.gregtechceu.gtceu.uipro.Level stateLevel() {
        var target = getTarget();
        if (target == null) return com.gregtechceu.gtceu.uipro.Level.ERROR;
        return target.isRecipeHandlingActive() ? com.gregtechceu.gtceu.uipro.Level.GOOD : com.gregtechceu.gtceu.uipro.Level.WARNING;
    }
}
