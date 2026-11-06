package com.gregtechceu.gtceu.common.capability;

import com.gregtechceu.gtceu.api.capability.IMedicalConditionTracker;
import com.gregtechceu.gtceu.api.data.medicalcondition.MedicalCondition;
import com.gregtechceu.gtceu.api.data.medicalcondition.MedicalConditionProgression;
import com.gregtechceu.gtceu.api.data.medicalcondition.Symptom;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.util.INBTSerializable;

import com.gto.fastcollection.fastutil.O2IOpenCacheHashMap;
import it.unimi.dsi.fastutil.objects.*;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

public class MedicalConditionTracker implements IMedicalConditionTracker, INBTSerializable<CompoundTag> {

    @Getter
    private final Reference2FloatOpenHashMap<MedicalCondition> medicalConditions = new Reference2FloatOpenHashMap<>();
    private final ReferenceOpenHashSet<MedicalCondition> permanentConditions = new ReferenceOpenHashSet<>();
    private final Reference2IntOpenHashMap<Symptom.ConfiguredSymptom> activeSymptoms = new Reference2IntOpenHashMap<>();
    private final O2IOpenCacheHashMap<MobEffect> activeMobEffects = new O2IOpenCacheHashMap<>();
    private final ReferenceOpenHashSet<MedicalCondition> flaggedForRemoval = new ReferenceOpenHashSet<>();
    @Setter
    private int maxAirSupply = -1;
    @Getter
    private final Player player;

    public MedicalConditionTracker(Player player) {
        this.player = player;
    }

    @Override
    public void tick() {
        if (player.isCreative() || player.isSpectator()) return;
        for (ObjectIterator<Object2IntMap.Entry<MobEffect>> it = activeMobEffects.object2IntEntrySet().fastIterator(); it.hasNext();) {
            var entry = it.next();
            player.addEffect(new MobEffectInstance(entry.getKey(), 100, entry.getIntValue()));
        }
        if (player.level().getGameTime() % 20 == 0) {
            // apply idle progression every second
            for (MedicalCondition condition : medicalConditions.keySet()) {
                if (condition.idleProgressionType == MedicalCondition.IdleProgressionType.NONE) {
                    continue;
                }
                if (permanentConditions.contains(condition) && condition.idleProgressionType == MedicalCondition.IdleProgressionType.HEAL) {
                    // can't automatically heal permanent conditions.
                    continue;
                }
                int multiplier = (condition.idleProgressionType == MedicalCondition.IdleProgressionType.HEAL) ? -1 : 1;
                float next = medicalConditions.getFloat(condition) + condition.idleProgressionRate * multiplier;
                medicalConditions.replace(condition, Math.min(next,
                        MedicalConditionProgression.capProgression(condition.maxProgression, condition.canBePermanent)));
                evaluateMedicalCondition(condition);
            }
            if (!medicalConditions.isEmpty()) {
                updateActiveSymptoms();
            }
        }
    }

    public void progressCondition(@NotNull MedicalCondition condition, float strength) {
        if (player.isCreative()) return;
        // 非有限值会穿透 Math.min（与 NaN 取小仍是 NaN）并写进存档；命令层由 Brigadier 拒绝，
        // 这里兜住 API 与旧存档传进来的 NaN/Infinity
        if (!Float.isFinite(strength) || strength == 0f) return;
        float cap = MedicalConditionProgression.capProgression(condition.maxProgression, condition.canBePermanent);
        medicalConditions.put(condition, Math.min(medicalConditions.getOrDefault(condition, 0) + strength, cap));
        updateActiveSymptoms();
    }

    private void updateActiveSymptoms() {
        // 先落地已标记的治愈/移除：否则本轮仍按旧档位施加症状，满进度的致死会抢在清理前击杀玩家
        flushFlaggedForRemoval();
        for (var it = medicalConditions.reference2FloatEntrySet().fastIterator(); it.hasNext();) {
            var entry = it.next();
            MedicalCondition condition = entry.getKey();
            if (condition.canBePermanent && entry.getFloatValue() >= condition.maxProgression * 2) {
                // If condition has been applied for 2x the maximum time, make it permanent.
                permanentConditions.add(condition);
            }
            for (Symptom.ConfiguredSymptom symptom : condition.symptoms) {
                int stage = calculateStage(condition, symptom, entry.getFloatValue());
                Symptom.ConfiguredSymptom currentSymptom = findActiveSymptom(symptom.symptom);
                if (stage > 0) {
                    // 每秒重试类效果（如致死）在档位不变时也要执行
                    symptom.symptom.tick(this, condition, symptom, stage);
                }
                if (currentSymptom == symptom) {
                    int currentStage = activeSymptoms.getOrDefault(symptom, 0);
                    if (stage == currentStage) {
                        continue;
                    }
                    // 档位升降都要重算：先归零再按新档施加，进度回落后不能残留更高档的减益
                    symptom.symptom.applyProgression(this, condition, symptom, 0);
                    if (stage <= 0) {
                        activeSymptoms.removeInt(symptom);
                    } else {
                        activeSymptoms.replace(symptom, stage);
                        symptom.symptom.applyProgression(this, condition, symptom, stage);
                    }
                    continue;
                }
                if (stage <= 0) {
                    continue;
                }
                if (currentSymptom == null) {
                    activeSymptoms.put(symptom, stage);
                    symptom.symptom.applyProgression(this, condition, symptom, stage);
                    continue;
                }
                int currentStage = activeSymptoms.getOrDefault(currentSymptom, 0);
                if (symptom.relativeHarshness * stage > currentSymptom.relativeHarshness * currentStage) {
                    currentSymptom.symptom.applyProgression(this, condition, symptom, currentStage);
                    activeSymptoms.removeInt(currentSymptom);
                    activeSymptoms.put(symptom, stage);
                    symptom.symptom.applyProgression(this, condition, symptom, stage);
                }
            }
        }
    }

