package com.lingualoop.android.data.api

import com.google.common.truth.Truth.assertThat
import com.lingualoop.android.data.api.dto.QueueResponse
import com.lingualoop.android.data.api.dto.RegisterRequest
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class LinguaLoopApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: LinguaLoopApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor { "test-jwt-token" })
            .build()
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(LinguaLoopApi::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `register parses the auth response`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {"token":"jwt","learner":{"id":7,"email":"ana@example.com",
                     "displayName":"Ana","timezone":"Europe/Madrid"}}
                    """.trimIndent()
                )
        )

        val auth = api.register(RegisterRequest("ana@example.com", "supersecret1", "Ana"))

        assertThat(auth.token).isEqualTo("jwt")
        assertThat(auth.learner.id).isEqualTo(7)
        assertThat(auth.learner.timezone).isEqualTo("Europe/Madrid")
    }

    @Test
    fun `queue parses the new flag from the json field name`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {"items":[
                      {"exerciseId":21,"type":"TRANSLATE","prompt":"Good morning",
                       "answer":"Buenos días","choices":[],"lessonId":1,
                       "lessonTitle":"Saludos básicos","unitId":1,
                       "unitTitle":"Unidad 1 · Saludos y presentaciones",
                       "new":true},
                      {"exerciseId":22,"type":"LISTEN","prompt":"Listen",
                       "answer":"Hola","choices":[],"caption":"Hola",
                       "audioAsset":{"id":5,"durationMs":2100,"bytes":35867},
                       "lessonId":3,"lessonTitle":"Comprensión auditiva",
                       "unitId":1,"unitTitle":"Unidad 1 · Saludos y presentaciones",
                       "new":false,
                       "review":{"easeFactor":2.5,"intervalDays":1.0,"repetitions":3,
                                 "dueAt":"2026-10-01T00:00:00Z","lastGrade":4,"lapses":0}}
                    ],"dueCount":1}
                    """.trimIndent()
                )
        )

        val queue: QueueResponse = api.queue()

        assertThat(queue.dueCount).isEqualTo(1)
        assertThat(queue.items[0].isNew).isTrue()
        assertThat(queue.items[1].isNew).isFalse()
        assertThat(queue.items[1].audioAsset?.id).isEqualTo(5)
        assertThat(queue.items[1].review?.repetitions).isEqualTo(3)
    }

    @Test
    fun `authenticated calls carry the bearer token`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"items":[],"dueCount":0}""")
        )

        api.queue()

        val request = server.takeRequest()
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer test-jwt-token")
        assertThat(request.path).isEqualTo("/api/learners/me/queue")
    }
}
