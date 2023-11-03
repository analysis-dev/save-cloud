/**
 * A view with organization details
 */

package com.saveourtool.save.frontend.components.views

import com.saveourtool.save.domain.Role
import com.saveourtool.save.entities.*
import com.saveourtool.save.filters.ProjectFilter
import com.saveourtool.save.frontend.components.RequestStatusContext
import com.saveourtool.save.frontend.components.basic.*
import com.saveourtool.save.frontend.components.basic.organizations.*
import com.saveourtool.save.frontend.components.modal.displayModal
import com.saveourtool.save.frontend.components.modal.smallTransparentModalStyle
import com.saveourtool.save.frontend.components.requestStatusContext
import com.saveourtool.save.frontend.externals.fontawesome.*
import com.saveourtool.save.frontend.http.getOrganization
import com.saveourtool.save.frontend.http.postImageUpload
import com.saveourtool.save.frontend.utils.*
import com.saveourtool.save.info.UserInfo
import com.saveourtool.save.utils.AvatarType
import com.saveourtool.save.utils.getHighestRole

import js.core.jso
import org.w3c.fetch.Headers
import react.*
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h1
import react.dom.html.ReactHTML.h4
import react.dom.html.ReactHTML.h6
import react.dom.html.ReactHTML.img
import react.dom.html.ReactHTML.label
import react.dom.html.ReactHTML.li
import react.dom.html.ReactHTML.nav
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.textarea
import remix.run.router.Location
import web.cssom.*
import web.html.ButtonType

import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * The mandatory column id.
 * For each cell, this will be transformed into "cell_%d_delete_button" and
 * visible as the key in the "Components" tab of the developer tools.
 */
const val DELETE_BUTTON_COLUMN_ID = "delete_button"

/**
 * Empty table header.
 */
const val EMPTY_COLUMN_HEADER = ""

/**
 * CSS classes of the "delete project" button.
 */
val actionButtonClasses: List<String> = listOf("btn", "btn-small")

/**
 * CSS classes of the "delete project" icon.
 */
val actionIconClasses: List<String> = listOf("trash-alt")

/**
 * `Props` retrieved from router
 */
@Suppress("MISSING_KDOC_CLASS_ELEMENTS")
external interface OrganizationProps : PropsWithChildren {
    var organizationName: String
    var currentUserInfo: UserInfo?
    var location: Location<*>
}

/**
 * [State] of project view component
 */
external interface OrganizationViewState : StateWithRole, State {
    /**
     * Organization
     */
    var organization: OrganizationDto?

    /**
     * List of projects for `this` organization
     */
    var projects: List<ProjectDto>

    /**
     * Message of error
     */
    var errorMessage: String

    /**
     * Flag to handle error
     */
    var isErrorOpen: Boolean

    /**
     * Error label
     */
    var errorLabel: String

    /**
     * State for the creation of unified confirmation logic
     */
    var confirmationType: ConfirmationType

    /**
     * Flag to handle confirm Window
     */
    var isConfirmWindowOpen: Boolean

    /**
     * Whether editing of organization info is disabled
     */
    var isEditDisabled: Boolean

    /**
     * Users in organization
     */
    var usersInOrganization: List<UserInfo>?

    /**
     * Label that will be shown on Close button of modal windows
     */
    var closeButtonLabel: String?

    /**
     * Current state of description input form
     */
    var draftOrganizationDescription: String

    /**
     * Currently selected [OrganizationMenuBar] tab
     */
    var selectedMenu: OrganizationMenuBar

    /**
     * Flag to handle avatar Window
     */
    var isAvatarWindowOpen: Boolean

    /**
     * Organization avatar
     */
    var avatar: String
}

/**
 * A Component for owner view
 */
class OrganizationView : AbstractView<OrganizationProps, OrganizationViewState>(Style.SAVE_LIGHT) {
    init {
        state.organization = OrganizationDto.empty
        state.selectedMenu = OrganizationMenuBar.defaultTab
        state.projects = mutableListOf()
        state.closeButtonLabel = null
        state.selfRole = Role.NONE
        state.draftOrganizationDescription = ""
        state.isConfirmWindowOpen = false
        state.isErrorOpen = false
        state.confirmationType = ConfirmationType.DELETE_CONFIRM
        state.isAvatarWindowOpen = false
    }

    private fun showNotification(notificationLabel: String, notificationMessage: String) {
        setState {
            isErrorOpen = true
            errorLabel = notificationLabel
            errorMessage = notificationMessage
            closeButtonLabel = "Confirm"
        }
    }

