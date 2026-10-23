package app.duolian.local

import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.duolian.local.domain.*
import kotlin.math.*

private val DemoBackground = Color(0xFFEDF0E9)
private val DemoMuscle = Color(0xFFD87554)

@Composable private fun animationsEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) { runCatching {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }.getOrDefault(true) }
}

@Composable internal fun rememberMotionClock(key: String, playing: Boolean, speed: Float = 1f): MutableFloatState {
    val progress = rememberSaveable(key) { mutableFloatStateOf(0f) }
    MotionClock(progress, playing, speed)
    return progress
}

@Composable private fun MotionClock(progress: MutableFloatState, playing: Boolean, speed: Float) {
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner, progress, playing, speed) {
        if (playing) owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous = 0L
            while (true) withFrameNanos { current ->
                if (previous != 0L) {
                    val seconds = ((current-previous)/1_000_000_000f).coerceIn(0f,.06f)
                    progress.floatValue = (progress.floatValue + seconds*speed/5.5f) % 1f
                }
                previous=current
            }
        }
    }
}

/** Native vector scene: no image upload, no remote media, no rescaled low-resolution GIF. */
@Composable internal fun ExerciseMotionCanvas(guide: ExerciseGuide, progress: State<Float>,
    modifier: Modifier = Modifier, angle: Float = 32f) {
    Canvas(modifier.background(DemoBackground).semantics {
        contentDescription = "${guide.name}动态示范，主要肌群：${guide.focus}"
    }) {
        drawExercisePose(MotionEngine.sample(guide.kind, MotionEngine.amount(progress.value)), guide, angle)
    }
}

@Composable internal fun ExerciseMotionPreview(guide:ExerciseGuide, playing:Boolean, modifier:Modifier=Modifier) {
    val enabled=animationsEnabled()
    val progress=rememberMotionClock("preview-${guide.key}",playing&&enabled)
    ExerciseMotionCanvas(guide,progress,modifier)
}

