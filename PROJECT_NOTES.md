# Mahalaxmi Dental Clinic App – Project Notes

> Paste this whole file at the start of any new chat with Claude so it knows the project.
> Last updated: 28 Nov 2026 · Own Drive backup per device, restore list, Undo last restore, Add patients from another phone, TEST app, allergies + BP/sugar, clinic name built in, Lato + Merriweather everywhere, redesign decision pending.

---

## 1. Quick facts

| | |
|---|---|
| Repo (private) | https://github.com/omkarmaturkar/Mahalakshmi-Dental-Clinic-V2 (the repo name keeps the old spelling; that is fine). **Keep it private: it contains `app/clinic.keystore`.** |
| Package / applicationId | `com.mahalakshmi.dental` — **NEVER change it.** The phone would treat it as a new app and all data would be lost. Nobody sees it. |
| TEST app | Package `com.mahalakshmi.dental.test` (build type `sandbox`), name "Mahalaxmi TEST". Installs next to the clinic app with its own separate data (section 3). |
| App name (under the icon) | Mahalaxmi Dental Clinic (set by `manifestPlaceholders["appLabel"]` in `app/build.gradle.kts`; the manifest uses `android:label="${appLabel}"`) |
| Version | Read it in `app/build.gradle.kts`: `versionCode = N` and `versionName = "N.0"`. Every tool raises it by 1. **52** after tool-restore (27 Nov 2026). **The clinic phones still run 47** until the next clinic build (section 12). |
| Tech | Kotlin 2.0.21 · Jetpack Compose (BOM 2024.12.01, Material 3) · AGP 8.7.3 · Gradle 8.11.1 · Java 17 · minSdk 26 · target/compile 35 |
| Libraries | activity-compose 1.9.3 · core-ktx 1.15.0 · material-icons-core · play-services-auth 21.2.0 · biometric 1.1.0 · fragment-ktx 1.8.5 · work-runtime-ktx 2.9.1 |
| Database | Plain SQLite (`ClinicDb` extends `SQLiteOpenHelper`, DB version 4). No Room. WAL is switched off, so the whole database is one file (`clinic.db`); backups copy that file. |
| Navigation | Own back stack (`stack` of `Screen`) in `ClinicApp`. `go(screen)` pushes, `back()` pops, `open(screen)` resets to Home + that screen. |
| Main activity | `MainActivity` extends `FragmentActivity` (needed for fingerprint) |
| Offline | Everything works offline. Internet is only used for Google Drive backup, WhatsApp and the trial's internet-time check. |
| Kotlin files | `app/src/main/java/com/mahalakshmi/dental/` |
| Devices | Phones and a Samsung Galaxy Tab S9. **Each device has its own database; they do not sync.** Patients from another device can be added with "Add its patients" (section 8, Backups). |

