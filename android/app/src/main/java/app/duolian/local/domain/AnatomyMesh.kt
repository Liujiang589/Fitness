package app.duolian.local.domain

import kotlin.math.*

enum class MusclePart { SURFACE, PECTORAL, LAT, TRAPEZIUS, ERECTOR, DELTOID, BICEPS, TRICEPS,
    FOREARM, ABDOMINAL, GLUTE, QUADRICEPS, HAMSTRING, CALF, EQUIPMENT }
data class MuscleProfile(val primary: Set<MusclePart>, val secondary: Set<MusclePart>)
data class AnatomyVertex(val point: MotionPoint, val normal: MotionPoint)
data class AnatomyFace(val a: AnatomyVertex, val b: AnatomyVertex, val c: AnatomyVertex, val part: MusclePart)

/** Original articulated surface illustration. Muscle surfaces follow the pose, not an EMG simulation. */
object AnatomyMesh {
    private fun cross(a: MotionPoint, b: MotionPoint) = MotionPoint(a.y*b.z-a.z*b.y,a.z*b.x-a.x*b.z,a.x*b.y-a.y*b.x)
    fun profile(kind: MotionKind): MuscleProfile {
        fun p(vararg parts: MusclePart) = parts.toSet()
        val primary = when (kind) {
            MotionKind.BENCH, MotionKind.DB_BENCH, MotionKind.INCLINE_BENCH, MotionKind.FLY,
            MotionKind.CABLE_FLY, MotionKind.PUSHUP -> p(MusclePart.PECTORAL)
            MotionKind.SQUAT, MotionKind.BODY_SQUAT, MotionKind.SPLIT_SQUAT, MotionKind.LEG_PRESS -> p(MusclePart.QUADRICEPS,MusclePart.GLUTE)
            MotionKind.RDL, MotionKind.DB_DEADLIFT -> p(MusclePart.HAMSTRING,MusclePart.GLUTE)
            MotionKind.PULLDOWN, MotionKind.PULLUP, MotionKind.STRAIGHT_PULLDOWN -> p(MusclePart.LAT)
            MotionKind.ROW, MotionKind.ONE_ARM_ROW, MotionKind.SEATED_ROW -> p(MusclePart.LAT,MusclePart.TRAPEZIUS)
            MotionKind.SHOULDER_PRESS, MotionKind.ARNOLD, MotionKind.FRONT_RAISE, MotionKind.LATERAL_RAISE -> p(MusclePart.DELTOID)
            MotionKind.REVERSE_FLY, MotionKind.FACE_PULL -> p(MusclePart.DELTOID,MusclePart.TRAPEZIUS)
            MotionKind.CURL, MotionKind.BARBELL_CURL, MotionKind.CABLE_CURL -> p(MusclePart.BICEPS)
            MotionKind.HAMMER_CURL -> p(MusclePart.BICEPS,MusclePart.FOREARM)
            MotionKind.PUSHDOWN, MotionKind.OVERHEAD_TRICEPS, MotionKind.LYING_TRICEPS -> p(MusclePart.TRICEPS)
            MotionKind.LEG_EXTENSION -> p(MusclePart.QUADRICEPS)
            MotionKind.LEG_CURL -> p(MusclePart.HAMSTRING)
            MotionKind.BRIDGE -> p(MusclePart.GLUTE)
            MotionKind.CRUNCH, MotionKind.REVERSE_CRUNCH, MotionKind.HANGING_RAISE, MotionKind.TWIST -> p(MusclePart.ABDOMINAL)
            MotionKind.STANDING_CALF, MotionKind.SEATED_CALF -> p(MusclePart.CALF)
        }
        val secondary = when (kind) {
            MotionKind.BENCH, MotionKind.DB_BENCH, MotionKind.INCLINE_BENCH, MotionKind.PUSHUP -> p(MusclePart.TRICEPS,MusclePart.DELTOID)
            MotionKind.SHOULDER_PRESS, MotionKind.ARNOLD -> p(MusclePart.TRICEPS)
            MotionKind.ROW, MotionKind.ONE_ARM_ROW, MotionKind.SEATED_ROW, MotionKind.PULLDOWN, MotionKind.PULLUP -> p(MusclePart.BICEPS)
            MotionKind.RDL, MotionKind.DB_DEADLIFT -> p(MusclePart.ERECTOR)
            MotionKind.SQUAT, MotionKind.BODY_SQUAT, MotionKind.SPLIT_SQUAT -> p(MusclePart.ABDOMINAL)
            MotionKind.BRIDGE -> p(MusclePart.HAMSTRING)
            MotionKind.CURL, MotionKind.BARBELL_CURL, MotionKind.CABLE_CURL -> p(MusclePart.FOREARM)
            else -> emptySet()
        }
        return MuscleProfile(primary, secondary - primary)
    }

