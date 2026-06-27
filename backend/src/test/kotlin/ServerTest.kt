package me.orange

import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import io.ktor.server.testing.ApplicationTestBuilder
import kotlin.test.*

class ServerTest {

    @Test
    fun `test root endpoint`() = testApplication {
        // loads default configuration
        configure()
        // verify server root returns 200
        assertEquals(HttpStatusCode.OK, client.get("/").status)
    }

}

private fun ApplicationTestBuilder.configure() {
    application {
        configureSerialization()
        configureWebsockets()
        configureRouting()
    }
}
