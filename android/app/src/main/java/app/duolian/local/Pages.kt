package app.duolian.local

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
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
    a: ScreenActions) {
    val fs = food.filter { it.date == date }
    val ml = water.filter { it.date == date }.sumOf { it.ml }
    val protein = fs.sumOf { it.protein }; val carbs = fs.sumOf { it.carbs }; val fat = fs.sumOf { it.fat }
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("今日营养", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f))
            Text("详细 ›", color = Blue, style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clickable { a.nutrition() })
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text("已摄入 ", color = Muted, style = MaterialTheme.typography.labelMedium)
            Text("${number(fs.sumOf { it.calories })}", style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold)
            Text(" kcal", color = Muted, style = MaterialTheme.typography.labelMedium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val tiles = listOf(
                Triple("蛋白 (g)", number(protein), Color(0xFFFFF6E8)),
                Triple("碳水 (g)", number(carbs), Color(0xFFEEF1FF)),
                Triple("脂肪 (g)", number(fat), Color(0xFFFFEEF1)),
                Triple("饮水 (ml)", ml.toString(), Color(0xFFE9F8FA)))
            tiles.forEach { (label, value, tint) ->
                Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(tint).padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(label, color = Muted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    Spacer(Modifier.height(4.dp))
                    Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
            }
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
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (active != null) item { ActiveTraining(active, vm, busy, a.ask) }
        else {
            item { NutritionSummary(food, water, LocalDate.now().toString(), a) }
            item { WeekStrip(history) }
            item { ScheduleCard(plans, active, busy, vm) }
            item { TemplateLibrary(vm) }
        }
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
            EmptyPanel("建立你的训练安排", "创建计划，选择动作，再设置每个动作的训练组数。")
        }
        items(plans, key = { it.id }) { p ->
            var menu by remember { mutableStateOf(false) }
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(PaleBlue),
                        contentAlignment = Alignment.Center) { AppSymbol(2, Blue) }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(p.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                        Text("${p.exerciseIds.size} 个动作 · ${p.sets} 组 × ${p.reps} 次", color = Muted,
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
                val tags = buildList {
                    if (p.scheme != SetScheme.STRAIGHT) add(p.scheme.label)
                    if (p.superset != SupersetMode.NONE) add(p.superset.label)
                    if (p.restSeconds > 0) add("休息 ${p.restSeconds}s")
                }
                if (tags.isNotEmpty()) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        tags.forEach { tag ->
                            Text(tag, Modifier.clip(RoundedCornerShape(9.dp)).background(Page)
                                .padding(horizontal = 9.dp, vertical = 4.dp), color = Muted,
                                style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                p.exerciseIds.take(2).forEach { id ->
                    exercises.find { it.id == id }?.let { e ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(5.dp).clip(RoundedCornerShape(3.dp)).background(Blue))
                            Text(e.name, Modifier.weight(1f).padding(start = 9.dp),
                                style = MaterialTheme.typography.bodyMedium)
                            Text("${p.sets} × ${p.reps}", style = MaterialTheme.typography.bodySmall, color = Muted)
                        }
                    }
                }
                if (p.exerciseIds.size > 2) Text("还有 ${p.exerciseIds.size - 2} 个动作", color = Muted,
                    style = MaterialTheme.typography.labelMedium)
                Button(enabled = active == null && !busy, onClick = { vm.act { vm.repo.start(p.id) } },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(14.dp)) { Text("开始训练") }
            }
        }
    }
}

