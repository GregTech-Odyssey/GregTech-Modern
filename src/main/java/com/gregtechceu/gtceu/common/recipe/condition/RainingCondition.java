package com.gregtechceu.gtceu.common.recipe.condition;

import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeCondition;

import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;

@Getter
public class RainingCondition extends RecipeCondition {

    public static final RainingCondition INSTANCE = new RainingCondition();
    private float level;

    public RainingCondition(boolean isReverse, float level) {
        super(isReverse);
        this.level = level;
    }

    public RainingCondition(float level) {
        this.level = level;
    }

    @Override
    public Component getTooltips() {
        return Component.translatable("recipe.condition.rain.tooltip", level);
    }

    @Override
    public boolean testCondition(@NotNull GTRecipe recipe, @NotNull RecipeLogic recipeLogic) {
        Level level = recipeLogic.machine.self().getLevel();
        return level != null && level.getRainLevel(1) >= this.level;
    }

    public RainingCondition() {}
}
