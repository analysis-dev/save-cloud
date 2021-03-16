package org.cqfn.save.backend.controllers

import org.cqfn.save.backend.service.TestStatusesService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@Suppress("MISSING_KDOC_TOP_LEVEL")
@RestController
class TestStatusesController {
    @Autowired
    private lateinit var testStatusesService: TestStatusesService

    /**
     * @param tests
     */
    // Fixme: change List<String> to Test when it will be ready
    @PostMapping("/saveTestStatuses")
    fun saveTestStatuses(@RequestBody tests: List<String>) {
        testStatusesService.saveTestStatuses(tests)
    }
}
