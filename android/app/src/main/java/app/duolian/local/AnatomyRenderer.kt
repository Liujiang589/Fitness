package app.duolian.local

import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.DrawResult
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.dp
import app.duolian.local.domain.*
import kotlin.math.*

internal fun CacheDrawScope.anatomyScene(guide: ExerciseGuide, progress: Float, camera: AnatomyCamera,
    trajectories: List<List<MotionPoint>>, detailed: Boolean, showTrajectory: Boolean): DrawResult {
    val amount=MotionEngine.amount(progress)
    val pose=MotionEngine.sample(guide.kind,amount)
    val padding=if(detailed)16.dp.toPx() else 5.dp.toPx()
    val header=if(detailed)34.dp.toPx() else 0f
    val frame=AnatomyProjection.frame(AnatomyMesh.build(pose,guide.kind,detailed),camera,
        AnatomyMesh.profile(guide.kind),amount,size.width,size.height,padding,header)
    val paint=AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply { color=android.graphics.Color.WHITE }
    fun point(p: MotionPoint)=camera.point(p,size.width,size.height,padding,header).let { Offset(it.first,it.second) }
    val paths=if(detailed && showTrajectory) trajectories.map { samples ->
        samples.map(::point)
    } else emptyList()
    return onDrawBehind {
        val foot=point(MotionPoint(0f,0f))
        drawOval(Color(0x14607363),foot-Offset(size.width*.21f,5.dp.toPx()),
            androidx.compose.ui.geometry.Size(size.width*.42f,12.dp.toPx()))
        paths.forEach { samples ->
            val path=Path().apply { samples.forEachIndexed { i,p -> if(i==0)moveTo(p.x,p.y) else lineTo(p.x,p.y) } }
            drawPath(path,Color(0xFF508D89).copy(alpha=.65f),style=Stroke(1.5.dp.toPx(),cap=StrokeCap.Round))
            val target=samples.last(); val before=samples[samples.lastIndex-2]
            val delta=target-before
            if(delta.getDistance()>3.dp.toPx()) {
                val direction=delta/delta.getDistance(); val normal=Offset(-direction.y,direction.x)
                val arrow=Path().apply { moveTo(target.x,target.y)
                    val a=target-direction*8.dp.toPx()+normal*3.5.dp.toPx(); lineTo(a.x,a.y)
                    val b=target-direction*8.dp.toPx()-normal*3.5.dp.toPx(); lineTo(b.x,b.y); close() }
                drawPath(arrow,Color(0xFF508D89))
            }
            drawCircle(Color(0xFF508D89),2.5.dp.toPx(),samples.first())
        }
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawVertices(AndroidCanvas.VertexMode.TRIANGLES,frame.xy.size,frame.xy,0,
                null,0,frame.colors,0,null,0,0,paint)
        }
    }
}
