# Mahalakshmi Dental Clinic App – Project Notes

> Paste this whole file at the start of any new chat with Claude so it knows the project.
> Last updated: 2 Nov 2026 · Version 24.0

---

## 1. Quick facts

| | |
|---|---|
| Repo (private) | https://github.com/omkarmaturkar/Mahalakshmi-Dental-Clinic-V2 |
| Package | `com.mahalakshmi.dental` |
| Version | 24.0 (versionCode 24) |
| Tech | Kotlin 2.0.21 · Jetpack Compose · AGP 8.7.3 · Gradle 8.11.1 · minSdk 26 · target/compile 35 |
| Extra libraries | androidx.biometric 1.1.0 · fragment-ktx 1.8.5 · work-runtime-ktx 2.9.1 · play-services-auth 21.2.0 |
| Database | Plain SQLite (`ClinicDb` extends `SQLiteOpenHelper`, DB version 4). No Room. |
| Navigation | Own back stack of a `Screen` sealed class (no Navigation library) |
| Main activity | `MainActivity` extends `FragmentActivity` (needed for fingerprint) |
| Offline | Everything works offline. Internet is only used for Google Drive backup and WhatsApp. |
| Kotlin files | `app/src/main/java/com/mahalakshmi/dental/` |

## 2. Clinic details
- Mahalakshmi Dental Clinic, Gajanan Colony, Angur Bagicha Road, Gondia
- Mon–Sat 10 AM–8 PM · Dr. Tushar Maturkar, MBBS, BDS · Phone 8888882345

## 3. Building the app
- **build.yml** (Actions → Build APK → Run workflow). Inputs: *Trial days* (0 = full version) and *Your contact*. It writes `TrialConfig.kt` (TRIAL_UNTIL, TRIAL_CONTACT) at build time. Artifact name: FULL or TRIAL-until-date.
- Signing: `app/clinic.keystore`, alias `clinic`, passwords in `app/build.gradle.kts`. **Never share the keystore.**
- One-time tools in `.github/workflows/` have all been run already. **Do not run them again.**
  - Older tools: recolor, darkmode, teeth, security.
  - New tools: sittings, perio, surfaces, braces, education. Each one inserted a card into `PatientScreens.kt`, raised the version and started a build.
- **Finding build errors:** open the failed run → job **build** → step **Build the APK** → search `e: file:///`. Each line gives the file, the line number and the problem.

## 4. Files

**Core & navigation**
| File | What's in it |
|---|---|
| MainActivity.kt | Screen sealed class, ClinicApp (drawer + floating bottom bar; tablet ≥900dp permanent menu), TrialGate, NavArea (contentKey "tabs"), ScreenHost |
| SwipeTabs.kt | TabPager: swipe between Home/Patients/Schedule/Finance; tabIndexOf() |
| Theme.kt | AppTheme (Light/Dark/Auto), Brand colours (getters), ThemeToggle, AppearanceRow, all building blocks (see section 9) |
| Menu.kt | AppMenu (Appearance + App lock under Settings), SectionTopBar, BottomNavBar (dark pill with animated NavPill) |
| SplashScreen.kt | Logo animation with shine and sparkle |
| CrashGuard.kt | Saves last_crash.txt; CrashReportScreen |

**Home, lists, money**
| File | What's in it |
|---|---|
| TodayScreen.kt | "Spotlight" home: greeting + ThemeToggle, swipeable next-patient card, 4 metric cards, quick actions, recalls. PatientPickerDialog. HomeColors (dark-mode aware) |
| PatientsScreen.kt | All patients with search and filters (All/Dues/Today); DueCard with remind button |
| ScheduleScreen.kt | Next 30 days of appointments; DayBooking, bookingsBetween(), appointmentReminder() |
| FinanceScreen.kt | Today/This week/This month/Last month + custom range (DateRangePicker); paymentsBetween(), billedByDoctor() |

