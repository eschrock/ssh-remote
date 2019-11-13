/*
 * Copyright The Titan Project Contributors.
 */

package io.titandata.remote.ssh.client

import io.kotlintest.TestCase
import io.kotlintest.TestResult
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import io.kotlintest.specs.StringSpec
import io.mockk.MockKAnnotations
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.OverrideMockKs
import java.io.Console
import java.net.URI

class SshRemoteClientTest : StringSpec() {

    @MockK
    lateinit var console: Console

    @InjectMockKs
    @OverrideMockKs
    var client = SshRemoteClient()

    override fun beforeTest(testCase: TestCase) {
        return MockKAnnotations.init(this)
    }

    override fun afterTest(testCase: TestCase, result: TestResult) {
        clearAllMocks()
    }

    init {
        "get provider returns ssh" {
            client.getProvider() shouldBe "ssh"
        }

        "parsing full SSH URI succeeds" {
            val result = client.parseUri(URI("ssh://user:pass@host:8022/path"), emptyMap())
            result["username"] shouldBe "user"
            result["password"] shouldBe "pass"
            result["address"] shouldBe "host"
            result["port"] shouldBe 8022
            result["path"] shouldBe "/path"
            result["keyFile"] shouldBe null
        }

        "parsing simple SSH URI succeeds" {
            val result = client.parseUri(URI("ssh://user@host/path"), emptyMap())
            result["username"] shouldBe "user"
            result["password"] shouldBe null
            result["address"] shouldBe "host"
            result["port"] shouldBe null
            result["path"] shouldBe "/path"
            result["keyFile"] shouldBe null
        }

        "specifying key file in properties succeeds" {
            val result = client.parseUri(URI("ssh://user@host/path"), mapOf("keyFile" to "~/.ssh/id_dsa"))
            result["keyFile"] shouldBe "~/.ssh/id_dsa"
        }

        "parsing relative path succeeds" {
            val result = client.parseUri(URI("ssh://user@host/~/relative/path"), emptyMap())
            result["path"] shouldBe "relative/path"
        }

        "specifying password and key file fails" {
            shouldThrow<IllegalArgumentException> {
                client.parseUri(URI("ssh://user:password@host/path"), mapOf("keyFile" to "~/.ssh/id_dsa"))
            }
        }

        "specifying an invalid property fails" {
            shouldThrow<IllegalArgumentException> {
                client.parseUri(URI("ssh://user@host/path"), mapOf("foo" to "bar"))
            }
        }

        "missing host fials" {
            shouldThrow<IllegalArgumentException> {
                client.parseUri(URI("ssh:///path"), emptyMap())
            }
        }

        "plain ssh provider fails" {
            shouldThrow<IllegalArgumentException> {
                client.parseUri(URI("ssh"), emptyMap())
            }
        }

        "missing username in ssh URI fails" {
            shouldThrow<IllegalArgumentException> {
                client.parseUri(URI("ssh://host/path"), emptyMap())
            }
        }

        "missing path in ssh URI fails" {
            shouldThrow<IllegalArgumentException> {
                client.parseUri(URI("ssh://user@host"), emptyMap())
            }
        }

        "missing host in ssh URI fails" {
            shouldThrow<IllegalArgumentException> {
                client.parseUri(URI("ssh://user@/path"), emptyMap())
            }
        }

        "basic SSH remote to URI succeeds" {
            val (uri, parameters) = client.toUri(mapOf("username" to "username", "address" to "host",
                    "path" to "/path"))
            uri shouldBe "ssh://username@host/path"
            parameters.size shouldBe 0
        }

        "SSH remote with password to URI succeeds" {
            val (uri, parameters) = client.toUri(mapOf("username" to "username", "address" to "host",
                    "path" to "/path", "password" to "pass"))
            uri shouldBe "ssh://username:*****@host/path"
            parameters.size shouldBe 0
        }

        "SSH remote with port to URI succeeds" {
            val (uri, parameters) = client.toUri(mapOf("username" to "username", "address" to "host",
                    "path" to "/path", "port" to 812))
            uri shouldBe "ssh://username@host:812/path"
            parameters.size shouldBe 0
        }

        "SSH remote with relative path to URI succeeds" {
            val (uri, parameters) = client.toUri(mapOf("username" to "username", "address" to "host",
                    "path" to "path"))
            uri shouldBe "ssh://username@host/~/path"
            parameters.size shouldBe 0
        }

        "SSH remote with keyfile to URI succeeds" {
            val (uri, parameters) = client.toUri(mapOf("username" to "username", "address" to "host",
                    "path" to "/path", "keyFile" to "keyfile"))
            uri shouldBe "ssh://username@host/path"
            parameters.size shouldBe 1
            parameters["keyFile"] shouldBe "keyfile"
        }

        "get basic SSH get parameters succeeds" {
            val params = client.getParameters(mapOf("username" to "username", "address" to "host",
                    "path" to "/path", "password" to "pass"))
            params["password"] shouldBe null
            params["key"] shouldBe null
        }

        "get SSH parameters with keyfile succeeds" {
            val keyFile = createTempFile()
            try {
                keyFile.writeText("KEY")
                val params = client.getParameters(mapOf("username" to "username", "address" to "host",
                        "path" to "/path", "keyFile" to keyFile.absolutePath))
                params["password"] shouldBe null
                params["key"] shouldBe "KEY"
            } finally {
                keyFile.delete()
            }
        }

        "prompt for SSH password succeeds" {
            every { console.readPassword(any()) } returns "pass".toCharArray()
            val params = client.getParameters(mapOf("username" to "username", "address" to "host",
                    "path" to "/path"))
            params["password"] shouldBe "pass"
            params["key"] shouldBe null
        }
    }
}
