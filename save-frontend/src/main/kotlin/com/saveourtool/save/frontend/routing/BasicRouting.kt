/**
 * All routs for the mobile version of the frontend
 */

@file:Suppress("FILE_NAME_MATCH_CLASS", "EXTENSION_FUNCTION_WITH_CLASS")

package com.saveourtool.save.frontend.routing

import com.saveourtool.save.domain.ProjectCoordinates
import com.saveourtool.save.domain.TestResultStatus
import com.saveourtool.save.filters.TestExecutionFilter
import com.saveourtool.save.frontend.components.basic.projects.createProjectProblem
import com.saveourtool.save.frontend.components.basic.projects.projectProblem
import com.saveourtool.save.frontend.components.views.*
import com.saveourtool.save.frontend.components.views.agreements.cookieTermsOfUse
import com.saveourtool.save.frontend.components.views.agreements.termsOfUsageView
import com.saveourtool.save.frontend.components.views.contests.*
import com.saveourtool.save.frontend.components.views.demo.cpgView
import com.saveourtool.save.frontend.components.views.demo.demoCollectionView
import com.saveourtool.save.frontend.components.views.demo.demoView
import com.saveourtool.save.frontend.components.views.index.indexView
import com.saveourtool.save.frontend.components.views.projectcollection.CollectionView
import com.saveourtool.save.frontend.components.views.toprating.topRatingView
import com.saveourtool.save.frontend.components.views.userprofile.userProfileView
import com.saveourtool.save.frontend.components.views.usersettings.*
import com.saveourtool.save.frontend.components.views.vuln.*
import com.saveourtool.save.frontend.components.views.welcome.saveWelcomeView
import com.saveourtool.save.frontend.components.views.welcome.vulnWelcomeView
import com.saveourtool.save.frontend.utils.*
import com.saveourtool.save.frontend.utils.isSuperAdmin
import com.saveourtool.save.info.UserInfo
import com.saveourtool.save.validation.FrontendRoutes.*
import js.core.jso

import org.w3c.dom.url.URLSearchParams
import react.*
import react.router.*
import react.router.dom.createBrowserRouter

private val fallbackElementWithoutRouterLink = FallbackView::class.react.create {
    bigText = "404"
    smallText = "Page not found"
    withRouterLink = false
}

private val fallbackElementWithRouterLink = FallbackView::class.react.create {
    bigText = "404"
    smallText = "Page not found"
    withRouterLink = true
}

/**
 * Just put a map: View -> Route URL to this list
 *
 * @param userInfo currently logged-in user or null
 * @param userInfoSetter setter of user info (it can be updated in settings on several views)
 * @return array of [RouteObject]
 */