    fun trajectoryJoints(kind: MotionKind): List<MotionJoint> = when (kind) {
        MotionKind.SQUAT, MotionKind.BODY_SQUAT, MotionKind.SPLIT_SQUAT, MotionKind.RDL,
        MotionKind.DB_DEADLIFT, MotionKind.BRIDGE, MotionKind.STANDING_CALF -> listOf(MotionJoint.PELVIS)
        MotionKind.PUSHUP, MotionKind.PULLUP, MotionKind.CRUNCH -> listOf(MotionJoint.CHEST)
        MotionKind.HANGING_RAISE, MotionKind.REVERSE_CRUNCH, MotionKind.LEG_EXTENSION,
        MotionKind.LEG_CURL, MotionKind.LEG_PRESS, MotionKind.SEATED_CALF -> listOf(MotionJoint.ANKLE_L,MotionJoint.ANKLE_R)
        MotionKind.ONE_ARM_ROW -> listOf(MotionJoint.WRIST_R)
        else -> listOf(MotionJoint.WRIST_L,MotionJoint.WRIST_R)
    }
    fun trajectories(kind: MotionKind): List<List<MotionPoint>> = trajectoryJoints(kind).map { joint ->
        (0..16).map { MotionEngine.sample(kind, it / 16f)[joint] }
    }

