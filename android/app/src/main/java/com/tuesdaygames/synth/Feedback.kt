//
//  Feedback.kt
//
//  "Send feedback": an email to Tuesday Games with the details that help
//  (app version, Android version, device, whether the full version is owned)
//  filled in under room to write. The person sees everything and chooses to
//  send it; the app itself sends nothing. Ported from iOS's Feedback.swift.
//

package com.tuesdaygames.synth

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build

object Feedback {
    const val ADDRESS = "info@tuesdaygames.com"
    const val SUBJECT = "808Synth feedback"

    /** The email body: room to write at the top, details for us underneath. */
    fun body(appVersion: String, system: String, device: String, fullVersion: Boolean): String = """


        ———
        Write your message above. These details help us help you:
        808Synth $appVersion
        $system · $device
        Full version: ${if (fullVersion) "yes" else "no"}
    """.trimIndent().let { "\n\n" + it.trimStart('\n') }

    /** A mailto: link with everything filled in; most mail apps read it all. */
    fun mailtoUri(body: String): Uri = Uri.parse(
        "mailto:$ADDRESS?subject=${Uri.encode(SUBJECT)}&body=${Uri.encode(body)}",
    )

    fun deviceBody(context: Context, fullVersion: Boolean): String {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        @Suppress("DEPRECATION")
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()
        return body(
            appVersion = "${info.versionName} ($code)",
            system = "Android ${Build.VERSION.RELEASE}",
            device = "${Build.MANUFACTURER} ${Build.MODEL}",
            fullVersion = fullVersion,
        )
    }

    /** Opens the phone's mail app with the email ready. False if there isn't one. */
    fun send(context: Context, fullVersion: Boolean): Boolean {
        val body = deviceBody(context, fullVersion)
        val intent = Intent(Intent.ACTION_SENDTO, mailtoUri(body)).apply {
            // Some mail apps read these instead of the link's own fields.
            putExtra(Intent.EXTRA_EMAIL, arrayOf(ADDRESS))
            putExtra(Intent.EXTRA_SUBJECT, SUBJECT)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (error: Exception) {
            false
        }
    }
}
