package com.gregtechceu.gtceu.common.recipe.condition;

import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeCondition;
import com.gregtechceu.gtceu.common.machine.owner.FTBOwner;
import com.gregtechceu.gtceu.common.machine.owner.MachineOwner;

import net.minecraft.network.chat.Component;

import dev.ftb.mods.ftbquests.api.FTBQuestsAPI;
import dev.ftb.mods.ftbquests.quest.BaseQuestFile;
import dev.ftb.mods.ftbquests.quest.QuestObject;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;

public class FTBQuestCondition extends RecipeCondition {

    private static final Long2ObjectMap<QuestObject> QUEST_CACHE = new Long2ObjectOpenHashMap<>();
    private final long parsedQuestId;

    public FTBQuestCondition(boolean isReverse, long questId) {
        super(isReverse);
        this.parsedQuestId = questId;
    }

    private QuestObject getQuest() {
        return QUEST_CACHE.computeIfAbsent(parsedQuestId, id -> FTBQuestsAPI.api().getQuestFile(false).get(id));
    }

    @Override
    public Component getTooltips() {
        Component questTitle = getQuest().getTitle();
        if (isReverse) {
            return Component.translatable("recipe.condition.quest.not_completed.tooltip", questTitle);
        } else {
            return Component.translatable("recipe.condition.quest.completed.tooltip", questTitle);
        }
    }

    @Override
    public boolean testCondition(@NotNull GTRecipe recipe, @NotNull RecipeLogic recipeLogic) {
        MachineOwner owner = recipeLogic.machine.self().getOwner();
        if (!(owner instanceof FTBOwner ftbOwner)) return false;
        if (ftbOwner.getTeam() == null) return false;
        BaseQuestFile questFile = FTBQuestsAPI.api().getQuestFile(false);
        return questFile.getOrCreateTeamData(ftbOwner.getTeam()).isCompleted(getQuest());
    }
}
