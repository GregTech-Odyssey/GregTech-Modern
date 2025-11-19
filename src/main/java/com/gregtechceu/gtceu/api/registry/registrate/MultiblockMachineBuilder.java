package com.gregtechceu.gtceu.api.registry.registrate;

import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.gui.editor.EditableMachineUI;
import com.gregtechceu.gtceu.api.item.MetaMachineItem;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.MultiblockShapeInfo;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.modifier.RecipeModifier;
import com.gregtechceu.gtceu.utils.memoization.GTMemoizer;
import com.gregtechceu.gtceu.utils.memoization.MemoizedSupplier;

import com.lowdragmc.lowdraglib.client.renderer.IRenderer;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.tterrag.registrate.Registrate;
import com.tterrag.registrate.builders.BlockBuilder;
import com.tterrag.registrate.builders.ItemBuilder;
import com.tterrag.registrate.util.nullness.NonNullUnaryOperator;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import org.apache.commons.lang3.function.TriFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.function.*;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class MultiblockMachineBuilder extends MachineBuilder<MultiblockMachineDefinition> {

    private int checkPriority;
    private boolean generator;
    private Function<MultiblockMachineDefinition, BlockPattern> pattern;
    private List<Function<MultiblockMachineDefinition, BlockPattern>> subPattern;
    private final List<Function<MultiblockMachineDefinition, List<MultiblockShapeInfo>>> shapeInfos = new ObjectArrayList<>();
    /**
     * Set this to false only if your multiblock is set up such that it could have a wall-shared controller.
     */
    private boolean allowFlip = true;
    private MufflerProductionGenerator recoveryItems;
    private TriFunction<IMultiController, IMultiPart, Direction, BlockState> partAppearance;
    private BiConsumer<IMultiController, List<Component>> additionalDisplay = (m, l) -> {};

    protected MultiblockMachineBuilder(Registrate registrate, String name, Function<MetaMachineBlockEntity, ? extends MultiblockControllerMachine> metaMachine, BiFunction<BlockBehaviour.Properties, MultiblockMachineDefinition, MetaMachineBlock> blockFactory, BiFunction<MetaMachineBlock, Item.Properties, MetaMachineItem> itemFactory, TriFunction<BlockEntityType<?>, BlockPos, BlockState, MetaMachineBlockEntity> blockEntityFactory) {
        super(registrate, name, MultiblockMachineDefinition::createDefinition, metaMachine::apply, blockFactory, itemFactory, blockEntityFactory);
        allowExtendedFacing(true);
        allowCoverOnFront(true);
    }

    public static MultiblockMachineBuilder createMulti(Registrate registrate, String name, Function<MetaMachineBlockEntity, ? extends MultiblockControllerMachine> metaMachine, BiFunction<BlockBehaviour.Properties, MultiblockMachineDefinition, MetaMachineBlock> blockFactory, BiFunction<MetaMachineBlock, Item.Properties, MetaMachineItem> itemFactory, TriFunction<BlockEntityType<?>, BlockPos, BlockState, MetaMachineBlockEntity> blockEntityFactory) {
        return new MultiblockMachineBuilder(registrate, name, metaMachine, blockFactory, itemFactory, blockEntityFactory);
    }

    public MultiblockMachineBuilder shapeInfo(Function<MultiblockMachineDefinition, MultiblockShapeInfo> shape) {
        this.shapeInfos.add(d -> List.of(shape.apply(d)));
        return this;
    }

    public MultiblockMachineBuilder shapeInfos(Function<MultiblockMachineDefinition, List<MultiblockShapeInfo>> shapes) {
        this.shapeInfos.add(shapes);
        return this;
    }

    public MultiblockMachineBuilder recoveryStaticItems(Supplier<Item> item) {
        this.recoveryItems = ofMemorized(item);
        return this;
    }

    public MultiblockMachineBuilder recoveryStacks(MufflerProductionGenerator stack) {
        this.recoveryItems = stack;
        return this;
    }

    public MultiblockMachineBuilder nonYAxisRotation() {
        return rotationState(RotationState.NON_Y_AXIS).allowExtendedFacing(false);
    }

    public MultiblockMachineBuilder allRotation() {
        return rotationState(RotationState.ALL);
    }

    public MultiblockMachineBuilder noneRotation() {
        return rotationState(RotationState.NONE).allowExtendedFacing(false).allowFlip(false);
    }

    @Override
    public MultiblockMachineBuilder definition(Function<ResourceLocation, MultiblockMachineDefinition> definition) {
        return (MultiblockMachineBuilder) super.definition(definition);
    }

    @Override
    public MultiblockMachineBuilder machine(Function<MetaMachineBlockEntity, MetaMachine> machine) {
        return (MultiblockMachineBuilder) super.machine(machine);
    }

    @Override
    public MultiblockMachineBuilder renderer(@Nullable Supplier<IRenderer> renderer) {
        return (MultiblockMachineBuilder) super.renderer(renderer);
    }

    @Override
    public MultiblockMachineBuilder shape(VoxelShape shape) {
        return (MultiblockMachineBuilder) super.shape(shape);
    }

    @Override
    public MultiblockMachineBuilder multiblockPreviewRenderer(boolean multiBlockWorldPreview, boolean multiBlockXEIPreview) {
        return (MultiblockMachineBuilder) super.multiblockPreviewRenderer(multiBlockWorldPreview, multiBlockXEIPreview);
    }

    @Override
    public MultiblockMachineBuilder rotationState(RotationState rotationState) {
        return (MultiblockMachineBuilder) super.rotationState(rotationState);
    }

    @Override
    public MultiblockMachineBuilder hasTESR(boolean hasTESR) {
        return (MultiblockMachineBuilder) super.hasTESR(hasTESR);
    }

    @Override
    public MultiblockMachineBuilder blockProp(NonNullUnaryOperator<BlockBehaviour.Properties> blockProp) {
        return (MultiblockMachineBuilder) super.blockProp(blockProp);
    }

    @Override
    public MultiblockMachineBuilder itemProp(NonNullUnaryOperator<Item.Properties> itemProp) {
        return (MultiblockMachineBuilder) super.itemProp(itemProp);
    }

    @Override
    public MultiblockMachineBuilder blockBuilder(Consumer<BlockBuilder<? extends Block, ?>> blockBuilder) {
        return (MultiblockMachineBuilder) super.blockBuilder(blockBuilder);
    }

    @Override
    public MultiblockMachineBuilder itemBuilder(Consumer<ItemBuilder<? extends MetaMachineItem, ?>> itemBuilder) {
        return (MultiblockMachineBuilder) super.itemBuilder(itemBuilder);
    }

    @Override
    public MultiblockMachineBuilder recipeTypes(GTRecipeType... recipeTypes) {
        return (MultiblockMachineBuilder) super.recipeTypes(recipeTypes);
    }

    @Override
    public MultiblockMachineBuilder recipeType(GTRecipeType recipeTypes) {
        return (MultiblockMachineBuilder) super.recipeType(recipeTypes);
    }

    @Override
    public MultiblockMachineBuilder tier(int tier) {
        return (MultiblockMachineBuilder) super.tier(tier);
    }

    public MultiblockMachineBuilder recipeOutputLimits(Reference2IntOpenHashMap<RecipeCapability<?>> map) {
        return (MultiblockMachineBuilder) super.recipeOutputLimits(map);
    }

    @Override
    public MultiblockMachineBuilder addOutputLimit(RecipeCapability<?> capability, int limit) {
        return (MultiblockMachineBuilder) super.addOutputLimit(capability, limit);
    }

    @Override
    public MultiblockMachineBuilder itemColor(BiFunction<ItemStack, Integer, Integer> itemColor) {
        return (MultiblockMachineBuilder) super.itemColor(itemColor);
    }

    @Override
    public MultiblockMachineBuilder modelRenderer(Supplier<ResourceLocation> model) {
        return (MultiblockMachineBuilder) super.modelRenderer(model);
    }

    @Override
    public MultiblockMachineBuilder defaultModelRenderer() {
        return (MultiblockMachineBuilder) super.defaultModelRenderer();
    }

    @Override
    public MultiblockMachineBuilder tieredHullRenderer(ResourceLocation model) {
        return (MultiblockMachineBuilder) super.tieredHullRenderer(model);
    }

    @Override
    public MultiblockMachineBuilder overlayTieredHullRenderer(String name) {
        return (MultiblockMachineBuilder) super.overlayTieredHullRenderer(name);
    }

    @Override
    public MultiblockMachineBuilder workableTieredHullRenderer(ResourceLocation workableModel) {
        return (MultiblockMachineBuilder) super.workableTieredHullRenderer(workableModel);
    }

    @Override
    public MultiblockMachineBuilder workableCasingRenderer(ResourceLocation baseCasing, ResourceLocation overlayModel) {
        return (MultiblockMachineBuilder) super.workableCasingRenderer(baseCasing, overlayModel);
    }

    @Override
    public MultiblockMachineBuilder workableCasingRenderer(ResourceLocation baseCasing, ResourceLocation overlayModel, boolean tint) {
        return (MultiblockMachineBuilder) super.workableCasingRenderer(baseCasing, overlayModel, tint);
    }

    @Override
    public MultiblockMachineBuilder sidedWorkableCasingRenderer(String basePath, ResourceLocation overlayModel, boolean tint) {
        return (MultiblockMachineBuilder) super.sidedWorkableCasingRenderer(basePath, overlayModel, tint);
    }

    @Override
    public MultiblockMachineBuilder sidedWorkableCasingRenderer(String basePath, ResourceLocation overlayModel) {
        return (MultiblockMachineBuilder) super.sidedWorkableCasingRenderer(basePath, overlayModel);
    }

    @Override
    public MultiblockMachineBuilder tooltipBuilder(BiConsumer<ItemStack, List<Component>> tooltipBuilder) {
        return (MultiblockMachineBuilder) super.tooltipBuilder(tooltipBuilder);
    }

    @Override
    public MultiblockMachineBuilder appearance(Supplier<BlockState> state) {
        return (MultiblockMachineBuilder) super.appearance(state);
    }

    @Override
    public MultiblockMachineBuilder appearanceBlock(Supplier<? extends Block> block) {
        return (MultiblockMachineBuilder) super.appearanceBlock(block);
    }

    @Override
    public MultiblockMachineBuilder langValue(@Nullable String langValue) {
        return (MultiblockMachineBuilder) super.langValue(langValue);
    }

    @Override
    public MultiblockMachineBuilder overlaySteamHullRenderer(String name) {
        return (MultiblockMachineBuilder) super.overlaySteamHullRenderer(name);
    }

    @Override
    public MultiblockMachineBuilder workableSteamHullRenderer(boolean isHighPressure, ResourceLocation workableModel) {
        return (MultiblockMachineBuilder) super.workableSteamHullRenderer(isHighPressure, workableModel);
    }

    @Override
    public MultiblockMachineBuilder tooltips(Component... components) {
        return (MultiblockMachineBuilder) super.tooltips(components);
    }

    @Override
    public MultiblockMachineBuilder conditionalTooltip(Component component, BooleanSupplier condition) {
        return (MultiblockMachineBuilder) super.conditionalTooltip(component, condition);
    }

    @Override
    public MultiblockMachineBuilder conditionalTooltip(Component component, boolean condition) {
        return (MultiblockMachineBuilder) super.conditionalTooltip(component, condition);
    }

    @Override
    public MultiblockMachineBuilder abilities(PartAbility... abilities) {
        return (MultiblockMachineBuilder) super.abilities(abilities);
    }

    @Override
    public MultiblockMachineBuilder paintingColor(int paintingColor) {
        return (MultiblockMachineBuilder) super.paintingColor(paintingColor);
    }

    @Override
    public MultiblockMachineBuilder recipeModifier(RecipeModifier recipeModifier) {
        return (MultiblockMachineBuilder) super.recipeModifier(recipeModifier);
    }

    @Override
    public MultiblockMachineBuilder recipeModifiers(RecipeModifier... recipeModifiers) {
        return (MultiblockMachineBuilder) super.recipeModifiers(recipeModifiers);
    }

    public MultiblockMachineBuilder noRecipeModifier() {
        return (MultiblockMachineBuilder) super.noRecipeModifier();
    }

    @Override
    public MultiblockMachineBuilder onWorking(Predicate<IRecipeLogicMachine> onWorking) {
        return (MultiblockMachineBuilder) super.onWorking(onWorking);
    }

    @Override
    public MultiblockMachineBuilder regressWhenWaiting(boolean regressWhenWaiting) {
        return (MultiblockMachineBuilder) super.regressWhenWaiting(regressWhenWaiting);
    }

    @Override
    public MultiblockMachineBuilder editableUI(@Nullable EditableMachineUI editableUI) {
        return (MultiblockMachineBuilder) super.editableUI(editableUI);
    }

    @Override
    public MultiblockMachineBuilder allowExtendedFacing(boolean allowExtendedFacing) {
        return (MultiblockMachineBuilder) super.allowExtendedFacing(allowExtendedFacing);
    }

    @Override
    public MultiblockMachineBuilder allowCoverOnFront(boolean allowCoverOnFront) {
        return (MultiblockMachineBuilder) super.allowCoverOnFront(allowCoverOnFront);
    }

    @Override
    public MultiblockMachineDefinition register() {
        var definition = super.register();
        definition.setCheckPriority(checkPriority);
        definition.setGenerator(generator);
        if (pattern == null) {
            throw new IllegalStateException("missing pattern while creating multiblock " + name);
        }
        definition.setPatternFactory(pattern);
        if (subPattern != null) {
            definition.setSubPatternFactory(subPattern);
        }
        definition.setShapes(() -> shapeInfos.stream().map(factory -> factory.apply(definition)).flatMap(Collection::stream).toList());
        definition.setAllowFlip(allowFlip);
        if (recoveryItems != null) {
            definition.setRecoveryItems(recoveryItems);
        }
        if (partAppearance == null) {
            partAppearance = (controller, part, side) -> definition.getAppearance().get();
        }
        definition.setPartAppearance(partAppearance);
        definition.setAdditionalDisplay(additionalDisplay);
        return value = definition;
    }

    public MultiblockMachineBuilder checkPriority(final int checkPriority) {
        this.checkPriority = checkPriority;
        return this;
    }

    /**
     * @return {@code this}.
     */
    public MultiblockMachineBuilder generator(final boolean generator) {
        this.generator = generator;
        this.checkPriority = 1;
        return this;
    }

    /**
     * @return {@code this}.
     */
    public MultiblockMachineBuilder pattern(@NotNull Function<MultiblockMachineDefinition, BlockPattern> pattern) {
        this.pattern = pattern;
        return this;
    }

    /**
     * @return {@code this}.
     */
    public MultiblockMachineBuilder addSubPattern(@NotNull Function<MultiblockMachineDefinition, BlockPattern> pattern) {
        if (subPattern == null) subPattern = new ObjectArrayList<>();
        subPattern.add(pattern);
        return this;
    }

    /**
     * Set this to false only if your multiblock is set up such that it could have a wall-shared controller.
     * 
     * @return {@code this}.
     */
    public MultiblockMachineBuilder allowFlip(final boolean allowFlip) {
        this.allowFlip = allowFlip;
        return this;
    }

    /**
     * @return {@code this}.
     */
    public MultiblockMachineBuilder partAppearance(final TriFunction<IMultiController, IMultiPart, Direction, BlockState> partAppearance) {
        this.partAppearance = partAppearance;
        return this;
    }

    /**
     * @return {@code this}.
     */
    public MultiblockMachineBuilder additionalDisplay(final BiConsumer<IMultiController, List<Component>> additionalDisplay) {
        this.additionalDisplay = additionalDisplay;
        return this;
    }

    @FunctionalInterface
    public interface MufflerProductionGenerator {

        ItemStack getMuffledProduction(MetaMachine machine, @Nullable GTRecipe recipe);
    }

    static MufflerProductionGenerator ofMemorized(Supplier<Item> item) {
        final MemoizedSupplier<ItemStack> memorized = GTMemoizer.memoize(() -> new ItemStack(item.get()));
        return ((machine1, recipe) -> memorized.get());
    }
}
