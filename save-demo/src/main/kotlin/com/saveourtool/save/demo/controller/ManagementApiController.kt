package com.saveourtool.save.demo.controller

import com.saveourtool.save.demo.DemoInfo
import com.saveourtool.save.demo.DemoStatus
import com.saveourtool.save.demo.entity.*
import com.saveourtool.save.demo.service.*
import com.saveourtool.save.utils.*
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.annotations.tags.Tags

import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Mono
import reactor.kotlin.core.util.function.component1
import reactor.kotlin.core.util.function.component2

/**
 * Controller that allows to query management's api
 */
@Tags(
    Tag(name = "demo"),
    Tag(name = "management"),
)
@RestController
@RequestMapping("/demo/api/manager")
class ManagementApiController(
    private val toolService: ToolService,
    private val demoService: DemoService,
) {
    /**
     * @param organizationName name of GitHub user/organization
     * @param projectName name of GitHub repository
     * @return [Mono] of [DemoStatus] of current demo
     */
    @GetMapping("/{organizationName}/{projectName}/status")
    fun getDemoStatus(
        @PathVariable organizationName: String,
        @PathVariable projectName: String,
    ): Mono<DemoStatus> = demoService.findBySaveourtoolProjectOrNotFound(organizationName, projectName) {
        "Could not find demo for $organizationName/$projectName."
    }
        .map {
            /*
             * todo:
             * kubernetesService.getStatus(it)
             */
            DemoStatus.STOPPED
        }

    /**
     * @param organizationName name of GitHub user/organization
     * @param projectName name of GitHub repository
     * @return [Mono] of [DemoStatus] of current demo
     */
    @GetMapping("/{organizationName}/{projectName}")
    fun getDemoInfo(
        @PathVariable organizationName: String,
        @PathVariable projectName: String,
    ): Mono<DemoInfo> = demoService.findBySaveourtoolProjectOrNotFound(organizationName, projectName) {
        "Could not find demo for $organizationName/$projectName."
    }
        .zipWith(getDemoStatus(organizationName, projectName))
        .map { (demo, status) ->
            DemoInfo(
                demo.toDto().copy(vcsTagName = ""),
                status,
            )
        }
        .zipWhen { demoInfo ->
            blockingToMono {
                demoInfo.demoDto
                    .githubProjectCoordinates
                    ?.let { repo ->
                        toolService.findCurrentVersion(repo)
                    } ?: "manual"
            }
        }
        .map { (demoInfo, currentVersion) ->
            demoInfo.copy(
                demoDto = demoInfo.demoDto.copy(
                    vcsTagName = currentVersion
                )
            )
        }
}