    override fun componentDidMount() {
        super.componentDidMount()
        val comparator: Comparator<ProjectDto> =
                compareBy<ProjectDto> { it.status.ordinal }
                    .thenBy { it.name }

        scope.launch {
            val organizationLoaded = getOrganization(props.organizationName)
            val projectsLoaded = getProjectsForOrganizationAndStatus(enumValues<ProjectStatus>().toSet()).sortedWith(comparator)
            val role = getRoleInOrganization()
            val users = getUsers()
            val highestRole = getHighestRole(role, props.currentUserInfo?.globalRole)
            setState {
                organization = organizationLoaded
                draftOrganizationDescription = organizationLoaded.description
                projects = projectsLoaded
                isEditDisabled = true
                selfRole = highestRole
                usersInOrganization = users
                avatar = organizationLoaded.avatar?.avatarRenderer() ?: AVATAR_ORGANIZATION_PLACEHOLDER
            }
        }
    }

    override fun ChildrenBuilder.render() {
        val errorCloseCallback = {
            setState {
                isErrorOpen = false
                closeButtonLabel = null
            }
        }
        displayModal(state.isErrorOpen, state.errorLabel, state.errorMessage, smallTransparentModalStyle, errorCloseCallback) {
            buttonBuilder(state.closeButtonLabel ?: "Close", "secondary") { errorCloseCallback() }
        }

        renderOrganizationMenuBar()

        when (state.selectedMenu) {
            OrganizationMenuBar.INFO -> renderInfo()
            OrganizationMenuBar.TOOLS -> renderTools()
            OrganizationMenuBar.BENCHMARKS -> renderBenchmarks()
            OrganizationMenuBar.SETTINGS -> renderSettings()
            OrganizationMenuBar.CONTESTS -> renderContests()
            OrganizationMenuBar.VULNERABILITIES -> renderVulnerabilities()
        }
    }

    @Suppress(
        "TOO_LONG_FUNCTION",
        "LongMethod",
        "ComplexMethod",
        "PARAMETER_NAME_IN_OUTER_LAMBDA",
    )
    private fun ChildrenBuilder.renderInfo() {
        // ================= Rows for TOP projects ================
        val topProjects = state.projects.sortedByDescending { it.contestRating }.take(TOP_PROJECTS_NUMBER)

        if (topProjects.isNotEmpty()) {
            // ================= Title for TOP projects ===============
            div {
                className = ClassName("row justify-content-center mb-2")
                h4 {
                    +"Top Tools"
                }
            }
            div {
                className = ClassName("row justify-content-center")

                renderTopProject(topProjects.getOrNull(0))
                renderTopProject(topProjects.getOrNull(1))
            }

            @Suppress("MAGIC_NUMBER")
            div {
                className = ClassName("row justify-content-center")

                renderTopProject(topProjects.getOrNull(2))
                renderTopProject(topProjects.getOrNull(3))
            }
        }
        div {
            className = ClassName("row justify-content-center")

            div {
                className = ClassName("col-4 mb-4")
                div {
                    className = ClassName("card shadow mb-4")
                    div {
                        className = ClassName("card-header py-3")
                        div {
                            className = ClassName("row")
                            h6 {
                                className = ClassName("m-0 font-weight-bold text-primary")
                                style = jso {
                                    display = Display.flex
                                    alignItems = AlignItems.center
                                }
                                +"Description"
                            }
                            if (state.selfRole.hasWritePermission() && state.isEditDisabled) {
                                button {
                                    type = ButtonType.button
                                    className = ClassName("btn btn-link text-xs text-muted text-left ml-auto")
                                    +"Edit  "
                                    fontAwesomeIcon(icon = faEdit)
                                    onClick = {
                                        turnEditMode(isOff = false)
                                    }
                                }
                            }
                        }
                    }
                    div {
                        className = ClassName("card-body")
                        textarea {
                            className = ClassName("auto_height form-control-plaintext pt-0 pb-0")
                            value = state.draftOrganizationDescription
                            disabled = !state.selfRole.hasWritePermission() || state.isEditDisabled
                            onChange = {
                                setNewDescription(it.target.value)
                            }
                        }
                    }
                    div {
                        className = ClassName("ml-3 mt-2 align-items-right float-right")
                        button {
                            type = ButtonType.button
                            className = ClassName("btn")
                            fontAwesomeIcon(icon = faCheck)
                            hidden = !state.selfRole.hasWritePermission() || state.isEditDisabled
                            onClick = {
                                state.organization?.let { onOrganizationSave(it) }
                                turnEditMode(true)
                            }
                        }

                        button {
                            type = ButtonType.button
                            className = ClassName("btn")
                            fontAwesomeIcon(icon = faTimesCircle)
                            hidden = !state.selfRole.hasWritePermission() || state.isEditDisabled
                            onClick = {
                                turnEditMode(true)
                            }
                        }
                    }
                }
            }

            div {
                className = ClassName("col-2")
                userBoard {
                    users = state.usersInOrganization.orEmpty()
                    avatarOuterClasses = "col-4 px-0"
                    avatarInnerClasses = "mx-sm-3"
                    widthAndHeight = 6.rem
                }
            }
        }
    }

