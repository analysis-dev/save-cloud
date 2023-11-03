/**
 * Utility methods for beautiful titles/slogans on welcome view
 */

package com.saveourtool.save.frontend.components.views.welcome

import com.saveourtool.save.frontend.externals.fontawesome.faChevronDown
import com.saveourtool.save.frontend.externals.fontawesome.fontAwesomeIcon

import js.core.jso
import react.ChildrenBuilder
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h1
import react.dom.html.ReactHTML.h3
import web.cssom.*

/**
 * @param textColor
 * @param isDark
 */
fun ChildrenBuilder.saveWelcomeMarketingTitle(textColor: String, isDark: Boolean = false) {
    div {
        className = ClassName("col-4 text-left mt-5 $textColor")
        marketingTitle("Software", isDark)
        marketingTitle("Analysis", isDark)
        marketingTitle("Verification &", isDark)
        marketingTitle("Evaluation", isDark)
        h3 {
            if (isDark) {
                style = jso {
                    color = "rgb(6, 7, 89)".unsafeCast<Color>()
                }
            }
            className = ClassName("mt-4")
            +"Advanced open-source cloud eco-system for continuous integration, evaluation and benchmarking of software tools."
        }
    }
}

/**
 * @param textColor
 * @param isDark
 */
fun ChildrenBuilder.vulnWelcomeMarketingTitle(textColor: String, isDark: Boolean = false) {
    div {
        className = ClassName("col-4 text-left mt-5 mx-5 $textColor")
        marketingTitle("Vulnerability", isDark)
        marketingTitle("Database", isDark)
        marketingTitle(" and", isDark)
        marketingTitle("Benchmark", isDark)
        marketingTitle("Archive", isDark)
        h3 {
            if (isDark) {
                style = jso {
                    color = "rgb(6, 7, 89)".unsafeCast<Color>()
                }
            }
            className = ClassName("mt-4")
            +"A huge storage of known vulnerabilities."
        }
    }
}

/**
 * @param str
 * @param isDark
 */
fun ChildrenBuilder.marketingTitle(str: String, isDark: Boolean) {
    div {
        if (isDark) {
            style = jso {
                color = "rgb(6, 7, 89)".unsafeCast<Color>()
            }
        }
        className = ClassName("mb-0 mt-0")
        h1Bold(str[0].toString())
        h1Normal(str.substring(1, str.length))
    }
}

/**
 * @param col
 */
@Suppress("MAGIC_NUMBER")
fun ChildrenBuilder.chevron(col: String) {
    div {
        className = ClassName("mt-5 row justify-content-center")
        h1 {
            className = ClassName("mt-5 animate__animated animate__pulse animate__infinite")
            style = jso {
                fontSize = 5.rem
                color = col.unsafeCast<Color>()
            }
            fontAwesomeIcon(faChevronDown)
        }
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