**Patient file**
| File | What's in it |
|---|---|
| PatientScreens.kt | PatientFileScreen (tabs on phone, two columns on tablet), PatientHeader, treatment/payment cards, PaymentRow |
| PatientEdit.kt | Patient form, tenDigits(), 10-digit mobile check, duplicate phone warning |
| PatientSheets.kt | TreatmentDialog, ReceivePaymentDialog, BookingDialog, NoteDialog, PhotoOptionsDialog, BeforeAfterDialog, OldStatusChart |
| PaymentEdit.kt | EditPaymentDialog, updatePayment(id, amount, mode, date) |
| CaseData.kt | Treatment, Payment, CaseNote, CasePhoto, Booking, Account; all CRUD; buildHistory(); priceFor()/rememberPrice(); doctorNames() |
| DentalChart.kt | Real tooth shapes: StatusChart (patient file) and ToothChart (choose teeth) |
| VisitScreen.kt | Old visit form (legacy), OldToothChart, DatePick(), withDay() |
| Prescription.kt | Medicine, Prescription, RxTemplate, PrescriptionScreen, prescriptionPdf() |

**Clinical modules (added Nov 2026)**
| File | What's in it |
|---|---|
| Sittings.kt | Stage 1: treatments done over several sittings |
| Perio.kt | Stage 2: gum (perio) chart |
| MouthStatus.kt | Stage 3: mouth status (tooth conditions and surfaces) |
| Ortho.kt | Stage 4: braces case sheet |
| Education.kt | Stage 5: patient education library |

**Bills, PDFs, settings**
| File | What's in it |
|---|---|
| Bills.kt | invoicePdf(), paymentReceiptPdf(ctx, db, paymentId), estimatePdf(), amountInWords(), rupees2() |
| Pdf.kt | PdfBuilder, PdfCell, SummaryLine; WhatsApp/print/share; PdfActionsDialog; DocIcons; letterheadSamplePdf(). White letterhead, faint watermark, partyBox, bordered tables, summary box, signature bottom-right |
| ClinicSettings.kt | ClinicInfo, clinicInfo(), saveClinicInfo(), doctors CRUD, ClinicSettingsScreen, DEFAULT_CLINIC_NAME |

**Data, backup, security**
| File | What's in it |
|---|---|
| Database.kt | ClinicDb; onOpen() calls createExtraTables() + visits conversion; patientCount(), stats(), upcoming() |
| ExtraTables.kt | createExtraTables(), deleteVisitExtras(), deletePatientExtras() |
| Backup.kt | Folder backup (autoBackup, exportZip, restoreZip, restoreFromFolder) |
| DriveBackup.kt | Google Drive backup (drive.file scope): backup, restore, silentToken, markConnected, latestBackupDate, autoBackup |
| BackupScreen.kt | Drive + folder backup with a live status line (updates every 2 s) |
| Security.kt | AppLock (PIN + fingerprint), LockGate, AppLockRow; AutoBackup + DriveBackupWorker |
| Trial.kt / TrialConfig.kt | Trial system (TrialConfig.kt is written by build.yml) |
| HomeScreen.kt | Old home (unused, kept for reference) |

## 5. Database

**Core tables (DB version 4):** patients, visits (old), payments, settings, prescriptions, rx_templates, treatments, case_notes, case_photos, appointments, doctors.
- treatments.status: 0 planned · 1 ongoing · 2 done
- appointments.status: 0 booked · 1 done · 2 cancelled
- doctors: id, name, qualification, specialty, reg_no, phone, on_bills, main, visiting, share_percent, active

**Feature tables.** Each feature file creates its own tables on first use (`CREATE TABLE IF NOT EXISTS`). They are not in ExtraTables.kt, and the DB version did not change.

| Table | File | Columns |
|---|---|---|
| sittings | Sittings.kt | id, treatment_id, patient_id, position, name, fee, done, done_at, payment_id, notes |
| perio_exams | Perio.kt | id, patient_id, date, data (JSON per tooth), notes |
| tooth_status | MouthStatus.kt | key (patient_id, tooth); decay, filled, crown, rct, implant, missing, broken, note, updated_at |
| ortho_cases | Ortho.kt | id, patient_id, start_date, months, status, complaint, bite, profile, overjet, overbite, crowding, findings, extraction, appliance, plan, fee, down_payment, monthly, paid_before, treatment_id, created_at |
| ortho_visits | Ortho.kt | id, case_id, patient_id, date, upper_wire, lower_wire, elastics, notes |
| ortho_payments | Ortho.kt | key (case_id, payment_id); links braces payments to the case |

- All tables are in the same database file, so backups and restores include them automatically.
- **Perio JSON per tooth:** pd[6], bop[6] (0/1), rc/rt (recession cheek/tongue, −1 = none), mob, fur, mis. Spots 0–2 are the cheek side (back, middle, front); spots 3–5 are the palate/tongue side.
- **Surfaces in tooth_status:** M (front), O (biting; shown as I on front teeth), D (back), B (cheek), L (tongue/palate). Example: "MO". Healthy teeth have no row.
- **Shared preferences:** "appearance" (theme), app lock settings, "education" → "lang" (0 English, 1 Hindi, 2 Marathi).

