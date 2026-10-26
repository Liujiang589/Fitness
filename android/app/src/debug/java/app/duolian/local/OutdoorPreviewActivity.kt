package app.duolian.local

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.duolian.local.domain.*
import app.duolian.local.outdoor.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Debug variant only. No repository, location service, permissions or real sensor inputs. */
class OutdoorPreviewActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        check(BuildConfig.DEBUG)
        val mode=intent.getStringExtra("mode") ?: "workout"
        val total=intent.getIntExtra("points",80).coerceIn(2,14000)
        val epoch=1700000000000L
        val route=List(total){i->
            val angle=i.toDouble()/total*kotlin.math.PI*1.6
            RoutePoint(30.0+kotlin.math.sin(angle)*.003,120.0+kotlin.math.cos(angle)*.004,
                epoch+i*1000L,5f)
        }
        val run=OutdoorRun(kind=OutdoorKind.RUN,startedAt=epoch,updatedAt=epoch+total*1000L,
            elapsedMs=660000,distanceMeters=2180.0,steps=2110,stepCounting=true,points=route)
        val source=MutableStateFlow(OutdoorLive(running=true,run=run,signal=OutdoorSignal.GOOD,
            speedMps=3.3,accuracy=5f,mapPosition=route.last(),fixAgeMs=0))
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.RESUMED) {
            repeat(60){n->delay(1000)
                val r=source.value.run ?: return@repeat
                if(r.status==OutdoorStatus.ACTIVE) {
                    val point=route.last().copy(latitude=route.last().latitude+(n+1)*.00002,
                        time=route.last().time+(n+1)*1000)
                    source.value=source.value.copy(run=r.copy(elapsedMs=r.elapsedMs+1000,distanceMeters=r.distanceMeters+2.2,
                        steps=r.steps+2,points=r.points+point),mapPosition=point)
                }
            }
        } }
        setContent { DuolianTheme { Surface(color=Page) {
            var review by remember{mutableStateOf<WorkoutExit?>(null)}
            var finished by remember{mutableStateOf(false)}
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Text("步行与跑步",Modifier.padding(horizontal=20.dp,vertical=12.dp),style=MaterialTheme.typography.titleLarge)
                Text("开发预览 · 模拟数据",Modifier.padding(horizontal=20.dp),color=Muted,style=MaterialTheme.typography.labelSmall)
                when {
                    finished -> Text("模拟记录已结束",Modifier.padding(24.dp))
                    mode=="prepare" -> OutdoorPreparationView(OutdoorKind.RUN,1,true,5f,false,Modifier.weight(1f),
                        start={finished=true},cancel={finished=true})
                    else -> {
                        OutdoorLiveWorkout(run,Modifier.weight(1f),source)
                        val state by source.collectAsState()
                        WorkoutControls(state.run?.status==OutdoorStatus.ACTIVE,false,toggle={
                            val paused=state.run?.status==OutdoorStatus.ACTIVE
                            source.value=state.copy(run=state.run?.copy(status=if(paused)OutdoorStatus.PAUSED else OutdoorStatus.ACTIVE),
                                signal=if(paused)OutdoorSignal.PAUSED else OutdoorSignal.GOOD)
                        },finish={
                            source.value=state.copy(run=state.run?.copy(status=OutdoorStatus.PAUSED),signal=OutdoorSignal.PAUSED)
                            review=WorkoutExit.SAVE
                        },discard={review=WorkoutExit.DISCARD})
                    }
                }
            }
            review?.let{exit->WorkoutExitDialog(source.value.run ?: run,exit,false,false,
                dismiss={review=null},confirm={review=null;finished=true})}
        } } }
    }
}
