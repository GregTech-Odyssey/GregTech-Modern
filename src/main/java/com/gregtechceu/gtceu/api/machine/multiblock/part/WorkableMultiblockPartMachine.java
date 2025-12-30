package com.gregtechceu.gtceu.api.machine.multiblock.part;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.IRecipeHandler;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IWorkableMultiController;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IWorkableMultiPart;
import com.gregtechceu.gtceu.api.machine.trait.IRecipeHandlerTrait;
import com.gregtechceu.gtceu.api.machine.trait.RecipeHandlerList;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.utils.asm.EmptyMethodChecker;

import net.minecraft.MethodsReturnNonnullByDefault;

import it.unimi.dsi.fastutil.objects.Reference2BooleanOpenHashMap;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class WorkableMultiblockPartMachine extends MultiblockPartMachine implements IWorkableMultiPart {

    public static final Reference2BooleanOpenHashMap<Class<?>> ON_WORKING_METHOD = new Reference2BooleanOpenHashMap<>();
    public static final Reference2BooleanOpenHashMap<Class<?>> BEFORE_WORKING_METHOD = new Reference2BooleanOpenHashMap<>();
    public static final Reference2BooleanOpenHashMap<Class<?>> AFTER_WORKING_METHOD = new Reference2BooleanOpenHashMap<>();
    public static final Reference2BooleanOpenHashMap<Class<?>> MODIFY_RECIPE_METHOD = new Reference2BooleanOpenHashMap<>();

    protected @Nullable RecipeHandlerList handlerList;

    public WorkableMultiblockPartMachine(MetaMachineBlockEntity holder) {
        super(holder);
    }

    @Override
    public boolean hasOnWorkingMethod() {
        var c = getClass();
        return ON_WORKING_METHOD.computeIfAbsent(c, k -> {
            try {
                return EmptyMethodChecker.hasMethodBody(c.getMethod("onWorking", IWorkableMultiController.class));
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public boolean hasBeforeWorkingMethod() {
        var c = getClass();
        return BEFORE_WORKING_METHOD.computeIfAbsent(c, k -> {
            try {
                return EmptyMethodChecker.hasMethodBody(c.getMethod("beforeWorking", IWorkableMultiController.class, GTRecipe.class));
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public boolean hasAfterWorkingMethod() {
        var c = getClass();
        return AFTER_WORKING_METHOD.computeIfAbsent(c, k -> {
            try {
                return EmptyMethodChecker.hasMethodBody(c.getMethod("afterWorking", IWorkableMultiController.class));
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public boolean hasModifyRecipeMethod() {
        var c = getClass();
        return MODIFY_RECIPE_METHOD.computeIfAbsent(c, k -> {
            try {
                return EmptyMethodChecker.hasMethodBody(c.getMethod("modifyRecipe", IWorkableMultiController.class, GTRecipe.class));
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public List<RecipeHandlerList> getRecipeHandlers() {
        return List.of(getHandlerList());
    }

    protected RecipeHandlerList getHandlerList() {
        if (handlerList == null) {
            List<IRecipeHandler<?>> handlers = new ArrayList<>();
            IO handlerIO = null;
            for (var trait : traits) {
                if (trait instanceof IRecipeHandlerTrait<?> rht && rht.isAvailable() && rht.getHandlerIO() != IO.NONE) {
                    if (handlerIO == null) handlerIO = rht.getHandlerIO();
                    handlers.add(rht);
                }
            }

            if (handlers.isEmpty()) {
                handlerList = RecipeHandlerList.NO_DATA;
            } else {
                handlerList = RecipeHandlerList.of(handlerIO, this, handlers);
            }
        }
        return handlerList;
    }
}
