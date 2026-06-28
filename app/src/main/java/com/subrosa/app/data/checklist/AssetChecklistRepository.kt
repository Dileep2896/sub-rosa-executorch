package com.subrosa.app.data.checklist

import com.subrosa.app.data.AssetReader
import com.subrosa.app.domain.ChecklistRepository
import com.subrosa.app.domain.model.Checklist
import com.subrosa.app.domain.model.MatterType
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/** Loads the matter-type checklists from `assets/checklists.json`. */
class AssetChecklistRepository(
    private val assets: AssetReader,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val assetName: String = "checklists.json",
) : ChecklistRepository {

    private val checklists: List<Checklist> by lazy {
        json.decodeFromString<List<Checklist>>(assets.read(assetName))
    }

    override fun all(): List<Checklist> = checklists

    override fun forType(type: MatterType): Checklist =
        checklists.first { it.matterType == type }
}