@Composable private fun WeekStrip(history: List<Session>) {
    val today = LocalDate.now(); val monday = today.minusDays(today.dayOfWeek.value.toLong() - 1)
    val days = history.map { Instant.ofEpochMilli(it.startedAt).atZone(ZoneId.systemDefault()).toLocalDate() }.toSet()
    val thisWeek = days.count { it >= monday && it <= monday.plusDays(6) }
    val streak = Analytics.streak(history)
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("本周", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium)
            Text("$thisWeek 天", color = Blue, style = MaterialTheme.typography.labelLarge)
            Text(" · 连续 $streak 天", color = Muted, style = MaterialTheme.typography.labelLarge)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEachIndexed { i, label ->
                val d = monday.plusDays(i.toLong())
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(label, color = Muted, style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(7.dp))
                    Box(Modifier.size(34.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (d in days) Blue else if (d == today) PaleBlue else Page),
                        contentAlignment = Alignment.Center) {
                        Text(if (d in days) "✓" else d.dayOfMonth.toString(),
                            color = if (d in days) Color.White else if (d == today) Blue else Muted,
                            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable internal fun ExercisePage(exercises: List<Exercise>, a: ScreenActions) {
    var query by rememberSaveable{mutableStateOf("")};var muscle by rememberSaveable{mutableStateOf("全部")};var equipment by rememberSaveable{mutableStateOf("全部")}
    var favoritesOnly by rememberSaveable{mutableStateOf(false)}
    val muscles=listOf("全部")+exercises.map{it.muscle}.distinct()
    val filtered=exercises.filter{(query.isBlank()||it.name.contains(query,true))&&(muscle=="全部"||it.muscle==muscle)&&(equipment=="全部"||it.equipment==equipment)&&(!favoritesOnly||it.favorite)}
        .sortedWith(compareByDescending<Exercise>{it.favorite}.thenBy{it.name})
    Column(Modifier.fillMaxSize()){
        Row(Modifier.padding(horizontal=18.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
            AppField(query,{query=it},placeholder="搜索动作名称",modifier=Modifier.weight(1f))
            FilledIconButton(onClick={a.exercise(null)},modifier=Modifier.size(50.dp),shape=RoundedCornerShape(17.dp)){Text("＋",style=MaterialTheme.typography.headlineMedium)}
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.weight(1f)){
            LazyColumn(Modifier.width(76.dp).fillMaxHeight(),contentPadding=PaddingValues(bottom=12.dp)){
                items(muscles){m->Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(topEnd=13.dp,bottomEnd=13.dp)).background(if(m==muscle)Color.White else Color.Transparent).clickable{muscle=m}.padding(vertical=18.dp),contentAlignment=Alignment.Center){Text(m,color=if(m==muscle)Blue else Muted,fontWeight=if(m==muscle)FontWeight.Bold else FontWeight.Normal,style=MaterialTheme.typography.bodyMedium)}}
            }
            Column(Modifier.weight(1f).padding(start=10.dp,end=18.dp)){
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(7.dp)){
                    FilterChip(selected=favoritesOnly,onClick={favoritesOnly=!favoritesOnly},label={Text("★ 收藏")},shape=RoundedCornerShape(11.dp))
                    (listOf("全部")+exercises.map{it.equipment}.distinct()).forEach{e->FilterChip(selected=equipment==e,onClick={equipment=e},label={Text(e)},shape=RoundedCornerShape(11.dp))}
                }
                Row(Modifier.padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically){Text(if(muscle=="全部")"全部动作" else muscle,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f));Text("${filtered.size} 项",color=Muted,style=MaterialTheme.typography.labelMedium)}
                LazyVerticalGrid(columns=GridCells.Fixed(2),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(bottom=20.dp),modifier=Modifier.weight(1f)){
                    gridItems(filtered,key={it.id}){e->Surface(onClick={a.detail(e)},color=Color.White,shape=RoundedCornerShape(19.dp)){
                        Column(Modifier.padding(10.dp),horizontalAlignment=Alignment.CenterHorizontally){
                            if(e.mediaUri!=null)ExercisePhoto(e.mediaUri!!,Modifier.fillMaxWidth().height(94.dp).clip(RoundedCornerShape(12.dp)))
                            else ExerciseArt(e.muscle,Modifier.fillMaxWidth().height(94.dp))
                            Text((if(e.favorite)"★ " else "")+e.name,fontWeight=FontWeight.SemiBold,style=MaterialTheme.typography.bodyMedium,maxLines=2,overflow=TextOverflow.Ellipsis,modifier=Modifier.heightIn(min=42.dp))
                            Text(e.equipment,color=Muted,style=MaterialTheme.typography.labelSmall)
                        }
                    }}
                }
                if(filtered.isEmpty())Text("没有匹配的动作",Modifier.padding(12.dp),color=Muted)
            }
        }
    }
}

@Composable internal fun HistoryPage(sessions: List<Session>, exercises: List<Exercise>, plans: List<Plan>,
    body: List<Body>, food: List<Food>, vm: MainViewModel,a: ScreenActions){
    var range by rememberSaveable{mutableStateOf("全部")};var selected by rememberSaveable{mutableStateOf<String?>(null)}
    var days by rememberSaveable{mutableStateOf(30)}
    var month by remember{mutableStateOf(java.time.YearMonth.now())}
    var picked by remember{mutableStateOf<LocalDate?>(null)}
    val all=sessions.filter{it.endedAt!=null}.sortedByDescending{it.startedAt}
    val history=all.filter{(picked==null||Analytics.dateOf(it)==picked)&&(range=="全部"||Instant.ofEpochMilli(it.startedAt).atZone(ZoneId.systemDefault()).toLocalDate()>=LocalDate.now().minusDays(if(range=="近7天")6 else 29))}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(start=18.dp,end=18.dp,bottom=20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
        item{CalendarCard(all,month,{month=it},picked){ d -> picked = if(picked==d) null else d }}
        item{TrainingLoadPanel(all,days){days=it}}
        item{BodyPanel(body)}
        item{NutritionPanel(food)}
        item{MusclePanel(all,exercises)}
        item{RecordsPanel(all)}
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
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                StatTile("${s.movements.sumOf{it.sets.count{it.done}}}","完成组",Modifier.weight(1f))
                StatTile(number(Rules.volumeKg(s)),"容量 kg",Modifier.weight(1f))
                StatTile(kg(Progression.bestOneRm(s)),"最佳 1RM",Modifier.weight(1f), accent=Blue)
            }
            if(selected==s.id){s.movements.forEach{m->
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

@Composable internal fun NutritionPage(food: List<Food>,water: List<Water>,vm: MainViewModel,a: ScreenActions){
    var date by rememberSaveable{mutableStateOf(LocalDate.now().toString())}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(start=18.dp,end=18.dp,bottom=20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
        item{Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
            TextButton(onClick={date=LocalDate.parse(date).minusDays(1).toString()}){Text("‹ 前一天")}
            Text(date,Modifier.weight(1f),style=MaterialTheme.typography.labelLarge)
            TextButton(onClick={date=LocalDate.parse(date).plusDays(1).toString()}){Text("后一天 ›")}
        }}
        item{NutritionSummary(food,water,date,a)}
        listOf("早餐","午餐","晚餐","加餐").forEach{meal->
            val entries=food.filter{it.date==date&&it.meal==meal}
            item{Section(meal,"＋ 记录",{a.auxiliary("meal:$meal:$date")})}
            if(entries.isEmpty())item{Text("暂无记录",Modifier.padding(start=4.dp),color=Muted,style=MaterialTheme.typography.bodySmall)}
            items(entries,key={it.id}){f->Panel{Row{Column(Modifier.weight(1f)){Text(f.name,fontWeight=FontWeight.SemiBold);Text("${number(f.calories)} kcal",color=Blue)};TextButton(onClick={a.food(f)}){Text("编辑")};TextButton(onClick={a.ask("删除此饮食记录？"){vm.act{vm.repo.deleteFood(f.id)}}}){Text("删除",color=Muted)}};Text("蛋白 ${number(f.protein)}g · 碳水 ${number(f.carbs)}g · 脂肪 ${number(f.fat)}g",color=Muted,style=MaterialTheme.typography.bodySmall)}}
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
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("计划模板", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f))
            Text("${Templates.all.size} 套 · 点击即加入", color = Muted, style = MaterialTheme.typography.labelMedium)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Templates.all.forEach { t ->
                Surface(shape = RoundedCornerShape(18.dp), color = Color.White, modifier = Modifier.width(186.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(t.level, color = Blue, style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold)
                        Text(t.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium,
                            maxLines = 1)
                        Text(t.subtitle, color = Muted, style = MaterialTheme.typography.labelSmall,
                            maxLines = 1)
                        Text("${t.exercises.size} 动作 · ${t.sets}×${t.reps}", color = Muted,
                            style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.height(4.dp))
                        Button(onClick = { vm.applyTemplate(t) }, modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(11.dp), contentPadding = PaddingValues(vertical = 6.dp)) {
                            Text("使用", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}
