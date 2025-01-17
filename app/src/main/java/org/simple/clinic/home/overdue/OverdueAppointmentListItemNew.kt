package org.simple.clinic.home.overdue

import android.graphics.Rect
import android.view.TouchDelegate
import android.view.View
import androidx.annotation.StringRes
import androidx.recyclerview.widget.DiffUtil
import io.reactivex.subjects.Subject
import org.simple.clinic.R
import org.simple.clinic.databinding.ListItemOverdueListSectionHeaderBinding
import org.simple.clinic.databinding.ListItemOverduePatientBinding
import org.simple.clinic.databinding.ListItemOverduePendingListFooterBinding
import org.simple.clinic.patient.Gender
import org.simple.clinic.patient.displayIconRes
import org.simple.clinic.util.UserClock
import org.simple.clinic.widgets.ItemAdapter
import org.simple.clinic.widgets.UiEvent
import org.simple.clinic.widgets.dp
import org.simple.clinic.widgets.executeOnNextMeasure
import org.simple.clinic.widgets.recyclerview.BindingViewHolder
import org.simple.clinic.widgets.visibleOrGone
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

sealed class OverdueAppointmentListItemNew : ItemAdapter.Item<UiEvent> {

  companion object {

    fun from(
        overdueAppointmentSections: OverdueAppointmentSections,
        clock: UserClock
    ): List<OverdueAppointmentListItemNew> {
      val pendingToCallHeader = listOf(OverdueSectionHeader(R.string.overdue_pending_to_call_header, overdueAppointmentSections.pendingAppointments.size))
      val pendingAppointmentsContent = overdueAppointmentSections.pendingAppointments
          .map { from(it, clock) }
          .ifEmpty { listOf(NoPendingPatients) }

      val agreedToVisitHeader = listOf(OverdueSectionHeader(R.string.overdue_agreed_to_visit_call_header, overdueAppointmentSections.agreedToVisitAppointments.size))
      val agreedToVisitListItems = overdueAppointmentSections.agreedToVisitAppointments.map { from(it, clock) }

      val remindToCallHeader = listOf(OverdueSectionHeader(R.string.overdue_remind_to_call_header, overdueAppointmentSections.remindToCallLaterAppointments.size))
      val remindToCallListItems = overdueAppointmentSections.remindToCallLaterAppointments.map { from(it, clock) }

      val removedFromOverdueListHeader = listOf(OverdueSectionHeader(R.string.overdue_removed_from_list_call_header, overdueAppointmentSections.removedFromOverdueAppointments.size))
      val removedFromOverdueListItems = overdueAppointmentSections.removedFromOverdueAppointments.map { from(it, clock) }

      val moreThanAnOneYearOverdueHeader = listOf(OverdueSectionHeader(R.string.overdue_no_visit_in_one_year_call_header, overdueAppointmentSections.moreThanAnYearOverdueAppointments.size))
      val moreThanAnOneYearOverdueListItems = overdueAppointmentSections.moreThanAnYearOverdueAppointments.map { from(it, clock) }

      val pendingListFooterItem = listOf(PendingListFooter)

      val dividerListItem = listOf(Divider)

      return pendingToCallHeader + pendingAppointmentsContent +
          pendingListFooterItem + dividerListItem +
          agreedToVisitHeader + agreedToVisitListItems + dividerListItem +
          remindToCallHeader + remindToCallListItems + dividerListItem +
          removedFromOverdueListHeader + removedFromOverdueListItems + dividerListItem +
          moreThanAnOneYearOverdueHeader + moreThanAnOneYearOverdueListItems
    }

    private fun from(
        overdueAppointment: OverdueAppointment,
        clock: UserClock
    ): OverdueAppointmentListItemNew {
      return OverdueAppointmentRow(
          appointmentUuid = overdueAppointment.appointment.uuid,
          patientUuid = overdueAppointment.appointment.patientUuid,
          name = overdueAppointment.fullName,
          gender = overdueAppointment.gender,
          age = overdueAppointment.ageDetails.estimateAge(clock),
          phoneNumber = overdueAppointment.phoneNumber?.number,
          overdueDays = daysBetweenNowAndDate(overdueAppointment.appointment.scheduledDate, clock),
          isAtHighRisk = overdueAppointment.isAtHighRisk,
          villageName = overdueAppointment.patientAddress.colonyOrVillage
      )
    }

    private fun daysBetweenNowAndDate(
        date: LocalDate,
        clock: UserClock
    ): Int {
      return ChronoUnit.DAYS.between(date, LocalDate.now(clock)).toInt()
    }
  }

