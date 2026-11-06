package com.gregtechceu.gtceu.api.data.medicalcondition;

/**
 * {@link MedicalConditionProgression} 的确定性校验：只算数值，不启动游戏、不加载任何游戏类。
 *
 * <p>
 * 覆盖两件事：症状档位必须按配置在 0~满进度之间真的升档（旧公式把 {@code * stages} 放进分母，
 * 配置了 10 档的症状永远停在 0 档），以及只有可永久化的疾病才允许累积到满进度的 2 倍。
 *
 * <p>
 * 跑法：
 *
 * <pre>{@code
 * ./gradlew medicalConditionCheck
 * }</pre>
 */
public final class MedicalConditionProgressionCheck {

    private static int checks;
    private static int failures;

    private MedicalConditionProgressionCheck() {}

    public static void main(String[] args) {
        // 化学烧伤：max=200，唯一症状 WEAKNESS 走多档症状的默认阈值 0（档位随进度连续上升）
        stage("化学烧伤 0%", 0f, 200f, 0f, 10, 0);
        stage("化学烧伤 10%", 20f, 200f, 0f, 10, 1);
        stage("化学烧伤 99%", 198f, 200f, 0f, 10, 9);
        stage("化学烧伤 100%", 200f, 200f, 0f, 10, 10);
        stage("化学烧伤 200% 封顶后不再升档", 400f, 200f, 0f, 10, 10);

        // 致癌物：DEATH(1 档, 阈值 1) 必须在 100% 触发；HEALTH_DEBUFF(10 档, 阈值 .75)
        stage("致癌物 DEATH 99%", 19800f, 20000f, 1f, 1, 0);
        stage("致癌物 DEATH 100%", 20000f, 20000f, 1f, 1, 1);
        stage("致癌物 DEATH 150%", 30000f, 20000f, 1f, 1, 1);
        stage("致癌物 健康减益 70%", 14000f, 20000f, .75f, 10, 0);
        stage("致癌物 健康减益 75%", 15000f, 20000f, .75f, 10, 1);
        stage("致癌物 健康减益 100%", 20000f, 20000f, .75f, 10, 10);

        // 其余现有配置各取一条，确认阈值到满进度之间是分段线性
        stage("砷中毒 饥饿 20%", 200f, 1000f, .2f, 2, 1);
        stage("砷中毒 饥饿 100%", 1000f, 1000f, .2f, 2, 2);
        stage("一氧化碳 反胃 50%", 1000f, 2000f, .5f, 2, 1);
        stage("一氧化碳 反胃 100%", 2000f, 2000f, .5f, 2, 2);
        stage("甲醇 失明 25%", 125f, 500f, .25f, 2, 1);
        stage("石棉 饥饿 100%", 5000f, 5000f, .2f, 5, 5);

        // 非法与边界输入
        stage("零上限疾病", 10f, 0f, 1f, 10, 0);
        stage("进度为 0", 0f, 200f, 0f, 10, 0);
        stage("负进度", -5f, 200f, .5f, 10, 0);
        // 命令参数可以传 NaN（FloatArgumentType.floatArg(0) 拦不住），NaN 不能算出可用档位
        stage("NaN 进度", Float.NaN, 200f, 0f, 10, 0);
        stage("Infinity 进度按满进度封顶", Float.POSITIVE_INFINITY, 200f, 0f, 10, 10);
        stage("零档", 200f, 200f, 1f, 0, 0);
        stage("阈值 0 从第一点起步", 20f, 200f, 0f, 10, 1);
        stage("阈值 0 满进度到顶", 200f, 200f, 0f, 10, 10);

        cap("普通疾病封顶 100%", 200f, false, 200f);
        cap("可永久化疾病封顶 200%", 20000f, true, 40000f);
        cap("零上限疾病无法累积", 0f, true, 0f);

        // 解毒剂按满进度折算：花环 10% → 化学烧伤 20 / 致癌物 2000，且小病至少扣 1 点
        amount("花环对化学烧伤", 200f, 10, 20);
        amount("花环对致癌物", 20000f, 10, 2000);
        amount("小病种至少 1 点", 5f, 10, 1);
        amount("-1 走直接治愈不参与折算", 200f, -1, 0);
        amount("零上限疾病不折算", 0f, 10, 0);
        amount("NaN 上限不折算", Float.NaN, 10, 0);

        System.out.println("医学进度校验：" + checks + " 项，失败 " + failures);
        if (failures > 0) {
            System.exit(1);
        }
    }

    private static void stage(String name, float progression, float max, float threshold, int stages,
                              int expected) {
        checks++;
        int actual = MedicalConditionProgression.symptomStage(progression, max, threshold, stages);
        if (actual != expected) {
            failures++;
            System.out.println("FAIL 档位 " + name + "：期望 " + expected + "，实际 " + actual);
        }
    }

    private static void cap(String name, float max, boolean canBePermanent, float expected) {
        checks++;
        float actual = MedicalConditionProgression.capProgression(max, canBePermanent);
        if (actual != expected) {
            failures++;
            System.out.println("FAIL 上限 " + name + "：期望 " + expected + "，实际 " + actual);
        }
    }

    private static void amount(String name, float max, int percent, int expected) {
        checks++;
        int actual = MedicalConditionProgression.antidoteAmount(max, percent);
        if (actual != expected) {
            failures++;
            System.out.println("FAIL 解毒量 " + name + "：期望 " + expected + "，实际 " + actual);
        }
    }
}
