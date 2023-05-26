@file:Suppress("FILE_NAME_MATCH_CLASS", "FILE_WILDCARD_IMPORTS", "LargeClass")

package com.saveourtool.save.frontend.components.basic.projects

import com.saveourtool.save.domain.ProjectCoordinates
import com.saveourtool.save.domain.Role
import com.saveourtool.save.entities.ProjectDto
import com.saveourtool.save.frontend.components.basic.fileuploader.fileManagerComponent
import com.saveourtool.save.info.UserInfo

import org.w3c.fetch.Response
import react.*
import react.dom.html.ReactHTML.div
import web.cssom.ClassName

/**
 * FILES tab in ProjectView
 */
val projectFilesMenu: FC<ProjectFilesMenuProps> = FC { props ->
    div {
        className = ClassName("row justify-content-center mb-2")
        // ===================== LEFT COLUMN =======================================================================
        div {
            className = ClassName("col-4 mb-2 pl-0 pr-0 mr-2 ml-2")
            div {
                className = ClassName("text-xs text-center font-weight-bold text-primary text-uppercase mb-3")
                +"File manager"
            }
            fileManagerComponent {
                projectCoordinates = ProjectCoordinates(props.project.organizationName, props.project.name)
            }
        }
    }
}

/**
 * ProjectFilesMenu component props
 */
external interface ProjectFilesMenuProps : Props {
    /**
     * Current project settings
     */
    var project: ProjectDto

    /**
     * Information about current user
     */
    var currentUserInfo: UserInfo

    /**
     * Role of a current user
     */
    var selfRole: Role

    /**
     * Callback to show error message
     */
    @Suppress("TYPE_ALIAS")
    var updateErrorMessage: (Response, String) -> Unit
}
