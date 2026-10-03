package com.gregtechceu.gtceu.api.machine.steam;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.widget.PredicatedImageWidget;
import com.gregtechceu.gtceu.api.machine.feature.IDummyEnergyMachine;
import com.gregtechceu.gtceu.api.machine.feature.IExhaustVentMachine;
import com.gregtechceu.gtceu.api.machine.feature.IFancyUIMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableInventory;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.recipe.handler.IRecipeHandlerHolder;
import com.gregtechceu.gtceu.api.recipe.handler.RecipeHandlerUnit;
import com.gregtechceu.gtceu.api.recipe.info.ItemRecipeInfo;
import com.gregtechceu.gtceu.api.recipe.info.RecipeInfo;
import com.gregtechceu.gtceu.common.recipe.condition.VentCondition;
import com.gregtechceu.gtceu.uipro.Level;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.elements.StatusLine;
import com.gregtechceu.gtceu.uipro.styletemplate.MachineEra;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.window.MachineWindow;
import com.gregtechceu.gtceu.uiwidgets.recipe.RecipeMachinePage;
import com.gregtechceu.gtceu.utils.FormattingUtil;

import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fluids.FluidType;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import com.google.common.collect.Tables;
import com.gto.datasynclib.annotations.SaveToDisk;
import com.gto.datasynclib.datastream.DataComponentMap;
import dev.vfyjxf.taffy.style.TaffyPosition;
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
public class SimpleSteamMachine extends SteamWorkableMachine implements IExhaustVentMachine, IFancyUIMachine, IDummyEnergyMachine {

    @SaveToDisk
    public final NotifiableInventory<AEItemKey> importItems;
    @SaveToDisk
    public final NotifiableInventory<AEItemKey> exportItems;
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
    protected NotifiableInventory<AEFluidKey> createSteamTank(Object... args) {
        return NotifiableInventory.fluids(this, 1, 16 * FluidType.BUCKET_VOLUME, IO.NONE, IO.IN);
    }

    protected NotifiableInventory<AEItemKey> createImportItemHandler(Object... args) {
        var handler = NotifiableInventory.items(this, getRecipeType().getMaxInputs(ItemRecipeInfo.INSTANCE), IO.IN);
        if (handler.storage.size() == 0) handler.setAvailable(false);
        return handler;
    }

    protected NotifiableInventory<AEItemKey> createExportItemHandler(Object... args) {
        var handler = NotifiableInventory.items(this, getRecipeType().getMaxOutputs(ItemRecipeInfo.INSTANCE), IO.OUT);
        if (handler.storage.size() == 0) handler.setAvailable(false);
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

    @Override
    public void onWaiting() {
        super.onWaiting();
        var recipe = recipeLogic.getLastRecipe();
        if (recipe != null && recipe.eut > 0 && !useEnergy(recipe.eut, true)) setIdleReason(Component.translatable("gtceu.issue.steam_short"));
    }

    @Nullable
    public static GTRecipe recipeModifier(IRecipeHandlerHolder machine, RecipeHandlerUnit unit, GTRecipe recipe) {
        if (!(machine instanceof SimpleSteamMachine steamMachine)) {
            return null;
        }
        if (!steamMachine.checkVenting()) {
            machine.setIdleReason(Component.translatable("gtceu.issue.vent_blocked"));
            return null;
        }
        if (!VentCondition.INSTANCE.testCondition(machine, unit, recipe.definition)) {
            machine.setIdleReason(Component.translatable("gtceu.issue.vent_blocked"));
            return null;
        }
        if (!steamMachine.isHighPressure) recipe.durationMultiplier(2);
        return recipe;
    }

    //////////////////////////////////////
    // *********** GUI ***********//
    //////////////////////////////////////
    @Override
    public ModularUI createUI(Player entityPlayer) {
        return MachineWindow.createUI(this, this, entityPlayer);
    }

    @Override
    public ResourceLocation getWindowSkin() {
        return MachineEra.steam(isHighPressure).getSkin();
    }

    @Override
    public Widget createUIWidget() {
        var storages = Tables.newCustomTable(new EnumMap<>(IO.class), Reference2ReferenceLinkedOpenHashMap<RecipeInfo, Object>::new);
        storages.put(IO.IN, ItemRecipeInfo.INSTANCE, importItems.storage);
        storages.put(IO.OUT, ItemRecipeInfo.INSTANCE, exportItems.storage);
        var group = getRecipeType().getRecipeUI().createUITemplate(recipeLogic::getProgressPercent, storages, new DataComponentMap(), Collections.emptyList(), true, isHighPressure);
        int width = group.getSize().width, height = group.getSize().height;
        var stage = new UIElement().layout(l -> l.size(width, height));
        stage.addChild(group);
        var indicator = new UIElement().layout(l -> l.positionType(TaffyPosition.ABSOLUTE).left(width / 2 - 9).top(height / 2 - 9).size(18, 18));
        indicator.addChild(new PredicatedImageWidget(0, 0, 18, 18, GuiTextures.INDICATOR_NO_STEAM.get(isHighPressure)).setPredicate(recipeLogic::isWaiting));
        stage.addChild(indicator);
        var steam = StatusLine.of(UISizes.CONTENT_WIDTH, Component.translatable("gtceu.gui.steam_machine.steam"), new SteamText())
                .bindLevel(() -> recipeLogic.isWaiting() ? Level.WARNING : Level.NORMAL)
                .bindDetail(() -> recipeLogic.isWaiting() ? Component.translatable("gtceu.gui.steam_machine.waiting") : Component.empty());
        return RecipeMachinePage.page(stage, steam);
    }

    /** 蒸汽储量一行的数值：服务端每 tick 取值，储量不变时复用上次的文字，不重复拼字符串。 */
    private final class SteamText implements Supplier<Component> {

        private long amount = -1;
        private Component text = Component.empty();

        @Override
        public Component get() {
            long current = steamTank.storage.amountAt(0);
            if (current != amount) {
                amount = current;
                text = Component.literal(FormattingUtil.formatNumbers(current) + " / " + FormattingUtil.formatNumbers(steamTank.storage.slotLimit(0)) + " mB");
            }
            return text;
        }
    }
}
