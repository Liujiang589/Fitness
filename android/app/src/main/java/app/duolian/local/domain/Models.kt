package app.duolian.local.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()
val Codec = Json { encodeDefaults = true; ignoreUnknownKeys = false }

@Serializable data class Exercise(
    val id: String = newId(), val name: String, val muscle: String,
    val equipment: String, val defaultGrams: Long = 0, val notes: String = "",
    val favorite: Boolean = false, val mediaUri: String? = null
)
@Serializable data class Plan(
    val id: String = newId(), val name: String, val exerciseIds: List<String>,
    val sets: Int = 3, val reps: Int = 10,
    val scheme: SetScheme = SetScheme.STRAIGHT, val restSeconds: Int = 0,
    val superset: SupersetMode = SupersetMode.NONE,
    /** ISO weekdays (1 = Monday … 7 = Sunday) this plan is scheduled on. */
    val weekdays: List<Int> = emptyList()
)
@Serializable data class TrainingSet(
    val id: String = newId(), val grams: Long, val reps: Int = 10,
    val warmup: Boolean = false, val done: Boolean = false,
    val kind: SetKind = SetKind.WORK, val rpe: Int? = null, val restSeconds: Int? = null
) {
    /** Old payloads only carry [warmup]; new payloads keep both fields in sync. */
    val effectiveKind: SetKind get() = if (warmup && kind == SetKind.WORK) SetKind.WARMUP else kind
    val countsVolume: Boolean get() = done && !warmup && effectiveKind != SetKind.WARMUP
    fun withKind(k: SetKind): TrainingSet = copy(kind = k, warmup = k == SetKind.WARMUP)
}
@Serializable data class Movement(
    val id: String = newId(), val exerciseId: String, val name: String,
    val sets: List<TrainingSet>,
    val supersetGroup: Int? = null, val note: String = ""
)
@Serializable data class Session(
    val id: String = newId(), val title: String, val startedAt: Long,
    val endedAt: Long? = null, val pausedAt: Long? = null, val pausedMs: Long = 0,
    val restDeadline: Long? = null, val restRemainingMs: Long = 0,
    val movements: List<Movement>, val planId: String? = null, val note: String = ""
)
@Serializable data class Body(
    val id: String = newId(), val date: String = LocalDate.now().toString(),
    val grams: Long, val fatPercent: Double? = null
)
@Serializable data class Food(
    val id: String = newId(), val date: String = LocalDate.now().toString(),
    val meal: String, val name: String, val protein: Double, val carbs: Double, val fat: Double,
    val grams: Double? = null, val catalogId: String? = null, val nutritionSource: String? = null,
    val preparation: String? = null
) { val calories: Double get() = protein * 4 + carbs * 4 + fat * 9 }
@Serializable data class Water(
    val id: String = newId(), val date: String = LocalDate.now().toString(), val ml: Int
)
@Serializable data class Backup(
    val format: String = "duolian-native", val version: Int = 3,
    val exercises: List<Exercise>, val plans: List<Plan>, val sessions: List<Session>,
    val body: List<Body>, val food: List<Food>, val water: List<Water>, val restSeconds: Int = 60,
    val hiitRuns: List<HiitRun> = emptyList()
)

