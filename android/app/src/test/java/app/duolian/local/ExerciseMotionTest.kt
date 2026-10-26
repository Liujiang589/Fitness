package app.duolian.local

import app.duolian.local.domain.*
import java.io.File
import kotlin.math.abs
import org.junit.Assert.*
import org.junit.Test

class ExerciseMotionTest {
    @Test fun everyBuiltInAndTemplateExerciseHasItsOwnGuide() {
        val names=listOf("杠铃卧推","杠铃深蹲","高位下拉","坐姿划船","哑铃肩上推举","哑铃弯举","徒手深蹲","俯卧撑",
            "上斜杠铃卧推","哑铃卧推","哑铃飞鸟","绳索夹胸","杠铃划船","单臂哑铃划船","引体向上","直臂下压",
            "哑铃硬拉","罗马尼亚硬拉","腿举","保加利亚分腿蹲","坐姿腿屈伸","俯卧腿弯举","臀桥","哑铃侧平举",
            "哑铃前平举","俯身哑铃飞鸟","绳索面拉","阿诺德推举","锤式弯举","杠铃弯举","绳索弯举","绳索下压",
            "绳索过顶臂屈伸","仰卧杠铃臂屈伸","仰卧卷腹","悬垂举腿","反向卷腹","俄罗斯转体","站姿提踵","坐姿提踵")
        assertEquals(names.toSet(),ExerciseGuides.all.map{it.name}.toSet())
        assertEquals(MotionKind.entries.toSet(),ExerciseGuides.all.map{it.kind}.toSet())
        assertEquals(40,ExerciseGuides.all.map{it.key}.distinct().size)
        Templates.all.flatMap{it.exercises}.forEach {
            assertNotNull(it.name,ExerciseGuides.find(Exercise(name=it.name,muscle=it.muscle,equipment=it.equipment)))
        }
    }

    @Test fun everyGuideIncludesUsableTechniqueAndMistakes() {
        ExerciseGuides.all.forEach {
            assertEquals(3,it.stages.size);assertTrue(it.name,it.stages.all{text->text.length>=10})
            assertEquals(2,it.mistakes.size);assertTrue(it.breathing.isNotBlank());assertTrue(it.focus.isNotBlank())
            assertEquals(it,ExerciseGuides.find(Exercise(name=it.name,muscle=it.muscle,equipment=it.equipment)))
        }
    }

    @Test fun renamedOrUnknownExercisesNeverGetAnUnrelatedDemo() {
        assertNull(ExerciseGuides.find(Exercise(id="bench",name="自定义未知动作",muscle="胸部",equipment="杠铃")))
        assertNull(ExerciseGuides.find(Exercise(name="杠铃卧推",muscle="胸部",equipment="徒手")))
        assertNull(ExerciseGuides.find(Exercise(name="史密斯深蹲",muscle="腿部",equipment="器械")))
        assertEquals(MotionKind.SHOULDER_PRESS,ExerciseGuides.find(Exercise(name=" 哑铃肩推 ",muscle="肩部",equipment="哑铃"))?.kind)
    }

    @Test fun allFramesRemainFiniteAndKeepHumanLimbLengths() {
        val bones=listOf(MotionJoint.SHOULDER_L to MotionJoint.ELBOW_L,MotionJoint.ELBOW_L to MotionJoint.WRIST_L,
            MotionJoint.SHOULDER_R to MotionJoint.ELBOW_R,MotionJoint.ELBOW_R to MotionJoint.WRIST_R)
        val legs=listOf(MotionJoint.HIP_L to MotionJoint.KNEE_L,MotionJoint.KNEE_L to MotionJoint.ANKLE_L,
            MotionJoint.HIP_R to MotionJoint.KNEE_R,MotionJoint.KNEE_R to MotionJoint.ANKLE_R)
        MotionKind.entries.forEach { kind -> (0..60).forEach { i ->
            val pose=MotionEngine.sample(kind,i/60f)
            assertEquals(MotionJoint.entries.toSet(),pose.joints.keys)
            (pose.joints.values+pose.props.flatMap{listOf(it.a,it.b)}).forEach { p ->
                assertTrue("$kind invalid point $p",p.x.isFinite()&&p.y.isFinite()&&p.z.isFinite())
                assertTrue("$kind outside stage $p",abs(p.x)<2.5f&&p.y in -.05f..2.5f&&abs(p.z)<1.6f)
            }
            bones.forEach { (a,b) -> assertTrue("$kind $a $b",(pose[a]-pose[b]).length() in .30f.. .38f) }
            legs.forEach { (a,b) -> assertTrue("$kind $a $b",(pose[a]-pose[b]).length() in .40f.. .53f) }
        } }
        exportKeyPosesForVisualReview()
    }

