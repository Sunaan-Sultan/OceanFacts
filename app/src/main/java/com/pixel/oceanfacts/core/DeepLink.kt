package com.pixel.oceanfacts.core

import android.content.Intent
import android.net.Uri

/**
 * The `oceanfacts://` links that point back into the app — `fact/<id>` for a single fact, built
 * by the reminder and by the caption on a shared fact image, and `quiz` for today's round, built
 * by the reminder when a streak is at stake. Both are parsed by MainActivity.
 *
 * One place so they never drift: a link that does not match the manifest's intent filter fails
 * silently, with nothing to see at either end.
 */
object DeepLink {

    const val SCHEME = "oceanfacts"
    const val HOST_FACT = "fact"
    const val HOST_QUIZ = "quiz"

    fun toFact(factId: String): String = "$SCHEME://$HOST_FACT/$factId"

    fun toDailyQuiz(): String = "$SCHEME://$HOST_QUIZ"

    /** Whether [intent] asks for today's quiz. */
    fun isQuizLink(intent: Intent?): Boolean = isQuizLink(intent?.data)

    fun isQuizLink(uri: Uri?): Boolean = isQuizLink(uri?.scheme, uri?.host)

    /** The fact named by [intent], if it is one of our links and the fact still exists. */
    fun factIdFrom(intent: Intent?): String? = factIdFrom(intent?.data)

    fun factIdFrom(uri: Uri?): String? = factIdFrom(uri?.scheme, uri?.host, uri?.lastPathSegment)

    // The matching itself takes plain strings rather than a Uri so it can be tested on the JVM:
    // android.net.Uri is a stub outside an instrumented run, and this is the logic worth testing.
    internal fun isQuizLink(scheme: String?, host: String?): Boolean =
        scheme == SCHEME && host == HOST_QUIZ

    internal fun factIdFrom(scheme: String?, host: String?, lastSegment: String?): String? {
        if (scheme != SCHEME || host != HOST_FACT) return null
        val id = lastSegment?.takeIf { it.isNotBlank() } ?: return null
        return id.takeIf { factById(it) != null }
    }
}
