package com.gregtechceu.gtceu.api.machine;

import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.gui.editor.EditableMachineUI;
import com.gregtechceu.gtceu.api.item.MetaMachineItem;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.modifier.RecipeModifier;
import com.gregtechceu.gtceu.utils.GTUtil;

import com.lowdragmc.lowdraglib.client.renderer.IRenderer;
import com.lowdragmc.lowdraglib.utils.ShapeUtils;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.*;

/**
 * Representing basic information of a machine.
 */
public class MachineDefinition implements Supplier<MetaMachineBlock>, ItemLike {

    @Getter
    private final ResourceLocation id;
    // This is only stored here for KJS use.
    @Getter
    @Setter
    private String langValue;
    @Setter
    private Supplier<? extends MetaMachineBlock> blockSupplier;
    @Setter
    private Supplier<? extends MetaMachineItem> itemSupplier;
    @Setter
    private Supplier<BlockEntityType<? extends BlockEntity>> blockEntityTypeSupplier;
    @Setter
    private Function<MetaMachineBlockEntity, MetaMachine> machineSupplier;
    @Nullable
    private GTRecipeType[] recipeTypes;
    @Getter
    @Setter
    private int tier;
    @Getter
    @Setter
    private int defaultPaintingColor;
    @Getter
    @Setter
    private RecipeModifier recipeModifier;
    @NotNull
    private Consumer<IRecipeLogicMachine> onWorking = GTUtil.NOOP_CONSUMER;
    @Getter
    @Setter
    private boolean regressWhenWaiting = true;
    /**
     * Whether this machine can be rotated or face upwards.
     * -- SETTER --
     * Whether this machine can be rotated or face upwards.
     * -- GETTER --
     * Whether this machine can be rotated or face upwards.
     * 
     * 
     */
    @Getter
    @Setter
    private boolean allowExtendedFacing;
    @Getter
    @Setter
    private IRenderer renderer;
    @Setter
    private VoxelShape shape;
    @Getter
    @Setter
    private boolean renderWorldPreview;
    @Getter
    @Setter
    private boolean renderXEIPreview;
    private final Map<Direction, VoxelShape> cache = new EnumMap<>(Direction.class);
    @Getter
    @Setter
    private BiConsumer<ItemStack, List<Component>> tooltipBuilder;
    @Getter
    @Setter
    private Supplier<BlockState> appearance;
    @Getter
    @Setter
    private boolean allowCoverOnFront;
    @Nullable
    private EditableMachineUI editableUI;
    @Getter
    @Setter
    private Reference2IntOpenHashMap<RecipeCapability<?>> recipeOutputLimits;

    protected MachineDefinition(ResourceLocation id) {
        this.id = id;
    }

    public static MachineDefinition createDefinition(ResourceLocation id) {
        return new MachineDefinition(id);
    }

    @Override
    public @NotNull MetaMachineItem asItem() {
        return itemSupplier.get();
    }

    public BlockEntityType<? extends BlockEntity> getBlockEntityType() {
        return blockEntityTypeSupplier.get();
    }

    public MetaMachine createMetaMachine(MetaMachineBlockEntity blockEntity) {
        return machineSupplier.apply(blockEntity);
    }

    public ItemStack asStack() {
        return new ItemStack(asItem());
    }

    public ItemStack asStack(int count) {
        return new ItemStack(asItem(), count);
    }

    public VoxelShape getShape(Direction direction) {
        if (shape.isEmpty() || shape == Shapes.block() || direction == Direction.NORTH) return shape;
        return this.cache.computeIfAbsent(direction, dir -> ShapeUtils.rotate(shape, dir));
    }

    @Override
    public MetaMachineBlock get() {
        return blockSupplier.get();
    }

    public String getName() {
        return id.getPath();
    }

    @Override
    public String toString() {
        return "[Definition: %s]".formatted(id);
    }

    public String getDescriptionId() {
        return get().getDescriptionId();
    }

    public BlockState defaultBlockState() {
        return get().defaultBlockState();
    }

    @Nullable
    public GTRecipeType[] getRecipeTypes() {
        return this.recipeTypes;
    }

    public void setRecipeTypes(@Nullable final GTRecipeType[] recipeTypes) {
        this.recipeTypes = recipeTypes;
    }

    public void setOnWorking(@NotNull final Consumer<IRecipeLogicMachine> onWorking) {
        this.onWorking = onWorking;
    }

    @NotNull
    public Consumer<IRecipeLogicMachine> getOnWorking() {
        return this.onWorking;
    }

    @Nullable
    public EditableMachineUI getEditableUI() {
        return this.editableUI;
    }

    public void setEditableUI(@Nullable final EditableMachineUI editableUI) {
        this.editableUI = editableUI;
    }

    private static final ThreadLocal<MachineDefinition> STATE = new ThreadLocal<>();

    public static MachineDefinition getBuilt() {
        return STATE.get();
    }

    public static void setBuilt(MachineDefinition state) {
        STATE.set(state);
    }

    public static void clearBuilt() {
        STATE.remove();
    }
}
