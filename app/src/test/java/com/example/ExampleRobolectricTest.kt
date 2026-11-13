package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.design.APP_NAME
import com.example.lib.dev.JsonJwtEngine
import com.example.lib.dev.JsonProcessResult
import com.example.lib.text.TextEncodersEngine
import com.example.services.capabilities.CapabilitiesService
import com.example.services.storage.DotkitRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app_name and probe capabilities from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Omni Deck", APP_NAME)
        assertEquals(APP_NAME, appName)

        val snapshot = CapabilitiesService.probe(
            context = context,
            companionReachable = false,
            geminiConfigured = false,
            offlineOnlyAi = true
        )
        assertTrue(snapshot.cpuCores >= 1)
        assertTrue(snapshot.probes.isNotEmpty())
    }

    @Test
    fun `repository starts cleanly with zero pre-seeded mock data`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = DotkitRepository(context)
        assertTrue(repo.counters.first().isEmpty())
        assertTrue(repo.notes.first().isEmpty())
        assertTrue(repo.expenses.first().isEmpty())
        assertTrue(repo.savedFiles.first().isEmpty())
        assertTrue(repo.healthLogs.first().isEmpty())
    }

    @Test
    fun `aes gcm encryption and stylizer round-trip cleanly`() {
        val env = TextEncodersEngine.encryptAesGcm("Omni Deck Secret Payload", "master-key-123")
        val dec = TextEncodersEngine.decryptAesGcm(env.cipherBase64, env.ivBase64, env.saltBase64, "master-key-123")
        assertTrue(dec.isSuccess)
        assertEquals("Omni Deck Secret Payload", dec.getOrThrow())

        val styled = TextEncodersEngine.stylizeText("HELLO", TextEncodersEngine.StylishMode.FULLWIDTH)
        assertEquals("ＨＥＬＬＯ", styled)
    }

    @Test
    fun `json formatter and jwt decoder parse real payloads`() {
        val formatted = JsonJwtEngine.formatJson("""{"b":1,"a":2}""", indentSpaces = 2, sortKeys = true)
        assertTrue(formatted is JsonProcessResult.Valid)
        val valid = formatted as JsonProcessResult.Valid
        assertEquals(2, valid.keyCount)
        assertTrue(valid.formatted.indexOf("\"a\"") < valid.formatted.indexOf("\"b\""))

        val invalid = JsonJwtEngine.formatJson("{bad json")
        assertTrue(invalid is JsonProcessResult.Invalid)

        val sampleJwt =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJvbW5pZGVjay11c2VyIiwibmFtZSI6IlNhbXBsZSBUb2tlbiIsImlhdCI6MTcwMDAwMDAwMCwiZXhwIjoxOTk5OTk5OTk5fQ.sig"
        val jwt = JsonJwtEngine.decodeJwt(sampleJwt).getOrThrow()
        assertEquals("HS256", jwt.algorithm)
        assertEquals("omnideck-user", jwt.subject)
        assertFalse(jwt.algNoneWarning)
    }
}
