/**
 * A view related to the sign-in view
 */

@file:Suppress("FILE_WILDCARD_IMPORTS", "WildcardImport")

package com.saveourtool.save.frontend.components.views

import com.saveourtool.save.frontend.AWESOME_BENCHMARKS
import com.saveourtool.save.frontend.CONTESTS
import com.saveourtool.save.frontend.PROJECTS
import com.saveourtool.save.frontend.SETTINGS_EMAIL
import com.saveourtool.save.frontend.components.RequestStatusContext
import com.saveourtool.save.frontend.components.requestStatusContext
import com.saveourtool.save.frontend.externals.animations.*
import com.saveourtool.save.frontend.externals.fontawesome.*
import com.saveourtool.save.frontend.utils.*
import com.saveourtool.save.frontend.utils.noopResponseHandler
import com.saveourtool.save.info.OauthProviderInfo
import com.saveourtool.save.info.UserInfo

import csstype.*
import org.w3c.dom.HTML.a
import org.w3c.fetch.Headers
import react.*
import react.dom.html.ReactHTML
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.form
import react.dom.html.ReactHTML.h1
import react.dom.html.ReactHTML.h3
import react.dom.html.ReactHTML.h4
import react.dom.html.ReactHTML.main
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.span

import kotlinx.browser.window
import kotlinx.coroutines.launch
import kotlinx.js.jso

/**
 * [State] of project creation view component
 */
external interface IndexViewState : State {
    /**
     * State that checks the validity of login
     */
    var isValidLogin: Boolean?

    /**
     * State that checks the validity of password
     */
    var isValidPassword: Boolean?

    /**
     * List of OAuth providers, that can be accepted by backend
     */
    var oauthProviders: List<OauthProviderInfo>?
}

/**
 * Properties used in WelcomeView (passed from App.kt)
 */
external interface WelcomeProps : PropsWithChildren {
    /**
     * Currently logged in user or null
     */
    var userInfo: UserInfo?
}

/**
 * Main entry point view with sign-in page
 */
@JsExport
@OptIn(ExperimentalJsExport::class)
class WelcomeView : AbstractView<WelcomeProps, IndexViewState>(true) {
    init {
        state.isValidLogin = true
        state.isValidPassword = true
    }

    override fun componentDidMount() {
        super.componentDidMount()
        scope.launch {
            val oauthProviderInfoList: List<OauthProviderInfo>? = get(
                "${window.location.origin}/sec/oauth-providers",
                Headers(),
                loadingHandler = ::classLoadingHandler,
                responseHandler = ::noopResponseHandler,
            ).run {
                if (ok) decodeFromJsonString() else null
            }
            oauthProviderInfoList?.let {
                setState {
                    oauthProviders = it
                }
            }
        }
    }

