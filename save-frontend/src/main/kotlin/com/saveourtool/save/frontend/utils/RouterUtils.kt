/**
 * Utilities to work with react-router
 */

@file:Suppress("FILE_NAME_MATCH_CLASS")

package com.saveourtool.save.frontend.utils

import history.Location
import react.*
import react.router.*

/**
 * Interface that provides [NavigateFunction] to use [navigation support](https://reactrouter.com/en/v6.3.0/api#navigation)
 * from react-router v6
 */
interface NavigateFunctionContext {
    /**
     * Function that performs navigation using react-router library
     */
    val navigate: NavigateFunction
}

/**
 * @param handler DOM builder that can consume [NavigateFunctionContext]
 */
fun ChildrenBuilder.withNavigate(handler: ChildrenBuilder.(NavigateFunctionContext) -> Unit) {
    val wrapper: VFC = VFC {
        val navigate = useNavigate()
        val ctx = object : NavigateFunctionContext {
            override val navigate: NavigateFunction = navigate
        }
        handler(ctx)
    }
    wrapper()
}

/**
 * Wrapper function component to allow using router props in class components
 * as suggested in https://reactrouter.com/docs/en/v6/faq#what-happened-to-withrouter-i-need-it
 *
 * @param handler router props aware builder
 * @return a function component
 */
@Suppress("TYPE_ALIAS")
fun <T : Props> withRouter(handler: ChildrenBuilder.(Location, Params) -> Unit) = FC<T> {
    val location = useLocation()
    val params = useParams()
    handler(location, params)
}
