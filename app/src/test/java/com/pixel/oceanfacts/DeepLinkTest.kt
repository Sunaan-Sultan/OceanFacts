package com.pixel.oceanfacts

import com.pixel.oceanfacts.core.ALL_FACTS
import com.pixel.oceanfacts.core.DeepLink
import com.pixel.oceanfacts.core.OceanData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

class DeepLinkTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun loadCatalog() {
            val assets = File("src/main/assets")
            OceanData.load { path -> File(assets, path).readText() }
        }
    }

    @Test
    fun aFactLinkRoundTrips() {
        val id = ALL_FACTS.first().id
        val url = DeepLink.toFact(id)
        assertEquals("oceanfacts://fact/$id", url)
        assertEquals(id, DeepLink.factIdFrom(DeepLink.SCHEME, DeepLink.HOST_FACT, id))
    }

    @Test
    fun aLinkToANonExistentFactIsRefused() {
        assertNull(DeepLink.factIdFrom(DeepLink.SCHEME, DeepLink.HOST_FACT, "not-a-fact"))
        assertNull(DeepLink.factIdFrom(DeepLink.SCHEME, DeepLink.HOST_FACT, ""))
        assertNull(DeepLink.factIdFrom(DeepLink.SCHEME, DeepLink.HOST_FACT, null))
    }

    @Test
    fun anotherSchemeIsIgnored() {
        val id = ALL_FACTS.first().id
        assertNull(DeepLink.factIdFrom("https", DeepLink.HOST_FACT, id))
        assertNull(DeepLink.factIdFrom(DeepLink.SCHEME, "quiz", id))
    }

    @Test
    fun theQuizLinkIsRecognised() {
        assertEquals("oceanfacts://quiz", DeepLink.toDailyQuiz())
        assertTrue(DeepLink.isQuizLink(DeepLink.SCHEME, DeepLink.HOST_QUIZ))
        assertFalse(DeepLink.isQuizLink(DeepLink.SCHEME, DeepLink.HOST_FACT))
        assertFalse(DeepLink.isQuizLink("https", DeepLink.HOST_QUIZ))
    }
}
