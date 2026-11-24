package com.kixyu9527.kixyubook.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.*
import javax.inject.Inject

/** App distribution owns license packaging; modules own neither license copies nor this task. */
@CacheableTask
abstract class PrepareThirdPartyAssets @Inject constructor(private val files: FileSystemOperations) : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val licenses: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val notices: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val misplacedLicenses: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun prepare() {
        check(misplacedLicenses.isEmpty) {
            "Third-party licenses belong in /third_party/licenses, not module assets: ${misplacedLicenses.files}"
        }
        files.sync {
            from(licenses) { into("licenses") }
            from(notices) { into("licenses") }
            into(outputDirectory)
        }
    }
}
