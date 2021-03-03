/**
 * View for tests execution history
 */

package org.cqfn.save.frontend.components.views

import org.cqfn.save.entities.Project
import org.cqfn.save.frontend.components.tables.tableComponent
import react.RBuilder
import react.RComponent
import react.RProps
import react.RState
import react.child

/**
 * [RProps] for tests execution history
 */
external interface HistoryProps : RProps {
    /**
     * An active [Project]
     */
    var project: Project
}

/**
 * A table to display execution results for a certain project.
 */
class HistoryView : RComponent<HistoryProps, RState>() {
    override fun RBuilder.render() {
        child(tableComponent<Project>(
            data = arrayOf(

            ),
            columns = arrayOf(

            ),
        )) {
            attrs.tableHeader = "Execution details"
        }
    }
}