    fun build(pose: MotionPose, kind: MotionKind, detailed: Boolean = true): List<AnatomyFace> {
        val faces = ArrayList<AnatomyFace>(5000)
        val sides = if (detailed) 12 else 8
        val rings = if (detailed) 7 else 5
        fun vertex(p: MotionPoint, n: MotionPoint) = AnatomyVertex(p,n.unit())
        fun patch(grid: List<List<AnatomyVertex>>, part: MusclePart) {
            for (i in 0 until grid.lastIndex) for (j in 0 until sides) {
                val k = (j+1)%sides
                faces += AnatomyFace(grid[i][j],grid[i+1][j],grid[i+1][k],part)
                faces += AnatomyFace(grid[i][j],grid[i+1][k],grid[i][k],part)
            }
        }
        fun ellipsoid(center: MotionPoint, up: MotionPoint, side: MotionPoint, front: MotionPoint,
            width: Float, length: Float, thickness: Float, part: MusclePart) {
            val grid = (0..rings).map { i -> val phi = PI.toFloat()*i/rings
                (0 until sides).map { j -> val theta = 2*PI.toFloat()*j/sides
                    val x=sin(phi)*cos(theta); val y=cos(phi); val z=sin(phi)*sin(theta)
                    vertex(center+side*(width*x)+up*(length*y)+front*(thickness*z),
                        side*(x/width)+up*(y/length)+front*(z/thickness))
                }
            }
            patch(grid,part)
        }
        fun frame(up: MotionPoint, preferred: MotionPoint): Pair<MotionPoint,MotionPoint> {
            val front = (preferred-up*preferred.dot(up)).let { v -> if(v.length()>.01f)v.unit()
                else cross(up,if(abs(up.z)<.9f)MotionPoint(0f,0f,1f) else MotionPoint(1f,0f)).unit() }
            return cross(up,front).unit() to front
        }
        fun tube(a: MotionPoint, b: MotionPoint, radius: Float, part: MusclePart = MusclePart.EQUIPMENT) {
            val up=(b-a).unit(); val (side,front)=frame(up,MotionPoint(1f,0f))
            ellipsoid((a+b)*.5f,up,side,front,radius,(b-a).length()*.5f+radius*.15f,radius,part)
        }
        val pelvis=pose[MotionJoint.PELVIS]; val chest=pose[MotionJoint.CHEST]
        val up=(chest-pelvis).unit()
        val side=(pose[MotionJoint.SHOULDER_L]-pose[MotionJoint.SHOULDER_R]).unit()
        val supine=kind in setOf(MotionKind.BENCH,MotionKind.DB_BENCH,MotionKind.INCLINE_BENCH,MotionKind.FLY,
            MotionKind.LYING_TRICEPS,MotionKind.BRIDGE,MotionKind.CRUNCH,MotionKind.REVERSE_CRUNCH)
        val front=cross(up,side).unit()*(if(supine)-1f else 1f)
        val torso=chest-pelvis
        // Continuous tapered trunk, rather than rods and exposed spherical joints.
        val sections=listOf(Triple(0f,.155f,.10f),Triple(.13f,.165f,.105f),Triple(.33f,.125f,.085f),
            Triple(.55f,.170f,.105f),Triple(.80f,.215f,.115f),Triple(1f,.175f,.09f),Triple(1.09f,.065f,.06f))
        patch(sections.map { (t,w,d) -> (0 until sides).map { j -> val a=2*PI.toFloat()*j/sides
            vertex(pelvis+torso*t+side*(cos(a)*w)+front*(sin(a)*d),side*cos(a)+front*sin(a))
        } },MusclePart.SURFACE)
        // Sculpted torso muscle surfaces: paired pecs/lats, abdominal blocks and spinal columns.
        for (s in listOf(-1f,1f)) {
            ellipsoid(pelvis+torso*.79f+side*(s*.100f)+front*.108f,up,side,front,.104f,.080f,.025f,MusclePart.PECTORAL)
            ellipsoid(pelvis+torso*.61f+side*(s*.125f)-front*.082f,up,side,front,.078f,.172f,.042f,MusclePart.LAT)
            ellipsoid(chest-up*.045f+side*(s*.075f)-front*.074f,up,side,front,.095f,.090f,.031f,MusclePart.TRAPEZIUS)
            ellipsoid(pelvis+torso*.39f+side*(s*.037f)-front*.085f,up,side,front,.029f,.19f,.024f,MusclePart.ERECTOR)
            ellipsoid(pelvis-up*.035f+side*(s*.082f)-front*.070f,up,side,front,.100f,.12f,.067f,MusclePart.GLUTE)
            for (row in 0..2) ellipsoid(pelvis+torso*(.32f+row*.13f)+side*(s*.043f)+front*.089f,
                up,side,front,.039f,.043f,.013f,MusclePart.ABDOMINAL)
        }
        fun limb(a: MotionPoint,b: MotionPoint,preferred: MotionPoint,widths: List<Float>) {
            val axis=(b-a).unit(); val (s,f)=frame(axis,preferred)
            patch(widths.mapIndexed { i,w -> val t=i.toFloat()/widths.lastIndex
                (0 until sides).map { j -> val angle=2*PI.toFloat()*j/sides
                    vertex(a+(b-a)*t+s*(w*cos(angle))+f*(w*.86f*sin(angle)),s*cos(angle)+f*(sin(angle)/.86f))
                }
            },MusclePart.SURFACE)
        }
        fun belly(a: MotionPoint,b: MotionPoint,preferred: MotionPoint,sign: Float,part: MusclePart,
            width: Float,thickness: Float,at: Float = .48f,portion: Float = .36f,offset: Float = .05f) {
            val axis=(b-a).unit(); val (s,f)=frame(axis,preferred)
            ellipsoid(a+(b-a)*at+f*(sign*offset),axis,s,f,width,(b-a).length()*portion,thickness,part)
        }
        for (left in listOf(true,false)) {
            val shoulder=pose[if(left)MotionJoint.SHOULDER_L else MotionJoint.SHOULDER_R]
            val elbow=pose[if(left)MotionJoint.ELBOW_L else MotionJoint.ELBOW_R]
            val wrist=pose[if(left)MotionJoint.WRIST_L else MotionJoint.WRIST_R]
            val hip=pose[if(left)MotionJoint.HIP_L else MotionJoint.HIP_R]
            val knee=pose[if(left)MotionJoint.KNEE_L else MotionJoint.KNEE_R]
            val ankle=pose[if(left)MotionJoint.ANKLE_L else MotionJoint.ANKLE_R]
            val toe=pose[if(left)MotionJoint.TOE_L else MotionJoint.TOE_R]
            // Keep anterior thigh/shin surfaces on the knee-facing side, independent of torso lean.
            val thighFront=MotionPoint(hip.y-knee.y,knee.x-hip.x,0f).unit()
            val shinFront=MotionPoint(knee.y-ankle.y,ankle.x-knee.x,0f).unit()
            limb(shoulder,elbow,front,listOf(.071f,.080f,.077f,.064f,.045f))
            limb(elbow,wrist,front,listOf(.046f,.064f,.056f,.041f,.031f))
            limb(hip,knee,thighFront,listOf(.085f,.108f,.102f,.079f,.056f))
            limb(knee,ankle,shinFront,listOf(.054f,.073f,.065f,.042f,.035f))
            ellipsoid(elbow,(wrist-elbow).unit(),side,front,.044f,.048f,.041f,MusclePart.SURFACE)
            ellipsoid(knee,(ankle-knee).unit(),side,front,.055f,.058f,.049f,MusclePart.SURFACE)
            ellipsoid(shoulder,up,side,front,.078f,.088f,.075f,MusclePart.DELTOID)
            belly(shoulder,elbow,front,1f,MusclePart.BICEPS,.050f,.038f,offset=.057f)
            belly(shoulder,elbow,front,-1f,MusclePart.TRICEPS,.052f,.032f,offset=.056f)
            belly(elbow,wrist,front,1f,MusclePart.FOREARM,.043f,.024f,at=.38f,offset=.038f)
            belly(hip,knee,thighFront,1f,MusclePart.QUADRICEPS,.080f,.043f,portion=.39f,offset=.074f)
            belly(hip,knee,thighFront,-1f,MusclePart.HAMSTRING,.070f,.036f,portion=.37f,offset=.068f)
            belly(knee,ankle,shinFront,-1f,MusclePart.CALF,.058f,.038f,at=.37f,portion=.31f,offset=.048f)
            val handAxis=(wrist-elbow).unit(); val (hs,hf)=frame(handAxis,front)
            ellipsoid(wrist+handAxis*.026f,handAxis,hs,hf,.041f,.065f,.027f,MusclePart.SURFACE)
            val footAxis=(toe-ankle).unit(); val (fs,ff)=frame(footAxis,MotionPoint(0f,1f))
            ellipsoid((ankle+toe)*.5f,footAxis,fs,ff,.055f,(toe-ankle).length()*.62f,.043f,MusclePart.SURFACE)
        }
        tube(chest,pose[MotionJoint.NECK]+up*.03f,.052f,MusclePart.SURFACE)
        val head=pose[MotionJoint.HEAD]
        ellipsoid(head,up,side,front,.086f,.120f,.100f,MusclePart.SURFACE)
        ellipsoid(head+front*.097f-up*.008f,up,side,front,.024f,.031f,.029f,MusclePart.SURFACE)
        for(s in listOf(-1f,1f)) ellipsoid(head+side*(s*.087f),up,side,front,.013f,.030f,.021f,MusclePart.SURFACE)
        // Original equipment geometry shares the mesh depth ordering with the anatomical surface.
        for (prop in pose.props) {
            val a=prop.a; val b=prop.b; val axis=(b-a).unit()
            when(prop.kind) {
                MotionPropKind.BARBELL,MotionPropKind.DUMBBELL -> {
                    tube(a,b,.018f)
                    val (s,f)=frame(axis,MotionPoint(0f,1f))
                    val r=if(prop.kind==MotionPropKind.BARBELL).17f else .105f
                    for(c in listOf(a+axis*.03f,b-axis*.03f)) ellipsoid(c,axis,s,f,r,.033f,r,MusclePart.EQUIPMENT)
                }
                MotionPropKind.BENCH -> {
                    val (s,f)=frame(axis,MotionPoint(0f,1f))
                    ellipsoid((a+b)*.5f,axis,s,f,.235f,(b-a).length()*.5f,.065f,MusclePart.EQUIPMENT)
                    tube(a.copy(y=.03f),a,.025f); tube(b.copy(y=.03f),b,.025f)
                }
                MotionPropKind.MAT -> Unit
                MotionPropKind.CABLE,MotionPropKind.ROPE -> tube(a,b,.008f)
                MotionPropKind.PAD,MotionPropKind.ROLLER -> tube(a,b,.070f)
                else -> tube(a,b,.025f)
            }
        }
        return faces
    }
}