    private void flushFlaggedForRemoval() {
        if (flaggedForRemoval.isEmpty()) {
            return;
        }
        for (MedicalCondition condition : flaggedForRemoval) {
            clearCondition(condition);
        }
        flaggedForRemoval.clear();
    }

    private Symptom.ConfiguredSymptom findActiveSymptom(Symptom symptom) {
        for (Symptom.ConfiguredSymptom active : activeSymptoms.keySet()) {
            if (active.symptom == symptom) {
                return active;
            }
        }
        return null;
    }

    /**
     * 移除疾病并清理它的全部症状状态。
     *
     * <p>
     * 档位记录必须一起清掉：{@link #activeSymptoms} 按基础症状全局记账，只清进度会让下一次感染
     * 卡在"阶段没有上升"，症状（含致死）从此不再施加。
     */
    private void clearCondition(MedicalCondition condition) {
        for (var it = activeSymptoms.keySet().iterator(); it.hasNext();) {
            Symptom.ConfiguredSymptom configuredSymptom = it.next();
            if (!condition.symptoms.contains(configuredSymptom)) {
                continue;
            }
            // reset all symptom effects for this condition
            configuredSymptom.symptom.applyProgression(this, condition, configuredSymptom, 0);
            it.remove();
        }
        medicalConditions.removeFloat(condition);
        permanentConditions.remove(condition);
    }

    @Override
    public void removeMedicalCondition(MedicalCondition condition) {
        flaggedForRemoval.add(condition);
        permanentConditions.remove(condition);
    }

    private int calculateStage(MedicalCondition condition, Symptom.ConfiguredSymptom symptom, float progression) {
        return MedicalConditionProgression.symptomStage(progression,
                condition.maxProgression, symptom.progressionThreshold, symptom.stages);
    }

    // removes MedicalConditions without progression
    private void evaluateMedicalCondition(MedicalCondition condition) {
        if (permanentConditions.contains(condition)) {
            return;
        }
        if (medicalConditions.getFloat(condition) <= 0) {
            removeMedicalCondition(condition);
        }
    }

    /**
     * called on antidote/cure consumption
     *
     * @param condition   MedicalCondition to heal
     * @param progression amount of progression to decrease
     */
    @Override
    public void heal(MedicalCondition condition, int progression) {
        if (progression >= medicalConditions.getOrDefault(condition, 0)) {
            clearCondition(condition);
            return;
        }
        medicalConditions.replace(condition, medicalConditions.getOrDefault(condition, 0) - progression);
    }

    @Override
    public int getMaxAirSupply() {
        return maxAirSupply;
    }

    @Override
    public void setMobEffect(MobEffect effect, int amplifier) {
        if (amplifier <= 0) {
            activeMobEffects.removeInt(effect);
        } else if (amplifier >= activeMobEffects.getOrDefault(effect, -1)) {
            activeMobEffects.put(effect, amplifier);
        }
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        ListTag effectsTag = new ListTag();
        for (var it = medicalConditions.reference2FloatEntrySet().fastIterator(); it.hasNext();) {
            var entry = it.next();
            CompoundTag medicalConditionTag = new CompoundTag();
            medicalConditionTag.putString("condition", entry.getKey().name);
            medicalConditionTag.putFloat("progression", entry.getFloatValue());
            effectsTag.add(medicalConditionTag);
        }
        tag.put("medical_conditions", effectsTag);
        ListTag permanentsTag = new ListTag();
        for (MedicalCondition condition : permanentConditions) {
            permanentsTag.add(StringTag.valueOf(condition.name));
        }
        tag.put("permanent_conditions", permanentsTag);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag arg) {
        ListTag medicalConditionsTag = arg.getList("medical_conditions", Tag.TAG_COMPOUND);
        for (int i = 0; i < medicalConditionsTag.size(); ++i) {
            CompoundTag compoundTag = medicalConditionsTag.getCompound(i);
            MedicalCondition condition = MedicalCondition.CONDITIONS.get(compoundTag.getString("condition"));
            if (condition == null) {
                continue;
            }
            // 旧存档可能留着超过上限的进度（曾在没有上限时累积），读档时按当前规则收回
            float progression = Math.min(compoundTag.getFloat("progression"),
                    MedicalConditionProgression.capProgression(condition.maxProgression, condition.canBePermanent));
            medicalConditions.put(condition, progression);
        }
        ListTag permanentConditionsTag = arg.getList("permanent_conditions", Tag.TAG_STRING);
        for (int i = 0; i < permanentConditionsTag.size(); ++i) {
            MedicalCondition condition = MedicalCondition.CONDITIONS.get(permanentConditionsTag.getString(i));
            // 旧版本会把任何疾病在 2 倍满进度后标记为永久，读档时按当前规则丢掉不该永久的那些
            if (condition != null && condition.canBePermanent) {
                permanentConditions.add(condition);
            }
        }
    }
}
