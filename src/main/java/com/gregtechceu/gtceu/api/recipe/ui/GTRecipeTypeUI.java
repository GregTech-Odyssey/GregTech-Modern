package com.gregtechceu.gtceu.api.recipe.ui;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.SteamTexture;
import com.gregtechceu.gtceu.api.gui.WidgetUtils;
import com.gregtechceu.gtceu.api.gui.editor.IEditableUI;
import com.gregtechceu.gtceu.api.gui.widget.DualProgressWidget;
import com.gregtechceu.gtceu.api.gui.widget.SlotWidget;
import com.gregtechceu.gtceu.api.gui.widget.TankWidget;
import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.RecipeCondition;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.recipe.info.ContentRecipeInfo;
import com.gregtechceu.gtceu.api.recipe.info.FluidRecipeInfo;
import com.gregtechceu.gtceu.api.recipe.info.ItemRecipeInfo;
import com.gregtechceu.gtceu.api.recipe.info.RecipeInfo;
import com.gregtechceu.gtceu.integration.emi.recipe.GTRecipeEMICategory;
import com.gregtechceu.gtceu.utils.GTUtil;

import com.lowdragmc.lowdraglib.gui.editor.configurator.IConfigurableWidget;
import com.lowdragmc.lowdraglib.gui.editor.data.Resources;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ProgressTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.ProgressWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.utils.Position;
import com.lowdragmc.lowdraglib.utils.Size;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.packs.resources.ResourceManager;

import com.google.common.collect.Table;
import com.gto.datasynclib.datasream.DataComponentMap;
import dev.emi.emi.api.EmiApi;
import it.unimi.dsi.fastutil.bytes.Byte2ObjectArrayMap;
import it.unimi.dsi.fastutil.bytes.Byte2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2IntAVLTreeMap;
import it.unimi.dsi.fastutil.objects.Object2IntSortedMap;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.DoubleSupplier;

@SuppressWarnings("UnusedReturnValue")
public class GTRecipeTypeUI {

    @Getter
    @Setter
    private Byte2ObjectMap<IGuiTexture> slotOverlays = new Byte2ObjectArrayMap<>();
    private final GTRecipeType recipeType;
    @Getter
    @Setter
    private ProgressTexture progressBarTexture = new ProgressTexture(GuiTextures.PROGRESS_BAR_ARROW.getSubTexture(0, 0, 1, 0.5), GuiTextures.PROGRESS_BAR_ARROW.getSubTexture(0, 0.5, 1, 0.5));
    @Setter
    private SteamTexture steamProgressBarTexture = null;
    @Setter
    private ProgressTexture.FillDirection steamMoveType = ProgressTexture.FillDirection.LEFT_TO_RIGHT;
    @Nullable
    protected BiConsumer<GTRecipeDefinition, WidgetGroup> uiBuilder;
    @Getter
    @Setter
    protected int maxTooltips = 1;
    private CompoundTag customUICache;
    private Size xeiSize;
    @Getter
    private int originalWidth;

    /**
     * @param recipeType the recipemap corresponding to this ui
     */
    public GTRecipeTypeUI(@NotNull GTRecipeType recipeType) {
        this.recipeType = recipeType;
    }

    public CompoundTag getCustomUI() {
        if (this.customUICache == null) {
            ResourceManager resourceManager = null;
            if (GTCEu.isClientSide()) {
                resourceManager = Minecraft.getInstance().getResourceManager();
            } else if (GTCEu.getMinecraftServer() != null) {
                resourceManager = GTCEu.getMinecraftServer().getResourceManager();
            }
            if (resourceManager == null) {
                this.customUICache = new CompoundTag();
            } else {
                try {
                    var resource = resourceManager.getResourceOrThrow(GTUtil.getResourceLocation(recipeType.registryName.getNamespace(), "ui/recipe_type/%s.rtui".formatted(recipeType.registryName.getPath())));
                    try (InputStream inputStream = resource.open()) {
                        try (DataInputStream dataInputStream = new DataInputStream(inputStream)) {
                            this.customUICache = NbtIo.read(dataInputStream, NbtAccounter.UNLIMITED);
                        }
                    }
                } catch (Exception e) {
                    this.customUICache = new CompoundTag();
                }
                if (this.customUICache == null) {
                    this.customUICache = new CompoundTag();
                }
            }
        }
        return this.customUICache;
    }

    public boolean hasCustomUI() {
        return !getCustomUI().isEmpty();
    }

    public void reloadCustomUI() {
        this.customUICache = null;
        this.xeiSize = null;
    }

