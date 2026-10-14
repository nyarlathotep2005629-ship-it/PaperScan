# PaperScan Update — 2026-10-14

## Camera

- `PreviewView.ScaleType.FILL_CENTER` can make the preview appear cropped even when `zoomRatio = 1.0`; display crop and digital zoom are separate concerns.
- Preview and capture now follow a shared aspect and `ViewPort` contract.
- The implementation uses `FIT_CENTER`, a shared 4:3 `ResolutionSelector`, `UseCaseGroup` binding, explicit 1.0x zoom, and rotation-aware rebinding.

## Corner Editing

- `CornerEditDraft` is the unsaved candidate/editor state.
- `PageItem.corners` remains the persisted Source of Truth.
- Automatic Detection proposes draft corners and does not directly overwrite saved corners.
- Manual Save is the boundary that persists corners; failed re-detection preserves the existing valid state.

## Automatic Detection

- Canonical corners are normalized 0..1 coordinates after EXIF correction and user rotation, ordered TL / TR / BR / BL.
- Display-to-image conversion must account for letterbox and the displayed image bounds.
- The OpenCV pipeline works on a bounded bitmap (`maxLongEdgePx = 1600` in the current path), followed by grayscale, blur, Canny, morphology, quadrilateral extraction, and scoring.
- The decode-after-size check remains `PARTIAL`: sampling and test expectations exist, but an explicit post-decode dimension guard still requires follow-up confirmation.

## PDF Export

- Editing sources and the optimized PDF export derivative are kept separate.
- Current export contract: approximately 200 DPI, aspect-preserving FIT, white background, JPEG quality 85, `/DCTDecode`, and sequential page output.
- `JpegPdfWriter` requires focused review of xref offsets, stream lengths, page tree, object references, failure/cancellation handling, and viewer compatibility.

## Tests

- `assembleDebug`: PASS.
- `testDebugUnitTest`: PASS.
- `lintDebug`: PASS.
- Instrumented tests: NOT RUN in the current verification because the ADB daemon could not start. The previously reported 16/16 result is not reasserted without current local evidence.

## Known Limitations

- Real-device camera framing, hardware flash, and preview/capture framing comparison remain NOT RUN.
- Real lecture-handout, stacked-paper, curved-paper, clutter, and extreme-low-contrast validation remain NOT RUN.
- The real 26-page before/after PDF size benchmark, real-device PDF export, and 200 DPI/JPEG 85 readability check remain NOT RUN.
- The `JpegPdfWriter` architecture/compatibility review remains NOT RUN.

## Architecture Notes

- The canonical coordinate system is the post-EXIF/post-user-rotation normalized image space.
- UI draft state and persisted page state must not become independent Sources of Truth.
- Export optimization must not mutate the original captured image, saved corners, or editing source.
- This document records design and verification boundaries only; it does not claim source-code or real-device completion beyond the checks listed above.