## 6. Screens & navigation
- Home→TodayScreen · Patients(filter, focusSearch)→PatientsScreen · Schedule→ScheduleScreen · Finance→FinanceScreen · PatientForm(id)→PatientEditScreen · PatientDetail(id)→PatientFileScreen · VisitForm(id)→PatientFileScreen(startAdd=true) · RxForm(patientId, rxId)→PrescriptionScreen · BackupPage→BackupScreen · ClinicSettings→ClinicSettingsScreen
- TabPager wraps Home/Patients/Schedule/Finance for swiping. contentKey "tabs" stops the tabs re-animating.

**Patient file → Treatments tab, top to bottom:**
TreatmentProgress → OrthoCard → treatments card (teeth chart) → MouthStatusCard → PerioCard → EducationCard
- Phone line in PatientScreens.kt: `0 -> { TreatmentProgress(db, patientId); OrthoCard(db, patientId); treatmentsCard(); MouthStatusCard(db, patientId); PerioCard(db, patientId); EducationCard(db, patientId) }`
- Tablet: the same order, as separate lines after `header()` and `actions()` in the left column.
- The gum chart, mouth status, braces case sheet and education library open as **full-screen Dialogs**, so MainActivity didn't need changes.

## 7. Design
- "Spotlight" home: dark next-patient card, light metric cards, dark floating bottom bar.
- Brand colours are getters that switch with dark mode: Ink, Sub, Faint, Line, Soft, Surface (white / dark card), Night, GreenDeep, GreenText, GreenSoft, GreenLine, Gold, GoldDeep, GoldSoft, Red, RedSoft. Use `Brand.Surface` instead of `Color.White`.
- The owner prefers **design A: clean, light cards that are easy to read**. Show a preview before building any new screen and let him choose.
- Charts and education pictures use fixed illustration colours on a light panel, even in dark mode.

## 8. Features

### Before Nov 2026 (up to v19)
1. **Trial system.** build.yml sets TRIAL_UNTIL.
   - Trial.kt checks expiry, with clock-rollback protection (highest time seen) and internet time from google.com.
   - When the trial ends, TrialEndedScreen replaces the app (backup still works).
   - TrialStrip warns during the last 5 days.
2. **App lock.**
   - 4-digit PIN (salted SHA-256) and fingerprint.
   - Lock after 0/1/5 minutes.
   - Forgot PIN uses the phone's own screen lock.
   - 5 wrong tries → 30 s wait.
3. **Instant backup.**
   - AutoBackup.onAppClosed() runs in onStop() when the database has changed.
   - Folder backup runs immediately. Drive backup runs immediately, with a WorkManager safety copy after 2 minutes.
4. **Finance date picker.** The calendar icon opens a DateRangePicker. Tap one date for a single day, or two dates for a range. A "Custom" chip appears.
5. **Swipeable next-patient card.** Shows all patients of the next booked day, with page dots, balance/alert chips, and Call/WhatsApp/Start buttons.
6. **Dark mode everywhere.** Light/Dark/Auto, saved in "appearance". ThemeToggle on Home, Appearance row in the menu.
7. **Professional PDFs (v13+).**
   - White letterhead with a faint logo watermark.
   - Patient/invoice box and bordered tables.
   - Amount in words with totals; signature bottom-right.
8. **Real teeth chart** (DentalChart.kt) and **swipe tabs**.

### Stage 1 – Multi-sitting tracker (v20) · Sittings.kt
- `sittingTemplate(name)`:
  - Root canal: 4 sittings (opening, cleaning canals, filling canals, crown)
  - Implant: 4 · Denture: 4 · Crown/Cap/Bridge: 2
  - Braces were removed in v23; the braces case sheet handles them.
- `TreatmentProgress(db, patientId)`:
  - A gold "Track" suggestion for matching treatments.
  - A timeline card for the active treatment, with dates, paid amounts and the next booking.
  - Collapsed cards for other tracked treatments.
  - "+ Track another treatment in sittings" (adds 2 generic sittings).
- **Mark sitting done:**
  - Asks for the date and an optional payment (`addPayment`, linked through `payment_id`).
  - Then offers the receipt (PdfActionsDialog) and BookingDialog, with the reason "Treatment: next sitting".
