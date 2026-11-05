package com.wty.superaiagent.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.Locale;

/**
 * 23 计算工具：把健身领域里"必须算准"的几个指标交给代码计算，而不是让大模型心算。
 * <p>
 * 一、为什么要做成工具
 * 大模型算数是"生成"出来的，不是"计算"出来的：同一个 BMI，问两次可能给两个结果；
 * 让它算 TDEE 时还会把乘法顺序搞错。这类要求数字准确的场景，标准做法是提供工具，
 * 把公式写死在 Java 里，模型只负责"决定调用哪个工具、填什么参数、怎么解释结果"。
 * <p>
 * 二、包含五个工具
 * <ul>
 *     <li>{@link #calculateBmi}：BMI 及体重分级；</li>
 *     <li>{@link #calculateBmrAndTdee}：基础代谢率 BMR 与每日总消耗 TDEE；</li>
 *     <li>{@link #calculateOneRepMax}：单次最大重量 1RM 及对应训练重量；</li>
 *     <li>{@link #calculateTargetHeartRate}：有氧心率区间；</li>
 *     <li>{@link #calculateMacros}：每日蛋白/脂肪/碳水克数。</li>
 * </ul>
 * 五个方法是纯函数：只依赖入参，不读数据库、不调网络，因此毫秒级返回、可重复、无副作用。
 * <p>
 * 三、写法约定
 * <ul>
 *     <li>参数都用包装类型（{@code Double}、{@code Integer}、{@code Boolean}）而不是基本类型，
 *     这样模型漏传参数时能拿到 {@code null} 并给出"参数缺失"的提示，而不是直接算出一个 0；</li>
 *     <li>返回值统一是<b>中文文本</b>而不是数字：工具结果会原样进入模型上下文，
 *     带上计算过程和单位，模型才好直接复述给用户；</li>
 *     <li>每个方法先校验再计算，非法输入返回可读原因（工具方法不要抛异常，否则调用链会中断）。</li>
 * </ul>
 * <p>
 * 四、公式来源
 * <ul>
 *     <li>BMI：体重(kg) ÷ 身高(m)²，分级采用中国成人标准；</li>
 *     <li>BMR：Mifflin-St Jeor 公式，目前公认比 Harris-Benedict 更准；</li>
 *     <li>1RM：Epley 公式，简单且对 1~10 RM 范围误差可接受；</li>
 *     <li>心率：Karvonen（储备心率）法，比直接用最大心率百分比更贴合个人；</li>
 *     <li>宏量营养素：蛋白按体重给、脂肪按总热量 25%、剩余热量给碳水。</li>
 * </ul>
 * <p>
 * 注意：这些结果都是<b>估算值</b>，个体差异（体脂率、训练年限、代谢适应）会让实际值偏离，
 * 工具返回文本里也提醒了这一点，避免模型把估算当成精确结论。
 *
 * @author 王天一
 * @version 1.0
 */
public class CalculateTool {

    /** 每克蛋白质提供的热量（千卡） */
    private static final double KCAL_PER_GRAM_PROTEIN = 4.0;

    /** 每克碳水化合物提供的热量（千卡） */
    private static final double KCAL_PER_GRAM_CARB = 4.0;

    /** 每克脂肪提供的热量（千卡） */
    private static final double KCAL_PER_GRAM_FAT = 9.0;

    /**
     * 计算 BMI（身体质量指数）。
     *
     * @param weightKg 体重，单位千克
     * @param heightCm 身高，单位厘米
     * @return BMI 数值 + 所处区间 + 计算过程；参数非法时返回失败原因
     */
    @Tool(description = "计算 BMI（身体质量指数）。当需要判断用户偏瘦、正常、超重还是肥胖时使用。")
    public String calculateBmi(
            @ToolParam(description = "体重，单位千克，例如 70.5") Double weightKg,
            @ToolParam(description = "身高，单位厘米，例如 175") Double heightCm) {

        if (weightKg == null || heightCm == null) {
            return "计算失败：体重和身高都必须提供。";
        }
        if (weightKg <= 0 || heightCm <= 0) {
            return "计算失败：体重和身高必须大于 0。";
        }

        double heightM = heightCm / 100.0;
        double bmi = weightKg / (heightM * heightM);

        // 分级采用中国成人标准（与 WHO 国际标准的分界值不同，超重线是 24 而不是 25）
        String level;
        if (bmi < 18.5) {
            level = "偏瘦";
        } else if (bmi < 24) {
            level = "正常";
        } else if (bmi < 28) {
            level = "超重";
        } else {
            level = "肥胖";
        }

        return String.format(Locale.ROOT,
                "BMI = %.1f，属于「%s」。计算过程：%.1f kg ÷ (%.2f m)²。"
                        + "提示：BMI 不区分肌肉和脂肪，健身人群肌肉量大时 BMI 会偏高，"
                        + "建议结合体脂率一起判断。",
                bmi, level, weightKg, heightM);
    }