## 2. Clinic details
- **Mahalaxmi Dental Clinic** (correct spelling; it was wrongly written "Mahalakshmi" until 19 Nov 2026)
- Gajanan Colony, Angur Bagicha Road, Gondia · Mon–Sat 10 AM–8 PM
- Dr. Tushar Maturkar, MBBS, BDS · Phone 8888882345
- **The app is only for Mahalaxmi Dental Clinic** (not for other clinics). The clinic name is **built into the app** (`DEFAULT_CLINIC_NAME` in ClinicSettings.kt). Clinic & doctors shows it without an edit box, and a name typed earlier on a device (for example "Mahalxmi") is corrected automatically the first time the new version opens. Address, phone, email, timings, doctors, footer and paper size stay editable on each device.
- Patient ID letters: **MDC** (made from the clinic name's initials the first time, then saved in settings key `patient_id_prefix`).

## 3. Building the app
- **Clinic app: build.yml** (Actions → Build APK → Run workflow). Inputs: *Trial days* (0 = full version) and *Your contact*. It writes `TrialConfig.kt` (TRIAL_UNTIL, TRIAL_CONTACT). Artifact name: FULL or TRIAL-until-date. Always use **Trial days 0** for the clinic.
- **TEST app: build-test.yml** (Actions → Build TEST app → Run workflow). **Keep this file.** It runs `assembleSandbox`, always writes a full-version `TrialConfig.kt`, and uploads **Mahalaxmi-TEST-APK**. It is signed with the same key, so a new TEST build installs over the old TEST app and keeps its data.
  - In `app/build.gradle.kts`: `create("sandbox") { initWith(getByName("release")); applicationIdSuffix = ".test"; versionNameSuffix = "-test"; manifestPlaceholders["appLabel"] = "Mahalaxmi TEST" }`. **A build type name cannot start with "test"** (Gradle error "BuildType names cannot start with 'test'"), so it is called `sandbox`.
  - The TEST app shows a **gold strip at the top of every screen**, **never writes backups** (`isTestApp(context)` = package ends with ".test"; folder backup is blocked and says "Backups are off in the TEST app"), and **Google Drive does not connect** in it (no OAuth client exists for the `.test` package; add one in Google Cloud with the same SHA-1 only if ever needed).
  - To test with real data: clinic app → Backup → Save backup file (.zip); TEST app → Backup → Restore from .zip file (or Add patients from a .zip file).
  - Way of working: try every change in the TEST app first; build the clinic app once at the end of a round.
- **Signing:** `app/clinic.keystore`, alias `clinic`. Passwords are GitHub repository secrets `CLINIC_STORE_PASSWORD` and `CLINIC_KEY_PASSWORD` (Settings → Secrets and variables → Actions → Secrets). `app/build.gradle.kts` reads them with `System.getenv`. **Never share the keystore. Never write passwords into a file.**
- If the build stops at "Check the signing passwords", a secret is missing (or saved under Variables instead of Secrets).
- **Fonts** in `app/src/main/res/font`: Poppins (`poppins_regular/medium/semibold/bold`; build.yml downloads them if missing; now only kept so old references work), Lato (`lato_regular`, `lato_bold`), Merriweather fixed weights (`merriweather_regular`, `merriweather_bold`). In Theme.kt: `ClinicTextFont` = Lato (all text), `ClinicTitleFont` = Merriweather (titles), and `Poppins` is an alias of `ClinicTextFont`, so old code that asks for Poppins gets Lato.
- **Finding build errors:** failed run → job **build** → step **Build the APK** → search `e: file:///`. Each line gives file, line number and problem. Gradle setup errors appear under "What went wrong". Send those lines to Claude.
- **Private repo limits:** GitHub Free gives 2,000 build minutes/month and 500 MB storage for artifacts. If storage is full, delete old run downloads.
- **Undoing a change:** never install a lower versionCode over the app (it cannot install without uninstalling, and uninstalling erases data). An undo is a new build with a higher number.

## 4. How changes are made (the "tool" method)
- The repo is private, so Claude cannot read it. The owner opens a file on GitHub → **Copy raw file** → pastes it (or attaches it).
- Changes come as a **one-time workflow tool** (a `.yml` file in `.github/workflows`):
  1. It may first write brand-new files to `/tmp/new` (with `cat > file <<'KT_END'` blocks).
  2. A Python step **checks every line it will change** (exact match after trimming spaces) and that new names don't already exist. If anything is not as expected, it prints **NOTHING WAS CHANGED** with the reasons and changes nothing.
  3. It makes the changes, raises the version by 1, prints **ALL DONE**, and commits.
  4. The owner deletes the tool afterwards by hand.
  5. Then the owner builds the TEST app (or the clinic app at the end of a round).
- **Tools cannot change any file in `.github/workflows`** (the workflow's GITHUB_TOKEN is not allowed to). build.yml and build-test.yml are edited by hand: edit link → Ctrl+A → paste → Commit changes.
- **Never write two dollar-brace characters (`${{`) inside a tool script:** GitHub fills them in before the script runs. In Python write `"$" + "{{"`.
- **Read-only tools** can collect information without changing anything (for example tool-tables printed every CREATE TABLE). To copy a log: open the run → the job → gear icon → **View raw logs** → Ctrl+A, Ctrl+C.
- Check the run's **green tick** before deleting a tool. A red cross: open the run → job → "Check every line, then change", send the last lines, and delete nothing.
- **After editing a tool file, use "Run workflow".** "Re-run jobs" on an old run repeats the OLD version of the tool.
- Tools that edit the same file can run in any order as long as they change different lines.
- The owner prefers **one step at a time**: short numbered steps, each ending with "reply done".
- Useful GitHub links:
  - New file: `https://github.com/omkarmaturkar/Mahalakshmi-Dental-Clinic-V2/new/main?filename=<path>`
  - Edit file: `https://github.com/omkarmaturkar/Mahalakshmi-Dental-Clinic-V2/edit/main/<path>`
  - Delete file: `https://github.com/omkarmaturkar/Mahalakshmi-Dental-Clinic-V2/delete/main/<path>`
  - View a file: `https://github.com/omkarmaturkar/Mahalakshmi-Dental-Clinic-V2/blob/main/<path>`
  - Actions: `https://github.com/omkarmaturkar/Mahalakshmi-Dental-Clinic-V2/actions`
  - One workflow (with its Run workflow button): `https://github.com/omkarmaturkar/Mahalakshmi-Dental-Clinic-V2/actions/workflows/<file>.yml`
- Only `build.yml` and `build-test.yml` should stay in `.github/workflows`. Delete any tool that is still there once it has run.

## 5. Files

**Core & navigation**
| File | What's in it |
|---|---|
| MainActivity.kt | `Screen` sealed class (Home, Schedule, Finance, BackupPage, ClinicSettings, FollowUps, Patients, PatientForm, PatientDetail, VisitForm, RxForm). `ClinicApp`: drawer (phone) / permanent menu (≥900dp), provides `LocalNotify`, `LocalGo`, `LocalQuickActions`; hosts `QuickActionsHost` when "+" is tapped. Bottom bar shows on **every screen except PatientForm and RxForm**; inside other screens the tab you came from stays lit; BackupPage/ClinicSettings light none. TrialGate, NavArea, ScreenHost. Refreshes all screens at midnight; removes leftover data at start. |
| SwipeTabs.kt | `TabPager`: swipe between Home/Patients/Schedule/Finance; `tabIndexOf()` |
| Theme.kt | `AppTheme` (Light/Dark/Auto), `Brand` colours, `ClinicTheme` (wraps content in `ScaledForTablets`; adds the gold strip in the TEST app), `ClinicTextFont` (Lato), `ClinicTitleFont` (Merriweather), `Poppins` (alias of ClinicTextFont), `AppTypography` (display/headline/titleLarge in Merriweather, the rest in Lato), `isTestApp(context)`, formatting helpers, building blocks (section 9). Titles of AppTopBar, FormCard, SectionHeader, ConfirmDialog, EmptyState and the "Mahalaxmi" in BrandRow use Merriweather. |
| Menu.kt | `AppMenu` (Quick actions item at top when available, Home, Patients, Schedule, Finance, Pending dues, **Follow-ups**, Settings…), `SectionTopBar`, `BottomNavBar` (dark pill; **green "+" button** between Patients and Schedule) |
| SplashScreen.kt | Logo animation (fits in 1.1 s) |
| CrashGuard.kt | Saves last_crash.txt; CrashReportScreen |

**Home, lists, money**
| File | What's in it |
|---|---|
| TodayScreen.kt | `TodayScreenContent` (renamed from TodayScreen): greeting, dark next-patient card, **RecentPatientsCard**, **FollowUpsEntry**, compact metric cards, quick actions (New patient · Book · Treatment · Payment), tablet two columns. `PatientPickerDialog`, `HomeIcons`, `HomeColors`, `recallMessage()`. Old recall section code is still in the file but no longer shown. |
| HomePatients.kt | `TodayScreen()` wrapper: Home uses **ClassicTypography (Lato)** and loads the patient-ID prefix. Its own `Lato` and `Merriweather` font families. `RecentPatientsCard` (5 most recent; calendar button swaps in Today/Yesterday/This week/This month/Last month/picked dates until ×; scrolls inside after 5). `patientsSeen()` |
| PatientsScreen.kt | All patients with search and filters |
| PatientCards.kt | `filterPatients()` (also matches Patient ID), `patientSubtitle()` (= Patient ID · last visit), PatientCard, DueBadge |
| ScheduleScreen.kt | Next 30 days of appointments |
| FinanceScreen.kt | Today/week/month/last month + custom range |
| FollowUps.kt | **Follow-ups** screen and Home entry (section 8) |

**Patient file & records**
| File | What's in it |
|---|---|
| PatientScreens.kt | PatientFileScreen. Top bar subtitle "Patient ID: MDC-…". Treatments tab = `TreatmentRecordsCard` + `ClinicalRecordsCard`. The Treatment action opens today's record. Old "Teeth & treatments" card code is still there but unused. `openWhatsApp`, `callNumber`, `reminderMessage`. |
| TreatmentRecords.kt | **Treatment records** (one per visit): list card, record screen (`TxDialog`), teeth chart per record, sittings inside, notes, money, Receive payment, Bill, estimate, Delete record, move date, Finish visit, OPD badge. Record bill/estimate PDFs. |
| PatientSheets.kt | `TreatmentDialog` (has `defaultStatus`; your own treatment names as chips; "+ Add new"; chips scroll after 3 rows; "Remove … from the list"), ReceivePaymentDialog, BookingDialog, NoteDialog, photo dialogs, BeforeAfterDialog |
| TreatmentList.kt | `NewTreatmentDialog` (name + usual fee; "Save & add another") |
| Sittings.kt | Multi-sitting tracker. `TreatmentProgress(db, patientId, onlyIds)` shows only one record's treatments; money row hidden inside records (`showMoney`) |
| PatientEdit.kt | Patient form: Patient card, **Medical alerts** card (allergy entries are not shown here), **AllergiesCard** (from Allergies.kt), more details, delete |
| CaseData.kt | Treatment, Payment, notes, photos, bookings; CRUD; `accountOf()`; `buildHistory()`; `priceFor()/rememberPrice()` |
| DentalChart.kt | `StatusChart` (colours by status; tap a tooth) and `ToothChart` (choose teeth) |
| Prescription.kt | Prescription screen + PDF. Red strip (`AlertLines`), complaint, **What we found with BP and sugar** (`FindingsVitalsCard`), Patient ID/OPD box, "Repeat last prescription", **allergy warning** before adding a matching medicine, medicine rows turn red with "Allergy: …". `ensureRxColumns()` adds complaint, findings, bp, sugar. |
| RxNotes.kt | `ComplaintCard` (chips: Pain, Swelling, Sensitivity, Bleeding gums, Broken tooth, Loose tooth, Food lodging, Bad breath, Missing tooth, Check-up, Cleaning, Braces; "Since": Today, 2–3 days, 1 week, 1 month, Long time) and `FindingsCard` (the prescription now uses `FindingsVitalsCard` instead) |
| **Allergies.kt** | `allergyOptions`, `allergyName()`, `allergiesIn()`, the medicine words for each allergy (`allergyGroups` — doctor to review), `allergyClash()`, `AllergiesCard`, `RowScope.AlertLines`, `FindingsVitalsCard`, `AllergyWarningDialog` |
| QuickActions.kt | `LocalGo`, `LocalQuickActions`, `RecordJump`, `QuickPlusButton`, `BottomSheetFrame(top)`, the "+" sheet (`QuickSheet` + `QuickActionsHost`), `FinishVisitSheet`, `RepeatLastButton` |
| AppHelpers.kt | `ScaledForTablets`, your own treatment names (`customTreatments`, `rememberTreatment`, `forgetTreatment`), `SquareIconButton`, Patient ID / OPD ID (`loadPatientPrefix`, `patientCode`, `opdId`) |

**Clinical modules (full-screen windows from the patient file)**
Perio.kt (gum chart), MouthStatus.kt (tooth conditions/surfaces), Ortho.kt (braces case sheet), Education.kt (8 topics in English/Hindi/Marathi). Shown as compact rows by `ClinicalRecordsCard`.

**Bills, PDFs, settings**
| File | What's in it |
|---|---|
| Bills.kt | Full bill (`invoicePdf`: each treatment line shows its visit's OPD No.), payment receipt (OPD No. of the payment day under the title), estimate. `patientRows()` prints Patient ID = MDC-…. `visitDays()`, `amountInWords()`, `rupees2()` |
| Pdf.kt | PdfBuilder (title, partyBox, patientBox, table, row, text, space, summary, notes, signature, save), PdfCell, SummaryLine, PdfActionsDialog, DocIcons, letterhead |
| ClinicSettings.kt | ClinicInfo, `clinicInfo()` (name always `DEFAULT_CLINIC_NAME`; corrects a saved `clinic_name` once), `saveClinicInfo()`, doctors (`doctors()`, `mainDoctor()`, `saveDoctor()`, `deleteDoctor()`), Clinic & doctors screen (name shown, not editable) |

**Data, backup, security**
| File | What's in it |
|---|---|
| Database.kt | ClinicDb (WAL off), `Patient` (`medical` text → `alerts` list), `getPatients()`, `getPatient()`, `savePatient()`, `deletePatient()`, `patientCount()`, `stats()`, `upcoming()` |
| ExtraTables.kt | Core tables, `deletePatientExtras`, `deleteFeatureData`, `removeLeftoverData`; FEATURE_TABLES includes the record tables |
| Backup.kt | Folder backup (blocked in the TEST app; keeps 30 days), .zip export, restore from folder or .zip, `planAddZip()`/`finishAddZip()` (add patients from a .zip), safety copy before a restore |
| DriveBackup.kt | Google Drive backup **per device**: file `clinic_<date>_<device code>.db` with appProperties device, deviceId, patients; keeps up to 15 per device; `deviceName()`; `listBackups()` (all app folders whose name contains "Backup", including the old-spelling one); `restoreItem()`; `planAdd()`/`finishAdd()` (add patients from another device); safety copy before a restore |
| BackupScreen.kt | Backup & Restore screen: Drive card ("This device", Last backup, Back up now, Restore, automatic backup, mobile data, Disconnect), **Undo last restore** card, folder backup, .zip, Restore from folder/.zip, **Add patients from a .zip file**; backup list → **Add its patients / Replace everything** → warnings; the app restarts after any restore |
| **SafeRestore.kt** | `RestoreSafety` (safety copy in `files/before_restore`, `available()`, `patients()`, `time()`, `undo()`) and `PatientMerge` (`plan()`, `apply()`: adds new patients with all their records) |
| Security.kt | App lock, AutoBackup, DriveBackupWorker |
| Trial.kt / TrialConfig.kt | Trial system (TrialConfig.kt is written by the build workflows) |

**Build files**
`app/build.gradle.kts` (release + `sandbox` build types, `appLabel` placeholder, version lines) · `app/src/main/AndroidManifest.xml` (`android:label="${appLabel}"`, FileProvider authority uses `${applicationId}`, allowBackup false) · `.github/workflows/build.yml` (clinic app) · `.github/workflows/build-test.yml` (TEST app).

## 6. Database (all tables, from tool-tables on 27 Nov 2026)

| Table | Columns |
|---|---|
| patients | id, name, phone, age, gender, created_at, medical, notes |
| visits (old) | id, patient_id, date, reason, problem, fees, paid, photos, teeth, next_visit, pay_mode |
| payments | id, patient_id, visit_id, amount, date, mode |
| settings | key (PK), value |
| prescriptions | id, patient_id, visit_id, date, diagnosis, medicines (JSON), advice, complaint, findings, bp, sugar (the last four are added by `ensureRxColumns()`) |
| rx_templates | id, name, medicines, advice |
| treatments | id, patient_id, name, teeth, fee, status, doctor_id, notes, created_at, done_at, legacy_visit |
| case_notes | id, patient_id, treatment_id, date, text |
| case_photos | id, patient_id, treatment_id, file, kind, date |
| appointments | id, patient_id, day, time, reason, status, created_at |
| doctors | id, name, qualification, specialty, reg_no, phone, on_bills, main, visiting, share_percent, active |
| tooth_status | patient_id + tooth (PK), decay, filled, crown, rct, implant, missing, broken, note, updated_at |
| ortho_cases | id, patient_id, start_date, months, status, complaint, bite, profile, overjet, overbite, crowding, findings, extraction, appliance, plan, fee, down_payment, monthly, paid_before, treatment_id, created_at |
| ortho_visits | id, case_id, patient_id, date, upper_wire, lower_wire, elastics, notes |
| ortho_payments | case_id + payment_id (PK) |
| perio_exams | id, patient_id, date, data (JSON per tooth), notes |
| sittings | id, treatment_id, patient_id, position, name, fee, done, done_at, payment_id, notes |
| treatment_records | id, patient_id, day (epoch day of the visit), notes, created_at |
| record_items | treatment_id (PK), record_id, patient_id — which treatment is in which record |
| record_payments | payment_id (PK), record_id, patient_id — payments received inside a record |

- Feature tables (sittings, perio, tooth status, ortho, records) are made on first use with `CREATE TABLE IF NOT EXISTS`; all are in the same file, so backups include them.
- treatments.status: 0 To do (planned) · 1 In progress · 2 Done. Planned = estimate only, not billed.
- appointments.status: 0 booked · 1 arrived/done · 2 cancelled. `saveBooking()` replaces any earlier booking (one per patient).
- Patient ids use AUTOINCREMENT, so a deleted patient's number is never reused (Patient IDs stay permanent).
- **Medical alerts** are `patients.medical`, comma-separated. **Allergies** are saved there as `Allergy: <name>`; older entries like "Penicillin allergy" or "Allergic to X" are understood too.
- **settings keys:** `price:<treatment>` (remembered fee) · `treatments:custom` (your own treatment names, one per line) · `patient_id_prefix` (MDC) · `followup:<patientId>` (hidden from Follow-ups until this time) · `clinic_name` (always set back to the built-in name) · phone, email, address, timings, footer, paper · `visits_moved`, `doctors_moved` (one-time conversions).
- **Shared preferences (not in backups):** "appearance" (theme), app lock, "education" → "lang", "drive_backup" (connected, account, last, auto, mobile, device_id), "backup" (folder, last, lastAuto), "restore_safety" (patients, time).
- **Files:** photos in `files/photos`; safety copy in `files/before_restore`; temporary folders in cache (`drive_restore`, `drive_add`, `restore`, `zip_add`).
- Deleting a patient removes everything of theirs, including records. `removeLeftoverData()` also cleans record links whose treatment or payment no longer exists.

## 7. Screens & navigation
- Home→TodayScreen · Patients→PatientsScreen · Schedule→ScheduleScreen · Finance→FinanceScreen · PatientForm→PatientEditScreen · PatientDetail→PatientFileScreen · VisitForm→PatientFileScreen(startAdd = true → opens today's record) · RxForm→PrescriptionScreen · BackupPage→BackupScreen · ClinicSettings · **FollowUps→FollowUpsScreen**
- **Bottom bar on every screen** except the patient form and prescription form (they have their own Save bar).
- **"+" quick sheet** (from the bottom bar, or "Quick actions" in the side menu on tablets held sideways). Opens **at the top of the screen** so the keyboard never covers it.
- Windows that open over the patient file (treatment record, gum chart, mouth status, braces, education) cover the bar; back closes them.
- **Tablets:** `ScaledForTablets` draws everything 1.3× larger when smallest width ≥ 720dp (1.15× from 600dp). Tab S9 sideways shows the permanent side menu and two columns (Home, patient file, Backup); upright uses the phone layout.

## 8. Features

### Before Nov 2026 (up to v30)
- Trial system (TRIAL_UNTIL; clock-rollback protection; TrialEndedScreen; warning in last 5 days).
- App lock: 4-digit PIN (salted SHA-256) + fingerprint; lock after 0/1/5 min; forgot PIN uses phone lock; 5 wrong tries → 30 s wait.
- Automatic backup when leaving the app (folder backup and Google Drive; DriveBackupWorker as a safety copy).
- Finance date picker; swipeable next-patient card; dark mode; professional PDFs (white letterhead, watermark, bordered tables, amount in words, signature); real teeth chart; swipe tabs.
- Stage 1 sittings · Stage 2 gum chart · Stage 3 mouth status · Stage 4 braces case sheet · Stage 5 education library (see the files above).
- Fixes round 1 (v30): full patient delete, no double sitting payment, midnight refresh, clinic name in reminders, splash timing, signing passwords in secrets.

### Treatment records (v33–v34) — "job cards"
- One file per patient; **one record per visit**. The Treatments tab lists records newest first: date, Open/Done/**Upcoming** chip, OPD badge, treatments, total, "Next: <sitting>", "₹… due".
- **New treatment record** = today's record (opens the existing one if today already has a record). The **calendar button** next to it makes a record for any other day (e.g. an advance plan). Treatments added to a future record start as **To do** (not billed).
- Inside a record: OPD No. badge, **teeth chart** for that visit (tap a tooth to open its treatment or add one), treatments (tap the status pill to move it on), sittings, notes, Total/Paid/Left, Receive payment, Bill, Send estimate, **Finish visit** (today's record), **Delete this record** (removes its treatments and sittings; payments stay), calendar button to **move** the record to another date.
- A finished record asks before a treatment is changed. A record left empty is removed when closed.
- Old treatments were put into records by the day they were added. Treatments made elsewhere (Mouth status, braces) also join the record of their day automatically.
- **Money rule:** a payment received inside a record counts for that record (up to its total). Every other payment pays the **oldest records first**. So the records always add up to the patient's Balance due.

### Home (v35–v36)
- Recent patients card right after the next-patient card; the calendar button temporarily swaps it for patients seen on chosen days (same size; scrolls inside); × returns.
- Compact summary cards (icon + label + value) and smaller quick actions.
- Home uses its own Merriweather (names, titles, numbers) + Lato (other text) from HomePatients.kt. Small text on Home is 13sp in darker grey (Brand.Slate).

### Patient ID and OPD ID (v37+)
- **Patient ID** = prefix + patient number padded to 3 digits, e.g. **MDC-001**. Permanent (numbers are never reused).
- **OPD ID** = Patient ID + visit date (DDMMYY), e.g. **MDC-001-191126** for 19 Nov 2026. A future record gets its future date.
- Shown on: patient file top bar, patient lists (searchable), record cards and record screen, record bill ("OPD No."), full bill (each treatment line), payment receipt (under the title, OPD of the payment day), prescription (screen and PDF box: Rx No., OPD No., Date, Consultant), bills/receipts/estimates ("Patient ID").

### Treatment names
- Your own names (e.g. "Secure 2", "Set 1") are saved and shown as chips after the usual ones. "+ Add new" adds a name and usual fee in advance ("Save & add another"). Typing a usual treatment with a fee sets its fee. Chip area shows 3 rows and scrolls.

### Prescriptions
- Sections on the form: red strip (**Allergic to: …** in bold, then **Medical alerts: …**) → **What the patient told us** (chief complaint: chips + "Since" + your words) → **What we found** (**BP** and **Sugar (mg/dL)** boxes, then findings) → Diagnosis (pre-filled from today's treatments) → Repeat last prescription (only for a new prescription when an older one exists; copies medicines + advice, not the complaint) → Templates → Medicines → **What we told the patient** (advice).
- PDF order: letterhead → PRESCRIPTION → Patient details / Prescription details box → **Allergic to** (red, bold) → Medical alerts (red) → Chief complaint → On examination (**BP … mmHg · Sugar … mg/dL**, then findings) → Diagnosis/Treatment → Rx table → "1-0-1 means…" → Advice → Next visit → signature. Empty sections are not printed.
- The app checks **allergies only** (simple word match on medicine names, section below). It does not check doses or drug interactions.

### Allergies, BP and sugar (27 Nov 2026, v51)
- Patient form → **Allergies** card: Penicillin, Sulfa drugs, Painkillers (NSAIDs), Aspirin, Metronidazole, Local anaesthetic, Latex, plus "Other allergy". ("No known allergy" was left out on purpose: it would show in red like a real allergy.)
- Saved as `Allergy: <name>` in the medical alerts, so they also show in red on the patient file.
- Adding or changing a medicine that matches an allergy opens **Allergy alert**: "<first name> is allergic to <allergy>. <medicine> may cause a reaction." → **Don't add** / **Add anyway**. Medicines added by a template or "Repeat last" don't ask, but their rows turn red with "Allergy: …".
- Medicine words per allergy (in `allergyGroups`, Allergies.kt — **doctor to review**): Penicillin → anything with "cillin", augmentin, clavulan, clavam, amoxyclav, amoxiclav · Sulfa → sulfa, sulpha, cotrimoxazole, septran, bactrim · NSAIDs → ibuprofen, diclofenac, aceclofenac, ketorolac, naproxen, nimesulide, mefenamic, aspirin, etoricoxib, piroxicam, indomethacin, ketoprofen, flurbiprofen, combiflam, brufen, voveran, zerodol, meftal, dolonex, ketorol, ecosprin, disprin · Aspirin → aspirin, ecosprin, disprin, acetylsalicylic · Metronidazole → metronidazole, metrogyl, flagyl, tinidazole, ornidazole · Local anaesthetic → lignocaine, lidocaine, xylocaine, benzocaine, articaine, bupivacaine, prilocaine · any other allergy name (4+ letters) matches medicines containing that word.
- BP accepts digits and "/" (e.g. 130/80); sugar up to 3 digits. Saved in `prescriptions.bp` / `prescriptions.sugar`.

### Clinic name built in (27 Nov 2026, v50)
- See section 2. The typo "Mahalxmi" that was saved on a phone is corrected automatically.

### Fonts and readability (27 Nov 2026, v49)
- Lato for all text and Merriweather for titles across the whole app (`ClinicTextFont`, `ClinicTitleFont`).
- Grey text one shade darker: `Brand.Sub` light #555B59 (was #69716D), dark #B4BCB8; `Brand.Faint` light #8A918D, dark #86908B. FormCard and ToggleRow subtitles 12 → 13sp. AppTopBar title is Merriweather 20sp.
- This may be replaced by the redesign (section 12).

### Backups (25–27 Nov 2026)
- **Folder backup:** when leaving the app (at most every 30 minutes), into a folder you choose; keeps the last 30 days. Also **Save backup file (.zip)**.
- **Google Drive backup, one per device:** each phone or tablet saves its own file (its device code in the name, its name and patient count stored with it), so one device never replaces another device's backup. Up to 15 daily backups per device. Automatic when leaving the app (DriveBackup.kt waits 20 hours between automatic backups) or **Back up now** any time. The Backup screen shows "This device: <name>" (the name comes from the phone's Settings → About phone).
- **Restore from Drive:** a list of every backup on the Google account (also those in the old-spelling folder), newest first, with device name, date and time, patients, and "This device". Tap one → **Add its patients** or **Replace everything**.
- **Replace everything:** warning "This phone has X patients. They will be replaced by Y patients from <device> (<date>)." If the backup has fewer patients, the warning and button turn red and say how many would be removed.
- **Add its patients** (also **Add patients from a .zip file**, which works offline): shows first how many new patients will be added and which are already on this phone. A patient counts as **already here** if the name is the same (ignoring capitals and extra spaces) **and** the mobile is the same or one of them is blank. Already-here patients are left exactly as they are (their visits on the other device are not joined in). New patients come with all their records: visits, payments, treatments, treatment records and their links, prescriptions, notes, photos, appointments, mouth status, gum charts, braces records and sittings. Every record gets new numbers on this phone and all links are kept; added patients get the next Patient IDs here. Doctors are matched by name; a missing doctor is added (not as main doctor). Settings, templates and follow-up states are not copied. If the other device has newer data the app doesn't know, it stops with "Update both phones to the same version of the app first".
- **Safety copy and Undo:** before any restore or "add patients", the device's current data is saved in `files/before_restore` (for a replace, photos are moved there, not copied, so no extra space is used). The **Undo last restore** card appears at the top of the Backup screen; it brings that data back once.
- The app **restarts by itself** 1.5 seconds after a restore or undo, so every screen shows the right data.
- Google Drive scope is `drive.file` (only files this app made). Files made by the app on one device are visible to the same app on another device with the same Google account.

### "+" quick actions
- Search by name, mobile, Patient ID or **OPD No.** (an OPD No. opens that exact visit through `RecordJump`). Recent patients as chips. Actions: New patient, Treatment record, Receive payment (then receipt), Book appointment, Prescription. Action buttons hide while typing.

### Finish visit
- Opens when a treatment in **today's** record becomes Done (or with the Finish visit button): Receive payment · Prescription · Book next visit · Bill on WhatsApp · Done.

### Follow-ups (patient tracker)
- One row per patient, most urgent first. Reasons:
  - **Missed** – a booked day in the last 60 days passed, the patient didn't come, nothing new booked.
  - **Unfinished** – a treatment In progress, no booking, no visit in the last 3 days.
  - **Not started** – a To do treatment older than 7 days, no booking.
  - **Dues** – money left, no booking, no visit in the last 3 days.
  - **Check-up** – last visit 6 months to 3 years ago, no booking (replaces the old "Recalls due").
- Each row: WhatsApp (ready message per reason, editable in WhatsApp), Call, ✓ = contacted (hidden for 7 days). "Next reminder: <name> (n left)" sends one by one. Entry on Home ("N patients need a follow-up", hidden when 0), in the side menu, and from the bell.

### TEST app (27 Nov 2026)
- See section 3.

## 9. Shared building blocks (signatures confirmed by builds)
- `AppCard(modifier, onClick = …, padding = …) { … }` · `Pill(text, bg, fg, icon?, modifier)` · `SectionHeader(title, modifier)` · `FormCard(title, subtitle) { … }` · `EmptyState(title, message)`
- `SoftButton(text, icon?, modifier, bg =, fg =, height =) { … }` · `GradientButton(text, modifier, icon?, enabled =, height =) { … }` · `CircleIconButton(icon, onClick, bg =, tint =, size =)` · `SquareIconButton(icon, label) { … }`
- `AppField(value, onChange, label, modifier =, icon =, type =, lines =, caps =, prefix =)` · `ChoiceChip(text, selected, modifier =, selectedBg =) { … }` · `Segmented(options, selected) { … }` · `ToggleRow(title, subtitle, checked) { … }`
- `AppTopBar(title, subtitle, onBack) { actions }` · `ConfirmDialog(title, message, confirmText =, danger =, onConfirm =, onDismiss =)` · `BusyDialog(text)`
- `DatePick(epochDay, onDismiss =) { day -> }` · `BookingDialog(initial, defaultReason, onDismiss, onCancelBooking) { day, time, reason -> }` · `ReceivePaymentDialog(balance, onDismiss) { amount, mode -> }` · `PatientPickerDialog(db, title, onDismiss) { patient -> }`
- `TreatmentDialog(db, patientId, initial, presetTooth, defaultStatus = STATUS_ONGOING, onDismiss, onSaved)` · `TreatmentProgress(db, patientId, onlyIds = null)` · `StatusChart(statusByTooth) { tooth -> }`
- `TreatmentRecordsCard(db, patientId, openNew) { onOpened }` · `RecentPatientsCard(db, go)` · `FollowUpsEntry(db) { open }` · `FollowUpsScreen(db, go, onBack)` · `ComplaintCard(text) { }` · `FindingsCard(text) { }` · `QuickActionsHost(db, go) { onClose }` · `NewTreatmentDialog(db, onDismiss, onAdded)`
- **New:** `AllergiesCard(medical)` · `RowScope.AlertLines(alerts)` · `FindingsVitalsCard(text, bp, sugar, onBp, onSugar) { onChange }` · `AllergyWarningDialog(patientName, medicine, allergy, onKeepOut, onAddAnyway)` · `allergiesIn(alerts)` · `allergyName(alert)` · `allergyClash(allergies, medicine)` · `isTestApp(context)` · `RestoreSafety.available/patients/time/undo` · `PatientMerge.plan/apply` · `DriveBackup.deviceName/listBackups/restoreItem/planAdd/finishAdd` · `Backup.planAddZip/finishAddZip`
- `PdfActionsDialog(title, subtitle, phone, message, build = { ctx -> file }, onDismiss)` · `PdfCell(text, width, size, color, style, align)` · `SummaryLine(label, value, strong =, color =)`
- Helpers: `rs()`, `numText()`, `fmtDate(ms)`, `fmtDateTime(ms)`, `fmtDay(day)`, `fmtDayLong(day)`, `relativeTime(ms)`, `agoText(ms)`, `todayEpochDay()`, `epochDayOf(ms)`, `withDay(ms, day)`, `startOfToday()`, `patientCode(id)`, `opdId(patientId, day)`, `db.loadPatientPrefix()`, `db.clinicInfo()`, `LocalNotify.current("msg")`, `LocalGo.current(screen)`, `DataBus.version` / `DataBus.changed()`

## 10. Version history
| Version | What changed |
|---|---|
| ≤30 | See section 8 (before Nov 2026) |
| 31–32 | Built outside these notes (compact clinical rows `ClinicalRecordsCard` appear here) |
| 33 | Treatment records (job cards) |
| 34 | Teeth chart inside each record; Delete this record |
| 35 | Home: recent patients + calendar, compact cards, tablet two columns, Merriweather + Lato on Home |
| 36 | Fixed-weight Merriweather; darker, larger small text on Home |
| 37 → 48 (each tool +1, in the order they were run) | tool-v37 (tablet size, own treatment names, records for any date, Patient ID / OPD ID) · tool-rx (IDs on prescriptions) · tool-opd (OPD on receipts and full bill) · tool-treatments (+ Add new, scrolling chips) · tool-menu (bottom bar everywhere) · tool-complaint (complaint + findings) · tool-quick ("+", Finish visit, Repeat last prescription) · tool-sheetfix (actions hide while typing) · tool-followups (Follow-ups + name Mahalaxmi) · tool-readable (clearer record text) · tool-sheettop ("+" sheet at the top) · **tool-drivefix** (25 Nov: own Drive backup per device, restore list, restart after restore). The clinic phones run **47**. |
| 49 | tool-round1 (27 Nov): Lato + Merriweather everywhere, darker grey text, TEST app (`sandbox` build type, gold strip, no backups) |
| — | By hand (27 Nov): build-test.yml added; build type renamed from `tester` to `sandbox` (no version change) |
| 50 | tool-clinicname: clinic name built in |
| 51 | tool-allergy: allergies, allergy warning, BP and sugar |
| 52 | tool-restore: Undo last restore, warnings with patient counts, Add patients from another device or .zip |

### Tool checklist (each must have run; delete the tool file afterwards)
| Tool | How to check in the repo |
|---|---|
| tool-v37 | `AppHelpers.kt` exists (IDs show in the app) |
| tool-rx | `Prescription.kt` contains `PRESCRIPTION DETAILS` |
| tool-opd | `Bills.kt` contains `visitDays` |
| tool-treatments | `TreatmentList.kt` exists; `PatientSheets.kt` contains `+ Add new` |
| tool-menu | `MainActivity.kt` contains `is Screen.PatientForm, is Screen.RxForm -> null` |
| tool-complaint | `RxNotes.kt` exists |
| tool-quick | `QuickActions.kt` exists |
| tool-sheetfix | `QuickActions.kt` contains `if (query.isBlank()) {` |
| tool-followups | `FollowUps.kt` exists; no `Mahalakshmi` left in `res/values/strings.xml` |
| tool-readable | `TreatmentRecords.kt` contains `"OPD No. ${record.opd}"` |
| tool-sheettop | `QuickActions.kt` contains `top = true` |
| tool-drivefix | `DriveBackup.kt` contains `fun listBackups(` ✓ (seen 27 Nov) |
| tool-round1 | `Theme.kt` contains `val ClinicTextFont`; `app/build.gradle.kts` contains `create("sandbox")` ✓ |
| tool-clinicname | `ClinicSettings.kt` contains `name = DEFAULT_CLINIC_NAME,` ✓ |
| tool-allergy | `Allergies.kt` exists ✓ |
| tool-restore | `SafeRestore.kt` exists ✓ |
| tool-tables | Read-only; delete it if it is still there |

## 11. Things to remember (gotchas)
- Kotlin has no `toMutableSet()` on text: use `text.toSet().toMutableSet()`.
- After any database change call `DataBus.changed()`.
- Payments belong to the patient, not to a treatment; records and sittings only link payment ids.
- Feature tables use an `ensure…()` function once per DB connection, so they come back after a restore. Code that touches a feature table from elsewhere must check it exists (`PRAGMA table_info`) or wrap the query in `runCatching`.
- `saveTreatment()` marks today's appointment as arrived. `saveBooking()` replaces earlier bookings.
- Values in `remember(...)` that depend on today's date must include the day in their keys.
- Names from the undone Heritage redesign do not exist: DisplayFont, BodyFont, RichCard, glassEdge, Brand.Sun, OnSun, Rich, Cloud, Accent, OnAccent, Focus, GlassEdge.
- Kotlin 2.0 reports a named argument given twice as "None of the following candidates is applicable".
- In Kotlin lambdas, an `if`/`when` whose branches are assignments must use braces `{ }` when it is the last statement.
- Kotlin 2.0.21: `break`/`continue` can't jump out of an inline lambda (like `use { }`) to a loop outside it; loops inside the lambda are fine.
- A function with an expression body can't use a plain `return` inside `synchronized { }`; use `return@synchronized` or a block body.
- **Tool marker checks:** extension functions are written `fun ClinicDb.name(` — check for `name(`, not `fun name(` (tool-treatments failed once because of this).
- In tool scripts apply only one text-replacement rule per line (rules can chain).
- A workflow's GITHUB_TOKEN cannot create, change or delete files in `.github/workflows`.
- Never put `${{` in a tool script (GitHub fills it in); write `"$" + "{{"` in Python.
- **A build type name cannot start with "test"** (the TEST app's build type is `sandbox`).
- GitHub web editor error "File could not be edited" = page out of date; reload, paste again, commit.
- **Keyboard and pop-up windows:** on the clinic phone, `SOFT_INPUT_ADJUST_RESIZE` did not move a Compose Dialog above the keyboard. Any sheet with typing must open at the top (`BottomSheetFrame(top = true)`).
- **Variable fonts** rendered only their thinnest weight on the phone: always use fixed-weight TTF files.
- **Readability:** keep important text ≥13sp and dark (`Brand.Ink`/`Brand.Slate`/`Brand.Sub`).
- **Each device has its own database.** A restore replaces everything on that device; "Add its patients" only adds patients that are not there yet. Undo keeps one copy (the state before the last restore or add).
- The TEST app has a different package, so its Google sign-in fails ("Google Drive setup is not finished yet") — expected.
- Never change the package `com.mahalakshmi.dental`.
- Version lines in `app/build.gradle.kts` must stay exactly `versionCode = N` and `versionName = "N.0"` (tools look for this).
- Long code messages can get cut off: send code in parts, or let the tool write new files itself.
- Incognito chats are not saved. Keep these notes in the repo and update them after each round.

## 12. Pending & ideas

**Next steps for the owner**
1. **Build the clinic app** (Build APK, Trial days 0) and install it on the phone and the tablet. The phones run version 47, so they don't have the backup fix, allergies, Undo or Add patients yet.
2. On each device: Backup → **Back up now** (makes the new per-device Drive backup).
3. Test between two phones: Restore → pick the other phone's backup → **Add its patients**, then **Undo last restore**. Also test **Replace everything** on a spare phone.
4. If the main device has only test patients (Uvi, Hugf, Gahh, Test Q = MDC-001 to MDC-004), clear the app's data there before real use, so the first real patient is MDC-001. Then set doctors and details in Clinic & doctors.
5. Google Auth Platform → Audience: check it says **In production** (in Testing, Google cancels Drive access after 7 days).
6. Doctor to review the allergy medicine words (Allergies.kt) and the Hindi/Marathi lines in Education.kt.

**Redesign — decision pending (27–28 Nov 2026)**
- The owner shared a developer's design (Splash, Home, Patients, Schedule empty, Finance, Quick actions sheet, Menu): flat white cards on warm grey, thin outline icons, green buttons, near-black selected filter chips, **4-tab bottom bar without the "+"**, Home title = date + doctor name, no dark gradient card. Rule: **only the look changes; every feature and all data stay the same; dark mode must work.**
- **unslop review of the current app:** gradient buttons with a green glow, dark gradient cards with a green glow (Home next-patient card, Backup header), decorative sparkles, spaced capitals "DENTAL CLINIC", cards sliding in one by one (`Modifier.appear`), filled icons mixed with drawn ones. All go in the redesign.
- **Option A — developer's design + unslop rules:** Inter font; warm grey #F4F3EF, white cards with hairline #E8E7E2, text #1C211F/#5F6662, green #4D7C0F (dark mode #7CB518 with dark text), red #C53B3B only for dues/allergies; corners 10 (buttons, fields) / 16 (cards); 4-point spacing.
- **Option B — "clinic record" (UI UX Pro Max rules), recommended so it doesn't look AI-made:** IBM Plex Sans (has a matching Devanagari font) + IBM Plex Mono for Patient IDs, OPD numbers, dates and amounts; lists as one ruled register; tooth numbers in small boxes like a dental chart; visit timeline in the patient file; corners 8 (controls) / 12 (cards); logo colours (leaf green #4A7A12 for actions, lime #7CB518 for marks); amber only for follow-ups, red only for dues/allergies; bottom bar with a thin line on the active tab.
- Both keep: date title on Home, quick actions from the menu and the search button, the Recent patients date filter (small calendar icon next to "See all"), dark mode in Menu → Appearance (the moon button on Home goes).
- **Plan after the choice:** Round 1 building blocks in Theme.kt (fonts, light + dark colours, cards, buttons, chips, fields, top bar, dialogs) → Round 2 Home, splash, quick actions, bottom bar, menu → Round 3 Patients + patient file → Round 4 Schedule, Finance and the rest. Each round: preview → tool → TEST app check. New font files must be fixed-weight TTFs.
- Claude has the **unslop** and **UI UX Pro Max** skills installed. In a chat without a code environment their search scripts can't run; only their rules are used.

**Older plans (may be folded into the redesign)**
- Tablet layouts for Patients, Schedule and Finance, and loading data in the background (Home and patient file are done).
- Clean-up: remove the old "Teeth & treatments" card (PatientScreens.kt) and the old recall section (TodayScreen.kt).
- One shared title/dialog style (review item No. 13).

**Ideas from the BestoSys review (all work offline), in suggested order:** patient journey messages (thank-you with care instructions, birthday wishes – needs date of birth, Google review request); consent forms with on-screen signature + doctor signature/stamp on PDFs; lab work tracker; monthly growth report + expenses; UPI QR on bills/receipts; home-screen widget with today's patients. Later: memberships, waiting-room display, family accounts, price list with special rates, import from Excel. Skip: online booking/patient app, payment gateway, automatic WhatsApp sending, leads/campaigns, insurance. (Allergy alert + vitals: done.)
- BestoSys print ideas to consider: print on plain paper or pre-printed letterhead with adjustable margins; Hindi/Marathi prescription instructions; medicines suggested per treatment; leave a medicine off the print; pharmacy details at the bottom of prescriptions (doctor to confirm if required).
- Other ideas: larger-text switch in the menu; Call/WhatsApp buttons on patient rows; end-of-day summary; receptionist mode; GST billing + Tally export; inventory with expiry; doctor commission (share_percent exists); ABHA/ABDM; DPDP privacy kit (law enforced from May 2027).
- **Live sync between devices (future project):** would need an online database such as Google Firebase. For one clinic the patient records fit Firebase's free plan (1 GB stored, 50,000 reads and 20,000 writes a day; on the free plan a product stops for the rest of the month if a limit is passed). Photos need the pay-as-you-go plan (card needed; first 5 GB free). Several weeks of work; patient data online needs careful security. Until then: one main device for entering patients + "Add its patients" when needed.
- Windows app: discussed, not wanted.

## 13. Rules for Claude
- The repo is private: **ask for the current contents of a file before changing it**, even if it was shown earlier. Never rebuild a whole file from memory; work only from the pasted file (or a file Claude wrote itself in the same chat).
- Send only the files a change needs. Prefer a new self-contained file plus one one-time tool that checks every line, raises the version and commits. The tool should write new files itself when possible.
- **Always give the GitHub link together with the code** it belongs to (create/edit/delete links, the workflow page). **One step at a time**, in short numbered steps; each step ends with "reply done". Avoid long mixed messages.
- **Show a preview before building any new screen or design change**; the owner picks. Keep everything simple: one screen, one tap, no new menus unless needed. The Heritage redesign was rejected; don't bring it back. The redesign choice is pending (section 12).
- Test every change in the **TEST app** first; build the clinic app once at the end of a round.
- Colour rule: green = brand and money received, gold/amber = attention, red = dues, allergies and danger, grey for everything else. Important text must be easy to read (≥13sp, dark).
- **Patient data comes first:** never make a change that could lose data without a safety copy or undo.
- The app is only for Mahalaxmi Dental Clinic; the clinic name is built in.
- Never ask for or send `clinic.keystore`; never write signing passwords into a file. Never change the package name. Keep the repo private.
- When a fix doesn't work, say so plainly and change the approach rather than repeating it.
- The owner asked that the main chat is never closed — but incognito chats are not kept, so these notes must be updated in the repo after each round.
