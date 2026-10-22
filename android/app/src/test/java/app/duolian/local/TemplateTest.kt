package app.duolian.local

import app.duolian.local.domain.*
import org.junit.Assert.*
import org.junit.Test

class TemplateTest {
    @Test fun everyTemplateIsUsable() {
        assertTrue(Templates.all.size >= 6)
        Templates.all.forEach { t ->
            assertTrue("名称不能为空: ${t.id}", t.name.isNotBlank())
            assertTrue("需要动作: ${t.id}", t.exercises.isNotEmpty())
            assertTrue("组数越界: ${t.id}", t.sets in 1..100)
            assertTrue("次数越界: ${t.id}", t.reps in 0..1000)
            assertTrue("排期越界: ${t.id}", t.weekdays.all { it in 1..7 })
            assertEquals("排期重复: ${t.id}", t.weekdays.distinct().size, t.weekdays.size)
            assertTrue("缺少说明: ${t.id}", t.subtitle.isNotBlank())
        }
    }

    @Test fun templateIdsAreUnique() {
        assertEquals(Templates.all.size, Templates.all.map { it.id }.distinct().size)
    }

    @Test fun templatesMapToValidPlans() {
        Templates.all.forEach { t ->
            val exercises = t.exercises.mapIndexed { i, te ->
                Exercise("e$i-${t.id}", te.name, te.muscle, te.equipment, te.grams)
            }
            val plan = Plan("p-${t.id}", t.name, exercises.map { it.id }, t.sets, t.reps,
                t.scheme, 0, t.superset, t.weekdays)
            Rules.plan(plan, exercises)
        }
    }

    @Test fun libraryGroupsByLevel() {
        val grouped = Templates.byLevel()
        assertEquals(Templates.all.size, grouped.values.sumOf { it.size })
        assertTrue(grouped.keys.containsAll(listOf("新手", "中高级")))
        assertTrue(Templates.levels().distinct().size == grouped.size)
    }

    @Test fun weekdayScheduleIsValidated() {
        val e = Exercise("e", "卧推", "胸部", "杠铃")
        try {
            Rules.plan(Plan("p", "测试", listOf("e"), weekdays = listOf(0)), listOf(e))
            fail("星期 0 应被拒绝")
        } catch (ex: IllegalArgumentException) {
            assertTrue(ex.message!!.contains("排期"))
        }
        try {
            Rules.plan(Plan("p2", "测试", listOf("e"), weekdays = listOf(1, 1)), listOf(e))
            fail("重复星期应被拒绝")
        } catch (ex: IllegalArgumentException) {
            assertTrue(ex.message!!.contains("排期"))
        }
        Rules.plan(Plan("p3", "测试", listOf("e"), weekdays = listOf(1, 3, 5)), listOf(e))
    }

    @Test fun scheduleSelectsTodaysPlans() {
        val monday = java.time.LocalDate.of(2026, 10, 19) // Monday
        val plans = listOf(
            Plan("a", "推", listOf("e"), weekdays = listOf(1, 4)),
            Plan("b", "拉", listOf("e"), weekdays = listOf(2, 5)),
            Plan("c", "腿", listOf("e"), weekdays = emptyList()))
        val dow = monday.dayOfWeek.value
        val today = plans.filter { dow in it.weekdays }
        assertEquals(listOf("a"), today.map { it.id })
    }
}
