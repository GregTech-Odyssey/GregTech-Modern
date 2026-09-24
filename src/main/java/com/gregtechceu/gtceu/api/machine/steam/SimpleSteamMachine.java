package com.gregtechceu.gtceu.api.machine.steam;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.widget.PredicatedImageWidget;
import com.gregtechceu.gtceu.api.machine.feature.IDummyEnergyMachine;
import com.gregtechceu.gtceu.api.machine.feature.IExhaustVentMachine;
import com.gregtechceu.gtceu.api.machine.feature.IUIMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableFluidTank;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.recipe.handler.IRecipeHandlerHolder;
import com.gregtechceu.gtceu.api.recipe.handler.RecipeHandlerUnit;
import com.gregtechceu.gtceu.api.recipe.info.ItemRecipeInfo;
import com.gregtechceu.gtceu.api.recipe.info.RecipeInfo;
import com.gregtechceu.gtceu.common.recipe.condition.VentCondition;
import com.gregtechceu.gtceu.uipro.elements.StatusLine;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.utils.FormattingUtil;

import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.utils.Position;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fluids.FluidType;

import com.google.common.collect.Tables;
import com.gto.datasynclib.annotations.SaveToDisk;
import com.gto.datasynclib.datastream.DataComponentMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceLinkedOpenHashMap;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.EnumMap;
import java.util.function.Supplier;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class SimpleSteamMachine extends SteamWorkableMachine implements IExhaustVentMachine, IUIMachine, IDummyEnergyMachine {

    /// 蒸汽机界面里玩家背包的纵坐标（原版 176×166 容器）
    private static final int INVENTORY_Y = 84;
    /// 蒸汽底板在背包右上方印着 GT 标志，蒸汽储量一行在它左边结束
    private static final int STEAM_LOGO_SPACE = 24;

    @SaveToDisk
    public final NotifiableItemStackHandler importItems;
    @SaveToDisk
    public final NotifiableItemStackHandler exportItems;
    @Setter
    @SaveToDisk(defaultValue = "false")
    private boolean needsVenting;

    @Getter
    private final SteamEnergyContainer energyContainer;

    public SimpleSteamMachine(MetaMachineBlockEntity holder, boolean isHighPressure, Object... args) {
        super(holder, isHighPressure, args);
        this.importItems = createImportItemHandler(args);
        this.exportItems = createExportItemHandler(args);
        this.energyContainer = new SteamEnergyContainer(getConversionRate(), steamTank);
    }

    @Override
    protected NotifiableFluidTank createSteamTank(Object... args) {
        return new NotifiableFluidTank(this, 1, 16 * FluidType.BUCKET_VOLUME, IO.NONE, IO.IN);
    }

    protected NotifiableItemStackHandler createImportItemHandler(Object... args) {
        var handler = new NotifiableItemStackHandler(this, getRecipeType().getMaxInputs(ItemRecipeInfo.INSTANCE), IO.IN);
        if (handler.storage.size == 0) handler.setAvailable(false);
        return handler;
    }

    protected NotifiableItemStackHandler createExportItemHandler(Object... args) {
        var handler = new NotifiableItemStackHandler(this, getRecipeType().getMaxOutputs(ItemRecipeInfo.INSTANCE), IO.OUT);
        if (handler.storage.size == 0) handler.setAvailable(false);
        return handler;
    }

    @Override
    public void onMachineRemoved() {
        clearInventory(importItems.storage);
        clearInventory(exportItems.storage);
    }

    //////////////////////////////////////
    // ****** Venting Logic ******//
    //////////////////////////////////////
    @Override
    public float getVentingDamage() {
        return isHighPressure() ? 12.0F : 6.0F;
    }

    @Override
    public Direction getVentingDirection() {
        return getOutputFacing();
    }

    @Override
    public boolean isNeedsVenting() {
        return this.needsVenting;
    }

    @Override
    public void markVentingComplete() {
        this.needsVenting = false;
    }

    public double getConversionRate() {
        return isHighPressure() ? 2.0 : 1.0;
    }

    @Override
    public void afterWorking() {
        super.afterWorking();
        needsVenting = true;
        checkVenting();
    }

    @Nullable
    public static GTRecipe recipeModifier(IRecipeHandlerHolder machine, RecipeHandlerUnit unit, GTRecipe recipe) {
        if (!(machine instanceof SimpleSteamMachine steamMachine)) {
            return null;
        }
        if (!steamMachine.checkVenting()) {
            return null;
        }
        if (!VentCondition.INSTANCE.testCondition(machine, unit, recipe.definition)) return null;
        if (!steamMachine.isHighPressure) recipe.durationMultiplier(2);
        return recipe;
    }

    //////////////////////////////////////
    // *********** GUI ***********//
    //////////////////////////////////////
    /**
     * 蒸汽机保留自己的铜 / 钢皮肤：蒸汽版底板、槽位和玩家背包，不用新式外壳。
     * 配方槽位区在上方居中；等待中（蒸汽不足或排气口被挡）时槽位区中央显示缺蒸汽图标；
     * 蒸汽储量一行贴在玩家背包正上方。
     */
    @Override
    public ModularUI createUI(Player entityPlayer) {
        var storages = Tables.newCustomTable(new EnumMap<>(IO.class), Reference2ReferenceLinkedOpenHashMap<RecipeInfo, Object>::new);
        storages.put(IO.IN, ItemRecipeInfo.INSTANCE, importItems.storage);
        storages.put(IO.OUT, ItemRecipeInfo.INSTANCE, exportItems.storage);
        var group = getRecipeType().getRecipeUI().createUITemplate(recipeLogic::getProgressPercent, storages, new DataComponentMap(), Collections.emptyList(), true, isHighPressure);
        Position pos = new Position((Math.max(group.getSize().width + 4 + 8, 176) - 4 - group.getSize().width) / 2 + 4, 32);
        group.setSelfPosition(pos);
        var steam = new StatusLine(UISizes.CONTENT_WIDTH - STEAM_LOGO_SPACE, Component.translatable("gtceu.gui.steam_machine.steam"), new SteamText())
                .level(() -> recipeLogic.isWaiting() ? StatusLine.Level.WARNING : StatusLine.Level.NORMAL)
                .detail(() -> recipeLogic.isWaiting() ? Component.translatable("gtceu.gui.steam_machine.waiting") : Component.empty());
        steam.setSelfPosition(new Position(UISizes.WINDOW_PADDING_X, INVENTORY_Y - UISizes.GAP - StatusLine.HEIGHT));
        return new ModularUI(176, 166, this, entityPlayer).background(GuiTextures.BACKGROUND_STEAM.get(isHighPressure)).widget(group)
                .widget(new LabelWidget(5, 5, getBlockState().getBlock().getDescriptionId()))
                .widget(new PredicatedImageWidget(pos.x + group.getSize().width / 2 - 9, pos.y + group.getSize().height / 2 - 9, 18, 18, GuiTextures.INDICATOR_NO_STEAM.get(isHighPressure)).setPredicate(recipeLogic::isWaiting))
                .widget(steam)
                .widget(UITemplate.bindPlayerInventory(entityPlayer.getInventory(), GuiTextures.SLOT_STEAM.get(isHighPressure), UISizes.WINDOW_PADDING_X, INVENTORY_Y, true));
    }

    /** 蒸汽储量一行的数值：服务端每 tick 取值，储量不变时复用上次的文字，不重复拼字符串。 */
    private final class SteamText implements Supplier<Component> {

        private long amount = -1;
        private Component text = Component.empty();

        @Override
        public Component get() {
            long current = steamTank.getFluidInTank(0).getAmount();
            if (current != amount) {
                amount = current;
                text = Component.literal(FormattingUtil.formatNumbers(current) + " / " + FormattingUtil.formatNumbers(steamTank.getTankCapacity(0)) + " mB");
            }
            return text;
        }
    }
}
