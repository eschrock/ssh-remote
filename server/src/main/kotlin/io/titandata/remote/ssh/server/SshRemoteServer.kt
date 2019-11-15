package io.titandata.remote.ssh.server

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import io.titandata.remote.RemoteOperation
import io.titandata.remote.RemoteServer
import io.titandata.remote.RemoteServerUtil
import io.titandata.shell.CommandException
import io.titandata.shell.CommandExecutor
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission

class SshRemoteServer : RemoteServer {

    internal val executor = CommandExecutor()
    internal val gson = GsonBuilder().create()
    internal val util = RemoteServerUtil()

    /**
     * Validate remote configuration. Required parameters include (username, address, path). Optional parameters include
     * (password, port, keyFile). For the port, we have to
     */
    override fun validateRemote(remote: Map<String, Any>): Map<String, Any> {
        val validated = mutableMapOf<String, Any>()
        for (prop in listOf("username", "address", "path")) {
            if (!remote.containsKey(prop)) {
                throw IllegalArgumentException("missing required remote property '$prop")
            }
            validated[prop] = remote[prop]!!.toString()
        }

        for (prop in listOf("password", "port", "keyFile")) {
            if (remote.containsKey(prop)) {
                if (prop == "port") {
                    val port = if (remote[prop] is Double) {
                        (remote[prop] as Double).toInt()
                    } else if (remote[prop] is Int) {
                        remote[prop] as Int
                    } else {
                        throw IllegalArgumentException("port must be a number or integer")
                    }
                    validated[prop] = port
                } else {
                    validated[prop] = remote[prop]!!.toString()
                }
            }
        }

        for (prop in remote.keys) {
            if (!validated.containsKey(prop)) {
                throw IllegalArgumentException("invalid property '$prop'")
            }
        }
        return validated
    }

    /**
     * Validate parameters, which can optionall contain either (password, key)
     */
    override fun validateParameters(parameters: Map<String, Any>): Map<String, Any> {
        for (prop in parameters.keys) {
            if (prop != "password" && prop != "key") {
                throw IllegalArgumentException("invalid property '$prop'")
            }
        }
        return parameters
    }

    /**
     * This method will parse the remote configuration and parameters to determine if we should use password
     * authentication or key-based authentication. It returns a pair where exactly one element must be set, either
     * the first (password) or second (key).
     */
    internal fun getSshAuth(remote: Map<String, Any>, parameters: Map<String, Any>): Pair<String?, String?> {
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

    internal fun buildSshCommand(
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

    internal fun runSsh(remote: Map<String, Any>, parameters: Map<String, Any>, vararg command: String): String {
        val file = createTempFile()
        file.deleteOnExit()
        try {
            val args = buildSshCommand(remote, parameters, file, true, *command)
            return executor.exec(*args.toTypedArray())
        } finally {
            file.delete()
        }
    }

    override fun getProvider(): String {
        return "ssh"
    }

    /**
     * To get a commit, we look up the metadata.json file in the directory named by the given commit ID.
     */
    override fun getCommit(remote: Map<String, Any>, parameters: Map<String, Any>, commitId: String): Map<String, Any>? {
        try {
            val json = runSsh(remote, parameters, "cat", "${remote["path"]}/$commitId/metadata.json")
            return gson.fromJson(json, object : TypeToken<Map<String, Any>>() {}.type)
        } catch (e: CommandException) {
            if (e.output.contains("No such file or directory")) {
                return null
            }
            throw e
        }
    }

    /**
     * To list commits, we first iterate over all directory entries in the target path. We then have to invoke
     * getCommit() for each one to read the contents of the files. There are certainly more efficient methods, but
     * this is straightforward and sufficient for this simplistic remote provider.
     */
    override fun listCommits(remote: Map<String, Any>, parameters: Map<String, Any>, tags: List<Pair<String, String?>>): List<Pair<String, Map<String, Any>>> {
        val output = runSsh(remote, parameters, "ls", "-1", remote["path"] as String)
        val commits = mutableListOf<Pair<String, Map<String, Any>>>()
        for (line in output.lines()) {
            val commitId = line.trim()
            if (commitId != "") {
                val commit = getCommit(remote, parameters, commitId)
                if (commit != null && util.matchTags(commit, tags)) {
                    commits.add(commitId to commit)
                }
            }
        }

        return util.sortDescending(commits)
    }

    override fun endOperation(operation: RemoteOperation, isSuccessful: Boolean) {
        throw NotImplementedError()
    }

    override fun startOperation(operation: RemoteOperation) {
        throw NotImplementedError()
    }

    override fun syncVolume(operation: RemoteOperation, volumeName: String, volumeDescription: String, volumePath: String, scratchPath: String) {
        throw NotImplementedError()
    }
}