    public Size getJEISize(GTRecipeDefinition recipe) {
        Size size = this.xeiSize;
        if (size == null) {
            var originalSize = createEditableUITemplate(false, false).createDefault().getSize();
            this.originalWidth = originalSize.width;
            this.xeiSize = size = new Size(Math.max(originalWidth, 150), 5 + originalSize.height);
        }
        return new Size(size.width, size.height + getPropertyHeightShift(recipe));
    }

    public record RecipeHolder(DoubleSupplier progressSupplier, Table<IO, RecipeInfo, Object> storages, DataComponentMap data, List<RecipeCondition> conditions, boolean isSteam, boolean isHighPressure) {}

    /**
     * Auto layout UI template for recipes.
     * 
     * @param progressSupplier progress. To create a JEI / REI UI, use the para {@link ProgressWidget#JEIProgress}.
     */
    public WidgetGroup createUITemplate(DoubleSupplier progressSupplier, Table<IO, RecipeInfo, Object> storages, DataComponentMap data, List<RecipeCondition> conditions, boolean isSteam, boolean isHighPressure) {
        var template = createEditableUITemplate(isSteam, isHighPressure);
        var group = template.createDefault();
        template.setupUI(group, new RecipeHolder(progressSupplier, storages, data, conditions, isSteam, isHighPressure));
        return group;
    }

    public WidgetGroup createUITemplate(DoubleSupplier progressSupplier, Table<IO, RecipeInfo, Object> storages, DataComponentMap data, List<RecipeCondition> conditions) {
        return createUITemplate(progressSupplier, storages, data, conditions, false, false);
    }

    /**
     * Auto layout UI template for recipes.
     */
    public IEditableUI<WidgetGroup, RecipeHolder> createEditableUITemplate(final boolean isSteam, final boolean isHighPressure) {
        return createEditableUITemplate(isSteam, isHighPressure, (a, b) -> true);
    }

    /**
     * Auto layout UI template for recipes.
     */
    public IEditableUI<WidgetGroup, RecipeHolder> createEditableUITemplate(final boolean isSteam, final boolean isHighPressure, final BiPredicate<Boolean, RecipeInfo> doRenderSlot) {
        return new IEditableUI.Normal<>(() -> {
            var isCustomUI = !isSteam && hasCustomUI();
            if (isCustomUI) {
                CompoundTag nbt = getCustomUI();
                WidgetGroup group = new WidgetGroup();
                IConfigurableWidget.deserializeNBT(group, nbt.getCompound("root"), Resources.fromNBT(nbt.getCompound("resources")), false);
                group.setSelfPosition(new Position(0, 0));
                return group;
            }
            var inputs = addInventorySlotGroup(false, isSteam, isHighPressure, doRenderSlot);
            var outputs = addInventorySlotGroup(true, isSteam, isHighPressure, doRenderSlot);
            var maxWidth = Math.max(inputs.getSize().width, outputs.getSize().width);
            var group = new WidgetGroup(0, 0, 2 * maxWidth + 40, Math.max(inputs.getSize().height, outputs.getSize().height));
            var size = group.getSize();
            inputs.addSelfPosition((maxWidth - inputs.getSize().width) / 2, (size.height - inputs.getSize().height) / 2);
            outputs.addSelfPosition(maxWidth + 40 + (maxWidth - outputs.getSize().width) / 2, (size.height - outputs.getSize().height) / 2);
            group.addWidget(inputs);
            group.addWidget(outputs);
            var progressWidget = new ProgressWidget(ProgressWidget.JEIProgress, maxWidth + 10, size.height / 2 - 10, 20, 20, progressBarTexture);
            progressWidget.setId("progress");
            group.addWidget(progressWidget);
            progressWidget.setProgressTexture((isSteam && steamProgressBarTexture != null) ? new ProgressTexture(steamProgressBarTexture.get(isHighPressure).getSubTexture(0, 0, 1, 0.5), steamProgressBarTexture.get(isHighPressure).getSubTexture(0, 0.5, 1, 0.5)).setFillDirection(steamMoveType) : progressBarTexture);
            return group;
        }, (template, recipeHolder) -> {
            var isJEI = recipeHolder.progressSupplier == ProgressWidget.JEIProgress;
            // bind progress
            List<Widget> progress = new ArrayList<>();
            // First set the progress suppliers separately.
            WidgetUtils.widgetByIdForEach(template, "^progress$", ProgressWidget.class, progressWidget -> {
                progressWidget.setProgressSupplier(recipeHolder.progressSupplier);
                progress.add(progressWidget);
            });
            // Then set the dual-progress widgets, to override their builtin ones' suppliers, in case someone forgot to
            // remove the id from the internal ones.
            WidgetUtils.widgetByIdForEach(template, "^progress$", DualProgressWidget.class, dualProgressWidget -> {
                dualProgressWidget.setProgressSupplier(recipeHolder.progressSupplier);
                progress.add(dualProgressWidget);
            });
            // add recipe button
            if (!isJEI && GTCEu.Mods.isEMILoaded()) {
                for (Widget widget : progress) {
                    template.addWidget(new ButtonWidget(widget.getPosition().x, widget.getPosition().y, widget.getSize().width, widget.getSize().height, IGuiTexture.EMPTY, cd -> {
                        if (cd.isRemote) {
                            if (GTCEu.Mods.isEMILoaded()) {
                                EmiApi.displayRecipeCategory(GTRecipeEMICategory.machineCategory(recipeType.getCategory()));
                            }
                        }
                    }).setHoverTooltips("gtceu.recipe_type.show_recipes"));
                }
            }
            // Bind I/O
            for (var capabilityEntry : recipeHolder.storages.rowMap().entrySet()) {
                IO io = capabilityEntry.getKey();
                for (var storagesEntry : capabilityEntry.getValue().entrySet()) {
                    if (storagesEntry.getKey() instanceof ContentRecipeInfo cap) {
                        Object storage = storagesEntry.getValue();
                        // bind overlays
                        var widgetClass = cap.getWidgetClass();
                        if (widgetClass != null) {
                            WidgetUtils.widgetByIdForEach(template, "^%s_[0-9]+$".formatted(cap.slotName(io)), widgetClass, widget -> {
                                var index = WidgetUtils.widgetIdIndex(widget);
                                cap.applyWidgetInfo(widget, index, isJEI, io, recipeHolder, recipeType, null, null, storage, 0, 0);
                            });
                        }
                    }
                }
            }
        });
    }

