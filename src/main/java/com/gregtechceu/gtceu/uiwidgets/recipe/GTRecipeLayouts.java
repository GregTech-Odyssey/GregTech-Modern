package com.gregtechceu.gtceu.uiwidgets.recipe;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.recipe.info.FluidRecipeInfo;
import com.gregtechceu.gtceu.api.recipe.info.ItemRecipeInfo;
import com.gregtechceu.gtceu.api.recipe.ui.RecipeSlotLayout;
import com.gregtechceu.gtceu.api.recipe.ui.RecipeSlots;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;

import static com.gregtechceu.gtceu.api.recipe.ui.RecipeSlotLayouts.*;
import static com.lowdragmc.lowdraglib.gui.texture.ProgressTexture.FillDirection.*;

/**
 * GTM 带专用底图的配方类型的槽位区排布（原来是 LDLib 编辑器存的 .rtui，改成 Java）。
 * 槽位和进度条要对准底图，按底图坐标摆在画布上；坐标沿用原布局。
 * 配方所需的研究数据改由研究条件作为配方页底部的展示槽显示，排布里不再留数据球槽。
 */
public final class GTRecipeLayouts {

    private GTRecipeLayouts() {}

    private static final ResourceTexture DISTILLATION_TOWER_COIL = new ResourceTexture("gtceu:textures/gui/progress_bar/progress_bar_distillation_tower_coil.png");
    private static final ResourceTexture DISTILLATION_TOWER_BUBBLES = new ResourceTexture("gtceu:textures/gui/progress_bar/progress_bar_distillation_tower_bubbles.png");

    /**
     * 装配线：左 4×4 物品输入，中间一张大箭头底图，流体输入竖排压在箭头上，右上输出。
     * 组装模块、电路装配线等同样排布的配方类型也用它。
     */
    public static final RecipeSlotLayout ASSEMBLY_LINE = slots -> {
        var canvas = canvas(154, 80);
        // 箭头先放：流体槽画在它上面
        place(canvas, slots.progress(GuiTextures.PROGRESS_BAR_ASSEMBLY_LINE, LEFT_TO_RIGHT, 53, 69), 76, 5);
        var items = slots.slots(IO.IN, ItemRecipeInfo.INSTANCE);
        for (int i = 0; i < items.size(); i++) place(canvas, items.get(i), 4 + (i % 4) * UISizes.SLOT, 4 + (i / 4) * UISizes.SLOT);
        var fluids = slots.slots(IO.IN, FluidRecipeInfo.INSTANCE);
        for (int i = 0; i < fluids.size(); i++) place(canvas, fluids.get(i), 93, 4 + i * UISizes.SLOT);
        var outputs = slots.slots(IO.OUT, ItemRecipeInfo.INSTANCE);
        for (int i = 0; i < outputs.size(); i++) place(canvas, outputs.get(i), 130, 4 + i * UISizes.SLOT);
        return canvas;
    };

    /**
     * 蒸馏塔：左侧流体输入（下方线圈、上方气泡），中间塔身，右侧 3 列 × 4 行流体输出（自下而上编号，对应塔的层），
     * 下方物品输出。进度先走线圈（前 40%），再同时走塔身和气泡。
     */
    public static final RecipeSlotLayout DISTILLATION_TOWER = slots -> {
        var canvas = canvas(148, 96);
        place(canvas, slots.progress(DISTILLATION_TOWER_COIL, DOWN_TO_UP, 12, 18).setRange(0, 0.4), 19, 60);
        place(canvas, slots.progress(GuiTextures.PROGRESS_BAR_DISTILLATION_TOWER, LEFT_TO_RIGHT, 78, 76).setRange(0.4, 1), 32, 11);
        if (slots.count(IO.IN, FluidRecipeInfo.INSTANCE) > 0) {
            place(canvas, slots.slot(IO.IN, FluidRecipeInfo.INSTANCE, 0, GuiTextures.BEAKER_OVERLAY_1), 16, 39);
        }
        IGuiTexture[] beakers = { GuiTextures.BEAKER_OVERLAY_2, GuiTextures.BEAKER_OVERLAY_3, GuiTextures.BEAKER_OVERLAY_4 };
        int outputs = slots.count(IO.OUT, FluidRecipeInfo.INSTANCE);
        for (int i = 0; i < outputs; i++) {
            int column = i % 3, row = i / 3;
            place(canvas, slots.slot(IO.OUT, FluidRecipeInfo.INSTANCE, i, beakers[column]), 78 + column * UISizes.SLOT, 57 - row * UISizes.SLOT);
        }
        if (slots.count(IO.OUT, ItemRecipeInfo.INSTANCE) > 0) {
            place(canvas, slots.slot(IO.OUT, ItemRecipeInfo.INSTANCE, 0, GuiTextures.DUST_OVERLAY), 78, 75);
        }
        place(canvas, slots.progress(DISTILLATION_TOWER_BUBBLES, DOWN_TO_UP, 7, 32).setRange(0.4, 1), 21, 4);
        return canvas;
    };

