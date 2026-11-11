package com.gregtechceu.gtceu.api.machine.feature.multiblock;

import com.gregtechceu.gtceu.api.gui.fancy.ConfiguratorPanel;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyConfiguratorButton;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.IRecipeHandlerTrait;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableContentHandler;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.recipe.handler.IRecipeHandler;
import com.gregtechceu.gtceu.api.recipe.handler.RecipeHandlerUnit;
import com.gregtechceu.gtceu.uiwidgets.icon.WidgetIcons;
import com.gregtechceu.gtceu.utils.asm.EmptyMethodChecker;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import it.unimi.dsi.fastutil.objects.Reference2BooleanOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public interface IWorkableMultiPart extends IMultiPart {

    Reference2BooleanOpenHashMap<Class<?>> ON_WORKING_METHOD = new Reference2BooleanOpenHashMap<>();
    Reference2BooleanOpenHashMap<Class<?>> BEFORE_WORKING_METHOD = new Reference2BooleanOpenHashMap<>();
    Reference2BooleanOpenHashMap<Class<?>> AFTER_WORKING_METHOD = new Reference2BooleanOpenHashMap<>();
    Reference2BooleanOpenHashMap<Class<?>> MODIFY_RECIPE_METHOD = new Reference2BooleanOpenHashMap<>();

    static boolean hasOnWorkingMethod(IWorkableMultiPart part) {
        var c = part.getClass();
        return ON_WORKING_METHOD.computeIfAbsent(c, k -> {
            try {
                return EmptyMethodChecker.hasMethodBody(c.getMethod("onWorking", IWorkableMultiController.class));
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        });
    }

    static boolean hasBeforeWorkingMethod(IWorkableMultiPart part) {
        var c = part.getClass();
        return BEFORE_WORKING_METHOD.computeIfAbsent(c, k -> {
            try {
                return EmptyMethodChecker.hasMethodBody(c.getMethod("beforeWorking", IWorkableMultiController.class, RecipeHandlerUnit.class, GTRecipe.class));
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        });
    }

    static boolean hasAfterWorkingMethod(IWorkableMultiPart part) {
        var c = part.getClass();
        return AFTER_WORKING_METHOD.computeIfAbsent(c, k -> {
            try {
                return EmptyMethodChecker.hasMethodBody(c.getMethod("afterWorking", IWorkableMultiController.class));
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        });
    }

    static boolean hasModifyRecipeMethod(IWorkableMultiPart part) {
        var c = part.getClass();
        return MODIFY_RECIPE_METHOD.computeIfAbsent(c, k -> {
            try {
                return EmptyMethodChecker.hasMethodBody(c.getMethod("modifyRecipe", IWorkableMultiController.class, RecipeHandlerUnit.class, GTRecipe.class));
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        });
    }

    default void setAvailableRecipeTypes(@NotNull GTRecipeType[] types) {}

    //////////////////////////////////////
    // ***** Recipe Handler Switch ******//
    //////////////////////////////////////

    NotifiableContentHandler[] NO_RECIPE_CONTENT_HANDLERS = new NotifiableContentHandler[0];

    Component RECIPE_HANDLING_HEADER = Component.translatable("gtceu.machine.recipe_handling")
            .withStyle(ChatFormatting.YELLOW);
    Component RECIPE_HANDLING_ON = Component.translatable("gtceu.machine.recipe_handling.on")
            .withStyle(ChatFormatting.GREEN);
    Component RECIPE_HANDLING_OFF = Component.translatable("gtceu.machine.recipe_handling.off")
            .withStyle(ChatFormatting.RED);
    List<Component> RECIPE_HANDLING_ON_TOOLTIP = List.of(
            RECIPE_HANDLING_HEADER.copy().append(RECIPE_HANDLING_ON),
            Component.translatable("gtceu.machine.recipe_handling.on.desc").withStyle(ChatFormatting.GRAY));
    List<Component> RECIPE_HANDLING_OFF_TOOLTIP = List.of(
            RECIPE_HANDLING_HEADER.copy().append(RECIPE_HANDLING_OFF),
            Component.translatable("gtceu.machine.recipe_handling.off.desc").withStyle(ChatFormatting.GRAY));

    /**
     * 这个部件上真正作为配方仓使用的内容处理器：方向为输入/输出的物品仓、流体仓（含仓内电路槽）。
     * <p>
     * 方向为 {@link IO#NONE}（未挂载）或 {@link IO#BOTH}（不参与输入/输出分组）的处理器不算在内。
     */
    static boolean isRecipeContentHandler(NotifiableContentHandler handler) {
        var io = handler.getHandlerIO();
        return io == IO.IN || io == IO.OUT;
    }

    default NotifiableContentHandler[] getRecipeContentHandlers() {
        var handlers = self().getTraits(NotifiableContentHandler.class);
        if (handlers.length == 0) return NO_RECIPE_CONTENT_HANDLERS;
        var list = new ArrayList<NotifiableContentHandler>(handlers.length);
        for (var handler : handlers) {
            if (isRecipeContentHandler(handler)) list.add(handler);
        }
        return list.isEmpty() ? NO_RECIPE_CONTENT_HANDLERS : list.toArray(NO_RECIPE_CONTENT_HANDLERS);
    }

    default boolean hasRecipeContentHandlers() {
        for (var handler : self().getTraits(NotifiableContentHandler.class)) {
            if (isRecipeContentHandler(handler)) return true;
        }
        return false;
    }

    /** 部件的所有配方仓都处于开启状态时返回 {@code true}。 */
    default boolean isRecipeHandlingActive() {
        for (var handler : self().getTraits(NotifiableContentHandler.class)) {
            if (isRecipeContentHandler(handler) && !handler.isActive()) return false;
        }
        return true;
    }

    /**
     * 同时开关该部件上所有配方仓（主仓与电路槽）。覆盖板也走这里，因此界面按钮始终显示仓的真实状态。
     */
    default void setRecipeHandlingActive(boolean active) {
        for (var handler : self().getTraits(NotifiableContentHandler.class)) {
            if (isRecipeContentHandler(handler)) handler.setActive(active);
        }
    }

    /**
     * 给仓的配置栏挂上「配方处理」开关；没有配方仓的部件（能量仓、维护仓等）不会出现这个按钮。
     */
    default void attachRecipeHandlingConfigurator(ConfiguratorPanel configuratorPanel) {
        if (!hasRecipeContentHandlers()) return;
        configuratorPanel.attachConfigurators(new IFancyConfiguratorButton.Toggle(
                WidgetIcons.RECIPE_HANDLER_OFF, WidgetIcons.RECIPE_HANDLER_ON,
                this::isRecipeHandlingActive, (clickData, pressed) -> setRecipeHandlingActive(pressed))
                .setTooltipsSupplier(pressed -> pressed ? RECIPE_HANDLING_ON_TOOLTIP : RECIPE_HANDLING_OFF_TOOLTIP));
    }

    default boolean hasOnWorkingMethod() {
        return IWorkableMultiPart.hasOnWorkingMethod(this);
    }

    default boolean hasBeforeWorkingMethod() {
        return IWorkableMultiPart.hasBeforeWorkingMethod(this);
    }

    default boolean hasAfterWorkingMethod() {
        return IWorkableMultiPart.hasAfterWorkingMethod(this);
    }

    default boolean hasModifyRecipeMethod() {
        return IWorkableMultiPart.hasModifyRecipeMethod(this);
    }

    default List<RecipeHandlerUnit> getRecipeHandlers() {
        return Collections.singletonList(getHandlerUnit());
    }

    default RecipeHandlerUnit getHandlerUnit() {
        var list = getRecipeHandlerUnit();
        if (list == null) {
            List<IRecipeHandler> handlers = new ArrayList<>();
            IO handlerIO = null;
            for (var trait : self().getTraits(IRecipeHandlerTrait.class)) {
                if (trait.isAvailable() && trait.getHandlerIO() != IO.NONE) {
                    if (handlerIO == null) handlerIO = trait.getHandlerIO();
                    handlers.add(trait);
                }
            }

            if (handlers.isEmpty()) {
                list = RecipeHandlerUnit.NO_DATA;
                setRecipeHandlerUnit(list);
            } else {
                list = RecipeHandlerUnit.of(handlerIO, this, handlers);
                setRecipeHandlerUnit(list);
            }
        }
        return list;
    }

    /**
     * Called per tick in {@link RecipeLogic#handleRecipeWorking()}
     */
    default void onWorking(IWorkableMultiController controller) {}

    /**
     * Called per tick in {@link RecipeLogic#handleRecipeWorking()}
     */
    default void onWaiting(IWorkableMultiController controller) {}

    /**
     * Called in {@link WorkableMultiblockMachine#setWorkingEnabled(boolean)}
     */
    default void onPaused(IWorkableMultiController controller) {}

    /**
     * Called in {@link RecipeLogic#onRecipeFinish()} before outputs are produced
     */
    default void afterWorking(IWorkableMultiController controller) {}

    /**
     * Called in {@link RecipeLogic#setupRecipe(RecipeHandlerUnit,GTRecipe)}
     */
    default void beforeWorking(IWorkableMultiController controller, RecipeHandlerUnit unit, @NotNull GTRecipe recipe) {}

    /**
     * Override it to modify recipe on the fly e.g. applying overclock, change chance, etc
     *
     * @param recipe recipe from detected from GTRecipeType
     * @return modified recipe.
     *         null -- this recipe is unavailable
     */
    default @Nullable GTRecipe modifyRecipe(IWorkableMultiController controller, RecipeHandlerUnit unit, @NotNull GTRecipe recipe) {
        return recipe;
    }

    RecipeHandlerUnit getRecipeHandlerUnit();

    void setRecipeHandlerUnit(RecipeHandlerUnit list);
}
