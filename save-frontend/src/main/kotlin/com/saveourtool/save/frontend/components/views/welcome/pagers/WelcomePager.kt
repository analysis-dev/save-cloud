package com.saveourtool.save.frontend.components.views.welcome.pagers

import com.saveourtool.save.frontend.externals.animations.Animation
import react.ChildrenBuilder

val allWelcomePagers = listOf(
    listOf(HighLevelSave),
    listOf(SloganAboutCi),
    listOf(GeneralInfoFirstPicture, GeneralInfoSecondPicture, GeneralInfoThirdPicture, GeneralInfoFourthPicture),
    listOf(SloganAboutBenchmarks),
    listOf(AwesomeBenchmarks),
    listOf(SloganAboutTests),
    listOf(SloganAboutContests),
    listOf(BobPager)
)

/**
 * common interface for all pagers on welcome view
 */
interface WelcomePager {
    /**
     * animation for the pager
     */
    val animation: Animation

    /**
     * rendering function - place your html code here
     *
     * @param childrenBuilder
     */
    fun renderPage(childrenBuilder: ChildrenBuilder)
}