    /**
     * 计算基础代谢率 BMR 与每日总消耗 TDEE。
     *
     * @param weightKg      体重，单位千克
     * @param heightCm      身高，单位厘米
     * @param age           年龄，单位岁
     * @param male          true 表示男性，false 表示女性
     * @param activityLevel 活动水平：久坐 / 轻度 / 中度 / 高度 / 极高（也接受 sedentary/light/moderate/active/very_active）
     * @return BMR 与 TDEE（千卡/天）及计算过程；参数非法时返回失败原因
     */
    @Tool(description = "计算基础代谢率 BMR 和每日总消耗热量 TDEE（单位千卡）。"
            + "当需要给出每日热量摄入建议、制定增肌或减脂饮食计划时使用。")
    public String calculateBmrAndTdee(
            @ToolParam(description = "体重，单位千克，例如 70.5") Double weightKg,
            @ToolParam(description = "身高，单位厘米，例如 175") Double heightCm,
            @ToolParam(description = "年龄，单位岁，例如 25") Integer age,
            @ToolParam(description = "是否男性：true 男性，false 女性") Boolean male,
            @ToolParam(description = "活动水平，可选：久坐、轻度、中度、高度、极高") String activityLevel) {

        if (weightKg == null || heightCm == null || age == null || male == null) {
            return "计算失败：体重、身高、年龄、性别都必须提供。";
        }
        if (weightKg <= 0 || heightCm <= 0 || age <= 0) {
            return "计算失败：体重、身高、年龄必须大于 0。";
        }

        // Mifflin-St Jeor：男性 +5，女性 -161，其余项相同
        double bmr = 10 * weightKg + 6.25 * heightCm - 5 * age + (male ? 5 : -161);

        // 活动系数：把"日常活动量"折算成 BMR 的倍数
        double factor = activityFactor(activityLevel);
        double tdee = bmr * factor;

        // 给出三个常用目标下的热量参考：减脂、维持、增肌
        double cut = tdee - 500;        // 每日 500 千卡缺口，约每周减 0.45 kg
        double bulk = tdee + 300;       // 每日 300 千卡盈余，尽量少长脂肪

        return String.format(Locale.ROOT,
                "基础代谢 BMR = %.0f 千卡/天，每日总消耗 TDEE = %.0f 千卡/天（活动系数 %.3f）。"
                        + "参考摄入：减脂约 %.0f 千卡/天，维持约 %.0f 千卡/天，增肌约 %.0f 千卡/天。"
                        + "提示：BMR 用 Mifflin-St Jeor 公式估算，实际值受体脂率、代谢适应影响。",
                bmr, tdee, factor, cut, tdee, bulk);
    }

