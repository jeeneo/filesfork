package texteditor.server.http

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveMultipart
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.util.cio.writeChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.copyAndClose
import kotlinx.coroutines.CancellationException
import texteditor.server.RouteContext
import texteditor.server.data.RefreshSource
import texteditor.server.data.RefreshType
import android.util.Base64

fun Route.pathApiRoutes(ctx: RouteContext) {
    get("/api/tree") {
        call.respondPathApi {
            val path = call.queryValue("path").orEmpty()
            jsonArray(ctx.pathStore.list(path).map(PathEntry::toJson))
        }
    }

    get("/api/file") {
        call.respondPathApi {
            val path = call.requiredQuery("path")
            val content = ctx.pathStore.read(path)
            val markdown = ctx.pathStore.supportsMarkdownPreview(path)
            val encoded = Base64.encodeToString(content.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            jsonObject(
                "path" to jsonString(path),
                "content" to jsonString(encoded),
                "editable" to "true",
                "markdown" to markdown.toString(),
            )
        }
    }

    put("/api/file") {
        call.respondPathApi {
            val path = call.requiredQuery("path")
            val content = call.receiveText()
            val bytes = ctx.pathStore.write(path, content)
            val clientId = call.request.headers["X-Editor-Id"]
            ctx.registry.emitRefresh(
                type = RefreshType.FILE,
                path = path,
                sourceId = RefreshSource.WEB_UI,
                clientId = clientId,
            )
            jsonObject(
                "ok" to "true",
                "path" to jsonString(path),
                "bytes" to bytes.toString(),
            )
        }
    }

    post("/api/newfile") {
        call.respondPathApi {
            ctx.pathStore.createFile(call.queryValue("path").orEmpty(), call.requiredQuery("name"))
            jsonObject("ok" to "true")
        }
    }

    post("/api/newfolder") {
        call.respondPathApi {
            ctx.pathStore.createDirectory(
                call.queryValue("path").orEmpty(),
                call.requiredQuery("name"),
            )
            jsonObject("ok" to "true")
        }
    }

    post("/api/upload") {
        call.respondPathApi {
            val directory = call.queryValue("path").orEmpty()
            val uploaded = mutableListOf<PathUpload>()
            try {
                call.receiveMultipart(formFieldLimit = Long.MAX_VALUE).forEachPart { part ->
                    try {
                        if (part is PartData.FileItem) {
                            val name =
                                part.originalFileName?.trim()?.takeIf { it.isNotEmpty() }
                                    ?: "upload_${System.currentTimeMillis()}"
                            uploaded += ctx.pathStore.upload(directory, name, part.provider())
                        }
                    } finally {
                        part.release()
                    }
                }
            } catch (e: PathStoreException) {
                ctx.pathStore.rollbackUploads(uploaded)
                throw e
            } catch (e: CancellationException) {
                ctx.pathStore.rollbackUploads(uploaded)
                throw e
            } catch (e: Exception) {
                ctx.pathStore.rollbackUploads(uploaded)
                throw PathStoreException(
                    PathFailure.INVALID,
                    "Invalid multipart upload: ${e.message}",
                    e,
                )
            }
            if (uploaded.isEmpty()) {
                throw PathStoreException(PathFailure.INVALID, "No files were uploaded")
            }
            jsonObject(
                "ok" to "true",
                "count" to uploaded.size.toString(),
                "files" to
                        jsonArray(
                            uploaded.map { upload ->
                                jsonObject(
                                    "path" to jsonString(upload.path),
                                    "bytes" to upload.bytes.toString(),
                                )
                            },
                        ),
            )
        }
    }

    post("/api/delete") {
        call.respondPathApi {
            ctx.pathStore.delete(call.requiredQuery("path"))
            jsonObject("ok" to "true")
        }
    }

    post("/api/rename") {
        call.respondPathApi {
            val path =
                ctx.pathStore.rename(call.requiredQuery("path"), call.requiredQuery("new_name"))
            jsonObject("ok" to "true", "path" to jsonString(path))
        }
    }
}

private fun ApplicationCall.queryValue(name: String): String? = request.queryParameters[name]

private fun ApplicationCall.requiredQuery(name: String): String =
    queryValue(name) ?: throw PathStoreException(PathFailure.INVALID, "Missing $name")

private suspend fun PathStore.rollbackUploads(uploaded: List<PathUpload>) {
    uploaded.asReversed().forEach { upload ->
        try {
            delete(upload.path)
        } catch (_: Throwable) {
        }
    }
}

private fun PathEntry.toJson(): String =
    if (type == "dir") {
        jsonObject(
            "name" to jsonString(name),
            "type" to jsonString(type),
        )
    } else {
        jsonObject(
            "name" to jsonString(name),
            "type" to jsonString(type),
            "editable" to editable.toString(),
            "markdown" to markdown.toString(),
        )
    }

private val PathStoreException.status: HttpStatusCode
    get() =
        when (failure) {
            PathFailure.INVALID, PathFailure.CONFLICT -> HttpStatusCode.BadRequest
            PathFailure.ACCESS -> HttpStatusCode.Forbidden
            PathFailure.NOT_FOUND -> HttpStatusCode.NotFound
            PathFailure.IO -> HttpStatusCode.InternalServerError
        }

private suspend fun ApplicationCall.respondPathApi(block: suspend () -> String) {
    val body =
        try {
            block()
        } catch (e: PathStoreException) {
            respondText(
                e.message ?: "Request failed",
                status = e.status,
                contentType = ContentType.Text.Plain,
            )
            return
        } catch (e: CancellationException) {
            throw e
        } catch (_: SecurityException) {
            respondText(
                "Access denied",
                status = HttpStatusCode.Forbidden,
                contentType = ContentType.Text.Plain,
            )
            return
        } catch (_: Exception) {
            respondText(
                "Internal error",
                status = HttpStatusCode.InternalServerError,
                contentType = ContentType.Text.Plain,
            )
            return
        }
    respondText(body, ContentType.Application.Json)
}
