/**
 * Controller for processing links between users and their roles in organizations:
 * 1) to put new roles of users
 * 2) to get users and their roles by organization
 * 3) to remove users from organizations
 */

package com.saveourtool.save.backend.controllers

import com.saveourtool.save.backend.StringResponse
import com.saveourtool.save.backend.configs.ApiSwaggerSupport
import com.saveourtool.save.backend.configs.RequiresAuthorizationSourceHeader
import com.saveourtool.save.backend.security.OrganizationPermissionEvaluator
import com.saveourtool.save.backend.security.TestSuitePermissionEvaluator
import com.saveourtool.save.backend.service.LnkOrganizationTestSuiteService
import com.saveourtool.save.backend.service.OrganizationService
import com.saveourtool.save.backend.service.TestSuitesService
import com.saveourtool.save.domain.Role
import com.saveourtool.save.domain.isAllowedForContests
import com.saveourtool.save.entities.LnkOrganizationTestSuiteDto
import com.saveourtool.save.entities.TestSuite
import com.saveourtool.save.filters.TestSuiteFilters
import com.saveourtool.save.permission.Permission
import com.saveourtool.save.permission.Rights
import com.saveourtool.save.permission.SetRightsRequest
import com.saveourtool.save.testsuite.TestSuiteDto
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
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toMono
import reactor.kotlin.core.util.function.component1
import reactor.kotlin.core.util.function.component2

import java.util.*

/**
 * Controller for processing links between organizations and their rights over test suites
 */
