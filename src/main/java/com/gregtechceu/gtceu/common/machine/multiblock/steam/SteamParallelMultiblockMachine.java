package com.gregtechceu.gtceu.common.machine.multiblock.steam;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.fancy.ConfiguratorPanel;
import com.gregtechceu.gtceu.api.gui.fancy.TooltipsPanel;
import com.gregtechceu.gtceu.api.machine.feature.IDummyEnergyMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiblockFancyUIMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockDisplayText;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.steam.SteamEnergyContainer;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableInventory;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.recipe.handler.IRecipeHandlerHolder;
import com.gregtechceu.gtceu.api.recipe.handler.RecipeHandlerUnit;
import com.gregtechceu.gtceu.api.recipe.modifier.ParallelLogic;
import com.gregtechceu.gtceu.api.recipe.modifier.RecipeModifier;
import com.gregtechceu.gtceu.api.transfer.key.Keys;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.elements.ProgressBar;
import com.gregtechceu.gtceu.uipro.styletemplate.MachineEra;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;
import com.gregtechceu.gtceu.uiwidgets.icon.IdleReasonInfo;
import com.gregtechceu.gtceu.uiwidgets.multiblock.MultiblockPage;
import com.gregtechceu.gtceu.utils.FormattingUtil;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;

import net.minecraft.ChatFormatting;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKeyTypes;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class SteamParallelMultiblockMachine extends WorkableMultiblockMachine implements IMultiblockFancyUIMachine, IDummyEnergyMachine {

    // if in millibuckets, this is 2.0, Meaning 2mb of steam -> 1 EU
    public static final double CONVERSION_RATE = 2.0;

    protected final int maxParallels;

    @Getter
    protected IEnergyContainer energyContainer = IEnergyContainer.DEFAULT;

    public SteamParallelMultiblockMachine(MetaMachineBlockEntity holder, Object... args) {
        super(holder);
        if (args.length > 0 && args[0] instanceof Integer i) {
            this.maxParallels = i;
        } else {
            this.maxParallels = 8;
        }
    }

    @Override
    public int getRecipeTier() {
        return 1;
    }

    @Override
    public void onStructureInvalid() {
        super.onStructureInvalid();
        energyContainer = IEnergyContainer.DEFAULT;
    }

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        energyContainer = IEnergyContainer.DEFAULT;
        addSteamEnergy();
        if (energyContainer == IEnergyContainer.DEFAULT) {
            // No steam hatch found
            onStructureInvalid();
        }
    }

    protected void addSteamEnergy() {
        for (var part : getWorkableParts()) {
            if (!PartAbility.STEAM.isApplicable(part.self().getDefinition().get())) continue;
            var handlers = part.getRecipeHandlers();
            for (var hl : handlers) {
                if (!hl.isValid(IO.IN)) continue;
                for (var fluidHandler : hl.fluidHandlers) {
                    if (!(fluidHandler instanceof NotifiableInventory<?> inventory) || inventory.keyType() != AEKeyTypes.FLUIDS) continue;
                    @SuppressWarnings("unchecked")
                    var steamTank = (NotifiableInventory<AEFluidKey>) inventory;
                    energyContainer = new SteamEnergyContainer(getConversionRate(), steamTank);
                    return;
                }
            }
        }
    }

    public double getConversionRate() {
        return CONVERSION_RATE;
    }

    /**
     * Recipe Modifier for <b>Steam Multiblock Machines</b> - can be used as a valid {@link RecipeModifier}
     * <p>
     * Recipe is rejected if tier is greater than LV
     * </p>
     * <p>
     * Recipe is parallelized up to the Multiblock's parallel limit.
     * Then, duration is multiplied by {@code 1.5×} and EUt is multiplied by {@code (8/9) × parallels}, up to a cap of
     * 32 EUt
     * </p>
     *
     * @param machine a {@link SteamParallelMultiblockMachine}
     * @param recipe  recipe
     */
    @Nullable
    public static GTRecipe recipeModifier(IRecipeHandlerHolder machine, RecipeHandlerUnit unit, GTRecipe recipe) {
        if (!(machine instanceof SteamParallelMultiblockMachine steamMachine)) {
            return null;
        }
        // Duration = 1.5x base duration
        // EUt (not steam) = (4/3) * (2/3) * parallels * base EUt, up to a max of 32 EUt
        long eut = recipe.getInputEUt();
        var parallelAmount = ParallelLogic.getMaxParallelAmount(machine, unit, recipe, steamMachine.maxParallels);
        if (parallelAmount == 0) return null;
        double eutMultiplier = (eut * 0.8888 * parallelAmount <= 32) ? (0.8888 * parallelAmount) : (32.0 / eut);
        recipe.durationMultiplier(1.5);
        recipe.euMultiplier(eutMultiplier);
        recipe.modifier(parallelAmount, false);
        return recipe;
    }

    @Override
    public void addDisplayText(List<Component> textList) {
        IMultiblockFancyUIMachine.super.addDisplayText(textList);
        if (isFormed()) {
            if (energyContainer instanceof SteamEnergyContainer container) {
                textList.add(Component.translatable("gtceu.multiblock.steam.steam_stored", container.steamTank.storage.amountAt(0), container.steamTank.storage.slotLimit(0)));
            }
            MultiblockDisplayText.builder(textList, true, false).addReasonLines(recipeLogic);
            if (recipeLogic.isWaiting() && IdleReasonInfo.reasonOf(recipeLogic) == null) {
                textList.add(Component.translatable("gtceu.multiblock.steam.low_steam").setStyle(Style.EMPTY.withColor(ChatFormatting.RED)));
            }
            if (isWorkingEnabled() && isActive()) {
                if (maxParallels > 1) textList.add(Component.translatable("gtceu.multiblock.parallel", maxParallels));
                int currentProgress = (int) (recipeLogic.getProgressPercent() * 100);
                double maxInSec = (float) recipeLogic.getDuration() / 20.0F;
                double currentInSec = (float) recipeLogic.getProgress() / 20.0F;
                textList.add(Component.translatable("gtceu.multiblock.progress", String.format("%.2f", (float) currentInSec), String.format("%.2f", (float) maxInSec), currentProgress));
            }
        }
    }

    @Override
    public void onWaiting() {
        super.onWaiting();
        var recipe = recipeLogic.getLastRecipe();
        if (recipe != null && recipe.eut > 0 && !useEnergy(recipe.eut, true)) setIdleReason(Component.translatable("gtceu.issue.steam_short"));
    }

    @Override
    public IGuiTexture getScreenTexture() {
        return GuiTextures.DISPLAY_STEAM.get(ConfigHolder.INSTANCE.machines.steelSteamMultiblocks);
    }

    public static final String LANG_STEAM = "gtceu.gui.multiblock.steam";
    public static final String LANG_STEAM_USAGE = "gtceu.gui.multiblock.steam_usage";

    protected MachineEra getEra() {
        return MachineEra.steam(ConfigHolder.INSTANCE.machines.steelSteamMultiblocks);
    }

    @Override
    public ResourceLocation getWindowSkin() {
        return getEra().getSkin();
    }

    @Override
    public UIElement createUIWidget() {
        var page = MultiblockPage.of(this).setScreen(getEra().getScreen());
        addPageContent(page);
        return page.build();
    }

    protected void addPageContent(MultiblockPage page) {
        page.addLine(MultiblockPage.PARALLEL, MultiblockPage.fractionText(() -> {
            var recipe = recipeLogic.isIdle() ? null : recipeLogic.getLastRecipe();
            return recipe == null ? 0 : recipe.parallels;
        }, () -> maxParallels, ""));
        page.addLine(LANG_STEAM_USAGE, MultiblockPage.cached(this::steamUsage,
                usage -> Component.literal(FormattingUtil.formatNumbers(usage * 20) + " mB/s")));
        page.addBar(LANG_STEAM, UITheme::barSteam, this::steamStored).percent()
                .bindDetail(MultiblockPage.fractionText(this::steamAmount, this::steamCapacity, "mB"));
        addScreenReadouts(page);
        addControls(page.getControls());
    }

    private ProgressBar.Progress steamStored() {
        return new ProgressBar.Progress(steamAmount(), steamCapacity(), 0);
    }

    private int steamAmount() {
        return energyContainer instanceof SteamEnergyContainer container ? Keys.saturatedInt(container.steamTank.storage.amountAt(0)) : 0;
    }

    private int steamCapacity() {
        return energyContainer instanceof SteamEnergyContainer container ? Keys.saturatedInt(container.steamTank.storage.slotLimit(0)) : 0;
    }

    private long steamUsage() {
        var recipe = recipeLogic.isWorking() ? recipeLogic.getLastRecipe() : null;
        return recipe == null ? 0 : (long) Math.ceil(recipe.getInputEUt() * getConversionRate());
    }

    @Override
    public void attachConfigurators(ConfiguratorPanel configuratorPanel) {
        attachPowerConfigurator(configuratorPanel);
        attachRecipeConfigurators(configuratorPanel);
        attachExtraConfigurators(configuratorPanel);
        attachCoverConfigurators(configuratorPanel);
    }

    protected void attachExtraConfigurators(ConfiguratorPanel configuratorPanel) {}

    @Override
    public void attachTooltips(TooltipsPanel tooltipsPanel) {
        attachTraitTooltips(tooltipsPanel);
        attachPartTooltips(tooltipsPanel);
    }
}
