# Mahalakshmi Dental Clinic App – Project Notes (updated 2 Nov 2026)

For Claude: read this first. Ask for the current version of any file before changing it.
Rules: never send clinic.keystore; send only the files a change needs.

## Basics
- Repo: omkarmaturkar/Mahalakshmi-Dental-Clinic-V2 (private). Package com.mahalakshmi.dental
- Kotlin files: app/src/main/java/com/mahalakshmi/dental/
- Current version: 19.0 (versionCode 19)
- Jetpack Compose, Kotlin 2.0.21, AGP 8.7.3, Gradle 8.11.1, minSdk 26, target/compile 35
- Extra libraries: androidx.biometric 1.1.0, fragment-ktx 1.8.5, work-runtime-ktx 2.9.1, play-services-auth 21.2.0
- Plain SQLite (SQLiteOpenHelper, DB version 4). Own back stack of a Screen sealed class.
- MainActivity extends FragmentActivity. Content: Splash → LockGate { TrialGate(db) } → ClinicApp

## Building (.github/workflows)
- build.yml – Build APK. Inputs: "Trial days" (0 = full version) and "Your contact".
  Writes TrialConfig.kt (TRIAL_UNTIL, TRIAL_CONTACT) at build time. Artifact name shows FULL or TRIAL-until-date.
- recolor.yml, darkmode.yml, teeth.yml, security.yml – one-time tools, already run (running again fails safely).
- Signing: app/clinic.keystore, alias "clinic" (passwords in app/build.gradle.kts).

## Database (version 4)
patients, visits (old), payments, settings, prescriptions, rx_templates, treatments, case_notes, case_photos, appointments, doctors.
- treatments.status: 0 planned, 1 ongoing, 2 done. appointments.status: 0 booked, 1 done, 2 cancelled.

## Files
- MainActivity.kt – Screen, ClinicApp (drawer + floating bottom bar; tablet ≥900dp permanent menu), TrialGate, NavArea (contentKey "tabs"), ScreenHost
- SwipeTabs.kt – TabPager: swipe between Home, Patients, Schedule, Finance; tabIndexOf()
- Theme.kt – AppTheme (Light/Dark/Auto, saved), Brand colours as getters (Surface, Night…), ThemeToggle, AppearanceRow, building blocks
- Menu.kt – AppMenu (Appearance + App lock under Settings), SectionTopBar, BottomNavBar (dark floating pill)
- TodayScreen.kt – Home "Spotlight": greeting + dark mode button, swipeable next-patient card (all patients of the next clinic day, dots), 4 metric cards, quick actions, recalls, PatientPickerDialog
- DentalChart.kt – real tooth shapes in an arch: StatusChart (patient file, coloured by status) and ToothChart (choose teeth). Old ones renamed OldStatusChart / OldToothChart.
- Security.kt – AppLock (4-digit PIN salted SHA-256, fingerprint, lock after 0/1/5 min, Forgot PIN via phone screen lock), LockGate, AppLockRow; AutoBackup + DriveBackupWorker
- Trial.kt – Trial (expiry, clock-rollback protection, internet time check), TrialEndedScreen (still allows backup), TrialStrip (last 5 days)
- FinanceScreen.kt – periods + custom date or range (calendar button, DateRangePicker)
- BackupScreen.kt – Drive + folder backup, live "Automatic" status line
- PatientsScreen.kt, PatientScreens.kt (patient file), PatientEdit.kt, PatientSheets.kt (dialogs), PaymentEdit.kt, CaseData.kt, ScheduleScreen.kt, Prescription.kt, Bills.kt, Pdf.kt, ClinicSettings.kt, Database.kt, ExtraTables.kt, Backup.kt, DriveBackup.kt, CrashGuard.kt, SplashScreen.kt, VisitScreen.kt (old), HomeScreen.kt (old, unused)

## Automatic backup
- On every close, if the database file changed since the last backup:
  folder backup straight away; Google Drive straight away + WorkManager safety copy ("drive-backup", 2 min delay, waits for internet).
- Backup screen shows status: Backing up / Up to date / Waiting for internet / Sign in needed / error text.
- Google Drive manual backup confirmed working.

## PDFs
Professional layout: white letterhead, faint big logo watermark, patient + document boxes, bordered tables,
amount in words + totals box, signature bottom-right of last page. Invoice, receipt, estimate, prescription. A4/A5.

## Clinic
Mahalakshmi Dental Clinic, Gajanan Colony, Angur Bagicha Road, Gondia · Mon–Sat 10 AM–8 PM · Dr. Tushar Maturkar, MBBS, BDS

## Decided / discussed
- Windows app: discussed (Compose Multiplatform; live sync would need a cloud database) – NOT wanted for now.
- UI refresh of the other screens: pending – choose (1) match everything to Home, (2) new look, or (3) specific screens.
- Next feature ideas: UPI QR on bills, treatment follow-up tracker, prescription safety check, lab work tracker,
  expenses + profit report, end-of-day summary, consent forms with signature, Marathi/Hindi prescriptions,
  receptionist mode, Google review requests, in-app logo setup for selling to other clinics.
