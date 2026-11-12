package com.paperscan.model

enum class CornerProposalSource { SAVED, AUTO_DETECTION, MANUAL }

/**
 * One editor draft shared by the overlay and the save action.
 * PageItem.corners remains the persisted source of truth; detection only replaces this draft.
 */
data class CornerEditDraft(
    val savedCorners: CornerPoints?,
    val editableCorners: CornerPoints,
    val source: CornerProposalSource,
    val detectionConfidence: DetectionConfidence? = null
) {
    val hasUnsavedChanges: Boolean
        get() = savedCorners != editableCorners

    fun moveCorner(index: Int, point: NormalizedPoint): CornerEditDraft {
        if (index !in 0..3 || !point.isInsideUnitSquare()) return this
        val updated = when (index) {
            0 -> editableCorners.copy(topLeft = point)
            1 -> editableCorners.copy(topRight = point)
            2 -> editableCorners.copy(bottomRight = point)
            else -> editableCorners.copy(bottomLeft = point)
        }
        return copy(editableCorners = updated, source = CornerProposalSource.MANUAL)
    }

    /** A failed or invalid re-detection never destroys the previous usable draft. */
    fun applyDetection(corners: CornerPoints?, confidence: DetectionConfidence): CornerEditDraft? {
        val detected = corners?.takeIf(CornerPoints::isValid) ?: return null
        return copy(
            editableCorners = detected,
            source = CornerProposalSource.AUTO_DETECTION,
            detectionConfidence = confidence
        )
    }

    fun rotateClockwise(): CornerEditDraft? {
        val rotatedEditable = editableCorners.rotateClockwise() ?: return null
        return copy(
            savedCorners = savedCorners?.rotateClockwise(),
            editableCorners = rotatedEditable
        )
    }

    /** The current TL/TR/BR/BL draft is validated before canonicalization. */
    fun cornersToCommit(): CornerPoints? {
        if (!editableCorners.isValid()) return null
        return CornerPoints.fromUnordered(editableCorners.points)
    }

    companion object {
        fun fromSaved(corners: CornerPoints): CornerEditDraft = CornerEditDraft(
            savedCorners = corners,
            editableCorners = corners,
            source = CornerProposalSource.SAVED
        )

        fun fromDetection(corners: CornerPoints?, confidence: DetectionConfidence): CornerEditDraft? {
            val detected = corners?.takeIf(CornerPoints::isValid) ?: return null
            return CornerEditDraft(
                savedCorners = null,
                editableCorners = detected,
                source = CornerProposalSource.AUTO_DETECTION,
                detectionConfidence = confidence
            )
        }

        fun manualFallback(): CornerEditDraft = CornerEditDraft(
            savedCorners = null,
            editableCorners = CornerPoints.fullImage(),
            source = CornerProposalSource.MANUAL
        )
    }
}
