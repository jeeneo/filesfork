package texteditor.server.http

import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.file.Files

class PathStoreTest {
    @Test
    fun `list sorts directories first and allows opening every file`() =
        runBlocking {
            withStore { root, store ->
                root.resolve("zeta.txt").writeText("z")
                root.resolve("alpha.md").writeText("a")
                root.resolve("binary.bin").writeText("b")
                root.resolve("Folder").mkdirs()
                root.resolve(".hidden").writeText("h")

                val entries = store.list("")

                assertEquals(listOf("Folder", "alpha.md", "binary.bin", "zeta.txt"), entries.map { it.name })
                assertEquals(listOf("dir", "file", "file", "file"), entries.map { it.type })
                assertTrue(entries[1].editable)
                assertTrue(entries[1].markdown)
                assertTrue(entries[2].editable)
                assertFalse(entries[2].markdown)
                assertTrue(entries[3].editable)
                assertFalse(entries[3].markdown)
            }
        }

    @Test
    fun `identifies markdown without gating other extensions`() =
        runBlocking {
            withStore { root, store ->
                listOf("notes.md", "script.kt", "config.json", "photo.jpg", "archive.zip")
                    .forEach { root.resolve(it).writeText("x") }

                val entries = store.list("").associateBy { it.name }

                assertTrue(entries.getValue("notes.md").markdown)
                assertTrue(entries.getValue("script.kt").editable)
                assertFalse(entries.getValue("script.kt").markdown)
                assertTrue(entries.getValue("config.json").editable)
                assertFalse(entries.getValue("config.json").markdown)
                assertTrue(entries.getValue("photo.jpg").editable)
                assertFalse(entries.getValue("photo.jpg").markdown)
                assertTrue(entries.getValue("archive.zip").editable)
                assertFalse(entries.getValue("archive.zip").markdown)
            }
        }

    @Test
    fun `write read and replace preserve utf8 content`() =
        runBlocking {
            withStore { _, store ->
                val bytes = store.write("notes/é.md", "héllo\n世界")

                assertEquals(
                    bytes,
                    store
                        .read("notes/é.md")
                        .toByteArray(Charsets.UTF_8)
                        .size
                        .toLong(),
                )
                assertEquals("héllo\n世界", store.read("notes/é.md"))
                store.write("notes/é.md", "updated")
                assertEquals("updated", store.read("notes/é.md"))
            }
        }

    @Test
    fun `create rename and delete operate on relative paths`() =
        runBlocking {
            withStore { root, store ->
                store.createDirectory("", "folder")
                store.createFile("folder", "old.txt")
                assertTrue(root.resolve("folder/old.txt").isFile)

                assertEquals("folder/new.md", store.rename("folder/old.txt", "new.md"))
                assertFalse(root.resolve("folder/old.txt").exists())
                assertEquals("", store.read("folder/new.md"))

                store.delete("folder")
                assertFalse(root.resolve("folder").exists())
            }
        }

    @Test
    fun `path traversal and invalid names are rejected`() =
        runBlocking {
            withStore { root, store ->
                root.resolve("outside").writeText("outside")

                expectFailure(PathFailure.INVALID) { store.list("../outside") }
                expectFailure(PathFailure.INVALID) { store.read("../outside") }
                expectFailure(PathFailure.INVALID) { store.createFile("", "..") }
                expectFailure(PathFailure.NOT_FOUND) { store.rename("missing", "safe") }
            }
        }

    @Test
    fun `existing names cannot be overwritten or reused`() =
        runBlocking {
            withStore { root, store ->
                store.createFile("", "note.md")
                expectFailure(PathFailure.CONFLICT) { store.createFile("", "note.md") }
                expectFailure(PathFailure.CONFLICT) { store.createDirectory("", "note.md") }
                store.write("note.md", "changed")
                expectFailure(PathFailure.CONFLICT) {
                    store.rename("note.md", "note.md")
                }
                assertEquals("changed", root.resolve("note.md").readText())
            }
        }

    @Test
    fun `upload writes into the target directory and reports size`() =
        runBlocking {
            withStore { root, store ->
                store.createDirectory("", "notes")
                store.createDirectory("notes", "sub")

                val content = "héllo\nworld".toByteArray(Charsets.UTF_8)
                val upload = store.upload("notes/sub", "réport.md", channel(content))

                assertEquals("notes/sub/réport.md", upload.path)
                assertEquals(content.size.toLong(), upload.bytes)
                assertTrue(content.contentEquals(root.resolve("notes/sub/réport.md").readBytes()))
                assertEquals(listOf("sub"), store.list("notes").map { it.name })
            }
        }

    @Test
    fun `upload rejects unsafe names missing folders and collisions`() =
        runBlocking {
            withStore { root, store ->
                root.resolve("taken.txt").writeText("old")

                expectFailure(PathFailure.INVALID) { store.upload("", "..", channel(byteArrayOf(1))) }
                expectFailure(PathFailure.NOT_FOUND) { store.upload("missing", "note.md", channel(byteArrayOf(1))) }
                expectFailure(PathFailure.CONFLICT) { store.upload("", "taken.txt", channel(byteArrayOf(2))) }
                assertEquals("subdirstripped.md", store.upload("", "sub/dir/stripped.md", channel(byteArrayOf(4))).path)
                assertTrue(root.resolve("subdirstripped.md").isFile)
                assertEquals("old", root.resolve("taken.txt").readText())
                assertEquals(0, root.listFiles()!!.count { it.name.startsWith(".mdserver-") })
            }
        }

    private fun channel(content: ByteArray): ByteReadChannel = ByteReadChannel(ByteBuffer.wrap(content))

    private suspend fun expectFailure(
        failure: PathFailure,
        operation: suspend () -> Unit,
    ) {
        try {
            operation()
            fail("Expected $failure")
        } catch (e: PathStoreException) {
            assertEquals(failure, e.failure)
        }
    }

    private suspend fun withStore(block: suspend (java.io.File, PathStore) -> Unit) {
        val root = Files.createTempDirectory("path-store-test").toFile()
        try {
            block(root, PathStore { root })
        } finally {
            root.deleteRecursively()
        }
    }
}
