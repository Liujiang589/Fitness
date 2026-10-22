package app.duolian.local

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal val Ink = Color(0xFF17212F)
internal val Muted = Color(0xFF788394)
internal val Blue = Color(0xFF2582F5)
internal val Page = Color(0xFFF3F5F8)
internal val PaleBlue = Color(0xFFEBF3FF)
internal fun kg(g: Long) = String.format(Locale.US, "%.3f", g / 1000.0).trimEnd('0').trimEnd('.')
internal fun number(n: Double) = String.format(Locale.US, "%.1f", n)
internal fun clock(ms: Long): String {
    val s = ms.coerceAtLeast(0) / 1000
    return "%02d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60)
}
internal fun stamp(t: Long): String = Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))

@Composable internal fun Panel(modifier: Modifier = Modifier, color: Color = Color.White,
    content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = color) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable internal fun Section(title: String, action: String? = null, onClick: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (action != null) TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 4.dp)) { Text(action) }
    }
}

@Composable internal fun Stat(value: String, label: String, modifier: Modifier = Modifier, accent: Color = Ink) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = accent)
        Text(label, style = MaterialTheme.typography.labelMedium, color = Muted)
    }
}

@Composable internal fun EmptyPanel(title: String, message: String) {
    Panel { Text(title, fontWeight = FontWeight.SemiBold); Text(message, color = Muted, style = MaterialTheme.typography.bodyMedium) }
}

@Composable internal fun AppSymbol(kind: Int, color: Color, modifier: Modifier = Modifier.size(23.dp)) {
    Canvas(modifier) {
        val w = size.width; val h = size.height; val s = w * .075f
        fun line(x: Float,y: Float,a: Float,b: Float) = drawLine(color, Offset(w*x,h*y), Offset(w*a,h*b),s,StrokeCap.Round)
        when(kind) {
            0 -> { drawRoundRect(color,Offset(w*.2f,h*.18f),Size(w*.6f,h*.72f),CornerRadius(w*.1f),style=Stroke(s));
                drawRoundRect(color,Offset(w*.36f,h*.1f),Size(w*.28f,h*.2f),CornerRadius(w*.05f));line(.35f,.47f,.65f,.47f);line(.35f,.65f,.65f,.65f) }
            1 -> { drawCircle(color,w*.34f,Offset(w*.5f,h*.5f),style=Stroke(s));line(.5f,.3f,.5f,.52f);line(.5f,.52f,.65f,.6f) }
            2 -> { line(.2f,.5f,.8f,.5f);line(.2f,.3f,.2f,.7f);line(.32f,.22f,.32f,.78f);line(.68f,.22f,.68f,.78f);line(.8f,.3f,.8f,.7f) }
            3 -> { line(.18f,.8f,.88f,.8f);line(.28f,.65f,.28f,.42f);line(.5f,.65f,.5f,.2f);line(.72f,.65f,.72f,.34f) }
            4 -> { drawCircle(color,w*.15f,Offset(w*.5f,h*.3f),style=Stroke(s));drawArc(color,180f,180f,false,Offset(w*.2f,h*.56f),Size(w*.6f,h*.5f),style=Stroke(s)) }
            5 -> { line(.58f,.1f,.3f,.55f);line(.3f,.55f,.52f,.55f);line(.52f,.55f,.4f,.9f);line(.4f,.9f,.74f,.4f);line(.74f,.4f,.5f,.4f) }
            6 -> { drawCircle(color,w*.36f,Offset(w*.5f,h*.5f),style=Stroke(s));drawCircle(color,w*.09f,Offset(w*.5f,h*.5f),style=Stroke(s)) }
            7 -> { drawRoundRect(color,Offset(w*.16f,h*.14f),Size(w*.68f,h*.74f),CornerRadius(w*.09f),style=Stroke(s));
                drawRoundRect(color,Offset(w*.26f,h*.24f),Size(w*.48f,h*.18f),CornerRadius(w*.03f),style=Stroke(s));
                listOf(.3f,.5f,.7f).forEach{x->line(x,.62f,x,.76f)};listOf(.3f,.5f,.7f).forEach{x->line(x,.84f,x,.84f)} }
            8 -> { drawCircle(color,w*.34f,Offset(w*.5f,h*.5f),style=Stroke(s));
                drawCircle(color,w*.035f,Offset(w*.5f,h*.33f));line(.5f,.46f,.5f,.68f) }
            else -> {line(.5f,.2f,.5f,.8f);line(.2f,.5f,.8f,.5f)}
        }
    }
}

