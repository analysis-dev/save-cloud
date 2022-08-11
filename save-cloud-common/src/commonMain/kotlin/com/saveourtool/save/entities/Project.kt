package com.saveourtool.save.entities

import com.saveourtool.save.utils.EnumType
import com.saveourtool.save.validation.isValidEmail

import kotlinx.serialization.Serializable

/**
 * @property name
 * @property url
 * @property description description of the project, may be absent
 * @property status status of project
 * @property public
 * @property userId the user that has created this project. No automatic mapping, because Hibernate is not available in common code.
 * @property organization
 * @property email
 * @property numberOfContainers
 * @property contestRating
 */
@Entity
@Serializable
data class Project(
    var name: String,
    var url: String?,
    var description: String?,
    @Enumerated(EnumType.STRING)
    var status: ProjectStatus,
    var public: Boolean = true,
    var userId: Long? = null,
    var email: String? = null,
    var numberOfContainers: Int = 3,

    @ManyToOne
    @JoinColumn(
        name = "organization_id",
        table = "",
        foreignKey = ForeignKey(),
        referencedColumnName = "",
        unique = false,
        nullable = true,
        insertable = true,
        updatable = true,
        columnDefinition = "",
    )
    var organization: Organization,
    var contestRating: Long = 0,
) {
    /**
     * id of project
     */
    @Id
    @GeneratedValue
    var id: Long? = null

    /**
     * @return [id] as not null with validating
     * @throws IllegalArgumentException when [id] is not set that means entity is not saved yet
     */
    fun requiredId(): Long = requireNotNull(id) {
        "Entity is not saved yet: $this"
    }

    /**
     * Email validation
     *
     * @return true if email is valid, false otherwise
     */
    fun validateEmail() = email.isNullOrEmpty() || email?.isValidEmail() ?: true

    /**
     * @return [ProjectDto] from [Project]
     */
    fun toDto() = ProjectDto(
        name,
        organization.name,
        public,
        description ?: "",
        url ?: "",
        email ?: "",
    )

    companion object {
        /**
         * Create a stub for testing. Since all fields are mutable, only required ones can be set after calling this method.
         *
         * @param id id of created project
         * @param organization
         * @return a project
         */
        fun stub(
            id: Long?,
            organization: Organization = Organization("stub", OrganizationStatus.CREATED, null, null, null)
        ) = Project(
            name = "stub",
            url = null,
            description = null,
            status = ProjectStatus.CREATED,
            userId = -1,
            organization = Organization("stub", OrganizationStatus.CREATED, null, null, null),
        ).apply {
            this.id = id
            this.organization = organization
        }
    }
}

/**
 * @param organization organization that is an owner of a given project
 * @param status
 * @return [Project] from [ProjectDto]
 */
fun ProjectDto.toProject(
    organization: Organization,
    status: ProjectStatus = ProjectStatus.CREATED,
) = Project(
    name,
    url,
    description,
    status,
    isPublic,
    userId = null,
    email,
    organization = organization,
)