fun createBasicRoutes(
    userInfo: UserInfo?,
    userInfoSetter: StateSetter<UserInfo?>,
): Array<RouteObject> {
    val indexRoute: RouteObject = jso {
        index = true
        element = indexView.create { this.userInfo = userInfo }
    }
    val userProfileView = withRouter { _, params ->
        userProfileView {
            userName = params["name"]!!
            currentUserInfo = userInfo
        }
    }

    val contestView = withRouter { location, params ->
        ContestView::class.react {
            currentUserInfo = userInfo
            currentContestName = params["contestName"]
            this.location = location
        }
    }

    val contestExecutionView = withRouter { _, params ->
        ContestExecutionView::class.react {
            currentUserInfo = userInfo
            contestName = params["contestName"]!!
            organizationName = params["organizationName"]!!
            projectName = params["projectName"]!!
        }
    }

    val projectView = withRouter { location, params ->
        ProjectView::class.react {
            name = params["name"]!!
            owner = params["owner"]!!
            currentUserInfo = userInfo
            this.location = location
        }
    }

    val historyView = withRouter { _, params ->
        HistoryView::class.react {
            name = params["name"]!!
            organizationName = params["owner"]!!
        }
    }

    val executionView = withRouter { location, params ->
        ExecutionView::class.react {
            organization = params["organization"]!!
            project = params["project"]!!
            executionId = params["executionId"]!!
            filters = web.url.URLSearchParams(location.search).let { params ->
                TestExecutionFilter(
                    status = params["status"]?.let { TestResultStatus.valueOf(it) },
                    fileName = params["fileName"],
                    testSuite = params["testSuite"],
                    tag = params["tag"]
                )
            }
            testAnalysisEnabled = true
        }
    }

    val organizationView = withRouter { location, params ->
        OrganizationView::class.react {
            organizationName = params["owner"]!!
            currentUserInfo = userInfo
            this.location = location
        }
    }

    val awesomeBenchmarksView = withRouter { location, _ ->
        AwesomeBenchmarksView::class.react {
            this.location = location
        }
    }

    val contestGlobalRatingView = withRouter { location, _ ->
        ContestGlobalRatingView::class.react {
            organizationName = URLSearchParams(location.search).get("organizationName")
            projectName = URLSearchParams(location.search).get("projectName")
            this.location = location
        }
    }

    val contestTemplateView = withRouter { _, params ->
        contestTemplateView {
            id = requireNotNull(params["id"]).toLong()
            currentUserInfo = userInfo
        }
    }

    val demoView = withRouter { _, params ->
        demoView {
            projectCoordinates = ProjectCoordinates(
                requireNotNull(params["organizationName"]),
                requireNotNull(params["projectName"]),
            )
        }
    }

    val vulnerabilityCollectionView = withRouter { location, _ ->
        vulnerabilityCollectionView {
            currentUserInfo = userInfo
            filter = URLSearchParams(location.search).toVulnerabilitiesFilter()
        }
    }

    val vulnerabilityView = withRouter { _, params ->
        vulnerabilityView {
            identifier = requireNotNull(params["identifier"])
            currentUserInfo = userInfo
        }
    }

    val createProjectProblemView = withRouter { _, params ->
        createProjectProblem {
            organizationName = requireNotNull(params["owner"])
            projectName = requireNotNull(params["name"])
        }
    }

    val projectProblemView = withRouter { _, params ->
        projectProblem {
            organizationName = requireNotNull(params["owner"])
            projectName = requireNotNull(params["name"])
            projectProblemId = requireNotNull(params["id"]).toLong()
        }
    }

    val routeToUserProfileView: RouteObject? = userInfo?.name?.let { userName ->
        jso {
            path = "/$userName"
            element = Navigate.create {
                to = "/$this/$SETTINGS_PROFILE"
            }
        }
    }

    val routeToManageOrganizationView: RouteObject = jso {
        path = "/$MANAGE_ORGANIZATIONS"
        element = when (userInfo.isSuperAdmin()) {
            true -> OrganizationAdminView::class.react.create()
            else -> fallbackElementWithoutRouterLink
        }
    }

    val routeToFallbackView: RouteObject = jso {
        path = "*"
        element = fallbackElementWithRouterLink
    }


    return listOf(
        saveWelcomeView.create { this.userInfo = userInfo } to SAVE,
        vulnWelcomeView.create { this.userInfo = userInfo } to VULN,
        sandboxView.create() to SANDBOX,
        AboutUsView::class.react.create() to ABOUT_US,
        createOrganizationView.create() to CREATE_ORGANIZATION,
        registrationView.create {
            this.userInfo = userInfo
            this.userInfoSetter = userInfoSetter
        } to REGISTRATION,
        CollectionView::class.react.create { currentUserInfo = userInfo } to PROJECTS,
        contestListView.create { currentUserInfo = userInfo } to CONTESTS,
        fallbackElementWithRouterLink to ERROR_404,
        banView.create { this.userInfo = userInfo } to BAN,
        contestGlobalRatingView.create() to CONTESTS_GLOBAL_RATING,
        contestView.create() to "$CONTESTS/:contestName",
        createContestTemplateView.create() to CREATE_CONTESTS_TEMPLATE,
        contestTemplateView.create() to "$CONTESTS_TEMPLATE/:id",
        contestExecutionView.create() to "$CONTESTS/:contestName/:organizationName/:projectName",
        awesomeBenchmarksView.create() to AWESOME_BENCHMARKS,
        createProjectView.create() to "$CREATE_PROJECT/:organization?",
        organizationView.create() to ":owner",
        historyView.create() to ":owner/:name/history",
        projectView.create() to ":owner/:name",
        createProjectProblemView.create() to "project/:owner/:name/security/problems/new",
        projectProblemView.create() to "project/:owner/:name/security/problems/:id",
        executionView.create() to ":organization/:project/history/execution/:executionId",
        demoView.create() to "$DEMO/:organizationName/:projectName",
        cpgView.create() to "$DEMO/cpg",
        testExecutionDetailsView.create() to "/:organization/:project/history/execution/:executionId/test/:testId",
        vulnerabilityCollectionView.create() to "$VULN/list/:params?",
        createVulnerabilityView.create() to VULN_CREATE,
        uploadVulnerabilityView.create() to VULN_UPLOAD,
        vulnerabilityView.create() to "$VULNERABILITY_SINGLE/:identifier",
        demoCollectionView.create() to DEMO,
        userProfileView.create() to "$VULN_PROFILE/:name",
        topRatingView.create() to VULN_TOP_RATING,
        termsOfUsageView.create() to TERMS_OF_USE,
        cookieTermsOfUse.create() to COOKIE,
        thanksForRegistrationView.create() to THANKS_FOR_REGISTRATION,
        cosvSchemaView.create() to VULN_COSV_SCHEMA,

        userSettingsView.create {
            this.userInfo = userInfo
            this.userInfoSetter = userInfoSetter
            type = SETTINGS_PROFILE
        } to SETTINGS_PROFILE,

        userSettingsView.create {
            this.userInfo = userInfo
            this.userInfoSetter = userInfoSetter
            type = SETTINGS_EMAIL
        } to SETTINGS_EMAIL,

        userSettingsView.create {
            this.userInfo = userInfo
            type = SETTINGS_TOKEN
        } to SETTINGS_TOKEN,

        userSettingsView.create {
            this.userInfo = userInfo
            type = SETTINGS_ORGANIZATIONS
        } to SETTINGS_ORGANIZATIONS,

        userSettingsView.create {
            this.userInfo = userInfo
            type = SETTINGS_DELETE
        } to SETTINGS_DELETE,

    )
        .map { (view, route) ->
            jso<RouteObject> {
                path = "/$route"
                element = view
            }
        }
        .let { routes ->
            routeToUserProfileView?.let { routes + it } ?: routes
        }
        .plus(routeToManageOrganizationView)
        .plus(routeToFallbackView)
        .plus(indexRoute)
        .toTypedArray()
}
