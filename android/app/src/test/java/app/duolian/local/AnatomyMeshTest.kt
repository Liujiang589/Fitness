package app.duolian.local

import app.duolian.local.domain.*
import java.io.File
import kotlin.math.abs
import org.junit.Assert.*
import org.junit.Test

class AnatomyMeshTest {
    @Test fun squatForearmDoesNotTwistOrCollapseWhenTheTorsoLeans() {
        val start=MotionEngine.sample(MotionKind.BODY_SQUAT,0f)
        val initial=AthleteBody.build(start,MotionKind.BODY_SQUAT)
        for(amount in listOf(.25f,.5f,.75f,1f)) {
            val pose=MotionEngine.sample(MotionKind.BODY_SQUAT,amount)
            val current=AthleteBody.build(pose,MotionKind.BODY_SQUAT)
            var checked=0
            initial.forEachIndexed { i,face ->
                val vertex=face.a
                if(vertex.muscle!=MusclePart.FOREARM)return@forEachIndexed
                val left=vertex.point.z>0
                val elbow=start[if(left)MotionJoint.ELBOW_L else MotionJoint.ELBOW_R]
                val wrist=start[if(left)MotionJoint.WRIST_L else MotionJoint.WRIST_R]
                val along=(vertex.point-elbow).dot((wrist-elbow).unit())/(wrist-elbow).length()
                if(along !in .25f.. .75f)return@forEachIndexed
                val shoulder=if(left)MotionJoint.SHOULDER_L else MotionJoint.SHOULDER_R
                val movement=pose[shoulder]-start[shoulder]
                assertTrue("forearm shape changes as torso leans",(current[i].a.point-vertex.point-movement).length()<.005f)
                assertTrue("forearm rolls against a fixed palm",current[i].a.normal.dot(vertex.normal)>.999f)
                checked++
            }
            assertTrue("must inspect both forearms",checked>40)
        }
    }
    @Test fun riggedHumanIncludesDetailedBodyClothingAndContinuousVertexRegions() {
        assertTrue(AthleteBody.sourceVertexCount in 8000..10000)
        assertTrue(AthleteBody.sourceTriangleCount in 14000..17000)
        for(kind in listOf(MotionKind.BENCH,MotionKind.BODY_SQUAT,MotionKind.CURL)) {
            val body=AthleteBody.build(MotionEngine.sample(kind,.5f),kind)
            assertTrue(body.any { it.material==BodyMaterial.SHORTS })
            assertTrue(body.any { it.material==BodyMaterial.SHOES })
            assertTrue(body.all { it.a.muscle!=null && it.b.muscle!=null && it.c.muscle!=null })
            // Garments must remain dark even when the muscles underneath are primary targets.
            val shorts=body.filter { it.material==BodyMaterial.SHORTS }
            val camera=AnatomyCamera.forMotion(kind,55f)
            fun colors(profile: MuscleProfile)=AnatomyProjection.frame(shorts,camera,profile,.5f,
                480f,620f,20f,65f,false).colors
            assertArrayEquals(colors(AnatomyMesh.profile(MotionKind.BODY_SQUAT)),colors(AnatomyMesh.profile(MotionKind.CURL)))
        }
    }
    @Test fun thighMusclesStayOnTheAnteriorAndPosteriorSidesAsTheKneeBends() {
        for(kind in listOf(MotionKind.BODY_SQUAT,MotionKind.LEG_EXTENSION,MotionKind.RDL)) for(amount in listOf(0f,.5f,1f)) {
            val pose=MotionEngine.sample(kind,amount)
            val a=pose[MotionJoint.HIP_L];val b=pose[MotionJoint.KNEE_L]
            val front=MotionPoint(a.y-b.y,b.x-a.x,0f).unit()
            val hip=(pose[MotionJoint.HIP_L]+pose[MotionJoint.HIP_R])*.5f
            val knee=(pose[MotionJoint.KNEE_L]+pose[MotionJoint.KNEE_R])*.5f
            val axisCenter=hip+(knee-hip)*.48f
            val mesh=AnatomyMesh.build(pose,kind)
            fun center(part: MusclePart)=mesh.filter { it.part==part }.map { it.a.point }.let { list ->
                list.fold(MotionPoint(0f,0f)) { sum,p -> sum+p }*(1f/list.size)
            }
            // Painted skin regions must retain front/back order; they no longer protrude from the limb.
            assertTrue("$kind front/back",(center(MusclePart.QUADRICEPS)-center(MusclePart.HAMSTRING)).dot(front)>.04f)
        }
    }
    @Test fun allFortyExercisesHaveVisibleMuscleSurfacesAndFinitePosedGeometry() {
        MotionKind.entries.forEach { kind ->
            val profile=AnatomyMesh.profile(kind)
            assertTrue(kind.name,profile.primary.isNotEmpty())
            assertTrue((profile.primary intersect profile.secondary).isEmpty())
            listOf(0f,.5f,1f).forEach { amount ->
                val faces=AnatomyMesh.build(MotionEngine.sample(kind,amount),kind)
                assertTrue(kind.name,faces.size in 14000..22000)
                assertTrue(kind.name,faces.map { it.part }.containsAll(profile.primary))
                faces.forEach { f -> listOf(f.a,f.b,f.c).forEach { v ->
                    assertTrue("$kind invalid geometry",listOf(v.point.x,v.point.y,v.point.z).all { it.isFinite() })
                    assertTrue("$kind invalid normal",abs(v.normal.length()-1f)<.002f)
                } }
            }
        }
    }
    @Test fun musclesMoveWithTheBodyInsteadOfRemainingAStaticOverlay() {
        val start=AnatomyMesh.build(MotionEngine.sample(MotionKind.BODY_SQUAT,0f),MotionKind.BODY_SQUAT)
        val bottom=AnatomyMesh.build(MotionEngine.sample(MotionKind.BODY_SQUAT,1f),MotionKind.BODY_SQUAT)
        fun center(faces: List<AnatomyFace>)=faces.filter { it.part==MusclePart.GLUTE }.map { it.a.point.y }.average()
        assertTrue(center(start)-center(bottom)>.25)
    }
    @Test fun cameraRemainsFixedThroughoutEachRepAndProjectsToFiniteCoordinates() {
        MotionKind.entries.forEach { kind -> listOf(0f,55f,-55f,90f,-90f).forEach { angle ->
            val camera=AnatomyCamera.forMotion(kind,angle)
            listOf(0f,1f).forEach { amount ->
                val frame=AnatomyProjection.frame(AnatomyMesh.build(MotionEngine.sample(kind,amount),kind,false),
                    camera,AnatomyMesh.profile(kind),amount,1000f,800f,30f,70f)
                assertTrue(frame.xy.isNotEmpty()); assertEquals(frame.xy.size/2,frame.colors.size)
                assertEquals(frame.colors.size,frame.depth.size)
                assertTrue(frame.depth.all { it.isFinite() && it in -1f..1f })
                assertTrue(frame.xy.all { it.isFinite() })
                assertTrue("$kind/$angle outside view",frame.xy.withIndex().all { (n,v) ->
                    v in if(n%2==0)-10f..1010f else -10f..810f })
            }
        } }
    }
    @Test fun trajectoriesFollowTheMovingBodyPartAndDoNotMislabelFixedPullupHands() {
        assertEquals(listOf(MotionJoint.CHEST),AnatomyMesh.trajectoryJoints(MotionKind.PULLUP))
        MotionKind.entries.forEach { kind ->
            assertTrue(kind.name,AnatomyMesh.trajectories(kind).any { points -> (points.first()-points.last()).length()>.03f })
        }
    }
    @Test fun exportRepresentativeNativeProjectionFramesForVisualReview() {
        val kinds=listOf(MotionKind.BENCH,MotionKind.BODY_SQUAT,MotionKind.CURL,MotionKind.PULLDOWN,MotionKind.RDL,MotionKind.SHOULDER_PRESS)
        val text=buildString {
            append('[')
            kinds.forEachIndexed { i,kind ->
                if(i>0)append(',')
                val angle=if(kind in listOf(MotionKind.PULLDOWN,MotionKind.RDL))-55f else 55f
                val camera=AnatomyCamera.forMotion(kind,angle)
                append("{\"kind\":\"").append(kind.name).append("\",\"frames\":[")
                listOf(0f,.5f,1f).forEachIndexed { n,amount ->
                    if(n>0)append(',')
                    val f=AnatomyProjection.frame(AnatomyMesh.build(MotionEngine.sample(kind,amount),kind),camera,
                        AnatomyMesh.profile(kind),amount,1000f,800f,30f,70f)
                    append("{\"xy\":[").append(f.xy.joinToString(",")).append("],\"colors\":[")
                    append(f.colors.joinToString(",")).append("],\"depth\":[").append(f.depth.joinToString(",")).append("]}")
                }
                append("]}")
            }
            append(']')
        }
        File("build/reports/anatomy-frames.json").apply { parentFile?.mkdirs(); writeText(text) }
    }
}
