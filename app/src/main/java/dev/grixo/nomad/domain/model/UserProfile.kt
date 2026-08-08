package dev.grixo.nomad.domain.model

enum class Gender(val label: String) {
    FEMALE("Female"),
    MALE("Male"),
    NON_BINARY("Non-binary"),
    PREFER_NOT("Prefer not to say"),
    OTHER("Other");

    companion object {
        fun fromStorage(value: String?): Gender? =
            entries.firstOrNull { it.name == value }
    }
}

data class UserProfile(
    val name: String,
    val email: String?,
    val phone: String?,
    val age: Int,
    val gender: Gender
)

enum class OnboardingStatus {
    PENDING,
    REGISTERED,
    SKIPPED
}
