/**
 * In this file we describe tricky class that is used to store input information
 */

package com.saveourtool.save.frontend.components.views.usersettings.right

import com.saveourtool.save.frontend.components.inputform.InputTypes
import com.saveourtool.save.info.UserInfo

/**
 * This Data class is used to STORE all input from input view.
 * It is updated each time user touches input fields.
 *
 * And it is updated very carefully:
 * 1) Only one field is updated, other fields are saved and used from previous selection
 * 2) Each time we create immutable object with copy to prevent any potential problems
 * 3) If the value was not changed or touched - we just have null in the corresponding field
 * 4) In that case that null value does not break anything - we just do not update the value (user did not change it)
 * and work with an old one
 * @property userName
 * @property userEmail
 * @property realName
 * @property company
 * @property location
 * @property website
 * @property linkedIn
 * @property github
 * @property twitter
 * @property freeText
 */
data class SettingsInputFields(
    val userName: SettingsFromInput = SettingsFromInput(),
    val userEmail: SettingsFromInput = SettingsFromInput(),
    val realName: SettingsFromInput = SettingsFromInput(),
    val company: SettingsFromInput = SettingsFromInput(),
    val location: SettingsFromInput = SettingsFromInput(),
    val website: SettingsFromInput = SettingsFromInput(),
    val linkedIn: SettingsFromInput = SettingsFromInput(),
    val github: SettingsFromInput = SettingsFromInput(),
    val twitter: SettingsFromInput = SettingsFromInput(),
    val freeText: SettingsFromInput = SettingsFromInput(),
) {
    /**
     * Updating some particular field and saving all old fields that were not affected by this change
     *
     * @param inputType
     * @param value
     * @param validation
     */
    fun updateValue(inputType: InputTypes, value: String? = null, validation: String? = null) =
            when (inputType) {
                InputTypes.USER_NAME -> this.copy(userName = singleFieldCopy(this.userName, SettingsFromInput(value, validation)))
                InputTypes.USER_EMAIL -> this.copy(userEmail = singleFieldCopy(this.userEmail, SettingsFromInput(value, validation)))
                InputTypes.REAL_NAME -> this.copy(realName = singleFieldCopy(this.realName, SettingsFromInput(value, validation)))
                InputTypes.COMPANY -> this.copy(company = singleFieldCopy(this.company, SettingsFromInput(value, validation)))
                InputTypes.LOCATION -> this.copy(location = singleFieldCopy(this.location, SettingsFromInput(value, validation)))
                InputTypes.WEBSITE -> this.copy(website = singleFieldCopy(this.website, SettingsFromInput(value, validation)))
                InputTypes.LINKEDIN -> this.copy(linkedIn = singleFieldCopy(this.linkedIn, SettingsFromInput(value, validation)))
                InputTypes.GITHUB -> this.copy(github = singleFieldCopy(this.github, SettingsFromInput(value, validation)))
                InputTypes.TWITTER -> this.copy(twitter = singleFieldCopy(this.twitter, SettingsFromInput(value, validation)))
                InputTypes.FREE_TEXT -> this.copy(freeText = singleFieldCopy(this.freeText, SettingsFromInput(value, validation)))
                else -> throw IllegalArgumentException("Invalid input type: $inputType")
            }

    /**
     * @param inputType
     */
    fun getValueByType(inputType: InputTypes) =
            when (inputType) {
                InputTypes.USER_NAME -> userName
                InputTypes.USER_EMAIL -> userEmail
                InputTypes.REAL_NAME -> realName
                InputTypes.COMPANY -> company
                InputTypes.LOCATION -> location
                InputTypes.WEBSITE -> website
                InputTypes.LINKEDIN -> linkedIn
                InputTypes.GITHUB -> github
                InputTypes.TWITTER -> twitter
                InputTypes.FREE_TEXT -> freeText
                else -> throw IllegalArgumentException("Invalid input type: $inputType")
            }

    /**
     * If an input field was somehow changed, we reflect it:
     * we have both value and error in the same object (to have all logic in the same place)
     * If value was not submitted -> we save old value and add only changed part
     */
    private fun singleFieldCopy(old: SettingsFromInput, new: SettingsFromInput) =
            when (Pair(new.value, new.validation)) {
                Pair(new.value, null) -> old.copy(value = new.value)
                Pair(null, new.validation) -> old.copy(validation = new.validation)
                else -> old
            }

    /**
     * @param userInfo
     * @return converted input fields to user info
     */
    fun toUserInfo(userInfo: UserInfo): UserInfo {
        val newName = this.userName.value?.trim()
        return userInfo.copy(
            name = newName ?: userInfo.name,
            // `oldName` is not saved into database, basically it's just a flag for
            // backend to understand that name was or wasn't changed on the frontend
            // need to pass `null` to backend if the field
            oldName = newName?.let { userInfo.name },
            email = this.userEmail.value?.trim() ?: userInfo.email,
            company = this.company.value?.trim() ?: userInfo.company,
            location = this.location.value?.trim() ?: userInfo.location,
            linkedin = this.linkedIn.value?.trim() ?: userInfo.linkedin,
            gitHub = this.github.value?.trim() ?: userInfo.gitHub,
            twitter = this.twitter.value?.trim() ?: userInfo.twitter,
            website = this.website.value?.trim() ?: userInfo.twitter,
            realName = this.realName.value?.trim() ?: userInfo.realName,
            freeText = this.freeText.value?.trim() ?: userInfo.freeText,
        )
    }
}

/**
 * @property value
 * @property validation
 */
data class SettingsFromInput(
    val value: String? = null,
    val validation: String? = null,
)
