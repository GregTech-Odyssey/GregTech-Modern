package com.gregtechceu.gtceu.api.capability.recipe;

import com.gregtechceu.gtceu.api.recipe.chance.logic.ChanceLogic;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.content.ContentModifier;
import com.gregtechceu.gtceu.api.recipe.content.SerializerLong;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class EURecipeCapability extends RecipeCapability<Long> {

    public final static EURecipeCapability CAP = new EURecipeCapability();

    protected EURecipeCapability() {
        super("eu", 0xFFFFFF00, false, 2, SerializerLong.INSTANCE);
    }

    @Override
    public Long copyInner(Long content) {
        return content;
    }

    @Override
    public Long copyWithModifier(Long content, ContentModifier modifier) {
        return modifier.apply(content);
    }

    /**
     * Creates a {@code List<Content>} with the specified EU
     * 
     * @param eu EU/t value to put in the Content
     * @return Singleton list of a new Content with the given EU value
     */
    public static List<Content> makeEUContent(Long eu) {
        return Collections.singletonList(
                new Content(eu, ChanceLogic.getMaxChancedValue(), 0));
    }

    /**
     * Puts an EU Singleton Content in the given content map
     * 
     * @param contents content map
     * @param eu       EU value to put inside content map
     */
    public static void putEUContent(Map<RecipeCapability<?>, List<Content>> contents, long eu) {
        contents.put(EURecipeCapability.CAP, makeEUContent(eu));
    }
}