/** User-picked exercise photo; decoded at half size to keep memory bounded. */
@Composable internal fun ExercisePhoto(uri: String, modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val image by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, uri) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(android.net.Uri.parse(uri))?.use { input ->
                    val options = android.graphics.BitmapFactory.Options().apply { inSampleSize = 2 }
                    android.graphics.BitmapFactory.decodeStream(input, null, options)
                        ?.asImageBitmap()
                }
            }.getOrNull()
        }
    }
    val bitmap = image
    if (bitmap != null) {
        Image(bitmap, contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop)
    } else {
        Box(modifier.background(Page, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Text("图片不可用", color = Muted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** Independently drawn category illustrations, not exercise-technique diagrams. */
@Composable internal fun ExerciseArt(muscle: String, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w=size.width; val h=size.height; val c=Color(0xFFBBC7D6); val stroke=w*.037f
        fun line(x:Float,y:Float,a:Float,b:Float,color:Color=c,width:Float=stroke)=drawLine(color,Offset(w*x,h*y),Offset(w*a,h*b),width,StrokeCap.Round)
        drawCircle(Color(0xFFF0F5FC),h*.43f,Offset(w*.5f,h*.48f))
        drawCircle(Ink,h*.065f,Offset(w*.5f,h*.18f))
        line(.5f,.29f,.5f,.58f,Ink,stroke*1.2f)
        line(.5f,.58f,.37f,.86f,Ink);line(.5f,.58f,.64f,.86f,Ink)
        val upper = muscle in listOf("肩部","手臂","背部")
        line(.5f,.33f,.29f,if(upper) .26f else .47f,Ink)
        line(.5f,.33f,.71f,if(upper) .26f else .47f,Ink)
        line(.29f,if(upper) .26f else .47f,.22f,if(upper) .13f else .34f,Ink)
        line(.71f,if(upper) .26f else .47f,.78f,if(upper) .13f else .34f,Ink)
        if(muscle in listOf("腿部","小腿")) {line(.5f,.61f,.39f,.81f,Blue,stroke*1.4f);line(.5f,.61f,.62f,.81f,Blue,stroke*1.4f)}
        else if(muscle=="腹部") line(.5f,.43f,.5f,.56f,Blue,stroke*1.6f)
        else {line(.41f,.35f,.59f,.35f,Blue,stroke*1.6f)}
        line(.14f,if(upper).13f else .34f,.3f,if(upper).13f else .34f,Blue)
        line(.7f,if(upper).13f else .34f,.86f,if(upper).13f else .34f,Blue)
        line(.15f,if(upper).08f else .29f,.15f,if(upper).18f else .39f,Blue)
        line(.85f,if(upper).08f else .29f,.85f,if(upper).18f else .39f,Blue)
    }
}

/** Small caption that sits above a field, so the box itself only holds the value. */
@Composable internal fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, color = Muted, style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Medium)
}

/**
 * Filled, rounded input used by every form: no outline box, unit shown inside,
 * focus tinted instead of using Material's underline.
 */
@Composable internal fun AppField(value: String, onValueChange: (String) -> Unit, label: String = "",
    modifier: Modifier = Modifier, placeholder: String = "", suffix: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text, singleLine: Boolean = true) {
    var focused by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label.isNotBlank()) FieldLabel(label)
        Surface(shape = RoundedCornerShape(15.dp),
            color = if (focused) Color.White else Page,
            border = BorderStroke(1.5.dp, if (focused) Blue else Color(0xFFE7ECF3)),
            modifier = Modifier.fillMaxWidth()) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Ink, fontWeight = FontWeight.SemiBold),
                cursorBrush = SolidColor(Blue),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
                decorationBox = { inner ->
                    Row(Modifier.padding(horizontal = 15.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            if (value.isEmpty() && placeholder.isNotEmpty())
                                Text(placeholder, color = Muted, style = MaterialTheme.typography.bodyLarge)
                            inner()
                        }
                        suffix?.let {
                            Text(it, color = Muted, style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                })
        }
    }
}

/** Solid pill used for mutually exclusive choices (scheme, set type, superset mode). */
@Composable internal fun ChoicePill(text: String, selected: Boolean, onClick: () -> Unit,
    modifier: Modifier = Modifier, accent: Color = Blue) {
    Surface(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(13.dp),
        color = if (selected) accent else Color.White,
        border = if (selected) null else BorderStroke(1.dp, Color(0xFFE7ECF3))) {
        Text(text, Modifier.padding(horizontal = 15.dp, vertical = 9.dp),
            color = if (selected) Color.White else Ink,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

/** Row used for pickable lists (plan exercises): whole row tappable, check on the right. */
@Composable internal fun SelectRow(title: String, subtitle: String? = null, selected: Boolean,
    modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
        color = if (selected) PaleBlue else Color.White) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    style = MaterialTheme.typography.bodyLarge)
                subtitle?.let { Text(it, color = Muted, style = MaterialTheme.typography.labelSmall) }
            }
            if (selected) Text("✓", color = Blue, fontWeight = FontWeight.Bold)
        }
    }
}

/** Metric tile used by the home and history summaries. */
@Composable internal fun StatTile(value: String, label: String, modifier: Modifier = Modifier,
    accent: Color = Ink, tint: Color = Page) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(tint).padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
            color = accent, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Muted, maxLines = 1)
    }
}

/** White rounded container that groups setting rows. */
@Composable internal fun GroupCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = Color.White) {
        Column(content = content)
    }
}

/** Settings row with a coloured icon chip, optional value and chevron. */
@Composable internal fun IconSettingRow(symbol: Int, tint: Color, title: String, subtitle: String? = null,
    trailing: String? = null, enabled: Boolean = true, onClick: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth()
        .then(if (onClick != null && enabled) Modifier.clickable { onClick() } else Modifier)
        .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center) { AppSymbol(symbol, tint, Modifier.size(19.dp)) }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(title, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) Ink else Muted)
            subtitle?.let { Text(it, color = Muted, style = MaterialTheme.typography.labelSmall) }
        }
        trailing?.let { Text(it, color = Muted, style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(start = 8.dp)) }
        if (onClick != null) Text("›", color = Muted, style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 6.dp))
    }
}
