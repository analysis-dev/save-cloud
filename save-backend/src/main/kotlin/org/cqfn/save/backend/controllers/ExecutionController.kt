package org.cqfn.save.backend.controllers

import org.cqfn.save.backend.service.ExecutionService
import org.cqfn.save.entities.Execution
import org.cqfn.save.execution.ExecutionDto
import org.cqfn.save.execution.ExecutionUpdateDto

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono

typealias ExecutionDtoListResponse = ResponseEntity<List<ExecutionDto>>

/**
 * Controller that accepts executions
 */
@RestController
class ExecutionController(private val executionService: ExecutionService) {
    /**
     * @param execution
     * @return id of created [Execution]
     */
    @PostMapping("/createExecution")
    fun createExecution(@RequestBody execution: Execution): Long = executionService.saveExecution(execution)

    /**
     * @param executionUpdateDto
     */
    @PostMapping("/updateExecution")
    fun updateExecution(@RequestBody executionUpdateDto: ExecutionUpdateDto) {
        executionService.updateExecution(executionUpdateDto)
    }

    /**
     * @param executionId
     * @return execution dto
     */
    @GetMapping("/executionDto")
    fun getExecutionDto(@RequestParam executionId: Long): ResponseEntity<ExecutionDto> =
            executionService.getExecutionDto(executionId)?.let {
                ResponseEntity.status(HttpStatus.OK).body(it)
            } ?: ResponseEntity.status(HttpStatus.NOT_FOUND).build()

    /**
     * @param name
     * @param owner
     * @return list of execution dtos
     */
    @GetMapping("/executionDtoList")
    fun getExecutionByProject(@RequestParam name: String, @RequestParam owner: String): ExecutionDtoListResponse =
            ResponseEntity
                .status(HttpStatus.OK)
                .body(executionService.getExecutionDtoByNameAndOwner(name, owner).reversed())

    /**
     * Get latest (by start time an) execution by project name and project owner
     *
     * @param name project name
     * @param owner project owner
     * @return Execution
     * @throws ResponseStatusException if execution is not found
     */
    @GetMapping("/latestExecution")
    fun getLatestExecutionForProject(@RequestParam name: String, @RequestParam owner: String): Mono<ExecutionDto> =
            Mono.fromCallable { executionService.getLatestExecutionByProjectNameAndProjectOwner(name, owner) }
                .map { it?.toDto() ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Execution not found for project (name=$name, owner=$owner)") }
}
