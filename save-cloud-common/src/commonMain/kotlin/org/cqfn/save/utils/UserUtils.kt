/**
 * Utilities of User entity
 */

package org.cqfn.save.utils

/**
 * @param userInformation
 * @return pair of username and source (where the user identity is coming from)
 */
fun extractUserNameAndSource(userInformation: String): Pair<String, String> {
    // for users, which are not linked with any source (also convenient for local deployment)
    if (!userInformation.contains("@")) {
        return userInformation to "basic"
    }
    userInformation.split("@").map { userInfo -> userInfo.trim() }.let { sourceAndUserNameList ->
        require(sourceAndUserNameList.size == 2) {
            "User information $userInformation should contain source and username, separated by `@` but found after extraction: $sourceAndUserNameList"
        }
        return sourceAndUserNameList.last() to sourceAndUserNameList.first()
    }
}
