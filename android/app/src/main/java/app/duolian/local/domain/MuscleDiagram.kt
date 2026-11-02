package app.duolian.local.domain

enum class BodySide { FRONT, BACK }
data class MuscleRegion(val part: MusclePart, val side: BodySide, val path: String, val mirrored: Boolean = true)

/** Original flat vector geometry, viewport 100 × 220. Illustrates regions, not activation intensity. */
object MuscleDiagram {
    const val outline = "M45 30 L45 35 Q37 36 31 41 Q27 46 25 56 L19 79 L15 101 Q10 111 14 116 Q18 118 20 110 L24 94 L30 79 L35 62 Q37 76 37 88 L34 107 Q32 126 34 148 L36 164 L34 192 L30 203 Q29 208 35 208 L43 207 L44 194 L46 167 L48 143 L50 117 L52 143 L54 167 L56 194 L57 207 L65 208 Q71 208 70 203 L66 192 L64 164 L66 148 Q68 126 66 107 L63 88 Q63 76 65 62 L70 79 L76 94 L80 110 Q82 118 86 116 Q90 111 85 101 L81 79 L75 56 Q73 46 69 41 Q63 36 55 35 L55 30 Z"
    val regions = listOf(
        MuscleRegion(MusclePart.PECTORAL, BodySide.FRONT, "M36 44 Q41 40 49 43 L49 61 Q42 65 35 58 Z"),
        MuscleRegion(MusclePart.DELTOID, BodySide.FRONT, "M31 44 Q34 40 37 42 L34 57 L27 59 Q28 49 31 44 Z"),
        MuscleRegion(MusclePart.DELTOID, BodySide.BACK, "M31 44 Q34 40 37 42 L34 57 L27 59 Q28 49 31 44 Z"),
        MuscleRegion(MusclePart.BICEPS, BodySide.FRONT, "M28 61 L33 61 L29 78 Q25 82 23 78 Z"),
        MuscleRegion(MusclePart.TRICEPS, BodySide.BACK, "M28 59 L33 60 L29 78 Q25 82 23 77 Z"),
        MuscleRegion(MusclePart.FOREARM, BodySide.FRONT, "M22 83 L27 83 L21 102 L17 104 Z"),
        MuscleRegion(MusclePart.FOREARM, BodySide.BACK, "M22 83 L27 83 L21 102 L17 104 Z"),
        MuscleRegion(MusclePart.ABDOMINAL, BodySide.FRONT, "M43 66 L49 66 L49 96 L43 94 Q40 82 43 66 Z"),
        MuscleRegion(MusclePart.QUADRICEPS, BodySide.FRONT, "M37 109 Q42 106 47 110 L44 143 Q42 155 38 153 Q35 137 37 109 Z"),
        MuscleRegion(MusclePart.TRAPEZIUS, BodySide.BACK, "M45 37 L49 39 L49 66 L43 58 L36 43 Z"),
        MuscleRegion(MusclePart.LAT, BodySide.BACK, "M36 59 L42 63 L46 70 L43 89 L38 83 Z"),
        MuscleRegion(MusclePart.ERECTOR, BodySide.BACK, "M46 69 L49 71 L49 98 L45 96 Z"),
        MuscleRegion(MusclePart.GLUTE, BodySide.BACK, "M37 98 Q42 94 49 99 L49 114 Q42 119 35 112 Z"),
        MuscleRegion(MusclePart.HAMSTRING, BodySide.BACK, "M36 119 Q41 121 47 117 L44 144 Q43 154 38 153 Z"),
        MuscleRegion(MusclePart.CALF, BodySide.BACK, "M38 165 L43 164 L42 183 L38 193 Q35 178 38 165 Z")
    )
    fun name(part: MusclePart): String = when (part) {
        MusclePart.PECTORAL -> "胸大肌"
        MusclePart.LAT -> "背阔肌"
        MusclePart.TRAPEZIUS -> "斜方肌"
        MusclePart.ERECTOR -> "竖脊肌"
        MusclePart.DELTOID -> "三角肌"
        MusclePart.BICEPS -> "肱二头肌"
        MusclePart.TRICEPS -> "肱三头肌"
        MusclePart.FOREARM -> "前臂肌群"
        MusclePart.ABDOMINAL -> "腹部肌群"
        MusclePart.GLUTE -> "臀肌"
        MusclePart.QUADRICEPS -> "股四头肌"
        MusclePart.HAMSTRING -> "腘绳肌"
        MusclePart.CALF -> "小腿肌群"
        MusclePart.SURFACE -> "身体轮廓"
        MusclePart.EQUIPMENT -> "器械"
    }
    fun names(parts: Set<MusclePart>) = parts.joinToString("、", transform = ::name)
}
