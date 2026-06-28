package com.subrosa.app.domain

import com.subrosa.app.domain.model.Checklist
import com.subrosa.app.domain.model.MatterType

/** Source of the human-authored intake checklists. */
interface ChecklistRepository {
    fun all(): List<Checklist>
    fun forType(type: MatterType): Checklist
}
