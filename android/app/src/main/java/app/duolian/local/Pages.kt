package app.duolian.local

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.duolian.local.domain.*
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId

internal class ScreenActions(
    val plan: (Plan?) -> Unit,
    val exercise: (Exercise?) -> Unit,
    val detail: (Exercise) -> Unit,
    val auxiliary: (String) -> Unit,
    val body: (Body?) -> Unit,
    val food: (Food?) -> Unit,
    val ask: (String, () -> Unit) -> Unit,
    val nutrition: () -> Unit
)

@Composable internal fun NutritionSummary(food: List<Food>, water: List<Water>, date: String,
    a: ScreenActions, detailed: Boolean = false) {
    val fs = food.filter { it.date == date }
    val ml = water.filter { it.date == date }.sumOf { it.ml }
    val protein = fs.sumOf { it.protein }; val carbs = fs.sumOf { it.carbs }; val fat = fs.sumOf { it.fat }
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (date == LocalDate.now().toString()) "今日营养" else "当日营养", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f))
            if (!detailed) TextButton(onClick = a.nutrition) { Text("饮食手记 ›") }
        }
        Text(if (fs.isEmpty()) "吃了什么，记一下就好。" else "食物和重量已记录，营养由独练计算。",
            color = Ink, style = MaterialTheme.typography.bodyMedium)
        NutritionBreakdown(protein, carbs, fat)
        if (detailed) {
            Text("已摄入 ≈ ${number(fs.sumOf { it.calories })} kcal", color = Blue,
                style = MaterialTheme.typography.titleMedium)
        }
        DetailDisclosure("营养明细") {
            if (!detailed) Text("已摄入 ≈ ${number(fs.sumOf { it.calories })} kcal", color = Blue,
                style = MaterialTheme.typography.titleMedium)
            listOf("蛋白质" to "${number(protein)} g", "碳水化合物" to "${number(carbs)} g",
                "脂肪" to "${number(fat)} g", "饮水" to "$ml ml").forEach { (label, value) ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, color = Muted, modifier = Modifier.weight(1f))
                    Text(value, color = Ink, fontWeight = FontWeight.SemiBold)
                }
            }
            Text("营养根据已记录食物估算；未设置个人摄入目标。", color = Muted,
                style = MaterialTheme.typography.bodySmall)
        }
        HorizontalDivider(color = Page)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("早餐", "午餐", "晚餐", "加餐", "饮水", "体重").forEachIndexed { i, label ->
                Column(Modifier.clip(RoundedCornerShape(12.dp)).clickable {
                    when (i) { 4 -> a.auxiliary("water:$date"); 5 -> a.body(null); else -> a.auxiliary("meal:$label:$date") }
                }.padding(horizontal = 4.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(if (i == 4) PaleBlue else Page),
                        contentAlignment = Alignment.Center) {
                        Text(listOf("早", "午", "晚", "＋", "水", "秤")[i], color = if (i == 4) Blue else Ink,
                            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(5.dp))
                    Text(label, style = MaterialTheme.typography.labelSmall, color = Muted)
                }
            }
        }
    }
}

