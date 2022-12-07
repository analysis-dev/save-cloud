package com.saveourtool.save.demo.diktat

import com.saveourtool.save.demo.DemoAdditionalParams
import com.saveourtool.save.utils.Languages
import kotlinx.serialization.Serializable

/**
 * @property mode
 * @property tool
 * @property config
 * @property language
 */
@Serializable
data class DemoAdditionalParams(
    val mode: DiktatDemoMode = DiktatDemoMode.WARN,
    val tool: DiktatDemoTool = DiktatDemoTool.DIKTAT,
    val config: List<String> = defaultDiktatConfig,
    val language: Languages = Languages.KOTLIN,
) : DemoAdditionalParams {
    companion object {
        val defaultDiktatConfig = """
            |- name: DIKTAT_COMMON
            |  enabled: true
            |  configuration:
            |    domainName: com.test
            |    kotlinVersion: 1.7
            |    srcDirectories: "src"
            |- name: ENUM_VALUE
            |  enabled: true
            |  configuration:
            |    enumStyle: SNAKE_CASE
            |- name: HEADER_MISSING_OR_WRONG_COPYRIGHT
            |  enabled: true
            |  configuration:
            |    isCopyrightMandatory: false
            |    copyrightText: ''
            |- name: FILE_IS_TOO_LONG
            |  enabled: true
            |  configuration:
            |    maxSize: 2000
            |    ignoreFolders: ''
            |- name: FILE_UNORDERED_IMPORTS
            |  enabled: true
            |  configuration:
            |    useRecommendedImportsOrder: true
            |- name: BRACES_BLOCK_STRUCTURE_ERROR
            |  enabled: true
            |  configuration:
            |    openBraceNewline: true
            |    closeBraceNewline: true
            |- name: WRONG_INDENTATION
            |  enabled: true
            |  configuration:
            |    newlineAtEnd: true
            |    extendedIndentOfParameters: false
            |    alignedParameters: true
            |    extendedIndentAfterOperators: true
            |    extendedIndentBeforeDot: false
            |    indentationSize: 4
            |    extendedIndentForExpressionBodies: true
            |- name: EMPTY_BLOCK_STRUCTURE_ERROR
            |  enabled: true
            |  configuration:
            |    styleEmptyBlockWithNewline: true
            |    allowEmptyBlocks: false
            |- name: LONG_LINE
            |  enabled: true
            |  configuration:
            |    lineLength: 180
            |- name: WRONG_NEWLINES
            |  enabled: true
            |  configuration:
            |    maxParametersInOneLine: 3
            |- name: TOO_MANY_CONSECUTIVE_SPACES
            |  enabled: true
            |  configuration:
            |    maxSpaces: 1
            |    saveInitialFormattingForEnums: false
            |- name: LONG_NUMERICAL_VALUES_SEPARATED
            |  enabled: true
            |  configuration:
            |    maxNumberLength: 5
            |    maxBlockLength: 3
            |- name: WRONG_DECLARATIONS_ORDER
            |  enabled: true
            |  configuration:
            |    sortEnum: true
            |    sortProperty: true
            |- name: COMMENT_WHITE_SPACE
            |  enabled: true
            |  configuration:
            |    maxSpacesBeforeComment: 2
            |    maxSpacesInComment: 1
            |- name: TYPE_ALIAS
            |  enabled: true
            |  configuration:
            |    typeReferenceLength: 25
            |- name: TOO_LONG_FUNCTION
            |  enabled: true
            |  configuration:
            |    maxFunctionLength: 55
            |    isIncludeHeader: false
            |- name: TOO_MANY_PARAMETERS
            |  enabled: true
            |  configuration:
            |    maxParameterListSize: 5
            |- name: NESTED_BLOCK
            |  enabled: true
            |  configuration:
            |    maxNestedBlockQuantity: 4
            |- name: TRAILING_COMMA
            |  enabled: false
            |  configuration:
            |    valueArgument: true
            |    valueParameter: true
            |- name: MAGIC_NUMBER
            |  enabled: true
            |- name: COMPLEX_EXPRESSION
            |  enabled: true
        """.trimMargin().split("\n")
    }
}