    @Test fun standingSupportFeetDoNotSlideDuringRep() {
        listOf(MotionKind.SQUAT,MotionKind.BODY_SQUAT,MotionKind.RDL,MotionKind.DB_DEADLIFT,
            MotionKind.SPLIT_SQUAT,MotionKind.CURL,MotionKind.BARBELL_CURL,MotionKind.PUSHDOWN).forEach { kind ->
            val start=MotionEngine.sample(kind,0f)
            (0..30).forEach { i ->
                val pose=MotionEngine.sample(kind,i/30f)
                listOf(MotionJoint.ANKLE_L,MotionJoint.ANKLE_R).forEach {
                    assertTrue("$kind support foot moved",(pose[it]-start[it]).length()<.001f)
                }
            }
        }
    }

    @Test fun curlsAndTricepsUseFixedUpperArms() {
        listOf(MotionKind.CURL,MotionKind.HAMMER_CURL,MotionKind.BARBELL_CURL,MotionKind.CABLE_CURL,
            MotionKind.PUSHDOWN,MotionKind.OVERHEAD_TRICEPS,MotionKind.LYING_TRICEPS).forEach { kind ->
            val start=MotionEngine.sample(kind,0f);val end=MotionEngine.sample(kind,1f)
            assertTrue((start[MotionJoint.ELBOW_L]-end[MotionJoint.ELBOW_L]).length()<.001f)
            assertTrue((start[MotionJoint.WRIST_L]-end[MotionJoint.WRIST_L]).length()>.15f)
        }
    }

    @Test fun bodySquatKeepsRelaxedForwardArmsInsteadOfFlaringAndDroopingElbows() {
        (0..60).forEach { frame ->
            val pose=MotionEngine.sample(MotionKind.BODY_SQUAT,frame/60f)
            for((shoulderJoint,elbowJoint,wristJoint) in listOf(
                Triple(MotionJoint.SHOULDER_L,MotionJoint.ELBOW_L,MotionJoint.WRIST_L),
                Triple(MotionJoint.SHOULDER_R,MotionJoint.ELBOW_R,MotionJoint.WRIST_R))) {
                val shoulder=pose[shoulderJoint];val elbow=pose[elbowJoint];val wrist=pose[wristJoint]
                val upper=elbow-shoulder;val lower=wrist-elbow
                assertTrue("elbow should only bend softly",upper.unit().dot(lower.unit())>.94f)
                assertTrue("elbow flares sideways",abs(elbow.z-shoulder.z)<.015f)
                assertTrue("forearm drops away from shoulder level",abs(elbow.y-shoulder.y)<.09f)
                assertTrue("arms should reach forwards",wrist.x-shoulder.x>.65f)
                assertTrue("hands should stay near shoulder height",abs(wrist.y-shoulder.y)<.07f)
            }
            assertEquals(pose[MotionJoint.ELBOW_L].x,pose[MotionJoint.ELBOW_R].x,.0001f)
            assertEquals(pose[MotionJoint.ELBOW_L].y,pose[MotionJoint.ELBOW_R].y,.0001f)
            assertEquals(pose[MotionJoint.ELBOW_L].z,-pose[MotionJoint.ELBOW_R].z,.0001f)
        }
    }

    @Test fun pullupGripStaysOnBarAndChinClearsIt() {
        val start=MotionEngine.sample(MotionKind.PULLUP,0f);val top=MotionEngine.sample(MotionKind.PULLUP,1f)
        assertTrue((start[MotionJoint.WRIST_L]-top[MotionJoint.WRIST_L]).length()<.001f)
        assertTrue(top[MotionJoint.HEAD].y-.11f>2.08f)
    }

