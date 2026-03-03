package com.gregtechceu.gtceu.api.recipe.content;

import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapabilityMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record ContentModifier(double multiplier) {

    public static final ContentModifier IDENTITY = new ContentModifier(1);

    public static ContentModifier multiplier(double multiplier) {
        return multiplier == 1 ? IDENTITY : new ContentModifier(multiplier);
    }

    public int apply(int number) {
        return (int) (number * multiplier);
    }

    public long apply(long number) {
        return (long) (number * multiplier);
    }

    public float apply(float number) {
        return (float) (number * multiplier);
    }

    public double apply(double number) {
        return number * multiplier;
    }

    /**
     * Applies this ContentModifier to all entries in the given Content map
     *
     * @param contents the content map to apply to
     * @return A new Content map that is the modified version of the argument
     */
    public RecipeCapabilityMap<List<Content>> copyContents(Map<RecipeCapability<?>, List<Content>> contents) {
        if (this == IDENTITY) return new RecipeCapabilityMap<>(contents);
        var copyContents = new RecipeCapabilityMap<List<Content>>();
        contents.forEach((cap, contentList) -> {
            if (contentList != null && !contentList.isEmpty()) {
                List<Content> contentsCopy = new ArrayList<>();
                for (Content content : contentList) {
                    contentsCopy.add(content.copy(cap, this));
                }
                copyContents.put(cap, contentsCopy);
            }
        });
        return copyContents;
    }

    public TickContentMap copyContents(TickContentMap contents) {
        var copyContents = new TickContentMap(contents);
        if (this == IDENTITY) return copyContents;
        copyContents.applyModifier(multiplier);
        return copyContents;
    }

    public void applyContents(Map<RecipeCapability<?>, List<Content>> contents) {
        for (Map.Entry<RecipeCapability<?>, List<Content>> entry : contents.entrySet()) {
            var contentList = entry.getValue();
            if (contentList != null && !contentList.isEmpty()) {
                List<Content> contentsCopy = new ArrayList<>(contentList.size());
                for (Content content : contentList) {
                    contentsCopy.add(content.copy(entry.getKey(), this));
                }
                entry.setValue(contentsCopy);
            }
        }
    }
}
