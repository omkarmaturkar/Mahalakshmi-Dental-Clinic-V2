@file:OptIn(ExperimentalLayoutApi::class)

package com.mahalakshmi.dental

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ───────────── What the patient told us / what we found ─────────────
private val complaintOptions = listOf(
    "Pain", "Swelling", "Sensitivity", "Bleeding gums", "Broken tooth", "Loose tooth",
    "Food lodging", "Bad breath", "Missing tooth", "Check-up", "Cleaning", "Braces"
)
private val sinceOptions = listOf(
    "Today" to "since today", "2–3 days" to "since 2–3 days", "1 week" to "since 1 week",
    "1 month" to "since 1 month", "Long time" to "for a long time"
)

private fun tidyComplaint(s: String): String =
    s.replace(Regex("(\\s*,\\s*)+"), ", ").replace(Regex("\\s{2,}"), " ")
        .trim().trim(',').trim().replaceFirstChar { it.uppercase() }

/** Adds the word (before any "since …" part) or takes it out again. */
private fun toggleComplaint(text: String, word: String): String {
    val at = text.indexOf(word, ignoreCase = true)
    if (at >= 0) return tidyComplaint(text.removeRange(at, at + word.length))
    val t = text.trim()
    if (t.isEmpty()) return word
    val sinceAt = sinceOptions.map { (_, p) -> t.indexOf(" $p", ignoreCase = true) }.filter { it >= 0 }.minOrNull()
    return if (sinceAt != null) tidyComplaint(t.substring(0, sinceAt) + ", " + word.lowercase() + t.substring(sinceAt))
    else "$t, ${word.lowercase()}"
}

/** Sets how long it has been (only one), or takes it out when tapped again. */
private fun toggleSince(text: String, phrase: String): String {
    val had = text.contains(phrase, ignoreCase = true)
    var t = text
    sinceOptions.forEach { (_, p) -> t = t.replace(p, "", ignoreCase = true) }
    t = tidyComplaint(t)
    return when {
        had -> t
        t.isEmpty() -> phrase.replaceFirstChar { it.uppercase() }
        else -> "$t $phrase"
    }
}

/** Chief complaint: chips fill in the sentence; the doctor can type anything. */
@Composable
fun ComplaintCard(text: String, onChange: (String) -> Unit) {
    FormCard("What the patient told us", "Chief complaint: tap to fill in, or type in the patient's words") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            complaintOptions.forEach { c ->
                ChoiceChip(c, text.contains(c, ignoreCase = true)) { onChange(toggleComplaint(text, c)) }
            }
        }
        Text("Since", fontSize = 13.sp, color = Brand.Sub, fontWeight = FontWeight.Medium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            sinceOptions.forEach { (label, phrase) ->
                ChoiceChip(label, text.contains(phrase, ignoreCase = true)) { onChange(toggleSince(text, phrase)) }
            }
        }
        AppField(text, onChange, "In the patient's words", lines = 2)
    }
}

/** On examination: optional findings. */
@Composable
fun FindingsCard(text: String, onChange: (String) -> Unit) {
    FormCard("What we found", "On examination (optional)") {
        AppField(text, onChange, "Findings, e.g. deep cavity in 46, tender on tapping", lines = 2)
    }
}
