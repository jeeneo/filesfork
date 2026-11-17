package texteditor.server.http

// SPDX-License-Identifier: GPL-3.0-or-later

import android.content.Context
import android.graphics.Bitmap
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.response.header
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondOutputStream
import io.ktor.server.response.respondText
import io.ktor.server.routing.IgnoreTrailingSlash
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import texteditor.R
import texteditor.server.RouteContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.BufferedWriter
import java.io.ByteArrayOutputStream
import kotlin.time.Duration.Companion.seconds

private fun BufferedWriter.writeEvent(eventName: String, data: String) {
    write("event: $eventName\n")
    write("data: $data\n\n")
    flush()
}

private fun BufferedWriter.writeHeartbeat() {
    write(": heartbeat\n\n")
    flush()
}

fun Route.eventsRoute(ctx: RouteContext) {
    get("/events") {
        call.response.header(HttpHeaders.ContentType, ContentType.Text.EventStream.toString())
        call.response.header(HttpHeaders.Connection, "keep-alive")
        call.response.header("X-Accel-Buffering", "no")

        call.respondOutputStream {
            val writer = bufferedWriter()
            val writerMutex = Mutex()
            try {
                writer.writeEvent("init", "connected")
                coroutineScope {
                    launch {
                        ctx.registry.refresh.collect { signal ->
                            writerMutex.withLock {
                                val data =
                                    jsonObject(
                                        "path" to jsonString(signal.path),
                                        "sourceId" to jsonString(signal.sourceId.name),
                                        "clientId" to (signal.clientId?.let { jsonString(it) } ?: "null"),
                                    )
                                writer.writeEvent(
                                    "refresh-${signal.type.name.lowercase()}",
                                    data,
                                )
                            }
                        }
                    }

                    launch {
                        while (true) {
                            delay(15.seconds)
                            writerMutex.withLock { writer.writeHeartbeat() }
                        }
                    }
                }
            } catch (_: CancellationException) {
                // drop connection
            }
        }
    }
}

private fun Route.landingRoutes(
    ctx: RouteContext,
    template: String,
) {
    get("/") {
        val html = renderVars(
            template,
            ctx.uiStrings + mapOf(
                "appname" to ctx.appName,
                "appurl" to "",
            ),
        )
        call.respondText(html, ContentType.Text.Html)
    }
}

fun Application.serverModule(ctx: RouteContext) {
    install(IgnoreTrailingSlash)

    routing {
        get("/favicon.ico") {
            val png = faviconPng(ctx.appContext, 32) ?: return@get call.respondText(
                "",
                status = HttpStatusCode.NoContent,
            )
            call.response.header(HttpHeaders.CacheControl, "public, max-age=31536000, immutable")
            call.respondBytes(png, ContentType.Image.PNG)
        }
        landingRoutes(ctx, ctx.templateHtml)
        get("/stylesheet.css") {
            call.response.header(HttpHeaders.CacheControl, "no-cache")
            call.respondText(ctx.stylesheetCss, ContentType.Text.CSS)
        }
        get("/script.js") {
            val js = renderVars(
                ctx.javascriptJS,
                ctx.uiStrings + mapOf(
                    "appname" to ctx.appName,
                    "appurl" to "",
                ),
            )
            call.response.header(HttpHeaders.CacheControl, "no-cache")
            call.respondText(js, ContentType.Text.JavaScript)
        }
        get("/editor/{asset}") {
            val asset = call.parameters["asset"]
            val content = asset?.let(ctx.editorWebAssets::get)
            val contentType = when {
                asset == null -> null
                asset.endsWith(".js") -> ContentType.Text.JavaScript
                asset.endsWith(".css") -> ContentType.Text.CSS
                else -> null
            }
            if (content == null || contentType == null) {
                call.respondText("", status = HttpStatusCode.NotFound)
                return@get
            }
            call.response.header(HttpHeaders.CacheControl, "no-cache")
            call.respondText(content, contentType)
        }
        pathApiRoutes(ctx)
        eventsRoute(ctx)
    }
}

fun renderVars(
    template: String,
    vars: Map<String, String>,
): String = vars.entries.fold(template) { html, (key, value) ->
    html.replace("{{$key}}", value)
}

fun faviconPng(
    context: Context,
    size: Int,
): ByteArray? = runCatching {
    ContextCompat.getDrawable(context, R.drawable.ic_localhost)?.let { drawable ->
        val bitmap = drawable.toBitmap(size, size)
        ByteArrayOutputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.toByteArray()
        }
    }
}.getOrNull()
