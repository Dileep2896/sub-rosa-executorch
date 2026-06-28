package com.subrosa.app.domain.model

import kotlinx.serialization.Serializable

/**
 * A legal matter for a [Client]. A client can have several cases; each case owns its own documents
 * and its own consultations (sessions). The [matterType] fixes the intake checklist for the case, so
 * consultations under it don't re-ask the matter type.
 */
@Serializable
data class Case(
    val id: String,
    val clientId: String,
    val matterType: MatterType,
    val title: String,
    val createdAtMs: Long,
    val updatedAtMs: Long = createdAtMs,
)
