package io.titandata.remote.ssh.server

import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import io.kotlintest.specs.StringSpec
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import java.io.File
import java.lang.IllegalArgumentException
import java.nio.file.Files

class SshRemoteServerTest : StringSpec() {
    private val client = SshRemoteServer()

    private fun mockFile() : File {
        mockkStatic("kotlin.io.FilesKt__FileReadWriteKt")
        mockkStatic(Files::class)
        val file : File = mockk()
        every { file.path } returns "/path"
        every { file.toPath() } returns mockk()
        every { file.writeText(any()) } just Runs
        every { Files.setPosixFilePermissions(any(), any()) } returns mockk()
        return file
    }

    init {
        "get provider returns ssh" {
            client.getProvider() shouldBe "ssh"
        }

        "ssh auth fails if neither password nor key is specified in parameters" {
            shouldThrow<IllegalArgumentException> {
                client.getSshAuth(emptyMap(), emptyMap())
            }
        }

        "ssh auth fails if both password and key is specified in parameters" {
            shouldThrow<IllegalArgumentException> {
                client.getSshAuth(emptyMap(), mapOf("password" to "password", "key" to "key"))
            }
        }

        "ssh auth returns password if specified in parameters" {
            val (password, key) = client.getSshAuth(emptyMap(), mapOf("password" to "password"))
            password shouldBe "password"
            key shouldBe null
        }

        "ssh auth returns password if specified in remote" {
            val (password, key) = client.getSshAuth(mapOf("password" to "password"), emptyMap())
            password shouldBe "password"
            key shouldBe null
        }

        "ssh auth returns key if specified in parameters" {
            val (password, key) = client.getSshAuth(emptyMap(), mapOf("key" to "key"))
            password shouldBe null
            key shouldBe "key"
        }

        "build SSH command uses sshpass for password authentication" {
            val file = mockFile()
            val command = client.buildSshCommand(emptyMap(), mapOf("password" to "password"), file, false)
            command shouldBe arrayOf("sshpass", "-f", "/path", "ssh", "-o", "StrictHostKeyChecking=no",
                    "-o", "UserKnownHostsFile=/dev/null")
            verify {
                file.writeText("password")
            }
        }

        "build SSH command uses key file for key authentication" {
            val file = mockFile()
            val command = client.buildSshCommand(emptyMap(), mapOf("key" to "key"), file, false)
            command shouldBe arrayOf("ssh", "-i", "/path", "-o", "StrictHostKeyChecking=no",
                    "-o", "UserKnownHostsFile=/dev/null")
            verify {
                file.writeText("key")
            }
        }

        "build SSH command with port and address succeeds" {
            val file = mockFile()
            val command = client.buildSshCommand(mapOf("port" to 1234, "username" to "user", "address" to "host"),
                    mapOf("key" to "key"), file, true, "ls", "/var/tmp")
            command shouldBe arrayOf("ssh", "-i", "/path", "-p", "1234", "-o", "StrictHostKeyChecking=no",
                    "-o", "UserKnownHostsFile=/dev/null", "user@host", "ls", "/var/tmp")
        }
    }
}
