package digital.jinatra.tidyra.core.runtime

import org.junit.Assert.*
import org.junit.Test

class RunningParserTest {
    @Test fun processesRejectMalformedAndOtherProfiles() {
        val result = RunningParser.processes("""
            UID PID NAME
            10123 23 com.example.app
            10123 24 com.example.app:worker
            1010123 25 com.example.work
            nonsense
            10123 -1 invalid
            10123 24 com.example.app:worker
        """.trimIndent(), 0)
        assertEquals(listOf(23, 24), result.map { it.pid })
    }

    @Test fun servicesRequireLiveProcessAndCorrectProfile() {
        val result = RunningParser.services("""
            ACTIVITY MANAGER SERVICES (dumpsys activity services)
              * ServiceRecord{aaa u0 com.example.app/.Worker c:com.example.app}
                app=ProcessRecord{bbb 42:com.example.app/u0a123}
                isForeground=true foregroundId=1
                * ConnectionRecord{nested ServiceRecord{not-a-real-header}}
              * ServiceRecord{ccc u0 com.example.app/.Pending}
                app=null
              * ServiceRecord{ddd u10 com.example.work/.Worker}
                app=ProcessRecord{eee 43:com.example.work/u10a123}
        """.trimIndent(), 0)
        assertEquals(1, result.size)
        assertEquals("com.example.app/.Worker", result.single().component)
        assertEquals(42, result.single().pid)
        assertTrue(result.single().foreground)
    }

    @Test fun appMatchingUsesUidAndProcessNamesInsteadOfSubstring() {
        val app = InstalledApp("com.example.app", "Example", 10123, "com.shared.process", false, null)
        val snapshot = RunningSnapshot(processes = listOf(
            RunningProcess(10123, 1, "com.example.app:worker"), RunningProcess(10123, 2, "com.shared.process"),
            RunningProcess(10124, 3, "com.example.app"), RunningProcess(10123, 4, "com.example.application")))
        assertEquals(listOf(1, 2), app.processes(snapshot).map { it.pid })
    }

    @Test fun emptyOrUnrecognizedServiceOutputNeverInventsRunningServices() {
        assertTrue(RunningParser.services("Permission Denial", 0).isEmpty())
        assertTrue(RunningParser.services("* ServiceRecord{abc u0 com.example.app/.Worker}\n  app=null", 0).isEmpty())
    }
}