    protected WidgetGroup addInventorySlotGroup(boolean isOutputs, boolean isSteam, boolean isHighPressure, final BiPredicate<Boolean, RecipeInfo> doRenderSlot) {
        int maxCount = 0;
        int totalR = 0;
        Object2IntSortedMap<RecipeInfo> map = new Object2IntAVLTreeMap<>(RecipeInfo.COMPARATOR);
        if (isOutputs) {
            for (var value : recipeType.maxOutputs.object2IntEntrySet()) {
                if (value.getKey().doRenderSlot && doRenderSlot.test(true, value.getKey())) {
                    int val = value.getIntValue();
                    if (val > maxCount) {
                        maxCount = Math.min(val, 3);
                    }
                    totalR += (val + 2) / 3;
                    map.put(value.getKey(), val);
                }
            }
        } else {
            for (var value : recipeType.maxInputs.object2IntEntrySet()) {
                if (value.getKey().doRenderSlot && doRenderSlot.test(false, value.getKey())) {
                    int val = value.getIntValue();
                    if (val > maxCount) {
                        maxCount = Math.min(val, 3);
                    }
                    totalR += (val + 2) / 3;
                    map.put(value.getKey(), val);
                }
            }
        }
        WidgetGroup group = new WidgetGroup(0, 0, maxCount * 18 + 8, totalR * 18 + 8);
        int index = 0;
        for (var entry : map.object2IntEntrySet()) {
            if (entry.getKey() instanceof ContentRecipeInfo cap) {
                var widgetClass = cap.getWidgetClass();
                if (widgetClass == null) {
                    continue;
                }
                int capCount = entry.getIntValue();
                for (int slotIndex = 0; slotIndex < capCount; slotIndex++) {
                    var slot = cap.createWidget();
                    slot.setSelfPosition(new Position((index % 3) * 18 + 4, (index / 3) * 18 + 4));
                    slot.setBackground(getOverlaysForSlot(isOutputs, cap, slotIndex == capCount - 1, isSteam, isHighPressure));
                    slot.setId(cap.slotName(isOutputs ? IO.OUT : IO.IN, slotIndex));
                    group.addWidget(slot);
                    index++;
                }
                // move to new row
                index += (3 - (index % 3)) % 3;
            }
        }
        return group;
    }

