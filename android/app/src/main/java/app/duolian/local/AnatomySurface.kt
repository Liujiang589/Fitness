package app.duolian.local

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.*
import android.os.Handler
import android.os.HandlerThread
import android.view.TextureView
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.duolian.local.domain.*
import kotlinx.coroutines.flow.collectLatest
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

private data class AnatomyScene(val kind: MotionKind,val progress: Float,val angle: Float,val paths: Boolean,val density: Float)

/** TextureView clips and scrolls with Compose. Depth-buffered triangles keep muscle surfaces attached. */
@Composable internal fun AnatomySurface(guide: ExerciseGuide,progress: State<Float>,angle: Float,paths: Boolean,modifier: Modifier) {
    val context=LocalContext.current
    val density=LocalDensity.current.density
    val owner=LocalLifecycleOwner.current
    var failed by remember { mutableStateOf(false) }
    val surface=remember(context) { AnatomyTexture(context) { failed=true } }
    if(failed) { ExerciseMotionCanvas(guide,progress,modifier,angle,showTrajectory=paths);return }
    AndroidView(factory={surface},modifier=modifier.semantics { contentDescription="${guide.name}肌肉分区立体示范" })
    LaunchedEffect(surface,guide.kind,angle,paths,density) {
        snapshotFlow { progress.value }.collectLatest { surface.submit(AnatomyScene(guide.kind,it,angle,paths,density)) }
    }
    DisposableEffect(surface,owner) {
        val observer=LifecycleEventObserver { _,event ->
            if(event==Lifecycle.Event.ON_PAUSE)surface.paused=true
            if(event==Lifecycle.Event.ON_RESUME) { surface.paused=false;surface.refresh() }
        }
        surface.paused=!owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer);surface.paused=true }
    }
}

private class AnatomyTexture(context: Context,private val failed: () -> Unit): TextureView(context),TextureView.SurfaceTextureListener {
    @Volatile var paused=false
    @Volatile private var scene: AnatomyScene?=null
    private class Session(val texture: SurfaceTexture) {
        val thread=HandlerThread("独练肌肉示范").apply { start() }
        val handler=Handler(thread.looper)
        var renderer: AnatomyGl?=null // Access only on this session's render thread.
    }
    @Volatile private var session: Session?=null
    private val pending=AtomicBoolean(false)
    init { surfaceTextureListener=this; isOpaque=true }
    fun submit(next: AnatomyScene) { scene=next;refresh() }
    fun refresh() {
        val target=session ?: return
        if(paused || !pending.compareAndSet(false,true))return
        target.handler.post {
            pending.set(false)
            val current=scene
            if(!paused && session===target && current!=null) runCatching { target.renderer?.draw(current) }
                .onFailure { post { failed() } }
            if(scene!==current)refresh()
        }
    }
    override fun onSurfaceTextureAvailable(texture: SurfaceTexture,width: Int,height: Int) {
        val target=Session(texture);session=target
        target.handler.post {
            var renderer: AnatomyGl?=null
            runCatching { renderer=AnatomyGl(texture,width,height);renderer.initialize();target.renderer=renderer;refresh() }
                .onFailure { runCatching { renderer?.release() };post { failed() } }
        }
    }
    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture,width: Int,height: Int) {
        val target=session ?: return
        target.handler.post { target.renderer?.resize(width,height);refresh() }
    }
    override fun onSurfaceTextureUpdated(texture: SurfaceTexture)=Unit
    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
        val target=session;session=null;pending.set(false)
        if(target==null)texture.release() else target.handler.post {
            runCatching { target.renderer?.release() };target.renderer=null;texture.release();target.thread.quitSafely()
        }
        return false // Release the SurfaceTexture only after the GPU has detached it.
    }
}

