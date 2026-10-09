package com.example.gabayai.ui

import com.example.gabayai.domain.RecognitionResult
import com.example.gabayai.imaging.PreparedPhoto
import java.util.UUID

enum class ScanStage { EMPTY, PREPARING, PREVIEW, OCR, MATCHING, MODEL_LOADING, SEMANTIC, CANCELLING, READY, ERROR }
enum class ScanProblem { UNREADABLE, UNSUPPORTED, TOO_LARGE, TOO_SMALL, STORAGE, NO_TEXT, OCR_FAILED, CATALOGUE_FAILED }

data class ScanState(
    val photo: PreparedPhoto? = null,
    val stage: ScanStage = ScanStage.EMPTY,
    val result: RecognitionResult? = null,
    val problem: ScanProblem? = null,
    /** Opaque session identity; no image path or OCR text is saved by the guide. */
    val sessionId: String = UUID.randomUUID().toString(),
) {
    val error: Boolean get() = problem != null
    val busy: Boolean get() = stage in setOf(ScanStage.PREPARING, ScanStage.OCR, ScanStage.MATCHING,
        ScanStage.MODEL_LOADING, ScanStage.SEMANTIC, ScanStage.CANCELLING)
}

fun ScanProblem.message(fil: Boolean): String = when (this) {
    ScanProblem.UNREADABLE -> word(fil, "Hindi mabuksan ang picture. Pumili ng ibang picture o kumuha ng bago.", "This photo could not be opened. Choose another photo or take a new one.")
    ScanProblem.UNSUPPORTED -> word(fil, "Hindi suportado ang uri ng file na ito. Pumili ng JPG, PNG, o WebP na picture.", "This file type is not supported. Choose a JPG, PNG, or WebP photo.")
    ScanProblem.TOO_LARGE -> word(fil, "Masyadong malaki ang picture. Kumuha ng bago gamit ang karaniwang camera setting.", "This photo is too large. Take a new photo using the normal camera setting.")
    ScanProblem.TOO_SMALL -> word(fil, "Masyadong maliit ang picture para mabasa. Ilapit ang camera sa form at kumuha ng bago.", "This photo is too small to read. Move the camera closer to the form and take a new photo.")
    ScanProblem.STORAGE -> word(fil, "Kulang ang bakanteng storage ng phone. Magbakante ng kaunting storage, saka subukan muli.", "The phone needs more free storage. Free some storage, then try again.")
    ScanProblem.NO_TEXT -> word(fil, "Walang nabasang salita sa picture. Ilapit ang camera sa mga nakasulat at iwasan ang anino o silaw.", "No words were found in this photo. Move closer to the printed words and avoid shadows or glare.")
    ScanProblem.OCR_FAILED -> word(fil, "Hindi natapos ang pagbasa ng picture. Nandito pa ang picture mo; subukan muli.", "Reading the photo did not finish. Your photo is still here; try again.")
    ScanProblem.CATALOGUE_FAILED -> word(fil, "Hindi mabuksan ang mga paliwanag sa phone. Subukan muli. Nandito pa ang picture mo.", "The saved explanations could not be opened. Try again. Your photo is still here.")
}
