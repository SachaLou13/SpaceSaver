package com.sachalou.spacesaver

import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class GmailRulesTest {
    private val today = LocalDate.of(2026, 11, 4)
    private fun millis(date: String) = LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    private fun mail(id: String = "a", date: String = "2020-01-01", labels: Set<String> = setOf("CATEGORY_PROMOTIONS")) =
        GmailMessage(id, "Test", "sender@example.com", millis(date), 123, labels)

    @Test fun categoriesAreRestrictedByAgeAndCategory() {
        val frozen = GmailRules().freeze(today, ZoneOffset.UTC)
        assertTrue(frozen.matches(mail()))
        assertTrue(frozen.matches(mail(labels = setOf("CATEGORY_SOCIAL"))))
        assertFalse(frozen.matches(mail(labels = setOf("CATEGORY_PERSONAL"))))
        assertFalse(frozen.matches(mail(date = "2025-01-01")))
        assertFalse(frozen.matches(mail(date = "2024-11-04")))
        assertTrue(frozen.matches(mail(date = "2024-11-03")))
    }
    @Test fun combinedRulesUseUnionWithIndependentAges() {
        val frozen = GmailRules(allEnabled = true, allYears = 10).freeze(today, ZoneOffset.UTC)
        assertTrue(frozen.matches(mail(date = "2023-01-01")))
        assertTrue(frozen.matches(mail(date = "2010-01-01", labels = emptySet())))
        assertFalse(frozen.matches(mail(date = "2020-01-01", labels = emptySet())))
        assertTrue(frozen.query().contains(" OR "))
        assertTrue(frozen.query().contains("before:${millis("2024-11-04") / 1000}"))
        assertTrue(frozen.query().contains("before:${millis("2016-11-04") / 1000}"))
    }
    @Test fun allMailModeDoesNotRequireCategory() {
        val frozen = GmailRules(categoriesEnabled = false, allEnabled = true).freeze(today, ZoneOffset.UTC)
        assertTrue(frozen.matches(mail(date = "2010-01-01", labels = emptySet())))
        assertFalse(frozen.query().contains("category:"))
    }
    @Test fun protectionsApplyToEveryBranch() {
        val frozen = GmailRules(allEnabled = true, protectUnread = true).freeze(today, ZoneOffset.UTC)
        listOf("STARRED", "IMPORTANT", "UNREAD", "SENT", "DRAFT", "SPAM", "TRASH").forEach {
            assertFalse(it, frozen.matches(mail(date = "2010-01-01", labels = setOf("CATEGORY_PROMOTIONS", it))))
        }
        assertTrue(GmailRules(protectStarred = false).freeze(today, ZoneOffset.UTC)
            .matches(mail(labels = setOf("CATEGORY_PROMOTIONS", "STARRED"))))
    }
    @Test fun invalidRulesCannotBecomeQueries() {
        assertFalse(GmailRules(categoriesEnabled = false).valid)
        assertFalse(GmailRules(promotions = false, social = false).valid)
        assertFalse(GmailRules(categoryYears = 0).valid)
        assertFalse(GmailRules(allYears = 31).valid)
    }
    @Test fun leapYearUsesCalendarYears() {
        val frozen = GmailRules(categoryYears = 1).freeze(LocalDate.of(2024, 2, 29), ZoneOffset.UTC)
        assertEquals(millis("2023-02-28"), frozen.categoryBefore)
    }
    private class FakeGateway(val messages: Map<String, GmailMessage>) : GmailGateway {
        val moved = mutableListOf<String>()
        var email = "bruno@example.com"
        var failOn: String? = null
        override suspend fun profile() = email
        override suspend fun list(query: String, pageToken: String?) = GmailPage(messages.keys.toList(), null, messages.size)
        override suspend fun message(id: String) = messages[id] ?: throw GmailFailure("Absent", 404)
        override suspend fun trash(id: String) {
            if (failOn == id) throw GmailFailure("Network failure")
            moved.add(id)
        }
    }
    @Test fun cleanupUsesOnlyReviewedIdsAndRechecksProtection() = runBlocking {
        val gateway = FakeGateway(mapOf("a" to mail(), "b" to mail("b", labels = setOf("CATEGORY_PROMOTIONS", "STARRED")), "c" to mail("c")))
        val result = trashReviewed(gateway, gateway.email, setOf("a", "b"), GmailRules().freeze(today, ZoneOffset.UTC))
        assertEquals(listOf("a"), gateway.moved)
        assertEquals(setOf("b"), result.skipped)
        assertFalse(gateway.moved.contains("c"))
    }
    @Test fun accountMismatchPreventsAllWrites() = runBlocking {
        val gateway = FakeGateway(mapOf("a" to mail()))
        try {
            trashReviewed(gateway, "other@example.com", setOf("a"), GmailRules().freeze(today, ZoneOffset.UTC))
            fail("Account mismatch must fail")
        } catch (_: GmailFailure) { assertTrue(gateway.moved.isEmpty()) }
    }
    @Test fun failureStopsBatchAndReportsPartialOutcome() = runBlocking {
        val gateway = FakeGateway(listOf("a", "b", "c").associateWith { mail(it) }).apply { failOn = "b" }
        val result = trashReviewed(gateway, gateway.email, linkedSetOf("a", "b", "c"), GmailRules().freeze(today, ZoneOffset.UTC))
        assertEquals(setOf("a"), result.moved)
        assertEquals("b", result.uncertainId)
        assertEquals(listOf("a"), gateway.moved)
        assertTrue(result.stopped)
    }
    @Test fun stopDoesNotMoveFurtherMessages() = runBlocking {
        val gateway = FakeGateway(mapOf("a" to mail(), "b" to mail("b")))
        val result = trashReviewed(gateway, gateway.email, linkedSetOf("a", "b"), GmailRules().freeze(today, ZoneOffset.UTC),
            { gateway.moved.isNotEmpty() })
        assertEquals(setOf("a"), result.moved)
        assertTrue(result.stopped)
    }
}
