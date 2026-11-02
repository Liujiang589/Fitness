package app.duolian.local.domain

enum class StepSampleDecision { REJECT, BASELINE, COUNT }

/** One subscription generation. Validate BEFORE updating either daily or workout baselines. */
class StepSampleGate {
    private var lastSample: Long? = null
    private var subscribedAt = 0L
    fun reset(now: Long = 0L) { lastSample = null; subscribedAt = now }
    fun accept(counter: Long, sample: Long, now: Long): StepSampleDecision {
        if (counter < 0 || sample < subscribedAt || sample > now || now - sample > 15000 ||
            lastSample?.let { sample <= it } == true) return StepSampleDecision.REJECT
        val first = lastSample == null
        lastSample = sample
        return if (first) StepSampleDecision.BASELINE else StepSampleDecision.COUNT
    }
}
