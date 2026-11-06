package com.gregtechceu.gtceu.api.data.medicalcondition;

/**
 * 疾病进度与症状档位的纯数值规则：不引用任何游戏类，可脱离客户端/服务端直接校验。
 *
 * <p>
 * 进度以满进度 {@code maxProgression} 为 100%：可永久化的疾病允许累积到 200%（超过即标记为永久），
 * 其余疾病在 100% 封顶。症状档位从 {@code progressionThreshold}（满进度的比例）开始，
 * 到满进度时升到配置的 {@code stages} 档；档位 0 表示该症状尚未生效。
 */
public final class MedicalConditionProgression {

    private MedicalConditionProgression() {}

    /**
     * @return 允许累积到的最大进度：可永久化 200%，其余疾病 100%。
     */
    public static float capProgression(float maxProgression, boolean canBePermanent) {
        return canBePermanent ? maxProgression * 2 : maxProgression;
    }

    /**
     * @return 症状档位；0 表示未达到 {@code progressionThreshold}，已生效的效果应按 0 档移除。
     */
    public static int symptomStage(float progression, float maxProgression, float progressionThreshold, int stages) {
        if (maxProgression <= 0 || progression <= 0 || stages <= 0) {
            return 0;
        }
        float threshold = progressionThreshold * maxProgression;
        if (progression < threshold) {
            return 0;
        }
        float span = maxProgression - threshold;
        // 阈值即满进度（致死症状的阈值 1）：只有满进度才进入最高档，没有可分的区间
        if (span <= 0) {
            return stages;
        }
        // 超过满进度（可永久化疾病的 200%）不再升档，避免档位随进度无上限膨胀
        float ratio = (Math.min(progression, maxProgression) - threshold) / span;
        return 1 + (int) Math.floor(ratio * (stages - 1));
    }

    /**
     * @return 解毒剂每口按<b>满进度</b>折算的扣除量；{@code removePercent <= 0}（-1 表示直接治愈）时返回 0。
     */
    public static int antidoteAmount(float maxProgression, int removePercent) {
        if (maxProgression <= 0 || removePercent <= 0) {
            return 0;
        }
        // 至少扣 1 点：按当前值折算加截断会让小病永远清不掉尾巴
        return Math.max(1, Math.round(maxProgression * removePercent / 100.0f));
    }
}
