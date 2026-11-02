package app.duolian.local.domain

import kotlin.math.*

data class FootFrame(val forward: MotionPoint, val up: MotionPoint) {
    val side = MotionPoint(forward.y*up.z-forward.z*up.y,
        forward.z*up.x-forward.x*up.z,forward.x*up.y-forward.y*up.x)
    fun map(p: MotionPoint) = forward*p.x + up*p.y + side*p.z
    fun ball(ankle: MotionPoint) = ankle + map(FootKinematics.restBall)
}

/** Absolute shoe frame: shin rotation must not twist a planted foot or change its length. */
object FootKinematics {
    // Ankle-to-ball and forefoot support point measured in the packaged CC0 mesh's rest space.
    val restBall = MotionPoint(.14218f,-.07130f)
    val forefootSupport = MotionPoint(.19f,-.096f)
    const val flatHeight = .098f
    fun frame(kind: MotionKind, left: Boolean, knee: MotionPoint, ankle: MotionPoint, amount: Float): FootFrame {
        val shin=(ankle-knee).unit()
        val pitch = when(kind) {
            MotionKind.LEG_EXTENSION, MotionKind.LEG_CURL, MotionKind.HANGING_RAISE,
            MotionKind.REVERSE_CRUNCH, MotionKind.PULLUP, MotionKind.SEATED_ROW -> atan2(shin.x,-shin.y)
            MotionKind.LEG_PRESS -> PI.toFloat()*.75f
            MotionKind.STANDING_CALF, MotionKind.SEATED_CALF -> -PI.toFloat()/6*amount
            MotionKind.PUSHUP -> -PI.toFloat()/3
            MotionKind.SPLIT_SQUAT -> if(left)0f else -PI.toFloat()*.75f
            MotionKind.BENCH, MotionKind.DB_BENCH, MotionKind.INCLINE_BENCH, MotionKind.FLY,
            MotionKind.LYING_TRICEPS, MotionKind.BRIDGE, MotionKind.CRUNCH, MotionKind.TWIST -> PI.toFloat()
            else -> 0f
        }
        // Supine planted soles face the floor while toes point away from the head.
        val supinePlanted=kind in setOf(MotionKind.BENCH,MotionKind.DB_BENCH,MotionKind.INCLINE_BENCH,
            MotionKind.FLY,MotionKind.LYING_TRICEPS,MotionKind.BRIDGE,MotionKind.CRUNCH,MotionKind.TWIST)
        if(supinePlanted)return FootFrame(MotionPoint(-1f,0f),MotionPoint(0f,1f))
        val yaw=if(kind in setOf(MotionKind.SQUAT,MotionKind.BODY_SQUAT,MotionKind.RDL,MotionKind.DB_DEADLIFT))
            (if(left)1 else -1)*.14f else 0f
        return FootFrame(MotionPoint(cos(pitch)*cos(yaw),sin(pitch),cos(pitch)*sin(yaw)),
            MotionPoint(-sin(pitch)*cos(yaw),cos(pitch),-sin(pitch)*sin(yaw)))
    }
    fun planted(kind: MotionKind,left: Boolean): Boolean = when(kind) {
        MotionKind.LEG_EXTENSION,MotionKind.LEG_CURL,MotionKind.HANGING_RAISE,MotionKind.REVERSE_CRUNCH,
        MotionKind.PULLUP,MotionKind.SEATED_ROW,MotionKind.LEG_PRESS,MotionKind.STANDING_CALF,
        MotionKind.SEATED_CALF,MotionKind.PUSHUP -> false
        MotionKind.SPLIT_SQUAT -> left
        else -> true
    }
    fun raisedAnkle(base: MotionPoint,amount: Float): MotionPoint {
        val angle=-PI.toFloat()/6*amount
        val foot=FootFrame(MotionPoint(cos(angle),sin(angle)),MotionPoint(-sin(angle),cos(angle)))
        return base + forefootSupport - foot.map(forefootSupport)
    }
}
