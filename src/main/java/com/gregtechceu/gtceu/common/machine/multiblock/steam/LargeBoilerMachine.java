package com.gregtechceu.gtceu.common.machine.multiblock.steam;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.fancy.TooltipsPanel;
import com.gregtechceu.gtceu.api.machine.feature.IExplosionMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiblockFancyUIMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import com.gregtechceu.gtceu.api.misc.TickTimeMonitor;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.handler.IRecipeHandlerHolder;
import com.gregtechceu.gtceu.api.recipe.handler.RecipeHandlerUnit;
import com.gregtechceu.gtceu.api.recipe.modifier.ParallelLogic;
import com.gregtechceu.gtceu.api.recipe.modifier.RecipeModifier;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.data.GTTickTimeMonitors;
import com.gregtechceu.gtceu.common.data.machines.GTMultiMachines;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.elements.ProgressBar;
import com.gregtechceu.gtceu.uipro.styletemplate.MachineEra;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;
import com.gregtechceu.gtceu.uiwidgets.multiblock.ControlPanel;
import com.gregtechceu.gtceu.uiwidgets.multiblock.MultiblockPage;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;

import net.minecraft.ChatFormatting;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidType;

import appeng.api.stacks.AEFluidKey;
import com.gto.datasynclib.annotations.SaveToDisk;
import com.suried.gtoutils.schedule.Schedulers;
import com.suried.gtoutils.schedule.Task;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class LargeBoilerMachine extends WorkableMultiblockMachine implements IExplosionMachine, IMultiblockFancyUIMachine {

    public static final int TICKS_PER_STEAM_GENERATION = 5;
    public static final long STEAM_BUFFER_CAPACITY = 64L * FluidType.BUCKET_VOLUME;
    private static final Component THROTTLE_LABEL = Component.translatable("gtceu.multiblock.large_boiler.throttle.label");
    private static final Component THROTTLE_TOOLTIP = Component.translatable("gtceu.multiblock.large_boiler.throttle.tooltip");
    private static final Component TEMPERATURE_LABEL = Component.translatable("gtceu.multiblock.large_boiler.temperature.label");
    private static final Component STEAM_OUTPUT_LABEL = Component.translatable("gtceu.multiblock.large_boiler.steam_output.label");
    private static final Component STEAM_BUFFER_LABEL = Component.translatable("gtceu.multiblock.large_boiler.steam_buffer.label");

    private static final Fluid STEAM = GTMaterials.Steam.getFluid();
    private static final AEFluidKey STEAM_KEY = AEFluidKey.of(STEAM);

    @Getter
    public final int maxTemperature;
    @Getter
    public final int heatSpeed;
    @Getter
    @SaveToDisk(defaultValue = "0")
    private int currentTemperature;
    @Getter
    @SaveToDisk(defaultValue = "100")
    private int throttle;
    @SaveToDisk(defaultValue = "100")
    private int activeThrottle;
    @Getter
    @SaveToDisk(defaultValue = "0")
    private long steamBuffer;
    @Nullable
    private final TickTimeMonitor steamMonitor = holder.monitorTick(GTTickTimeMonitors.STEAM, this::updateCurrentTemperature);
    protected Task temperatureSubs;
    private int steamGenerated;

    private boolean hasNoWater;

    public LargeBoilerMachine(MetaMachineBlockEntity holder, int maxTemperature, int heatSpeed, Object... args) {
        super(holder, args);
        this.maxTemperature = maxTemperature;
        this.heatSpeed = heatSpeed;
        this.throttle = 100;
        this.activeThrottle = 100;
    }

    //////////////////////////////////////
    // ****** Recipe Logic ******//
    //////////////////////////////////////
    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        if (getLevel() instanceof ServerLevel serverLevel) {
            Schedulers.of(serverLevel).schedule(this::updateSteamSubscription);
        }
    }

    @Override
    public void onStructureInvalid() {
        super.onStructureInvalid();
        if (getLevel() instanceof ServerLevel serverLevel) {
            Schedulers.of(serverLevel).schedule(this::updateSteamSubscription);
        }
    }

    @Override
    public void onUnload() {
        if (temperatureSubs != null) {
            temperatureSubs.cancel();
            temperatureSubs = null;
        }
        super.onUnload();
    }

    protected void updateSteamSubscription() {
        if (currentTemperature > 0 || steamBuffer > 0) {
            temperatureSubs = subscribeServerTick(temperatureSubs, steamMonitor);
        } else if (temperatureSubs != null) {
            temperatureSubs.cancel();
            temperatureSubs = null;
        }
    }

    protected void updateCurrentTemperature() {
        if (recipeLogic.isWorking()) {
            if (getOffsetTimer() % 5 == 0) {
                if (currentTemperature < maxTemperature) {
                    currentTemperature = Mth.clamp(currentTemperature + heatSpeed, 0, maxTemperature);
                }
            }
        } else if (currentTemperature > 0) {
            currentTemperature -= 1;
        }
        if (isFormed() && getOffsetTimer() % 5 == 0) {
            long steam = steamBuffer;
            boolean explode = false;
            if (currentTemperature > 100) {
                int currentThrottle = recipeLogic.isWorking() ? activeThrottle : throttle;
                int water = currentTemperature * currentThrottle * 5 / 16000;
                if (water > 0) {
                    if (inputFluid(Fluids.WATER, water)) {
                        steamGenerated = currentTemperature * currentThrottle * 5 / 100;
                        if (steamGenerated > 0) {
                            steam += steamGenerated;
                        }
                        explode = hasNoWater;
                    } else {
                        hasNoWater = true;
                    }
                }
            }
            if (steam > 0) {
                long next = Math.min(STEAM_BUFFER_CAPACITY, steam - outputUpTo(STEAM_KEY, steam));
                if (next != steamBuffer) {
                    steamBuffer = next;
                    onChanged();
                }
            }
            if (explode) {
                doExplosion(2.0F);
            }
        }
    }

    protected int getCoolDownRate() {
        return 1;
    }

    @Override
    public void beforeWorking(RecipeHandlerUnit unit, GTRecipe recipe) {
        super.beforeWorking(unit, recipe);
        activeThrottle = throttle;
    }

    @Override
    public void onWorking() {
        super.onWorking();
        if (currentTemperature < getMaxTemperature()) {
            currentTemperature = Math.max(1, currentTemperature);
            updateSteamSubscription();
        }
    }

    /**
     * Recipe Modifier for <b>Large Boiler Machines</b> - can be used as a valid {@link RecipeModifier}
     * <p>
     * Duration is multiplied by {@code 100 / throttle} if throttle is less than 100
     * </p>
     * 
     * @param machine a {@link LargeBoilerMachine}
     * @param recipe  recipe
     */
    @Nullable
    public static GTRecipe recipeModifier(IRecipeHandlerHolder machine, RecipeHandlerUnit unit, GTRecipe recipe) {
        if (machine instanceof LargeBoilerMachine largeBoilerMachine) {
            double duration = recipe.duration * 1600.0D / largeBoilerMachine.maxTemperature;
            if (duration < 1) {
                long parallels = recipe.parallels;
                recipe = ParallelLogic.accurateParallel(machine, unit, recipe, (long) (1 / duration));
                if (recipe == null) return null;
                duration = duration * recipe.parallels / parallels;
            }
            if (largeBoilerMachine.throttle < 100) {
                duration = duration * 100 / largeBoilerMachine.throttle;
            }
            recipe.duration = Math.max(1, (int) duration);
            return recipe;
        }
        return null;
    }

    public void addDisplayText(List<Component> textList) {
        IMultiblockFancyUIMachine.super.addDisplayText(textList);
        if (isFormed()) {
            textList.add(Component.translatable("gtceu.multiblock.large_boiler.temperature", currentTemperature + 274, maxTemperature + 274));
            textList.add(Component.translatable("gtceu.multiblock.large_boiler.steam_output", steamGenerated / TICKS_PER_STEAM_GENERATION));
            var throttleText = Component.translatable("gtceu.multiblock.large_boiler.throttle", ChatFormatting.AQUA.toString() + getThrottle() + "%").withStyle(Style.EMPTY.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, THROTTLE_TOOLTIP)));
            textList.add(throttleText);
        }
    }

    @Override
    public void addControls(ControlPanel controls) {
        super.addControls(controls);
        controls.addInt(THROTTLE_LABEL, () -> throttle, value -> throttle = value, 25, 100, THROTTLE_TOOLTIP)
                .disabled(() -> !isFormed(), MultiblockPage.STATE_UNFORMED);
    }

    private MachineEra getEra() {
        var definition = getDefinition();
        if (definition == GTMultiMachines.LARGE_BOILER_BRONZE) return MachineEra.BRONZE;
        return definition == GTMultiMachines.LARGE_BOILER_STEEL ? MachineEra.STEEL : MachineEra.CLASSIC;
    }

    @Override
    public @Nullable ResourceLocation getWindowSkin() {
        return getEra().getSkin();
    }

    @Override
    public UIElement createUIWidget() {
        var page = MultiblockPage.of(this).setScreen(getEra().getScreen());
        page.addBar(TEMPERATURE_LABEL, UITheme::barHeat, () -> new ProgressBar.Progress(currentTemperature, maxTemperature, 0)).percent()
                .bindDetail(MultiblockPage.cached(() -> currentTemperature, temperature -> Component.literal((temperature + 274) + " / " + (maxTemperature + 274) + " K")));
        page.addNumber(STEAM_OUTPUT_LABEL, () -> steamGenerated / TICKS_PER_STEAM_GENERATION, "mB/t");
        page.addBar(STEAM_BUFFER_LABEL, UITheme::barSteam, () -> new ProgressBar.Progress(steamBuffer, STEAM_BUFFER_CAPACITY, 0)).percent()
                .bindDetail(MultiblockPage.fractionText(() -> steamBuffer, () -> STEAM_BUFFER_CAPACITY, "mB"));
        addScreenReadouts(page);
        addControls(page.getControls());
        return page.build();
    }

    @Override
    public void attachTooltips(TooltipsPanel tooltipsPanel) {
        attachTraitTooltips(tooltipsPanel);
        attachPartTooltips(tooltipsPanel);
    }

    @Override
    public IGuiTexture getScreenTexture() {
        return GuiTextures.DISPLAY_STEAM.get(maxTemperature > 800);
    }
}
