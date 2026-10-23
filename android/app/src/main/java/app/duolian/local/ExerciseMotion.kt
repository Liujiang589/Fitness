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
import androidx.compose.ui.draw.drawWithCache
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
                if (previous != 0L && current-previous>=30_000_000L) {
                    val seconds = ((current-previous)/1_000_000_000f).coerceIn(0f,.06f)
                    progress.floatValue = (progress.floatValue + seconds*speed/5.5f) % 1f
                    previous=current
                } else if(previous==0L) previous=current
            }
        }
    }
}

/** Articulated, shaded anatomical surface; one native triangle batch and optional movement paths. */
@Composable internal fun ExerciseMotionCanvas(guide: ExerciseGuide, progress: State<Float>,
    modifier: Modifier = Modifier, angle: Float = 55f, detailed: Boolean = true, showTrajectory: Boolean = true) {
    val camera=remember(guide.kind,angle) { AnatomyCamera.forMotion(guide.kind,angle) }
    val paths=remember(guide.kind) { AnatomyMesh.trajectories(guide.kind) }
    Box(modifier.background(DemoBackground).graphicsLayer().semantics {
        contentDescription = "${guide.name}肌肉分区动态示范，主要肌群：${guide.focus}"
    }.drawWithCache {
        anatomyScene(guide,progress.value,camera,paths,detailed,showTrajectory)
    })
}

