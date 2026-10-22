package app.duolian.local.domain

/** One exercise inside a built-in plan template. */
data class TemplateExercise(
    val name: String, val muscle: String, val equipment: String, val grams: Long = 0
)

/**
 * A ready-made plan shown in the template library. Templates only carry names and
 * training parameters; matching exercises are created on demand.
 */
data class PlanTemplate(
    val id: String, val name: String, val subtitle: String, val level: String,
    val sets: Int, val reps: Int,
    val scheme: SetScheme = SetScheme.STRAIGHT,
    val superset: SupersetMode = SupersetMode.NONE,
    val weekdays: List<Int> = emptyList(),
    val exercises: List<TemplateExercise>
)

object Templates {
    val all: List<PlanTemplate> = listOf(
        PlanTemplate("tpl-full-body", "全身基础", "新手 · 每周 3 练", "新手", 3, 10,
            weekdays = listOf(1, 3, 5),
            exercises = listOf(
                TemplateExercise("杠铃卧推", "胸部", "杠铃", 40000),
                TemplateExercise("杠铃深蹲", "腿部", "杠铃", 40000),
                TemplateExercise("高位下拉", "背部", "器械", 30000))),
        PlanTemplate("tpl-upper-lower", "上下肢分化", "中级 · 每周 4 练", "中高级", 4, 8,
            weekdays = listOf(1, 2, 4, 5),
            exercises = listOf(
                TemplateExercise("杠铃卧推", "胸部", "杠铃", 50000),
                TemplateExercise("坐姿划船", "背部", "器械", 35000),
                TemplateExercise("杠铃深蹲", "腿部", "杠铃", 60000),
                TemplateExercise("罗马尼亚硬拉", "腿部", "杠铃", 50000))),
        PlanTemplate("tpl-push", "推 · 胸肩三头", "三分化之一", "中高级", 3, 12,
            weekdays = listOf(1, 4),
            exercises = listOf(
                TemplateExercise("上斜杠铃卧推", "胸部", "杠铃", 40000),
                TemplateExercise("哑铃肩上推举", "肩部", "哑铃", 15000),
                TemplateExercise("绳索夹胸", "胸部", "绳索", 20000),
                TemplateExercise("绳索下压", "手臂", "绳索", 25000))),
        PlanTemplate("tpl-pull", "拉 · 背二头", "三分化之一", "中高级", 3, 12,
            weekdays = listOf(2, 5),
            exercises = listOf(
                TemplateExercise("引体向上", "背部", "徒手", 0),
                TemplateExercise("杠铃划船", "背部", "杠铃", 40000),
                TemplateExercise("哑铃弯举", "手臂", "哑铃", 10000),
                TemplateExercise("绳索面拉", "肩部", "绳索", 20000))),
        PlanTemplate("tpl-legs", "腿 · 股四臀", "三分化之一", "中高级", 3, 10,
            weekdays = listOf(3, 6),
            exercises = listOf(
                TemplateExercise("杠铃深蹲", "腿部", "杠铃", 60000),
                TemplateExercise("腿举", "腿部", "器械", 80000),
                TemplateExercise("俯卧腿弯举", "腿部", "器械", 30000),
                TemplateExercise("站姿提踵", "小腿", "徒手", 0))),
        PlanTemplate("tpl-bodyweight", "自重入门", "新手 · 无器械", "新手", 3, 12,
            weekdays = listOf(1, 3, 5),
            exercises = listOf(
                TemplateExercise("俯卧撑", "胸部", "徒手", 0),
                TemplateExercise("徒手深蹲", "腿部", "徒手", 0),
                TemplateExercise("臀桥", "腿部", "徒手", 0),
                TemplateExercise("仰卧卷腹", "腹部", "徒手", 0))),
        PlanTemplate("tpl-strength", "力量三项", "进阶 · 5×5", "偏高级", 5, 5,
            scheme = SetScheme.PYRAMID, weekdays = listOf(1, 4),
            exercises = listOf(
                TemplateExercise("杠铃深蹲", "腿部", "杠铃", 80000),
                TemplateExercise("杠铃卧推", "胸部", "杠铃", 60000),
                TemplateExercise("罗马尼亚硬拉", "腿部", "杠铃", 70000)))
    )

    fun levels(): List<String> = all.map { it.level }.distinct()

    fun byLevel(): Map<String, List<PlanTemplate>> = all.groupBy { it.level }

    fun find(id: String): PlanTemplate? = all.firstOrNull { it.id == id }
}