private class AnatomyGl(private val texture: SurfaceTexture,private var width: Int,private var height: Int) {
    private val display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
    private var context: EGLContext=EGL14.EGL_NO_CONTEXT
    private var surface: EGLSurface=EGL14.EGL_NO_SURFACE
    private var program=0
    private var position=0
    private var color=0
    private var lastKind: MotionKind?=null
    private var lastAngle=Float.NaN
    private var camera: AnatomyCamera?=null
    private var paths=emptyList<List<MotionPoint>>()
    private var buffer=ByteBuffer.allocateDirect(1024).order(ByteOrder.nativeOrder()).asFloatBuffer()
    fun initialize() {
        check(EGL14.eglInitialize(display,IntArray(2),0,IntArray(2),0))
        val configs=arrayOfNulls<EGLConfig>(1)
        check(EGL14.eglChooseConfig(display,intArrayOf(EGL14.EGL_RENDERABLE_TYPE,EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_ALPHA_SIZE,8,
            EGL14.EGL_DEPTH_SIZE,16,EGL14.EGL_NONE),0,configs,0,1,IntArray(1),0))
        val config=checkNotNull(configs[0])
        context=EGL14.eglCreateContext(display,config,EGL14.EGL_NO_CONTEXT,intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE),0)
        surface=EGL14.eglCreateWindowSurface(display,config,texture,intArrayOf(EGL14.EGL_NONE),0)
        check(EGL14.eglMakeCurrent(display,surface,surface,context))
        fun shader(type: Int,source: String): Int {
            val id=GLES20.glCreateShader(type);GLES20.glShaderSource(id,source);GLES20.glCompileShader(id)
            val status=IntArray(1);GLES20.glGetShaderiv(id,GLES20.GL_COMPILE_STATUS,status,0)
            check(status[0]!=0) { "人体示范着色器不可用" };return id
        }
        val vertex=shader(GLES20.GL_VERTEX_SHADER,"attribute vec3 p; attribute vec4 c; varying vec4 v; void main(){gl_Position=vec4(p,1.0);v=c;}")
        val fragment=shader(GLES20.GL_FRAGMENT_SHADER,"precision mediump float; varying vec4 v; void main(){gl_FragColor=v;}")
        program=GLES20.glCreateProgram();GLES20.glAttachShader(program,vertex);GLES20.glAttachShader(program,fragment);GLES20.glLinkProgram(program)
        val status=IntArray(1);GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,status,0);check(status[0]!=0)
        GLES20.glDeleteShader(vertex);GLES20.glDeleteShader(fragment)
        position=GLES20.glGetAttribLocation(program,"p");color=GLES20.glGetAttribLocation(program,"c")
    }
    fun resize(w: Int,h: Int) { width=w; height=h }
    private fun batch(vertices: FloatArray,mode: Int) {
        if(vertices.isEmpty())return
        if(buffer.capacity()<vertices.size)buffer=ByteBuffer.allocateDirect(vertices.size*4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        buffer.clear();buffer.put(vertices);buffer.position(0)
        GLES20.glVertexAttribPointer(position,3,GLES20.GL_FLOAT,false,28,buffer);GLES20.glEnableVertexAttribArray(position)
        buffer.position(3);GLES20.glVertexAttribPointer(color,4,GLES20.GL_FLOAT,false,28,buffer);GLES20.glEnableVertexAttribArray(color)
        GLES20.glDrawArrays(mode,0,vertices.size/7)
    }
    fun draw(scene: AnatomyScene) {
        if(width<1 || height<1)return
        if(lastKind!=scene.kind || lastAngle!=scene.angle) {
            camera=AnatomyCamera.forMotion(scene.kind,scene.angle); paths=AnatomyMesh.trajectories(scene.kind)
            lastKind=scene.kind;lastAngle=scene.angle
        }
        val view=checkNotNull(camera)
        val amount=MotionEngine.amount(scene.progress)
        val frame=AnatomyProjection.frame(AnatomyMesh.build(MotionEngine.sample(scene.kind,amount),scene.kind),view,
            AnatomyMesh.profile(scene.kind),amount,width.toFloat(),height.toFloat(),16*scene.density,34*scene.density,false)
        GLES20.glViewport(0,0,width,height);GLES20.glClearColor(241/255f,243/255f,246/255f,1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT);GLES20.glUseProgram(program)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        if(scene.paths) for(path in paths) {
            val vertices=FloatArray(path.size*7)
            path.forEachIndexed { i,p -> val xy=view.point(p,width.toFloat(),height.toFloat(),16*scene.density,34*scene.density)
                val n=i*7;vertices[n]=xy.first/width*2-1;vertices[n+1]=1-xy.second/height*2;vertices[n+2]=0f
                vertices[n+3]=.31f;vertices[n+4]=.55f;vertices[n+5]=.54f;vertices[n+6]=1f
            }
            GLES20.glLineWidth(2*scene.density);batch(vertices,GLES20.GL_LINE_STRIP)
        }
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);GLES20.glDepthFunc(GLES20.GL_LEQUAL)
        val vertices=FloatArray(frame.colors.size*7)
        frame.colors.forEachIndexed { i,c -> val n=i*7
            vertices[n]=frame.xy[i*2]/width*2-1;vertices[n+1]=1-frame.xy[i*2+1]/height*2;vertices[n+2]=frame.depth[i]
            vertices[n+3]=((c shr 16) and 255)/255f;vertices[n+4]=((c shr 8) and 255)/255f;vertices[n+5]=(c and 255)/255f;vertices[n+6]=1f
        }
        batch(vertices,GLES20.GL_TRIANGLES);check(EGL14.eglSwapBuffers(display,surface))
    }
    fun release() {
        GLES20.glDeleteProgram(program)
        EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT)
        EGL14.eglDestroySurface(display,surface);EGL14.eglDestroyContext(display,context);EGL14.eglTerminate(display)
    }
}
