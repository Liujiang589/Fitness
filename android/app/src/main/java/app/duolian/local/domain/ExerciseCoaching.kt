package app.duolian.local.domain

data class ExerciseAlternative(val kind: MotionKind, val reason: String, val difference: String)

/** Curated movement relationships, never guessed from a shared muscle name or applied to user data. */
object ExerciseCoaching {
    private val cues = mapOf(
        MotionKind.BENCH to "脚踩稳、肩胛稳，再把重量推起。",
        MotionKind.SQUAT to "全脚掌支撑，膝盖与脚尖方向协调。",
        MotionKind.PULLDOWN to "让肘向身体两侧下拉，躯干保持稳定。",
        MotionKind.SEATED_ROW to "肘向后拉，不用腰来回甩动。",
        MotionKind.SHOULDER_PRESS to "肋骨与骨盆稳定，避免挺腰推起。",
        MotionKind.CURL to "上臂留在身体两侧，只让肘部屈伸。",
        MotionKind.BODY_SQUAT to "先站稳，再选择自己能控制的下蹲深度。",
        MotionKind.PUSHUP to "身体整体升降，头、躯干与腿保持稳定连线。",
        MotionKind.INCLINE_BENCH to "肩胛贴稳凳面，下放到肩部舒适的位置。",
        MotionKind.DB_BENCH to "左右手各自稳定，顶端不碰撞哑铃。",
        MotionKind.FLY to "肘保持柔和弯曲，沿可控弧线打开与合拢。",
        MotionKind.CABLE_FLY to "躯干站稳，用胸前合拢的弧线带动把手。",
        MotionKind.ROW to "先保持屈髋和背部稳定，再向后拉肘。",
        MotionKind.ONE_ARM_ROW to "支撑侧稳住，骨盆不要随哑铃扭转。",
        MotionKind.PULLUP to "握点稳定，避免摆腿借力上拉。",
        MotionKind.STRAIGHT_PULLDOWN to "肘角基本固定，避免把动作变成臂屈伸。",
        MotionKind.DB_DEADLIFT to "重量贴近身体，以屈髋带动下放。",
        MotionKind.RDL to "臀部向后移，腰背稳定，感受大腿后侧张力。",
        MotionKind.LEG_PRESS to "骨盆贴稳靠垫，膝盖方向与脚尖协调。",
        MotionKind.SPLIT_SQUAT to "前脚踩稳，先掌握平衡，再缓慢下蹲。",
        MotionKind.LEG_EXTENSION to "大腿贴稳坐垫，受控伸膝和回落。",
        MotionKind.LEG_CURL to "髋部贴稳垫面，不用抬髋甩动重量。",
        MotionKind.BRIDGE to "用臀部抬髋，顶端避免过度挺腰。",
        MotionKind.LATERAL_RAISE to "肘保持微屈，平稳抬臂，不耸肩甩起。",
        MotionKind.FRONT_RAISE to "保持躯干稳定，手臂向前抬起不过度借力。",
        MotionKind.REVERSE_FLY to "屈髋站稳，手臂向两侧打开，不甩腰。",
        MotionKind.FACE_PULL to "绳索拉向面部两侧，保持肩部可控。",
        MotionKind.ARNOLD to "旋转与推举平稳衔接，腰背保持稳定。",
        MotionKind.HAMMER_CURL to "掌心相对，上臂稳定，控制哑铃下放。",
        MotionKind.BARBELL_CURL to "双肘靠近体侧，避免后仰借力。",
        MotionKind.CABLE_CURL to "上臂稳定，让绳索张力随肘部屈伸变化。",
        MotionKind.PUSHDOWN to "上臂靠近体侧，伸肘下压而不是压低整个肩膀。",
        MotionKind.OVERHEAD_TRICEPS to "上臂靠近头侧，伸肘时不要过度挺腰。",
        MotionKind.LYING_TRICEPS to "上臂稳定，缓慢屈伸肘部，重量始终可控。",
        MotionKind.CRUNCH to "收紧腹部卷起上背，不拉扯头颈。",
        MotionKind.HANGING_RAISE to "先稳住悬垂，再抬腿，避免摆荡。",
        MotionKind.REVERSE_CRUNCH to "轻轻卷起骨盆，不靠甩腿弹起。",
        MotionKind.TWIST to "胸口随躯干转动，不只快速甩手。",
        MotionKind.STANDING_CALF to "以前脚掌支撑，脚踝平稳升降。",
        MotionKind.SEATED_CALF to "保持屈膝坐稳，让脚跟带动垫块上移。"
    )
    fun cue(guide: ExerciseGuide) = cues.getValue(guide.kind)
    private fun option(kind: MotionKind, reason: String, difference: String) = ExerciseAlternative(kind, reason, difference)
    private val alternatives: Map<MotionKind, List<ExerciseAlternative>> = buildMap {
        val press = listOf(MotionKind.BENCH, MotionKind.DB_BENCH, MotionKind.PUSHUP)
        press.forEach { source -> put(source, press.filter { it != source }.map { target ->
            option(target, "胸部推的另一种练法", when (target) {
                MotionKind.BENCH -> "需要杠铃、训练凳和合适保护条件；不沿用原动作重量。"
                MotionKind.DB_BENCH -> "需要哑铃和训练凳；左右手独立稳定，重量重新选择。"
                else -> "无需自由重量，但需支撑自身体重；难度并不一定更低。"
            }) }) }
        put(MotionKind.INCLINE_BENCH, listOf(option(MotionKind.DB_BENCH, "没有上斜凳时，可了解平板推举", "凳面角度和胸部受力侧重改变，不是同角度替换。")))
        put(MotionKind.FLY, listOf(option(MotionKind.CABLE_FLY, "换用绳索完成胸部合拢", "需要龙门架；站姿稳定和阻力方向不同。")))
        put(MotionKind.CABLE_FLY, listOf(option(MotionKind.FLY, "换用哑铃完成胸部合拢", "需要训练凳；阻力曲线改变，下放幅度重新控制。")))
        val rows = listOf(MotionKind.SEATED_ROW, MotionKind.ROW, MotionKind.ONE_ARM_ROW)
        rows.forEach { source -> put(source, rows.filter { it != source }.map { target ->
            option(target, "水平拉的另一种器械选择", when (target) {
                MotionKind.SEATED_ROW -> "坐姿有脚部和凳面支撑，需要划船器械。"
                MotionKind.ROW -> "需要杠铃；屈髋姿势对躯干稳定要求不同。"
                else -> "使用哑铃与稳定支撑，单侧完成，避免身体旋转。"
            }) }) }
        put(MotionKind.PULLUP, listOf(option(MotionKind.PULLDOWN, "希望用器械调节上拉阻力", "需要高位下拉器械；坐姿固定与自重悬垂不同。")))
        put(MotionKind.PULLDOWN, listOf(option(MotionKind.PULLUP, "已有稳定悬垂和自重上拉能力时", "需要牢固横杆；自重难度通常更高，不作为默认降阶。")))
        put(MotionKind.STRAIGHT_PULLDOWN, listOf(option(MotionKind.PULLDOWN, "背阔肌训练的多关节变化", "增加肘屈伸和手臂参与，动作模式改变。")))
        put(MotionKind.SQUAT, listOf(option(MotionKind.BODY_SQUAT, "先练习无外部负重的下蹲", "减少外部负重，仍需保持平衡和可控深度。"), option(MotionKind.LEG_PRESS, "选择器械支撑的腿部训练", "需要腿举机；躯干支撑和运动轨迹不同。")))
        put(MotionKind.BODY_SQUAT, listOf(option(MotionKind.LEG_PRESS, "希望在器械上调整腿部阻力", "需要腿举机；不是相同自重负荷，不直接换算重量。")))
        put(MotionKind.LEG_PRESS, listOf(option(MotionKind.BODY_SQUAT, "没有腿举机时了解无器械下蹲", "自由站立需要平衡；负重、稳定要求与器械不同。")))
        put(MotionKind.SPLIT_SQUAT, listOf(option(MotionKind.BODY_SQUAT, "希望先练双脚稳定支撑的下蹲", "由单侧侧重改为双侧动作，训练刺激并不相同。")))
        put(MotionKind.LEG_EXTENSION, listOf(option(MotionKind.LEG_PRESS, "股四头肌训练的器械变化", "由单关节伸膝改为髋膝共同发力，不是等效替换。")))
        put(MotionKind.DB_DEADLIFT, listOf(option(MotionKind.RDL, "了解杠铃屈髋训练", "需要杠铃；膝角、负重方式和幅度重新掌握。")))
        put(MotionKind.RDL, listOf(option(MotionKind.DB_DEADLIFT, "换用哑铃练习屈髋", "膝部弯曲程度和持重方式不同，先重新熟悉动作。")))
        val shoulder = listOf(MotionKind.SHOULDER_PRESS, MotionKind.ARNOLD)
        shoulder.forEach { source -> put(source, shoulder.filter { it != source }.map {
            option(it, "哑铃肩部推举的另一种变化", "阿诺德推举包含旋转；肩部活动范围与握法不同。") }) }
        put(MotionKind.REVERSE_FLY, listOf(option(MotionKind.FACE_PULL, "肩后侧与上背训练变化", "需要绳索；增加肘部弯曲和旋转控制，不是相同轨迹。")))
        put(MotionKind.FACE_PULL, listOf(option(MotionKind.REVERSE_FLY, "换用哑铃了解肩后侧训练", "需要屈髋稳定；旋转控制与面拉不同。")))
        val curls = listOf(MotionKind.CURL, MotionKind.HAMMER_CURL, MotionKind.BARBELL_CURL, MotionKind.CABLE_CURL)
        curls.forEach { source -> put(source, curls.filter { it != source }.take(2).map {
            option(it, "换一种器械或握法练习屈肘", "握法、阻力方向和前臂参与不同，重新选择重量。") }) }
        val extensions = listOf(MotionKind.PUSHDOWN, MotionKind.OVERHEAD_TRICEPS, MotionKind.LYING_TRICEPS)
        extensions.forEach { source -> put(source, extensions.filter { it != source }.map {
            option(it, "肱三头肌伸肘训练变化", "肩部位置与器械不同，先选择舒适且可控的幅度。") }) }
        put(MotionKind.HANGING_RAISE, listOf(option(MotionKind.REVERSE_CRUNCH, "暂不做悬垂时了解垫上骨盆卷起", "省去悬垂握持，但骨盆卷起与举腿动作并不相同。")))
        put(MotionKind.CRUNCH, listOf(option(MotionKind.REVERSE_CRUNCH, "腹部训练的另一种垫上动作", "由上背卷起改为骨盆卷起，不是同一动作。")))
        put(MotionKind.REVERSE_CRUNCH, listOf(option(MotionKind.CRUNCH, "了解上背卷起的腹部训练", "骨盆和脊柱的运动方式不同，保持颈部舒适。")))
        put(MotionKind.STANDING_CALF, listOf(option(MotionKind.SEATED_CALF, "了解屈膝坐姿提踵", "需要器械；膝角改变会影响小腿肌肉的训练侧重。")))
        put(MotionKind.SEATED_CALF, listOf(option(MotionKind.STANDING_CALF, "没有坐姿提踵机时", "改为站姿需保持平衡，膝角与肌肉侧重不同。")))
    }
    fun alternatives(guide: ExerciseGuide): List<ExerciseAlternative> = alternatives[guide.kind].orEmpty()
    fun guide(kind: MotionKind) = ExerciseGuides.all.single { it.kind == kind }
}
