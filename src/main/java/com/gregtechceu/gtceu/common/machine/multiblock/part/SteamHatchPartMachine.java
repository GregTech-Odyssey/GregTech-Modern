package com.gregtechceu.gtceu.common.machine.multiblock.part;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableFluidTank;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uiwidgets.inventory.HatchViews;

import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.utils.Position;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fluids.FluidType;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class SteamHatchPartMachine extends FluidHatchPartMachine {

    /** 蒸汽部件界面（176×166 的铜/钢皮肤）里内容区的顶边（标题下方）、玩家背包的顶边。 */
    public static final int STEAM_CONTENT_Y = 18;
    public static final int STEAM_INVENTORY_Y = 84;
    /** 内容区高度：到背包上方留一个区块间距。 */
    public static final int STEAM_CONTENT_HEIGHT = STEAM_INVENTORY_Y - UISizes.SECTION_GAP - STEAM_CONTENT_Y;
    /** 显示区宽度：右边让出皮肤上背包右上方的 GT 标志。 */
    public static final int STEAM_DISPLAY_WIDTH = UISizes.CONTENT_WIDTH - UISizes.SLOT - UISizes.SECTION_GAP;

    public static final int INITIAL_TANK_CAPACITY = 64 * FluidType.BUCKET_VOLUME;
    public static final boolean IS_STEEL = ConfigHolder.INSTANCE.machines.steelSteamMultiblocks;

    public SteamHatchPartMachine(MetaMachineBlockEntity holder) {
        this(holder, INITIAL_TANK_CAPACITY);
    }

    protected SteamHatchPartMachine(MetaMachineBlockEntity holder, int capacity) {
        super(holder, 0, IO.IN, capacity, 1);
    }

    @Override
    protected NotifiableFluidTank createTank(int initialCapacity, int slots, Object... args) {
        return super.createTank(initialCapacity, slots)
                .setFilter(fluidStack -> fluidStack.getFluid() == GTMaterials.Steam.getFluid());
    }

    @Override
    public ModularUI createUI(Player entityPlayer) {
        // 保留蒸汽皮肤（铜/钢底板、蒸汽槽背包）；内容与其他仓一样分两区：上面流体槽，下面状态面板
        var content = HatchViews.fixedPage(UISizes.CONTENT_WIDTH, STEAM_CONTENT_HEIGHT,
                HatchViews.tankOperation(tank, IO.IN), HatchViews.tankStatus(tank, IO.IN), STEAM_DISPLAY_WIDTH);
        content.setSelfPosition(new Position(UISizes.WINDOW_PADDING_X, STEAM_CONTENT_Y));
        return new ModularUI(176, 166, this, entityPlayer)
                .background(GuiTextures.BACKGROUND_STEAM.get(IS_STEEL))
                .widget(new LabelWidget(6, 6, getBlockState().getBlock().getDescriptionId()))
                .widget(content)
                .widget(UITemplate.bindPlayerInventory(entityPlayer.getInventory(),
                        GuiTextures.SLOT_STEAM.get(IS_STEEL), UISizes.WINDOW_PADDING_X, STEAM_INVENTORY_Y, true));
    }

    // By returning false here, we don't allow shift-clicking
    // with a screwdriver to swap the IO, since this is a
    // hatch that only allows steam in, not
    // a steam version of an input/output hatch
    @Override
    public boolean swapIO() {
        return false;
    }
}
