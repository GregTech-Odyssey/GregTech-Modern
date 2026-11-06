package com.gregtechceu.gtceu.api.data.medicalcondition;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.common.capability.MedicalConditionTracker;
import com.gregtechceu.gtceu.common.data.GTMobEffects;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.function.Supplier;

public class Symptom {

    public static final UUID SYMPTOM_HEALTH_DEBUFF_UUID = UUID.fromString("607aa6d9-a7e4-4919-9962-f007104c4be8");
    public static final UUID SYMPTOM_ATTACK_SPEED_DEBUFF_UUID = UUID.fromString("f2378ee6-3427-45b5-8440-4b797f7b664a");
    public static final UUID SYMPTOM_WEAKNESS_UUID = UUID.fromString("482e64e0-de77-49cd-b9bc-96b7e7eb16db");
    public static final UUID SYMPTOM_SLOWNESS_UUID = UUID.fromString("b3ac6b40-2d30-419f-9cac-5b2cf998ad72");

    public static final Symptom DEATH = new Symptom(defaultKey("death"), 1, 1,
            (tracker, condition, configuredSymptom, baseSymptom, modifier) -> kill(tracker, condition, modifier),
            (tracker, condition, configuredSymptom, baseSymptom, modifier) -> {
                // 已到最高档就每秒继续致死：图腾等一次性免死不该让满进度疾病永久失效
                if (tracker.getPlayer().level().getGameTime() % 20 == 0) {
                    kill(tracker, condition, modifier);
                }
            });
    // 默认阈值 0 表示档位随进度从 0 连续升到满进度的 defaultStages 档；只有 1 档症状（致死、凋零）
    // 默认阈值为 1，即只有满进度才生效。各疾病可用 ConfiguredSymptom 的阈值把起始点后移。
    public static final Symptom RANDOM_DAMAGE = new Symptom(defaultKey("random_damage"), 10, 0,
            (medicalConditionTracker, condition, configuredSymptom, baseSymptom, modifier) -> {},
            (medicalConditionTracker, condition, configuredSymptom, baseSymptom, modifier) -> {
                int stages = configuredSymptom != null ? configuredSymptom.stages : baseSymptom.defaultStages;
                if (modifier > 0 && GTValues.RNG.nextInt(stages * 500 / modifier) == 0) {
                    medicalConditionTracker.getPlayer().hurt(condition.getDamageSource(medicalConditionTracker), 0.5f);
                }
            });
    public static final Symptom HEALTH_DEBUFF = new Symptom(defaultKey("health_debuff"), 10, 0, 1,
            Attributes.MAX_HEALTH, SYMPTOM_HEALTH_DEBUFF_UUID);
    public static final Symptom ATTACK_SPEED_DEBUFF = new Symptom(defaultKey("attack_speed_debuff"), 10, 0, .2f,
            Attributes.ATTACK_SPEED, SYMPTOM_ATTACK_SPEED_DEBUFF_UUID);
    public static final Symptom WEAKNESS = new Symptom(defaultKey("weakness"), 10, 0, .1f, Attributes.ATTACK_DAMAGE,
            SYMPTOM_WEAKNESS_UUID);
    public static final Symptom SLOWNESS = new Symptom(defaultKey("slowness"), 7, 0, .005f, Attributes.MOVEMENT_SPEED,
            SYMPTOM_SLOWNESS_UUID);
    public static final Symptom AIR_SUPPLY_DEBUFF = new Symptom(defaultKey("air_supply_debuff"), 10, 0,
            (hazardEffectTracker, damageSource, configuredSymptom, baseSymptom, modifier) -> hazardEffectTracker
                    .setMaxAirSupply(300 - 10 * modifier));
    public static final Symptom BLINDNESS = new Symptom(defaultKey("blindness"), 10, 0, MobEffects.BLINDNESS);
    public static final Symptom NAUSEA = new Symptom(defaultKey("nausea"), 10, 0, MobEffects.CONFUSION);
    public static final Symptom MINING_FATIGUE = new Symptom(defaultKey("mining_fatigue"), 10, 0,
            MobEffects.DIG_SLOWDOWN);
    public static final Symptom WITHER = new Symptom(defaultKey("wither"), 1, 1,
            MobEffects.WITHER);
    public static final Symptom WEAK_POISONING = new Symptom(defaultKey("weak_poisoning"), 10,
            0, GTMobEffects.WEAK_POISON::get);
    public static final Symptom POISONING = new Symptom(defaultKey("poisoning"), 10,
            0, MobEffects.POISON);
    public static final Symptom HUNGER = new Symptom(defaultKey("hunger"), 5, 0, MobEffects.HUNGER);

    public final String name;
    public final int defaultStages;
    public final float defaultProgressionThreshold;

    // integer corresponds to symptom stage, if integer is 0 symptom effects should be removed
    private final Effect progressionEffect;
    private final Effect tickEffect;

    public Symptom(String name, int defaultStages, float defaultProgressionThreshold,
                   Effect progressionEffect, Effect tickEffect) {
        this.name = name;
        this.defaultStages = defaultStages;
        this.defaultProgressionThreshold = defaultProgressionThreshold;
        this.progressionEffect = progressionEffect;
        this.tickEffect = tickEffect;
    }

    public Symptom(String name, int defaultStages, float defaultProgressionThreshold, Effect progressionEffect) {
        this(name, defaultStages, defaultProgressionThreshold, progressionEffect,
                (tracker, condition, configuredSymptom, baseSymptom, amplifier) -> {});
    }

