package app.duolian.local

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

@Composable internal fun CrashReportEntry() {
    val context=LocalContext.current
    var report by remember { mutableStateOf<String?>(null) }
    var copied by remember { mutableStateOf(false) }
    IconSettingRow(8,Muted,"异常诊断","闪退后可复制信息用于排查",onClick={
        copied=false;report=AppCrashReport.read(context)
    })
    report?.let { text -> AlertDialog(onDismissRequest={report=null},title={Text("异常诊断")},
        text={Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState())) {
            Text(text,style=MaterialTheme.typography.bodySmall.copy(fontFamily=FontFamily.Monospace))
        }},confirmButton={TextButton(onClick={
            context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("独练异常诊断",text))
            copied=true
        }) {Text(if(copied)"已复制" else "复制信息")}},
        dismissButton={TextButton(onClick={report=null}){Text("关闭")}}) }
}
