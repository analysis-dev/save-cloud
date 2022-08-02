package com.saveourtool.save.backend.controller

import com.saveourtool.save.backend.SaveApplication
import com.saveourtool.save.backend.repository.GitRepository
import com.saveourtool.save.backend.repository.OrganizationRepository
import com.saveourtool.save.backend.repository.ProjectRepository
import com.saveourtool.save.backend.service.LnkUserProjectService
import com.saveourtool.save.backend.utils.AuthenticationDetails
import com.saveourtool.save.backend.utils.MySqlExtension
import com.saveourtool.save.backend.utils.mutateMockedUser
import com.saveourtool.save.entities.*
import com.saveourtool.save.v1

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.test.mock.mockito.MockBeans
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.security.test.context.support.WithUserDetails
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody
import org.springframework.web.reactive.function.BodyInserters

@SpringBootTest(classes = [SaveApplication::class])
@AutoConfigureWebTestClient
@ExtendWith(MySqlExtension::class)
@MockBeans(
    MockBean(LnkUserProjectService::class),
)
@Suppress("UnsafeCallOnNullableType")
class ProjectControllerTest {
    @Autowired
    private lateinit var projectRepository: ProjectRepository

    @Autowired
    private lateinit var organizationRepository: OrganizationRepository

    @Autowired
    private lateinit var gitRepository: GitRepository

    @Autowired
    lateinit var webClient: WebTestClient

    @Test
    @WithMockUser
    fun `should return all public projects`() {
        mutateMockedUser {
            details = AuthenticationDetails(id = 99)
        }

        webClient
            .get()
            .uri("/api/$v1/projects/not-deleted")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .isOk
            .expectBody<List<Project>>()
            .consumeWith { exchangeResult ->
                val projects = exchangeResult.responseBody!!
                Assertions.assertTrue(projects.isNotEmpty())
                projects.forEach { Assertions.assertTrue(it.public) }
            }
    }

    @Test
    @WithUserDetails(value = "admin")
    fun `should return project based on name and owner`() {
        mutateMockedUser {
            details = AuthenticationDetails(id = 1)
        }

        getProjectAndAssert("huaweiName", "Huawei") {
            expectStatus()
                .isOk
                .expectBody<Project>()
                .consumeWith {
                    requireNotNull(it.responseBody)
                    Assertions.assertEquals(it.responseBody!!.url, "huawei.com")
                }
        }
    }

    @Test
    @WithMockUser(username = "Mr. Bruh", roles = ["VIEWER"])
    fun `should return 200 if project is public`() {
        mutateMockedUser {
            details = AuthenticationDetails(id = 99)
        }

        getProjectAndAssert("huaweiName", "Huawei") {
            expectStatus().isOk
        }
    }

    @Test
    @WithMockUser(username = "Mr. Bruh", roles = ["VIEWER"])
    fun `should return 404 if user doesn't have access to a private project`() {
        mutateMockedUser {
            details = AuthenticationDetails(id = 99)
        }

        getProjectAndAssert("The Project", "Example.com") {
            expectStatus().isNotFound
        }
    }

    @Test
    @WithUserDetails(value = "admin")
    fun `delete project with owner permission`() {
        mutateMockedUser {
            details = AuthenticationDetails(id = 2)
        }
        val organization: Organization = organizationRepository.getOrganizationById(1)
        val project = Project("ToDelete", "http://test.com", "", ProjectStatus.CREATED, organization = organization)

        projectRepository.save(project)

        webClient.delete()
            .uri("/api/$v1/projects/${organization.name}/${project.name}/delete")
            .exchange()
            .expectStatus()
            .isOk

        val projectFromDb = projectRepository.findByNameAndOrganization(project.name, organization)
        Assertions.assertTrue(
            projectFromDb?.status == ProjectStatus.DELETED
        )
    }

    @Test
    @WithUserDetails(value = "John Doe")
    fun `delete project without owner permission`() {
        mutateMockedUser {
            details = AuthenticationDetails(id = 2)
        }
        val organization: Organization = organizationRepository.getOrganizationById(1)
        val project = Project("ToDelete1", "http://test.com", "", ProjectStatus.CREATED, organization = organization)

        projectRepository.save(project)

        webClient.delete()
            .uri("/api/$v1/projects/${organization.name}/${project.name}/delete")
            .exchange()
            .expectStatus()
            .isForbidden

        val projectFromDb = projectRepository.findByNameAndOrganization(project.name, organization)
        Assertions.assertTrue(
            projectFromDb?.status == ProjectStatus.CREATED
        )
    }

    @Test
    @WithMockUser(username = "John Doe", roles = ["VIEWER"])
    fun `check save new project`() {
        mutateMockedUser {
            details = AuthenticationDetails(id = 2)
        }

        // `project` references an existing user from test data
        val organization: Organization = organizationRepository.getOrganizationById(1)
        val project = Project("I", "http://test.com", "uurl", ProjectStatus.CREATED, userId = 2, organization = organization)
        saveProjectAndAssert(
            project,
            { expectStatus().isOk }
        ) {
            expectStatus()
                .isOk
                .expectBody<Project>()
                .consumeWith {
                    requireNotNull(it.responseBody)
                    Assertions.assertEquals(it.responseBody!!.url, project.url)
                }
        }
    }

    @Test
    @WithMockUser
    fun `should forbid updating a project for a viewer`() {
        val project = Project.stub(99).apply {
            userId = 1
            organization = organizationRepository.findById(1).get()
        }
        projectRepository.save(project)
        mutateMockedUser {
            details = AuthenticationDetails(id = 3)
        }

        webClient.post()
            .uri("/api/$v1/projects/update")
            .bodyValue(project.toDto())
            .exchange()
            .expectStatus()
            .isForbidden
    }

    private fun getProjectAndAssert(
        name: String,
        organizationName: String,
        assertion: WebTestClient.ResponseSpec.() -> Unit
    ) = webClient
        .get()
        .uri("/api/$v1/projects/get/organization-name?name=$name&organizationName=$organizationName")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .let { assertion(it) }

    private fun saveProjectAndAssert(
        newProject: Project,
        saveAssertion: WebTestClient.ResponseSpec.() -> Unit,
        getAssertion: WebTestClient.ResponseSpec.() -> Unit,
    ) {
        webClient
            .post()
            .uri("/api/$v1/projects/save")
            .body(BodyInserters.fromValue(newProject.toDto()))
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .let { saveAssertion(it) }

        webClient
            .get()
            .uri("/api/$v1/projects/get/organization-name?name=${newProject.name}&organizationName=${newProject.organization.name}")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .let { getAssertion(it) }
    }
}