    @Suppress("TOO_LONG_FUNCTION", "LongMethod")
    private fun ChildrenBuilder.renderTools() {
        organizationToolsMenu {
            currentUserInfo = props.currentUserInfo
            selfRole = state.selfRole
            organization = state.organization
            projects = state.projects
            updateProjects = { projectsList ->
                setState { projects = projectsList }
            }
        }
    }

    private fun setNewDescription(value: String) {
        setState {
            draftOrganizationDescription = value
        }
    }

    private fun onOrganizationSave(newOrganization: OrganizationDto) {
        newOrganization.copy(
            description = state.draftOrganizationDescription
        ).let { organizationWithNewDescription ->
            scope.launch {
                val response = post(
                    "$apiUrl/organizations/${props.organizationName}/update",
                    jsonHeaders,
                    Json.encodeToString(organizationWithNewDescription),
                    loadingHandler = ::noopLoadingHandler,
                )
                if (response.ok) {
                    setState {
                        organization = organizationWithNewDescription
                    }
                }
            }
        }
    }

    private fun ChildrenBuilder.renderBenchmarks() {
        organizationTestsMenu {
            organizationName = props.organizationName
            selfRole = state.selfRole
        }
    }

    private fun ChildrenBuilder.renderContests() {
        organizationContestsMenu {
            organizationName = props.organizationName
            selfRole = state.selfRole
            updateErrorMessage = {
                setState {
                    isErrorOpen = true
                    errorLabel = ""
                    errorMessage = "Failed to create contest: ${it.status} ${it.statusText}"
                }
            }
        }
    }

    private fun ChildrenBuilder.renderVulnerabilities() {
        organizationVulnerabilitiesTab {
            organizationName = props.organizationName
            isMember = state.usersInOrganization?.let { usersInOrg ->
                props.currentUserInfo in usersInOrg || props.currentUserInfo.isSuperAdmin()
            } ?: false
        }
    }

    private fun ChildrenBuilder.renderSettings() {
        organizationSettingsMenu {
            organizationName = props.organizationName
            currentUserInfo = props.currentUserInfo ?: UserInfo(name = "Undefined")
            selfRole = state.selfRole
            updateErrorMessage = { response, message ->
                setState {
                    isErrorOpen = true
                    errorLabel = response.statusText
                    errorMessage = message
                }
            }
            updateNotificationMessage = ::showNotification
            organization = state.organization ?: OrganizationDto.empty
            onCanCreateContestsChange = ::onCanCreateContestsChange
            onCanBulkUploadCosvFilesChange = ::onCanBulkUploadCosvFilesChange
        }
    }

    private fun turnEditMode(isOff: Boolean) {
        setState {
            isEditDisabled = isOff
        }
    }

    private suspend fun getProjectsForOrganizationAndStatus(statuses: Set<ProjectStatus>): List<ProjectDto> = post(
        url = "$apiUrl/projects/by-filters",
        headers = jsonHeaders,
        body = Json.encodeToString(ProjectFilter("", props.organizationName, statuses)),
        loadingHandler = ::classLoadingHandler,
    )
        .unsafeMap {
            it.decodeFromJsonString()
        }

    private fun onCanCreateContestsChange(canCreateContests: Boolean) {
        scope.launch {
            val response = post(
                "$apiUrl/organizations/${props.organizationName}/manage-contest-permission?isAbleToCreateContests=${!state.organization!!.canCreateContests}",
                headers = jsonHeaders,
                undefined,
                loadingHandler = ::classLoadingHandler,
            )
            if (response.ok) {
                setState {
                    organization = organization?.copy(canCreateContests = canCreateContests)
                }
            }
        }
    }

