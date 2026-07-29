# Seeds a demo-ready clinic through the PUBLIC API (never direct SQL), so the
# data that appears is data the system actually accepts: validation, events,
# and authorization all run exactly as they would for a real user.
#
#   .\scripts\seed-demo.ps1
#   .\scripts\seed-demo.ps1 -Gateway http://localhost:8080 -AdminEmail admin@careconnect.local -AdminPassword 'ChangeMe123'
#
# Prerequisite: identity-service seeded an admin (SEED_ADMIN_* in .env).

param(
    [string]$Gateway = "http://localhost:8080",
    [string]$AdminEmail = "admin@careconnect.local",
    [string]$AdminPassword = "Admin12345",
    [string]$DoctorPassword = "Doctor12345",
    [string]$PatientPassword = "Patient12345"
)

$ErrorActionPreference = "Stop"
$stamp = Get-Date -Format "HHmmss"

function Invoke-Api {
    param($Method, $Path, $Body, $Token)
    $headers = @{ "Content-Type" = "application/json" }
    if ($Token) { $headers["Authorization"] = "Bearer $Token" }
    $json = if ($Body) { $Body | ConvertTo-Json -Depth 6 } else { $null }
    return Invoke-RestMethod -Method $Method -Uri "$Gateway$Path" -Headers $headers -Body $json
}

Write-Host "1/6 Signing in as admin..." -ForegroundColor Cyan
$admin = Invoke-Api POST "/api/auth/login" @{ email = $AdminEmail; password = $AdminPassword }
$adminToken = $admin.data.accessToken

Write-Host "2/6 Creating a doctor account..." -ForegroundColor Cyan
$doctorEmail = "dr.rao.$stamp@careconnect.local"
$doctorUser = Invoke-Api POST "/api/users" @{
    email = $doctorEmail; password = $DoctorPassword; roles = @("DOCTOR")
} $adminToken

Write-Host "3/6 Creating the doctor profile + weekly availability..." -ForegroundColor Cyan
$departments = Invoke-Api GET "/api/providers/departments" $null $adminToken
$cardiology = ($departments.data | Where-Object { $_.name -eq "Cardiology" } | Select-Object -First 1)
$doctor = Invoke-Api POST "/api/providers/doctors" @{
    firstName = "Nisha"; lastName = "Rao"; specialty = "Cardiology"
    departmentId = $cardiology.id; consultationFee = 800
    email = $doctorEmail; userId = $doctorUser.data.id
} $adminToken

# Mon-Fri, 09:00-13:00 and 14:00-17:00, 30-minute slots
$slots = @()
foreach ($day in 1..5) {
    $slots += @{ dayOfWeek = $day; startTime = "09:00:00"; endTime = "13:00:00"; slotMinutes = 30 }
    $slots += @{ dayOfWeek = $day; startTime = "14:00:00"; endTime = "17:00:00"; slotMinutes = 30 }
}
Invoke-Api PUT "/api/providers/doctors/$($doctor.data.id)/availability" @{ slots = $slots } $adminToken | Out-Null

Write-Host "4/6 Registering a patient and completing their profile..." -ForegroundColor Cyan
$patientEmail = "asha.$stamp@example.dev"
$patient = Invoke-Api POST "/api/auth/register" @{ email = $patientEmail; password = $PatientPassword }
$patientToken = $patient.data.accessToken
Invoke-Api POST "/api/patients/me" @{
    firstName = "Asha"; lastName = "Verma"; dateOfBirth = "1990-04-12"
    gender = "FEMALE"; phone = "9876512345"; emergencyContactName = "Ravi Verma"
} $patientToken | Out-Null

Write-Host "5/6 Booking the next available slot..." -ForegroundColor Cyan
$date = (Get-Date).AddDays(1)
while ($date.DayOfWeek -eq "Saturday" -or $date.DayOfWeek -eq "Sunday") { $date = $date.AddDays(1) }
$dateString = $date.ToString("yyyy-MM-dd")
$available = Invoke-Api GET "/api/appointments/available?doctorId=$($doctor.data.id)&date=$dateString" $null $patientToken
if (-not $available.data -or $available.data.Count -eq 0) {
    throw "No free slots returned for $dateString - is provider-service healthy?"
}
$appointment = Invoke-Api POST "/api/appointments" @{
    doctorId = $doctor.data.id; startAt = $available.data[0].startAt; reason = "Routine check-up"
} $patientToken

Write-Host "6/6 Confirming and completing the visit (fires the event chain)..." -ForegroundColor Cyan
Invoke-Api POST "/api/appointments/$($appointment.data.id)/confirmation" @{} $adminToken | Out-Null
Invoke-Api POST "/api/appointments/$($appointment.data.id)/completion" @{} $adminToken | Out-Null

Write-Host ""
Write-Host "Demo data ready." -ForegroundColor Green
Write-Host "  Admin    : $AdminEmail / $AdminPassword"
Write-Host "  Doctor   : $doctorEmail / $DoctorPassword"
Write-Host "  Patient  : $patientEmail / $PatientPassword"
Write-Host ""
Write-Host "The completed visit just published AppointmentCompleted. Within a second:"
Write-Host "  - medical-record-service opened an encounter (doctor: Charts)"
Write-Host "  - billing-service issued an invoice     (patient: Invoices)"
Write-Host "  - notification-service sent two emails  (docker logs careconnect-notification-service)"