@Composable internal fun TrainingPage(vm: MainViewModel, exercises: List<Exercise>, plans: List<Plan>,
    sessions: List<Session>, food: List<Food>, water: List<Water>, busy: Boolean, a: ScreenActions) {
    val active = sessions.firstOrNull { it.endedAt == null }; val history = sessions.filter { it.endedAt != null }
    val alerts = remember(sessions, exercises) { Diagnostics.alerts(sessions, exercises) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (active != null) item { ActiveTraining(active, vm, sessions, exercises, busy, a.ask) }
        else {
            item { TrainingSpotlight(plans, busy, vm) { a.plan(null) } }
            item { WeeklyMomentum(history) }
        }
        if (active == null) {
        if (alerts.isNotEmpty()) item { DiagnosticsBanner(alerts) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("训练计划", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f))
                Surface(onClick = { a.plan(null) }, shape = RoundedCornerShape(12.dp), color = PaleBlue) {
                    Text("＋ 新建", Modifier.padding(horizontal = 12.dp, vertical = 7.dp), color = Blue,
                        style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (plans.isEmpty()) item {
            EmptyPanel("建立你的训练安排", "点击“创建我的计划”或“＋ 新建”，自己选择动作和训练组数。")
        }
        items(plans, key = { it.id }) { p ->
            var menu by remember { mutableStateOf(false) }
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(PaleBlue),
                        contentAlignment = Alignment.Center) { AppSymbol(2, Blue) }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(p.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                        Text(exercises.filter { it.id in p.exerciseIds }.map { it.muscle }.distinct().joinToString(" · ").ifBlank { "按计划逐组完成" }, color = Muted,
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Box {
                        TextButton(onClick = { menu = true }, contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.width(36.dp)) { Text("•••") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("编辑计划") }, onClick = { menu = false; a.plan(p) })
                            DropdownMenuItem(text = { Text("复制计划") }, onClick = {
                                menu = false
                                vm.act { vm.repo.savePlan(p.copy(id = newId(), name = (p.name.take(94) + " 副本"))) }
                            })
                            DropdownMenuItem(text = { Text("删除计划", color = MaterialTheme.colorScheme.error) },
                                onClick = { menu = false; a.ask("删除计划「${p.name}」？") { vm.act { vm.repo.deletePlan(p.id) } } })
                        }
                    }
                }
                DetailDisclosure("计划内容") {
                    Text("${p.exerciseIds.size} 个动作 · ${p.sets} 组 × ${p.reps} 次 · 休息 ${p.restSeconds}s",
                        color = Muted, style = MaterialTheme.typography.bodySmall)
                    Text("${p.scheme.label} · ${p.superset.label}", color = Blue,
                        style = MaterialTheme.typography.labelMedium)
                    p.exerciseIds.forEach { id ->
                        exercises.find { it.id == id }?.let { e ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ExerciseArt(e.muscle, Modifier.size(38.dp))
                                Text(e.name, Modifier.weight(1f).padding(start = 10.dp),
                                    style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                Button(enabled = !busy, onClick = { vm.act { vm.repo.start(p.id) } },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(14.dp)) { Text("开始训练") }
            }
        }
        item { TemplateLibrary(vm) }
        if (plans.any { it.weekdays.isNotEmpty() }) item { ScheduleCard(plans, active, busy, vm) }
        item { NutritionSummary(food, water, LocalDate.now().toString(), a) }
        }
    }
}

@Composable internal fun ExercisePage(exercises: List<Exercise>, a: ScreenActions) {
    var query by rememberSaveable { mutableStateOf("") }
    var muscle by rememberSaveable { mutableStateOf("全部") }
    var equipment by rememberSaveable { mutableStateOf("全部") }
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    var autoplay by rememberSaveable { mutableStateOf(true) }
    val filtered = exercises.filter {
        (query.isBlank() || it.name.contains(query, true) || it.muscle.contains(query, true) || it.equipment.contains(query, true)) &&
            (muscle == "全部" || it.muscle == muscle) && (equipment == "全部" || it.equipment == equipment) &&
            (!favoritesOnly || it.favorite)
    }.sortedWith(compareByDescending<Exercise> { it.favorite }.thenBy { it.name })
    val listState=rememberLazyListState()
    val ids=remember(filtered){filtered.map{it.id}.toSet()}
    val playingId by remember(listState,ids) { derivedStateOf {
        val info=listState.layoutInfo
        info.visibleItemsInfo.filter{(it.key as? String)?.let(ids::contains)==true}.maxByOrNull {
            (minOf(it.offset+it.size,info.viewportEndOffset)-maxOf(it.offset,info.viewportStartOffset)).coerceAtLeast(0)
        }?.key as? String
    } }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AppField(query, { query = it }, placeholder = "搜索动作、肌群或器械", modifier = Modifier.weight(1f))
            FilledIconButton(onClick = { a.exercise(null) }, modifier = Modifier.size(52.dp),
                shape = RoundedCornerShape(16.dp)) { Text("＋", fontSize = 26.sp) }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (listOf("全部") + exercises.map { it.muscle }.distinct()).forEach { name ->
                ChoicePill(name, muscle == name, { muscle = name })
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = favoritesOnly, onClick = { favoritesOnly = !favoritesOnly },
                label = { Text("★ 收藏") })
            FilterChip(selected = autoplay, onClick = { autoplay = !autoplay }, label = { Text("动态预览") })
            (listOf("全部") + exercises.map { it.equipment }.distinct()).forEach { name ->
                FilterChip(selected = equipment == name, onClick = { equipment = name },
                    label = { Text(if (name == "全部") "全部器械" else name) })
            }
        }
        Row(Modifier.padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("看示范，再开始练", fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Text("${filtered.size} 个动作", color = Muted, style = MaterialTheme.typography.labelMedium)
        }
        LazyColumn(state=listState,modifier=Modifier.weight(1f),
            contentPadding=PaddingValues(start=18.dp,end=18.dp,bottom=24.dp),
            verticalArrangement=Arrangement.spacedBy(16.dp)) {
            if (filtered.isEmpty()) item { EmptyPanel("没有匹配的动作", "试试其他关键词，或切换肌群和器械筛选。") }
            items(filtered,key={it.id}) { exercise ->
                val guide=remember(exercise.name,exercise.equipment){ExerciseGuides.find(exercise)}
                val playing=autoplay&&playingId==exercise.id
                Surface(onClick={a.detail(exercise)},color=Color.White,shape=RoundedCornerShape(24.dp),
                    border=BorderStroke(1.dp,Hairline)) {
                    Column {
                        Box(Modifier.fillMaxWidth().background(Color(0xFFEDF0E9))) {
                            if(guide!=null) {
                                ExerciseMotionPreview(guide,playing,Modifier.fillMaxWidth().aspectRatio(1.7f))
                                Surface(Modifier.align(Alignment.TopStart).padding(13.dp),
                                    shape=RoundedCornerShape(9.dp),color=Color.White.copy(alpha=.9f)) {
                                    Text(if(playing)"▶ 正在演示" else "内置动态示范",
                                        Modifier.padding(horizontal=9.dp,vertical=5.dp),color=Blue,
                                        style=MaterialTheme.typography.labelSmall)
                                }
                            } else {
                                ExerciseArt(exercise.muscle,Modifier.fillMaxWidth().height(142.dp).padding(12.dp))
                                Text("自定义动作",Modifier.align(Alignment.TopStart).padding(14.dp),color=Muted,
                                    style=MaterialTheme.typography.labelSmall)
                            }
                        }
                        Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically,
                            horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                                Text((if(exercise.favorite)"★ " else "")+exercise.name,
                                    fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium)
                                Text("${exercise.muscle} · ${exercise.equipment}",color=Muted,
                                    style=MaterialTheme.typography.bodySmall)
                                Text(if(guide!=null)"动态示范 · 分解要领 · 常见错误" else "个人要点 · 暂无内置示范",
                                    color=Blue,style=MaterialTheme.typography.labelSmall)
                            }
                            Text("查看 ›",color=Blue,style=MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}

@Composable internal fun HistoryPage(sessions: List<Session>, exercises: List<Exercise>, plans: List<Plan>,
    body: List<Body>, food: List<Food>, vm: MainViewModel,a: ScreenActions){
    var range by rememberSaveable{mutableStateOf("全部")};var selected by rememberSaveable{mutableStateOf<String?>(null)}
    var days by rememberSaveable{mutableStateOf(30)}
    var section by rememberSaveable { mutableIntStateOf(0) }
    var month by remember{mutableStateOf(java.time.YearMonth.now())}
    var picked by remember{mutableStateOf<LocalDate?>(null)}
    val all=sessions.filter{it.endedAt!=null}.sortedByDescending{it.startedAt}
    val history=all.filter{(picked==null||Analytics.dateOf(it)==picked)&&(range=="全部"||Instant.ofEpochMilli(it.startedAt).atZone(ZoneId.systemDefault()).toLocalDate()>=LocalDate.now().minusDays(if(range=="近7天")6 else 29))}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(start=18.dp,end=18.dp,bottom=20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
        item { ProgressOverview(all) }
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("训练记录", "身体与饮食", "专业分析").forEachIndexed { i, label ->
                    FilterChip(selected = section == i, onClick = { section = i }, label = { Text(label) })
                }
            }
        }
        if (section == 1) {
            item { LifestyleBanner(R.drawable.nutrition_daylight, "好好吃饭，\n好好训练", "看长期的变化，也关心今天的感受。") }
            item { BodyPanel(body) }
            item { NutritionPanel(food) }
        }
        if (section == 2) {
            item { Text("先看趋势与建议，需要时展开计算指标。", color = Muted,
                style = MaterialTheme.typography.bodyMedium) }
            item { TrainingLoadPanel(all,days){days=it} }
            item { DiagnosticsPanel(all,exercises) }
            item { MusclePanel(all,exercises) }
            item { RecordsPanel(all) }
        }
        if (section == 0) {
        item{CalendarCard(all,month,{month=it},picked){ d -> picked = if(picked==d) null else d }}
        item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("全部","近7天","近30天").forEach{r->ChoicePill(r, range==r, {range=r})}}}
        item{Section("训练记录")}
        if(history.isEmpty())item{EmptyPanel("还没有训练记录","完成并保存一次训练，就能查看每组明细与容量。")}
        items(history,key={it.id}){s->Panel{
            Row(verticalAlignment=Alignment.CenterVertically){
                val day=Instant.ofEpochMilli(s.startedAt).atZone(ZoneId.systemDefault()).toLocalDate()
                Column(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(PaleBlue),
                    horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){
                    Text("${day.dayOfMonth}",color=Blue,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium)
                    Text("${day.monthValue} 月",color=Blue,style=MaterialTheme.typography.labelSmall)
                }
                Column(Modifier.weight(1f).padding(start=12.dp)){
                    Text(s.title,fontWeight=FontWeight.Bold)
                    Text("${stamp(s.startedAt)} · ${clock(Rules.elapsedMs(s,s.endedAt!!))}",color=Muted,style=MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick={selected=if(selected==s.id)null else s.id}){Text(if(selected==s.id)"收起" else "详情")}
            }
            if(selected==s.id){
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                StatTile("${s.movements.sumOf{it.sets.count{it.done}}}","完成组",Modifier.weight(1f))
                StatTile(number(Rules.volumeKg(s)),"容量 kg",Modifier.weight(1f))
                StatTile(kg(Progression.bestOneRm(s)),"最佳 1RM",Modifier.weight(1f), accent=Blue)
            }
            s.movements.forEach{m->
                HorizontalDivider(color=Page)
                Row(verticalAlignment=Alignment.CenterVertically){
                    Text(m.name,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f))
                    m.supersetGroup?.let{g->Text("超级组 ${('A'.code+g-1).toChar()}",color=Blue,style=MaterialTheme.typography.labelSmall)}
                }
                m.sets.forEachIndexed{i,x->Text("${i+1}  ·  ${kg(x.grams)} kg × ${x.reps}  ·  ${x.effectiveKind.label}${x.rpe?.let{"  ·  RPE $it"}?:""}  ·  ${if(x.countsVolume)"计入容量" else if(x.done)"不计容量" else "未完成"}",style=MaterialTheme.typography.bodySmall,color=Muted)}
                val exercise=exercises.find{it.id==m.exerciseId}
                if(exercise!=null){
                    val targetReps=plans.find{it.id==s.planId}?.reps?:10
                    val earlier=all.filter{it.startedAt<s.startedAt}
                    val suggestion=Progression.suggestFor(m.exerciseId,earlier,targetReps,exercise.defaultGrams)
                    if(suggestion.level!=SuggestionLevel.NO_DATA){
                        Row(Modifier.fillMaxWidth().padding(top=4.dp),verticalAlignment=Alignment.CenterVertically){
                            Column(Modifier.weight(1f)){
                                Text(suggestion.headline,color=if(suggestion.level==SuggestionLevel.PROGRESS)Color(0xFF2C8C63) else Muted,style=MaterialTheme.typography.labelLarge)
                                Text(suggestion.detail,color=Muted,style=MaterialTheme.typography.bodySmall)
                            }
                            if(suggestion.level==SuggestionLevel.PROGRESS||suggestion.level==SuggestionLevel.DELOAD)
                                TextButton(onClick={vm.applySuggestion(exercise,suggestion.deltaGrams)}){Text("应用到默认重量")}
                        }
                    }
                }
            }
                TextButton(onClick={a.ask("永久删除这条训练历史？"){vm.act{vm.repo.deleteSession(s.id)}}}){Text("删除记录",color=MaterialTheme.colorScheme.error)}}
        }}
        }
    }
}

@Composable internal fun NutritionPage(food: List<Food>,water: List<Water>,vm: MainViewModel,a: ScreenActions){
    var date by rememberSaveable{mutableStateOf(LocalDate.now().toString())}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(start=18.dp,end=18.dp,bottom=20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
        item{Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
            TextButton(onClick={date=LocalDate.parse(date).minusDays(1).toString()}){Text("‹ 前一天")}
            Text(date,Modifier.weight(1f),style=MaterialTheme.typography.labelLarge)
            TextButton(onClick={date=LocalDate.parse(date).plusDays(1).toString()}){Text("后一天 ›")}
        }}
        item { LifestyleBanner(R.drawable.nutrition_daylight, "一餐一记，\n轻松了解", "选食物、填重量，自动估算营养。") }
        item{NutritionSummary(food,water,date,a,detailed=true)}
        listOf("早餐","午餐","晚餐","加餐").forEach{meal->
            val entries=food.filter{it.date==date&&it.meal==meal}
            item{Section(meal,"＋ 记录",{a.auxiliary("meal:$meal:$date")})}
            if(entries.isEmpty())item{Text("暂无记录",Modifier.padding(start=4.dp),color=Muted,style=MaterialTheme.typography.bodySmall)}
            items(entries,key={it.id}){f->Panel{Row{Column(Modifier.weight(1f)){Text(f.name,fontWeight=FontWeight.SemiBold)
                    f.grams?.let { Text("${number(it)}g · ${f.preparation.orEmpty()}", color=Muted, style=MaterialTheme.typography.labelSmall) }
                    Text("≈ ${number(f.calories)} kcal",color=Blue)};TextButton(onClick={a.food(f)}){Text("编辑")};TextButton(onClick={a.ask("删除此饮食记录？"){vm.act{vm.repo.deleteFood(f.id)}}}){Text("删除",color=Muted)}};DetailDisclosure("这份食物的营养") {
                        Text("蛋白 ${number(f.protein)}g · 碳水 ${number(f.carbs)}g · 脂肪 ${number(f.fat)}g",color=Muted,style=MaterialTheme.typography.bodySmall)
                    }}}
        }
        item{Section("饮水记录","＋ 记录",{a.auxiliary("water:$date")})}
        items(water.filter{it.date==date},key={it.id}){w->Panel{Row(verticalAlignment=Alignment.CenterVertically){Text("${w.ml} ml",Modifier.weight(1f),fontWeight=FontWeight.SemiBold);TextButton(onClick={a.ask("删除此饮水记录？"){vm.act{vm.repo.deleteWater(w.id)}}}){Text("删除")}}}}
    }
}

@Composable private fun TrainingLoadPanel(sessions: List<Session>, days: Int, onDays: (Int) -> Unit) {
    val daily = remember(sessions, days) { Analytics.daily(sessions, days) }
    val volume = daily.sumOf { it.volumeKg }
    val count = daily.sumOf { it.sessions }
    val minutes = daily.sumOf { it.minutes }
    val streak = remember(sessions) { Analytics.streak(sessions) }
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("训练负荷", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge)
            listOf(7, 30, 90).forEach { d ->
                FilterChip(selected = days == d, onClick = { onDays(d) },
                    label = { Text("${d}天", style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.padding(start = 4.dp))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(number(volume), "容量 kg", Modifier.weight(1f), accent = Blue)
            StatTile(count.toString(), "训练次数", Modifier.weight(1f))
            StatTile(streak.toString(), "连续天数", Modifier.weight(1f))
        }
        BarChart(values = daily.map { it.volumeKg.toFloat() },
            labels = daily.map { "${it.date.monthValue}/${it.date.dayOfMonth}" },
            labelEvery = when { days <= 7 -> 1; days <= 30 -> 4; else -> 10 })
        Text("合计 $minutes 分钟 · 每天容量（kg）随训练记录累积", color = Muted,
            style = MaterialTheme.typography.bodySmall)
    }
}

@Composable private fun BodyPanel(body: List<Body>) {
    Panel {
        Text("身体趋势", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
        if (body.isEmpty()) {
            Text("记录体重后生成趋势曲线。", color = Muted, style = MaterialTheme.typography.bodyMedium)
            return@Panel
        }
        val ordered = body.sortedBy { it.date }
        val latest = ordered.last()
        val delta = (latest.grams - ordered.first().grams) / 1000.0
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(kg(latest.grams), "最近体重 kg", Modifier.weight(1f), accent = Blue)
            StatTile((if (delta >= 0) "+" else "") + String.format(java.util.Locale.US, "%.1f", delta),
                "自首次变化 kg", Modifier.weight(1f),
                accent = if (delta <= 0) Color(0xFF2C8C63) else Ink)
            StatTile(latest.fatPercent?.let { number(it) + "%" } ?: "—", "最近体脂", Modifier.weight(1f))
        }
        LineChart(ordered.map { it.grams / 1000f }, Modifier.fillMaxWidth().height(90.dp))
        Text("${ordered.size} 条记录 · ${ordered.first().date} 至 ${ordered.last().date}", color = Muted,
            style = MaterialTheme.typography.bodySmall)
    }
}

@Composable private fun NutritionPanel(food: List<Food>) {
    val today = LocalDate.now()
    val macros = remember(food) { Analytics.macros(food, today.minusDays(6), today) }
    val average = remember(food) { Analytics.averageCalories(food, 7, today) }
    Panel {
        Text("近 7 天饮食", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            MacroRing(macros.protein, macros.carbs, macros.fat, Modifier.size(96.dp))
            Column(Modifier.weight(1f).padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("日均 ${number(average)} kcal", fontWeight = FontWeight.SemiBold)
                Text("蛋白 ${number(macros.protein)} g · 碳水 ${number(macros.carbs)} g · 脂肪 ${number(macros.fat)} g",
                    color = Muted, style = MaterialTheme.typography.bodySmall)
                Text("按有记录的日期平均，不是每周固定配额。", color = Muted, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable private fun MusclePanel(sessions: List<Session>, exercises: List<Exercise>) {
    val distribution = remember(sessions, exercises) { Analytics.volumeByMuscle(sessions, exercises) }
    Panel {
        Text("肌群容量分布", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
        if (distribution.isEmpty()) {
            Text("完成带重量的正式组后统计。", color = Muted, style = MaterialTheme.typography.bodyMedium)
            return@Panel
        }
        val total = distribution.sumOf { it.second }.coerceAtLeast(1.0)
        distribution.take(6).forEach { (muscle, volume) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(muscle, Modifier.width(56.dp), style = MaterialTheme.typography.bodyMedium)
                ShareBar((volume / total).toFloat(), Modifier.weight(1f).height(8.dp))
                Text("${number(volume)} kg", Modifier.width(84.dp), color = Muted,
                    style = MaterialTheme.typography.labelSmall, textAlign = androidx.compose.ui.text.style.TextAlign.End)
            }
        }
    }
}

@Composable private fun RecordsPanel(sessions: List<Session>) {
    val records = remember(sessions) { Analytics.records(sessions) }
    Panel {
        Text("个人纪录", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
        if (records.isEmpty()) {
            Text("完成正式组后按 Epley 估算记录最佳表现。", color = Muted,
                style = MaterialTheme.typography.bodyMedium)
            return@Panel
        }
        records.take(6).forEach { r ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(r.name, fontWeight = FontWeight.SemiBold)
                    Text("${r.date} · 最重 ${kg(r.grams)} kg", color = Muted,
                        style = MaterialTheme.typography.labelSmall)
                }
                Text("${kg(r.oneRm)} kg", color = Blue, fontWeight = FontWeight.Bold)
            }
        }
        Text("1RM 为估算值，仅用于比较同一动作的进步。", color = Muted,
            style = MaterialTheme.typography.labelSmall)
    }
}

@Composable private fun CalendarCard(sessions: List<Session>, month: java.time.YearMonth,
    onMonth: (java.time.YearMonth) -> Unit, picked: LocalDate?, onPick: (LocalDate) -> Unit) {
    val trained = remember(sessions) { sessions.map { Analytics.dateOf(it) }.toSet() }
    val offset = (month.atDay(1).dayOfWeek.value + 6) % 7
    val cells: List<LocalDate?> = List(offset) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onMonth(month.minusMonths(1)) }, contentPadding = PaddingValues(8.dp)) { Text("‹") }
            Text("${month.year} 年 ${month.monthValue} 月", Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontWeight = FontWeight.Bold)
            TextButton(onClick = { onMonth(month.plusMonths(1)) }, contentPadding = PaddingValues(8.dp)) { Text("›") }
        }
        Row(Modifier.fillMaxWidth()) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach {
                Text(it, Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = Muted, style = MaterialTheme.typography.labelSmall)
            }
        }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                        if (day != null) {
                            val isTrained = day in trained
                            val isPicked = day == picked
                            Box(Modifier.fillMaxSize().clip(CircleShape)
                                .background(when { isPicked -> Blue; isTrained -> PaleBlue; else -> Color.Transparent })
                                .clickable { onPick(day) }, contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("${day.dayOfMonth}", style = MaterialTheme.typography.bodySmall,
                                        color = when { isPicked -> Color.White; isTrained -> Blue; else -> Muted },
                                        fontWeight = if (isPicked || isTrained) FontWeight.Bold else FontWeight.Normal)
                                    if (isTrained && !isPicked)
                                        Box(Modifier.size(3.dp).clip(CircleShape).background(Blue))
                                }
                            }
                        }
                    }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        if (picked != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${picked.monthValue} 月 ${picked.dayOfMonth} 日 · ${sessions.count { Analytics.dateOf(it) == picked }} 次训练",
                    color = Blue, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { onPick(picked) }) { Text("查看全部") }
            }
        }
    }
}

