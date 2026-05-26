package com.gregtechceu.gtceu.common.machine.multiblock.steam;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.IExplosionMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IDisplayUIMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.handler.IRecipeHandlerHolder;
import com.gregtechceu.gtceu.api.recipe.handler.RecipeHandlerUnit;
import com.gregtechceu.gtceu.api.recipe.modifier.ParallelLogic;
import com.gregtechceu.gtceu.api.recipe.modifier.RecipeModifier;
import com.gregtechceu.gtceu.common.data.GTMaterials;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.util.ClickData;
import com.lowdragmc.lowdraglib.gui.widget.ComponentPanelWidget;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;

import net.minecraft.ChatFormatting;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class LargeBoilerMachine extends WorkableMultiblockMachine implements IExplosionMachine, IDisplayUIMachine {

    public static final int TICKS_PER_STEAM_GENERATION = 5;

    private static final Fluid STEAM = GTMaterials.Steam.getFluid();

    @Getter
    public final int maxTemperature;
    @Getter
    public final int heatSpeed;
    @Getter
    @Persisted
    private int currentTemperature;
    @Getter
    @Persisted
    private int throttle;
    @Nullable
    protected TickableSubscription temperatureSubs;
    private int steamGenerated;

    private boolean hasNoWater;

    public LargeBoilerMachine(MetaMachineBlockEntity holder, int maxTemperature, int heatSpeed, Object... args) {
        super(holder, args);
        this.maxTemperature = maxTemperature;
        this.heatSpeed = heatSpeed;
        this.throttle = 100;
    }

    //////////////////////////////////////
    // ****** Recipe Logic ******//
    //////////////////////////////////////
    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        if (getLevel() instanceof ServerLevel serverLevel) {
            serverLevel.getServer().tell(new TickTask(0, this::updateSteamSubscription));
        }
    }

    @Override
    public void onStructureInvalid() {
        super.onStructureInvalid();
        if (getLevel() instanceof ServerLevel serverLevel) {
            serverLevel.getServer().tell(new TickTask(0, this::updateSteamSubscription));
        }
    }

    @Override
    public void onUnload() {
        if (temperatureSubs != null) {
            temperatureSubs.unsubscribe();
            temperatureSubs = null;
        }
        super.onUnload();
    }

    protected void updateSteamSubscription() {
        if (currentTemperature > 0) {
            temperatureSubs = subscribeServerTick(temperatureSubs, this::updateCurrentTemperature);
        } else if (temperatureSubs != null) {
            temperatureSubs.unsubscribe();
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
        if (currentTemperature > 100 && isFormed() && getOffsetTimer() % 5 == 0) {
            int water = currentTemperature * throttle * 5 / 16000;
            if (water > 0) {
                if (inputFluid(Fluids.WATER, water)) {
                    steamGenerated = currentTemperature * throttle * 5 / 100;
                    if (steamGenerated > 0) {
                        outputFluid(STEAM, steamGenerated);
                    }
                    if (hasNoWater) {
                        doExplosion(2.0F);
                    }
                } else {
                    hasNoWater = true;
                }
            }
        }
    }

    protected int getCoolDownRate() {
        return 1;
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
                recipe = ParallelLogic.accurateParallel(machine, unit, recipe, (long) (1 / duration));
                if (recipe == null) return null;
            }
            if (largeBoilerMachine.throttle < 100) {
                duration = duration * 100 / largeBoilerMachine.throttle;
            }
            recipe.duration = (int) duration;
            return recipe;
        }
        return null;
    }

    public void addDisplayText(List<Component> textList) {
        IDisplayUIMachine.super.addDisplayText(textList);
        if (isFormed()) {
            textList.add(Component.translatable("gtceu.multiblock.large_boiler.temperature", currentTemperature + 274, maxTemperature + 274));
            textList.add(Component.translatable("gtceu.multiblock.large_boiler.steam_output", steamGenerated / TICKS_PER_STEAM_GENERATION));
            var throttleText = Component.translatable("gtceu.multiblock.large_boiler.throttle", ChatFormatting.AQUA.toString() + getThrottle() + "%").withStyle(Style.EMPTY.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("gtceu.multiblock.large_boiler.throttle.tooltip"))));
            textList.add(throttleText);
            var buttonText = Component.translatable("gtceu.multiblock.large_boiler.throttle_modify");
            buttonText.append(" ");
            buttonText.append(ComponentPanelWidget.withButton(Component.literal("[-]"), "sub"));
            buttonText.append(" ");
            buttonText.append(ComponentPanelWidget.withButton(Component.literal("[+]"), "add"));
            textList.add(buttonText);
        }
    }

    public void handleDisplayClick(String componentData, ClickData clickData) {
        if (!clickData.isRemote) {
            int result = componentData.equals("add") ? 5 : -5;
            this.throttle = Mth.clamp(throttle + result, 25, 100);
        }
    }

    @Override
    public IGuiTexture getScreenTexture() {
        return GuiTextures.DISPLAY_STEAM.get(maxTemperature > 800);
    }
}
