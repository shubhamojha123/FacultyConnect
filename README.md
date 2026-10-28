# FacultyConnect: Faculty Lecture Exchange & Coordination Platform

Native Android app (Kotlin + XML, Material 3) for faculty to post lecture-replacement / extra-lecture requests,
chat in-app, confirm assignments safely, and see confirmed lectures in a personal schedule.
Backend: Firebase Authentication, Cloud Firestore, Firebase Storage, Firebase Cloud Messaging.

> **Verification status (2026-10-24):** a clean debug build, JVM unit tests, and lint completed locally. The APK was
> installed on an Android 16 emulator and the login screen opened. Firebase is configured for this checkout, but
> sign-in and Firestore-backed workflows still need real test accounts and deployed backend services to verify end to end.

---
## 1. Features
| Area | What is implemented in the source |
|---|---|
| Auth | Splash, login, register (with optional photo), forgot password, password toggle, persistent session, logout |
| Verification | Firebase email verification followed by manual administrator approval |
| Feed | Live Firestore feed, default = Available, status chips, filters (type, department, subject, date), search (name/subject/class), pull-to-refresh, pagination (limit grows on scroll), loading/empty/error/offline states |
| Posts | Create / edit (only while Available) / close; date + time pickers; validation; duplicate-submit guard; stable post id so retries cannot duplicate |
| Contact Faculty | One primary button per card; sheet with **in-app chat** (preferred), **email intent**, **Teams / contact link** (only if that person added one) |
| Messaging | Messages tab, faculty directory, 1-to-1 + lecture-linked chats, live listeners, unread counts, previews, read receipts, pending/failed states + retry, older-message paging |
| Assignment | Poster confirms from the lecture chat; Firestore **transaction**; Available to Assigned; cancel / close; confirmation dialogs |
| Schedule | Upcoming / Past tabs, confirmed assignments only, one document per post so no duplicates |
| Notifications | In-app list (read/unread, mark all, deep link), tab badges, FCM push (needs Cloud Function) |
| Profile | View/edit, photo upload (validated, compressed), notification preferences, logout |
| Coordinator | Role from an admin-set custom claim; may close **Available** posts. No analytics (out of scope) |
| Design | Navy/teal palette, light + dark colour sets, rounded cards, text status labels (never colour alone) |

## 2. Project structure
```
FacultyConnect/
  app/src/main/java/com/facultyconnect/app/
    model/        Firestore models (Models.kt)
    domain/       Pure-Kotlin rules: LectureValidator, AssignmentRules/StatusRules, ConversationIds  (unit tested)
    data/         Repositories (Auth, User, Post, Chat, Assignment, Notification), Fb, ErrorMapper, Session
    ui/           auth/, home/, post/, chat/, messages/, directory/, schedule/, notifications/, profile/, common/
    push/         FcmService, NotificationHelper
    util/         Ext, TimeFormat, Avatar, ImageUtils, NetworkUtils, StatusStyle, DeepLink
  app/src/main/res/  layouts, drawables, values (+ values-night), menu, navigation
  app/src/test/      JVM unit tests
  firebase/          firestore.rules, storage.rules, firestore.indexes.json
  functions/         Cloud Function (push) + admin scripts (setRole.js, approveUser.js)
  rules-tests/       Firebase-emulator rules tests (not run yet)
  firebase.json
```
Architecture: Fragments/Activities (ViewBinding) -> ViewModels (StateFlow) -> Repositories -> Firebase. One `MainActivity`
hosts a Navigation graph with the 5 bottom-navigation destinations.

## 3. Requirements
* Android Studio **Koala (2024.1.1) or newer** (needs AGP 8.5 / JDK 17+; use a JDK supported by Gradle 8.7, such as 17 or 21). macOS is fine.
* Android SDK Platform 34. Device/emulator with Android 8.0 (API 26) or newer. Internet for first Gradle sync.
* A Google account for Firebase. (Push needs the Blaze plan; everything else works on the free Spark plan.)

## 4. Open, sync, run
1. Unzip, then in Android Studio: **File > Open** and choose the `FacultyConnect` folder.
2. Let Gradle sync, or run `./gradlew assembleDebug`. The Gradle 8.7 wrapper scripts and JAR are included. Set Android Studio's
   Gradle JDK to 17 or 21. The wrapper allows 120 seconds for the initial distribution download.