    private fun onCanBulkUploadCosvFilesChange(canBulkUpload: Boolean) {
        scope.launch {
            val response = post(
                "$apiUrl/organizations/${props.organizationName}/manage-bulk-upload-permission",
                params = jso<dynamic> {
                    isAbleToToBulkUpload = !state.organization!!.canBulkUpload
                },
                headers = jsonHeaders,
                undefined,
                loadingHandler = ::classLoadingHandler,
            )
            if (response.ok) {
                setState {
                    organization = organization?.copy(canBulkUpload = canBulkUpload)
                }
            }
        }
    }

    private suspend fun getRoleInOrganization(): Role = get(
        url = "$apiUrl/organizations/${props.organizationName}/users/roles",
        headers = Headers().also {
            it.set("Accept", "application/json")
        },
        loadingHandler = ::classLoadingHandler,
        responseHandler = ::noopResponseHandler,
    )
        .unsafeMap {
            it.decodeFromJsonString()
        }

    private suspend fun getUsers(): List<UserInfo> = get(
        url = "$apiUrl/organizations/${props.organizationName}/users",
        headers = jsonHeaders,
        loadingHandler = ::classLoadingHandler,
    )
        .unsafeMap { it.decodeFromJsonString() }

    private fun ChildrenBuilder.renderTopProject(topProject: ProjectDto?) {
        div {
            className = ClassName("col-3 mb-4")
            topProject?.let {
                scoreCard {
                    name = it.name
                    contestScore = it.contestRating
                    url = "/${props.organizationName}/${it.name}"
                }
            }
        }
    }

    @Suppress("LongMethod", "TOO_LONG_FUNCTION", "MAGIC_NUMBER")
    private fun ChildrenBuilder.renderOrganizationMenuBar() {
        avatarForm {
            isOpen = state.isAvatarWindowOpen
            title = AVATAR_TITLE
            onCloseWindow = {
                setState {
                    isAvatarWindowOpen = false
                }
            }
            imageUpload = { file ->
                scope.launch {
                    postImageUpload(file, props.organizationName, AvatarType.ORGANIZATION, ::noopLoadingHandler)
                }
            }
        }

        div {
            className = ClassName("row d-flex")
            div {
                className = ClassName("col-3 ml-auto justify-content-center")
                style = jso {
                    display = Display.flex
                    alignItems = AlignItems.center
                }
                label {
                    className = ClassName("btn")
                    title = AVATAR_TITLE
                    onClick = {
                        setState {
                            isAvatarWindowOpen = true
                        }
                    }
                    img {
                        className = ClassName("avatar avatar-user width-full border color-bg-default rounded-circle")
                        src = state.avatar
                        style = jso {
                            height = "10rem".unsafeCast<Height>()
                            width = "10rem".unsafeCast<Width>()
                        }
                        onError = {
                            setState {
                                avatar = AVATAR_ORGANIZATION_PLACEHOLDER
                            }
                        }
                    }
                }

                h1 {
                    className = ClassName("h3 mb-0 text-gray-800 ml-2")
                    +(state.organization?.name ?: "N/A")
                }
            }

            div {
                className = ClassName("col-auto mx-0 justify-content-center")
                style = jso {
                    display = Display.flex
                    alignItems = AlignItems.center
                }

                nav {
                    className = ClassName("nav nav-tabs")
                    OrganizationMenuBar.values()
                        .filter {
                            it != OrganizationMenuBar.SETTINGS || state.selfRole.isHigherOrEqualThan(Role.ADMIN)
                        }
                        .filter {
                            it != OrganizationMenuBar.CONTESTS || state.selfRole.isHigherOrEqualThan(Role.OWNER) && state.organization?.canCreateContests == true
                        }
                        .forEach { organizationMenu ->
                            li {
                                className = ClassName("nav-item")
                                style = jso {
                                    cursor = "pointer".unsafeCast<Cursor>()
                                }
                                val classVal = if (state.selectedMenu == organizationMenu) " active font-weight-bold" else ""
                                p {
                                    className = ClassName("nav-link $classVal text-gray-800")
                                    onClick = {
                                        if (state.selectedMenu != organizationMenu) {
                                            setState { selectedMenu = organizationMenu }
                                        }
                                    }
                                    +organizationMenu.getTitle()
                                }
                            }
                        }
                }
            }

            div {
                className = ClassName("col-3 mr-auto justify-content-center align-items-center")
            }
        }
    }

    companion object :
        RStatics<OrganizationProps, OrganizationViewState, OrganizationView, Context<RequestStatusContext?>>(
        OrganizationView::class
    ) {
        private const val AVATAR_TITLE = "Change organization's avatar"
        const val TOP_PROJECTS_NUMBER = 4
        init {
            contextType = requestStatusContext
        }
    }
}
