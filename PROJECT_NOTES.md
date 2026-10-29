# Mahalakshmi Dental Clinic App – Project Notes

For Claude: read this first. It describes the whole app. Before changing any file, ask for its current version.

## Basics
- Repo: omkarmaturkar/Mahalakshmi-Dental-Clinic-V2 (private). Old V1: omkarmaturkar/Mahalakshmi-Dental-Clinic (public).
- Package: com.mahalakshmi.dental. All Kotlin files: app/src/main/java/com/mahalakshmi/dental/
- Current version: 14.0 (versionCode 14)
- Jetpack Compose, Kotlin 2.0.21, AGP 8.7.3, Gradle 8.11.1, minSdk 26, target/compile SDK 35
- No Room: plain SQLite (SQLiteOpenHelper). No Navigation library: own back stack of a `Screen` sealed class.
- Fonts: Poppins regular/medium/semibold/bold (R.font.poppins_*), downloaded by the build workflow.
- Logo: R.drawable.clinic_logo, copied by the workflow from "Clinic Logo Image.png" in the repo root.
- Google Play Services Auth for Google Drive backup.

## Building
- .github/workflows/build.yml – manual (Run workflow). Downloads Gradle, fonts and logo, builds a signed release APK. Artifact: Mahalakshmi-Dental-APK. The log has a "Show SHA-1" step.
- .github/workflows/recolor.yml – one-time tool that turned the old teal into clinic green (already run in v14).
- Signing: app/clinic.keystore, alias "clinic" (passwords are in app/build.gradle.kts).
- Raise versionCode and versionName in app/build.gradle.kts for every release.

## Database (version 4)
Tables: patients, visits (old, kept), payments, settings, prescriptions, rx_templates, treatments, case_notes, case_photos, appointments, doctors.
- treatments: id, patient_id, name, teeth, fee, status (0 planned, 1 ongoing, 2 done), doctor_id, notes, created_at, done_at, legacy_visit
- appointments: id, patient_id, day (epoch day), time, reason, status (0 booked, 1 done, 2 cancelled), created_at
- doctors: id, name, qualification, specialty, reg_no, phone, on_bills, main, visiting, share_percent, active
- Database.kt onOpen() re-creates missing tables (createExtraTables) and converts old visits into treatments once (settings key visits_moved=1).

## Files
- MainActivity.kt – Screen sealed class, ClinicApp (phone: drawer + bottom bar; tablet ≥900dp: permanent left menu), ScreenHost
- Menu.kt – AppMenu, SectionTopBar, BottomNavBar, LocalMenu
- TodayScreen.kt – Home: header with logo, greeting, search, 4 stat cards, quick actions, today's appointments, recalls (HomeColors)
- PatientsScreen.kt – patient list, search, filters All / Dues / Today
- PatientScreens.kt – PatientFileScreen (tabs), PatientHeader, treatment and payment cards
- PatientEdit.kt – patient form, tenDigits(), 10-digit mobile check, duplicate phone warning
- PatientSheets.kt – StatusChart, TreatmentDialog, ReceivePaymentDialog, BookingDialog, NoteDialog, PhotoOptionsDialog, BeforeAfterDialog
- PaymentEdit.kt – EditPaymentDialog, updatePayment()
- CaseData.kt – Treatment, Payment, CaseNote, CasePhoto, Booking, Account; all CRUD; buildHistory(); priceFor() / rememberPrice()
- ScheduleScreen.kt – next 30 days of appointments; DayBooking, bookingsBetween(), appointmentReminder()
- FinanceScreen.kt – collections by period, payment mode and doctor; payments list
- Prescription.kt – Medicine, Prescription, RxTemplate; PrescriptionScreen; prescriptionPdf()
- Bills.kt – invoicePdf(), paymentReceiptPdf(), estimatePdf(), amountInWords(), rupees2()
- Pdf.kt – PdfBuilder, PdfCell, SummaryLine; WhatsApp / print / share; PdfActionsDialog; DocIcons; letterheadSamplePdf()
- ClinicSettings.kt – ClinicInfo, clinicInfo(), saveClinicInfo(), doctors CRUD, ClinicSettingsScreen
- ExtraTables.kt – creates newer tables; deleteVisitExtras(), deletePatientExtras()
- CrashGuard.kt – saves last crash to last_crash.txt; CrashReportScreen
- Database.kt; BackupScreen.kt (Google Drive backup); VisitScreen.kt (old visit form); HomeScreen.kt (old home, unused)
- res/xml/file_paths.xml – includes cache-path "pdf/" for sharing PDFs

## Screens (Screen → composable)
Home → TodayScreen · Schedule → ScheduleScreen · Finance → FinanceScreen · Patients(filter, focusSearch) → PatientsScreen · PatientForm(patientId) → PatientEditScreen · PatientDetail(patientId) → PatientFileScreen · VisitForm(patientId) → PatientFileScreen(startAdd = true) · RxForm(patientId, rxId) → PrescriptionScreen · BackupPage → BackupScreen · ClinicSettings → ClinicSettingsScreen

## Look
- Clinic green on every screen (Brand.Green, Brand.GreenDeep, Brand.GreenSoft), plus Brand.Charcoal, Brand.Gold, Brand.Red; Brand.Ink / Sub / Soft for text and backgrounds. Home teal replaced by green in v14.
- Poppins font throughout.

## PDFs (professional layout since v13)
- White letterhead: logo, clinic name, doctors, reg. no., contacts; green + gold double line; big faint logo watermark in the centre.
- Title band (INVOICE etc.), Patient details + document details boxes, bordered tables with green header, summary box (amount in words left, totals right), payment details table, notes, signature fixed bottom-right of the last page ("For <clinic>", doctor, reg. no., Authorised Signatory), footer with page number.
- PdfBuilder: title, partyBox, patientBox, table, row, summary, heading, notes, text, space, signature, save. Stamps are switched off.
- Documents: Invoice, Payment receipt, Estimate, Prescription, Letterhead sample. A4 or A5 (set in Clinic settings).

## Clinic
Mahalakshmi Dental Clinic, Gajanan Colony, Angur Bagicha Road, Gondia · Mon–Sat 10 AM–8 PM · Dr. Tushar Maturkar, MBBS, BDS

## Google Drive backup (still pending)
- Uses the original Google Cloud project; Android OAuth client for com.mahalakshmi.dental with the SHA-1 from the build log.
- Shows "refused access". Fix: enable Google Drive API; add scope https://www.googleapis.com/auth/drive.file (OAuth consent screen → Data Access); publish the app or add test users; disconnect and reconnect in the app.

## Planned features (not built yet)
Top picks: lost-revenue tracker (advised but not started treatments + WhatsApp follow-up), voice notes, prescription safety check against medical alerts, receptionist mode + app lock (PIN / fingerprint), UPI QR on bills, expenses + profit report + Excel export.
Others: appointment time slots, lab work tracker, instalment plans, consent forms with finger signature, Marathi / Hindi prescriptions, marking on X-rays and photos, end-of-day WhatsApp summary, birthday wishes + Google review request, case presentation PDF (option A vs B), family accounts, certificates (leave / fitness / referral), smart recalls by treatment, tooth passport, implant record, sterilization register, kids' teeth chart (51–85).

## Working with Claude
- Start each chat with this file, then add only the files the change needs.
- Claude sends complete files to paste on GitHub → Run workflow → download APK → install.