    @Suppress("ForbiddenComment", "LongMethod", "TOO_LONG_FUNCTION")
    override fun ChildrenBuilder.render() {
        main {
            className = ClassName("main-content mt-0 ps")
            div {
                className = ClassName("page-header align-items-start min-vh-100")
                style = jso {
                    background =
                            "-webkit-linear-gradient(270deg, rgb(0,20,73), rgb(13,71,161))".unsafeCast<Background>()
                }
                span {
                    className = ClassName("mask bg-gradient-dark opacity-6")
                }

                div {
                    Particles::class.react {
                        id = "tsparticles"
                        url = "${window.location.origin}/particles.json"
                    }

                    className = ClassName("row")
                    // Marketing information
                    div {
                        className = ClassName("col-lg-4 ml-auto mt-3 mb-5 mr-5 ml-0 text-white")
                        marketingTitle("Software")
                        marketingTitle("Analysis")
                        marketingTitle("Verification &")
                        marketingTitle("Evaluation")
                        h3 {
                            className = ClassName("mt-4")
                            +"Advanced cloud eco-system for continuous integration, evaluation and benchmarking of software tools."
                        }
                    }

                    // Sign-in header
                    div {
                        className = ClassName("col-lg-3 mr-auto ml-5 mt-5 mb-5")
                        div {
                            className = ClassName("card z-index-0 fadeIn3 fadeInBottom")
                            // if user is not logged in - he needs to input credentials
                            props.userInfo?.let {
                                welcomeUserView()
                            }
                                ?: run {
                                    inputCredentialsView()
                                }
                        }
                    }
                }
            }

            div {
                className = ClassName("min-vh-100")
                style = jso {
                    background =
                            "-webkit-linear-gradient(270deg, rgb(209, 229, 235),  rgb(217, 194, 229))".unsafeCast<Background>()
                }

                span {
                    className = ClassName("mask bg-gradient-dark opacity-6")
                }

                div {
                    className = ClassName("align-items-center justify-content-center")
                    scrollContainer {
                        scrollPage {
                            animator {
                                animation = fade
                                span {
                                    style = jso {
                                        fontSize = "40px".unsafeCast<FontSize>()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Suppress("TOO_LONG_FUNCTION")
    private fun ChildrenBuilder.inputCredentialsView() {
        div {
            className = ClassName("card-header p-0 position-relative mt-n4 mx-3 z-index-2 rounded")
            div {
                className = ClassName("shadow-primary border-radius-lg py-3 pe-1 rounded")
                style = jso {
                    backgroundColor = "#3075c0".unsafeCast<BackgroundColor>()
                }
                h4 {
                    className = ClassName("text-white font-weight-bolder text-center mt-2 mb-0")
                    +"Sign in"
                }
                div {
                    className = ClassName("row")
                    state.oauthProviders?.map {
                        oauthLogin(
                            it, when (it.registrationId) {
                                "github" -> faGithub
                                "codehub" -> faCopyright
                                else -> faSignInAlt
                            }
                        )
                    }
                }
            }
        }

        div {
            className = ClassName("card-body")
            form {
                className = ClassName("needs-validation")
                div {
                    className = ClassName("mt-4 text-sm text-center")
                    p {
                        className = ClassName("mb-0")
                        +"Don't have an account?"
                    }

                    // Fixme: validateDOMNesting(...): <h4> cannot appear as a descendant of <p>.
                    p {
                        className = ClassName("text-sm text-center")
                        h4 {
                            style = jso {
                                color = "#3075c0".unsafeCast<Color>()
                            }
                            a {
                                className = ClassName("text-gradient font-weight-bold ml-2 mr-2")
                                href = "#/$PROJECTS"
                                +"Continue "
                                fontAwesomeIcon(icon = faSignInAlt)
                            }
                        }
                        +"with limited functionality"
                    }
                }
            }
        }
    }

    @Suppress("TOO_LONG_FUNCTION")
    private fun ChildrenBuilder.welcomeUserView() {
        div {
            className = ClassName("card-header p-0 position-relative mt-n4 mx-3 z-index-2 rounded")
            div {
                className = ClassName("shadow-primary border-radius-lg py-3 pe-1 rounded")
                style = jso {
                    backgroundColor = "#3075c0".unsafeCast<BackgroundColor>()
                }
                h4 {
                    className = ClassName("text-white font-weight-bolder text-center mt-2 mb-0")
                    div {
                        className = ClassName("row")
                        div {
                            className = ClassName("col text-center px-1 mb-3")
                            fontAwesomeIcon(icon = faHome)
                        }
                    }
                    +"Welcome, ${props.userInfo?.name}!"
                }
            }
        }

        div {
            className = ClassName("card-body")
            div {
                className = ClassName("text-sm")

                menuTextAndLink("Contests", "/#/$CONTESTS", faBell)
                hrNoMargin()
                menuTextAndLink("List of Projects", "#/$PROJECTS", faExternalLinkAlt)
                hrNoMargin()
                menuTextAndLink("Benchmarks Archive", "/#/$AWESOME_BENCHMARKS", faFolderOpen)
                hrNoMargin()
                menuTextAndLink("User Settings", "/#/${props.userInfo?.name}/$SETTINGS_EMAIL", faUser)
            }
        }
    }

    private fun ChildrenBuilder.marketingTitle(str: String) {
        div {
            className = ClassName("mb-0 mt-0")
            h1Bold(str[0].toString())
            h1Normal(str.substring(1, str.length))
        }
    }

    private fun ChildrenBuilder.h1Bold(str: String) = h1 {
        +str
        style = jso {
            fontWeight = "bold".unsafeCast<FontWeight>()
            display = Display.inline
            fontSize = "4.5rem".unsafeCast<FontSize>()
        }
    }

    private fun ChildrenBuilder.h1Normal(str: String) = h1 {
        +str
        style = jso {
            display = Display.inline
        }
    }

    private fun ChildrenBuilder.oauthLogin(provider: OauthProviderInfo, icon: dynamic) {
        div {
            className = ClassName("col text-center px-1")
            a {
                href = provider.authorizationLink
                className = ClassName("btn btn-link px-3 text-white text-lg text-center")
                style = jso {
                    fontSize = "3.2rem".unsafeCast<FontSize>()
                }
                fontAwesomeIcon(icon = icon)
            }
        }
    }

    private fun ChildrenBuilder.menuTextAndLink(text: String, link: String, icon: FontAwesomeIconModule) =
            a {
                className = ClassName("text-gradient font-weight-bold ml-2 mr-2")
                href = link
                h4 {
                    style = jso {
                        color = "#3075c0".unsafeCast<Color>()
                        marginBottom = "0.0em".unsafeCast<Margin>()
                    }
                    fontAwesomeIcon(icon = icon, "ml-2 mr-2")
                    +text
                }
            }

    private fun ChildrenBuilder.hrNoMargin() =
            ReactHTML.hr {
                style = jso {
                    marginTop = "0.0em".unsafeCast<Margin>()
                    marginBottom = "0.0em".unsafeCast<Margin>()
                }
            }

    companion object :
        RStatics<WelcomeProps, IndexViewState, WelcomeView, Context<RequestStatusContext>>(WelcomeView::class) {
        init {
            contextType = requestStatusContext
        }
    }
}