    /**
     * Add a slot to this ui
     */
    protected void addSlot(WidgetGroup group, int x, int y, int slotIndex, int count, RecipeInfo capability, boolean isOutputs, boolean isSteam, boolean isHighPressure) {
        if (capability != FluidRecipeInfo.INSTANCE) {
            var slot = new SlotWidget();
            slot.initTemplate();
            slot.setSelfPosition(new Position(x, y));
            slot.setBackground(getOverlaysForSlot(isOutputs, capability, slotIndex == count - 1, isSteam, isHighPressure));
            slot.setId(ItemRecipeInfo.INSTANCE.slotName(isOutputs ? IO.OUT : IO.IN, slotIndex));
            group.addWidget(slot);
        } else {
            var tank = new TankWidget();
            tank.initTemplate();
            tank.setFillDirection(ProgressTexture.FillDirection.ALWAYS_FULL);
            tank.setSelfPosition(new Position(x, y));
            tank.setBackground(getOverlaysForSlot(isOutputs, capability, slotIndex == count - 1, isSteam, isHighPressure));
            tank.setId(FluidRecipeInfo.INSTANCE.slotName(isOutputs ? IO.OUT : IO.IN, slotIndex));
            group.addWidget(tank);
        }
    }

    protected static int[] determineSlotsGrid(int itemCount) {
        int itemSlotsToLeft;
        int itemSlotsToDown;
        double sqrt = Math.sqrt(itemCount);
        // if the number of input has an integer root
        // return it.
        if (sqrt % 1 == 0) {
            itemSlotsToLeft = itemSlotsToDown = (int) sqrt;
        } else if (itemCount == 3) {
            itemSlotsToLeft = 3;
            itemSlotsToDown = 1;
        } else {
            // if we couldn't fit all into a perfect square,
            // increase the amount of slots to the left
            itemSlotsToLeft = (int) Math.ceil(sqrt);
            itemSlotsToDown = itemSlotsToLeft - 1;
            // if we still can't fit all the slots in a grid,
            // increase the amount of slots on the bottom
            if (itemCount > itemSlotsToLeft * itemSlotsToDown) {
                itemSlotsToDown = itemSlotsToLeft;
            }
        }
        return new int[] { itemSlotsToLeft, itemSlotsToDown };
    }

    protected IGuiTexture getOverlaysForSlot(boolean isOutput, RecipeInfo capability, boolean isLast, boolean isSteam, boolean isHighPressure) {
        IGuiTexture base = capability == FluidRecipeInfo.INSTANCE ? GuiTextures.FLUID_SLOT : (isSteam ? GuiTextures.SLOT_STEAM.get(isHighPressure) : GuiTextures.SLOT);
        byte overlayKey = (byte) ((isOutput ? 2 : 0) + (capability == FluidRecipeInfo.INSTANCE ? 1 : 0) + (isLast ? 4 : 0));
        if (slotOverlays.containsKey(overlayKey)) {
            return new GuiTextureGroup(base, slotOverlays.get(overlayKey));
        }
        return base;
    }

    /**
     * @return the height used to determine size of background texture in JEI
     */
    public int getPropertyHeightShift(GTRecipeDefinition recipe) {
        int height = maxTooltips * 10;
        for (var info : recipe.recipeType.getDataInfos()) {
            if (info.apply(recipe).isBlank()) continue;
            height += 10;
        }
        for (var c : recipe.conditions) {
            height += c.getInfoHeight(recipe);
        }
        for (var e : recipe.recipeExtensions) {
            height += e.getInfoHeight(recipe);
        }
        for (var e : recipe.tickRecipeExtensions) {
            height += e.getInfoHeight(recipe);
        }
        return height;
    }

    public void appendJEIUI(GTRecipeDefinition recipe, WidgetGroup widgetGroup) {
        if (uiBuilder != null) {
            uiBuilder.accept(recipe, widgetGroup);
        }
    }

    public GTRecipeTypeUI setSlotOverlay(boolean isOutput, boolean isFluid, IGuiTexture slotOverlay) {
        return this.setSlotOverlay(isOutput, isFluid, false, slotOverlay).setSlotOverlay(isOutput, isFluid, true, slotOverlay);
    }

    public GTRecipeTypeUI setSlotOverlay(boolean isOutput, boolean isFluid, boolean isLast, IGuiTexture slotOverlay) {
        this.slotOverlays.put((byte) ((isOutput ? 2 : 0) + (isFluid ? 1 : 0) + (isLast ? 4 : 0)), slotOverlay);
        return this;
    }

    public GTRecipeTypeUI setProgressBar(ResourceTexture progressBar, ProgressTexture.FillDirection moveType) {
        this.progressBarTexture = new ProgressTexture(progressBar.getSubTexture(0, 0, 1, 0.5), progressBar.getSubTexture(0, 0.5, 1, 0.5)).setFillDirection(moveType);
        return this;
    }

    public void setUiBuilder(@Nullable final BiConsumer<GTRecipeDefinition, WidgetGroup> uiBuilder) {
        this.uiBuilder = uiBuilder;
    }
}