    /**
     * 计算单次最大重量 1RM，并给出常用训练重量。
     *
     * @param weightKg 本次举起的重量，单位千克
     * @param reps     本次完成的次数（1~15 次之间较准）
     * @return 1RM 与 95%/90%/85%/80%/75% 对应的重量；参数非法时返回失败原因
     */
    @Tool(description = "根据已完成的重量和次数估算单次最大重量 1RM，并给出各百分比对应的训练重量。"
            + "当用户想知道自己深蹲、卧推、硬拉的极限重量或安排训练强度时使用。")
    public String calculateOneRepMax(
            @ToolParam(description = "本次举起的重量，单位千克，例如 60") Double weightKg,
            @ToolParam(description = "本次完成的次数，例如 8") Integer reps) {

        if (weightKg == null || reps == null) {
            return "计算失败：重量和次数都必须提供。";
        }
        if (weightKg <= 0 || reps <= 0) {
            return "计算失败：重量和次数必须大于 0。";
        }
        if (reps == 1) {
            // 只做 1 次本来就是最大重量，不需要估算
            return String.format(Locale.ROOT, "1RM = %.1f kg（本次即为单次最大重量）。", weightKg);
        }
        if (reps > 15) {
            return "计算失败：次数超过 15 次时估算误差过大，请提供 1~15 次的数据。";
        }

        // Epley 公式：1RM = 重量 × (1 + 次数 ÷ 30)
        double oneRepMax = weightKg * (1 + reps / 30.0);

        // 各百分比对应重量，用于安排不同训练目标：85% 以上练力量，70~80% 练肌肥大
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT, "1RM ≈ %.1f kg（Epley 公式：%.1f × (1 + %d ÷ 30)）。%n",
                oneRepMax, weightKg, reps));
        sb.append("常用训练重量参考：");
        for (int percent : new int[]{95, 90, 85, 80, 75, 70}) {
            sb.append(String.format(Locale.ROOT, "%d%% = %.1f kg；", percent, oneRepMax * percent / 100.0));
        }
        sb.append("（85% 以上偏力量，70~80% 偏肌肥大。）");
        return sb.toString();
    }

    /**
     * 计算有氧运动的目标心率区间。
     *
     * @param age             年龄，单位岁
     * @param restingHeartRate 静息心率，单位次/分；不传则按最大心率百分比估算
     * @return 燃脂、有氧、无氧三个区间的心率范围；参数非法时返回失败原因
     */
    @Tool(description = "计算有氧运动的目标心率区间（燃脂/有氧/无氧三档）。"
            + "当用户询问跑步、骑车等有氧运动该保持什么心率时使用。")
    public String calculateTargetHeartRate(
            @ToolParam(description = "年龄，单位岁，例如 25") Integer age,
            @ToolParam(description = "静息心率（次/分），不知道可以不传", required = false) Integer restingHeartRate) {

        if (age == null || age <= 0) {
            return "计算失败：请提供有效的年龄。";
        }

        // 最大心率估算：220 - 年龄（个体误差可达 ±10~12 次）
        int maxHeartRate = 220 - age;

        // 有静息心率时用 Karvonen 法：目标心率 = (最大心率 - 静息心率) × 强度 + 静息心率
        // 没有静息心率时只能用最大心率百分比
        boolean useKarvonen = restingHeartRate != null && restingHeartRate > 0;
        double resting = useKarvonen ? restingHeartRate : 0;

        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT, "最大心率估算 = %d 次/分（220 - %d）。", maxHeartRate, age));
        sb.append(useKarvonen ? "已使用 Karvonen 储备心率法计算。" : "未提供静息心率，按最大心率百分比计算。");

        // 三档强度：60~70% 燃脂为主，70~80% 提升有氧能力，80~90% 提高无氧耐力
        int[][] zones = {{60, 70}, {70, 80}, {80, 90}};
        String[] names = {"燃脂区", "有氧区", "无氧区"};

        for (int i = 0; i < zones.length; i++) {
            int low;
            int high;
            if (useKarvonen) {
                low = (int) Math.round((maxHeartRate - resting) * zones[i][0] / 100.0 + resting);
                high = (int) Math.round((maxHeartRate - resting) * zones[i][1] / 100.0 + resting);
            } else {
                low = (int) Math.round(maxHeartRate * zones[i][0] / 100.0);
                high = (int) Math.round(maxHeartRate * zones[i][1] / 100.0);
            }
            sb.append(String.format(Locale.ROOT, "%s（%d%%~%d%%）：%d~%d 次/分；",
                    names[i], zones[i][0], zones[i][1], low, high));
        }
        return sb.toString();
    }

    /**
     * 计算每日蛋白质、脂肪、碳水化合物的克数。
     *
     * @param dailyCalories 每日目标热量，单位千卡
     * @param weightKg      体重，单位千克（用于按体重定蛋白质）
     * @param goal          目标：减脂 / 增肌 / 维持（也接受 cut/bulk/maintain）
     * @return 三类营养素的克数与对应热量；参数非法时返回失败原因
     */
    @Tool(description = "根据每日目标热量和体重，计算蛋白质、脂肪、碳水化合物的克数。"
            + "当用户需要具体到「每天吃多少克」的饮食方案时使用。")
    public String calculateMacros(
            @ToolParam(description = "每日目标热量，单位千卡，例如 2200") Double dailyCalories,
            @ToolParam(description = "体重，单位千克，例如 70") Double weightKg,
            @ToolParam(description = "目标：减脂、增肌或维持") String goal) {

        if (dailyCalories == null || weightKg == null) {
            return "计算失败：每日热量和体重都必须提供。";
        }
        if (dailyCalories <= 0 || weightKg <= 0) {
            return "计算失败：每日热量和体重必须大于 0。";
        }

        // 蛋白质按体重给：减脂期提高蛋白可以更好地保留肌肉，增肌期略低一些，维持居中
        String normalized = goal == null ? "" : goal.trim().toLowerCase(Locale.ROOT);
        double proteinPerKg;
        String goalName;
        if (normalized.contains("减") || normalized.contains("cut") || normalized.contains("lose")) {
            proteinPerKg = 2.0;
            goalName = "减脂";
        } else if (normalized.contains("增") || normalized.contains("bulk") || normalized.contains("gain")) {
            proteinPerKg = 1.8;
            goalName = "增肌";
        } else {
            proteinPerKg = 1.6;
            goalName = "维持";
        }

        double proteinGram = weightKg * proteinPerKg;
        double proteinKcal = proteinGram * KCAL_PER_GRAM_PROTEIN;

        // 脂肪固定占总热量的 25%：太低会影响激素水平，太高会挤占碳水
        double fatKcal = dailyCalories * 0.25;
        double fatGram = fatKcal / KCAL_PER_GRAM_FAT;

        // 剩下的热量全部给碳水。不足时说明热量设得太低，给出提醒而不是算出负数
        double carbKcal = dailyCalories - proteinKcal - fatKcal;
        if (carbKcal <= 0) {
            return String.format(Locale.ROOT,
                    "计算失败：每日 %.0f 千卡不足以同时满足 %.0f kg 体重的蛋白质和脂肪需求，"
                            + "请提高目标热量（建议不低于 %.0f 千卡）。",
                    dailyCalories, weightKg, proteinKcal + fatKcal);
        }
        double carbGram = carbKcal / KCAL_PER_GRAM_CARB;

        return String.format(Locale.ROOT,
                "按每日 %.0f 千卡、目标「%s」计算：蛋白质 %.0f g（%.0f 千卡，%.1f g/kg 体重）；"
                        + "脂肪 %.0f g（%.0f 千卡，占总热量 25%%）；"
                        + "碳水 %.0f g（%.0f 千卡，剩余热量）。"
                        + "提示：蛋白质按体重给足，脂肪不宜低于总热量 20%%，剩余热量给碳水供训练使用。",
                dailyCalories, goalName,
                proteinGram, proteinKcal, proteinPerKg,
                fatGram, fatKcal,
                carbGram, carbKcal);
    }

    /**
     * 把活动水平描述换算成活动系数。
     * <p>
     * 同时接受中文和英文写法，因为模型可能用中文填参数，也可能用英文枚举；
     * 认不出来时按"轻度活动"处理，避免算出明显偏离的结果。
     *
     * @param activityLevel 活动水平描述，可为空
     * @return 活动系数，取值范围 1.2 ~ 1.9
     */
    private double activityFactor(String activityLevel) {
        String level = activityLevel == null ? "" : activityLevel.trim().toLowerCase(Locale.ROOT);
        if (level.contains("久坐") || level.contains("sedentary")) {
            return 1.2;
        }
        if (level.contains("极高") || level.contains("very")) {
            return 1.9;
        }
        if (level.contains("高度") || level.contains("active")) {
            return 1.725;
        }
        if (level.contains("中度") || level.contains("moderate")) {
            return 1.55;
        }
        // 轻度或无匹配
        return 1.375;
    }
}