private fun DrawScope.drawExercisePose(pose: MotionPose, guide: ExerciseGuide, angle: Float) {
    val yaw=angle*PI.toFloat()/180f
    val scale=min(size.width/3.05f,size.height/2.62f)
    fun project(v:MotionPoint)=Offset(size.width*.49f+(v.x*cos(yaw)+v.z*sin(yaw))*scale,
        size.height*.93f-(v.y-(-v.x*sin(yaw)+v.z*cos(yaw))*.10f)*scale)
    fun depth(v:MotionPoint)=-v.x*sin(yaw)+v.z*cos(yaw)
    val metal=Color(0xFF69746C);val steel=Color(0xFFABB4A7)
    val body=Color(0xFFC7CDC3);val outline=Color(0xFF879184)
    val floor=project(MotionPoint(0f,0f))
    drawOval(Color(0xFF59654F).copy(alpha=.13f),topLeft=Offset(floor.x-scale*.79f,floor.y-scale*.10f),
        size=Size(scale*1.58f,scale*.17f))
    for(i in -3..3) {
        drawLine(Color(0xFFDDE2D7),project(MotionPoint(i*.4f,0f,-1.1f)),
            project(MotionPoint(i*.4f,0f,1.1f)),1f)
        drawLine(Color(0xFFDDE2D7),project(MotionPoint(-1.3f,0f,i*.35f)),
            project(MotionPoint(1.3f,0f,i*.35f)),1f)
    }
    fun rod(a:MotionPoint,b:MotionPoint,width:Float,color:Color,edge:Boolean=true) {
        val start=project(a);val end=project(b);val line=end-start
        val normal=Offset(-line.y,line.x).let { if(it.getDistance()<.001f)Offset(1f,0f) else it/it.getDistance() }
        val w=width*scale
        if(edge)drawLine(color.copy(red=color.red*.70f,green=color.green*.70f,blue=color.blue*.70f),
            start,end,w+1.1f,StrokeCap.Round)
        val mid=(start+end)/2f
        drawLine(Brush.linearGradient(listOf(color,Color.White.copy(alpha=.9f),color),
            mid-normal*w*.58f,mid+normal*w*.58f),start,end,w,StrokeCap.Round)
    }
    fun equipment(prop:MotionProp) {
        val a=prop.a;val b=prop.b
        when(prop.kind) {
            MotionPropKind.MAT -> {
                val pts=listOf(a+MotionPoint(0f,0f,-.55f),b+MotionPoint(0f,0f,-.55f),
                    b+MotionPoint(0f,0f,.55f),a+MotionPoint(0f,0f,.55f)).map(::project)
                val path=Path().apply { moveTo(pts[0].x,pts[0].y);pts.drop(1).forEach{lineTo(it.x,it.y)};close() }
                drawPath(path,Color(0xFFC8D1BA))
            }
            MotionPropKind.BENCH -> {
                rod(a.copy(y=.035f),a,.055f,steel);rod(b.copy(y=.035f),b,.055f,steel)
                rod(a.copy(y=.035f,z=a.z-.32f),a.copy(y=.035f,z=a.z+.32f),.045f,metal)
                rod(b.copy(y=.035f,z=b.z-.32f),b.copy(y=.035f,z=b.z+.32f),.045f,metal)
                rod(a,b,.14f,Color(0xFF4F5C48))
            }
            MotionPropKind.RAIL -> rod(a,b,.045f,steel)
            MotionPropKind.PAD,MotionPropKind.ROLLER -> rod(a,b,.13f,Color(0xFF536149))
            MotionPropKind.CABLE -> {
                val base=a.copy(y=.03f)
                rod(base,a.copy(y=max(a.y+.15f,1.8f)),.045f,steel)
                drawCircle(metal,.055f*scale,project(a))
                drawLine(metal,project(a),project(b),max(1.4f,.013f*scale),StrokeCap.Round)
            }
            MotionPropKind.ROPE -> {
                rod(a,b,.026f,Color(0xFF5F6352))
                drawCircle(Color(0xFF3D4934),.04f*scale,project(b))
            }
            MotionPropKind.BAR -> rod(a,b,.035f,metal)
            MotionPropKind.BARBELL, MotionPropKind.DUMBBELL -> {
                rod(a,b,.025f,metal)
                val axis=(b-a).unit()
                val radius=if(prop.kind==MotionPropKind.BARBELL).22f else .125f
                listOf(a+axis*.05f,b-axis*.05f).forEach { center ->
                    val pt=project(center)
                    drawOval(Color(0xFF34432E),Offset(pt.x-radius*scale*.30f,pt.y-radius*scale),
                        Size(radius*scale*.60f,radius*scale*2))
                    drawOval(Color(0xFF66814D),Offset(pt.x-radius*scale*.21f,pt.y-radius*scale*.86f),
                        Size(radius*scale*.42f,radius*scale*1.72f))
                    drawCircle(Color(0xFFB8C79F),max(1f,.023f*scale),pt)
                }
            }
        }
    }
    pose.props.filter { it.kind !in listOf(MotionPropKind.DUMBBELL,MotionPropKind.BARBELL,MotionPropKind.BAR) }
        .forEach(::equipment)
    if(guide.kind==MotionKind.SQUAT)pose.props.filter{it.kind==MotionPropKind.BARBELL}.forEach(::equipment)
    data class Layer(val z:Float,val draw:()->Unit)
    val layers=mutableListOf<Layer>()
    val muscle=guide.muscle
    val highlights=guide.highlightedRegions
    fun limb(a:MotionJoint,b:MotionJoint,width:Float,active:Boolean) {
        val pa=pose[a];val pb=pose[b]
        layers+=Layer(depth((pa+pb)*.5f)) {
            rod(pa,pb,width,body)
            if(active)rod(pa+(pb-pa)*.20f,pa+(pb-pa)*.70f,width*.64f,DemoMuscle,false)
            drawCircle(outline,width*scale*.29f,project(pb))
            drawCircle(Color(0xFFDCE0D6),width*scale*.20f,project(pb))
        }
    }
    listOf(true,false).forEach { left ->
        val shoulder=if(left)MotionJoint.SHOULDER_L else MotionJoint.SHOULDER_R
        val elbow=if(left)MotionJoint.ELBOW_L else MotionJoint.ELBOW_R
        val wrist=if(left)MotionJoint.WRIST_L else MotionJoint.WRIST_R
        val hip=if(left)MotionJoint.HIP_L else MotionJoint.HIP_R
        val knee=if(left)MotionJoint.KNEE_L else MotionJoint.KNEE_R
        val ankle=if(left)MotionJoint.ANKLE_L else MotionJoint.ANKLE_R
        val toe=if(left)MotionJoint.TOE_L else MotionJoint.TOE_R
        limb(hip,knee,.15f,"腿部" in highlights)
        limb(knee,ankle,.105f,"小腿" in highlights)
        layers+=Layer(depth(pose[ankle])) { rod(pose[ankle],pose[toe],.085f,Color(0xFF56634E)) }
        limb(shoulder,elbow,.105f,muscle=="手臂")
        limb(elbow,wrist,.079f,false)
        layers+=Layer(depth(pose[wrist])) { drawCircle(body,.045f*scale,project(pose[wrist])) }
    }
    layers+=Layer(depth(pose[MotionJoint.CHEST])) {
        val hip=project(pose[MotionJoint.PELVIS]);val chest=project(pose[MotionJoint.CHEST])
        val line=chest-hip;val normal=Offset(-line.y,line.x)/line.getDistance().coerceAtLeast(.001f)
        val topWidth=.22f*scale+(project(pose[MotionJoint.SHOULDER_L])-project(pose[MotionJoint.SHOULDER_R])).getDistance()*.55f
        val bottomWidth=(.23f+.11f*abs(sin(yaw)))*scale
        val tl=chest+normal*topWidth*.5f;val tr=chest-normal*topWidth*.5f
        val bl=hip+normal*bottomWidth*.5f;val br=hip-normal*bottomWidth*.5f
        val bodyPath=Path().apply {
            moveTo(tl.x,tl.y);cubicTo(tl.x+line.x*.10f,tl.y+line.y*.10f,tr.x+line.x*.10f,tr.y+line.y*.10f,tr.x,tr.y)
            cubicTo(tr.x-line.x*.65f,tr.y-line.y*.65f,br.x+line.x*.35f,br.y+line.y*.35f,br.x,br.y)
            quadraticTo(hip.x-line.x*.12f,hip.y-line.y*.12f,bl.x,bl.y)
            cubicTo(bl.x+line.x*.35f,bl.y+line.y*.35f,tl.x-line.x*.65f,tl.y-line.y*.65f,tl.x,tl.y);close()
        }
        drawPath(bodyPath,outline)
        drawPath(bodyPath,Brush.linearGradient(listOf(Color(0xFF9AA593),Color(0xFFE5E8DF),body),tl,tr))
        val a=pose[MotionJoint.PELVIS];val b=pose[MotionJoint.CHEST]
        if(highlights.any{it in listOf("胸部","背部","腹部")}) {
            val from=if(muscle=="腹部").25f else .57f
            rod(a+(b-a)*from,a+(b-a)*if(muscle=="腹部").66f else .86f,
                if(muscle=="腹部").14f else .20f,DemoMuscle,false)
        }
        rod(a,a+(b-a)*.12f,.25f,if("腿部" in highlights)DemoMuscle else Color(0xFF5D6D51))
        rod(b,pose[MotionJoint.NECK],.10f,body)
        val head=project(pose[MotionJoint.HEAD])
        drawCircle(outline,.115f*scale,head)
        drawCircle(Brush.radialGradient(listOf(Color(0xFFF0F1EC),Color(0xFFB8C1B0)),
            head-Offset(.025f*scale,.03f*scale),.13f*scale),.108f*scale,head)
        val spine=(b-a).unit()
        val supine=guide.kind in listOf(MotionKind.BENCH,MotionKind.DB_BENCH,MotionKind.INCLINE_BENCH,
            MotionKind.FLY,MotionKind.LYING_TRICEPS,MotionKind.BRIDGE,MotionKind.CRUNCH,MotionKind.REVERSE_CRUNCH)
        val side=(pose[MotionJoint.SHOULDER_L]-pose[MotionJoint.SHOULDER_R]).unit()
        val face=MotionPoint(spine.y*side.z-spine.z*side.y,spine.z*side.x-spine.x*side.z,
            spine.x*side.y-spine.y*side.x).unit()*(if(supine)-1f else 1f)
        drawCircle(Color(0xFFACB69F),.022f*scale,project(pose[MotionJoint.HEAD]+face*.10f))
        listOf(MotionJoint.SHOULDER_L,MotionJoint.SHOULDER_R).forEach { joint ->
            val center=project(pose[joint])
            drawCircle(if(muscle=="肩部")DemoMuscle else body,.065f*scale,center)
        }
    }
    layers.sortedBy { it.z }.forEach { it.draw() }
    pose.props.filter { it.kind in listOf(MotionPropKind.DUMBBELL,MotionPropKind.BARBELL,MotionPropKind.BAR) &&
        !(guide.kind==MotionKind.SQUAT&&it.kind==MotionPropKind.BARBELL) }
        .forEach(::equipment)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun ExerciseDemoPlayer(guide:ExerciseGuide) {
    val configuration=LocalConfiguration.current
    val largeSceneHeight=minOf((configuration.screenWidthDp-60)/1.32f,
        configuration.screenHeightDp*.52f).coerceAtLeast(120f).dp
    val enabled=animationsEnabled()
    var playing by rememberSaveable(guide.key) { mutableStateOf(enabled) }
    var speed by rememberSaveable(guide.key) { mutableFloatStateOf(1f) }
    var angle by rememberSaveable(guide.key) { mutableFloatStateOf(32f) }
    var large by rememberSaveable(guide.key) { mutableStateOf(false) }
    val progress=rememberMotionClock(guide.key,playing,speed)
    val phase by remember(progress) { derivedStateOf { MotionEngine.phase(progress.floatValue) } }
    @Composable fun player(modifier:Modifier=Modifier) {
        Column(modifier,verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Surface(color=DemoBackground,shape=RoundedCornerShape(22.dp)) {
                Box {
                    ExerciseMotionCanvas(guide,progress,if(large)Modifier.fillMaxWidth().height(largeSceneHeight)
                        else Modifier.fillMaxWidth().aspectRatio(1.32f),angle)
                    Text("内置动态示范",Modifier.align(Alignment.TopStart).padding(14.dp),color=Blue,
                        style=MaterialTheme.typography.labelSmall)
                    TextButton(onClick={large=!large},modifier=Modifier.align(Alignment.TopEnd)) {
                        Text(if(large)"缩小" else "放大 ↗")
                    }
                }
            }
            FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp),
                verticalArrangement=Arrangement.spacedBy(4.dp)) {
                FilledTonalButton(onClick={playing=!playing}) {
                    Text(if(playing)"暂停示范" else "播放示范")
                }
                TextButton(onClick={speed=if(speed==1f).5f else 1f}) { Text(if(speed==1f)"1× 速度" else "0.5× 慢速") }
                TextButton(onClick={angle=if(angle==32f)0f else 32f}) { Text(if(angle==32f)"侧面" else "斜侧") }
            }
            Slider(value=progress.floatValue,onValueChange={playing=false;progress.floatValue=it},
                modifier=Modifier.semantics{contentDescription="动作示范进度"})
            Row(horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                guide.phaseNames.forEachIndexed { i,label ->
                    Surface(onClick={playing=false;progress.floatValue=listOf(0f,.46f,.80f)[i]},
                        modifier=Modifier.weight(1f).heightIn(min=48.dp),shape=RoundedCornerShape(11.dp),
                        color=if(i==phase)PaleBlue else Page) {
                        Text(label,Modifier.padding(horizontal=5.dp,vertical=12.dp),color=if(i==phase)Blue else Muted,
                            style=MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Text("高亮：${guide.focus}",color=Blue,style=MaterialTheme.typography.bodySmall)
            Text("可拖动进度查看姿势；示范速度可调，按自己的可控节奏练习。",color=Muted,
                style=MaterialTheme.typography.labelSmall)
        }
    }
    if(!large)player() else {
        FilledTonalButton(onClick={large=true},modifier=Modifier.fillMaxWidth()) { Text("动态示范已放大 · 点击查看") }
        Dialog(onDismissRequest={large=false},properties=DialogProperties(usePlatformDefaultWidth=false)) {
            Surface(Modifier.fillMaxWidth().padding(10.dp),shape=RoundedCornerShape(24.dp),color=Color.White) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Text(guide.name,Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
                        TextButton(onClick={large=false}){Text("关闭放大")}
                    }
                    player()
                }
            }
        }
    }
}

@Composable internal fun ExerciseGuideDialog(exercise:Exercise,busy:Boolean,dismiss:()->Unit,
    edit:()->Unit,favorite:()->Unit,delete:()->Unit) {
    val guide=remember(exercise.name,exercise.equipment){ExerciseGuides.find(exercise)}
    val height=(LocalConfiguration.current.screenHeightDp*.92f).dp
    val uri=LocalUriHandler.current
    Dialog(onDismissRequest=dismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxWidth().padding(horizontal=12.dp).heightIn(max=height),
            shape=RoundedCornerShape(28.dp),color=Color.White) {
            Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(exercise.name,style=MaterialTheme.typography.titleLarge)
                        Text("${exercise.muscle} · ${exercise.equipment}",color=Blue,
                            style=MaterialTheme.typography.labelMedium)
                    }
                    TextButton(onClick=dismiss){Text("关闭")}
                }
                Column(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState()),
                    verticalArrangement=Arrangement.spacedBy(18.dp)) {
                    if(guide!=null) {
                        ExerciseDemoPlayer(guide)
                        Text("动作要领",style=MaterialTheme.typography.titleLarge)
                        guide.stages.forEachIndexed { i,text ->
                            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                                Surface(color=PaleBlue,shape=RoundedCornerShape(10.dp)) {
                                    Text("${i+1}",Modifier.padding(horizontal=12.dp,vertical=8.dp),color=Blue,
                                        fontWeight=FontWeight.Bold)
                                }
                                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                    Text(guide.phaseNames[i],fontWeight=FontWeight.SemiBold)
                                    Text(text,color=Muted,style=MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                        Surface(color=Color(0xFFFFF2E8),shape=RoundedCornerShape(18.dp)) {
                            Column(Modifier.padding(15.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                Text("留意这些常见错误",fontWeight=FontWeight.SemiBold,color=Ink)
                                guide.mistakes.forEach { Text("• $it",color=Ink,style=MaterialTheme.typography.bodyMedium) }
                            }
                        }
                        Text("呼吸与控制",fontWeight=FontWeight.SemiBold)
                        Text(guide.breathing,color=Muted,style=MaterialTheme.typography.bodyMedium)
                    } else {
                        EmptyPanel("这个自定义动作暂未内置示范","当前内置覆盖基础动作库。请按该动作的具体要求练习，可在下方保留个人要点。")
                    }
                    DetailDisclosure("个人训练设置") {
                        Text("默认重量 ${kg(exercise.defaultGrams)} kg",fontWeight=FontWeight.SemiBold)
                        if(exercise.notes.isNotBlank())Text(exercise.notes,color=Muted)
                        if(exercise.mediaUri!=null) {
                            Text("此前保存的个人照片",color=Muted,style=MaterialTheme.typography.labelMedium)
                            ExercisePhoto(exercise.mediaUri,Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(14.dp)))
                        }
                        TextButton(enabled=!busy,onClick=edit){Text("编辑个人设置")}
                    }
                    DetailDisclosure("示范说明与动作管理") {
                        Text("原创矢量小人展示常见动作路径；实际动作幅度应保持稳定、舒适。",color=Muted,
                            style=MaterialTheme.typography.bodySmall)
                        TextButton(onClick={runCatching{uri.openUri("https://www.acefitness.org/resources/everyone/exercise-library/")}}) {
                            Text("技术参考 · ACE 动作库 ↗")
                        }
                        TextButton(enabled=!busy,onClick=delete){Text("删除这个动作",color=MaterialTheme.colorScheme.error)}
                    }
                }
                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(enabled=!busy,onClick=favorite,modifier=Modifier.weight(1f)) {
                        Text(if(exercise.favorite)"★ 已收藏" else "☆ 收藏动作")
                    }
                    Button(enabled=!busy,onClick=edit,modifier=Modifier.weight(1f)){Text("编辑动作")}
                }
            }
        }
    }
}
