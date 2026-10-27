package app.duolian.local.domain

/** A delayed notification or confirmation must never act on a different workout. */
object OutdoorSessionGuard {
    fun requireCurrent(run: OutdoorRun?, expectedId: String?) {
        check(run != null && run.status != OutdoorStatus.FINISHED) { "没有可操作的运动，请返回查看当前状态" }
        check(expectedId == null || expectedId == run.id) { "运动记录已变化，请重新操作当前运动" }
    }
}