    @Test fun everyAnimationMovesAndCycleReturnsToStart() {
        MotionKind.entries.forEach { kind ->
            val start=MotionEngine.sample(kind,0f);val end=MotionEngine.sample(kind,1f)
            assertTrue("$kind is static",MotionJoint.entries.maxOf{(start[it]-end[it]).length()}>.06f)
        }
        assertEquals(0f,MotionEngine.amount(0f),0f);assertEquals(0f,MotionEngine.amount(1f),0f)
        assertEquals(1f,MotionEngine.amount(.46f),0f);assertEquals(0,MotionEngine.phase(0f))
        assertEquals(1,MotionEngine.phase(.46f));assertEquals(2,MotionEngine.phase(.8f))
    }

    @Test fun pushupAndBridgeKeepBodyAlignedAndRaisesKeepSoftElbows() {
        (0..30).forEach { i ->
            val pose=MotionEngine.sample(MotionKind.PUSHUP,i/30f)
            val shoulder=pose[MotionJoint.CHEST];val hip=pose[MotionJoint.PELVIS]
            val ankle=(pose[MotionJoint.ANKLE_L]+pose[MotionJoint.ANKLE_R])*.5f
            val direction=(shoulder-ankle).unit()
            val fromFoot=hip-ankle
            assertTrue("pushup hips leave body line",(fromFoot-direction*fromFoot.dot(direction)).length()<.004f)
        }
        val bridge=MotionEngine.sample(MotionKind.BRIDGE,1f)
        val chest=bridge[MotionJoint.CHEST];val hip=bridge[MotionJoint.PELVIS]
        val knee=(bridge[MotionJoint.KNEE_L]+bridge[MotionJoint.KNEE_R])*.5f
        assertTrue((chest-hip).unit().dot((hip-knee).unit())>.98f)
        listOf(MotionKind.FLY,MotionKind.LATERAL_RAISE,MotionKind.FRONT_RAISE,
            MotionKind.REVERSE_FLY,MotionKind.STRAIGHT_PULLDOWN).forEach { kind ->
            (0..30).forEach { i ->
                val p=MotionEngine.sample(kind,i/30f)
                val upper=(p[MotionJoint.ELBOW_L]-p[MotionJoint.SHOULDER_L]).unit()
                val lower=(p[MotionJoint.WRIST_L]-p[MotionJoint.ELBOW_L]).unit()
                assertTrue("$kind should not curl its elbow",upper.dot(lower)>.93f)
            }
        }
    }

    private fun StringBuilder.appendPose(pose: MotionPose) {
        append("{\"joints\":{")
        pose.joints.entries.forEachIndexed { ji,(joint,p) ->
            if(ji>0)append(",")
            append("\"").append(joint.name).append("\":[").append(p.x).append(",").append(p.y).append(",").append(p.z).append("]")
        }
        append("},\"props\":[")
        pose.props.forEachIndexed { pi,prop ->
            if(pi>0)append(",")
            append("{\"kind\":\"").append(prop.kind.name).append("\",\"a\":[")
            append(prop.a.x).append(",").append(prop.a.y).append(",").append(prop.a.z).append("],\"b\":[")
            append(prop.b.x).append(",").append(prop.b.y).append(",").append(prop.b.z).append("]}")
        }
        append("]}")
    }

    private fun exportKeyPosesForVisualReview() {
        val text=buildString {
            append("[")
            ExerciseGuides.all.forEachIndexed { index,guide ->
                if(index>0)append(",")
                append("{\"key\":\"").append(guide.key).append("\",\"name\":\"").append(guide.name)
                append("\",\"muscle\":\"").append(guide.muscle).append("\",\"frames\":[")
                listOf(0f,.5f,1f).forEachIndexed { fi,t ->
                    if(fi>0)append(",")
                    appendPose(MotionEngine.sample(guide.kind,t))
                }
                append("],\"playback\":[")
                (0 until 48).forEach { i ->
                    if(i>0)append(",")
                    appendPose(MotionEngine.sample(guide.kind,MotionEngine.amount(i/48f)))
                }
                append("]}")
            }
            append("]")
        }
        File("build/reports/motion-keyframes.json").apply { parentFile?.mkdirs();writeText(text) }
    }
}