object Rules {
    fun exercise(x: Exercise) {
        id(x.id); label(x.name); label(x.muscle); label(x.equipment)
        require(x.defaultGrams in 0..1_000_000 && x.notes.length <= 2000) { "动作数值或说明超限" }
        require(x.mediaUri == null || x.mediaUri.length <= 2000) { "图片地址过长" }
    }
    fun plan(x: Plan, exercises: List<Exercise>) {
        id(x.id); label(x.name)
        require(x.sets in 1..100 && x.reps in 0..1000) { "组数或次数超限" }
        require(x.restSeconds in 0..600) { "组间休息需为0–600秒" }
        require(x.exerciseIds.size in 1..100 && x.exerciseIds.distinct().size == x.exerciseIds.size) { "至少选择一个动作，不能重复" }
        require(x.exerciseIds.all { k -> exercises.any { it.id == k } }) { "计划引用的动作不存在" }
        require(x.superset != SupersetMode.PAIRS || x.exerciseIds.size >= 2) { "超级组至少需要两个动作" }
        require(x.weekdays.all { it in 1..7 } && x.weekdays.distinct().size == x.weekdays.size) { "排期星期无效" }
    }
    fun trainingSet(x: TrainingSet) {
        id(x.id); require(x.grams in 0..1_000_000 && x.reps in 0..1000) { "重量需为0–1000kg，次数0–1000" }
        require(x.rpe == null || x.rpe in 5..10) { "RPE需为5–10" }
        require(x.restSeconds == null || x.restSeconds in 0..600) { "本组休息需为0–600秒" }
    }
    fun movement(x: Movement) {
        id(x.exerciseId); label(x.name); require(x.sets.isNotEmpty()) { "动作至少要有一组" }
        unique(x.sets.map { it.id }, 100); require(x.sets.size <= 100) { "每个动作最多100组" }
        require(x.supersetGroup == null || x.supersetGroup in 1..100) { "超级组编号无效" }
        require(x.note.length <= 500) { "动作备注最多500字" }
        x.sets.forEach(::trainingSet)
    }
    fun volumeKg(s: Session): Double = s.movements.sumOf { m ->
        m.sets.filter { it.countsVolume }.sumOf { it.grams * it.reps }
    } / 1000.0
    fun elapsedMs(s: Session, now: Long): Long =
        ((s.endedAt ?: s.pausedAt ?: now) - s.startedAt - s.pausedMs).coerceAtLeast(0)
    fun restMs(s: Session, now: Long): Long = if (s.pausedAt != null) s.restRemainingMs
        else ((s.restDeadline ?: now) - now).coerceAtLeast(0)
    fun pause(s: Session, now: Long): Session {
        require(s.endedAt == null) { "训练已结束" }
        if (s.pausedAt != null) return s
        return s.copy(pausedAt = now, restRemainingMs = restMs(s, now), restDeadline = null)
    }
    fun resume(s: Session, now: Long): Session {
        val at = s.pausedAt ?: return s
        return s.copy(pausedAt = null, pausedMs = s.pausedMs + (now - at).coerceAtLeast(0),
            restDeadline = if (s.restRemainingMs > 0) now + s.restRemainingMs else null, restRemainingMs = 0)
    }
    /**
     * Completing a set starts a rest window. Inside a superset the transition to the next
     * movement uses a shorter window and only the last movement of the group earns the full rest.
     */
    fun complete(s: Session, movementId: String, setId: String, seconds: Int, now: Long): Session {
        require(s.endedAt == null && s.pausedAt == null) { "请先恢复训练" }
        require(seconds in 0..600)
        val movement = s.movements.first { it.id == movementId }
        val target = movement.sets.first { it.id == setId }
        val rest = restAfter(s, movement, target, seconds)
        return s.copy(movements = s.movements.map { m -> if (m.id != movementId) m else m.copy(
            sets = m.sets.map { if (it.id == setId) it.copy(done = !it.done) else it }) },
            restDeadline = if (!target.done && rest > 0) now + rest * 1000L else null,
            restRemainingMs = 0)
    }
    /** Rest length for the set that just changed state: set override → superset transition → fallback. */
    fun restAfter(s: Session, m: Movement, set: TrainingSet, fallback: Int): Int {
        set.restSeconds?.let { return it }
        val group = m.supersetGroup ?: return fallback
        val members = s.movements.filter { it.supersetGroup == group }
        val position = members.indexOfFirst { it.id == m.id }
        return if (position in 0 until members.size - 1) minOf(fallback, SupersetRestSeconds) else fallback
    }
    fun finish(s: Session, now: Long): Session {
        require(s.endedAt == null && s.movements.any { m -> m.sets.any { it.done } }) { "请先完成至少一组" }
        val extraPause = s.pausedAt?.let { (now - it).coerceAtLeast(0) } ?: 0
        return s.copy(endedAt = now, pausedAt = null, pausedMs = s.pausedMs + extraPause,
            restDeadline = null, restRemainingMs = 0)
    }
    fun start(p: Plan, exercises: List<Exercise>, now: Long): Session {
        plan(p, exercises)
        val groups = Progression.supersetGroups(p.superset, p.exerciseIds.size)
        return Session(title = p.name, startedAt = now, planId = p.id,
            movements = p.exerciseIds.mapIndexed { index, key ->
                val e = exercises.first { it.id == key }
                Movement(exerciseId = key, name = e.name,
                    sets = Progression.schemeSets(p.scheme, e.defaultGrams, p.reps, p.sets),
                    supersetGroup = groups[index])
            })
    }
    fun body(x: Body) {
        id(x.id); date(x.date); require(x.grams in 20_000..500_000) { "体重需为20–500kg" }
        require(x.fatPercent == null || (x.fatPercent.isFinite() && x.fatPercent in 0.0..80.0)) { "体脂率需为0–80%" }
    }
    fun food(x: Food) {
        id(x.id); date(x.date); label(x.name)
        require(x.meal in listOf("早餐", "午餐", "晚餐", "加餐")) { "请选择有效餐次" }
        require(listOf(x.protein, x.carbs, x.fat).all { it.isFinite() && it in 0.0..1000.0 }) { "营养素需为0–1000g" }
        require(x.grams == null || (x.grams.isFinite() && x.grams in 0.1..3000.0)) { "食物重量需为0.1–3000g" }
        require(x.grams == null || x.protein + x.carbs + x.fat <= x.grams * 1.01) { "营养素合计不能超过食物重量，请检查标签单位" }
        require(x.catalogId == null || x.catalogId.matches(Regex("[a-zA-Z0-9_-]{1,80}"))) { "食物条目无效" }
        require(x.nutritionSource == null || x.nutritionSource.length <= 200) { "营养来源过长" }
        require(x.preparation == null || x.preparation.length <= 80) { "食物状态过长" }
    }
    fun water(x: Water) { id(x.id); date(x.date); require(x.ml in 1..10000) { "饮水量需为1–10000ml" } }
    fun hiitTemplate(t: HiitTemplate) {
        label(t.name)
        require(t.prepareSeconds in 0..60) { "准备时间需为0–60秒" }
        require(t.workSeconds in 5..600) { "训练时间需为5–600秒" }
        require(t.restSeconds in 0..600) { "休息时间需为0–600秒" }
        require(t.rounds in 1..100) { "轮数需为1–100" }
    }
    fun hiitRun(r: HiitRun) {
        label(r.name)
        require(r.startedAt in 1..MAX_TIME && r.endedAt in r.startedAt..MAX_TIME) { "时间戳无效" }
        require(r.completedRounds in 0..r.rounds) { "完成轮数无效" }
        require(r.workSeconds in 5..600 && r.restSeconds in 0..600 && r.rounds in 1..100) { "记录参数无效" }
    }
    fun backup(b: Backup) {
        require(b.format == "duolian-native" && b.version in 1..3) { "不兼容此备份格式；支持独练 v1 / v2 / v3" }
        require(b.restSeconds in 0..600)
        unique(b.exercises.map { it.id }, 1000); b.exercises.forEach(::exercise)
        unique(b.plans.map { it.id }, 1000); b.plans.forEach { plan(it, b.exercises) }
        unique(b.sessions.map { it.id }, 10000)
        require(b.sessions.count { it.endedAt == null } <= 1) { "备份中存在多个未结束训练" }
        b.sessions.forEach { s ->
            id(s.id); label(s.title); require(s.note.length <= 1000) { "训练备注最多1000字" }
            require(s.startedAt in 1..MAX_TIME && s.pausedMs in 0..MAX_TIME)
            require(s.endedAt == null || s.endedAt in s.startedAt..MAX_TIME)
            require(s.pausedAt == null || (s.endedAt == null && s.pausedAt in s.startedAt..MAX_TIME))
            require(s.pausedMs <= ((s.endedAt ?: s.pausedAt ?: MAX_TIME) - s.startedAt))
            require(s.restRemainingMs in 0..600000 && (s.restDeadline == null || s.restDeadline in 1..MAX_TIME))
            require(s.pausedAt == null || s.restDeadline == null)
            require(s.endedAt == null || (s.pausedAt == null && s.restDeadline == null && s.restRemainingMs == 0L))
            require(s.movements.isNotEmpty()); unique(s.movements.map { it.id }, 100)
            s.movements.forEach(::movement)
        }
        unique(b.body.map { it.id }, 20000); b.body.forEach(::body)
        unique(b.food.map { it.id }, 20000); b.food.forEach(::food)
        unique(b.water.map { it.id }, 20000); b.water.forEach(::water)
        unique(b.hiitRuns.map { it.id }, 5000); b.hiitRuns.forEach(::hiitRun)
    }
    fun parseKg(text: String): Long {
        val kg = text.toBigDecimalOrNull() ?: error("请输入有效重量")
        require(kg >= java.math.BigDecimal.ZERO && kg <= java.math.BigDecimal(1000)) { "重量需为0–1000kg" }
        return kg.multiply(java.math.BigDecimal(1000)).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact()
    }
    /** Short transition inside a superset, kept below the usual rest so the pair stays intense. */
    const val SupersetRestSeconds = 15
    private const val MAX_TIME = 10_000_000_000_000L
    private fun id(s: String) { require(s.matches(Regex("[a-zA-Z0-9_-]{1,80}"))) { "记录ID无效" } }
    private fun label(s: String) { require(s.isNotBlank() && s.length <= 100) { "名称必填，最多100字" } }
    private fun date(s: String) { require(runCatching { LocalDate.parse(s).toString() == s }.getOrDefault(false)) { "日期格式为YYYY-MM-DD且必须是有效日期" } }
    private fun unique(ids: List<String>, max: Int) { require(ids.size <= max && ids.distinct().size == ids.size) { "记录过多或ID重复" } }
}
