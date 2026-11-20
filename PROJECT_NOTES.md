# Mahalaxmi Dental Clinic App – Project Notes

> Paste this whole file at the start of any new chat with Claude so it knows the project.
> Last updated: 21 Nov 2026 · Treatment records, Patient ID / OPD ID, Follow-ups, "+" quick actions, name corrected to Mahalaxmi.

---

## 1. Quick facts

| | |
|---|---|
| Repo (private) | https://github.com/omkarmaturkar/Mahalakshmi-Dental-Clinic-V2 (the repo name keeps the old spelling; that is fine) |
| Package / applicationId | `com.mahalakshmi.dental` — **NEVER change it.** The phone would treat it as a new app and all data would be lost. Nobody sees it. |
| App name (under the icon) | Mahalaxmi Dental Clinic |
| Version | Read it in `app/build.gradle.kts`: `versionCode = N` and `versionName = "N.0"`. Every tool raises it by 1. |
| Tech | Kotlin 2.0.21 · Jetpack Compose (BOM 2024.12.01, Material 3) · AGP 8.7.3 · Gradle 8.11.1 · Java 17 · minSdk 26 · target/compile 35 |
| Libraries | activity-compose 1.9.3 · core-ktx 1.15.0 · material-icons-core · play-services-auth 21.2.0 · biometric 1.1.0 · fragment-ktx 1.8.5 · work-runtime-ktx 2.9.1 |
| Database | Plain SQLite (`ClinicDb` extends `SQLiteOpenHelper`, DB version 4). No Room. One file on each device. |
| Navigation | Own back stack (`stack` of `Screen`) in `ClinicApp`. `go(screen)` pushes, `back()` pops, `open(screen)` resets to Home + that screen. |
| Main activity | `MainActivity` extends `FragmentActivity` (needed for fingerprint) |
| Offline | Everything works offline. Internet is only used for Google Drive backup, WhatsApp and the trial's internet-time check. |
| Kotlin files | `app/src/main/java/com/mahalakshmi/dental/` |
| Devices | Used on a phone and a Samsung Galaxy Tab S9. **Each device has its own database; they do not sync.** |

