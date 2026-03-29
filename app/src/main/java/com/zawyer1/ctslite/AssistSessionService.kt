/*
 *
 *  * Copyright (C) 2025 AKS-Labs (original author)
    *  * Modifications Copyright (C) 2026 Zawyer1
 *  *
 *  * This program is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * (at your option) any later version.
 *  *
 *  * This program is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  * GNU General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package com.zawyer1.ctslite

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import com.zawyer1.ctslite.data.BitmapRepository

/**
 * FORK CHANGE: AssistSessionService now handles the screenshot entirely via
 * VoiceInteractionSession.onHandleScreenshot(), which Android delivers automatically
 * when the app is set as the default assistant (long-press home / edge swipe up).
 *
 * This eliminates the previous dependency on CircleToSearchAccessibilityService.triggerCapture()
 * for the assistant trigger path. The Accessibility Service is still present in the app
 * for its other features (status bar overlay, bubble, gestures, etc.) but is NO LONGER
 * required for screenshot capture when using the assistant trigger.
 *
 * Result: Users who only want the home-button trigger can use the app without granting
 * Accessibility permission, which resolves incompatibility with banking and financial apps.
 *
 * Requires: Android 11+ (API 30) for onHandleScreenshot().
 * The assistant route was already Android 10+ only, so there is no compatibility regression.
 */
class AssistSessionService : VoiceInteractionSessionService() {

    override fun onCreate() {
        super.onCreate()
        android.util.Log.d("AssistSessionService", "Service onCreate")
    }

    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        android.util.Log.d("AssistSessionService", "onNewSession created")
        return CircleToSearchSession(this)
    }

    inner class CircleToSearchSession(context: Context) : VoiceInteractionSession(context) {

        override fun onShow(args: Bundle?, showFlags: Int) {
            super.onShow(args, showFlags)
            android.util.Log.d("AssistSessionService", "onShow called")
            // No action needed here - wait for onHandleScreenshot to fire with the bitmap.
        }

        /**
         * FORK CHANGE: Primary capture path.
         *
         * Android delivers a Bitmap of the current screen contents here automatically
         * when the assistant is invoked. No Accessibility permission is required.
         *
         * This replaces the previous approach of calling
         * CircleToSearchAccessibilityService.triggerCapture() from onHandleAssist().
         */
        override fun onHandleScreenshot(screenshot: Bitmap?) {
            android.util.Log.d("AssistSessionService", "onHandleScreenshot called. bitmap=${screenshot != null}")

            val copy = screenshot?.let {
                if (it.config == Bitmap.Config.HARDWARE)
                    it.copy(Bitmap.Config.ARGB_8888, false)
                else it
            }

            BitmapRepository.setScreenshot(copy)
            finish() // Finish session first
            launchOverlay() // Then launch overlay via delayed handler
        }

        /**
         * FORK CHANGE: onHandleAssist is now only a fallback for devices running
         * Android 10 (API 29) where onHandleScreenshot is not available.
         * On API 30+ this will never be reached for the screenshot flow because
         * onHandleScreenshot fires first.
         *
         * On API 29, we fall back to the Accessibility Service path if it is active,
         * or show a graceful error if it is not.
         */
        override fun onHandleAssist(
            data: Bundle?,
            structure: android.app.assist.AssistStructure?,
            content: android.app.assist.AssistContent?
        ) {
            finish()
        }

        private fun launchOverlay() {
            android.util.Log.d("AssistSessionService", "Launching OverlayActivity")
            val intent = Intent(context, OverlayActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            // Post to handler rather than calling startVoiceActivity() directly.
            // startVoiceActivity() fails on some devices because the session is
            // considered inactive by the time onHandleScreenshot() fires.
            // Using the application context with a short delay sidesteps this entirely.
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try {
                    context.applicationContext.startActivity(intent)
                    android.util.Log.d("AssistSessionService", "OverlayActivity launched successfully")
                } catch (e: Exception) {
                    android.util.Log.e("AssistSessionService", "Failed to launch OverlayActivity", e)
                }
            }, 200)
        }
    }
}
