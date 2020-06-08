package org.simple.clinic.registration.phone

import org.simple.clinic.facility.FacilityPullResult
import org.simple.clinic.user.OngoingRegistrationEntry
import org.simple.clinic.user.UserStatus
import org.simple.clinic.user.finduser.FindUserResult
import org.simple.clinic.util.Optional
import org.simple.clinic.widgets.UiEvent
import java.util.UUID

sealed class RegistrationPhoneEvent : UiEvent

data class RegistrationPhoneNumberTextChanged(val phoneNumber: String) : RegistrationPhoneEvent() {
  override val analyticsName = "Registration:Phone Entry:Phone Number Text Changed"
}

data class CurrentRegistrationEntryLoaded(val entry: Optional<OngoingRegistrationEntry>) : RegistrationPhoneEvent()

data class NewRegistrationEntryCreated(val entry: OngoingRegistrationEntry) : RegistrationPhoneEvent()

data class EnteredNumberValidated(val result: RegistrationPhoneValidationResult) : RegistrationPhoneEvent() {

  companion object {
    // The only reason this is being done here is to avoid coupling the `RegistrationPhoneUpdate`
    // to `PhoneNumberValidator`. The right way to fix this is remove the need for a validator
    // class and introduce a type to encapsulate the entered phone number and its validation.
    // TODO (vs) 04/06/20: https://www.pivotaltracker.com/story/show/173170198
    fun fromValidateNumberResult(result: PhoneNumberValidator.Result): EnteredNumberValidated {
      val registrationPhoneValidationResult = when (result) {
        PhoneNumberValidator.Result.VALID -> RegistrationPhoneValidationResult.Valid
        PhoneNumberValidator.Result.LENGTH_TOO_SHORT -> RegistrationPhoneValidationResult.Invalid.TooShort
        PhoneNumberValidator.Result.LENGTH_TOO_LONG -> RegistrationPhoneValidationResult.Invalid.TooLong
        PhoneNumberValidator.Result.BLANK -> RegistrationPhoneValidationResult.Invalid.Blank
      }

      return EnteredNumberValidated(registrationPhoneValidationResult)
    }
  }
}

data class FacilitiesSynced(val result: Result) : RegistrationPhoneEvent() {

  companion object {
    fun fromFacilityPullResult(facilityPullResult: FacilityPullResult): FacilitiesSynced {
      val result = when (facilityPullResult) {
        FacilityPullResult.Success -> Result.Synced
        FacilityPullResult.NetworkError -> Result.NetworkError
        FacilityPullResult.UnexpectedError -> Result.OtherError
      }
      return FacilitiesSynced(result)
    }
  }

  sealed class Result {

    object Synced : Result()

    object NetworkError : Result()

    object OtherError : Result()
  }
}

data class SearchForExistingUserCompleted(val result: Result) : RegistrationPhoneEvent() {

  companion object {
    fun fromFindUserResult(findUserResult: FindUserResult): SearchForExistingUserCompleted {
      val result = when (findUserResult) {
        is FindUserResult.Found -> Result.Found(findUserResult.uuid, findUserResult.status)
        FindUserResult.NotFound -> Result.NotFound
        FindUserResult.NetworkError -> Result.NetworkError
        FindUserResult.UnexpectedError -> Result.OtherError
      }

      return SearchForExistingUserCompleted(result)
    }
  }

  sealed class Result {

    data class Found(val uuid: UUID, val status: UserStatus) : Result()

    object NotFound : Result()

    object NetworkError : Result()

    object OtherError : Result()
  }
}

class RegistrationPhoneDoneClicked : RegistrationPhoneEvent() {
  override val analyticsName = "Registration:Phone Entry:Done Clicked"
}

object UserCreatedLocally : RegistrationPhoneEvent()

object CurrentRegistrationEntryCleared: RegistrationPhoneEvent()

data class CurrentUserUnauthorizedStatusLoaded(val isUserUnauthorized: Boolean): RegistrationPhoneEvent()

object CurrentRegistrationEntrySaved: RegistrationPhoneEvent()