- `syncTreatmentWithSittings`: first sitting done → In progress; all done → Done.
- Fee/Paid/Left on the card counts only payments taken at sittings.

### Stage 2 – Gum (perio) chart (v21) · Perio.kt
- **PerioCard** shows the latest summary (deep pockets, bleeding %, was → now), the last 5 check-ups and a "New check-up" button.
- **New check-ups start with missing teeth already marked:** teeth missing in the previous check-up, done extractions, and teeth marked missing in Mouth status.
- **PerioScreen:**
  - Upper/Lower teeth with a chart that scrolls along by itself.
  - Keypad 1–12 plus Back/Clear/Next. The cursor follows the probing order: cheek side left→right, then palate/tongue side right→left; upper, then lower.
  - "Mark bleeding" mode: tap spots to add red dots.
  - Tooth dialog: mobility 0–3, furcation (molars), recession, missing.
  - ▲/▼ when a tooth is 2 mm or more worse/better than last time.
  - Saves 0.7 s after each change. Date can be changed; the chart can be deleted; WhatsApp sends a text summary.
- `PERIO_UPPER` / `PERIO_LOWER` tooth lists are public and reused by MouthStatus.kt.

### Stage 3 – Mouth status (v22, working from v23) · MouthStatus.kt
- **MouthStatusCard** shows chips (Decay, Filled, Crown, Root canal, Implant, Missing, Broken) and opens **MouthStatusScreen**.
- Odontogram: each tooth is a circle with 5 surfaces, in two scrolling rows.
- **Tooth panel:**
  - A big surface picker; tap a surface to mark it.
  - A Decay / Filling switch chooses what tapping marks.
  - Toggles: Crown, Root canal done, Implant, Missing, Broken. Plus a note and "Clear this tooth".
- **Add treatment** creates a planned treatment using `priceFor()`, with a duplicate check:
  - Decay → "Filling (MO)"
  - Broken → "Crown"
  - Missing → "Implant"
- The surfaces are in the treatment name, so they appear on estimates and bills.

### Stage 4 – Braces case sheet (v23) · Ortho.kt
- **OrthoCard** appears only when a case exists, or when the patient has a treatment containing brace/ortho/aligner. "Start" creates the case, linked to that treatment's fee and start date.
- **The case sheet records:**
  - Status (Planning/Active/Retention/Finished), start date and planned months ("Month X of N").
  - Diagnosis: main concern, bite class, profile, crowding, overjet, overbite, findings.
  - Plan: extractions, appliance, notes.
