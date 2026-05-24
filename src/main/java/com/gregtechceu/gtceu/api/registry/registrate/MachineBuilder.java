package com.gregtechceu.gtceu.api.registry.registrate;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.gui.editor.EditableMachineUI;
import com.gregtechceu.gtceu.api.item.MetaMachineItem;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.modifier.RecipeModifier;
import com.gregtechceu.gtceu.api.recipe.modifier.RecipeModifierList;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.client.renderer.GTRendererProvider;
import com.gregtechceu.gtceu.client.renderer.machine.*;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.utils.GTUtil;
import com.gregtechceu.gtceu.utils.memoization.GTMemoizer;

import com.lowdragmc.lowdraglib.client.renderer.IRenderer;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.gto.registrate.Registrate;
import com.gto.registrate.builders.BlockBuilder;
import com.gto.registrate.builders.ItemBuilder;
import com.gto.registrate.providers.ProviderType;
import com.gto.registrate.util.entry.BlockEntry;
import com.gto.registrate.util.nullness.NonNullBiConsumer;
import com.gto.registrate.util.nullness.NonNullUnaryOperator;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.function.TriFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.*;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class MachineBuilder<DEFINITION extends MachineDefinition> extends BuilderBase<DEFINITION> {

    protected final Registrate registrate;
    protected final String name;
    protected final BiFunction<BlockBehaviour.Properties, DEFINITION, MetaMachineBlock> blockFactory;
    protected final BiFunction<MetaMachineBlock, Item.Properties, MetaMachineItem> itemFactory;
    protected final TriFunction<BlockEntityType<?>, BlockPos, BlockState, MetaMachineBlockEntity> blockEntityFactory;
    // non-final for KJS
    protected Function<ResourceLocation, DEFINITION> definition;
    // non-final for KJS
    protected Function<MetaMachineBlockEntity, MetaMachine> machine;
    @Nullable
    private Supplier<IRenderer> renderer;
    private VoxelShape shape = Shapes.block();
    private RotationState rotationState = RotationState.NON_Y_AXIS;
    /**
     * Whether this machine can be rotated or face upwards.
     */
    private boolean allowExtendedFacing = false;
    private boolean hasTESR;
    private boolean renderMultiblockWorldPreview = true;
    private boolean renderMultiblockXEIPreview = true;
    private NonNullUnaryOperator<BlockBehaviour.Properties> blockProp = p -> p;
    private NonNullUnaryOperator<Item.Properties> itemProp = p -> p;
    private Consumer<BlockBuilder<? extends Block, ?>> blockBuilder;
    private Consumer<ItemBuilder<? extends MetaMachineItem, ?>> itemBuilder;
    // getter for KJS
    private GTRecipeType[] recipeTypes;
    // getter for KJS
    private int tier;
    private Reference2IntOpenHashMap<RecipeCapability<?>> recipeOutputLimits = new Reference2IntOpenHashMap<>();
    private int paintingColor = Long.decode(ConfigHolder.INSTANCE.client.defaultPaintingColor).intValue();
    private BiFunction<ItemStack, Integer, Integer> itemColor = ((itemStack, tintIndex) -> tintIndex == 2 ? GTValues.VC[tier] : tintIndex == 1 ? paintingColor : -1);
    private PartAbility[] abilities = new PartAbility[0];
    private final List<Component> tooltips = new ArrayList<>();
    private BiConsumer<ItemStack, List<Component>> tooltipBuilder;
    private RecipeModifier recipeModifier = RecipeModifier.OVERCLOCKING;

    @NotNull
    private Predicate<IRecipeLogicMachine> onWorking = GTUtil.FAVORABLE;
    private boolean regressWhenWaiting = true;
    private boolean allowCoverOnFront = false;

    @Setter
    @Accessors(chain = true, fluent = true)
    protected boolean canMultiShared = true;

    private Supplier<BlockState> appearance;
    // getter for KJS
    @Nullable
    private EditableMachineUI editableUI;
    // getter for KJS
    @Nullable
    private String langValue = null;

    protected MachineBuilder(Registrate registrate, String name, Function<ResourceLocation, DEFINITION> definition, Function<MetaMachineBlockEntity, MetaMachine> machine, BiFunction<BlockBehaviour.Properties, DEFINITION, MetaMachineBlock> blockFactory, BiFunction<MetaMachineBlock, Item.Properties, MetaMachineItem> itemFactory, TriFunction<BlockEntityType<?>, BlockPos, BlockState, MetaMachineBlockEntity> blockEntityFactory) {
        super(GTUtil.getResourceLocation(registrate.getModid(), name));
        this.registrate = registrate;
        this.name = name;
        this.machine = machine;
        this.blockFactory = blockFactory;
        this.itemFactory = itemFactory;
        this.blockEntityFactory = blockEntityFactory;
        this.definition = definition;
    }

    public MachineBuilder<DEFINITION> recipeType(GTRecipeType type) {
        this.recipeTypes = ArrayUtils.add(this.recipeTypes, type);
        return this;
    }

    public MachineBuilder<DEFINITION> recipeTypes(GTRecipeType... types) {
        for (GTRecipeType type : types) {
            this.recipeTypes = ArrayUtils.add(this.recipeTypes, type);
        }
        return this;
    }

    public static <DEFINITION extends MachineDefinition> MachineBuilder<DEFINITION> create(Registrate registrate, String name, Function<ResourceLocation, DEFINITION> definitionFactory, Function<MetaMachineBlockEntity, MetaMachine> metaMachine, BiFunction<BlockBehaviour.Properties, DEFINITION, MetaMachineBlock> blockFactory, BiFunction<MetaMachineBlock, Item.Properties, MetaMachineItem> itemFactory, TriFunction<BlockEntityType<?>, BlockPos, BlockState, MetaMachineBlockEntity> blockEntityFactory) {
        return new MachineBuilder<>(registrate, name, definitionFactory, metaMachine, blockFactory, itemFactory, blockEntityFactory);
    }

    public MachineBuilder<DEFINITION> modelRenderer(Supplier<ResourceLocation> model) {
        this.renderer = () -> new MachineRenderer(model.get());
        return this;
    }

    public MachineBuilder<DEFINITION> defaultModelRenderer() {
        return modelRenderer(() -> GTUtil.getResourceLocation(registrate.getModid(), "block/" + name));
    }

    public MachineBuilder<DEFINITION> tieredHullRenderer(ResourceLocation model) {
        return renderer(() -> new TieredHullMachineRenderer(tier, model));
    }

    public MachineBuilder<DEFINITION> overlayTieredHullRenderer(String name) {
        return renderer(() -> new OverlayTieredMachineRenderer(tier, GTUtil.getResourceLocation(registrate.getModid(), "block/machine/part/" + name)));
    }

    public MachineBuilder<DEFINITION> overlaySteamHullRenderer(String name) {
        return renderer(() -> new OverlaySteamMachineRenderer(GTUtil.getResourceLocation(registrate.getModid(), "block/machine/part/" + name)));
    }

    public MachineBuilder<DEFINITION> workableTieredHullRenderer(ResourceLocation workableModel) {
        return renderer(() -> new WorkableTieredHullMachineRenderer(tier, workableModel));
    }

    public MachineBuilder<DEFINITION> simpleGeneratorMachineRenderer(ResourceLocation workableModel) {
        return renderer(() -> new SimpleGeneratorMachineRenderer(tier, workableModel));
    }

    public MachineBuilder<DEFINITION> workableSteamHullRenderer(boolean isHighPressure, ResourceLocation workableModel) {
        return renderer(() -> new WorkableSteamMachineRenderer(isHighPressure, workableModel));
    }

    public MachineBuilder<DEFINITION> workableCasingRenderer(ResourceLocation baseCasing, ResourceLocation workableModel) {
        return renderer(() -> new WorkableCasingMachineRenderer(baseCasing, workableModel));
    }

    public MachineBuilder<DEFINITION> workableCasingRenderer(ResourceLocation baseCasing, ResourceLocation workableModel, boolean tint) {
        return renderer(() -> new WorkableCasingMachineRenderer(baseCasing, workableModel, tint));
    }

    public MachineBuilder<DEFINITION> sidedWorkableCasingRenderer(String basePath, ResourceLocation overlayModel, boolean tint) {
        return renderer(() -> new WorkableSidedCasingMachineRenderer(basePath, overlayModel, tint));
    }

    public MachineBuilder<DEFINITION> sidedWorkableCasingRenderer(String basePath, ResourceLocation overlayModel) {
        return renderer(() -> new WorkableSidedCasingMachineRenderer(basePath, overlayModel));
    }

    public MachineBuilder<DEFINITION> appearanceBlock(Supplier<? extends Block> block) {
        appearance = () -> block.get().defaultBlockState();
        return this;
    }

    public MachineBuilder<DEFINITION> tooltips(Component... components) {
        tooltips.addAll(Arrays.stream(components).filter(Objects::nonNull).toList());
        return this;
    }

    public MachineBuilder<DEFINITION> conditionalTooltip(Component component, BooleanSupplier condition) {
        return conditionalTooltip(component, condition.getAsBoolean());
    }

    public MachineBuilder<DEFINITION> conditionalTooltip(Component component, boolean condition) {
        if (condition) tooltips.add(component);
        return this;
    }

    public MachineBuilder<DEFINITION> abilities(PartAbility... abilities) {
        this.abilities = abilities;
        return this;
    }

    public MachineBuilder<DEFINITION> recipeModifier(RecipeModifier recipeModifier) {
        this.recipeModifier = recipeModifier;
        return this;
    }

    public MachineBuilder<DEFINITION> recipeModifiers(RecipeModifier... recipeModifiers) {
        this.recipeModifier = recipeModifiers.length > 1 ? new RecipeModifierList(recipeModifiers) : recipeModifiers[0];
        return this;
    }

    public MachineBuilder<DEFINITION> noRecipeModifier() {
        this.recipeModifier = RecipeModifier.NO_MODIFIER;
        return this;
    }

    public MachineBuilder<DEFINITION> addOutputLimit(RecipeCapability<?> capability, int limit) {
        this.recipeOutputLimits.put(capability, limit);
        return this;
    }

    public MachineBuilder<DEFINITION> multiblockPreviewRenderer(boolean multiBlockWorldPreview, boolean multiBlockXEIPreview) {
        this.renderMultiblockWorldPreview = multiBlockWorldPreview;
        this.renderMultiblockXEIPreview = multiBlockXEIPreview;
        return this;
    }

    protected DEFINITION createDefinition() {
        return definition.apply(GTUtil.getResourceLocation(registrate.getModid(), name));
    }

    public DEFINITION register() {
        var definition = createDefinition();
        var blockBuilder = BlockBuilderWrapper.makeBlockBuilder(this, definition);
        if (this.langValue != null) {
            blockBuilder.lang(langValue);
            definition.setLangValue(langValue);
        }
        blockBuilder.canMultiShared(canMultiShared);
        if (this.blockBuilder != null) {
            this.blockBuilder.accept(blockBuilder);
        }
        var block = blockBuilder.register();
        var itemBuilder = ItemBuilderWrapper.makeItemBuilder(this, block);
        if (this.itemBuilder != null) {
            this.itemBuilder.accept(itemBuilder);
        }
        var item = itemBuilder.register();
        var blockEntityBuilder = registrate.blockEntity(name, (type, pos, state) -> blockEntityFactory.apply(type, pos, state)).validBlock(block);
        if (hasTESR) {
            blockEntityBuilder = blockEntityBuilder.renderer(() -> GTRendererProvider::getOrCreate);
        }
        var blockEntity = blockEntityBuilder.register();
        definition.setRecipeTypes(recipeTypes);
        definition.setBlockSupplier(block);
        definition.setItemSupplier(item);
        definition.setTier(tier);
        definition.setRecipeOutputLimits(recipeOutputLimits);
        definition.setBlockEntityTypeSupplier(blockEntity::get);
        definition.setMachineSupplier(machine);
        definition.setTooltipBuilder((itemStack, components) -> {
            components.addAll(tooltips);
            if (tooltipBuilder != null) tooltipBuilder.accept(itemStack, components);
        });
        definition.setRecipeModifier(recipeModifier);
        definition.setOnWorking(this.onWorking);
        definition.setRegressWhenWaiting(this.regressWhenWaiting);
        definition.setAllowCoverOnFront(this.allowCoverOnFront);
        if (renderer == null) {
            renderer = () -> new MachineRenderer(GTUtil.getResourceLocation(registrate.getModid(), "block/machine/" + name));
        }
        if (recipeTypes != null) {
            for (GTRecipeType type : recipeTypes) {
                if (type != null && type.getIconSupplier() == null) {
                    type.setIconSupplier(definition::asStack);
                }
            }
        }
        if (appearance == null) {
            appearance = block::getDefaultState;
        }
        if (editableUI != null) {
            definition.setEditableUI(editableUI);
        }
        definition.setAppearance(GTMemoizer.memoize(appearance));
        definition.setAllowExtendedFacing(allowExtendedFacing);
        definition.setRenderer(GTCEu.isClientSide() ? renderer.get() : IRenderer.EMPTY);
        definition.setShape(shape);
        definition.setDefaultPaintingColor(paintingColor);
        definition.setRenderXEIPreview(renderMultiblockXEIPreview);
        definition.setRenderWorldPreview(renderMultiblockWorldPreview);
        GTRegistries.MACHINES.register(definition.getId(), definition);
        return definition;
    }

    static class BlockBuilderWrapper {

        public static <DEFINITION extends MachineDefinition> BlockBuilder<MetaMachineBlock, Registrate> makeBlockBuilder(MachineBuilder<DEFINITION> builder, DEFINITION definition) {
            return
            // .tag(GTToolType.WRENCH.harvestTag)
            builder.registrate.block(builder.name, properties -> {
                RotationState.set(builder.rotationState);
                MachineDefinition.setBuilt(definition);
                var b = builder.blockFactory.apply(properties, definition);
                RotationState.clear();
                MachineDefinition.clearBuilt();
                return b;
            }).color(() -> () -> MetaMachineBlock::colorTinted).initialProperties(() -> Blocks.DISPENSER).properties(BlockBehaviour.Properties::noLootTable).addLayer(() -> RenderType::cutoutMipped).blockstate(NonNullBiConsumer.noop()).properties(builder.blockProp).onRegister(b -> Arrays.stream(builder.abilities).forEach(a -> a.register(builder.tier, b)));
        }
    }

    static class ItemBuilderWrapper {

        public static <DEFINITION extends MachineDefinition> ItemBuilder<MetaMachineItem, Registrate> makeItemBuilder(MachineBuilder<DEFINITION> builder, BlockEntry<MetaMachineBlock> block) {
            return  // do not gen any lang keys
            builder.registrate.item(builder.name, properties -> builder.itemFactory.apply(block.get(), properties)).setData(ProviderType.LANG, NonNullBiConsumer.noop()).model(NonNullBiConsumer.noop()).color(() -> () -> builder.itemColor::apply).properties(builder.itemProp);
        }
    }

    public MachineBuilder<DEFINITION> nonYAxisRotation() {
        return rotationState(RotationState.NON_Y_AXIS).allowExtendedFacing(false);
    }

    public MachineBuilder<DEFINITION> allRotation() {
        return rotationState(RotationState.ALL);
    }

    public MachineBuilder<DEFINITION> noneRotation() {
        return rotationState(RotationState.NONE).allowExtendedFacing(false);
    }

    /**
     * @return {@code this}.
     */
    public MachineBuilder<DEFINITION> definition(final Function<ResourceLocation, DEFINITION> definition) {
        this.definition = definition;
        return this;
    }

    /**
     * @return {@code this}.
     */
    public MachineBuilder<DEFINITION> machine(final Function<MetaMachineBlockEntity, MetaMachine> machine) {
        this.machine = machine;
        return this;
    }

    /**
     * @return {@code this}.
     */
    public MachineBuilder<DEFINITION> renderer(@Nullable final Supplier<IRenderer> renderer) {
        this.renderer = renderer;
        return this;
    }

    /**
     * @return {@code this}.
     */
    public MachineBuilder<DEFINITION> shape(final VoxelShape shape) {
        this.shape = shape;
        return this;
    }

    /**
     * @return {@code this}.
     */
    public MachineBuilder<DEFINITION> rotationState(final RotationState rotationState) {
        this.rotationState = rotationState;
        return this;
    }

    /**
     * Whether this machine can be rotated or face upwards.
     *
     * @return {@code this}.
     */
    public MachineBuilder<DEFINITION> allowExtendedFacing(final boolean allowExtendedFacing) {
        this.allowExtendedFacing = allowExtendedFacing;
        return this;
    }

    /**
     * @return {@code this}.
     */
    public MachineBuilder<DEFINITION> hasTESR(final boolean hasTESR) {
        this.hasTESR = hasTESR;
        return this;
    }

    /**
     * @return {@code this}.
     */
    public MachineBuilder<DEFINITION> renderMultiblockWorldPreview(final boolean renderMultiblockWorldPreview) {
        this.renderMultiblockWorldPreview = renderMultiblockWorldPreview;
        return this;
    }

    /**
     * @return {@code this}.
     */
    public MachineBuilder<DEFINITION> renderMultiblockXEIPreview(final boolean renderMultiblockXEIPreview) {
        this.renderMultiblockXEIPreview = renderMultiblockXEIPreview;
        return this;
    }

    /**
     * @return {@code this}.
     */
    public MachineBuilder<DEFINITION> blockProp(final NonNullUnaryOperator<BlockBehaviour.Properties> blockProp) {
        this.blockProp = blockProp;
        return this;
    }

    /**
     * @return {@code this}.
     */
    public MachineBuilder<DEFINITION> itemProp(final NonNullUnaryOperator<Item.Properties> itemProp) {
        this.itemProp = itemProp;
        return this;
    }

    /**
     * @return {@code this}.
     */
    public MachineBuilder<DEFINITION> blockBuilder(final Consumer<BlockBuilder<? extends Block, ?>> blockBuilder) {
        this.blockBuilder = blockBuilder;
        return this;
    }

    /**
     * @return {@code this}.
     */
    public MachineBuilder<DEFINITION> itemBuilder(final Consumer<ItemBuilder<? extends MetaMachineItem, ?>> itemBuilder) {
        this.itemBuilder = itemBuilder;
        return this;
    }

    public GTRecipeType[] recipeTypes() {
        return this.recipeTypes;
    }

    public int tier() {
        return this.tier;
    }

    /**
     * @return {@code this}.
     */
    public MachineBuilder<DEFINITION> tier(final int tier) {
        this.tier = tier;
        return this;
    }

    /**
     * @return {@code this}.
     */
    public MachineBuilder<DEFINITION> recipeOutputLimits(final Reference2IntOpenHashMap<RecipeCapability<?>> recipeOutputLimits) {
        this.recipeOutputLimits = recipeOutputLimits;
        return this;
    }

    /**
     * @return {@code this}.
     */
    public MachineBuilder<DEFINITION> paintingColor(final int paintingColor) {
        this.paintingColor = paintingColor;
        return this;
    }

    /**
     * @return {@code this}.
     */
    public MachineBuilder<DEFINITION> itemColor(final BiFunction<ItemStack, Integer, Integer> itemColor) {
        this.itemColor = itemColor;
        return this;
    }

    /**
     * @return {@code this}.
     */
    public MachineBuilder<DEFINITION> tooltipBuilder(final BiConsumer<ItemStack, List<Component>> tooltipBuilder) {
        this.tooltipBuilder = tooltipBuilder;
        return this;
    }

    /**
     * @return {@code this}.
     */
    public MachineBuilder<DEFINITION> onWorking(final Predicate<IRecipeLogicMachine> onWorking) {
        this.onWorking = onWorking;
        return this;
    }

    /**
     * @return {@code this}.
     */
    public MachineBuilder<DEFINITION> regressWhenWaiting(final boolean regressWhenWaiting) {
        this.regressWhenWaiting = regressWhenWaiting;
        return this;
    }

    /**
     * @return {@code this}.
     */
    public MachineBuilder<DEFINITION> allowCoverOnFront(final boolean allowCoverOnFront) {
        this.allowCoverOnFront = allowCoverOnFront;
        return this;
    }

    /**
     * @return {@code this}.
     */
    public MachineBuilder<DEFINITION> appearance(final Supplier<BlockState> appearance) {
        this.appearance = appearance;
        return this;
    }

    @Nullable
    public EditableMachineUI editableUI() {
        return this.editableUI;
    }

    /**
     * @return {@code this}.
     */
    public MachineBuilder<DEFINITION> editableUI(@Nullable final EditableMachineUI editableUI) {
        this.editableUI = editableUI;
        return this;
    }

    @Nullable
    public String langValue() {
        return this.langValue;
    }

    /**
     * @return {@code this}.
     */
    public MachineBuilder<DEFINITION> langValue(@Nullable final String langValue) {
        this.langValue = langValue;
        return this;
    }
}
