package com.sachalou.spacesaver

import java.time.LocalDate
import java.time.ZoneId

data class GmailRules(
    val categoriesEnabled: Boolean = true,
    val promotions: Boolean = true,
    val social: Boolean = true,
    val categoryYears: Int = 2,
    val allEnabled: Boolean = false,
    val allYears: Int = 10,
    val protectStarred: Boolean = true,
    val protectImportant: Boolean = true,
    val protectUnread: Boolean = false
) {
    val valid: Boolean get() = (categoriesEnabled && (promotions || social) || allEnabled) &&
        categoryYears in 1..30 && allYears in 1..30

    fun freeze(today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): FrozenGmailRules {
        require(valid) { "Choisis au moins une règle et un âge de 1 à 30 ans." }
        return FrozenGmailRules(this,
            today.minusYears(categoryYears.toLong()).atStartOfDay(zone).toInstant().toEpochMilli(),
            today.minusYears(allYears.toLong()).atStartOfDay(zone).toInstant().toEpochMilli())
    }
}

data class GmailMessage(val id: String, val subject: String, val sender: String,
    val dateMillis: Long, val sizeEstimate: Long, val labels: Set<String>)

data class FrozenGmailRules(val rules: GmailRules, val categoryBefore: Long, val allBefore: Long) {
    fun query(): String {
        val branches = mutableListOf<String>()
        if (rules.categoriesEnabled && (rules.promotions || rules.social)) {
            val categories = buildList {
                if (rules.promotions) add("category:promotions")
                if (rules.social) add("category:social")
            }
            branches.add("({${categories.joinToString(" ")}} before:${categoryBefore / 1000})")
        }
        if (rules.allEnabled) branches.add("(before:${allBefore / 1000})")
        require(branches.isNotEmpty())
        return buildList {
            add("(${branches.joinToString(" OR ")})")
            add("-in:trash -in:spam -in:drafts -in:sent")
            if (rules.protectStarred) add("-is:starred")
            if (rules.protectImportant) add("-is:important")
            if (rules.protectUnread) add("-is:unread")
        }.joinToString(" ")
    }

    // Rechecked against fresh metadata immediately before each move to Trash.
    fun matches(mail: GmailMessage): Boolean {
        if (mail.dateMillis <= 0 || mail.labels.any { it in setOf("TRASH", "SPAM", "DRAFT", "SENT") }) return false
        if (rules.protectStarred && "STARRED" in mail.labels) return false
        if (rules.protectImportant && "IMPORTANT" in mail.labels) return false
        if (rules.protectUnread && "UNREAD" in mail.labels) return false
        val categoryMatch = rules.categoriesEnabled && mail.dateMillis < categoryBefore &&
            (rules.promotions && "CATEGORY_PROMOTIONS" in mail.labels || rules.social && "CATEGORY_SOCIAL" in mail.labels)
        return categoryMatch || rules.allEnabled && mail.dateMillis < allBefore
    }
}

data class GmailPage(val ids: List<String>, val nextPage: String?, val estimate: Int)
interface GmailGateway {
    suspend fun profile(): String
    suspend fun list(query: String, pageToken: String?): GmailPage
    suspend fun message(id: String): GmailMessage
    suspend fun trash(id: String)
}
class GmailFailure(val publicMessage: String, val status: Int = 0) : Exception(publicMessage)

data class CleanupOutcome(val moved: Set<String>, val skipped: Set<String>, val stopped: Boolean,
    val error: String? = null, val uncertainId: String? = null)

suspend fun trashReviewed(
    gateway: GmailGateway, account: String, ids: Set<String>, rules: FrozenGmailRules,
    shouldStop: () -> Boolean = { false }, onProgress: (Int, Int) -> Unit = { _, _ -> }
): CleanupOutcome {
    require(ids.size <= 500) { "Maximum 500 messages par opération." }
    if (gateway.profile() != account) throw GmailFailure("Le compte Google a changé. Reconnecte-toi et relance l’analyse.")
    val moved = mutableSetOf<String>()
    val skipped = mutableSetOf<String>()
    for (id in ids) {
        if (shouldStop()) return CleanupOutcome(moved, skipped, true)
        var mutationStarted = false
        try {
            val current = gateway.message(id)
            if (current.id != id || !rules.matches(current)) {
                skipped.add(id)
            } else {
                if (shouldStop()) return CleanupOutcome(moved, skipped, true)
                mutationStarted = true
                gateway.trash(id)
                moved.add(id)
            }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) {
            if (e is GmailFailure && e.status == 404 && !mutationStarted) skipped.add(id)
            else return CleanupOutcome(moved, skipped, true,
                (e as? GmailFailure)?.publicMessage ?: "Connexion interrompue. Relance l’analyse avant de réessayer.",
                if (mutationStarted) id else null)
        }
        onProgress(moved.size + skipped.size, ids.size)
    }
    return CleanupOutcome(moved, skipped, false)
}
