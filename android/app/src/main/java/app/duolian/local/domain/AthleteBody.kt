package app.duolian.local.domain

import java.io.DataInputStream
import java.util.zip.GZIPInputStream
import kotlin.math.*

enum class BodyMaterial { SKIN, SHORTS, SHOES, EYES, BROWS, METAL, PAD }

/** Weighted, continuous CC0 human mesh. Pose deformation is authored here; no downloaded animations. */
object AthleteBody {
    private data class Bone(val name: String,val parent: Int,val rest: MotionPoint,val axis: MotionPoint)
    private data class Rig(val bones: List<Bone>,val p: Array<MotionPoint>,val n: Array<MotionPoint>,
        val joints: IntArray,val weights: FloatArray,val indices: IntArray,val parts: IntArray,val materials: IntArray,
        val vertexParts: IntArray)
    private data class M(val a: FloatArray) {
        fun apply(p: MotionPoint)=MotionPoint(a[0]*p.x+a[1]*p.y+a[2]*p.z,
            a[3]*p.x+a[4]*p.y+a[5]*p.z,a[6]*p.x+a[7]*p.y+a[8]*p.z)
        operator fun times(b: M): M {
            val c=FloatArray(9)
            for(r in 0..2)for(col in 0..2)for(k in 0..2)c[r*3+col]+=a[r*3+k]*b.a[k*3+col]
            return M(c)
        }
    }
    private fun cross(a: MotionPoint,b: MotionPoint)=MotionPoint(a.y*b.z-a.z*b.y,a.z*b.x-a.x*b.z,a.x*b.y-a.y*b.x)
    private val identity=M(floatArrayOf(1f,0f,0f,0f,1f,0f,0f,0f,1f))
    private fun rotation(axis: MotionPoint,angle: Float): M {
        val v=axis.unit();val x=v.x;val y=v.y;val z=v.z;val c=cos(angle);val s=sin(angle);val t=1-c
        return M(floatArrayOf(t*x*x+c,t*x*y-s*z,t*x*z+s*y,t*x*y+s*z,t*y*y+c,t*y*z-s*x,
            t*x*z-s*y,t*y*z+s*x,t*z*z+c))
    }
    private fun align(from: MotionPoint,to: MotionPoint): M {
        val a=from.unit();val b=to.unit();val dot=a.dot(b).coerceIn(-1f,1f)
        if(dot>.99999f)return identity
        val axis=if(dot<-.99999f)cross(a,if(abs(a.x)<.8f)MotionPoint(1f,0f) else MotionPoint(0f,1f)) else cross(a,b)
        return rotation(axis,acos(dot))
    }
    private val rig: Rig by lazy {
        val raw=checkNotNull(AthleteBody::class.java.getResourceAsStream("/models/athlete.bin.gz")) { "人体示范资源未打包" }
        DataInputStream(GZIPInputStream(raw)).use { input ->
            check(input.readInt()==0x41544832)
            val nb=input.readInt();val nv=input.readInt();val nf=input.readInt()
            check(nb in 1..100 && nv in 1..20000 && nf in 1..20000)
            fun point()=MotionPoint(input.readFloat(),input.readFloat(),input.readFloat())
            val bones=List(nb) { i ->
                val name=input.readUTF();val parent=input.readInt();check(parent in -1 until i)
                Bone(name,parent,point(),point())
            }
            val p=Array(nv){point()};val n=Array(nv){point()}
            val js=IntArray(nv*4){input.readUnsignedShort().also { check(it<nb) }}
            val ws=FloatArray(nv*4){input.readFloat().also { check(it.isFinite() && it in 0f..1f) }}
            repeat(nv) { v -> check(abs((0..3).sumOf { ws[v*4+it].toDouble() }-1.0)<.002) }
            val ids=IntArray(nf*3){input.readInt().also { check(it in 0 until nv) }}
            val parts=IntArray(nf){input.readUnsignedByte().also { check(it<MusclePart.entries.size) }}
            val materials=IntArray(nf){input.readUnsignedByte().also { check(it<BodyMaterial.entries.size) }}
            val vertexParts=IntArray(nv){input.readUnsignedByte().also { check(it<MusclePart.entries.size) }}
            check(input.read()==-1)
            Rig(bones,p,n,js,ws,ids,parts,materials,vertexParts)
        }
    }
    val sourceVertexCount: Int get()=rig.p.size
    val sourceTriangleCount: Int get()=rig.parts.size

