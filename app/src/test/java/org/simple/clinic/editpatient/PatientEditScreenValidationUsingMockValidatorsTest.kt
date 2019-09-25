package org.simple.clinic.editpatient

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.inOrder
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import junitparams.JUnitParamsRunner
import junitparams.Parameters
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.simple.clinic.editpatient.PatientEditValidationError.BOTH_DATEOFBIRTH_AND_AGE_ABSENT
import org.simple.clinic.editpatient.PatientEditValidationError.COLONY_OR_VILLAGE_EMPTY
import org.simple.clinic.editpatient.PatientEditValidationError.DATE_OF_BIRTH_IN_FUTURE
import org.simple.clinic.editpatient.PatientEditValidationError.DISTRICT_EMPTY
import org.simple.clinic.editpatient.PatientEditValidationError.FULL_NAME_EMPTY
import org.simple.clinic.editpatient.PatientEditValidationError.INVALID_DATE_OF_BIRTH
import org.simple.clinic.editpatient.PatientEditValidationError.PHONE_NUMBER_EMPTY
import org.simple.clinic.editpatient.PatientEditValidationError.PHONE_NUMBER_LENGTH_TOO_LONG
import org.simple.clinic.editpatient.PatientEditValidationError.PHONE_NUMBER_LENGTH_TOO_SHORT
import org.simple.clinic.editpatient.PatientEditValidationError.STATE_EMPTY
import org.simple.clinic.patient.Gender
import org.simple.clinic.patient.PatientMocker
import org.simple.clinic.patient.PatientPhoneNumber
import org.simple.clinic.patient.PatientRepository
import org.simple.clinic.registration.phone.PhoneNumberValidator
import org.simple.clinic.registration.phone.PhoneNumberValidator.Result.BLANK
import org.simple.clinic.registration.phone.PhoneNumberValidator.Result.LENGTH_TOO_LONG
import org.simple.clinic.registration.phone.PhoneNumberValidator.Result.LENGTH_TOO_SHORT
import org.simple.clinic.registration.phone.PhoneNumberValidator.Result.VALID
import org.simple.clinic.util.RxErrorsRule
import org.simple.clinic.util.TestUserClock
import org.simple.clinic.util.TestUtcClock
import org.simple.clinic.util.toOptional
import org.simple.clinic.widgets.UiEvent
import org.simple.clinic.widgets.ageanddateofbirth.UserInputDateValidator
import org.simple.clinic.widgets.ageanddateofbirth.UserInputDateValidator.Result.Valid
import org.threeten.bp.LocalDate
import org.threeten.bp.format.DateTimeFormatter
import java.util.Locale

@RunWith(JUnitParamsRunner::class)
class PatientEditScreenValidationUsingMockValidatorsTest {

  @get:Rule
  val rxErrorsRule = RxErrorsRule()

  private val uiEvents = PublishSubject.create<UiEvent>()
  val utcClock: TestUtcClock = TestUtcClock()

  private lateinit var screen: PatientEditScreen
  private lateinit var patientRepository: PatientRepository
  private lateinit var controller: PatientEditScreenController

  private lateinit var numberValidator: PhoneNumberValidator
  private lateinit var dobValidator: UserInputDateValidator