@Composable private fun ScheduleCard(plans: List<Plan>, active: Session?, busy: Boolean, vm: MainViewModel) {
    val today = LocalDate.now()
    val dow = today.dayOfWeek.value
    val names = listOf("一", "二", "三", "四", "五", "六", "日")
    val todayPlans = plans.filter { dow in it.weekdays }
    val upcoming = (1..7).map { today.plusDays(it.toLong()) }
        .map { d -> d to plans.filter { d.dayOfWeek.value in it.weekdays } }
        .filter { it.second.isNotEmpty() }.take(3)
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("今日安排", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f))
            Text("${today.monthValue}/${today.dayOfMonth} 周${names[dow - 1]}", color = Muted,
                style = MaterialTheme.typography.labelMedium)
        }
        if (todayPlans.isEmpty()) {
            Text("今天没有排期。给计划设一个重复日，或从下面的模板开始。", color = Muted,
                style = MaterialTheme.typography.bodyMedium)
        } else {
            todayPlans.forEach { p ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(PaleBlue),
                        contentAlignment = Alignment.Center) { AppSymbol(2, Blue, Modifier.size(20.dp)) }
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(p.name, fontWeight = FontWeight.SemiBold)
                        Text("${p.exerciseIds.size} 个动作 · ${p.sets} 组 × ${p.reps} 次", color = Muted,
                            style = MaterialTheme.typography.labelSmall)
                    }
                    Button(enabled = active == null && !busy, onClick = { vm.act { vm.repo.start(p.id) } },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) { Text("开始") }
                }
            }
        }
        if (upcoming.isNotEmpty()) {
            HorizontalDivider(color = Page)
            Text("即将到来", color = Muted, style = MaterialTheme.typography.labelMedium)
            upcoming.forEach { (d, ps) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${d.monthValue}/${d.dayOfMonth} 周${names[d.dayOfWeek.value - 1]}",
                        color = Muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(88.dp))
                    Text(ps.joinToString(" · ") { it.name }, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable private fun TemplateLibrary(vm: MainViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("找到你的训练方式", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Text("${Templates.all.size} 套计划", color = Muted, style = MaterialTheme.typography.labelSmall)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Templates.all.forEachIndexed { index, template ->
                val dark = index % 3 == 0
                val foreground = if (dark) Color.White else Ink
                Surface(shape = RoundedCornerShape(24.dp), color = if (dark) Night else Color.White,
                    border = BorderStroke(1.dp, if (dark) Night else Hairline), modifier = Modifier.width(220.dp)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("PROGRAM / ${"%02d".format(index + 1)}", color = if (dark) Volt else Blue,
                                style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f))
                            AppSymbol(2, if (dark) Volt else Blue, Modifier.size(26.dp))
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(template.name, color = foreground, style = MaterialTheme.typography.titleLarge)
                        Text(template.subtitle, color = if (dark) Color(0xFFBAC3B1) else Muted,
                            style = MaterialTheme.typography.bodySmall)
                        Text("${template.exercises.size} 个动作  ·  ${template.sets} 组 × ${template.reps} 次",
                            color = if (dark) Color(0xFFBAC3B1) else Muted, style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.height(3.dp))
                        OutlinedButton(onClick = { vm.applyTemplate(template) }, modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, if (dark) Volt else Hairline),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = if (dark) Volt else Blue)) {
                            Text("加入我的计划", modifier = Modifier.weight(1f))
                            Text("＋")
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun DiagnosticsBanner(alerts: List<String>) {
    Surface(color = Color(0xFFFFF2E2), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(Color(0xFFE59B42).copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center) { AppSymbol(8, Color(0xFFE59B42), Modifier.size(19.dp)) }
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text("训练提醒", fontWeight = FontWeight.Bold, color = Color(0xFFB46A12))
                Text(alerts.first(), style = MaterialTheme.typography.bodySmall, color = Ink)
                if (alerts.size > 1) DetailDisclosure("其他训练提醒") {
                    alerts.drop(1).forEach { Text(it, color = Ink, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

@Composable private fun DiagnosticsPanel(sessions: List<Session>, exercises: List<Exercise>) {
    val today = LocalDate.now()
    val risk = remember(sessions) { Diagnostics.loadRisk(sessions, today) }
    val muscles = remember(sessions, exercises) { Diagnostics.muscleBalance(sessions, exercises, today) }
    val missing = remember(sessions, exercises) { Diagnostics.missingMuscles(sessions, exercises, today) }
    val plateaus = remember(sessions, exercises) { Diagnostics.plateaus(sessions, exercises, today) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("训练诊断", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("负荷与恢复", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                val (color, label) = when (risk.level) {
                    RiskLevel.HIGH -> Color(0xFFE5534B) to "偏高"
                    RiskLevel.ELEVATED -> Color(0xFFE59B42) to "上升快"
                    RiskLevel.UNDER -> Color(0xFF7C5CE0) to "偏低"
                    RiskLevel.OK -> Color(0xFF2C8C63) to "平稳"
                }
                Text(label, color = color, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(9.dp)).background(color.copy(alpha = 0.12f))
                        .padding(horizontal = 9.dp, vertical = 3.dp), style = MaterialTheme.typography.labelMedium)
            }
            Text(risk.advice, color = Ink, style = MaterialTheme.typography.bodyMedium)
            DetailDisclosure("负荷计算指标") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(number(risk.acuteKg), "本周容量 kg", Modifier.weight(1f))
                StatTile(number(risk.chronicKg), "周均容量 kg", Modifier.weight(1f))
                StatTile("%.2f".format(risk.ratio), "急慢比", Modifier.weight(1f), accent = Blue)
            }
            Text("急慢比是近期与既往训练容量的比较，用于观察训练变化。", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
        Panel {
            Text("本周肌群组数", fontWeight = FontWeight.Bold)
            if (muscles.isEmpty()) {
                Text("完成训练后统计每个肌群的正式组数。", color = Muted, style = MaterialTheme.typography.bodySmall)
            } else {
                muscles.take(7).forEach { load ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(load.muscle, Modifier.width(46.dp), style = MaterialTheme.typography.bodyMedium)
                        ShareBar((load.sets / 22f).toFloat(), Modifier.weight(1f).height(8.dp),
                            color = when (load.status) {
                                MuscleStatus.LOW -> Color(0xFFE59B42)
                                MuscleStatus.HIGH -> Color(0xFFE5534B)
                                MuscleStatus.OK -> Color(0xFF2C8C63)
                            })
                        Text("${load.sets} 组", Modifier.width(46.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.End, color = Muted,
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
                Text("绿 10–20 组 / 橙 偏少 / 红 偏多（每周每肌群）", color = Muted,
                    style = MaterialTheme.typography.labelSmall)
                if (missing.isNotEmpty()) Text("本周未练：${missing.joinToString("、")}",
                    color = Color(0xFFB46A12), style = MaterialTheme.typography.bodySmall)
            }
        }
        Panel {
            Text("平台期", fontWeight = FontWeight.Bold)
            if (plateaus.isEmpty()) {
                Text("最近 4 周没有停滞动作，或样本不足 3 次。", color = Muted,
                    style = MaterialTheme.typography.bodySmall)
            } else {
                plateaus.take(4).forEach { p ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, fontWeight = FontWeight.SemiBold)
                            Text("${p.sessions} 次训练 · 最佳 1RM 变化 ${"%.1f".format(p.changePercent)}%",
                                color = Muted, style = MaterialTheme.typography.labelSmall)
                        }
                        Text(kg(p.bestOneRm) + " kg", color = Blue, style = MaterialTheme.typography.labelLarge)
                    }
                }
                Text("建议：换到 6–8 次或 12–15 次区间，或安排一周减载后再冲。", color = Muted,
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