@ApiSwaggerSupport
@Tags(
    Tag(name = "rights"),
    Tag(name = "organizations"),
    Tag(name = "test-suites"),
)
@RestController
@RequestMapping("/api/$v1/test-suites")
class LnkOrganizationTestSuiteController(
    private val lnkOrganizationTestSuiteService: LnkOrganizationTestSuiteService,
    private val organizationService: OrganizationService,
    private val organizationPermissionEvaluator: OrganizationPermissionEvaluator,
    private val testSuitesService: TestSuitesService,
    private val testSuitePermissionEvaluator: TestSuitePermissionEvaluator,
) {
    @GetMapping("/{organizationName}/available")
    @RequiresAuthorizationSourceHeader
    @PreAuthorize("permitAll()")
    @Operation(
        method = "GET",
        summary = "Get list of test suites that are available for given organization.",
        description = "Get list of test suites that are available for given organization.",
    )
    @Parameters(
        Parameter(name = "organizationName", `in` = ParameterIn.PATH, description = "name of an organization", required = true),
        Parameter(name = "isContest", `in` = ParameterIn.QUERY, description = "is given request sent for browsing test suites for contest, default is false", required = false),
    )
    @ApiResponse(responseCode = "200", description = "Successfully fetched test suites available for given organization.")
    @ApiResponse(responseCode = "403", description = "Current user doesn't have enough permissions to access test suites from current organization.")
    @ApiResponse(responseCode = "404", description = "Organization with such name was not found.")
    fun getAvailableTestSuitesByOrganization(
        @PathVariable organizationName: String,
        @RequestParam(defaultValue = "false") isContest: Boolean,
        @RequestParam(defaultValue = "false") onlyPrivate: Boolean,
        authentication: Authentication,
    ): Flux<TestSuiteDto> = getOrganizationIfParticipant(organizationName, authentication)
        .map { organization ->
            organization to (lnkOrganizationTestSuiteService.getAllTestSuitesByOrganization(organization) + testSuitesService.getPublicTestSuites())
                .distinctBy { it.requiredId() }
        }
        .map { (organization, testSuites) ->
            testSuites.filter { testSuite ->
                testSuitePermissionEvaluator.hasPermission(
                    organization,
                    testSuite,
                    if (onlyPrivate) {
                        Permission.WRITE
                    } else {
                        Permission.READ
                    },
                    authentication,
                )
            }
        }
        .mapToDtos(isContest)

    @GetMapping("/public")
    @PreAuthorize("permitAll()")
    @Operation(
        method = "GET",
        summary = "Get list of public test suites.",
        description = "Get list of public test suites.",
    )
    @Parameters(
        Parameter(name = "organizationName", `in` = ParameterIn.PATH, description = "name of an organization", required = true),
        Parameter(name = "isContest", `in` = ParameterIn.QUERY, description = "is given request sent for browsing test suites for contest, default is false", required = false),
    )
    @ApiResponse(responseCode = "200", description = "Successfully fetched public test suites.")
    fun getPublicTestSuites(
        @RequestParam(defaultValue = "false") isContest: Boolean
    ): Flux<TestSuiteDto> = testSuitesService.getPublicTestSuites().toMono()
        .mapToDtos(isContest)

    @PostMapping("/{organizationName}/get-by-ids")
    @RequiresAuthorizationSourceHeader
    @PreAuthorize("permitAll()")
    @Operation(
        method = "POST",
        summary = "Get test suites by ids.",
        description = "Get list of available test suites for given organization by their ids.",
    )
    @Parameters(
        Parameter(name = "organizationName", `in` = ParameterIn.PATH, description = "name of an organization", required = true),
        Parameter(name = "isContest", `in` = ParameterIn.QUERY, description = "is given request sent for browsing test suites for contest, default is false", required = false),
    )
    @ApiResponse(responseCode = "200", description = "Successfully fetched test suites by ids.")
    @ApiResponse(responseCode = "403", description = "Current user doesn't have enough permissions to access test suites from current organization.")
    @ApiResponse(responseCode = "404", description = "Organization with such name was not found.")
    fun getTestSuitesByIds(
        @PathVariable organizationName: String,
        @RequestBody testSuiteIds: List<Long>,
        @RequestParam(required = false, defaultValue = "false") isContest: Boolean,
        authentication: Authentication,
    ): Flux<TestSuiteDto> = getOrganizationIfParticipant(organizationName, authentication)
        .zipWith(testSuitesService.findTestSuitesByIds(testSuiteIds).toMono())
        .map { (organization, testSuites) ->
            testSuites.filter {
                testSuitePermissionEvaluator.hasPermission(organization, it, Permission.READ, authentication)
            }
        }
        .mapToDtos(isContest)

    @GetMapping("/{organizationName}/filtered")
    @PreAuthorize("permitAll()")
    @Operation(
        method = "GET",
        summary = "Get test suites with filters.",
        description = "Get test suites with filters.",
    )
    @Parameters(
        Parameter(name = "tags", `in` = ParameterIn.QUERY, description = "test suite tags substring for filtering, default is empty", required = false),
        Parameter(name = "name", `in` = ParameterIn.QUERY, description = "test suite name substring for filtering, default is empty", required = false),
        Parameter(name = "language", `in` = ParameterIn.QUERY, description = "test suite language substring for filtering, default is empty", required = false),
        Parameter(name = "isContest", `in` = ParameterIn.QUERY, description = "is given request sent for browsing test suites for contest, default is false", required = false),
    )
    @ApiResponse(responseCode = "200", description = "Successfully fetched filtered test suites.")
    @Suppress("TOO_MANY_PARAMETERS", "LongParameterList")
    fun getFilteredTestSuites(
        @PathVariable organizationName: String,
        @RequestParam(required = false, defaultValue = "") tags: String,
        @RequestParam(required = false, defaultValue = "") name: String,
        @RequestParam(required = false, defaultValue = "") language: String,
        @RequestParam(required = false, defaultValue = "false") isContest: Boolean,
        authentication: Authentication,
    ): Flux<TestSuiteDto> = getOrganizationIfParticipant(organizationName, authentication)
        .zipWith(TestSuiteFilters(name, language, tags).toMono())
        .map { (organization, testSuiteFilters) ->
            testSuitesService.findTestSuitesMatchingFilters(testSuiteFilters).filter {
                testSuitePermissionEvaluator.hasPermission(organization, it, Permission.READ, authentication)
            }
        }
        .mapToDtos(isContest)

    @GetMapping("/{organizationName}/{testSuiteId}")
    @RequiresAuthorizationSourceHeader
    @PreAuthorize("permitAll()")
    @Operation(
        method = "GET",
        summary = "Get organization's rights for given test suite.",
        description = "Get organization's rights for given test suite.",
    )
    @Parameters(
        Parameter(name = "organizationName", `in` = ParameterIn.PATH, description = "name of an organization for rights check", required = true),
        Parameter(name = "testSuiteId", `in` = ParameterIn.PATH, description = "id of a test suite", required = false),
    )
    @ApiResponse(responseCode = "200", description = "Successfully fetched organization's rights.")
    @ApiResponse(responseCode = "403", description = "Permissions for test suite access were not gained.")
    @ApiResponse(responseCode = "404", description = "Requested organization or test suite doesn't exist.")
    fun getRights(
        @PathVariable organizationName: String,
        @PathVariable testSuiteId: Long,
        authentication: Authentication,
    ): Mono<LnkOrganizationTestSuiteDto> = getTestSuiteAndOrganizationWithPermissions(testSuiteId, organizationName, Permission.WRITE, authentication)
        .filter { (organization, testSuite) ->
            testSuitePermissionEvaluator.hasPermission(organization, testSuite, Permission.READ, authentication)
        }
        .switchIfEmptyToResponseException(HttpStatus.FORBIDDEN) {
            "Permissions for test suite access were not gained (id = $testSuiteId)."
        }
        .map { (organization, testSuite) ->
            lnkOrganizationTestSuiteService.getDto(organization, testSuite)
        }

    @PostMapping("/{ownerOrganizationName}/{testSuiteId}")
    @RequiresAuthorizationSourceHeader
    @PreAuthorize("permitAll()")
    @Operation(
        method = "POST",
        summary = "Set organization's rights for given test suite.",
        description = "Set organization's rights for given test suite.",
    )
    @Parameters(
        Parameter(name = "ownerOrganizationName", `in` = ParameterIn.PATH, description = "name of an organization-maintainer", required = true),
        Parameter(name = "testSuiteId", `in` = ParameterIn.PATH, description = "id of test suite that is maintained", required = true),
    )
    @ApiResponse(responseCode = "200", description = "Rights changed")
    @ApiResponse(responseCode = "403", description = "Given organization has been forbidden to change given test suite rights")
    @ApiResponse(responseCode = "404", description = "Requested organization, test suite or organization-maintainer doesn't exist")
    @ApiResponse(responseCode = "409", description = "Cannot set Rights.NONE with this method.")
    fun setRights(
        @PathVariable ownerOrganizationName: String,
        @PathVariable testSuiteId: Long,
        @RequestBody setRightsRequest: SetRightsRequest,
        authentication: Authentication,
    ): Mono<StringResponse> = Mono.just(setRightsRequest)
        .filter {
            it.rights != Rights.NONE
        }
        .switchIfEmptyToResponseException(HttpStatus.CONFLICT) {
            "Cannot set Rights.NONE with this method. Use DELETE method instead."
        }
        .flatMap {
            getTestSuiteAndOrganizationWithPermissions(testSuiteId, ownerOrganizationName, Permission.WRITE, authentication)
        }
        .filter { (organizationMaintainer, testSuite) ->
            testSuitePermissionEvaluator.hasPermission(organizationMaintainer, testSuite, Permission.WRITE, authentication)
        }
        .switchIfEmptyToResponseException(HttpStatus.FORBIDDEN) {
            "Permissions for test suite management were not gained (testSuiteId = $testSuiteId)."
        }
        .flatMap { (_, testSuite) ->
            Mono.zip(
                organizationService.findByName(setRightsRequest.organizationName).toMono(),
                testSuite.toMono(),
            )
        }
        .switchIfEmptyToNotFound {
            "Organization with name ${setRightsRequest.organizationName} was not found."
        }
        .map { (requestedOrganization, testSuite) ->
            lnkOrganizationTestSuiteService.setOrDeleteRights(requestedOrganization, testSuite, setRightsRequest.rights)
            ResponseEntity.ok(
                "Successfully set rights ${setRightsRequest.rights} for organization ${setRightsRequest.organizationName} over test suite ${testSuite.name}."
            )
        }

    @PostMapping("/{ownerOrganizationName}/batch-set-rights")
    @RequiresAuthorizationSourceHeader
    @PreAuthorize("permitAll()")
    @Operation(
        method = "POST",
        summary = "Set organization's rights for given test suite.",
        description = "Set organization's rights for given test suite.",
    )
    @Parameters(
        Parameter(name = "ownerOrganizationName", `in` = ParameterIn.PATH, description = "name of an organization-maintainer", required = true),
    )
    @ApiResponse(responseCode = "200", description = "Rights changed")
    @ApiResponse(responseCode = "403", description = "Given organization has been forbidden to change given test suite rights")
    @ApiResponse(responseCode = "404", description = "Requested organization, test suite or organization-maintainer doesn't exist")
    fun setRightsBatched(
        @PathVariable ownerOrganizationName: String,
        @RequestBody setRightsRequest: SetRightsRequest,
        authentication: Authentication,
    ): Mono<StringResponse> = getOrganizationWithPermissions(ownerOrganizationName, Permission.WRITE, authentication)
        .zipWith(testSuitesService.findTestSuitesByIds(setRightsRequest.testSuiteIds).toMono())
        .map { (organizationMaintainer, testSuites) ->
            testSuites.filter { testSuite ->
                testSuitePermissionEvaluator.hasPermission(
                    organizationMaintainer,
                    testSuite,
                    if (setRightsRequest.rights == Rights.NONE) {
                        Permission.DELETE
                    } else {
                        Permission.WRITE
                    },
                    authentication,
                )
            }
        }
        .flatMap { testSuites ->
            Mono.zip(
                organizationService.findByName(setRightsRequest.organizationName).toMono(),
                testSuites.toMono(),
            )
        }
        .switchIfEmptyToNotFound {
            "Organization with name ${setRightsRequest.organizationName} was not found."
        }
        .map { (requestedOrganization, testSuites) ->
            testSuites.forEach { testSuite ->
                lnkOrganizationTestSuiteService.setOrDeleteRights(requestedOrganization, testSuite, setRightsRequest.rights)
            }
            testSuites
        }
        .map { testSuites ->
            setRightsRequest.testSuiteIds.filter { testSuiteId ->
                testSuiteId !in testSuites.map { it.requiredId() }
            }
        }
        .map { listOfFilteredOutTestSuiteIds ->
            val responseMessage: String = buildString {
                append("Successfully ")
                if (setRightsRequest.rights == Rights.NONE) {
                    append("deleted")
                } else {
                    append("set")
                }
                append(" rights ${setRightsRequest.rights} for organization ${setRightsRequest.organizationName} over requested test suites. ")
                if (listOfFilteredOutTestSuiteIds.isNotEmpty()) {
                    append("Test suites [${listOfFilteredOutTestSuiteIds.sorted().joinToString(", ")}] were skipped.")
                }
            }
            ResponseEntity.ok(responseMessage)
        }

    @DeleteMapping("/{ownerOrganizationName}/{testSuiteId}/{requestedOrganizationName}")
    @RequiresAuthorizationSourceHeader
    @PreAuthorize("permitAll()")
    @Operation(
        method = "DELETE",
        summary = "Remove organization's rights over test suite with given id.",
        description = "Remove organization's rights over test suite with given id.",
    )
    @Parameters(
        Parameter(name = "ownerOrganizationName", `in` = ParameterIn.PATH, description = "name of an organization-maintainer", required = true),
        Parameter(name = "testSuiteId", `in` = ParameterIn.PATH, description = "id of test suite that is maintained", required = true),
        Parameter(name = "requestedOrganizationName", `in` = ParameterIn.PATH, description = "name of an organization to be maintained", required = true),
    )
    @ApiResponse(responseCode = "200", description = "Rights were successfully removed")
    @ApiResponse(responseCode = "403", description = "Given organization has been forbidden to change given test suite rights")
    @ApiResponse(responseCode = "404", description = "Requested organization, test suite or organization-maintainer doesn't exist")
    fun removeRights(
        @PathVariable ownerOrganizationName: String,
        @PathVariable testSuiteId: Long,
        @PathVariable requestedOrganizationName: String,
        authentication: Authentication,
    ): Mono<StringResponse> = getTestSuiteAndOrganizationWithPermissions(testSuiteId, ownerOrganizationName, Permission.WRITE, authentication)
        .filter { (maintainerOrganization, testSuite) ->
            testSuitePermissionEvaluator.hasPermission(maintainerOrganization, testSuite, Permission.DELETE, authentication)
        }
        .switchIfEmptyToResponseException(HttpStatus.FORBIDDEN) {
            "Permissions for test suite management were not gained (testSuiteId = $testSuiteId)."
        }
        .flatMap { (_, testSuite) ->
            Mono.zip(
                organizationService.findByName(requestedOrganizationName).toMono(),
                testSuite.toMono(),
            )
        }
        .switchIfEmptyToNotFound {
            "Organization with name $requestedOrganizationName was not found."
        }
        .map { (requestedOrganization, testSuite) ->
            lnkOrganizationTestSuiteService.removeRights(requestedOrganization, testSuite)
            ResponseEntity.ok(
                "Successfully deleted rights of organization ${requestedOrganization.name} over test suite ${testSuite.name}."
            )
        }

    private fun getOrganizationIfParticipant(
        organizationName: String,
        authentication: Authentication?,
    ) = organizationService.findByName(organizationName)
        .toMono()
        .switchIfEmptyToNotFound {
            "Organization with name $organizationName was not found"
        }
        .filter {
            organizationPermissionEvaluator.hasOrganizationRole(authentication, it, Role.VIEWER)
        }
        .switchIfEmptyToResponseException(HttpStatus.FORBIDDEN) {
            "You must be a participant of $organizationName."
        }

    private fun getOrganizationWithPermissions(
        organizationName: String,
        permission: Permission,
        authentication: Authentication?,
    ) = organizationService.findByName(organizationName)
        .toMono()
        .switchIfEmptyToNotFound {
            "Organization with name $organizationName was not found"
        }
        .filter {
            organizationPermissionEvaluator.hasPermission(authentication, it, permission)
        }
        .switchIfEmptyToResponseException(HttpStatus.FORBIDDEN) {
            "You do not have enough permissions to access test suites from organization $organizationName."
        }

    private fun getTestSuiteAndOrganizationWithPermissions(
        testSuiteId: Long,
        organizationName: String,
        permission: Permission,
        authentication: Authentication?,
    ) = getOrganizationWithPermissions(organizationName, permission, authentication)
        .zipWith(testSuitesService.findTestSuiteById(testSuiteId).toMono())
        .switchIfEmptyToNotFound {
            "Could not find test suite with id $testSuiteId."
        }

    private fun Mono<List<TestSuite>>.mapToDtos(isContest: Boolean) = flatMapIterable {
        it
    }
        .filter {
            if (isContest) {
                it.pluginsAsListOfPluginType().isAllowedForContests()
            } else {
                it.pluginsAsListOfPluginType().isNotEmpty()
            }
        }
        .map {
            it.toDto()
        }
}