    /**
     * @param multiplier multiplier for Attribute modification
     * @param attribute  Attribute to modify
     * @param uuid       AttributeModifier UUID
     */
    public Symptom(String name, int defaultStages, float defaultProgressionThreshold, float multiplier,
                   Attribute attribute, UUID uuid) {
        this(name, defaultStages, defaultProgressionThreshold,
                ((medicalConditionTracker, $1, $2, $3, modifier) -> {
                    if (!medicalConditionTracker.getPlayer().getAttributes().hasAttribute(attribute)) {
                        return;
                    }
                    medicalConditionTracker.getPlayer().getAttribute(attribute).removeModifier(uuid);
                    if (modifier != 0) {
                        medicalConditionTracker.getPlayer().getAttribute(attribute).addPermanentModifier(
                                new AttributeModifier(uuid, name, -modifier * multiplier,
                                        AttributeModifier.Operation.ADDITION));
                    }
                    // re-set the health data value so the max health change is applied immediately
                    if (attribute == Attributes.MAX_HEALTH) {
                        medicalConditionTracker.getPlayer().setHealth(medicalConditionTracker.getPlayer().getHealth());
                    }
                }));
    }

    /**
     * @param mobEffect           MobEffect to apply
     * @param amplifierMultiplier amplifier added to MobEffect every progression
     */
    public Symptom(String name, int defaultStages, float defaultProgressionThreshold, MobEffect mobEffect,
                   int amplifierMultiplier) {
        this(name, defaultStages, defaultProgressionThreshold,
                (medicalConditionTracker, $1, $2, $3, modifier) -> medicalConditionTracker.setMobEffect(mobEffect,
                        amplifierMultiplier * modifier));
    }

    /**
     * @param mobEffect           MobEffect to apply
     * @param amplifierMultiplier amplifier added to MobEffect every progression
     */
    public Symptom(String name, int defaultStages, float defaultProgressionThreshold, Supplier<MobEffect> mobEffect,
                   int amplifierMultiplier) {
        this(name, defaultStages, defaultProgressionThreshold,
                (hazardEffectTracker, $1, $2, $3, modifier) -> hazardEffectTracker.setMobEffect(mobEffect.get(),
                        amplifierMultiplier * modifier));
    }

    /**
     * @param mobEffect MobEffect to apply
     */
    public Symptom(String name, int defaultStages, float defaultProgressionThreshold, MobEffect mobEffect) {
        this(name, defaultStages, defaultProgressionThreshold,
                (hazardEffectTracker, $1, $2, $3, modifier) -> hazardEffectTracker.setMobEffect(mobEffect, modifier));
    }

    /**
     * @param mobEffect MobEffect to apply
     */
    public Symptom(String name, int defaultStages, float defaultProgressionThreshold, Supplier<MobEffect> mobEffect) {
        this(name, defaultStages, defaultProgressionThreshold,
                (hazardEffectTracker, $1, $2, $3, modifier) -> hazardEffectTracker.setMobEffect(mobEffect.get(),
                        modifier));
    }

    public void applyProgression(MedicalConditionTracker subject, MedicalCondition condition,
                                 @Nullable ConfiguredSymptom symptom, int modifier) {
        progressionEffect.apply(subject, condition, symptom, this, modifier);
    }

    public void tick(MedicalConditionTracker subject, MedicalCondition condition,
                     @Nullable ConfiguredSymptom symptom, int modifier) {
        tickEffect.apply(subject, condition, symptom, this, modifier);
    }

    public static class ConfiguredSymptom {

        public final Symptom symptom;
        public final int stages;
        public final float progressionThreshold;
        public final float relativeHarshness;

        public ConfiguredSymptom(Symptom symptom, int stages, float progressionThreshold) {
            this.symptom = symptom;
            this.stages = stages;
            this.progressionThreshold = progressionThreshold;
            this.relativeHarshness = (float) stages / symptom.defaultStages;
        }

        public ConfiguredSymptom(Symptom symptom) {
            this(symptom, symptom.defaultStages, symptom.defaultProgressionThreshold);
        }

        public ConfiguredSymptom(Symptom symptom, int stages) {
            this(symptom, stages, symptom.defaultProgressionThreshold);
        }

        public ConfiguredSymptom(Symptom symptom, float progressionThreshold) {
            this(symptom, symptom.defaultStages, progressionThreshold);
        }
    }

    @FunctionalInterface
    public interface Effect {

        void apply(MedicalConditionTracker tracker, MedicalCondition condition,
                   @Nullable ConfiguredSymptom configuredSymptom, Symptom baseSymptom, int amplifier);
    }

    /**
     * 满进度的致死伤害：一次打掉当前生命的 20 倍，保证任何加成下都溢出致死
     * （此伤害类型带 {@code BYPASSES_ARMOR}，护甲与抗性不参与减免）。
     */
    private static void kill(MedicalConditionTracker tracker, MedicalCondition condition, int modifier) {
        if (modifier <= 0) {
            return;
        }
        Player player = tracker.getPlayer();
        if (player.isDeadOrDying()) {
            return;
        }
        player.hurt(condition.getDamageSource(tracker), Math.max(20f, player.getHealth() * 20));
    }

    private static String defaultKey(String name) {
        return "symptom.gtceu." + name;
    }
}
