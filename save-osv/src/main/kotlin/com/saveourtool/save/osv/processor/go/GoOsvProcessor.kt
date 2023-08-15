package com.saveourtool.save.osv.processor.go

import com.saveourtool.save.entities.vulnerability.VulnerabilityDto
import com.saveourtool.save.osv.processor.AbstractOsvProcessor
import com.saveourtool.save.osv.processor.OsvProcessor
import com.saveourtool.save.osv.storage.OsvStorage

import com.saveourtool.osv4k.OsvSchema
import org.springframework.stereotype.Component

/**
 * [OsvProcessor] for OSV from [Go Vulnerability Database](https://pkg.go.dev/vuln/)
 */
@Component
class GoOsvProcessor(
    osvStorage: OsvStorage,
) : AbstractOsvProcessor<GoUrl, GoImports, Unit, Unit>(osvStorage) {
    override val id: String = "GO"

    override fun VulnerabilityDto.updateBySpecificFields(osv: OsvSchema<GoUrl, GoImports, Unit, Unit>): VulnerabilityDto = copy(
        relatedLink = osv.databaseSpecific?.url,
    )
}