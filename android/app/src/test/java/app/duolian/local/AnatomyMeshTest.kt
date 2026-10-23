package app.duolian.local

import app.duolian.local.domain.*
import java.io.File
import kotlin.math.abs
import org.junit.Assert.*
import org.junit.Test

class AnatomyMeshTest {
    @Test fun allFortyExercisesHaveVisibleMuscleSurfacesAndFinitePosedGeometry() {
        MotionKind.entries.forEach { kind ->
            val profile=AnatomyMesh.profile(kind)
            assertTrue(kind.name,profile.primary.isNotEmpty())
            assertTrue((profile.primary intersect profile.secondary).isEmpty())
            listOf(0f,.5f,1f).forEach { amount ->
                val faces=AnatomyMesh.build(MotionEngine.sample(kind,amount),kind)
                assertTrue(kind.name,faces.size in 2000..15000)
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
