package app.duolian.local.domain

/** Text fields are parsed only on submission; invalid input never replaces a stored plan. */
data class PlanEditorInput(
    val id: String,
    val name: String,
    val exerciseIds: List<String>,
    val sets: String = "3",
    val reps: String = "10",
    val rest: String = "0",
    val scheme: SetScheme = SetScheme.STRAIGHT,
    val superset: SupersetMode = SupersetMode.NONE,
    val weekdays: List<Int> = emptyList()
) {
    fun toPlan(exercises: List<Exercise>): Plan {
        val title = name.trim()
        require(title.isNotEmpty()) { "给计划起个名字，再保存。" }
        require(title.length <= 100) { "计划名称请控制在100字以内。" }
        require(exerciseIds.isNotEmpty()) { "先选择至少一个训练动作。" }
        require(exerciseIds.size <= 100) { "一份计划最多选择100个动作。" }
        require(exerciseIds.distinct().size == exerciseIds.size) { "计划中有重复动作，请移除后重试。" }
        require(exerciseIds.all { id -> exercises.any { it.id == id } }) { "部分动作已被删除，请移除后重新选择。" }
        fun integer(value: String, range: IntRange, message: String): Int =
            value.trim().toIntOrNull()?.takeIf { it in range } ?: throw IllegalArgumentException(message)
        val count = integer(sets, 1..100, "组数请输入1–100的整数。")
        val repetitions = integer(reps, 0..1000, "次数请输入0–1000的整数。")
        val seconds = integer(rest, 0..600, "休息请输入0–600的整数；0表示沿用默认休息。")
        require(superset != SupersetMode.PAIRS || exerciseIds.size >= 2) { "两两超级组至少需要两个动作。" }
        return Plan(id, title, exerciseIds.toList(), count, repetitions, scheme, seconds,
            superset, weekdays.sorted()).also { Rules.plan(it, exercises) }
    }
}
