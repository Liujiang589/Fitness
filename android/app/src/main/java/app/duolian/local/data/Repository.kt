package app.duolian.local.data

import androidx.room.withTransaction
import app.duolian.local.domain.*
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString

class Repository(private val db: LocalDatabase) {
    private val dao = db.dao()
    val exercises = dao.exercises().distinctUntilChanged().map { rows -> rows.map { Codec.decodeFromString<Exercise>(it.payload) } }.flowOn(Dispatchers.Default)
    val plans = dao.plans().distinctUntilChanged().map { rows -> rows.map { Codec.decodeFromString<Plan>(it.payload) } }.flowOn(Dispatchers.Default)
    val sessions = dao.sessions().distinctUntilChanged().map { rows -> rows.map { Codec.decodeFromString<Session>(it.payload) } }.flowOn(Dispatchers.Default)
    val body = dao.body().distinctUntilChanged().map { rows -> rows.map { Codec.decodeFromString<Body>(it.payload) }.sortedByDescending { it.date } }.flowOn(Dispatchers.Default)
    val food = dao.food().distinctUntilChanged().map { rows -> rows.map { Codec.decodeFromString<Food>(it.payload) }.sortedByDescending { it.date } }.flowOn(Dispatchers.Default)
    val water = dao.water().distinctUntilChanged().map { rows -> rows.map { Codec.decodeFromString<Water>(it.payload) }.sortedByDescending { it.date } }.flowOn(Dispatchers.Default)