  private val dateOfBirthFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ENGLISH)

  private lateinit var errorConsumer: (Throwable) -> Unit

  @Before
  fun setUp() {
    screen = mock()
    patientRepository = mock()
    numberValidator = mock()
    dobValidator = mock()

    whenever(dobValidator.dateInUserTimeZone()).thenReturn(LocalDate.now(utcClock))

    controller = PatientEditScreenController(
        patientRepository,
        numberValidator,
        utcClock,
        TestUserClock(),
        dobValidator,
        dateOfBirthFormat)

    errorConsumer = { throw it }

    uiEvents
        .compose(controller)
        .subscribe({ uiChange -> uiChange(screen) }, { e -> errorConsumer(e) })
  }

  @Test
  @Parameters(method = "params for validating all fields on save clicks")
  fun `when save is clicked, all fields should be validated`(validateFieldsTestParams: ValidateFieldsTestParams) {
    val (alreadyPresentPhoneNumber,
        name,
        numberValidationResult,
        colonyOrVillage,
        district,
        state,
        age,
        userInputDateOfBirthValidationResult,
        dateOfBirth,
        expectedErrors
    ) = validateFieldsTestParams

    val patient = PatientMocker.patient()
    val address = PatientMocker.address()

    whenever(patientRepository.updatePhoneNumberForPatient(any(), any())).thenReturn(Completable.complete())
    whenever(patientRepository.updateAddressForPatient(any(), any())).thenReturn(Completable.complete())
    whenever(patientRepository.updatePatient(any())).thenReturn(Completable.complete())

    whenever(numberValidator.validate(any(), any())).thenReturn(numberValidationResult)

    if (userInputDateOfBirthValidationResult != null) {
      whenever(dobValidator.validate(any(), any())).thenReturn(userInputDateOfBirthValidationResult)
    }

    uiEvents.onNext(PatientEditScreenCreated.from(patient, address, alreadyPresentPhoneNumber))

    uiEvents.onNext(PatientEditPatientNameTextChanged(name))
    uiEvents.onNext(PatientEditPhoneNumberTextChanged(""))
    uiEvents.onNext(PatientEditColonyOrVillageChanged(colonyOrVillage))
    uiEvents.onNext(PatientEditDistrictTextChanged(district))
    uiEvents.onNext(PatientEditStateTextChanged(state))
    uiEvents.onNext(PatientEditGenderChanged(Gender.Male))

    if (age != null) {
      uiEvents.onNext(PatientEditDateOfBirthTextChanged(""))
      uiEvents.onNext(PatientEditAgeTextChanged(age))
    }

    if (dateOfBirth != null) {
      uiEvents.onNext(PatientEditAgeTextChanged(""))
      uiEvents.onNext(PatientEditDateOfBirthTextChanged(dateOfBirth))
    }

    if (age == null && dateOfBirth == null) {
      uiEvents.onNext(PatientEditAgeTextChanged(""))
    }

    uiEvents.onNext(PatientEditSaveClicked())

    if (expectedErrors.isNotEmpty()) {
      // This is order dependent because finding the first field
      // with error is only possible once the errors are set.
      val inOrder = inOrder(screen)

      inOrder.verify(screen).showValidationErrors(expectedErrors)
      inOrder.verify(screen).scrollToFirstFieldWithError()

    } else {
      verify(screen, never()).showValidationErrors(any())
      verify(screen, never()).scrollToFirstFieldWithError()
    }
  }

  @Suppress("Unused")
  private fun `params for validating all fields on save clicks`(): List<ValidateFieldsTestParams> {
    return listOf(
        ValidateFieldsTestParams(
            PatientMocker.phoneNumber(),
            "",
            BLANK,
            "",
            "",
            "",
            "1",
            null,
            null,
            setOf(FULL_NAME_EMPTY, PHONE_NUMBER_EMPTY, COLONY_OR_VILLAGE_EMPTY, DISTRICT_EMPTY, STATE_EMPTY)
        ),
        ValidateFieldsTestParams(
            null,
            "",
            BLANK,
            "",
            "",
            "",
            "",
            null,
            null,
            setOf(FULL_NAME_EMPTY, COLONY_OR_VILLAGE_EMPTY, DISTRICT_EMPTY, STATE_EMPTY, BOTH_DATEOFBIRTH_AND_AGE_ABSENT)
        ),
        ValidateFieldsTestParams(
            PatientMocker.phoneNumber(),
            "",
            LENGTH_TOO_SHORT,
            "Colony",
            "",
            "",
            "1",
            null,
            null,
            setOf(FULL_NAME_EMPTY, PHONE_NUMBER_LENGTH_TOO_SHORT, DISTRICT_EMPTY, STATE_EMPTY)
        ),
        ValidateFieldsTestParams(
            null,
            "",
            LENGTH_TOO_SHORT,
            "Colony",
            "",
            "",
            "",
            null,
            null,
            setOf(FULL_NAME_EMPTY, PHONE_NUMBER_LENGTH_TOO_SHORT, DISTRICT_EMPTY, STATE_EMPTY, BOTH_DATEOFBIRTH_AND_AGE_ABSENT)
        ),
        ValidateFieldsTestParams(
            PatientMocker.phoneNumber(),
            "Name",
            LENGTH_TOO_LONG,
            "",
            "District",
            "",
            "1",
            null,
            null,
            setOf(PHONE_NUMBER_LENGTH_TOO_LONG, COLONY_OR_VILLAGE_EMPTY, STATE_EMPTY)
        ),
        ValidateFieldsTestParams(
            null,
            "Name",
            LENGTH_TOO_LONG,
            "",
            "District",
            "",
            null,
            UserInputDateValidator.Result.Invalid.InvalidPattern,
            "01/01/2000",
            setOf(PHONE_NUMBER_LENGTH_TOO_LONG, COLONY_OR_VILLAGE_EMPTY, STATE_EMPTY, INVALID_DATE_OF_BIRTH)
        ),
        ValidateFieldsTestParams(
            PatientMocker.phoneNumber(),
            "",
            VALID,
            "Colony",
            "District",
            "",
            null,
            null,
            null,
            setOf(FULL_NAME_EMPTY, STATE_EMPTY, BOTH_DATEOFBIRTH_AND_AGE_ABSENT)
        ),
        ValidateFieldsTestParams(
            null,
            "",
            VALID,
            "Colony",
            "District",
            "",
            null,
            UserInputDateValidator.Result.Invalid.DateIsInFuture,
            "01/01/2000",
            setOf(FULL_NAME_EMPTY, STATE_EMPTY, DATE_OF_BIRTH_IN_FUTURE)
        ),
        ValidateFieldsTestParams(
            null,
            "",
            BLANK,
            "Colony",
            "District",
            "State",
            "",
            null,
            null,
            setOf(FULL_NAME_EMPTY, BOTH_DATEOFBIRTH_AND_AGE_ABSENT)
        ),
        ValidateFieldsTestParams(
            PatientMocker.phoneNumber(),
            "Name",
            VALID,
            "Colony",
            "District",
            "State",
            "1",
            null,
            null,
            emptySet()
        ),
        ValidateFieldsTestParams(
            null,
            "Name",
            VALID,
            "Colony",
            "District",
            "State",
            null,
            Valid(LocalDate.parse("1947-01-01")),
            "01/01/2000",
            emptySet()
        )
    )
  }

  @Test
  @Parameters(method = "params for validating phone numbers")
  fun `when save is clicked, phone number should be validated`(testParams: ValidatePhoneNumberTestParams) {
    val (alreadyPresentPhoneNumber, numberValidationResult, expectedError) = testParams

    val patient = PatientMocker.patient()
    val address = PatientMocker.address()

    whenever(patientRepository.patient(patient.uuid)).thenReturn(Observable.just(patient.toOptional()))
    whenever(patientRepository.address(address.uuid)).thenReturn(Observable.just(address.toOptional()))
    whenever(patientRepository.phoneNumber(patient.uuid)).thenReturn(Observable.just(alreadyPresentPhoneNumber.toOptional()))

    whenever(numberValidator.validate(any(), any())).thenReturn(numberValidationResult)

    whenever(patientRepository.updatePhoneNumberForPatient(eq(patient.uuid), any())).thenReturn(Completable.complete())
    whenever(patientRepository.createPhoneNumberForPatient(eq(patient.uuid), any(), any(), any())).thenReturn(Completable.complete())
    whenever(patientRepository.updateAddressForPatient(eq(patient.uuid), any())).thenReturn(Completable.complete())
    whenever(patientRepository.updatePatient(any())).thenReturn(Completable.complete())

    uiEvents.onNext(PatientEditScreenCreated.from(patient, address, alreadyPresentPhoneNumber))

    uiEvents.onNext(PatientEditGenderChanged(Gender.Male))
    uiEvents.onNext(PatientEditColonyOrVillageChanged("Colony"))
    uiEvents.onNext(PatientEditDistrictTextChanged("District"))
    uiEvents.onNext(PatientEditStateTextChanged("State"))
    uiEvents.onNext(PatientEditPatientNameTextChanged("Name"))
    uiEvents.onNext(PatientEditAgeTextChanged("1"))

    uiEvents.onNext(PatientEditPhoneNumberTextChanged(""))
    uiEvents.onNext(PatientEditSaveClicked())

    if (expectedError == null) {
      verify(screen, never()).showValidationErrors(any())
    } else {
      verify(screen).showValidationErrors(setOf(expectedError))
    }
  }

  @Suppress("Unused")
  private fun `params for validating phone numbers`(): List<ValidatePhoneNumberTestParams> {
    return listOf(
        ValidatePhoneNumberTestParams(null, BLANK, null),
        ValidatePhoneNumberTestParams(null, LENGTH_TOO_LONG, PHONE_NUMBER_LENGTH_TOO_LONG),
        ValidatePhoneNumberTestParams(null, LENGTH_TOO_SHORT, PHONE_NUMBER_LENGTH_TOO_SHORT),
        ValidatePhoneNumberTestParams(PatientMocker.phoneNumber(), BLANK, PHONE_NUMBER_EMPTY),
        ValidatePhoneNumberTestParams(PatientMocker.phoneNumber(), LENGTH_TOO_SHORT, PHONE_NUMBER_LENGTH_TOO_SHORT),
        ValidatePhoneNumberTestParams(PatientMocker.phoneNumber(), LENGTH_TOO_LONG, PHONE_NUMBER_LENGTH_TOO_LONG)
    )
  }
}

data class ValidateFieldsTestParams(
    val alreadyPresentPhoneNumber: PatientPhoneNumber?,
    val name: String,
    val numberValidationResult: PhoneNumberValidator.Result,
    val colonyOrVillage: String,
    val district: String,
    val state: String,
    val age: String?,
    val userInputDateOfBirthValidationResult: UserInputDateValidator.Result?,
    val dateOfBirth: String?,
    val expectedErrors: Set<PatientEditValidationError>
)

data class ValidatePhoneNumberTestParams(
    val alreadyPresentPhoneNumber: PatientPhoneNumber?,
    val numberValidationResult: PhoneNumberValidator.Result,
    val expectedError: PatientEditValidationError?
)
