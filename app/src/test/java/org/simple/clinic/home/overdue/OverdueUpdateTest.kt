package org.simple.clinic.home.overdue

import androidx.paging.PagingData
import com.spotify.mobius.test.NextMatchers.hasEffects
import com.spotify.mobius.test.NextMatchers.hasModel
import com.spotify.mobius.test.NextMatchers.hasNoEffects
import com.spotify.mobius.test.NextMatchers.hasNoModel
import com.spotify.mobius.test.UpdateSpec
import com.spotify.mobius.test.UpdateSpec.assertThatNext
import org.junit.Test
import org.simple.clinic.analytics.NetworkConnectivityStatus.ACTIVE
import org.simple.clinic.analytics.NetworkConnectivityStatus.INACTIVE
import org.simple.clinic.facility.FacilityConfig
import org.simple.clinic.home.overdue.PendingListState.SEE_ALL
import org.simple.clinic.home.overdue.PendingListState.SEE_LESS
import org.simple.clinic.overdue.download.OverdueListFileFormat.CSV
import org.simple.sharedTestCode.TestData
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

class OverdueUpdateTest {

  private val dateOnClock = LocalDate.parse("2018-01-01")
  private val updateSpec = UpdateSpec(OverdueUpdate(date = dateOnClock,
      canGeneratePdf = true,
      isOverdueSectionsFeatureEnabled = false))
  private val defaultModel = OverdueModel.create()

  @Test
  fun `when overdue patient is clicked, then open patient summary screen`() {
    val patientUuid = UUID.fromString("1211bce0-0b5d-4203-b5e3-004709059eca")

    updateSpec
        .given(defaultModel)
        .whenEvent(OverduePatientClicked(patientUuid))
        .then(assertThatNext(
            hasNoModel(),
            hasEffects(OpenPatientSummary(patientUuid))
        ))
  }

  @Test
  fun `when overdue appointments are loaded, then show overdue appointments`() {
    val overdueAppointments = PagingData.from(listOf(
        TestData.overdueAppointment_Old(appointmentUuid = UUID.fromString("4e4baeba-3a8e-4453-ace1-d3149088aefc")),
        TestData.overdueAppointment_Old(appointmentUuid = UUID.fromString("79c4bda9-50cf-4484-8a2a-c5336ce8af84"))
    ))
    val facility = TestData.facility(
        uuid = UUID.fromString("6d66fda7-7ca6-4431-ac3b-b570f1123624"),
        facilityConfig = FacilityConfig(
            diabetesManagementEnabled = true,
            teleconsultationEnabled = false
        )
    )
    val facilityLoadedModel = defaultModel
        .currentFacilityLoaded(facility)

    updateSpec
        .given(facilityLoadedModel)
        .whenEvent(OverdueAppointmentsLoaded_Old(overdueAppointments))
        .then(assertThatNext(
            hasNoModel(),
            hasEffects(ShowOverdueAppointments(overdueAppointments, isDiabetesManagementEnabled = true))
        ))
  }

  @Test
  fun `when current facility is loaded and overdue sections feature is disabled, then load pending overdue appointments`() {
    val facility = TestData.facility(
        uuid = UUID.fromString("6d66fda7-7ca6-4431-ac3b-b570f1123624"),
        facilityConfig = FacilityConfig(
            diabetesManagementEnabled = true,
            teleconsultationEnabled = false
        )
    )

    updateSpec
        .given(defaultModel)
        .whenEvent(CurrentFacilityLoaded(facility))
        .then(assertThatNext(
            hasModel(defaultModel.currentFacilityLoaded(facility)),
            hasEffects(LoadOverdueAppointments_old(dateOnClock, facility))
        ))
  }

  @Test
  fun `when current facility is loaded and overdue sections feature is enabled, then load overdue appointments`() {
    val facility = TestData.facility(
        uuid = UUID.fromString("6d66fda7-7ca6-4431-ac3b-b570f1123624"),
        facilityConfig = FacilityConfig(
            diabetesManagementEnabled = true,
            teleconsultationEnabled = false
        )
    )

    val updateSpec = UpdateSpec(OverdueUpdate(
        date = dateOnClock,
        canGeneratePdf = true,
        isOverdueSectionsFeatureEnabled = true
    ))

    updateSpec
        .given(defaultModel)
        .whenEvent(CurrentFacilityLoaded(facility))
        .then(assertThatNext(
            hasModel(defaultModel.currentFacilityLoaded(facility)),
            hasEffects(LoadOverdueAppointments(dateOnClock, facility))
        ))
  }

  @Test
  fun `when download overdue list button is clicked and network is not connected, then show no active connection dialog`() {
    updateSpec
        .given(defaultModel)
        .whenEvent(DownloadOverdueListClicked(networkStatus = Optional.of(INACTIVE)))
        .then(assertThatNext(
            hasNoModel(),
            hasEffects(ShowNoActiveNetworkConnectionDialog)
        ))
  }