    suspend fun initialize() = db.withTransaction {
        if (dao.metadata("initialized") == null) {
            // Original descriptions written for this app; no imported private media or source.
            val basic = listOf(
                Exercise("bench", "杠铃卧推", "胸部", "杠铃", 40000, "肩胛保持稳定，双脚踩稳，以可控制的速度推举。"),
                Exercise("squat", "杠铃深蹲", "腿部", "杠铃", 40000, "保持躯干稳定，膝盖随脚尖方向移动，选择舒适的下蹲深度。"),
                Exercise("pulldown", "高位下拉", "背部", "器械", 30000, "避免借助身体大幅摆动，控制下拉和回程。"),
                Exercise("row", "坐姿划船", "背部", "器械", 25000),
                Exercise("press", "哑铃肩上推举", "肩部", "哑铃", 10000),
                Exercise("curl", "哑铃弯举", "手臂", "哑铃", 8000),
                Exercise("body-squat", "徒手深蹲", "腿部", "徒手", 0),
                Exercise("pushup", "俯卧撑", "胸部", "徒手", 0)
            )
            val extra = listOf(
                Triple("上斜杠铃卧推", "胸部", "杠铃"), Triple("哑铃卧推", "胸部", "哑铃"),
                Triple("哑铃飞鸟", "胸部", "哑铃"), Triple("绳索夹胸", "胸部", "绳索"),
                Triple("杠铃划船", "背部", "杠铃"), Triple("单臂哑铃划船", "背部", "哑铃"),
                Triple("引体向上", "背部", "徒手"), Triple("直臂下压", "背部", "绳索"),
                Triple("哑铃硬拉", "背部", "哑铃"), Triple("罗马尼亚硬拉", "腿部", "杠铃"),
                Triple("腿举", "腿部", "器械"), Triple("保加利亚分腿蹲", "腿部", "哑铃"),
                Triple("坐姿腿屈伸", "腿部", "器械"), Triple("俯卧腿弯举", "腿部", "器械"),
                Triple("臀桥", "腿部", "徒手"), Triple("哑铃侧平举", "肩部", "哑铃"),
                Triple("哑铃前平举", "肩部", "哑铃"), Triple("俯身哑铃飞鸟", "肩部", "哑铃"),
                Triple("绳索面拉", "肩部", "绳索"), Triple("阿诺德推举", "肩部", "哑铃"),
                Triple("锤式弯举", "手臂", "哑铃"), Triple("杠铃弯举", "手臂", "杠铃"),
                Triple("绳索弯举", "手臂", "绳索"), Triple("绳索下压", "手臂", "绳索"),
                Triple("绳索过顶臂屈伸", "手臂", "绳索"), Triple("仰卧杠铃臂屈伸", "手臂", "杠铃"),
                Triple("仰卧卷腹", "腹部", "徒手"), Triple("悬垂举腿", "腹部", "徒手"),
                Triple("反向卷腹", "腹部", "徒手"), Triple("俄罗斯转体", "腹部", "徒手"),
                Triple("站姿提踵", "小腿", "徒手"), Triple("坐姿提踵", "小腿", "器械")
            ).mapIndexed { i, (name, muscle, equipment) ->
                Exercise("basic-${i + 1}", name, muscle, equipment, 0,
                    "按实际训练填写重量与次数；可在编辑中补充个人动作要点。")
            }
            val seed = basic + extra
            seed.forEach { saveExercise(it) }
            // Keep the exercise library available, but let new users choose their own first plan.
            dao.put(MetaRow("initialized", "1"))
        }
    }
    suspend fun saveExercise(e: Exercise) { Rules.exercise(e); dao.put(ExerciseRow(e.id, Codec.encodeToString(e))) }
    suspend fun savePlan(p: Plan) = db.withTransaction {
        Rules.plan(p, allExercises()); dao.put(PlanRow(p.id, Codec.encodeToString(p)))
    }
    suspend fun deleteExercise(id: String) = db.withTransaction {
        require(allPlans().none { id in it.exerciseIds }) { "动作仍在计划中，请先从计划移除" }
        dao.deleteExercise(id)
    }
    suspend fun deletePlan(id: String) = dao.deletePlan(id)
    /** Creates a plan from a built-in template, adding any exercises it references. */
    suspend fun applyTemplate(t: PlanTemplate): Plan = db.withTransaction {
        val existing = allExercises()
        val ids = t.exercises.map { te ->
            existing.firstOrNull { it.name == te.name }?.id ?: run {
                val created = Exercise(name = te.name, muscle = te.muscle, equipment = te.equipment,
                    defaultGrams = te.grams)
                saveExercise(created)
                created.id
            }
        }
        val plan = Plan(name = t.name, exerciseIds = ids, sets = t.sets, reps = t.reps,
            scheme = t.scheme, superset = t.superset, weekdays = t.weekdays)
        savePlan(plan)
        plan
    }
    suspend fun start(planId: String, now: Long = System.currentTimeMillis()) = db.withTransaction {
        require(dao.active() == null) { "已有未结束训练，请先继续或结束" }
        val p = allPlans().first { it.id == planId }
        putSession(Rules.start(p, allExercises(), now))
    }
    private suspend fun putSession(s: Session) = dao.put(SessionRow(s.id, if (s.endedAt == null) 1 else null, Codec.encodeToString(s)))
    suspend fun changeActive(change: (Session) -> Session): Session = db.withTransaction {
        val before = dao.active()?.let { Codec.decodeFromString<Session>(it.payload) } ?: error("没有进行中的训练")
        val after = change(before)
        require(after.id == before.id)
        putSession(after)
        after
    }
    suspend fun updateSet(movementId: String, set: TrainingSet) = changeActive { s ->
        require(s.pausedAt == null) { "请先恢复训练" }; Rules.trainingSet(set)
        require(s.movements.any { it.id == movementId && it.sets.any { x -> x.id == set.id } })
        s.copy(movements = s.movements.map { m -> if (m.id != movementId) m else
            m.copy(sets = m.sets.map { if (it.id == set.id) set.copy(done = it.done) else it }) })
    }
    suspend fun addSet(movementId: String) = changeActive { s ->
        require(s.pausedAt == null) { "请先恢复训练" }
        s.copy(movements = s.movements.map { m -> if (m.id != movementId) m else {
            require(m.sets.size < 100) { "每个动作最多100组" }
            m.copy(sets = m.sets + (m.sets.lastOrNull()?.copy(id = newId(), done = false) ?: TrainingSet(grams = 0)))
        } })
    }
    /** Appends a drop set at 80% of the last working weight, keeping reps and RPE empty. */
    suspend fun addDropSet(movementId: String) = changeActive { s ->
        require(s.pausedAt == null) { "请先恢复训练" }
        s.copy(movements = s.movements.map { m -> if (m.id != movementId) m else {
            require(m.sets.size < 100) { "每个动作最多100组" }
            val last = m.sets.lastOrNull() ?: TrainingSet(grams = 0)
            val grams = Progression.roundToStep(last.grams * 4 / 5)
            m.copy(sets = m.sets + last.copy(id = newId(), grams = grams, done = false,
                rpe = null, restSeconds = null).withKind(SetKind.DROP))
        } })
    }
    suspend fun removeSet(movementId: String, setId: String) = changeActive { s ->
        require(s.pausedAt == null) { "请先恢复训练" }
        s.copy(movements = s.movements.map { m -> if (m.id != movementId) m else {
            require(m.sets.size > 1) { "每个动作至少保留一组" }; m.copy(sets = m.sets.filterNot { it.id == setId })
        } })
    }
    suspend fun deleteSession(id: String) = dao.deleteSession(id)
    /** HIIT history lives in the metadata table, so no Room schema migration is needed. */
    suspend fun hiitRuns(): List<HiitRun> =
        dao.metadata(KEY_HIIT_RUNS)?.let { Codec.decodeFromString<List<HiitRun>>(it) } ?: emptyList()
    suspend fun addHiitRun(r: HiitRun) {
        Rules.hiitRun(r)
        val runs = (hiitRuns() + r).takeLast(MAX_HIIT_RUNS)
        dao.put(MetaRow(KEY_HIIT_RUNS, Codec.encodeToString(runs)))
    }
    suspend fun saveBody(x: Body) { Rules.body(x); dao.put(BodyRow(x.id, Codec.encodeToString(x))) }
    suspend fun saveFood(x: Food) { Rules.food(x); dao.put(FoodRow(x.id, Codec.encodeToString(x))) }
    suspend fun saveWater(x: Water) { Rules.water(x); dao.put(WaterRow(x.id, Codec.encodeToString(x))) }
    suspend fun deleteBody(id: String) = dao.deleteBody(id)
    suspend fun deleteFood(id: String) = dao.deleteFood(id)
    suspend fun deleteWater(id: String) = dao.deleteWater(id)
    private suspend fun allExercises() = dao.allExercises().map { Codec.decodeFromString<Exercise>(it.payload) }
    private suspend fun allPlans() = dao.allPlans().map { Codec.decodeFromString<Plan>(it.payload) }
    suspend fun snapshot(rest: Int): Backup = db.withTransaction { Backup(
        exercises = allExercises(), plans = allPlans(),
        sessions = dao.allSessions().map { Codec.decodeFromString<Session>(it.payload) },
        body = dao.allBody().map { Codec.decodeFromString<Body>(it.payload) },
        food = dao.allFood().map { Codec.decodeFromString<Food>(it.payload) },
        water = dao.allWater().map { Codec.decodeFromString<Water>(it.payload) }, restSeconds = rest,
        hiitRuns = hiitRuns(), outdoorRuns = OutdoorStore(db).runs(), dailySteps = OutdoorStore(db).days()
    ) }
    suspend fun restore(b: Backup) {
        Rules.backup(b) // Complete validation BEFORE mutating anything.
        db.withTransaction {
            dao.clearSessions(); dao.clearPlans(); dao.clearExercises(); dao.clearBody(); dao.clearFood(); dao.clearWater()
            b.exercises.forEach { dao.put(ExerciseRow(it.id, Codec.encodeToString(it))) }
            b.plans.forEach { dao.put(PlanRow(it.id, Codec.encodeToString(it))) }
            b.sessions.reversed().forEach { putSession(it) }
            b.body.forEach { dao.put(BodyRow(it.id, Codec.encodeToString(it))) }
            b.food.forEach { dao.put(FoodRow(it.id, Codec.encodeToString(it))) }
            b.water.forEach { dao.put(WaterRow(it.id, Codec.encodeToString(it))) }
            dao.put(MetaRow("initialized", "1"))
            dao.put(MetaRow(KEY_HIIT_RUNS, Codec.encodeToString(b.hiitRuns.takeLast(MAX_HIIT_RUNS))))
            OutdoorStore(db).replace(b.outdoorRuns.map { r -> if (r.status == OutdoorStatus.ACTIVE)
                r.copy(status = OutdoorStatus.PAUSED, segment = r.segment + 1) else r }, b.dailySteps)
        }
    }
    private companion object {
        const val KEY_HIIT_RUNS = "hiit-runs"
        const val MAX_HIIT_RUNS = 200
    }
}
