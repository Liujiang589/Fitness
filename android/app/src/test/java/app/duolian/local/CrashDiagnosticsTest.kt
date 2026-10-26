package app.duolian.local

import app.duolian.local.domain.CrashDiagnostics
import org.junit.Assert.*
import org.junit.Test

class CrashDiagnosticsTest {
    @Test fun reportPreservesVersionCauseAndFramesForOfflineDiagnosis() {
        val error = IllegalStateException("location worker", NoClassDefFoundError("missing.Locator"))
        val report = CrashDiagnostics.format("V1.23", "app.duolian.local:location_aid", "worker", "now", error)
        assertTrue(report.contains("V1.23"))
        assertTrue(report.contains("app.duolian.local:location_aid"))
        assertTrue(report.contains("java.lang.NoClassDefFoundError: missing.Locator"))
        assertTrue(report.contains("at app.duolian.local.CrashDiagnosticsTest"))
    }
    @Test fun credentialsAndLocationAssignmentsAreRedacted() {
        val text = "api_key=abcdef123456 token:private secret=other authorization=Bearer " +
            "0123456789abcdef0123456789abcdef latitude=30.123 longitude:120.456 \"lat\":30.9"
        val report = CrashDiagnostics.sanitize(text)
        for (value in listOf("abcdef123456", "private", "other", "Bearer", "0123456789abcdef0123456789abcdef", "30.123", "120.456", "30.9"))
            assertFalse(value, report.contains(value))
    }
    @Test fun hugeMessagesAndCyclicCausesProduceBoundedReports() {
        val a = IllegalStateException("a".repeat(20000))
        val b = IllegalArgumentException("b", a)
        a.initCause(b)
        val report = CrashDiagnostics.format("v", "p", "t", "now", a)
        assertTrue(report.length <= CrashDiagnostics.MAX_CHARS)
        assertEquals(1, Regex("java.lang.IllegalStateException:").findAll(report).count())
        assertEquals(1, Regex("java.lang.IllegalArgumentException:").findAll(report).count())
    }
}