    fun build(pose: MotionPose,kind: MotionKind): List<AnatomyFace> {
        val data=rig;val bones=data.bones
        val pelvis=pose[MotionJoint.PELVIS];val chest=pose[MotionJoint.CHEST]
        val up=(chest-pelvis).unit();val side=(pose[MotionJoint.SHOULDER_L]-pose[MotionJoint.SHOULDER_R]).unit()
        val supine=kind in setOf(MotionKind.BENCH,MotionKind.DB_BENCH,MotionKind.INCLINE_BENCH,MotionKind.FLY,
            MotionKind.LYING_TRICEPS,MotionKind.BRIDGE,MotionKind.CRUNCH,MotionKind.REVERSE_CRUNCH)
        val front=cross(up,side).unit()*(if(supine)-1f else 1f)
        val body=M(floatArrayOf(front.x,up.x,side.x,front.y,up.y,side.y,front.z,up.z,side.z))
        fun rest(name: String)=bones.first { it.name==name }.rest
        val restPelvis=rest("pelvis")
        val restShoulder=(rest("upperarm_l")+rest("upperarm_r"))*.5f
        val torsoScale=(chest-pelvis).length()/(restShoulder.y-restPelvis.y)
        val origins=Array(bones.size){pelvis};val rotations=Array(bones.size){body};val scales=FloatArray(bones.size){1f}
        val gripping=pose.props.any { it.kind in setOf(MotionPropKind.BARBELL,MotionPropKind.DUMBBELL,
            MotionPropKind.BAR,MotionPropKind.ROPE,MotionPropKind.CABLE) } || kind in setOf(MotionKind.PULLUP,MotionKind.HANGING_RAISE)
        for(i in bones.indices) {
            val bone=bones[i];val parent=bone.parent;val name=bone.name
            // The squat reach stays level in world space while the torso leans. Carrying the
            // torso's roll into the humerus twists the forearm against the palm-down wrist.
            val inherited=if(kind==MotionKind.BODY_SQUAT && name.startsWith("upperarm_"))identity
                else if(parent<0)body else rotations[parent]
            var r=inherited
            var origin=if(parent<0)pelvis-body.apply(restPelvis) else origins[parent]+inherited.apply(bone.rest-bones[parent].rest)
            var end: MotionPoint?=null
            val left=name.endsWith("_l");fun joint(l: MotionJoint,rr: MotionJoint)=pose[if(left)l else rr]
            val shoulder=joint(MotionJoint.SHOULDER_L,MotionJoint.SHOULDER_R)
            val elbow=joint(MotionJoint.ELBOW_L,MotionJoint.ELBOW_R)
            val wrist=joint(MotionJoint.WRIST_L,MotionJoint.WRIST_R)
            val floorHand=kind in setOf(MotionKind.PUSHUP,MotionKind.BRIDGE,MotionKind.REVERSE_CRUNCH)
            val flatHand=floorHand || kind==MotionKind.BODY_SQUAT
            val handForward=MotionPoint(if(kind==MotionKind.PUSHUP || kind==MotionKind.BODY_SQUAT)1f else -1f,0f)
            val hand=if(floorHand)wrist+MotionPoint(0f,-.025f)
                else if(flatHand)wrist-handForward*.055f else wrist-(wrist-elbow).unit()*.055f
            when {
                name=="pelvis" -> { origin=pelvis;r=body }
                name.startsWith("spine_") -> {
                    val delta=bone.rest-restPelvis
                    origin=pelvis+body.apply(delta.copy(y=delta.y*torsoScale));r=body;scales[i]=torsoScale
                }
                name.startsWith("clavicle_") || name=="neck_01" || name=="Head" -> {
                    origin=chest+body.apply(bone.rest-restShoulder);r=body
                }
                name.startsWith("upperarm_") -> { origin=shoulder;end=elbow }
                name.startsWith("lowerarm_") -> { origin=elbow;end=hand }
                name.startsWith("hand_") -> {
                    origin=hand
                    if(flatHand) {
                        // Keep palms on the support plane instead of extending the forearm into the floor.
                        val z=handForward*(if(left)1f else -1f)
                        val y=MotionPoint(0f,1f);val x=cross(y,z)
                        r=M(floatArrayOf(x.x,y.x,z.x,x.y,y.y,z.y,x.z,y.z,z.z))
                    }
                }
                name.startsWith("thigh_") -> { origin=joint(MotionJoint.HIP_L,MotionJoint.HIP_R);end=joint(MotionJoint.KNEE_L,MotionJoint.KNEE_R) }
                name.startsWith("calf_") -> { origin=joint(MotionJoint.KNEE_L,MotionJoint.KNEE_R);end=joint(MotionJoint.ANKLE_L,MotionJoint.ANKLE_R) }
                name.startsWith("foot_") -> { origin=joint(MotionJoint.ANKLE_L,MotionJoint.ANKLE_R);end=joint(MotionJoint.TOE_L,MotionJoint.TOE_R) }
                name=="ball_l" || name=="ball_r" -> origin=joint(MotionJoint.TOE_L,MotionJoint.TOE_R)
                gripping && name.contains("_0") && !name.contains("leaf") -> {
                    val thumb=name.startsWith("thumb")
                    val angle=if(thumb).38f else if(name.contains("_01")) .85f else 1.05f
                    val axis=cross(bone.axis,MotionPoint(0f,-1f))
                    r=inherited*rotation(axis,angle)
                }
            }
            end?.let { target ->
                r=align(inherited.apply(bone.axis),target-origin)*inherited
                val child=bones.firstOrNull { it.parent==i }
                if(child!=null)scales[i]=(target-origin).length()/(child.rest-bone.rest).length()
            }
            origins[i]=origin;rotations[i]=r
        }
        val posed=Array(data.p.size) { v ->
            var p=MotionPoint(0f,0f);var n=MotionPoint(0f,0f)
            for(slot in 0..3) {
                val offset=v*4+slot;val weight=data.weights[offset]
                if(weight<.00001f)continue
                val j=data.joints[offset];val bone=bones[j];val scale=scales[j].coerceIn(.2f,3f)
                val d=data.p[v]-bone.rest
                val stretch=d+bone.axis*((scale-1)*d.dot(bone.axis))
                val normal=data.n[v]+bone.axis*((1/scale-1)*data.n[v].dot(bone.axis))
                p=p+(origins[j]+rotations[j].apply(stretch))*weight
                n=n+rotations[j].apply(normal)*weight
            }
            AnatomyVertex(p,n.unit(),MusclePart.entries[data.vertexParts[v]])
        }
        return List(data.parts.size) { f -> AnatomyFace(posed[data.indices[f*3]],posed[data.indices[f*3+1]],
            posed[data.indices[f*3+2]],MusclePart.entries[data.parts[f]],BodyMaterial.entries[data.materials[f]]) }
    }
}
