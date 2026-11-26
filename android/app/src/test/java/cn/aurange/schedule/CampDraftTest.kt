package cn.aurange.schedule

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class CampDraftTest {
    private val date = LocalDate.parse("2026-10-25")
    @Test fun matchesWebsiteTermDateAndSkipLimitsWithoutRequiringSunday() {
        assertNull(CampDraft(1, date.plusDays(1), setOf(1, 12)).error(emptyList()))
        assertNull(CampDraft(10000, LocalDate.parse("2100-12-31")).error(emptyList()))
        assertNotNull(CampDraft(0, date).error(emptyList()))
        assertNotNull(CampDraft(10001, date).error(emptyList()))
        assertNotNull(CampDraft(1, LocalDate.parse("1999-12-31")).error(emptyList()))
        assertNotNull(CampDraft(1, date, setOf(13)).error(emptyList()))
        assertNotNull(CampDraft(1, date).error(listOf(Camp("existing", 1, date))))
    }
}
