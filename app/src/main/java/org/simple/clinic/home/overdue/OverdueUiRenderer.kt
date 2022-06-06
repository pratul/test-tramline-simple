package org.simple.clinic.home.overdue

import org.simple.clinic.mobius.ViewRenderer

class OverdueUiRenderer(
    private val ui: OverdueUi,
    private val isOverdueSectionsFeatureEnabled: Boolean
) : ViewRenderer<OverdueModel> {

  override fun render(model: OverdueModel) {
    if (isOverdueSectionsFeatureEnabled) {
      loadOverdueSections(model)
    }
  }

  private fun loadOverdueSections(model: OverdueModel) {
      if (model.hasLoadedOverdueAppointments) {
        ui.showOverdueAppointments(model.overdueAppointmentSections!!)
        ui.showOverdueCount(model.overdueCount)
        ui.hideProgress()
        ui.handleNoOverduePatientsViewVisibility(model.isOverdueAppointmentSectionsListEmpty)
      } else {
        ui.showProgress()
      }
  }
}
