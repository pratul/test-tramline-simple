package org.simple.clinic.recentpatientsview

import java.util.UUID

interface LatestRecentPatientsUi : LatestRecentPatientsUiActions {
  fun updateRecentPatients(recentPatients: List<RecentPatientItemType>)
  fun showOrHideRecentPatients(isVisible: Boolean)
  fun openRecentPatientsScreen()
  fun openPatientSummary(patientUuid: UUID)
}