package org.simple.clinic.patientcontact

import java.util.UUID

sealed class PatientContactEffect

data class LoadPatientProfile(val patientUuid: UUID): PatientContactEffect()

data class LoadLatestOverdueAppointment(val patientUuid: UUID): PatientContactEffect()