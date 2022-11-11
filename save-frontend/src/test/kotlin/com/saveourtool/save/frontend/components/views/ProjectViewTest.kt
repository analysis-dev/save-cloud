package com.saveourtool.save.frontend.components.views

import com.saveourtool.save.domain.FileInfo
import com.saveourtool.save.domain.Role
import com.saveourtool.save.entities.*
import com.saveourtool.save.frontend.externals.*
import com.saveourtool.save.frontend.utils.apiUrl
import com.saveourtool.save.frontend.utils.mockMswResponse
import com.saveourtool.save.frontend.utils.wrapper
import com.saveourtool.save.info.UserInfo
import com.saveourtool.save.utils.LocalDateTime

import react.create
import react.react

import kotlin.js.Promise
import kotlin.test.*
import kotlinx.js.jso

class ProjectViewTest {
    private val testOrganization = Organization(
        "TestOrg",
        OrganizationStatus.CREATED,
        LocalDateTime(2022, 6, 1, 12, 25),
    )
    private val testProject = Project(
        "TestProject",
        null,
        "Project Description",
        ProjectStatus.CREATED,
        true,
        "email@test.org",
        organization = testOrganization,
    )
    private val testUserInfo = UserInfo(
        "TestUser",
        source = "basic",
        projects = mapOf(testProject.name to Role.VIEWER),
        organizations = mapOf(testOrganization.name to Role.VIEWER),
        globalRole = Role.SUPER_ADMIN,
    )

    @Suppress("TOO_LONG_FUNCTION")
    private fun createWorker() = setupWorker(
        rest.get("$apiUrl/projects/get/organization-name") { _, res, _ ->
            res { response ->
                mockMswResponse(
                    response,
                    testProject
                )
            }
        },
        rest.get("$apiUrl/projects/${testOrganization.name}/${testProject.name}/users/roles") { _, res, _ ->
            res { response ->
                mockMswResponse(
                    response,
                    testUserInfo.projects[testProject.name]
                )
            }
        },
        rest.get("$apiUrl/files/${testOrganization.name}/${testProject.name}/list") { _, res, _ ->
            res { response ->
                mockMswResponse(
                    response,
                    emptyList<FileInfo>()
                )
            }
        },
        rest.get("$apiUrl/latestExecution") { _, res, _ ->
            res { response ->
                mockMswResponse(
                    response,
                    0.toLong()
                )
            }
        },
    )

    @Test
    fun projectViewShouldRender(): Promise<*> {
        val worker = createWorker()
        return (worker.start() as Promise<*>).then {
            renderProjectView()
        }
            .then {
                screen.findByText(
                    "Project ${testProject.name}",
                    waitForOptions = jso {
                        timeout = 15000
                    }
                )
            }
            .then {
                assertNotNull(it, "Should show project name")
            }
            .then {
                worker.stop()
            }
    }

    private fun renderProjectView(userInfo: UserInfo = testUserInfo) = wrapper.create {
        ProjectView::class.react {
            owner = testOrganization.name
            name = testProject.name
            currentUserInfo = userInfo
        }
    }.let {
        render(it)
    }
}
