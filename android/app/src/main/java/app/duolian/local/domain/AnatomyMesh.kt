package app.duolian.local.domain

import kotlin.math.*

enum class MusclePart { SURFACE, PECTORAL, LAT, TRAPEZIUS, ERECTOR, DELTOID, BICEPS, TRICEPS,
    FOREARM, ABDOMINAL, GLUTE, QUADRICEPS, HAMSTRING, CALF, EQUIPMENT }
data class MuscleProfile(val primary: Set<MusclePart>, val secondary: Set<MusclePart>)
data class AnatomyVertex(val point: MotionPoint, val normal: MotionPoint, val muscle: MusclePart? = null)
data class AnatomyFace(val a: AnatomyVertex, val b: AnatomyVertex, val c: AnatomyVertex, val part: MusclePart,
    val material: BodyMaterial = BodyMaterial.SKIN)

/** Rigged human and authored equipment. Muscle regions are teaching aids, not an EMG simulation. */
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
        val faces = ArrayList<AnatomyFace>(18000)
        val sides = if (detailed) 24 else 14
        val rings = if (detailed) 12 else 8
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
        faces.addAll(AthleteBody.build(pose,kind))
        fun cylinder(a: MotionPoint,b: MotionPoint,radius: Float,material: BodyMaterial=BodyMaterial.SKIN) {
            val axis=(b-a).unit();val (s,f)=frame(axis,MotionPoint(0f,1f))
            fun face(x: AnatomyVertex,y: AnatomyVertex,z: AnatomyVertex) {
                faces+=AnatomyFace(x,y,z,MusclePart.EQUIPMENT,material)
            }
            for(j in 0 until sides) {
                val t=2*PI.toFloat()*j/sides;val u=2*PI.toFloat()*(j+1)/sides
                val n=s*cos(t)+f*sin(t);val m=s*cos(u)+f*sin(u)
                val v0=vertex(a+n*radius,n);val v1=vertex(b+n*radius,n)
                val v2=vertex(b+m*radius,m);val v3=vertex(a+m*radius,m)
                face(v0,v1,v2);face(v0,v2,v3)
                face(vertex(a,axis*(-1f)),vertex(a+m*radius,axis*(-1f)),vertex(a+n*radius,axis*(-1f)))
                face(vertex(b,axis),vertex(b+n*radius,axis),vertex(b+m*radius,axis))
            }
        }
        fun box(center: MotionPoint,axis: MotionPoint,s: MotionPoint,f: MotionPoint,
            halfLength: Float,halfWidth: Float,halfDepth: Float) {
            val basis=listOf(axis,s,f);val sizes=listOf(halfLength,halfWidth,halfDepth)
            for(d in 0..2)for(sign in listOf(-1f,1f)) {
                val normal=basis[d]*sign;val c=center+normal*sizes[d]
                val u=basis[(d+1)%3]*sizes[(d+1)%3];val v=basis[(d+2)%3]*sizes[(d+2)%3]
                val points=listOf(c+u+v,c-u+v,c-u-v,c+u-v).map{vertex(it,normal)}
                faces+=AnatomyFace(points[0],points[1],points[2],MusclePart.EQUIPMENT,BodyMaterial.PAD)
                faces+=AnatomyFace(points[0],points[2],points[3],MusclePart.EQUIPMENT,BodyMaterial.PAD)
            }
        }
        // Original equipment geometry shares the mesh depth ordering with the anatomical surface.
        for (prop in pose.props) {
            val a=prop.a; val b=prop.b; val axis=(b-a).unit()
            when(prop.kind) {
                MotionPropKind.BARBELL,MotionPropKind.DUMBBELL -> {
                    cylinder(a,b,.014f,BodyMaterial.METAL)
                    val (s,f)=frame(axis,MotionPoint(0f,1f))
                    val r=if(prop.kind==MotionPropKind.BARBELL).17f else .105f
                    for(c in listOf(a+axis*.03f,b-axis*.03f)) cylinder(c-axis*.035f,c+axis*.035f,r)
                }
                MotionPropKind.BENCH -> {
                    val (s,f)=frame(axis,MotionPoint(0f,1f))
                    box((a+b)*.5f,axis,s,f,(b-a).length()*.5f,.155f,.045f)
                    for(end in listOf(a,b)) {
                        val ground=end.copy(y=.035f)
                        cylinder(ground,end,.023f,BodyMaterial.METAL)
                        cylinder(ground-s*.22f,ground+s*.22f,.025f,BodyMaterial.METAL)
                    }
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