@Composable internal fun ExerciseMotionPreview(guide:ExerciseGuide, playing:Boolean, modifier:Modifier=Modifier) {
    val enabled=animationsEnabled()
    val progress=rememberMotionClock("preview-${guide.key}",playing&&enabled)
    ExerciseMotionCanvas(guide,progress,modifier,detailed=false,showTrajectory=false)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun ExerciseDemoPlayer(guide:ExerciseGuide, onPhaseChanged: (Int) -> Unit = {}) {
    val configuration=LocalConfiguration.current
    val largeSceneHeight=minOf((configuration.screenWidthDp-60)/1.32f,
        configuration.screenHeightDp*.52f).coerceAtLeast(120f).dp
    val enabled=animationsEnabled()
    var playing by rememberSaveable(guide.key) { mutableStateOf(enabled) }
    var speed by rememberSaveable(guide.key) { mutableFloatStateOf(1f) }
    val defaultAngle=remember(guide.kind) { if(AnatomyMesh.profile(guide.kind).primary.any {
        it in setOf(MusclePart.LAT,MusclePart.TRAPEZIUS,MusclePart.HAMSTRING) }) -55f else 55f }
    var angle by rememberSaveable(guide.key) { mutableFloatStateOf(defaultAngle) }
    var showTrajectory by rememberSaveable(guide.key) { mutableStateOf(true) }
    var large by rememberSaveable(guide.key) { mutableStateOf(false) }
    val progress=rememberMotionClock(guide.key,playing,speed)
    val phase by remember(progress) { derivedStateOf { MotionEngine.phase(progress.floatValue) } }
    val phaseCallback by rememberUpdatedState(onPhaseChanged)
    LaunchedEffect(phase) { phaseCallback(phase) }
    @Composable fun player(modifier:Modifier=Modifier) {
        Column(modifier,verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Surface(color=DemoBackground,shape=RoundedCornerShape(22.dp)) {
                Box {
                    AnatomySurface(guide,progress,angle,showTrajectory,if(large)Modifier.fillMaxWidth().height(largeSceneHeight)
                        else Modifier.fillMaxWidth().aspectRatio(1.32f))
                    Text("肌肉分区示范",Modifier.align(Alignment.TopStart).padding(14.dp),color=Blue,
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
                TextButton(onClick={angle=if(angle==0f)defaultAngle else 0f}) { Text(if(angle==0f)"斜侧" else "侧面") }
                TextButton(onClick={angle=90f}) { Text("正面") }
                TextButton(onClick={angle=-90f}) { Text("背面") }
                TextButton(onClick={showTrajectory=!showTrajectory}) { Text(if(showTrajectory)"隐藏轨迹" else "显示轨迹") }
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
            Text("主要区域 · ${guide.focus}",color=Color(0xFFC35F48),style=MaterialTheme.typography.bodySmall)
            Text("红色：主要区域  ·  金色：辅助区域  ·  青线：移动路径",color=Muted,style=MaterialTheme.typography.labelSmall)
            Text("可慢放、拖动和切换视角。肌群分区与路径为教学示意，不代表个人实际肌肉激活强度。",color=Muted,
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

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun ExerciseGuideDialog(exercise: Exercise, busy: Boolean, dismiss: () -> Unit,
    edit: () -> Unit, favorite: () -> Unit, delete: () -> Unit, readOnly: Boolean = false) {
    val original = remember(exercise.name, exercise.equipment) { ExerciseGuides.find(exercise) }
    var alternativeKey by rememberSaveable(exercise.id) { mutableStateOf<String?>(null) }
    val guide = alternativeKey?.let { key -> ExerciseGuides.all.find { it.key == key } } ?: original
    val exploring = alternativeKey != null && guide != null
    var topic by rememberSaveable(guide?.key) { mutableIntStateOf(0) }
    var phase by rememberSaveable(guide?.key) { mutableIntStateOf(0) }
    val scroll = rememberScrollState()
    LaunchedEffect(guide?.key) { scroll.scrollTo(0) }
    val height = (LocalConfiguration.current.screenHeightDp * .94f).dp
    val uri = LocalUriHandler.current
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().padding(horizontal = 10.dp).heightIn(max = height),
            shape = RoundedCornerShape(28.dp), color = Color.White) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (exploring) TextButton(onClick = { alternativeKey = null }) { Text("‹ 返回原动作") }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (exploring) guide!!.name else exercise.name, style = MaterialTheme.typography.titleLarge)
                        Text(guide?.let { "${it.muscle} · ${it.equipment}" } ?: "${exercise.muscle} · ${exercise.equipment}",
                            color = Blue, style = MaterialTheme.typography.labelMedium)
                    }
                    TextButton(onClick = dismiss) { Text("关闭") }
                }
                Column(Modifier.weight(1f, fill = false).verticalScroll(scroll),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (guide != null) {
                        key(guide.key) { ExerciseDemoPlayer(guide) { phase = it } }
                        CoachingCue(guide)
                        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("动作要领", "常见错误", "替代动作").forEachIndexed { index, name ->
                                FilterChip(selected = topic == index, onClick = { topic = index }, label = { Text(name) })
                            }
                        }
                        when (topic) {
                            0 -> {
                                Text("呼吸与控制", fontWeight = FontWeight.SemiBold)
                                Text(guide.breathing, color = Muted, style = MaterialTheme.typography.bodyMedium)
                                DetailDisclosure("完整分解说明") { CoachingStages(guide, phase) }
                            }
                            1 -> CoachingMistakes(guide)
                            2 -> CoachingAlternatives(guide) { alternativeKey = it.key }
                        }
                        if (exploring) Text("需要换练时，在计划编辑中选择该动作，并重新设置合适重量。",
                            color = Muted, style = MaterialTheme.typography.bodySmall)
                    } else {
                        EmptyPanel("这个自定义动作暂未内置示范", "暂不为未知动作匹配相似示范，可在个人设置中保留具体练习要点。")
                    }
                    if (!exploring) DetailDisclosure("个人训练设置") {
                        Text("默认重量 ${kg(exercise.defaultGrams)} kg", fontWeight = FontWeight.SemiBold)
                        if (exercise.notes.isNotBlank()) Text(exercise.notes, color = Muted)
                        if (exercise.mediaUri != null) {
                            Text("此前保存的个人照片", color = Muted, style = MaterialTheme.typography.labelMedium)
                            ExercisePhoto(exercise.mediaUri, Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(14.dp)))
                        }
                        if (!readOnly) TextButton(enabled = !busy, onClick = edit) { Text("编辑个人设置") }
                    }
                    DetailDisclosure("示范说明与动作管理") {
                        Text("原创矢量示范展示常见动作路径，按可控、舒适的幅度练习。替代动作的器械、阻力和动作模式可能不同。",
                            color = Muted, style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { runCatching { uri.openUri("https://www.acefitness.org/resources/everyone/exercise-library/") } }) {
                            Text("技术参考 · ACE 动作库 ↗")
                        }
                        if (!exploring && !readOnly) TextButton(enabled = !busy, onClick = delete) {
                            Text("删除这个动作", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                when {
                    exploring -> Button(onClick = { alternativeKey = null }, modifier = Modifier.fillMaxWidth()) { Text("返回原动作") }
                    readOnly -> Button(onClick = dismiss, modifier = Modifier.fillMaxWidth()) { Text("完成查看") }
                    else -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(enabled = !busy, onClick = favorite, modifier = Modifier.weight(1f)) {
                            Text(if (exercise.favorite) "★ 已收藏" else "☆ 收藏动作")
                        }
                        Button(enabled = !busy, onClick = edit, modifier = Modifier.weight(1f)) { Text("编辑动作") }
                    }
                }
            }
        }
    }
}