- **Visits:** upper/lower wire (11 common wires; last visit's wires pre-selected), elastics and notes. A new visit marks the patient arrived and can book the next "Braces adjustment".
- **Fees:** total, down payment, monthly and paid earlier → `orthoSchedule()` lists each instalment as paid, part paid, due, overdue or upcoming.
  - "Receive payment" links the payment to the case and offers the receipt.
  - "Remind" sends a WhatsApp reminder.
  - "Add the braces fee to the bill" creates an ongoing treatment.
  - Changing the fee updates the linked treatment.
- **Progress photos:** photos from case_photos since 30 days before the start date. The case sheet can be deleted.

### Stage 5 – Patient education library (v24) · Education.kt
- **EducationCard** opens **EducationScreen**, with 8 topics of 3–4 steps each: cavity, root canal, tooth removal, crown, implant, braces, gum disease, wisdom tooth.
- Pictures are drawn in code (`EduScene` in a 200 × 170 design box), so they need no internet and almost no space.
- Text in English, Hindi and Marathi. Topics matching the patient's open treatments come first, with a gold dot.
- Swipe between steps. **Draw** mode allows red finger drawing, with Clear. **Send** puts all steps on WhatsApp in the chosen language.
- The Hindi and Marathi wording should be checked by the doctor.

## 9. Shared building blocks (signatures confirmed by working builds)
- `AppCard(modifier, onClick = …, padding = …) { … }` · `Pill(text, bg, fg, icon?)`
- `SoftButton(text, icon?, modifier, bg =, fg =, height =) { … }` · `GradientButton(text, modifier, icon?, enabled =, height =) { … }`
- `AppField(value, onChange, label, modifier =, type =, lines =, caps =, prefix =)`
- `ChoiceChip(text, selected) { … }` · `ToggleRow(title, subtitle, checked) { … }`
- `ConfirmDialog(title, message, confirmText =, onConfirm =, onDismiss =)` · `CircleIconButton(icon, onClick, bg =, tint =, size =)`
- `DatePick(epochDay, onDismiss =) { day -> }` · `BookingDialog(initial, defaultReason, onDismiss, onCancelBooking) { day, time, reason -> }`
- `PdfActionsDialog(title, subtitle, phone, message, build = { ctx -> … }, onDismiss)`
- **Helpers:**
  - Formatting: `rs()`, `numText()`, `fmtDate(ms)`, `fmtDay(epochDay)`
  - Dates: `todayEpochDay()`, `epochDayOf(ms)`, `withDay(ms, day)`
  - App: `BrandGradient`, `LocalNotify.current("message")`, `DataBus.version` / `DataBus.changed()`
- **CaseData classes:**
  - `Treatment(id, patientId, name, teeth = "16,17", fee, status, doctorId, notes, createdAt, doneAt)` + `teethList`
  - `Payment(id, patientId, amount, date, mode)` · `Booking(id, patientId, day, time, reason)`
- **CaseData functions:** `treatmentsOf`, `saveTreatment`, `addPayment` (returns id), `paymentsOf`, `getPaymentById`, `nextBooking`, `saveBooking`, `cancelBookings`, `markArrived`, `priceFor`, `rememberPrice`, `doctorNames`

## 10. Recent versions
| Version | What changed |
|---|---|
| 19 | Dark mode, swipe tabs, real teeth chart, app lock, trial, instant backup, finance dates |
| 20 | Stage 1: multi-sitting tracker |
| 21 | Stage 2: gum chart |
| 22 | Stage 3: mouth status (this build failed; see section 11) |
| 23 | Stage 4: braces case sheet, plus the fix for Stage 3 |
| 24 | Stage 5: patient education library |

## 11. Things to remember (gotchas)
- Kotlin has **no `toMutableSet()` on text**. Use `text.toSet().toMutableSet()`. This broke the v22 build in MouthStatus.kt.
- After any database change, call `DataBus.changed()` so all screens refresh.
- **Payments belong to the patient, not to a treatment.** Sittings and braces only link payment ids to show "paid", so the patient's balance stays correct.
- Feature tables use an `ensure…()` function that runs once per database connection, so the tables come back automatically after a restore.
- `saveTreatment()` also marks today's appointment as arrived.
- `saveBooking()` replaces any earlier booking (one booking per patient).
- Planned treatments (status 0) count as an estimate, not as billed.

## 12. Pending & ideas
- **Gum chart PDF report:** needs the current Pdf.kt so it matches the bills' letterhead.
- **Google Drive backup:** finish the fix.
  1. Enable the Drive API.
  2. Add the drive.file scope.
  3. Publish the app or add test users.
  4. Disconnect and reconnect in the app.
  - The SHA-1 is in the build log, step "Show SHA-1".
- The doctor to review the Hindi/Marathi lines in Education.kt.
- Optional: open the education library from the main menu too (right now it opens only from the patient file).
- **Top 3 next (researched):**
  1. Token queue with TV display.
  2. DPDP privacy kit (the law is enforced from May 2027).
  3. Membership plans.
- **Other researched ideas:**
  - Doctor commission engine (share_percent is already in the DB).
  - GST billing + Tally export.
  - Inventory with expiry alerts.
  - Performance dashboard.
  - ABHA/ABDM + DHIS incentives.
  - Multi-branch (needs cloud sync, e.g. Firebase Firestore asia-south1).
- **Older ideas:** UPI QR on bills, lab work tracker, expenses & profit report, end-of-day summary, consent forms with signature, Marathi/Hindi prescriptions, receptionist mode, Google review requests, in-app logo setup for multiple clinics.
- Windows app: discussed, not wanted.

## 13. Rules for Claude
- The repo is private, so Claude can't read it. **Ask for the current contents of a file before changing it.**
- Send only the files a change needs, not all files.
- Prefer a new self-contained file plus a small one-time workflow tool. The tool edits existing files, raises the version and starts the build.
- For app-wide colour or code changes, use a workflow tool instead of pasting many files.
- Show a preview before building a new screen; the owner picks the design.
- Never ask for or send `clinic.keystore`.
- Explain steps simply, with direct GitHub links (create/edit file links, Actions pages).
- The owner asked that the main chat is never closed.
