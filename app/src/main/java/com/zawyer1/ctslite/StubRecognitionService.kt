package com.zawyer1.ctslite

import android.speech.RecognitionService

/**
 * Stub RecognitionService required by Android's VoiceInteractionService
 * validator. This app does not perform voice recognition — this class
 * exists solely to satisfy the system requirement. It intentionally
 * does nothing.
 */
class StubRecognitionService : RecognitionService() {
    override fun onStartListening(intent: android.content.Intent?, listener: Callback?) {}
    override fun onStopListening(listener: Callback?) {}
    override fun onCancel(listener: Callback?) {}
}