  data class OverdueAppointmentRow(
      val appointmentUuid: UUID,
      val patientUuid: UUID,
      val name: String,
      val gender: Gender,
      val age: Int,
      val phoneNumber: String? = null,
      val overdueDays: Int,
      val isAtHighRisk: Boolean,
      val villageName: String?
  ) : OverdueAppointmentListItemNew() {

    override fun layoutResId(): Int = R.layout.list_item_overdue_patient

    override fun render(holder: BindingViewHolder, subject: Subject<UiEvent>) {
      val binding = holder.binding as ListItemOverduePatientBinding
      setupEvents(binding, subject)
      bindUi(holder)
    }

    private fun setupEvents(
        binding: ListItemOverduePatientBinding,
        eventSubject: Subject<UiEvent>
    ) {
      binding.callButton.setOnClickListener {
        eventSubject.onNext(CallPatientClicked(patientUuid))
      }

      binding.overdueCardView.setOnClickListener {
        eventSubject.onNext(OverduePatientClicked(patientUuid))
      }
    }

    private fun bindUi(holder: BindingViewHolder) {
      val binding = holder.binding as ListItemOverduePatientBinding
      val context = holder.itemView.context

      binding.patientNameTextView.text = context.getString(R.string.overdue_list_item_name_age, name, age.toString())
      binding.patientGenderIcon.setImageResource(gender.displayIconRes)
      binding.villageTextView.text = villageName.orEmpty()
      binding.villageTextView.visibleOrGone(isVisible = !villageName.isNullOrBlank())

      val callButtonDrawable = if (phoneNumber.isNullOrBlank()) {
        R.drawable.ic_overdue_no_phone_number
      } else {
        R.drawable.ic_overdue_call
      }
      binding.callButton.setImageResource(callButtonDrawable)
      increaseCallButtonTapArea(callButton = binding.callButton)

      binding.isAtHighRiskTextView.visibility = if (isAtHighRisk) View.VISIBLE else View.GONE

      binding.overdueDaysTextView.text = context.resources.getQuantityString(
          R.plurals.overdue_list_item_appointment_overdue_days,
          overdueDays,
          "$overdueDays"
      )
    }

    private fun increaseCallButtonTapArea(callButton: View) {
      val parent = callButton.parent as View

      parent.executeOnNextMeasure {
        val touchableArea = Rect()
        callButton.getHitRect(touchableArea)

        val buttonHeight = callButton.height
        val parentHeight = parent.height

        val verticalSpace = (parentHeight - buttonHeight) / 2
        val horizontalSpace = 24.dp

        with(touchableArea) {
          left -= horizontalSpace
          top -= verticalSpace
          right += horizontalSpace
          bottom += verticalSpace
        }

        parent.touchDelegate = TouchDelegate(touchableArea, callButton)
      }
    }
  }

  data class OverdueSectionHeader(
      @StringRes val headerText: Int,
      val count: Int
  ) : OverdueAppointmentListItemNew() {
    override fun layoutResId(): Int = R.layout.list_item_overdue_list_section_header

    override fun render(holder: BindingViewHolder, subject: Subject<UiEvent>) {
      val binding = holder.binding as ListItemOverdueListSectionHeaderBinding

      binding.overdueSectionHeaderTextView.setText(headerText)
      binding.overdueSectionHeaderIcon.text = count.toString()
      // TO-DO handle chevron right and down icon here when handling the collapse
    }
  }

  object PendingListFooter : OverdueAppointmentListItemNew() {
    override fun layoutResId(): Int = R.layout.list_item_overdue_pending_list_footer

    override fun render(holder: BindingViewHolder, subject: Subject<UiEvent>) {
      val binding = holder.binding as ListItemOverduePendingListFooterBinding

      binding.overduePendingSeeAllOrLessButton.setOnClickListener {
        subject.onNext(PendingListFooterClicked)
      }
    }
  }

  object NoPendingPatients : OverdueAppointmentListItemNew() {

    override fun layoutResId(): Int = R.layout.list_item_no_pending_patients

    override fun render(holder: BindingViewHolder, subject: Subject<UiEvent>) {
      /* no-op */
    }
  }

  class DiffCallback : DiffUtil.ItemCallback<OverdueAppointmentListItemNew>() {
    override fun areItemsTheSame(
        oldItem: OverdueAppointmentListItemNew,
        newItem: OverdueAppointmentListItemNew
    ): Boolean {
      return when {
        oldItem is OverdueAppointmentRow && newItem is OverdueAppointmentRow -> oldItem.patientUuid == newItem.patientUuid
        oldItem is OverdueSectionHeader && newItem is OverdueSectionHeader -> oldItem.headerText == newItem.headerText
        else -> false
      }
    }

    override fun areContentsTheSame(
        oldItem: OverdueAppointmentListItemNew,
        newItem: OverdueAppointmentListItemNew
    ): Boolean {
      return oldItem == newItem
    }
  }

  object Divider : OverdueAppointmentListItemNew() {

    override fun layoutResId(): Int = R.layout.list_item_divider

    override fun render(holder: BindingViewHolder, subject: Subject<UiEvent>) {
      /* no-op */
    }
  }
}
