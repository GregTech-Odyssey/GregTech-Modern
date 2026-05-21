package com.gregtechceu.gtceu.api.machine.steam;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.capability.IWailaDisplayProvider;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.widget.TankWidget;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.IDataInfoProvider;
import com.gregtechceu.gtceu.api.machine.feature.IExplosionMachine;
import com.gregtechceu.gtceu.api.machine.feature.IInteractedMachine;
import com.gregtechceu.gtceu.api.machine.feature.IUIMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableFluidTank;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.recipe.handler.IRecipeHandlerHolder;
import com.gregtechceu.gtceu.api.recipe.handler.RecipeHandlerUnit;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.item.PortableScannerBehavior;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.utils.FormattingUtil;

import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.texture.ProgressTexture;
import com.lowdragmc.lowdraglib.gui.widget.ImageWidget;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.ProgressWidget;
import com.lowdragmc.lowdraglib.syncdata.ISubscription;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;

import net.minecraft.ChatFormatting;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;

import com.gto.datasynclib.annotations.SyncToClient;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public abstract class SteamBoilerMachine extends SteamWorkableMachine implements IUIMachine, IExplosionMachine, IDataInfoProvider, IWailaDisplayProvider, IInteractedMachine {

    @Persisted
    public final NotifiableFluidTank waterTank;
    @Getter
    @Persisted
    @SyncToClient
    private int currentTemperature;
    @Getter
    @Persisted
    private int timeBeforeCoolingDown;
    @Getter
    private boolean hasNoWater;
    @Nullable
    protected TickableSubscription temperatureSubs;
    @Nullable
    protected TickableSubscription autoOutputSubs;
    @Nullable
    protected ISubscription steamTankSubs;

    public int fillAmount;

    public SteamBoilerMachine(MetaMachineBlockEntity holder, boolean isHighPressure, Object... args) {
        super(holder, isHighPressure, args);
        this.waterTank = createWaterTank(args);
        this.waterTank.setFilter(fluid -> fluid.getFluid() == Fluids.WATER);
    }

    @Override
    protected NotifiableFluidTank createSteamTank(Object... args) {
        return new NotifiableFluidTank(this, 1, 16 * FluidType.BUCKET_VOLUME, IO.NONE, IO.OUT);
    }

    protected NotifiableFluidTank createWaterTank(@SuppressWarnings("unused") Object... args) {
        return new NotifiableFluidTank(this, 1, 16 * FluidType.BUCKET_VOLUME, IO.IN);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (getLevel() instanceof ServerLevel serverLevel) {
            serverLevel.getServer().tell(new TickTask(0, this::updateAutoOutputSubscription));
            updateSteamSubscription();
            steamTankSubs = steamTank.addChangedListener(this::updateAutoOutputSubscription);
        }
    }

    @Override
    public void onUnload() {
        super.onUnload();
        if (steamTankSubs != null) {
            steamTankSubs.unsubscribe();
            steamTankSubs = null;
        }
    }

    @Override
    public void setOutputFacing(Direction outputFacing) {
        // no op - boilers do not have output facings
    }

    //////////////////////////////////////
    // ******* Auto Output *******//
    //////////////////////////////////////
    @Override
    public void onNeighborChanged(Block block, BlockPos fromPos, boolean isMoving) {
        super.onNeighborChanged(block, fromPos, isMoving);
        updateAutoOutputSubscription();
    }

    protected void updateAutoOutputSubscription() {
        if (Direction.stream().filter(direction -> direction != getFrontFacing() && direction != Direction.DOWN).anyMatch(direction -> blockEntityDirectionCache.hasAdjacentFluidHandler(getLevel(), getPos(), direction))) {
            autoOutputSubs = subscribeServerTick(autoOutputSubs, this::autoOutput, 20);
        } else if (autoOutputSubs != null) {
            autoOutputSubs.unsubscribe();
            autoOutputSubs = null;
        }
    }

    protected void autoOutput() {
        steamTank.exportToNearby(Direction.stream().filter(direction -> direction != getFrontFacing() && direction != Direction.DOWN).filter(direction -> blockEntityDirectionCache.hasAdjacentFluidHandler(getLevel(), getPos(), direction)).toArray(Direction[]::new));
        updateAutoOutputSubscription();
    }

    //////////////////////////////////////
    // ****** Recipe Logic ******//
    //////////////////////////////////////
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
            if (getOffsetTimer() % 12 == 0) {
                if (currentTemperature < getMaxTemperature()) if (isHighPressure) {
                    currentTemperature++;
                } else if (getOffsetTimer() % 24 == 0) {
                    currentTemperature++;
                }
            }
        } else if (timeBeforeCoolingDown == 0) {
            if (currentTemperature > 0) {
                currentTemperature -= getCoolDownRate();
                timeBeforeCoolingDown = getCooldownInterval();
            }
        } else--timeBeforeCoolingDown;
        if (getOffsetTimer() % 10 == 0) {
            fillAmount = 0;
            if (currentTemperature >= 100) {
                fillAmount = (int) (getBaseSteamOutput() * ((float) currentTemperature / getMaxTemperature()) / 2);
                boolean hasDrainedWater = !waterTank.drainInternal(1, FluidAction.EXECUTE).isEmpty();
                var filledSteam = 0L;
                if (hasDrainedWater) {
                    filledSteam = steamTank.fillInternal(GTMaterials.Steam.getFluid(fillAmount), FluidAction.EXECUTE);
                }
                if (this.hasNoWater && hasDrainedWater) {
                    doExplosion(2.0F);
                } else this.hasNoWater = !hasDrainedWater;
                if (filledSteam == 0 && hasDrainedWater && getLevel() instanceof ServerLevel serverLevel) {
                    final float x = getPos().getX() + 0.5F;
                    final float y = getPos().getY() + 0.5F;
                    final float z = getPos().getZ() + 0.5F;
                    serverLevel.sendParticles(ParticleTypes.CLOUD, x + getFrontFacing().getStepX() * 0.6, y + getFrontFacing().getStepY() * 0.6, z + getFrontFacing().getStepZ() * 0.6, 7 + GTValues.RNG.nextInt(3), getFrontFacing().getStepX() / 2.0, getFrontFacing().getStepY() / 2.0, getFrontFacing().getStepZ() / 2.0, 0.1);
                    if (ConfigHolder.INSTANCE.machines.machineSounds) {
                        getLevel().playSound(null, x, y, z, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 1.0F, 1.0F);
                    }
                    // bypass capability check for special case behavior
                    steamTank.drainInternal(FluidType.BUCKET_VOLUME * 4, FluidAction.EXECUTE);
                }
            } else this.hasNoWater = false;
        }
        updateSteamSubscription();
    }

    protected int getCooldownInterval() {
        return isHighPressure ? 40 : 45;
    }

    protected int getCoolDownRate() {
        return 1;
    }

    public int getMaxTemperature() {
        return isHighPressure ? 1000 : 500;
    }

    private double getTemperaturePercent() {
        return currentTemperature / (getMaxTemperature() * 1.0);
    }

    protected abstract long getBaseSteamOutput();

    @Override
    public boolean onWorking() {
        boolean value = super.onWorking();
        if (currentTemperature < getMaxTemperature()) {
            currentTemperature = Math.max(1, currentTemperature);
            updateSteamSubscription();
        }
        return value;
    }

    @Override
    public void afterWorking() {
        super.afterWorking();
        this.timeBeforeCoolingDown = getCooldownInterval();
    }

    @Nullable
    public static GTRecipe recipeModifier(IRecipeHandlerHolder holder, RecipeHandlerUnit unit, GTRecipe recipe) {
        if (!(holder instanceof SteamBoilerMachine boilerMachine)) {
            return null;
        }
        if (boilerMachine.isHighPressure) recipe.durationMultiplier(0.5);
        return recipe;
    }

    //////////////////////////////////////
    // ******* Interaction *******//
    //////////////////////////////////////
    @Override
    protected InteractionResult onSoftMalletClick(Player playerIn, InteractionHand hand, Direction gridSide, BlockHitResult hitResult) {
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResult onUse(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand,
                                   BlockHitResult hit) {
        if (!isRemote()) {
            if (FluidUtil.interactWithFluidHandler(player, hand, waterTank)) {
                return InteractionResult.SUCCESS;
            }
        }
        return IInteractedMachine.super.onUse(state, world, pos, player, hand, hit);
    }

    //////////////////////////////////////
    // ********** GUI ***********//
    //////////////////////////////////////
    @Override
    public ModularUI createUI(Player entityPlayer) {
        return new ModularUI(176, 166, this, entityPlayer).background(GuiTextures.BACKGROUND_STEAM.get(isHighPressure)).widget(new LabelWidget(6, 6, getBlockState().getBlock().getDescriptionId())).widget(new ProgressWidget(this::getTemperaturePercent, 96, 26, 10, 54).setProgressTexture(GuiTextures.PROGRESS_BAR_BOILER_EMPTY.get(isHighPressure), GuiTextures.PROGRESS_BAR_BOILER_HEAT).setFillDirection(ProgressTexture.FillDirection.DOWN_TO_UP).setDynamicHoverTips(pct -> I18n.get("gtceu.multiblock.large_boiler.temperature", currentTemperature + 274, getMaxTemperature() + 274))).widget(new TankWidget(waterTank.getStorages()[0], 83, 26, 10, 54, false, true).setShowAmount(false).setFillDirection(ProgressTexture.FillDirection.DOWN_TO_UP).setBackground(GuiTextures.PROGRESS_BAR_BOILER_EMPTY.get(isHighPressure))).widget(new TankWidget(steamTank.getStorages()[0], 70, 26, 10, 54, true, false).setShowAmount(false).setFillDirection(ProgressTexture.FillDirection.DOWN_TO_UP).setBackground(GuiTextures.PROGRESS_BAR_BOILER_EMPTY.get(isHighPressure))).widget(new ImageWidget(43, 44, 18, 18, GuiTextures.CANISTER_OVERLAY_STEAM.get(isHighPressure))).widget(UITemplate.bindPlayerInventory(entityPlayer.getInventory(), GuiTextures.SLOT_STEAM.get(isHighPressure), 7, 84, true));
    }

    //////////////////////////////////////
    // ********* Client *********//
    //////////////////////////////////////
    @Override
    public void animateTick(RandomSource random) {
        if (isActive()) {
            final BlockPos pos = getPos();
            float x = pos.getX() + 0.5F;
            float z = pos.getZ() + 0.5F;
            final var facing = getFrontFacing();
            final float horizontalOffset = random.nextFloat() * 0.6F - 0.3F;
            final float y = pos.getY() + random.nextFloat() * 0.375F;
            if (facing.getAxis() == Direction.Axis.X) {
                if (facing.getAxisDirection() == Direction.AxisDirection.POSITIVE) x += 0.52F;
                else x -= 0.52F;
                z += horizontalOffset;
            } else if (facing.getAxis() == Direction.Axis.Z) {
                if (facing.getAxisDirection() == Direction.AxisDirection.POSITIVE) z += 0.52F;
                else z -= 0.52F;
                x += horizontalOffset;
            }
            randomDisplayTick(random, x, y, z);
        }
    }

    protected void randomDisplayTick(RandomSource random, float x, float y, float z) {
        getLevel().addParticle(isHighPressure ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE, x, y, z, 0, 0, 0);
        getLevel().addParticle(ParticleTypes.FLAME, x, y, z, 0, 0, 0);
    }

    @Override
    public List<Component> getDataInfo(PortableScannerBehavior.DisplayMode mode) {
        if (mode == PortableScannerBehavior.DisplayMode.SHOW_ALL || mode == PortableScannerBehavior.DisplayMode.SHOW_MACHINE_INFO) {
            return Collections.singletonList(Component.translatable("gtceu.machine.steam_boiler.heat_amount", FormattingUtil.formatNumbers((int) (getTemperaturePercent() * 100))));
        }
        return new ArrayList<>();
    }

    @Override
    public void appendWailaTooltip(CompoundTag data, ITooltip iTooltip, BlockAccessor blockAccessor, IPluginConfig iPluginConfig) {
        var producing = data.getBoolean("producingSteam");
        if (data.getBoolean("heatingUp")) {
            iTooltip.add(Component.translatable("gtceu.machine.boiler.info.heating.up",
                    producing ? Component.translatable("gtceu.machine.boiler.info.producing.steam") : ""));
            var fillAmount = data.getInt("fillAmount");
            if (fillAmount > 0) {
                iTooltip.add(Component.translatable("gtceu.multiblock.large_boiler.steam_output", fillAmount).withStyle(ChatFormatting.GREEN));
            }
        } else if (data.getBoolean("coolingDown")) {
            iTooltip.add(Component.translatable("gtceu.machine.boiler.info.cooling.down",
                    producing ? Component.translatable("gtceu.machine.boiler.info.producing.steam") : ""));
        }
    }

    @Override
    public void appendWailaData(CompoundTag data, BlockAccessor blockAccessor) {
        data.putInt("fillAmount", fillAmount / 10);
        data.putBoolean("heatingUp", getRecipeLogic().isWorking());
        data.putBoolean("coolingDown", currentTemperature > 0);
        data.putBoolean("producingSteam", !hasNoWater && currentTemperature >= 100);
    }
}
