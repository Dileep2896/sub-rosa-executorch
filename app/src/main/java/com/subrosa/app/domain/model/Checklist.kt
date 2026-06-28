package com.subrosa.app.domain.model

import kotlinx.serialization.Serializable

/** Matter types we ship human-authored intake checklists for. */
@Serializable
enum class MatterType {
    CONTRACT_DISPUTE,
    LANDLORD_TENANT,
    EMPLOYMENT,
    PERSONAL_INJURY,
    FAMILY,
    IMMIGRATION,
    ESTATE_PROBATE,
    SMALL_CLAIMS,
    OTHER,
}

@Serializable
data class ChecklistItem(
    val id: String,
    val label: String,
    /** Optional clarifying note for the lawyer; not sent to the model. */
    val hint: String? = null,
)

/**
 * A human-authored list of standard intake elements for a [MatterType]. This is the app's legal
 * competence: knowledge lives in DATA, not model weights. "Missing info" is the set-difference of
 * these items against what the transcript actually covered.
 */
@Serializable
data class Checklist(
    val matterType: MatterType,
    val displayName: String,
    val items: List<ChecklistItem>,
)
