package com.summer.core.data.local.model

import androidx.room.Embedded
import androidx.room.Relation
import com.summer.core.data.local.entities.SmsNerEntity
import com.summer.core.data.local.entities.SmsNerExtractionEntity

data class CompletedNerExtraction(
    @Embedded
    val extraction: SmsNerExtractionEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "extraction_id",
    )
    val entities: List<SmsNerEntity>,
)
