package com.gregtechceu.gtceu.api.recipe.ui;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.SteamTexture;
import com.gregtechceu.gtceu.api.gui.WidgetUtils;
import com.gregtechceu.gtceu.api.gui.editor.IEditableUI;
import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.RecipeCondition;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.recipe.info.ContentRecipeInfo;
import com.gregtechceu.gtceu.api.recipe.info.FluidRecipeInfo;
import com.gregtechceu.gtceu.api.recipe.info.RecipeInfo;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ProgressTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;
import com.lowdragmc.lowdraglib.gui.widget.ProgressWidget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.utils.Size;

import com.google.common.collect.Table;
import com.gto.datasynclib.datastream.DataComponentMap;
import it.unimi.dsi.fastutil.bytes.Byte2ObjectArrayMap;
import it.unimi.dsi.fastutil.bytes.Byte2ObjectMap;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.DoubleSupplier;

/**
 * 配方类型的界面：槽位区（配方查看器与单方块机器界面共用）和配方页的附加信息。
 * <p>
 * 槽位区由 {@link RecipeSlotLayout} 用新式界面框架（uipro）搭出，默认 {@link RecipeSlotLayouts#DEFAULT}；
 * 需要专用底图的配方类型用 {@link #setSlotLayout} 换成自己的 Java 排布。搭好后按槽位 id 绑定：
 * 机器界面绑定机器库存与配方进度，配方查看器绑定配方内容。
 * 配方页的其余部分（信息区、电压档、额外展示槽）由 {@code GTRecipeWidget} 组装，附加内容经 {@link #setUiBuilder} 注册。
 */
@SuppressWarnings("UnusedReturnValue")
public class GTRecipeTypeUI {

    /** 配方类型设置的槽位角标，键见 {@link #overlayKey}。 */
    @Getter
    @Setter
    private Byte2ObjectMap<IGuiTexture> slotOverlays = new Byte2ObjectArrayMap<>();
    @Getter
    private final GTRecipeType recipeType;
    @Getter
    @Setter
    private ProgressTexture progressBarTexture = new ProgressTexture(GuiTextures.PROGRESS_BAR_ARROW.getSubTexture(0, 0, 1, 0.5), GuiTextures.PROGRESS_BAR_ARROW.getSubTexture(0, 0.5, 1, 0.5));
    @Setter
    private SteamTexture steamProgressBarTexture = null;
    @Setter
    private ProgressTexture.FillDirection steamMoveType = ProgressTexture.FillDirection.LEFT_TO_RIGHT;
    /** 配方页的附加内容（所需线圈、邻接流体……），见 {@link #setUiBuilder}。 */
    @Nullable
    protected BiConsumer<GTRecipeDefinition, RecipeInfoBuilder> uiBuilder;
    /** 配方页信息区至少占的行数：同一类别的配方页高度尽量一致，翻页时不跳。 */
    @Getter
    @Setter
    protected int maxTooltips = 1;
    @Getter
    @NotNull
    private RecipeSlotLayout slotLayout = RecipeSlotLayouts.DEFAULT;
    /// 配方查看器里槽位区的尺寸，第一次用到时搭一遍默认槽位区量出来
    @Nullable
    private Size slotAreaSize;

    /**
     * @param recipeType the recipemap corresponding to this ui
     */
    public GTRecipeTypeUI(@NotNull GTRecipeType recipeType) {
        this.recipeType = recipeType;
    }

    /** 换成专用排布（带整张底图的配方类型，替代原来 LDLib 编辑器存的 .rtui 布局）。 */
    public GTRecipeTypeUI setSlotLayout(@NotNull RecipeSlotLayout slotLayout) {
        this.slotLayout = slotLayout;
        this.slotAreaSize = null;
        return this;
    }

    /** 配方查看器里槽位区的尺寸（与配方无关，同一配方类型的所有配方相同）。 */
    public Size getSlotAreaSize() {
        var size = slotAreaSize;
        if (size == null) {
            size = slotLayout.build(new RecipeSlots(this, false, false, (output, cap) -> true)).getSize();
            slotAreaSize = size;
        }
        return size;
    }

    public record RecipeHolder(DoubleSupplier progressSupplier, Table<IO, RecipeInfo, Object> storages, DataComponentMap data, List<RecipeCondition> conditions, boolean isSteam, boolean isHighPressure) {}

    /**
     * 搭好并绑定好的槽位区。
     *
     * @param progressSupplier 配方进度；配方查看器传 {@link ProgressWidget#JEIProgress}（循环播放）
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

    /** 槽位区的两步构建：先按配方类型搭好（两端一致），再绑定。 */
    public IEditableUI<WidgetGroup, RecipeHolder> createEditableUITemplate(final boolean isSteam, final boolean isHighPressure) {
        return createEditableUITemplate(isSteam, isHighPressure, (output, cap) -> true);
    }

