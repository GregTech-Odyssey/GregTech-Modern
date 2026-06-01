package com.gregtechceu.gtceu.common.machine.multiblock.electric;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.block.IFusionCasingType;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableEnergyContainer;
import com.gregtechceu.gtceu.api.misc.EnergyContainerList;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.handler.IRecipeHandlerHolder;
import com.gregtechceu.gtceu.api.recipe.handler.RecipeHandlerUnit;
import com.gregtechceu.gtceu.api.recipe.modifier.RecipeModifier;
import com.gregtechceu.gtceu.common.block.FusionCasingBlock;
import com.gregtechceu.gtceu.common.data.GTRecipeDataKeys;
import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;

import com.gto.datasynclib.annotations.SaveToDisk;
import com.gto.datasynclib.annotations.SyncToClient;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2IntAVLTreeMap;
import it.unimi.dsi.fastutil.longs.Long2IntSortedMap;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.annotation.ParametersAreNonnullByDefault;

import static com.gregtechceu.gtceu.api.GTValues.*;
import static com.gregtechceu.gtceu.common.data.GTBlocks.*;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class FusionReactorMachine extends WorkableElectricMultiblockMachine {

    // Max EU -> Tier map, used to find minimum tier needed for X EU to start
    private static final Long2IntSortedMap FUSION_ENERGY = new Long2IntAVLTreeMap();
    // Tier -> Suffix map, i.e. LuV -> MKI
    public static final Int2ObjectMap<String> FUSION_NAMES = new Int2ObjectArrayMap<>(4);
    // Minimum registered fusion reactor tier
    public static int MINIMUM_TIER = MAX;
    @Getter
    private final int tier;
    @Nullable
    protected EnergyContainerList inputEnergyContainers;
    @SaveToDisk
    protected long heat = 0;
    @SaveToDisk
    protected final NotifiableEnergyContainer energyContainer;
    @Getter
    @SyncToClient
    private int color = -1;
    @Nullable
    protected TickableSubscription preHeatSubs;

    public FusionReactorMachine(MetaMachineBlockEntity holder, int tier) {
        super(holder);
        this.tier = tier;
        this.energyContainer = createEnergyContainer();
    }

    public NotifiableEnergyContainer createEnergyContainer() {
        // create an internal energy container for temp storage. its capacity is decided when the structure formed.
        // it doesn't provide any capability of all sides, but null for the goggles mod to check it storages.
        var container = new NotifiableEnergyContainer(this, 0, 0, 0, 0, 0);
        container.setCapabilityValidator(Objects::isNull);
        return container;
    }

    @Override
    public boolean hasBatchConfig() {
        return false;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (!isRemote()) {
            updatePreHeatSubscription();
        }
    }

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        // capture all energy containers
        List<IEnergyContainer> energyContainers = new ArrayList<>();
        for (var part : getWorkableParts()) {
            for (var handlerList : part.getRecipeHandlers()) {
                var containers = handlerList.getCapabilities(IEnergyContainer.class);
                if (!containers.isEmpty()) {
                    energyContainers.addAll(containers);
                    traitSubscriptions.add(handlerList.subscribe(this::updatePreHeatSubscription, IEnergyContainer.class));
                }
            }
        }
        this.inputEnergyContainers = new EnergyContainerList(energyContainers);
        energyContainer.resetBasicInfo(calculateEnergyStorageFactor(getTier(), energyContainers.size()), 0, 0, 0, 0);
        updatePreHeatSubscription();
    }

    @Override
    public void onStructureInvalid() {
        super.onStructureInvalid();
        this.inputEnergyContainers = null;
        heat = 0;
        energyContainer.resetBasicInfo(0, 0, 0, 0, 0);
        energyContainer.setEnergyStored(0);
        updatePreHeatSubscription();
    }

    //////////////////////////////////////
    // ***** Recipe Logic ******//
    //////////////////////////////////////
    protected void updatePreHeatSubscription() {
        // do preheat logic for heat cool down and charge internal energy container
        if (heat > 0 || (inputEnergyContainers != null && inputEnergyContainers.getEnergyStored() > 0 && energyContainer.getEnergyStored() < energyContainer.getEnergyCapacity())) {
            preHeatSubs = subscribeServerTick(preHeatSubs, this::updateHeat);
        } else if (preHeatSubs != null) {
            preHeatSubs.unsubscribe();
            preHeatSubs = null;
        }
    }

    @Nullable
    public static GTRecipe recipeModifier(IRecipeHandlerHolder machine, RecipeHandlerUnit unit, GTRecipe recipe) {
        if (!(machine instanceof FusionReactorMachine fusionReactorMachine)) {
            return null;
        }
        var eu = recipe.data.getLong(GTRecipeDataKeys.EU_TO_START);
        if (eu > fusionReactorMachine.energyContainer.getEnergyCapacity()) return null;
        long heatDiff = eu - fusionReactorMachine.heat;
        if (heatDiff > 0) {
            if (fusionReactorMachine.energyContainer.getEnergyStored() < heatDiff) return null;
            fusionReactorMachine.energyContainer.removeEnergy(heatDiff);
            fusionReactorMachine.heat += heatDiff;
        }
        return RecipeModifier.perfectOverclocking(machine, unit, recipe);
    }

    @Override
    public void onWorking() {
        super.onWorking();
        GTRecipe recipe = recipeLogic.getLastRecipe();
        assert recipe != null;
        if (recipe.data.contains(GTRecipeDataKeys.EU_TO_START)) {
            long heatDiff = recipe.data.getLong(GTRecipeDataKeys.EU_TO_START) - this.heat;
            // if the remaining energy needed is more than stored, do not run
            if (heatDiff > 0) {
                recipeLogic.setWaiting(Component.translatable("gtceu.recipe_logic.insufficient_fuel"));
                // if the remaining energy needed is more than stored, do not run
                if (this.energyContainer.getEnergyStored() < heatDiff) {
                    return;
                }
                // remove the energy needed
                this.energyContainer.removeEnergy(heatDiff);
                // increase the stored heat
                this.heat += heatDiff;
                this.updatePreHeatSubscription();
            }
        }
        if (color == -1) {
            if (!recipe.fluidOutputs.isEmpty()) {
                var fluid = recipe.fluidOutputs.getFirst().inner.getFluid();
                int newColor = -16777216 | GTUtil.getFluidColor(fluid);
                if (color != newColor) {
                    color = newColor;
                }
            }
        }
    }

    public void updateHeat() {
        // Drain heat when the reactor is not active, is paused via soft mallet, or does not have enough energy and has
        // fully wiped recipe progress
        // Don't drain heat when there is not enough energy and there is still some recipe progress, as that makes it
        // doubly hard to complete the recipe
        // (Will have to recover heat and recipe progress)
        if ((getRecipeLogic().isIdle() || !isWorkingEnabled() || (getRecipeLogic().isWaiting() && getRecipeLogic().getProgress() == 0)) && heat > 0) {
            heat = heat <= 10000 ? 0 : (heat - 10000);
        }
        // charge the internal energy storage
        var leftStorage = energyContainer.getEnergyCapacity() - energyContainer.getEnergyStored();
        if (inputEnergyContainers != null && leftStorage > 0) {
            energyContainer.addEnergy(inputEnergyContainers.removeEnergy(leftStorage));
        }
        updatePreHeatSubscription();
    }

    @Override
    public void onWaiting() {
        super.onWaiting();
        color = -1;
    }

    @Override
    public void afterWorking() {
        super.afterWorking();
        color = -1;
    }

    @Override
    public long getMaxVoltage() {
        return Math.min(GTValues.V[tier], super.getMaxVoltage());
    }

    @Override
    public long getOverclockVoltage() {
        return super.getMaxVoltage();
    }

    //////////////////////////////////////
    // ******** GUI *********//
    //////////////////////////////////////
    @Override
    public void addDisplayText(List<Component> textList) {
        super.addDisplayText(textList);
        if (isFormed()) {
            textList.add(Component.translatable("gtceu.multiblock.fusion_reactor.energy", this.energyContainer.getEnergyStored(), this.energyContainer.getEnergyCapacity()));
            textList.add(Component.translatable("gtceu.multiblock.fusion_reactor.heat", heat));
        }
    }

    //////////////////////////////////////
    // ******** MISC *********//
    //////////////////////////////////////
    public static void registerFusionTier(int tier, String name) {
        long maxEU = calculateEnergyStorageFactor(tier, 16);
        FUSION_ENERGY.put(maxEU, tier);
        FUSION_NAMES.put(tier, name);
        MINIMUM_TIER = Math.min(tier, MINIMUM_TIER);
    }

    public static int findCeilingTier(long euToStart) {
        long key;
        // tail = submap where all keys are >= EU to start
        // if tail is empty, then EU is greater than all the EU values, so we choose the last key
        // otherwise we want the first key in the tail map
        var tail = FUSION_ENERGY.tailMap(euToStart);
        if (tail.isEmpty()) key = FUSION_ENERGY.lastLongKey();
        else key = tail.firstLongKey();
        return FUSION_ENERGY.get(key);
    }

    public static long calculateEnergyStorageFactor(int tier, int energyInputAmount) {
        return energyInputAmount * (long) Math.pow(2, tier - LuV) * 10000000L;
    }

    public static Block getCasingState(int tier) {
        return switch (tier) {
            case LuV -> FUSION_CASING.get();
            case ZPM -> FUSION_CASING_MK2.get();
            default -> FUSION_CASING_MK3.get();
        };
    }

    public static Block getCoilState(int tier) {
        if (tier == GTValues.LuV) return SUPERCONDUCTING_COIL.get();
        return FUSION_COIL.get();
    }

    public static IFusionCasingType getCasingType(int tier) {
        return switch (tier) {
            case ZPM -> FusionCasingBlock.CasingType.FUSION_CASING_MK2;
            case UV -> FusionCasingBlock.CasingType.FUSION_CASING_MK3;
            default -> FusionCasingBlock.CasingType.FUSION_CASING;
        };
    }
}
