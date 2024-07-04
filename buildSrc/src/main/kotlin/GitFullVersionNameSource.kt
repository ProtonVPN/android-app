/*
 * Copyright (c) 2024. Proton AG
 *
 * This file is part of ProtonVPN.
 *
 * ProtonVPN is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ProtonVPN is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ProtonVPN.  If not, see <https://www.gnu.org/licenses/>.
 */

import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject

abstract class GitFullVersionNameSource : ValueSource<String, GitFullVersionNameSource.Parameters> {

    @get:Inject
    abstract val execOperations: ExecOperations

    override fun obtain(): String? {
        // Find last tag in the form M.m.D.R, D and R are optional
        val tag = exec("git", "tag", "--sort=v:refname", "--merged", "HEAD")
            .trim()
            .split("\n")
            .reversed()
            .find { it.matches(Regex("\\d+(\\.\\d+){1,3}")) }
        val tagSplit = tag
            ?.split(".")
            ?.map { it.toInt() }
        if (tagSplit == null || tagSplit.size < 2)
            throw IllegalArgumentException("Tag doesn't match the version name pattern: $tag")

        val major = tagSplit[0]
        val minor = tagSplit[1]
        var dev = tagSplit.getOrElse(2) { 0 }
        var release = tagSplit.getOrElse(3) { 0 }

        val onMaster = System.getenv("CI_COMMIT_BRANCH") == "master" ||
            exec("git", "rev-parse", "--abbrev-ref", "HEAD").trim() == "master"
        // On master or public repo just use last tag for version name
        if (!onMaster && execOrNull("git", "rev-parse", "--verify", "origin/development") != null) {
            // Tag is on development
            if (execOrNull("git", "merge-base", "--is-ancestor", "$tag", "origin/development") != null) {
                val branchPoint = exec("git", "merge-base", "origin/development", "HEAD").trim()
                // add #commits from tag to branch point with development to dev
                dev += exec("git", "rev-list", "--count", "${tag}..${branchPoint}").trim().toInt()
                // add #commits from branch point to HEAD to release
                release += exec("git", "rev-list", "--count", "origin/development..HEAD").trim().toInt()
            } else { // Tag is on current branch
                println("### tag: $tag")
                release += exec("git", "rev-list", "--count", "${tag}..HEAD").trim().toInt()
            }
        }
        return "${major}.${minor}.${dev}.${release}"
    }

    private fun exec(vararg commandArgs: String) = requireNotNull(execInternal(*commandArgs, throwOnError = true))

    private fun execOrNull(vararg commandArgs: String) = execInternal(*commandArgs, throwOnError = false)

    private fun execInternal(vararg commandArgs: String, throwOnError: Boolean = true): String? {
        val output = ByteArrayOutputStream()
        try {
            execOperations.exec {
                commandLine = commandArgs.toList()
                standardOutput = output
                workingDir = parameters.workingDir
            }
        } catch (e: Exception) {
            if (throwOnError) throw e
            else return null
        }
        return String(output.toByteArray())
    }

    interface Parameters : ValueSourceParameters {
        var workingDir: File
    }
}