3. `app/google-services.json` is intentionally ignored by Git and must come from your Firebase project. Without it, the app
   still builds and opens a "Firebase is not configured" screen (no fake data).

## 5. Firebase setup checklist
1. https://console.firebase.google.com > **Add project**.
2. **Add app > Android**, package name exactly `com.facultyconnect.app`.
3. Download **google-services.json** and put it in `FacultyConnect/app/` (same folder as `app/build.gradle.kts`). Re-sync Gradle.
4. **Build > Authentication > Get started > Email/Password > Enable.**
5. **Build > Firestore Database > Create database** (production mode, a region near you).
6. **Build > Storage > Get started** (Storage may require the Blaze plan in some regions/new projects; profile photos are optional; without Storage the app works but photo upload fails with a clear message).
7. **Faculty registration:** Users register and verify their email. An administrator reviews the account and sets `users/{uid}.verified` to `true` in Firebase before the user taps **Check again**.
8. Deploy rules + indexes with the Firebase CLI (`npm i -g firebase-tools`, `firebase login`, `firebase use <project-id>`):
   ```
   firebase deploy --only firestore:rules,firestore:indexes,storage
   ```
   (Or paste the three files into the console. Indexes take a few minutes to build; until then queries show "index missing".)
9. **Push notifications (optional, Blaze plan):** `cd functions && npm install && cd .. && firebase deploy --only functions`.
10. **Password reset:** On the sign-in screen, tap **Forgot password?**, enter the account email, and tap the link Firebase sends. This uses Firebase Authentication directly and does not require a custom OTP function or SMTP secrets.
   FCM itself needs no extra console step; the app registers its token in `users/{uid}/devices`.
11. **Test accounts:** register two faculty accounts in the app (see section 6).

### Registration and verification workflow
* Registering sends a verification email. The user opens the link and taps **Check again**. An administrator must then manually set `users/{uid}.verified` to `true` in Firebase before the account can access faculty data.
* **Coordinator role:** `node functions/scripts/setRole.js user@x.edu coordinator`, then the user signs out/in. Roles are Auth custom
  claims; the app and the rules never trust a Firestore field for roles. The "designation" a user picks is just a label.
* For test accounts, use any verified email address and approve the profile manually in Firebase.

## 6. How to test (two accounts, two devices/emulators)
1. Register **Faculty A** and **Faculty B** with different emails; verify email; get both approved (section 5).
2. A: Home > **New request** > fill form > Publish (try Replacement and Extra). Both appear in the feed (default filter *Available*).
3. B: Home > on A's card tap **Contact Faculty** > **Message in FacultyConnect** > send "Hello".
4. A: Messages tab shows the conversation with a badge/preview; open it. B sees "Read".
5. A: in that lecture chat, tap **Confirm assignment** > Confirm. Post becomes **Assigned**.
6. Both: Schedule > Upcoming shows the lecture. B also gets a notification (and a push if functions are deployed).
7. A: open the post > **Cancel assignment** (B is notified, post is Available again) or **Close post**.
8. Directory: Messages > **New message** > search a name/department/subject > Message.
9. Offline test: enable airplane mode, send a message: it shows "Sending..." and is delivered on reconnect (it never says "Sent" early).
10. Profile: edit profile / photo, toggle push preferences, log out.
11. Security: run the emulator rules tests (`cd rules-tests && npm install && npm test`), including "Faculty C cannot read A-B messages".

## 7. Firestore data model
```
users/{uid}                 fullName, email, department, designation, subjects[], photoUrl, teamsLink, verified,
                            notifyMessages, notifyAssignments, createdAt        (no role field; roles are Auth claims)
users/{uid}/devices/{token} createdAt                                           (owner-only; read by the Cloud Function)
lecturePosts/{postId}       posterId + snapshot(posterName, posterDepartment, posterPhotoUrl), type, subject, className, room,
                            reason, startAt, endAt, status(available|assigned|closed), assignedToId?, assignedToName?, createdAt, updatedAt
assignments/{postId}        postId, posterId, posterName, assigneeId, assigneeName, participantIds[2], status(confirmed|cancelled),
                            snapshot(type, subject, className, room, startAt, endAt), confirmedAt, updatedAt
conversations/{cid}         participants[2], participantNames{}, participantPhotos{}, postId?, postSubject?, lastMessage,
                            lastMessageAt, lastSenderId, unread{uid: n}, createdAt
conversations/{cid}/messages/{mid}   senderId, text, readBy[], createdAt
notifications/{id}          toUid, fromUid, fromName, type, title, body, refType, refId, read, createdAt
```
* **Conversation id** is deterministic: `uidA_uidB` (sorted) for direct chats and `uidA_uidB__postId` for lecture chats, so the same two people and
  post can never produce duplicates. Messages are a sub-collection of their conversation.