## 2. Clinic details
- **Mahalaxmi Dental Clinic** (correct spelling; it was wrongly written "Mahalakshmi" until 19 Nov 2026)
- Gajanan Colony, Angur Bagicha Road, Gondia · Mon–Sat 10 AM–8 PM
- Dr. Tushar Maturkar, MBBS, BDS · Phone 8888882345
- The clinic name used on bills, prescriptions and WhatsApp messages is **saved on each device** in Menu → Clinic & doctors. After the name fix it must be changed there once on the phone and once on the tablet.
- Patient ID letters: **MDC** (made from the clinic name's initials the first time, then saved in settings key `patient_id_prefix`).

## 3. Building the app
- **build.yml** (Actions → Build APK → Run workflow). Inputs: *Trial days* (0 = full version) and *Your contact*. It writes `TrialConfig.kt` (TRIAL_UNTIL, TRIAL_CONTACT). Artifact name: FULL or TRIAL-until-date. Always use **Trial days 0** for the clinic.
- **Signing:** `app/clinic.keystore`, alias `clinic`. Passwords are GitHub repository secrets `CLINIC_STORE_PASSWORD` and `CLINIC_KEY_PASSWORD` (Settings → Secrets and variables → Actions → Secrets). `app/build.gradle.kts` reads them with `System.getenv`. **Never share the keystore. Never write passwords into a file.**
- If the build stops at "Check the signing passwords", a secret is missing (or saved under Variables instead of Secrets).
- **Fonts** in `app/src/main/res/font`: Poppins (`poppins_regular/medium/semibold/bold`), Lato (`lato_regular`, `lato_bold`), Merriweather fixed weights (`merriweather_regular`, `merriweather_bold`, downloaded from the Google Fonts CSS API). The old variable `merriweather.ttf` was removed. build.yml downloads Poppins only if missing.
- **Finding build errors:** failed run → job **build** → step **Build the APK** → search `e: file:///`. Each line gives file, line number and problem. Send those lines to Claude.
- **Private repo limits:** GitHub Free gives 2,000 build minutes/month and 500 MB storage for artifacts. If storage is full, delete old run downloads.
- **Undoing a change:** never install a lower versionCode over the app (it cannot install without uninstalling, and uninstalling erases data). An undo is a new build with a higher number.

## 4. How changes are made (the "tool" method)
- The repo is private, so Claude cannot read it. The owner opens a file on GitHub → **Copy raw file** → pastes it (or attaches it).
- Changes come as a **one-time workflow tool** (a `.yml` file in `.github/workflows`):
  1. It may first write a brand-new Kotlin file (with a `cat > file <<'KT'` block).
  2. A Python step **checks every line it will change**. If any line is not exactly as expected, it prints **NOTHING WAS CHANGED** with the reasons and changes nothing.
  3. It makes the changes, raises the version by 1, and commits.
  4. The owner deletes the tool afterwards by hand (a workflow cannot edit or delete files in `.github/workflows`).
  5. Then the owner runs **Build APK** (Trial days 0).
- **After editing a tool file, use "Run workflow".** "Re-run jobs" on an old run repeats the OLD version of the tool.
- Tools that edit the same file can run in any order as long as they change different lines.
- Useful GitHub links:
  - New file: `https://github.com/omkarmaturkar/Mahalakshmi-Dental-Clinic-V2/new/main/<folder>?filename=<name>`
  - Edit file: `https://github.com/omkarmaturkar/Mahalakshmi-Dental-Clinic-V2/edit/main/<path>`
  - Delete file: `https://github.com/omkarmaturkar/Mahalakshmi-Dental-Clinic-V2/delete/main/<path>`
  - Actions: `https://github.com/omkarmaturkar/Mahalakshmi-Dental-Clinic-V2/actions`
- Only `build.yml` should stay in `.github/workflows`. Delete any tool that is still there once it has run.

## 5. Files

**Core & navigation**
| File | What's in it |
|---|---|
| MainActivity.kt | `Screen` sealed class (Home, Schedule, Finance, BackupPage, ClinicSettings, FollowUps, Patients, PatientForm, PatientDetail, VisitForm, RxForm). `ClinicApp`: drawer (phone) / permanent menu (≥900dp), provides `LocalNotify`, `LocalGo`, `LocalQuickActions`; hosts `QuickActionsHost` when "+" is tapped. Bottom bar shows on **every screen except PatientForm and RxForm**; inside other screens the tab you came from stays lit; BackupPage/ClinicSettings light none. TrialGate, NavArea, ScreenHost. Refreshes all screens at midnight; removes leftover data at start. |
| SwipeTabs.kt | `TabPager`: swipe between Home/Patients/Schedule/Finance; `tabIndexOf()` |
| Theme.kt | `AppTheme` (Light/Dark/Auto), `Brand` colours, `ClinicTheme` (wraps content in `ScaledForTablets`), `Poppins`, `AppTypography`, building blocks (section 9) |
| Menu.kt | `AppMenu` (Quick actions item at top when available, Home, Patients, Schedule, Finance, Pending dues, **Follow-ups**, Settings…), `SectionTopBar`, `BottomNavBar` (dark pill; **green "+" button** between Patients and Schedule) |
| SplashScreen.kt | Logo animation (fits in 1.1 s) |
| CrashGuard.kt | Saves last_crash.txt; CrashReportScreen |

**Home, lists, money**
| File | What's in it |
|---|---|
| TodayScreen.kt | `TodayScreenContent` (renamed from TodayScreen): greeting, dark next-patient card, **RecentPatientsCard**, **FollowUpsEntry**, compact metric cards, quick actions (New patient · Book · Treatment · Payment), tablet two columns. `PatientPickerDialog`, `HomeIcons`, `HomeColors`, `recallMessage()`. Old recall section code is still in the file but no longer shown. |
| HomePatients.kt | `TodayScreen()` wrapper: Home uses **ClassicTypography (Lato)** and loads the patient-ID prefix. `Lato` and `Merriweather` font families. `RecentPatientsCard` (5 most recent; calendar button swaps in Today/Yesterday/This week/This month/Last month/picked dates until ×; scrolls inside after 5). `patientsSeen()` |
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
| PatientEdit.kt | Patient form |
| CaseData.kt | Treatment, Payment, notes, photos, bookings; CRUD; `accountOf()`; `buildHistory()`; `priceFor()/rememberPrice()` |
| DentalChart.kt | `StatusChart` (colours by status; tap a tooth) and `ToothChart` (choose teeth) |
| Prescription.kt | Prescription screen + PDF. Chief complaint, findings, Patient ID/OPD box, "Repeat last prescription" |
| RxNotes.kt | `ComplaintCard` (chips: Pain, Swelling, Sensitivity, Bleeding gums, Broken tooth, Loose tooth, Food lodging, Bad breath, Missing tooth, Check-up, Cleaning, Braces; "Since": Today, 2–3 days, 1 week, 1 month, Long time) and `FindingsCard` |
| QuickActions.kt | `LocalGo`, `LocalQuickActions`, `RecordJump`, `QuickPlusButton`, `BottomSheetFrame(top)`, the "+" sheet (`QuickSheet` + `QuickActionsHost`), `FinishVisitSheet`, `RepeatLastButton` |
| AppHelpers.kt | `ScaledForTablets`, your own treatment names (`customTreatments`, `rememberTreatment`, `forgetTreatment`), `SquareIconButton`, Patient ID / OPD ID (`loadPatientPrefix`, `patientCode`, `opdId`) |

**Clinical modules (full-screen windows from the patient file)**
Perio.kt (gum chart), MouthStatus.kt (tooth conditions/surfaces), Ortho.kt (braces case sheet), Education.kt (8 topics in English/Hindi/Marathi). Shown as compact rows by `ClinicalRecordsCard`.

**Bills, PDFs, settings**
| File | What's in it |
|---|---|
| Bills.kt | Full bill (`invoicePdf`: each treatment line shows its visit's OPD No.), payment receipt (OPD No. of the payment day under the title), estimate. `patientRows()` prints Patient ID = MDC-…. `visitDays()`, `amountInWords()`, `rupees2()` |
| Pdf.kt | PdfBuilder (title, partyBox, patientBox, table, row, text, space, summary, notes, signature, save), PdfCell, SummaryLine, PdfActionsDialog, DocIcons, letterhead |
| ClinicSettings.kt | ClinicInfo, `clinicInfo()`, `saveClinicInfo()`, doctors, `DEFAULT_CLINIC_NAME` |

**Data, backup, security**
Database.kt (ClinicDb, `getPatients()`, stats), ExtraTables.kt (core tables, `deletePatientExtras`, `deleteFeatureData`, `removeLeftoverData`; FEATURE_TABLES includes the record tables), Backup.kt (folder backup), DriveBackup.kt, BackupScreen.kt, Security.kt (app lock, AutoBackup, DriveBackupWorker), Trial.kt / TrialConfig.kt.

## 6. Database

**Core tables:** patients, visits (old), payments, settings, prescriptions, rx_templates, treatments, case_notes, case_photos, appointments, doctors.
- treatments.status: 0 To do (planned) · 1 In progress · 2 Done. Planned = estimate only, not billed.
- appointments.status: 0 booked · 1 arrived/done · 2 cancelled. `saveBooking()` replaces any earlier booking (one per patient).
- Patient ids use AUTOINCREMENT, so a deleted patient's number is never reused (Patient IDs stay permanent).
- **prescriptions** columns: id, patient_id, visit_id, date, diagnosis, medicines (JSON), advice, **complaint, findings** (the last two are added with ALTER TABLE by `ensureRxColumns()` the first time).

**Feature tables** (each made on first use with `CREATE TABLE IF NOT EXISTS`; all in the same file, so backups include them):
| Table | Columns |
|---|---|
| sittings | id, treatment_id, patient_id, position, name, fee, done, done_at, payment_id, notes |
| perio_exams | id, patient_id, date, data (JSON per tooth), notes |
| tooth_status | key (patient_id, tooth); decay, filled, crown, rct, implant, missing, broken, note, updated_at |
| ortho_cases / ortho_visits / ortho_payments | braces case sheet, visits, linked payments |
| **treatment_records** | id, patient_id, day (epoch day of the visit), notes, created_at |
| **record_items** | treatment_id (PK), record_id, patient_id — which treatment is in which record |
| **record_payments** | payment_id (PK), record_id, patient_id — payments received inside a record |

**settings keys:** `price:<treatment>` (remembered fee) · `treatments:custom` (your own treatment names, one per line) · `patient_id_prefix` (MDC) · `followup:<patientId>` (hidden from Follow-ups until this time) · clinic info.
**Shared preferences:** "appearance" (theme), app lock, "education" → "lang".
- Deleting a patient removes everything of theirs, including records. `removeLeftoverData()` also cleans record links whose treatment or payment no longer exists.

## 7. Screens & navigation
- Home→TodayScreen · Patients→PatientsScreen · Schedule→ScheduleScreen · Finance→FinanceScreen · PatientForm→PatientEditScreen · PatientDetail→PatientFileScreen · VisitForm→PatientFileScreen(startAdd = true → opens today's record) · RxForm→PrescriptionScreen · BackupPage · ClinicSettings · **FollowUps→FollowUpsScreen**
- **Bottom bar on every screen** except the patient form and prescription form (they have their own Save bar).
- **"+" quick sheet** (from the bottom bar, or "Quick actions" in the side menu on tablets held sideways). Opens **at the top of the screen** so the keyboard never covers it.
- Windows that open over the patient file (treatment record, gum chart, mouth status, braces, education) cover the bar; back closes them.
- **Tablets:** `ScaledForTablets` draws everything 1.3× larger when smallest width ≥ 720dp (1.15× from 600dp). Tab S9 sideways shows the permanent side menu and two columns (Home, patient file); upright uses the phone layout.

## 8. Features

### Before Nov 2026 (up to v30)
- Trial system (TRIAL_UNTIL; clock-rollback protection; TrialEndedScreen; warning in last 5 days).
- App lock: 4-digit PIN (salted SHA-256) + fingerprint; lock after 0/1/5 min; forgot PIN uses phone lock; 5 wrong tries → 30 s wait.
- Instant backup when leaving the app if data changed: folder backup at once, Drive backup at once plus a WorkManager safety copy after 2 minutes.
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
- **Fonts:** Home only uses Merriweather (names, titles, numbers) + Lato (other text). Fixed-weight Merriweather (the variable font rendered too thin). The rest of the app still uses Poppins — waiting for the owner's decision.
- Small text on Home is 13sp in darker grey (Brand.Slate).

### Patient ID and OPD ID (v37+)
- **Patient ID** = prefix + patient number padded to 3 digits, e.g. **MDC-001**. Permanent (numbers are never reused).
- **OPD ID** = Patient ID + visit date (DDMMYY), e.g. **MDC-001-191126** for 19 Nov 2026. A future record gets its future date.
- Shown on: patient file top bar, patient lists (searchable), record cards and record screen, record bill ("OPD No."), full bill (each treatment line), payment receipt (under the title, OPD of the payment day), prescription (screen and PDF box: Rx No., OPD No., Date, Consultant), bills/receipts/estimates ("Patient ID").

### Treatment names
- Your own names (e.g. "Secure 2", "Set 1") are saved and shown as chips after the usual ones. "+ Add new" adds a name and usual fee in advance ("Save & add another"). Typing a usual treatment with a fee sets its fee. Chip area shows 3 rows and scrolls.

### Prescriptions
- Sections on the form: **What the patient told us** (chief complaint: chips + "Since" + your words) → **What we found** (on examination, optional) → Diagnosis (pre-filled from today's treatments) → Repeat last prescription (only for a new prescription when an older one exists; copies medicines + advice, not the complaint) → Templates → Medicines → **What we told the patient** (advice).
- PDF order: letterhead → PRESCRIPTION → Patient details / Prescription details box → medical alerts (red) → Chief complaint → On examination → Diagnosis/Treatment → Rx table → "1-0-1 means…" → Advice → Next visit → signature. Empty sections are not printed.
- The app does not check doses, interactions or allergies; it only shows medical alerts.

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

## 9. Shared building blocks (signatures confirmed by builds)
- `AppCard(modifier, onClick = …, padding = …) { … }` · `Pill(text, bg, fg, icon?, modifier)` · `SectionHeader(title, modifier)` · `FormCard(title, subtitle) { … }` · `EmptyState(title, message)`
- `SoftButton(text, icon?, modifier, bg =, fg =, height =) { … }` · `GradientButton(text, modifier, icon?, enabled =, height =) { … }` · `CircleIconButton(icon, onClick, bg =, tint =, size =)` · `SquareIconButton(icon, label) { … }`
- `AppField(value, onChange, label, modifier =, icon =, type =, lines =, caps =, prefix =)` · `ChoiceChip(text, selected) { … }` · `Segmented(options, selected) { … }` · `ToggleRow(title, subtitle, checked) { … }`
- `AppTopBar(title, subtitle, onBack) { actions }` · `ConfirmDialog(title, message, confirmText =, onConfirm =, onDismiss =)` · `BusyDialog(text)`
- `DatePick(epochDay, onDismiss =) { day -> }` · `BookingDialog(initial, defaultReason, onDismiss, onCancelBooking) { day, time, reason -> }` · `ReceivePaymentDialog(balance, onDismiss) { amount, mode -> }` · `PatientPickerDialog(db, title, onDismiss) { patient -> }`
- `TreatmentDialog(db, patientId, initial, presetTooth, defaultStatus = STATUS_ONGOING, onDismiss, onSaved)` · `TreatmentProgress(db, patientId, onlyIds = null)` · `StatusChart(statusByTooth) { tooth -> }`
- `TreatmentRecordsCard(db, patientId, openNew) { onOpened }` · `RecentPatientsCard(db, go)` · `FollowUpsEntry(db) { open }` · `FollowUpsScreen(db, go, onBack)` · `ComplaintCard(text) { }` · `FindingsCard(text) { }` · `QuickActionsHost(db, go) { onClose }` · `NewTreatmentDialog(db, onDismiss, onAdded)`
- `PdfActionsDialog(title, subtitle, phone, message, build = { ctx -> file }, onDismiss)` · `PdfCell(text, width, size, color, style, align)` · `SummaryLine(label, value, strong =, color =)`
- Helpers: `rs()`, `numText()`, `fmtDate(ms)`, `fmtDay(day)`, `fmtDayLong(day)`, `relativeTime(ms)`, `todayEpochDay()`, `epochDayOf(ms)`, `withDay(ms, day)`, `startOfToday()`, `patientCode(id)`, `opdId(patientId, day)`, `db.loadPatientPrefix()`, `LocalNotify.current("msg")`, `LocalGo.current(screen)`, `DataBus.version` / `DataBus.changed()`

## 10. Version history
| Version | What changed |
|---|---|
| ≤30 | See section 8 (before Nov 2026) |
| 31–32 | Built outside these notes (compact clinical rows `ClinicalRecordsCard` appear here) |
| 33 | Treatment records (job cards) |
| 34 | Teeth chart inside each record; Delete this record |
| 35 | Home: recent patients + calendar, compact cards, tablet two columns, Merriweather + Lato on Home |
| 36 | Fixed-weight Merriweather; darker, larger small text on Home |
| 37 → now (each tool +1, in the order they were run) | tool-v37 (tablet size, own treatment names, records for any date, Patient ID / OPD ID) · tool-rx (IDs on prescriptions) · tool-opd (OPD on receipts and full bill) · tool-treatments (+ Add new, scrolling chips) · tool-menu (bottom bar everywhere) · tool-complaint (complaint + findings) · tool-quick ("+", Finish visit, Repeat last prescription) · tool-sheetfix (actions hide while typing) · tool-followups (Follow-ups + name Mahalaxmi) · tool-readable (clearer record text) · tool-sheettop ("+" sheet at the top) |

### Tool checklist (confirm each one has run; delete the tool file afterwards)
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
- **Tool marker checks:** extension functions are written `fun ClinicDb.name(` — check for `name(`, not `fun name(` (tool-treatments failed once because of this).
- In tool scripts apply only one text-replacement rule per line (rules can chain).
- A workflow's GITHUB_TOKEN cannot create, change or delete files in `.github/workflows`.
- GitHub web editor error "File could not be edited" = page out of date; reload, paste again, commit.
- **Keyboard and pop-up windows:** on the clinic phone, `SOFT_INPUT_ADJUST_RESIZE` did not move a Compose Dialog above the keyboard. Any sheet with typing must open at the top (`BottomSheetFrame(top = true)`).
- **Variable fonts** rendered only their thinnest weight on the phone: always use fixed-weight TTF files.
- **Readability:** 12sp `Brand.Sub` grey is hard to read on the phone. Use ≥13sp and `Brand.Slate`/`Brand.Ink` for anything important.
- **Each device has its own database.** Phone and tablet do not sync; a restore replaces everything on that device.
- The clinic name is saved per device (Clinic & doctors); changing code defaults does not change it.
- Never change the package `com.mahalakshmi.dental`.
- Version lines in `app/build.gradle.kts` must stay exactly `versionCode = N` and `versionName = "N.0"` (tools look for this).
- Long code messages can get cut off: send code in parts, or let the tool write new files itself.
- Incognito chats are not saved. Keep these notes in the repo and update them after each round.

## 12. Pending & ideas
- **Fonts:** decide whether Merriweather + Lato go to the whole app (switch `AppTypography` in Theme.kt) or Merriweather for titles + Poppins for text.
- **Readability:** offer to make all grey text in the app one shade darker (one change in `Brand.Sub`).
- **Clinic name:** change it in Clinic & doctors on the phone and on the tablet after installing the name fix.
- **Google Drive backup:** finish the setup, then test a restore on a second device:
  1. Enable the Google Drive API. 2. Google Auth Platform → Data Access: add drive.file. 3. Audience: Publish app (in Testing, Google cancels access after 7 days). 4. In the app: disconnect and connect Drive, then Back up now. The SHA-1 is in the build log, step "Show SHA-1".
  - Send DriveBackup.kt to check how two devices on one Google account name their backups.
- **Tablet layouts** for Patients, Schedule and Finance (Home and patient file are done).
- Next fixes from the 6 Nov review: No. 6 load data in the background (new screens already do), No. 13 one shared title/dialog style. (No. 14 compact rows appears done.)
- **Ideas from the BestoSys review (all work offline), in suggested order:** allergy alert in prescriptions + vitals; patient journey messages (thank-you with care instructions, birthday wishes – needs date of birth, Google review request); consent forms with on-screen signature + doctor signature/stamp on PDFs; lab work tracker; monthly growth report + expenses; UPI QR on bills/receipts; home-screen widget with today's patients. Later: memberships, waiting-room display, family accounts, price list with special rates, import from Excel. Skip: online booking/patient app, payment gateway, automatic WhatsApp sending, leads/campaigns, insurance.
- BestoSys print ideas to consider: print on plain paper or pre-printed letterhead with adjustable margins; Hindi/Marathi prescription instructions; medicines suggested per treatment; leave a medicine off the print; pharmacy details at the bottom of prescriptions (doctor to confirm if required).
- Other ideas: larger-text switch in the menu; Call/WhatsApp buttons on patient rows; end-of-day summary; receptionist mode; GST billing + Tally export; inventory with expiry; doctor commission (share_percent exists); ABHA/ABDM; DPDP privacy kit (law enforced from May 2027); multi-branch with cloud sync (Firebase) – this would also sync phone and tablet.
- The doctor to review the Hindi/Marathi lines in Education.kt.
- Windows app: discussed, not wanted.

## 13. Rules for Claude
- The repo is private: **ask for the current contents of a file before changing it**, even if it was shown earlier. Never rebuild a whole file from memory; work only from the pasted file (or a file Claude wrote itself in the same chat).
- Send only the files a change needs. Prefer a new self-contained file plus one one-time tool that checks every line, raises the version and commits. The tool should write new files itself when possible.
- **Always give the GitHub link together with the code** it belongs to (create/edit/delete links, Actions page). Explain steps simply, in short numbered steps.
- **Show a preview before building any new screen**; the owner picks the design. Keep everything simple: one screen, one tap, no new menus unless needed. The owner prefers design A (clean, light cards). The Heritage redesign was rejected; don't bring it back.
- Before any app-wide visual change, change one screen first and let the owner check it **on the phone**.
- Colour rule: green = brand and money received, gold = attention, red = dues and danger, grey for everything else. Important text must be easy to read (≥13sp, dark).
- Never ask for or send `clinic.keystore`; never write signing passwords into a file. Never change the package name.
- When a fix doesn't work, say so plainly and change the approach rather than repeating it.
- The owner asked that the main chat is never closed — but incognito chats are not kept, so these notes must be updated in the repo after each round.