    /**
     * 槽位区的两步构建：先按配方类型搭好（两端一致），再绑定。
     *
     * @param doRenderSlot {@code (是否输出, 内容种类)} → 是否显示该种类的槽位，例如发电机界面只显示输入
     */
    public IEditableUI<WidgetGroup, RecipeHolder> createEditableUITemplate(final boolean isSteam, final boolean isHighPressure, final BiPredicate<Boolean, RecipeInfo> doRenderSlot) {
        return new IEditableUI.Normal<>(() -> slotLayout.build(new RecipeSlots(this, isSteam, isHighPressure, doRenderSlot)), this::bind);
    }

    /**
     * 绑定：所有 id 为 {@link RecipeProgressWidget#ID} 的进度条（含资源包自定义界面里的普通进度条）接配方进度，
     * 机器界面里点击配方进度条打开 EMI 配方页；槽位按 id 接存储。
     */
    private void bind(WidgetGroup template, RecipeHolder holder) {
        var isJEI = holder.progressSupplier() == ProgressWidget.JEIProgress;
        WidgetUtils.widgetByIdForEach(template, "^" + RecipeProgressWidget.ID + "$", ProgressWidget.class, progress -> {
            progress.setProgressSupplier(holder.progressSupplier());
            if (!isJEI && progress instanceof RecipeProgressWidget recipeProgress) recipeProgress.setOpenCategory(recipeType.getCategory());
        });
        for (var capabilityEntry : holder.storages().rowMap().entrySet()) {
            IO io = capabilityEntry.getKey();
            for (var storagesEntry : capabilityEntry.getValue().entrySet()) {
                if (!(storagesEntry.getKey() instanceof ContentRecipeInfo<?, ?> cap)) continue;
                var widgetClass = cap.getWidgetClass();
                if (widgetClass == null) continue;
                Object storage = storagesEntry.getValue();
                WidgetUtils.widgetByIdForEach(template, "^%s_[0-9]+$".formatted(cap.slotName(io)), widgetClass, widget -> {
                    var index = WidgetUtils.widgetIdIndex(widget);
                    cap.applyWidgetInfo(widget, index, isJEI, io, holder, recipeType, null, null, storage, 0, 0);
                });
            }
        }
    }

    /** 配方类型设置的槽位角标（{@link #setSlotOverlay}），没有时为 null。 */
    @Nullable
    public IGuiTexture getSlotOverlay(boolean isOutput, RecipeInfo capability, boolean isLast) {
        return slotOverlays.get(overlayKey(isOutput, capability == FluidRecipeInfo.INSTANCE, isLast));
    }

    /** 进度条底图：蒸汽机用配方类型的蒸汽版（设置过的话）。 */
    public ProgressTexture progressTexture(boolean isSteam, boolean isHighPressure) {
        if (isSteam && steamProgressBarTexture != null) {
            var texture = steamProgressBarTexture.get(isHighPressure);
            return new ProgressTexture(texture.getSubTexture(0, 0, 1, 0.5), texture.getSubTexture(0, 0.5, 1, 0.5)).setFillDirection(steamMoveType);
        }
        return progressBarTexture;
    }

    /** 往配方页信息区追加配方类型的附加内容（{@link #setUiBuilder}）。 */
    public void appendInfo(GTRecipeDefinition recipe, RecipeInfoBuilder info) {
        if (uiBuilder != null) uiBuilder.accept(recipe, info);
    }

    public GTRecipeTypeUI setSlotOverlay(boolean isOutput, boolean isFluid, IGuiTexture slotOverlay) {
        return this.setSlotOverlay(isOutput, isFluid, false, slotOverlay).setSlotOverlay(isOutput, isFluid, true, slotOverlay);
    }

    public GTRecipeTypeUI setSlotOverlay(boolean isOutput, boolean isFluid, boolean isLast, IGuiTexture slotOverlay) {
        this.slotOverlays.put(overlayKey(isOutput, isFluid, isLast), slotOverlay);
        return this;
    }

    private static byte overlayKey(boolean isOutput, boolean isFluid, boolean isLast) {
        return (byte) ((isOutput ? 2 : 0) + (isFluid ? 1 : 0) + (isLast ? 4 : 0));
    }

    public GTRecipeTypeUI setProgressBar(ResourceTexture progressBar, ProgressTexture.FillDirection moveType) {
        this.progressBarTexture = new ProgressTexture(progressBar.getSubTexture(0, 0, 1, 0.5), progressBar.getSubTexture(0, 0.5, 1, 0.5)).setFillDirection(moveType);
        return this;
    }

    /**
     * 配方页的附加内容：配方类型特有、又不属于某个配方条件的信息，例如 EBF 所需的线圈、碎岩机两侧的流体。
     * 与配方条件一样只能按配方决定追加什么（见 {@link RecipeInfoBuilder}）。
     */
    public void setUiBuilder(@Nullable final BiConsumer<GTRecipeDefinition, RecipeInfoBuilder> uiBuilder) {
        this.uiBuilder = uiBuilder;
    }
}
