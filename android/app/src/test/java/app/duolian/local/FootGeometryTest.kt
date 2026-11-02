package app.duolian.local

import app.duolian.local.domain.*
import java.io.DataOutputStream
import java.io.File
import java.util.zip.GZIPOutputStream
import kotlin.math.*
import org.junit.Assert.*
import org.junit.Test

class FootGeometryTest {
    @Test fun everyFootKeepsAnOrthonormalFrameAndFixedBallDistance() {
        for(kind in MotionKind.entries) for(i in 0..60) for(left in listOf(true,false)) {
            val t=i/60f;val pose=MotionEngine.sample(kind,t)
            val knee=pose[if(left)MotionJoint.KNEE_L else MotionJoint.KNEE_R]
            val ankle=pose[if(left)MotionJoint.ANKLE_L else MotionJoint.ANKLE_R]
            val toe=pose[if(left)MotionJoint.TOE_L else MotionJoint.TOE_R]
            val f=FootKinematics.frame(kind,left,knee,ankle,t)
            assertEquals("$kind foot length",FootKinematics.restBall.length(),(toe-ankle).length(),.0001f)
            assertEquals(1f,f.forward.length(),.0001f);assertEquals(1f,f.up.length(),.0001f)
            assertEquals(0f,f.forward.dot(f.up),.0001f);assertEquals(1f,f.side.length(),.0001f)
        }
    }
    @Test fun plantedSolesStayFlatThroughoutSquatsAndSupineWork() {
        for(kind in listOf(MotionKind.BODY_SQUAT,MotionKind.SQUAT,MotionKind.BENCH,MotionKind.BRIDGE,MotionKind.CRUNCH)) {
            for(t in listOf(0f,.5f,1f)) {
                val body=AthleteBody.build(MotionEngine.sample(kind,t),kind)
                val shoes=body.filter { it.material==BodyMaterial.SHOES }.flatMap { listOf(it.a.point,it.b.point,it.c.point) }
                assertTrue("$kind shoe under floor ${shoes.minOf { it.y }}",shoes.minOf { it.y } in -.003f.. .015f)
                assertTrue("$kind shoes detached or folded",shoes.maxOf { it.y }<.15f)
            }
        }
    }
    @Test fun shoesRemainRigidInsteadOfBlendingIntoCalfRotations() {
        for(kind in MotionKind.entries) {
            fun shoe(t:Float)=AthleteBody.build(MotionEngine.sample(kind,t),kind)
                .filter { it.material==BodyMaterial.SHOES && it.a.point.z>0 }.map { it.a.point }
            val start=shoe(0f)
            for(t in listOf(.5f,1f)) {
                val moved=shoe(t)
                assertEquals(start.size,moved.size)
                for(i in 0 until start.lastIndex step 7) {
                    assertEquals("$kind warped shoe",(start[i]-start[i+1]).length(),(moved[i]-moved[i+1]).length(),.001f)
                }
            }
        }
    }
    @Test fun raisedHeelsPivotAroundTheSameForefootSupport() {
        for(kind in listOf(MotionKind.STANDING_CALF,MotionKind.SEATED_CALF)) {
            var initial:MotionPoint?=null
            for(i in 0..60) {
                val t=i/60f;val pose=MotionEngine.sample(kind,t);val ankle=pose[MotionJoint.ANKLE_L]
                val f=FootKinematics.frame(kind,true,pose[MotionJoint.KNEE_L],ankle,t)
                val support=ankle+f.map(FootKinematics.forefootSupport)
                if(initial==null)initial=support
                assertTrue("$kind sliding support",(support-initial).length()<.0001f)
            }
        }
    }
    @Test fun seatedLegMovementsMaintainAnkleAngleAndBenchFeetPointAwayFromHead() {
        for(kind in listOf(MotionKind.LEG_EXTENSION,MotionKind.LEG_CURL,MotionKind.HANGING_RAISE)) for(t in listOf(0f,.5f,1f)) {
            val p=MotionEngine.sample(kind,t);val a=p[MotionJoint.ANKLE_L];val k=p[MotionJoint.KNEE_L]
            val f=FootKinematics.frame(kind,true,k,a,t)
            assertEquals("$kind ankle angle",0f,f.forward.dot((a-k).unit()),.001f)
        }
        val p=MotionEngine.sample(MotionKind.BENCH,0f)
        assertTrue(p[MotionJoint.TOE_L].x<p[MotionJoint.ANKLE_L].x)
    }
    @Test fun footPlatesTouchTheSoleInsteadOfPassingThroughTheAnkle() {
        for(kind in listOf(MotionKind.LEG_PRESS,MotionKind.SEATED_ROW)) for(t in listOf(0f,.5f,1f)) {
            val p=MotionEngine.sample(kind,t);val plate=p.props.single { it.kind==MotionPropKind.FOOT_PLATE }
            val center=(plate.a+plate.b)*.5f;val n=plate.normal!!
            val ankle=(p[MotionJoint.ANKLE_L]+p[MotionJoint.ANKLE_R])*.5f
            assertEquals(.121f,(ankle-center).dot(n),.0001f)
        }
    }
    @Test fun toeSupportsAndElevatedRearFootDoNotPassThroughTheirSupportSurfaces() {
        for(kind in listOf(MotionKind.PUSHUP,MotionKind.STANDING_CALF,MotionKind.SEATED_CALF,MotionKind.SPLIT_SQUAT))
            for(t in listOf(0f,.25f,.5f,.75f,1f)) {
                val body=AthleteBody.build(MotionEngine.sample(kind,t),kind)
                val rear=kind==MotionKind.SPLIT_SQUAT
                val points=body.filter { it.material==BodyMaterial.SHOES }
                    .flatMap { listOf(it.a.point,it.b.point,it.c.point) }.filter { if(rear)it.z<0 else it.z>0 }
                val surface=when(kind) { MotionKind.SPLIT_SQUAT -> .445f;MotionKind.SEATED_CALF -> .060f;else -> 0f }
                assertTrue("$kind sole penetrates support",points.minOf { it.y }>=surface-.004f)
                assertTrue("$kind foot floats above support",points.minOf { it.y }<=surface+.015f)
            }
    }
    /** Actual app triangles, not a separately authored illustration. Kept local as compressed binary. */
    @Test fun exportAllFootPosesForVisualReview() {
        val output=File("build/reports/foot-review.bin.gz").apply { parentFile?.mkdirs() }
        DataOutputStream(GZIPOutputStream(output.outputStream().buffered())).use { out ->
            out.writeInt(MotionKind.entries.size)
            for(kind in MotionKind.entries) {
                out.writeUTF(kind.name)
                val camera=AnatomyCamera.forMotion(kind,55f)
                for(t in listOf(0f,.5f,1f)) {
                    val pose=MotionEngine.sample(kind,t)
                    val mesh=AnatomyMesh.build(pose,kind)
                    fun write(f: AnatomyFrame) {
                        out.writeInt(f.colors.size)
                        for(i in f.colors.indices) { out.writeFloat(f.xy[i*2]);out.writeFloat(f.xy[i*2+1]);out.writeInt(f.colors[i]) }
                    }
                    write(AnatomyProjection.frame(mesh,camera,AnatomyMesh.profile(kind),t,480f,360f,15f,15f))
                    val ankle=pose[MotionJoint.ANKLE_L]
                    val feet=mesh.filter { f -> listOf(f.a,f.b,f.c).any { (it.point-ankle).length()<.30f && it.point.z>0f } }
                    val cy=ankle.y*cos(.28f)+ankle.z*sin(.28f)
                    write(AnatomyProjection.frame(feet,AnatomyCamera(0f,ankle.x-.26f,ankle.x+.34f,cy-.22f,cy+.32f),
                        AnatomyMesh.profile(kind),t,320f,240f,10f,10f))
                }
            }
        }
    }
}
