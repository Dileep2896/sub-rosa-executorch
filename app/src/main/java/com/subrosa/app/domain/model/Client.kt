package com.subrosa.app.domain.model

import kotlinx.serialization.Serializable

/**
 * A client of the practice. Cases (matters) and documents are organized under a client. Contact
 * fields are optional — it's privileged data, stored locally and encrypted at rest.
 */
@Serializable
data class Client(
    val id: String,
    val name: String,
    val phone: String? = null,
    val email: String? = null,
    val address: String? = null,
    val note: String? = null,
    val createdAtMs: Long,
)
