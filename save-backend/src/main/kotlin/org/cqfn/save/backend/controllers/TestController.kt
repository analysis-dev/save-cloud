package org.cqfn.save.backend.controllers

import org.cqfn.save.backend.service.TestExecutionService
import org.cqfn.save.backend.service.TestService
import org.cqfn.save.entities.Test
import org.cqfn.save.test.TestDto
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/**
 *  Controller used to initialize tests
 */
@RestController
class TestController {
    @Autowired
    private lateinit var testService: TestService

    @Autowired
    private lateinit var testExecutionService: TestExecutionService

    /**
     * @param tests
     */
    @PostMapping("/initializeTests")
    fun initializeTests(@RequestBody testDtos: List<TestDto>) {
        testService.saveTests(testDtos)
        testExecutionService.saveTestExecution(testDtos)
    }

    /**
     * @return test batches
     */
    @GetMapping("/getTestBatches")
    fun testBatches() = testService.getTestBatches()
}