  @Test
  fun `when download overdue list button is clicked, network is connected and pdf can be generated, then open select download format dialog`() {
    updateSpec
        .given(defaultModel)
        .whenEvent(DownloadOverdueListClicked(networkStatus = Optional.of(ACTIVE)))
        .then(assertThatNext(
            hasNoModel(),
            hasEffects(OpenSelectDownloadFormatDialog)
        ))
  }

  @Test
  fun `when download overdue list button is clicked, network is connected and pdf can not be generated, then schedule download`() {
    val updateSpec = UpdateSpec(OverdueUpdate(date = dateOnClock, canGeneratePdf = false, isOverdueSectionsFeatureEnabled = false))

    updateSpec
        .given(defaultModel)
        .whenEvent(DownloadOverdueListClicked(networkStatus = Optional.of(ACTIVE)))
        .then(assertThatNext(
            hasNoModel(),
            hasEffects(ScheduleDownload(CSV))
        ))
  }

  @Test
  fun `when share overdue list button is clicked and network is not connected, then show no active connection dialog`() {
    updateSpec
        .given(defaultModel)
        .whenEvent(ShareOverdueListClicked(networkStatus = Optional.of(INACTIVE)))
        .then(assertThatNext(
            hasNoModel(),
            hasEffects(ShowNoActiveNetworkConnectionDialog)
        ))
  }

  @Test
  fun `when share overdue list button is clicked, network is connected and pdf can be generated, then open select share format dialog`() {
    updateSpec
        .given(defaultModel)
        .whenEvent(ShareOverdueListClicked(networkStatus = Optional.of(ACTIVE)))
        .then(assertThatNext(
            hasNoModel(),
            hasEffects(OpenSelectShareFormatDialog)
        ))
  }

  @Test
  fun `when share overdue list button is clicked, network is connected but pdf can not be generated, then open progress for share dialog`() {
    val updateSpec = UpdateSpec(OverdueUpdate(date = dateOnClock, canGeneratePdf = false, isOverdueSectionsFeatureEnabled = false))

    updateSpec
        .given(defaultModel)
        .whenEvent(ShareOverdueListClicked(networkStatus = Optional.of(ACTIVE)))
        .then(assertThatNext(
            hasNoModel(),
            hasEffects(OpenSharingInProgressDialog)
        ))
  }

  @Test
  fun `when overdue appointments are loaded, then update the model`() {
    val pendingAppointments = listOf(
        TestData.overdueAppointment(appointmentUuid = UUID.fromString("ad63a726-f0ab-4e95-a20e-bd394b4c7d3c"))
    )
    val agreedToVisitAppointments = listOf(
        TestData.overdueAppointment(appointmentUuid = UUID.fromString("372871f0-0b11-4217-926f-9c5f2dce8202"))
    )
    val remindToCallLaterAppointments = listOf(
        TestData.overdueAppointment(appointmentUuid = UUID.fromString("09ad7724-b3e2-4b1c-b490-7d7951b4150d"))
    )
    val removedFromOverdueAppointments = listOf(
        TestData.overdueAppointment(appointmentUuid = UUID.fromString("e52d4555-d72d-4dfd-9b3e-21ead416e727"))
    )
    val moreThanAnYearOverdueAppointments = listOf(
        TestData.overdueAppointment(appointmentUuid = UUID.fromString("20bb3b3a-908e-49b5-97ef-730eb2504bd9"))
    )

    val overdueAppointmentSections = OverdueAppointmentSections(
        pendingAppointments = pendingAppointments,
        agreedToVisitAppointments = agreedToVisitAppointments,
        remindToCallLaterAppointments = remindToCallLaterAppointments,
        removedFromOverdueAppointments = removedFromOverdueAppointments,
        moreThanAnYearOverdueAppointments = moreThanAnYearOverdueAppointments
    )

    updateSpec
        .given(defaultModel)
        .whenEvent(OverdueAppointmentsLoaded(
            overdueAppointmentSections = overdueAppointmentSections
        ))
        .then(assertThatNext(
            hasModel(defaultModel.overdueAppointmentsLoaded(
                overdueAppointmentSections = overdueAppointmentSections
            )),
            hasNoEffects()
        ))
  }

  @Test
  fun `when pending list footer is clicked and pending list state is see less, then change the pending list state to see all`() {
    updateSpec
        .given(defaultModel)
        .whenEvent(PendingListFooterClicked)
        .then(
            assertThatNext(
                hasModel(defaultModel.pendingListStateChanged(state = SEE_ALL)),
                hasNoEffects()
            )
        )
  }

  @Test
  fun `when pending list footer is clicked and pending list state is see all, then change the pending list state to see less`() {
    updateSpec
        .given(defaultModel.pendingListStateChanged(state = SEE_ALL))
        .whenEvent(PendingListFooterClicked)
        .then(
            assertThatNext(
                hasModel(defaultModel.pendingListStateChanged(state = SEE_LESS)),
                hasNoEffects()
            )
        )
  }
}
