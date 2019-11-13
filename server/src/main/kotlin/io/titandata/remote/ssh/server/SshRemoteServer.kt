package io.titandata.remote.ssh.server

import com.google.gson.GsonBuilder
import io.titandata.remote.RemoteServer
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission

class SshRemoteServer : RemoteServer {

    internal val gson = GsonBuilder().create()

    /**
     * This method will parse the remote configuration and parameters to determine if we should use password
     * authentication or key-based authentication. It returns a pair where exactly one element must be set, either
     * the first (password) or second (key).
     */
    internal fun getSshAuth(remote: Map<String, Any>, parameters: Map<String, Any> ): Pair<String?, String?> {
        if (parameters["password"] != null && parameters["key"] != null) {
            throw IllegalArgumentException("only one of password or key can be specified")
        } else if (remote["password"] != null || parameters["password"] != null) {
            return Pair((parameters["password"] ?: remote["password"]) as String, null)
        } else if (parameters["key"] != null) {
            return Pair(null, parameters["key"] as String)
        } else {
            throw IllegalArgumentException("one of password or key must be specified")
        }
    }

    fun buildSshCommand(
            remote: Map<String, Any>,
            parameters: Map<String, Any>,
            file: File,
            includeAddress: Boolean,
            vararg command: String
    ): List<String> {
        val args = mutableListOf<String>()

        val (password, key) = getSshAuth(remote, parameters)

        if (password != null) {
            file.writeText(password)
            args.addAll(arrayOf("sshpass", "-f", file.path, "ssh"))
        } else {
            file.writeText(key!!)
            args.addAll(arrayOf("ssh", "-i", file.path))
        }
        Files.setPosixFilePermissions(file.toPath(), mutableSetOf(
                PosixFilePermission.OWNER_READ
        ))

        if (remote["port"] != null) {
            args.addAll(arrayOf("-p", remote["port"].toString()))
        }

        args.addAll(arrayOf("-o", "StrictHostKeyChecking=no"))
        args.addAll(arrayOf("-o", "UserKnownHostsFile=/dev/null"))
        if (includeAddress) {
            args.add("${remote["username"]}@${remote["address"]}")
        }
        args.addAll(command)

        return args
    }


    override fun getProvider(): String {
        return "ssh"
    }

    /**
     * The nop provider always returns success for any commit, and returns an empty set of properties.
     */
    override fun getCommit(remote: Map<String, Any>, parameters: Map<String, Any>, commitId: String): Map<String, Any>? {
        return emptyMap()
    }

    /**
     * The nop provider always returns an empty list of commits.
     */
    override fun listCommits(remote: Map<String, Any>, parameters: Map<String, Any>, tags: List<Pair<String, String?>>): List<Pair<String, Map<String, Any>>> {
        return emptyList()
    }
}
