package com.paperscan.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CornerEditDraftTest {
    private val saved = CornerPoints(
        NormalizedPoint(0.10f, 0.10f), NormalizedPoint(0.90f, 0.10f),
        NormalizedPoint(0.90f, 0.90f), NormalizedPoint(0.10f, 0.90f)
    )

    @Test
    fun moveCorner_thenCommit_usesTheSameEditableState() {
        val draft = CornerEditDraft.fromSaved(saved)
            .moveCorner(0, NormalizedPoint(0.22f, 0.18f))

        assertTrue(draft.hasUnsavedChanges)
        assertEquals(NormalizedPoint(0.22f, 0.18f), draft.cornersToCommit()?.topLeft)
    }

    @Test
    fun invalidSelfIntersection_isRejectedAndSavedCornersStayAvailable() {
        val invalid = CornerEditDraft.fromSaved(saved)
            .moveCorner(0, NormalizedPoint(0.95f, 0.95f))

        assertNull(invalid.cornersToCommit())
        assertEquals(saved, invalid.savedCorners)
    }

    @Test
    fun redetectFailure_preservesPreviousValidDraft() {
        val current = CornerEditDraft.fromSaved(saved)
            .moveCorner(0, NormalizedPoint(0.20f, 0.20f))

        val replacement = current.applyDetection(null, DetectionConfidence.LOW)

        assertNull(replacement)
        assertEquals(NormalizedPoint(0.20f, 0.20f), current.editableCorners.topLeft)
    }

    @Test
    fun explicitRedetect_replacesCandidateButNotPersistedCorners() {
        val detected = CornerPoints(
            NormalizedPoint(0.15f, 0.12f), NormalizedPoint(0.85f, 0.15f),
            NormalizedPoint(0.82f, 0.88f), NormalizedPoint(0.18f, 0.84f)
        )
        val original = CornerEditDraft.fromSaved(saved)

        val replacement = requireNotNull(
            original.applyDetection(detected, DetectionConfidence.HIGH)
        )

        assertEquals(detected, replacement.editableCorners)
        assertEquals(saved, replacement.savedCorners)
        assertTrue(replacement.hasUnsavedChanges)
        assertEquals(CornerProposalSource.AUTO_DETECTION, replacement.source)
    }

    @Test
    fun reopeningFromSavedCorners_doesNotApplyAutomaticCandidate() {
        val reopened = CornerEditDraft.fromSaved(saved)

        assertEquals(saved, reopened.editableCorners)
        assertFalse(reopened.hasUnsavedChanges)
        assertEquals(CornerProposalSource.SAVED, reopened.source)
    }
}
