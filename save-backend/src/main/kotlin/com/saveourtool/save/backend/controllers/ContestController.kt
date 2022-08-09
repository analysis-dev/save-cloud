package com.saveourtool.save.backend.controllers

import com.saveourtool.save.backend.StringResponse
import com.saveourtool.save.backend.configs.ApiSwaggerSupport
import com.saveourtool.save.backend.configs.RequiresAuthorizationSourceHeader
import com.saveourtool.save.backend.security.OrganizationPermissionEvaluator
import com.saveourtool.save.backend.service.ContestService
import com.saveourtool.save.backend.service.OrganizationService
import com.saveourtool.save.backend.service.TestService
import com.saveourtool.save.backend.storage.TestSuitesSourceSnapshotStorage
import com.saveourtool.save.backend.utils.justOrNotFound
import com.saveourtool.save.entities.Contest.Companion.toContest
import com.saveourtool.save.entities.ContestDto
import com.saveourtool.save.permission.Permission
import com.saveourtool.save.test.TestFilesContent
import com.saveourtool.save.test.TestFilesRequest
import com.saveourtool.save.utils.switchIfEmptyToNotFound
import com.saveourtool.save.utils.switchIfEmptyToResponseException
import com.saveourtool.save.v1
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.Parameters
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.annotations.tags.Tags
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.switchIfEmpty
import reactor.kotlin.core.publisher.toMono
import reactor.kotlin.core.util.function.component1
import reactor.kotlin.core.util.function.component2
import java.util.*
import kotlin.jvm.optionals.getOrNull

/**
 * Controller for working with contests.
 */
