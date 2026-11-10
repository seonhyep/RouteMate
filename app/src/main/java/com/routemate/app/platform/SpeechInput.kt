package com.routemate.app.platform

import android.content.Intent
import android.speech.RecognizerIntent

// Keep Android recognition behind an adapter; a Pleos SpeechToText adapter can replace it.
interface SpeechIntentProvider { fun createIntent(): Intent }
class AndroidSpeechIntentProvider : SpeechIntentProvider {
    override fun createIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
        putExtra(RecognizerIntent.EXTRA_PROMPT, "정차 중에 할 일을 말씀해 주세요")
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
    }
}
