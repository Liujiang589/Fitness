package app.duolian.local.domain

import kotlin.math.*

data class MotionPoint(val x: Float, val y: Float, val z: Float = 0f) {
    operator fun plus(b: MotionPoint) = MotionPoint(x+b.x,y+b.y,z+b.z)
    operator fun minus(b: MotionPoint) = MotionPoint(x-b.x,y-b.y,z-b.z)
    operator fun times(value: Float) = MotionPoint(x*value,y*value,z*value)
    fun length() = sqrt(x*x+y*y+z*z)
    fun unit(): MotionPoint = if (length()<.00001f) MotionPoint(0f,1f,0f) else this*(1f/length())
    fun dot(b: MotionPoint) = x*b.x+y*b.y+z*b.z
}
enum class MotionJoint {
    HEAD, NECK, CHEST, PELVIS, SHOULDER_L, SHOULDER_R, ELBOW_L, ELBOW_R,
    WRIST_L, WRIST_R, HIP_L, HIP_R, KNEE_L, KNEE_R, ANKLE_L, ANKLE_R, TOE_L, TOE_R
}
enum class MotionPropKind { BENCH, BARBELL, DUMBBELL, CABLE, ROPE, RAIL, PAD, MAT, BAR, ROLLER }
data class MotionProp(val kind: MotionPropKind, val a: MotionPoint, val b: MotionPoint)
data class MotionPose(val joints: Map<MotionJoint, MotionPoint>, val props: List<MotionProp>) {
    operator fun get(joint: MotionJoint) = joints.getValue(joint)
}

/** Anatomical-length two-bone IK. Pure Kotlin, shared by every native frame and pose checks. */
object MotionEngine {
    fun amount(progress: Float): Float {
        val p = progress.coerceIn(0f,1f)
        val raw = when {
            p < .08f -> 0f
            p < .40f -> (p-.08f)/.32f
            p < .52f -> 1f
            p < .95f -> 1f-(p-.52f)/.43f
            else -> 0f
        }
        return raw*raw*(3f-2f*raw)
    }
    fun phase(progress: Float) = when { progress < .08f -> 0; progress < .52f -> 1; else -> 2 }
    private fun p(x: Number, y: Number, z: Number = 0f) = MotionPoint(x.toFloat(),y.toFloat(),z.toFloat())
    private fun mix(a: Float, b: Float, t: Float) = a+(b-a)*t
    private fun mix(a: MotionPoint,b: MotionPoint,t: Float) = a+(b-a)*t
    private fun cross(a: MotionPoint,b: MotionPoint) = p(a.y*b.z-a.z*b.y,a.z*b.x-a.x*b.z,a.x*b.y-a.y*b.x)
    private fun ik(a: MotionPoint, target: MotionPoint, upper: Float, lower: Float,
        pole: MotionPoint): Pair<MotionPoint,MotionPoint> {
        val delta = target-a
        val distance = delta.length().coerceIn(abs(upper-lower)+.001f,upper+lower-.001f)
        val direction = delta.unit()
        val end = a+direction*distance
        val projected = pole-a-direction*((pole-a).dot(direction))
        val perpendicular = if (projected.length()>.001f) projected.unit()
            else cross(direction,if(abs(direction.z)<.9f)p(0,0,1) else p(1,0,0)).unit()
        val along = (upper*upper-lower*lower+distance*distance)/(2f*distance)
        val height = sqrt((upper*upper-along*along).coerceAtLeast(0f))
        return (a+direction*along+perpendicular*height) to end
    }

