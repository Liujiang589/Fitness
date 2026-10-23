package app.duolian.local.domain

import java.time.LocalDate

enum class HomeMode { FIRST_PLAN, SCHEDULED, FREE_CHOICE, REST_DAY, COMPLETED }
data class HomeFocus(val mode: HomeMode, val plan: Plan?, val label: String, val title: String,
    val description: String, val action: String)

/** Presentation only. Never creates a plan, assigns training days or starts a session. */
object TrainingHome {
    fun focus(plans: List<Plan>, sessions: List<Session>, today: LocalDate): HomeFocus {
        if (plans.isEmpty()) return HomeFocus(HomeMode.FIRST_PLAN, null, "从你的选择开始", "动一动，\n感觉更好。",
            "选择想练的动作，安排自己的第一场训练。", "创建我的计划")
        val finished = sessions.filter { it.endedAt != null && Analytics.dateOf(it) == today }
        val completedIds = finished.mapNotNull { it.planId }.toSet()
        val scheduled = plans.filter { today.dayOfWeek.value in it.weekdays }
        val pending = scheduled.firstOrNull { it.id !in completedIds }
        if (pending != null) return HomeFocus(HomeMode.SCHEDULED, pending, "你的今日安排", pending.name,
            "计划已就绪，按自己的节奏逐组完成。", "开始这场训练")
        if (scheduled.isNotEmpty() || finished.isNotEmpty()) return HomeFocus(HomeMode.COMPLETED, null,
            "今天，已经有所积累", "练过了，\n也好好休息。", "训练足迹已经留下，接下来按身体状态安排。", "查看我的计划")
        val flexible = plans.firstOrNull { it.weekdays.isEmpty() }
        if (flexible != null) return HomeFocus(HomeMode.FREE_CHOICE, flexible, "自由安排的一天", flexible.name,
            "这份计划没有固定训练日，今天是否开始由你决定。", "按此计划开始")
        return HomeFocus(HomeMode.REST_DAY, null, "今天没有固定排期", "放松一点，\n也在向前。",
            "可以散步、休息，或到计划库选择适合今天的训练。", "选择其他计划")
    }
}