* **Assignment id = post id**, so a post can have at most one assignment document. The confirm/cancel/close operations are single Firestore
  transactions, and the rules use `getAfter()` to require the post change and assignment change to be written together.
* The assignment stores a *snapshot* of lecture details (needed for the schedule query). Posts are not editable while assigned, so it cannot drift.
* **Notification dedupe:** one "new message" notification per conversation per recipient (overwritten); assignment notifications have fixed ids per event type.
* **Indexes** (firebase/firestore.indexes.json): lecturePosts (status, createdAt), (type, createdAt), (status, type, createdAt); conversations
  (participants array-contains, lastMessageAt desc); notifications (toUid, createdAt desc).

## 8. Security summary (see firebase/*.rules)
* Everything requires sign-in **and** `users/{uid}.verified == true` (except reading/creating your own profile).
* Users cannot set `verified` or any role at registration; only an administrator can approve an account in Firebase.
* Only the poster edits/assigns/cancels; coordinators may close Available posts; posts and messages are never deleted.
* Messages: only conversation participants read/write; `senderId` must equal the auth uid; text length-limited; recipients can only add themselves to `readBy`.
* Storage: only the owner uploads `profileImages/{uid}/*`, images under 5 MB. No service-account credentials exist in the app.

## 9. Troubleshooting
| Symptom | Fix |
|---|---|
| "Firebase is not configured" | Put `google-services.json` in `app/`, re-sync, rebuild |
| Gradle sync: plugin/AGP error | Use Android Studio Koala+ ; JDK 17+ ; File > Settings > Gradle > Gradle JDK |
| "A Firestore index is missing" | Deploy `firestore.indexes.json` and wait for it to finish building |
| "You don't have permission" everywhere | Rules not deployed, or the account isn't verified yet (approve it) |
| Stuck on "Awaiting approval" | Domain placeholder not set in the rules; approve the user in the console |
| Verification email missing | Check spam; tap Resend; Authentication > Templates in console |
| No push | Functions need Blaze + deploy; allow notifications on the device (Android 13+); user must have signed in on that device |
| Photo upload fails | Enable Storage and deploy `storage.rules` |

## 10. Known limitations / not implemented
* Authenticated workflows and Firebase-backed data flows have not been exercised with real test accounts. Only the login screen was opened on the emulator; other screen layouts were compile/lint checked.
* No file attachments on posts or chats (optional in the brief; not implemented).
* Search is client-side over loaded data (Firestore has no substring search); directory loads up to 500 verified faculty.
* Poster/participant name and photo are snapshots; they do not update on old posts/conversations when someone edits their profile.
* A participant can technically manipulate the other person's `unread` counter (rules restrict *which fields*, not values). A trusted
  Cloud Function would be needed to make counters tamper-proof. Likewise any verified faculty could spam notifications to another faculty member.
* Coordinator tooling is minimal (close Available posts). No dashboards/analytics.
* Strings are hard-coded in layouts/Kotlin (no localisation). Photo EXIF rotation is not applied. Message edit/delete is not offered.
* Push needs a deployed Cloud Function (Blaze plan). Push payloads intentionally carry no message text.

## 11. Verification status
| Item | Status |
|---|---|
| Gradle configuration / clean Kotlin compile / debug APK | **PASS** (`./gradlew clean assembleDebug`, Gradle 8.7, JDK 21, Android SDK 34) |
| JVM unit tests (`LectureValidatorTest`, `AssignmentRulesTest`) | **PASS**, 15 tests, 0 failures |
| Android lint | **PASS**, 0 errors; 188 warnings remain |
| Rules emulator tests | **Written, NOT run** |
| Emulator install and launch | **PASS** on Android 16 emulator; login screen opened |
| Firebase Auth, Firestore posts/chat/assignments/schedules/notifications, Storage, FCM | **Not verified end to end**; requires configured services, test accounts, and deployed rules/indexes/functions |
| Kotlin/XML screens | All app Kotlin sources and XML resources compiled; lint ran. Only login was manually viewed on the emulator. |
| Build command | `./gradlew clean assembleDebug testDebugUnitTest lintDebug` |