@ApiSwaggerSupport
@Tags(
    Tag(name = "contests"),
)
@RestController
@OptIn(ExperimentalStdlibApi::class)
@RequestMapping(path = ["/api/$v1/contests"])
@Suppress("LongParameterList")
internal class ContestController(
    private val contestService: ContestService,
    private val testService: TestService,
    private val organizationPermissionEvaluator: OrganizationPermissionEvaluator,
    private val organizationService: OrganizationService,
    private val testSuitesSourceSnapshotStorage: TestSuitesSourceSnapshotStorage,
) {
    @GetMapping("/{contestName}")
    @Operation(
        method = "GET",
        summary = "Get contest by name.",
        description = "Get contest by name.",
    )
    @Parameters(
        Parameter(name = "contestName", `in` = ParameterIn.PATH, description = "name of a contest", required = true),
    )
    @ApiResponse(responseCode = "200", description = "Successfully fetched contest by it's name.")
    @ApiResponse(responseCode = "404", description = "Contest with such name was not found.")
    fun getContestByName(@PathVariable contestName: String): Mono<ContestDto> = justOrNotFound(contestService.findByName(contestName))
        .map { it.toDto() }

    @GetMapping("/active")
    @Operation(
        method = "GET",
        summary = "Get list of contests that are in progress now.",
        description = "Get list of contests that are in progress now.",
    )
    @Parameters(
        Parameter(name = "pageSize", `in` = ParameterIn.QUERY, description = "amount of contests that should be returned, default: 10", required = false),
    )
    @ApiResponse(responseCode = "200", description = "Successfully fetched list of active contests.")
    fun getContestsInProgress(
        @RequestParam(defaultValue = "10") pageSize: Int,
    ): Flux<ContestDto> = Flux.fromIterable(
        contestService.findContestsInProgress(pageSize)
    ).map { it.toDto() }

    @GetMapping("/finished")
    @Operation(
        method = "GET",
        summary = "Get list of contests that has already finished.",
        description = "Get list of contests that has already finished.",
    )
    @Parameters(
        Parameter(name = "pageSize", `in` = ParameterIn.QUERY, description = "amount of contests that should be returned, default: 10", required = false),
    )
    @ApiResponse(responseCode = "200", description = "Successfully fetched list of finished contests.")
    fun getFinishedContests(
        @RequestParam(defaultValue = "10") pageSize: Int,
    ): Flux<ContestDto> = Flux.fromIterable(
        contestService.findFinishedContests(pageSize)
    ).map { it.toDto() }

    @GetMapping("/{contestName}/public-test")
    @Operation(
        method = "GET",
        summary = "Get public test for contest with given name.",
        description = "Get public test for contest with given name.",
    )
    @Parameters(
        Parameter(name = "contestName", `in` = ParameterIn.PATH, description = "name of a contest", required = true),
    )
    @ApiResponse(responseCode = "200", description = "Successfully fetched public tests.")
    @ApiResponse(responseCode = "404", description = "Either contest with such name was not found or tests are not provided.")
    fun getPublicTestForContest(
        @PathVariable contestName: String,
    ): Mono<TestFilesContent> {
        val contest = contestService.findByName(contestName).getOrNull()
            ?: return Mono.error(ResponseStatusException(HttpStatus.NOT_FOUND))
        val testSuite = contestService.getTestSuiteForPublicTest(contest)
            ?: return Mono.error(ResponseStatusException(HttpStatus.NOT_FOUND))
        val test = testService.findTestsByTestSuiteId(testSuite.requiredId()).firstOrNull()
            ?: return Mono.error(ResponseStatusException(HttpStatus.NOT_FOUND))
        return testSuitesSourceSnapshotStorage.getTestContent(TestFilesRequest(test.toDto(), testSuite.source.toDto(), testSuite.version))
            .map { testFilesContent ->
                testFilesContent.copy(
                    language = testSuite.language,
                )
            }
    }

    @GetMapping("/by-organization")
    @Operation(
        method = "GET",
        summary = "Get contests connected with given organization.",
        description = "Get contests connected with given organization.",
    )
    @Parameters(
        Parameter(name = "organizationName", `in` = ParameterIn.QUERY, description = "name of an organization", required = true),
        Parameter(name = "pageSize", `in` = ParameterIn.QUERY, description = "amount of records that will be returned", required = false),
    )
    @ApiResponse(responseCode = "200", description = "Successfully fetched public tests.")
    @ApiResponse(responseCode = "404", description = "Either contest with such name was not found or tests are not provided.")
    fun getOrganizationContests(
        @RequestParam organizationName: String,
        @RequestParam(required = false, defaultValue = "10") pageSize: Int,
    ): Flux<ContestDto> = Flux.fromIterable(
        contestService.findPageOfContestsByOrganizationName(organizationName, Pageable.ofSize(pageSize))
    )
        .map { it.toDto() }

    @PostMapping("/create")
    @RequiresAuthorizationSourceHeader
    @PreAuthorize("isAuthenticated()")
    @Operation(
        method = "POST",
        summary = "Create a new contest.",
        description = "Create a new contest.",
    )
    @Parameters(
        Parameter(name = "contestDto", `in` = ParameterIn.DEFAULT, description = "contest requested for creation", required = true),
    )
    @ApiResponse(responseCode = "200", description = "Contest was successfully created.")
    @ApiResponse(responseCode = "404", description = "Organization with given name was not found.")
    @ApiResponse(responseCode = "403", description = "User cannot create contests with given organization.")
    @ApiResponse(responseCode = "409", description = "Contest with given name is already present.")
    @Suppress("TYPE_ALIAS", "TOO_LONG_FUNCTION")
    fun createContest(
        @RequestBody contestDto: ContestDto,
        authentication: Authentication,
    ): Mono<StringResponse> = Mono.just(
        contestDto.organizationName
    )
        .flatMap {
            organizationService.findByName(it).toMono()
        }
        .switchIfEmpty {
            Mono.error(ResponseStatusException(HttpStatus.NOT_FOUND))
        }
        .filter {
            organizationPermissionEvaluator.canCreateContests(it, authentication)
        }
        .switchIfEmpty {
            Mono.error(ResponseStatusException(HttpStatus.FORBIDDEN))
        }
        .map {
            contestDto.toContest(it)
        }
        .filter {
            it.validate()
        }
        .switchIfEmpty {
            Mono.error(ResponseStatusException(HttpStatus.CONFLICT, "Contest data is not valid."))
        }
        .filter {
            contestService.createContestIfNotPresent(it)
        }
        .switchIfEmpty {
            Mono.error(ResponseStatusException(
                HttpStatus.CONFLICT,
                "Contest with name ${contestDto.name} is already present",
            ))
        }
        .map {
            ResponseEntity.ok("Contest has been successfully created!")
        }

    @PostMapping("/update")
    @RequiresAuthorizationSourceHeader
    @PreAuthorize("isAuthenticated()")
    @Operation(
        method = "POST",
        summary = "Update contest.",
        description = "Change existing contest settings.",
    )
    @Parameters(
        Parameter(name = "contestRequest", `in` = ParameterIn.DEFAULT, description = "name of an organization", required = true),
    )
    @ApiResponse(responseCode = "200", description = "Successfully fetched public tests.")
    @ApiResponse(responseCode = "403", description = "Not enough permission to edit current contest.")
    @ApiResponse(responseCode = "404", description = "Either organization or contest with such name was not found.")
    fun updateContest(
        @RequestBody contestRequest: ContestDto,
        authentication: Authentication,
    ): Mono<StringResponse> = Mono.zip(
        organizationService.findByName(contestRequest.organizationName).toMono(),
        Mono.justOrEmpty(contestService.findByName(contestRequest.name)),
    )
        .switchIfEmptyToNotFound {
            "Either organization [${contestRequest.organizationName}] or contest [${contestRequest.name}] was not found."
        }
        .filter { (organization, _) ->
            organizationPermissionEvaluator.hasPermission(authentication, organization, Permission.DELETE)
        }
        .switchIfEmptyToResponseException(HttpStatus.FORBIDDEN) {
            "You do not have enough permissions to edit this contest."
        }
        .map { (organization, contest) ->
            contestService.updateContest(
                contestRequest.toContest(organization, contest.testSuiteIds, contest.status).apply { id = contest.id }
            )
            ResponseEntity.ok("Contest successfully updated")
        }
}