    fun sample(kind: MotionKind, position: Float): MotionPose {
        val t = position.coerceIn(0f,1f)
        val props = mutableListOf<MotionProp>()
        var hip = p(0,.96f)
        var shoulder = p(0,1.56f)
        var torsoYaw = 0f
        var handL = p(.04f,.90f,.29f); var handR = p(.04f,.90f,-.29f)
        var footL = p(.05f,.08f,.22f); var footR = p(.05f,.08f,-.22f)
        var toeL: MotionPoint? = null; var toeR: MotionPoint? = null
        var elbowL: MotionPoint? = null; var elbowR: MotionPoint? = null
        var kneeL: MotionPoint? = null; var kneeR: MotionPoint? = null
        var armPoleL = p(.35f,1.2f,.8f); var armPoleR = p(.35f,1.2f,-.8f)
        var legPoleL = p(1,.5f,.3f); var legPoleR = p(1,.5f,-.3f)
        var dumbbell = false; var barbell = false; var oneDumbbell = false; var hammer = false
        var cable: MotionPoint? = null
        fun bench(a: MotionPoint,b: MotionPoint) { props += MotionProp(MotionPropKind.BENCH,a,b) }
        fun mat() { props += MotionProp(MotionPropKind.MAT,p(-1.2f,.015f),p(1,.015f)) }
        when (kind) {
            MotionKind.BENCH, MotionKind.DB_BENCH, MotionKind.INCLINE_BENCH, MotionKind.FLY,
            MotionKind.LYING_TRICEPS -> {
                val incline = kind == MotionKind.INCLINE_BENCH
                hip = p(-.32f,.69f)
                shoulder = if(incline)p(.19f,1.04f) else p(.30f,.74f)
                if(incline) {
                    bench(p(-.65f,.61f),p(-.32f,.61f))
                    bench(p(-.32f,.61f),p(.43f,1.13f))
                } else bench(p(-.65f,.61f),p(.62f,.61f))
                footL=p(-.68f,.08f,.30f);footR=p(-.68f,.08f,-.30f)
                legPoleL=p(-.20f,.75f,.32f);legPoleR=p(-.20f,.75f,-.32f)
                if(kind==MotionKind.FLY) {
                    val angle=mix(-8f,78f,t)*PI.toFloat()/180f
                    elbowL=shoulder+p(0,.34f*cos(angle),.21f+.34f*sin(angle))
                    elbowR=elbowL.copy(z=-elbowL.z)
                    handL=elbowL+p(.10f,.325f*cos(angle),.325f*sin(angle))
                    handR=handL.copy(z=-handL.z)
                    dumbbell=true
                } else if(kind==MotionKind.LYING_TRICEPS) {
                    elbowL=p(.31f,1.08f,.21f);elbowR=p(.31f,1.08f,-.21f)
                    val angle=mix(-35f,86f,t)*PI.toFloat()/180f
                    handL=elbowL+p(cos(angle)*.34f,sin(angle)*.34f,0)
                    handR=elbowR+p(cos(angle)*.34f,sin(angle)*.34f,0)
                    barbell=true
                } else {
                    val bottom=shoulder+p(.03f,.23f,.39f)
                    val top=shoulder+p(.02f,.66f,.25f)
                    handL=mix(bottom,top,t)
                    handR=handL.copy(z=-handL.z)
                    armPoleL=shoulder+p(-.13f,-.12f,.85f);armPoleR=armPoleL.copy(z=-armPoleL.z)
                    barbell=kind!=MotionKind.DB_BENCH;dumbbell=!barbell
                }
            }
            MotionKind.SQUAT, MotionKind.BODY_SQUAT, MotionKind.SPLIT_SQUAT -> {
                val split=kind==MotionKind.SPLIT_SQUAT
                hip=mix(p(0,.96f),if(split)p(-.10f,.56f) else p(-.39f,.55f),t)
                shoulder=hip+if(split)p(.02f,.60f) else mix(p(.02f,.60f),p(.31f,.52f),t)
                if(split) {
                    footL=p(.25f,.08f,.19f);footR=p(-.61f,.47f,-.19f)
                    legPoleR=p(.10f,.03f,-.2f)
                    bench(p(-.95f,.4f),p(-.45f,.4f))
                    handL=shoulder+p(.03f,-.65f,.30f);handR=handL.copy(z=-.30f)
                    dumbbell=true
                } else if(kind==MotionKind.BODY_SQUAT) {
                    handL=shoulder+p(.61f,-.08f,.22f);handR=handL.copy(z=-.22f)
                    armPoleL=shoulder+p(.45f,.04f,.4f);armPoleR=armPoleL.copy(z=-.4f)
                } else {
                    handL=shoulder+p(-.08f,.025f,.47f);handR=handL.copy(z=-.47f)
                    armPoleL=shoulder+p(-.35f,-.4f,.6f);armPoleR=armPoleL.copy(z=-.6f)
                    barbell=true
                }
            }
            MotionKind.RDL, MotionKind.DB_DEADLIFT -> {
                val rdl=kind==MotionKind.RDL
                hip=mix(p(0,.96f),if(rdl)p(-.43f,.81f) else p(-.30f,.61f),t)
                shoulder=hip+mix(p(.02f,.60f),if(rdl)p(.52f,.29f) else p(.40f,.48f),t)
                handL=shoulder+p(.13f,-.64f,.29f);handR=handL.copy(z=-.29f)
                armPoleL=shoulder+p(.25f,-.3f,.4f);armPoleR=armPoleL.copy(z=-.4f)
                barbell=rdl;dumbbell=!rdl
            }
            MotionKind.ROW, MotionKind.REVERSE_FLY, MotionKind.ONE_ARM_ROW -> {
                hip=p(-.32f,.88f);shoulder=p(.20f,1.18f)
                if(kind==MotionKind.REVERSE_FLY) {
                    val angle=mix(0f,84f,t)*PI.toFloat()/180f
                    elbowL=shoulder+p(.035f*cos(angle),-.338f*cos(angle),.21f+.34f*sin(angle))
                    elbowR=elbowL.copy(z=-elbowL.z)
                    handL=elbowL+p(.10f,-.325f*cos(angle),.325f*sin(angle))
                    handR=handL.copy(z=-handL.z)
                    dumbbell=true
                } else {
                    handL=mix(p(.40f,.57f,.28f),p(.03f,1.02f,.28f),t)
                    handR=handL.copy(z=-.28f)
                    armPoleL=p(-.5f,1.12f,.5f);armPoleR=armPoleL.copy(z=-.5f)
                    if(kind==MotionKind.ONE_ARM_ROW) {
                        bench(p(-.20f,.60f,.46f),p(.75f,.60f,.46f))
                        handL=p(.54f,.68f,.46f)
                        footL=p(-.25f,.08f,.5f);footR=p(.16f,.08f,-.34f)
                        oneDumbbell=true
                    } else barbell=true
                }
            }
            MotionKind.PULLDOWN, MotionKind.SEATED_ROW -> {
                hip=p(-.28f,.61f);shoulder=p(-.27f,1.22f)
                bench(p(-.56f,.53f),p(.08f,.53f))
                footL=p(.18f,.08f,.25f);footR=footL.copy(z=-.25f)
                if(kind==MotionKind.PULLDOWN) {
                    handL=mix(p(-.27f,1.87f,.44f),p(-.04f,1.29f,.44f),t)
                    handR=handL.copy(z=-.44f)
                    armPoleL=p(-.35f,1.13f,.8f);armPoleR=armPoleL.copy(z=-.8f)
                    cable=p(-.27f,2.25f)
                } else {
                    footL=p(.47f,.20f,.22f);footR=footL.copy(z=-.22f)
                    handL=mix(p(.36f,1.01f,.16f),p(-.04f,.99f,.16f),t);handR=handL.copy(z=-.16f)
                    armPoleL=p(-.7f,1.02f,.5f);armPoleR=armPoleL.copy(z=-.5f)
                    cable=p(.95f,.90f)
                }
            }
            MotionKind.PULLUP, MotionKind.HANGING_RAISE -> {
                val pull=kind==MotionKind.PULLUP
                hip=p(0,if(pull)mix(.818f,1.40f,t) else 1.01f)
                shoulder=hip+p(0,.60f)
                val barHeight=if(pull)2.08f else 2.27f
                handL=p(0,barHeight-.02f,.42f);handR=handL.copy(z=-.42f)
                armPoleL=shoulder+p(.08f,-.2f,.85f);armPoleR=armPoleL.copy(z=-.85f)
                props+=MotionProp(MotionPropKind.BAR,p(0,barHeight,-.78f),p(0,barHeight,.78f))
                if(pull) {
                    footL=hip+p(-.15f,-.72f,.18f);footR=footL.copy(z=-.18f)
                } else {
                    val angle=mix(-90f,-5f,t)*PI.toFloat()/180f
                    kneeL=hip+p(cos(angle)*.45f,sin(angle)*.45f,.16f);kneeR=kneeL.copy(z=-.16f)
                    footL=hip+p(cos(angle)*.90f,sin(angle)*.90f,.17f);footR=footL.copy(z=-.17f)
                }
            }
            MotionKind.SHOULDER_PRESS, MotionKind.ARNOLD -> {
                hip=p(-.14f,.62f);shoulder=p(-.14f,1.23f)
                bench(p(-.45f,.53f),p(.17f,.53f))
                footL=p(.14f,.08f,.25f);footR=footL.copy(z=-.25f)
                if(kind==MotionKind.ARNOLD && t<.4f) {
                    handL=mix(p(.18f,1.37f,.17f),p(-.12f,1.45f,.49f),t/.4f)
                } else {
                    val u=if(kind==MotionKind.ARNOLD)(t-.4f)/.6f else t
                    handL=mix(p(-.12f,1.45f,.49f),p(-.12f,1.90f,.24f),u)
                }
                handR=handL.copy(z=-handL.z)
                armPoleL=p(-.17f,1.20f,.9f);armPoleR=armPoleL.copy(z=-.9f)
                dumbbell=true
            }
            MotionKind.CURL, MotionKind.HAMMER_CURL, MotionKind.BARBELL_CURL,
            MotionKind.CABLE_CURL, MotionKind.PUSHDOWN -> {
                elbowL=p(.025f,1.23f,.23f);elbowR=elbowL.copy(z=-.23f)
                val angle=if(kind==MotionKind.PUSHDOWN)mix(15f,-85f,t) else mix(-85f,70f,t)
                val radians=angle*PI.toFloat()/180f
                handL=elbowL+p(cos(radians)*.34f,sin(radians)*.34f)
                handR=handL.copy(z=-.23f)
                dumbbell=kind==MotionKind.CURL||kind==MotionKind.HAMMER_CURL
                hammer=kind==MotionKind.HAMMER_CURL
                barbell=kind==MotionKind.BARBELL_CURL
                if(kind==MotionKind.CABLE_CURL)cable=p(.85f,.05f)
                if(kind==MotionKind.PUSHDOWN)cable=p(.65f,2.15f)
            }
            MotionKind.LATERAL_RAISE, MotionKind.FRONT_RAISE, MotionKind.CABLE_FLY,
            MotionKind.STRAIGHT_PULLDOWN, MotionKind.FACE_PULL, MotionKind.OVERHEAD_TRICEPS -> {
                when(kind) {
                    MotionKind.LATERAL_RAISE -> {
                        val angle=mix(0f,84f,t)*PI.toFloat()/180f
                        elbowL=shoulder+p(0,-.34f*cos(angle),.21f+.34f*sin(angle))
                        elbowR=elbowL.copy(z=-elbowL.z)
                        handL=elbowL+p(.10f,-.325f*cos(angle),.325f*sin(angle))
                        dumbbell=true
                    }
                    MotionKind.FRONT_RAISE -> {
                        val angle=mix(0f,84f,t)*PI.toFloat()/180f
                        elbowL=shoulder+p(.34f*sin(angle),-.34f*cos(angle),.21f)
                        elbowR=elbowL.copy(z=-elbowL.z)
                        handL=elbowL+p(.325f*sin(angle),-.325f*cos(angle),.10f)
                        dumbbell=true
                    }
                    MotionKind.CABLE_FLY -> {
                        val angle=mix(75f,-9f,t)*PI.toFloat()/180f
                        val upper=p(cos(angle)*.95f,-.20f,sin(angle)*.95f).unit()
                        val lower=p(cos(angle-.17f)*.95f,-.23f,sin(angle-.17f)*.95f).unit()
                        elbowL=shoulder+p(0,0,.21f)+upper*.34f
                        elbowR=elbowL.copy(z=-elbowL.z)
                        handL=elbowL+lower*.34f
                    }
                    MotionKind.STRAIGHT_PULLDOWN -> {
                        hip=p(-.15f,.95f);shoulder=p(.04f,1.52f)
                        val angle=mix(30f,-78f,t)*PI.toFloat()/180f
                        elbowL=shoulder+p(.34f*cos(angle),.34f*sin(angle),.21f)
                        elbowR=elbowL.copy(z=-elbowL.z)
                        handL=elbowL+p(.34f*cos(angle-.17f),.34f*sin(angle-.17f),0)
                        cable=p(.88f,2.12f)
                    }
                    MotionKind.FACE_PULL -> {
                        handL=mix(p(.65f,1.50f,.16f),p(.09f,1.72f,.44f),t)
                        armPoleL=p(-.25f,1.50f,.85f);cable=p(.98f,1.56f)
                    }
                    else -> {
                        elbowL=p(-.025f,1.88f,.22f);elbowR=elbowL.copy(z=-.22f)
                        // The extension sweeps behind the head, never through the face.
                        val angle=mix(-145f,-278f,t)*PI.toFloat()/180f
                        handL=elbowL+p(cos(angle)*.34f,sin(angle)*.34f)
                        cable=p(-.75f,.45f)
                    }
                }
                handR=handL.copy(z=-handL.z);armPoleR=armPoleL.copy(z=-armPoleL.z)
            }
            MotionKind.LEG_PRESS -> {
                hip=p(-.47f,.57f);shoulder=p(-.76f,1.10f)
                bench(p(-.49f,.48f),p(-.91f,1.22f))
                footL=mix(p(-.06f,.95f,.24f),p(.22f,1.18f,.24f),t);footR=footL.copy(z=-.24f)
                legPoleL=p(-.7f,1.4f,.25f);legPoleR=legPoleL.copy(z=-.25f)
                handL=p(-.34f,.44f,.34f);handR=handL.copy(z=-.34f)
                armPoleL=p(-.6f,.65f,.45f);armPoleR=armPoleL.copy(z=-.45f)
                props+=MotionProp(MotionPropKind.RAIL,p(-.10f,.80f),p(.58f,1.48f))
                props+=MotionProp(MotionPropKind.PAD,footL.copy(z=-.48f),footL.copy(z=.48f))
            }
            MotionKind.LEG_EXTENSION, MotionKind.SEATED_CALF -> {
                hip=p(-.33f,.65f);shoulder=p(-.36f,1.26f)
                bench(p(-.60f,.56f),p(.10f,.56f))
                handL=p(-.20f,.59f,.34f);handR=handL.copy(z=-.34f)
                if(kind==MotionKind.LEG_EXTENSION) {
                    kneeL=p(.10f,.64f,.18f);kneeR=kneeL.copy(z=-.18f)
                    val angle=mix(-90f,-6f,t)*PI.toFloat()/180f
                    footL=kneeL+p(cos(angle)*.44f,sin(angle)*.44f);footR=footL.copy(z=-.18f)
                } else {
                    kneeL=p(.10f,mix(.65f,.74f,t),.18f);kneeR=kneeL.copy(z=-.18f)
                    footL=p(.12f,mix(.16f,.25f,t),.18f);footR=footL.copy(z=-.18f)
                    toeL=p(.29f,.13f,.18f);toeR=toeL.copy(z=-.18f)
                    props+=MotionProp(MotionPropKind.PAD,kneeL+p(-.1f,.08f,-.55f),kneeR+p(.1f,.08f,.55f))
                }
                if(kind==MotionKind.LEG_EXTENSION) {
                    props+=MotionProp(MotionPropKind.ROLLER,footL+p(0,.10f,-.65f),footL+p(0,.10f,.30f))
                } else {
                    props+=MotionProp(MotionPropKind.PAD,p(.29f,.09f,-.43f),p(.29f,.09f,.43f))
                }
            }
            MotionKind.LEG_CURL -> {
                hip=p(-.14f,.65f);shoulder=p(.48f,.66f)
                bench(p(-.73f,.56f),p(.66f,.56f))
                kneeL=p(-.58f,.64f,.17f);kneeR=kneeL.copy(z=-.17f)
                val angle=mix(180f,90f,t)*PI.toFloat()/180f
                footL=kneeL+p(cos(angle)*.44f,sin(angle)*.44f);footR=footL.copy(z=-.17f)
                handL=p(.64f,.38f,.28f);handR=handL.copy(z=-.28f)
                armPoleL=p(.22f,.34f,.4f);armPoleR=armPoleL.copy(z=-.4f)
                props+=MotionProp(MotionPropKind.ROLLER,footL.copy(z=-.42f),footL.copy(z=.42f))
            }
            MotionKind.BRIDGE, MotionKind.CRUNCH, MotionKind.REVERSE_CRUNCH -> {
                mat()
                hip=p(-.14f,.18f);shoulder=p(.46f,.17f)
                footL=p(-.72f,.07f,.21f);footR=footL.copy(z=-.21f)
                legPoleL=p(-.3f,.9f,.21f);legPoleR=legPoleL.copy(z=-.21f)
                handL=p(.03f,.08f,.38f);handR=handL.copy(z=-.38f)
                armPoleL=p(.23f,.1f,.35f);armPoleR=armPoleL.copy(z=-.35f)
                if(kind==MotionKind.BRIDGE) {
                    footL=p(-.55f,.07f,.21f);footR=footL.copy(z=-.21f)
                    hip=mix(hip,p(-.115f,.375f),t)
                } else if(kind==MotionKind.CRUNCH) {
                    shoulder=mix(shoulder,p(.38f,.40f),t)
                    handL=shoulder+p(.13f,.11f,.19f);handR=handL.copy(z=-.19f)
                    armPoleL=shoulder+p(-.25f,.15f,.75f);armPoleR=armPoleL.copy(z=-.75f)
                } else {
                    hip=mix(hip,p(.04f,.34f),t)
                    kneeL=mix(p(-.20f,.63f,.18f),p(.24f,.76f,.18f),t);kneeR=kneeL.copy(z=-.18f)
                    footL=kneeL+p(-.42f,.08f);footR=footL.copy(z=-.18f)
                }
            }
            MotionKind.TWIST -> {
                mat();hip=p(-.08f,.38f);shoulder=p(.23f,.89f)
                torsoYaw=mix(-.60f,.60f,t)
                footL=p(-.62f,.07f,.24f);footR=footL.copy(z=-.24f)
                legPoleL=p(-.28f,.8f,.24f);legPoleR=legPoleL.copy(z=-.24f)
                val reach=p(.43f*cos(torsoYaw),-.12f,.43f*sin(torsoYaw))
                handL=shoulder+reach+p(0,0,.055f);handR=shoulder+reach+p(0,0,-.055f)
                armPoleL=shoulder+p(.28f,-.2f,.65f);armPoleR=shoulder+p(.28f,-.2f,-.65f)
            }
            MotionKind.STANDING_CALF -> {
                hip=hip+p(0,.10f*t);shoulder=shoulder+p(0,.10f*t)
                footL=footL+p(0,.10f*t);footR=footR+p(0,.10f*t)
                toeL=p(.22f,.04f,.22f);toeR=toeL.copy(z=-.22f)
                handL=handL+p(0,.10f*t);handR=handR+p(0,.10f*t)
            }
            MotionKind.PUSHUP -> {
                mat()
                val height=mix(.70f,.25f,t)
                val bodyLength=1.519f
                val forward=sqrt(bodyLength*bodyLength-(height-.07f)*(height-.07f))
                shoulder=p(-1.05f+forward,height)
                hip=p(-1.05f,.07f)+(shoulder-p(-1.05f,.07f))*(.919f/bodyLength)
                footL=p(-1.05f,.07f,.16f);footR=footL.copy(z=-.16f)
                toeL=p(-.93f,.04f,.16f);toeR=toeL.copy(z=-.16f)
                handL=p(.55f,.07f,.31f);handR=handL.copy(z=-.31f)
                armPoleL=p(.05f,.35f,.7f);armPoleR=armPoleL.copy(z=-.7f)
                legPoleL=p(-.55f,.45f,.16f);legPoleR=legPoleL.copy(z=-.16f)
            }
        }
        val joints=mutableMapOf<MotionJoint,MotionPoint>()
        val side=p(-sin(torsoYaw)*.21f,0,cos(torsoYaw)*.21f)
        val hipSide=p(0,0,.14f)
        val shoulderL=shoulder+side;val shoulderR=shoulder-side
        val hipL=hip+hipSide;val hipR=hip-hipSide
        val leftArm=if(elbowL==null)ik(shoulderL,handL,.34f,.34f,armPoleL) else elbowL to handL
        val rightArm=if(elbowR==null)ik(shoulderR,handR,.34f,.34f,armPoleR) else elbowR to handR
        val leftLeg=if(kneeL==null)ik(hipL,footL,.46f,.46f,legPoleL) else kneeL to footL
        val rightLeg=if(kneeR==null)ik(hipR,footR,.46f,.46f,legPoleR) else kneeR to footR
        joints[MotionJoint.PELVIS]=hip;joints[MotionJoint.CHEST]=shoulder
        val spine=(shoulder-hip).unit()
        joints[MotionJoint.NECK]=shoulder+spine*.085f
        val head=shoulder+spine*.205f
        joints[MotionJoint.HEAD]=head.copy(y=max(.12f,head.y))
        joints[MotionJoint.SHOULDER_L]=shoulderL;joints[MotionJoint.SHOULDER_R]=shoulderR
        joints[MotionJoint.HIP_L]=hipL;joints[MotionJoint.HIP_R]=hipR
        joints[MotionJoint.ELBOW_L]=leftArm.first!!;joints[MotionJoint.WRIST_L]=leftArm.second
        joints[MotionJoint.ELBOW_R]=rightArm.first!!;joints[MotionJoint.WRIST_R]=rightArm.second
        joints[MotionJoint.KNEE_L]=leftLeg.first!!;joints[MotionJoint.ANKLE_L]=leftLeg.second
        joints[MotionJoint.KNEE_R]=rightLeg.first!!;joints[MotionJoint.ANKLE_R]=rightLeg.second
        joints[MotionJoint.TOE_L]=toeL?:leftLeg.second+p(.13f,-.035f)
        joints[MotionJoint.TOE_R]=toeR?:rightLeg.second+p(.13f,-.035f)
        val wl=leftArm.second;val wr=rightArm.second
        if(barbell) {
            val center=(wl+wr)*.5f
            props+=MotionProp(MotionPropKind.BARBELL,center+p(0,0,-.70f),center+p(0,0,.70f))
        }
        if(dumbbell||oneDumbbell) {
            fun bell(hand:MotionPoint) {
                val axis=when {
                    hammer -> (hand-p(hand.x,1.23f,hand.z)).let{p(-it.y,it.x,0).unit()}
                    kind==MotionKind.FLY -> p(1,0,0)
                    else -> p(0,0,1)
                }
                props+=MotionProp(MotionPropKind.DUMBBELL,hand-axis*.16f,hand+axis*.16f)
            }
            if(dumbbell)bell(wl)
            bell(wr)
        }
        if(cable!=null) {
            val center=(wl+wr)*.5f
            if(kind in listOf(MotionKind.FACE_PULL,MotionKind.OVERHEAD_TRICEPS,MotionKind.PUSHDOWN)) {
                val join=center+(cable-center).unit()*.16f
                props+=MotionProp(MotionPropKind.CABLE,cable,join)
                props+=MotionProp(MotionPropKind.ROPE,join,wl)
                props+=MotionProp(MotionPropKind.ROPE,join,wr)
            } else {
                props+=MotionProp(MotionPropKind.CABLE,cable,center)
                props+=MotionProp(MotionPropKind.BAR,wl,wr)
            }
        }
        if(kind==MotionKind.CABLE_FLY) {
            props+=MotionProp(MotionPropKind.CABLE,p(-.1f,1.95f,1.1f),wl)
            props+=MotionProp(MotionPropKind.CABLE,p(-.1f,1.95f,-1.1f),wr)
        }
        return MotionPose(joints,props)
    }
}
