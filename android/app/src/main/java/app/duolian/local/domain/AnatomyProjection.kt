package app.duolian.local.domain

import kotlin.math.*

data class AnatomyCamera(val angle: Float, val left: Float, val right: Float, val bottom: Float, val top: Float) {
    private val yaw = angle * PI.toFloat()/180f
    private val elevation=.28f
    private val horizontalDirection=MotionPoint(sin(yaw),0f,-cos(yaw))
    val direction = horizontalDirection*cos(elevation)+MotionPoint(0f,sin(elevation))
    fun depth(p: MotionPoint) = p.dot(direction)
    private fun horizontal(p: MotionPoint) = p.x*cos(yaw)+p.z*sin(yaw)
    private fun vertical(p: MotionPoint) = p.y*cos(elevation)-p.dot(horizontalDirection)*sin(elevation)
    fun point(p: MotionPoint, width: Float, height: Float, padding: Float, header: Float): Pair<Float,Float> {
        val scale=min((width-padding*2)/(right-left),(height-padding*2-header)/(top-bottom)).coerceAtLeast(.1f)
        val x=width*.5f+(horizontal(p)-(left+right)*.5f)*scale
        val y=header+(height-header)*.5f-(vertical(p)-(bottom+top)*.5f)*scale
        return x to y
    }
    companion object {
        fun forMotion(kind: MotionKind, angle: Float): AnatomyCamera {
            val reference=AnatomyCamera(angle,0f,1f,0f,1f)
            val points=(0..12).flatMap { n -> MotionEngine.sample(kind,n/12f).let { pose ->
                pose.joints.values+pose.props.flatMap { listOf(it.a,it.b) }
            } }
            val minX=points.minOf { reference.horizontal(it) }; val maxX=points.maxOf { reference.horizontal(it) }
            // Equipment legs extend to the floor even in seated exercises whose joints are all raised.
            val minY=min(-.12f,points.minOf { reference.vertical(it) }); val maxY=points.maxOf { reference.vertical(it) }
            val width=max(1.55f,maxX-minX+.38f); val height=max(1.90f,maxY-minY+.30f)
            return AnatomyCamera(angle,(minX+maxX-width)*.5f,(minX+maxX+width)*.5f,
                (minY+maxY-height)*.5f,(minY+maxY+height)*.5f)
        }
    }
}
data class AnatomyFrame(val xy: FloatArray, val colors: IntArray, val depth: FloatArray)

/** One depth-sorted, shaded triangle batch. Canvas can submit it in one hardware draw call. */
object AnatomyProjection {
    fun frame(faces: List<AnatomyFace>, camera: AnatomyCamera, profile: MuscleProfile, amount: Float,
        width: Float,height: Float,padding: Float,header: Float, depthOrder: Boolean = true): AnatomyFrame {
        val visible=faces.filter { face ->
            (face.a.normal+face.b.normal+face.c.normal).dot(camera.direction) > -.05f
        }.let { if(depthOrder) it.sortedBy { f -> camera.depth((f.a.point+f.b.point+f.c.point)*(1f/3f)) } else it }
        val xy=FloatArray(visible.size*6); val colors=IntArray(visible.size*3)
        val depth=FloatArray(visible.size*3)
        val yaw=camera.angle*PI.toFloat()/180f
        val light=(camera.direction*.7f+MotionPoint(-cos(yaw)*.35f,.85f,-sin(yaw)*.35f)).unit()
        fun color(part: MusclePart, normal: MotionPoint): Int {
            val rgb=when {
                part==MusclePart.EQUIPMENT -> 0x39463E
                part in profile.primary -> 0xD25C45
                part in profile.secondary -> 0xC59A50
                part==MusclePart.SURFACE -> 0xCDD3C8
                else -> 0xA3B3A8
            }
            val brightness=(.43f+.57f*max(0f,normal.dot(light))) *
                if(part in profile.primary) (.94f+.06f*sin(amount*PI.toFloat())) else 1f
            fun channel(c: Int)=(c*brightness).roundToInt().coerceIn(0,255)
            return (255 shl 24) or (channel((rgb shr 16) and 255) shl 16) or
                (channel((rgb shr 8) and 255) shl 8) or channel(rgb and 255)
        }
        visible.forEachIndexed { index, face -> listOf(face.a,face.b,face.c).forEachIndexed { n,v ->
            val p=camera.point(v.point,width,height,padding,header)
            xy[index*6+n*2]=p.first; xy[index*6+n*2+1]=p.second
            colors[index*3+n]=color(face.part,v.normal)
            depth[index*3+n]=-camera.depth(v.point)/4f
        } }
        return AnatomyFrame(xy,colors,depth)
    }
}
