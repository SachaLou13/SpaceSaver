package com.sachalou.spacesaver

import java.time.LocalDate
import java.time.ZoneId

enum class AgeUnit { MONTHS, YEARS }
data class MailAge(val amount: Int = 2, val unit: AgeUnit = AgeUnit.YEARS) {
    val valid get() = amount in 1..(if (unit == AgeUnit.MONTHS) 360 else 30)
    fun cutoff(today: LocalDate, zone: ZoneId): Long {
        require(valid)
        val date = if (unit == AgeUnit.MONTHS) today.minusMonths(amount.toLong()) else today.minusYears(amount.toLong())
        return date.atStartOfDay(zone).toInstant().toEpochMilli()
    }
}
data class GmailRules(
    val promotions: Boolean = true, val promotionsAge: MailAge = MailAge(6, AgeUnit.MONTHS),
    val social: Boolean = true, val socialAge: MailAge = MailAge(1),
    val notifications: Boolean = true, val notificationsAge: MailAge = MailAge(2),
    val newsletters: Boolean = false, val newslettersAge: MailAge = MailAge(6, AgeUnit.MONTHS),
    val allEnabled: Boolean = false, val allAge: MailAge = MailAge(10),
    val largeEnabled: Boolean = false, val largeAge: MailAge = MailAge(2), val largeMegabytes: Int = 10,
    val protectStarred: Boolean = true, val protectImportant: Boolean = true, val protectUnread: Boolean = false
) {
    val valid get() = (promotions || social || notifications || newsletters || allEnabled || largeEnabled) &&
        listOf(promotionsAge, socialAge, notificationsAge, newslettersAge, allAge, largeAge).all { it.valid } && largeMegabytes in 1..100
    fun freeze(today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): FrozenGmailRules {
        require(valid)
        return FrozenGmailRules(this, promotionsAge.cutoff(today, zone), socialAge.cutoff(today, zone),
            notificationsAge.cutoff(today, zone), newslettersAge.cutoff(today, zone), allAge.cutoff(today, zone), largeAge.cutoff(today, zone))
    }
}
data class GmailMessage(val id: String, val subject: String, val sender: String,
    val dateMillis: Long, val sizeEstimate: Long, val labels: Set<String>, val subscriptionHint: Boolean = false)

// Presence of standard mailing-list headers is indicative, not a guarantee of marketing content.
fun hasSubscriptionHint(listId: String, unsubscribe: String): Boolean = listId.isNotBlank() || unsubscribe.isNotBlank()

data class FrozenGmailRules(val rules: GmailRules, val promotionsBefore: Long, val socialBefore: Long,
    val notificationsBefore: Long, val newslettersBefore: Long, val allBefore: Long, val largeBefore: Long) {
    fun query(): String {
        val branches = buildList {
            if (rules.promotions) add("(category:promotions before:${promotionsBefore / 1000})")
            if (rules.social) add("(category:social before:${socialBefore / 1000})")
            if (rules.notifications) add("(category:updates before:${notificationsBefore / 1000})")
            // Gmail has no documented arbitrary-header search: inspect metadata locally instead.
            if (rules.newsletters) add("(before:${newslettersBefore / 1000})")
            if (rules.allEnabled) add("(before:${allBefore / 1000})")
            if (rules.largeEnabled) add("(larger:${rules.largeMegabytes.toLong() * 1_000_000} before:${largeBefore / 1000})")
        }
        require(branches.isNotEmpty())
        return buildList {
            add("(${branches.joinToString(" OR ")})")
            add("-in:trash -in:spam -in:drafts -in:sent")
            if (rules.protectStarred) add("-is:starred")
            if (rules.protectImportant) add("-is:important")
            if (rules.protectUnread) add("-is:unread")
        }.joinToString(" ")
    }
    fun matches(mail: GmailMessage): Boolean {
        if (mail.dateMillis <= 0 || mail.labels.any { it in setOf("TRASH", "SPAM", "DRAFT", "SENT") }) return false
        if (rules.protectStarred && "STARRED" in mail.labels) return false
        if (rules.protectImportant && "IMPORTANT" in mail.labels) return false
        if (rules.protectUnread && "UNREAD" in mail.labels) return false
        return rules.promotions && "CATEGORY_PROMOTIONS" in mail.labels && mail.dateMillis < promotionsBefore ||
            rules.social && "CATEGORY_SOCIAL" in mail.labels && mail.dateMillis < socialBefore ||
            rules.notifications && "CATEGORY_UPDATES" in mail.labels && mail.dateMillis < notificationsBefore ||
            rules.newsletters && mail.subscriptionHint && mail.dateMillis < newslettersBefore ||
            rules.allEnabled && mail.dateMillis < allBefore ||
            rules.largeEnabled && mail.sizeEstimate > rules.largeMegabytes.toLong() * 1_000_000 && mail.dateMillis < largeBefore
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