    /** 锻造锤：输入 → 从上往下落的锤子（下面垫铁砧底座）→ 输出。蒸汽锻造锤用蒸汽版锤子和底座。 */
    public static final RecipeSlotLayout FORGE_HAMMER = slots -> {
        var canvas = singleInOut(slots, 92);
        IGuiTexture base = slots.isSteam() ? GuiTextures.PROGRESS_BAR_HAMMER_BASE_STEAM.get(slots.isHighPressure()) : GuiTextures.PROGRESS_BAR_HAMMER_BASE;
        image(canvas, base, 35, 20, 21, 6);
        place(canvas, slots.progress(), 36, 3);
        return canvas;
    };

    /** 车床：输入 → 车床进度（右端接夹头）→ 两个输出横排。 */
    public static final RecipeSlotLayout LATHE = slots -> {
        var canvas = singleInOut(slots, 110);
        place(canvas, slots.progress(), 36, 3);
        image(canvas, GuiTextures.PROGRESS_BAR_LATHE_BASE, 54, 4, 5, 18);
        return canvas;
    };

    /** 左边一个物品输入、右边从 x = 70 起横排的物品输出，高 26（槽上下各留 4）。 */
    private static UIElement singleInOut(RecipeSlots slots, int width) {
        var canvas = canvas(width, 26);
        var inputs = slots.slots(IO.IN, ItemRecipeInfo.INSTANCE);
        if (!inputs.isEmpty()) place(canvas, inputs.get(0), 4, 4);
        var outputs = slots.slots(IO.OUT, ItemRecipeInfo.INSTANCE);
        for (int i = 0; i < outputs.size(); i++) place(canvas, outputs.get(i), 70 + i * UISizes.SLOT, 4);
        return canvas;
    }

    /**
     * 研究站：左侧研究站底图，扫描物嵌在图中央；右侧研究对象；进度线从底图伸出、在对象槽下方穿过，
     * 最后转成向下的短箭头指向右下角的数据输出。前 75% 走横线，后 25% 走短箭头。
     */
    public static final RecipeSlotLayout RESEARCH_STATION = slots -> {
        var canvas = canvas(124, 68);
        place(canvas, slots.progress(GuiTextures.PROGRESS_BAR_RESEARCH_STATION_1, LEFT_TO_RIGHT, 54, 5).setRange(0, 0.75), 62, 28);
        place(canvas, slots.progress(GuiTextures.PROGRESS_BAR_RESEARCH_STATION_2, UP_TO_DOWN, 10, 16).setRange(0.75, 1), 109, 31);
        image(canvas, GuiTextures.PROGRESS_BAR_RESEARCH_STATION_BASE, 0, 0, 84, 60);
        int inputs = slots.count(IO.IN, ItemRecipeInfo.INSTANCE);
        if (inputs > 0) place(canvas, slots.slot(IO.IN, ItemRecipeInfo.INSTANCE, 0, GuiTextures.RESEARCH_STATION_OVERLAY), 87, 21);
        if (inputs > 1) place(canvas, slots.slot(IO.IN, ItemRecipeInfo.INSTANCE, 1, GuiTextures.SCANNER_OVERLAY), 33, 21);
        if (slots.count(IO.OUT, ItemRecipeInfo.INSTANCE) > 0) {
            place(canvas, slots.slot(IO.OUT, ItemRecipeInfo.INSTANCE, 0, GuiTextures.DATA_ORB_OVERLAY), 105, 50);
        }
        return canvas;
    };
}
