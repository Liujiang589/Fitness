package app.duolian.local

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable internal fun AuthorContactCard() {
    val context = LocalContext.current
    val author = stringResource(R.string.author_name)
    val phone = stringResource(R.string.author_phone)
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { delay(2500); copied = false } }
    Panel(color = Night) {
        Eyebrow("独练 / 关于作者", Volt)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).background(Volt, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                Text("LJ", color = Night, fontWeight = FontWeight.SemiBold, fontSize = 19.sp)
            }
            Column(Modifier.weight(1f).padding(start = 13.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(author, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 22.sp)
                Text("独练作者 · 建议与反馈", color = OnDarkMuted, style = MaterialTheme.typography.labelMedium)
            }
        }
        HorizontalDivider(color = DarkSeparator)
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("联系电话", color = OnDarkMuted, style = MaterialTheme.typography.labelSmall)
            Text(phone, color = Color.White, fontSize = 24.sp, lineHeight = 32.sp,
                fontWeight = FontWeight.SemiBold, letterSpacing = .8.sp)
            Text("关于训练、体验，或你想看到的新功能。", color = OnDarkMuted,
                style = MaterialTheme.typography.bodySmall)
        }
        Button(onClick = {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("作者联系电话", phone))
            copied = true
            if (Build.VERSION.SDK_INT < 33) Toast.makeText(context, "联系电话已复制", Toast.LENGTH_SHORT).show()
        }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Volt, contentColor = Night)) {
            AppSymbol(0, Night, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (copied) "已复制联系电话" else "复制联系方式", fontWeight = FontWeight.SemiBold)
        }
    }
}
