# Changelog

## 1.10.0 (2026-10-26): one Search, marked results, a Back arrow

- **Search and Strong's are one tab.** The same box finds words in the current translation,
  goes to a reference like John 3:16, and looks up Strong's by number (G26), English word
  (love) or transliteration (agape). Matching Strong's entries come first (three, with "Show
  all"), then the verses.
- **Your search shows in the reader.** Tapping a verse in the results opens it with the verse
  tinted and the words you searched for picked out. Going to a reference, or to a verse from
  a Strong's entry, tints the verse. The marks go when you swipe to another chapter.
- **The button at the top left is now a Back arrow** instead of Hide. It goes back one screen:
  to your search results (query and place kept), to the Strong's entry you came from, to the
  previous tab, or to the chapter you were on before picking another. It is faded when there
  is nowhere to go back to. Double-tapping the top-left corner still hides the reader, and so
  does Back with nowhere left to go.

## 1.9.1 (2026-10-20): notes on saved verses

- A saved verse or chapter now shows the notes you made on it in the reader, under its own
  notes, each marked with the verses it's on. Tap one to edit it; Done brings you back to the
  saved item. The note count and the filter on the Saved list include them too.

## 1.9.0 (2026-10-19): easier way into the dictionary

- To open the reader from the dictionary, **hold Search for three seconds**: a number pad
  appears. Type your code and tap **OK**. This replaces the old way (hold Search, type the
  code in the search box, hold Search again), which many people found hard to get right.
- A wrong code just closes the number pad and leaves the dictionary as it was. Back, or a
  tap outside the pad, closes it too.
- Your code doesn't change. On a new install, the same three-second hold opens setup.
- Anyone watching can see the number pad appear, so open the app out of sight.

## 1.8.0 (2026-10-04): the direct version starts as the dictionary

- A new install of the version from kanaiic.com and GitHub starts **disguised as
  the dictionary**. The first time it opens (and each time until you set a code) a message
  explains how to set your code and how to get in, with a **Set up now** button.
- That version now shows as **Dictionary**, with the dictionary icon, in the phone's app
  settings, the installer and other system screens. The Google Play version still starts as
  Kanaiic Reader and shows that name there, matching its store listing.
- The dictionary's **Search** button is now a proper filled button, so it's clearer that it
  can be pressed and held.
- Phones already set up keep whatever look they have now.

## 1.7.0 (2026-10-04): PDF viewer, disguise offer, Strong's tab

- The dictionary disguise gains a **PDF** tab next to Dictionary, Thesaurus and Notes. Tap
  Open, pick a PDF saved on the phone, scroll through it, pinch to zoom. It stays open while
  you look words up in the other tabs.
- It needs no permissions: files come through the phone's own file picker, are only read, and
  the app keeps no list of what was opened.
- Like the Notes tab, it is part of the disguise and is not locked: keep private documents out
  of it.
- Password-protected PDFs aren't supported; the app says so.
- After you set your code on a new install, the app asks whether to **disguise it now** or
  later. Disguising now first shows how to open it as the dictionary. Settings now always
  starts with how to open the app, and each disguise option shows its own way in.
- A **Strong's** tab in the reader searches the whole Hebrew and Greek lexicon by number,
  English word or transliteration. The button on selected verses now says "Strong's" (it said
  "Original", which was cut off on some screens).

## 1.6.0 (2026-10-04): both versions work the same way

- The direct version (`dictionary.apk`) now works exactly like Kanaiic Reader from Google
  Play: a new install opens as **Kanaiic Reader** with a plain code screen, and Settings >
  Disguise turns it into the dictionary. Before, the direct version could only ever be the
  dictionary.
- Phones that already look like a dictionary stay that way after updating; nothing changes
  for them.
- On both versions the phone's app settings list the app as "Kanaiic Reader", even while it
  looks like a dictionary. If you need the disguise, switch to it right after setting your
  code.

## 1.5.0 (2026-10-04): the dictionary is the only disguise

- The older disguises are gone. Every disguise someone has
  learned about is one they can recognise on a searched phone, so offering several made the
  app easier to find, not harder. The direct version is always the dictionary; Kanaiic Reader
  shows as itself or as the dictionary.
- Phones on one of the removed disguises become the dictionary when they update, before the
  app is even opened. Your code, notes, highlights and saved verses stay. To open the reader:
  hold Search until the box clears, type your code, hold Search again. The saved sudoku game
  is deleted so it doesn't show the disguise was used.
- The dictionary's Notes tab is an ordinary notepad (it never opened the reader).
- Settings no longer shows a Disguise section on the direct version.
- The user guide also covers Strong's numbers now.

## 1.4.0 (2026-10-04): Strong's numbers

- Select one or more verses and tap **Original**: each word the King James Version tags with a
  Strong's number, showing the Hebrew or Greek word, its transliteration, the number and the
  KJV's usual renderings. Works whichever translation you are reading.
- Tap a word for its **Strong's entry**: the original word, pronunciation, Strong's
  definition and his list of KJV renderings (numbers inside it link to their entries), how
  often the KJV translates it each way, and every verse that uses it, shown in your current
  translation. Tap a verse to go there.
- Type a Strong's number in Search ("H430", "g26") to open its entry.
- Sources: Strong's Hebrew and Greek dictionaries (1890, public domain; Open Scriptures and
  Ulrik Petersen XML editions) and the Strong's-tagged KJV from eBible.org. The TWOT numbers in
  the Hebrew file are copyrighted and left out. All of it ships in the encrypted packs (about
  3 MB; the APK is now about 17 MB). The source KJV doesn't tag words that go untranslated, so a few numbers
  (e.g. H853) show only the handful of verses where it does.
- The setup screen's "anyone watching can see the digits" warning no longer mentions the
  old disguise's display.
- Builds use less memory (Gradle 2 GB, Kotlin daemon 1 GB).

## 1.3.0 (2026-10-04): Kanaiic Reader, and a dictionary disguise

- Two versions from the same code:
  - **Kanaiic Reader** (for Google Play) opens as itself, with its own name and icon and a
    plain screen asking for your code. Disguises are optional in Settings. Its store page
    describes the disguises, so they only hide it from a glance.
  - **The direct version** (GitHub, kanaiic.com) stays fully disguised and now starts as a
    dictionary. Phones updated from 1.2 keep the disguise they had.
- New **Dictionary** disguise: a working offline dictionary and thesaurus built from
  Princeton's WordNet 3.0 (about 147,000 words with definitions, examples, synonyms, related
  words and opposites; "ran" finds run), plus a Notes tab, recent lookups and a word of the
  day. To open the reader: hold Search until the box clears, type your code, hold Search
  again. A dictionary also explains why the app is about 14 MB.
- Setting up a code now explains the way in for the disguise you are using (it always said
  "hold =" before).
- The direct download is now `dictionary.apk`.

## 1.2.0 (2026-10-04): more disguises — Clock and Sudoku

- Two more faces in Settings > Disguise, both opening the same reader:
  - **Clock**: a working wall clock and countdown timer. To open the reader, hold Start
    until the timer clears, type your code on the pad, then hold Start again. A normal tap
    of Start just runs a countdown.
  - **Sudoku**: a full game — new puzzles generated on the phone at Easy, Medium or Hard
    (always exactly one solution), pencil marks, undo, erase, hints, a timer, row/column/box
    and same-digit highlighting, conflict marking, a "Solved" screen, and the game is saved
    and resumed automatically. The entry is played on the board: hold Notes to arm, type the
    code on the number pad (Erase is 0, Undo removes a digit), hold Notes again. The presses
    look like ordinary moves; when the gesture ends the board goes back to how it was, and
    nothing is saved while armed, so the code never reaches the saved game.
- Both faces use `combinedClickable` long-press for the hidden gesture (the 1.1 pointerInput
  hold was unreliable from injected input).
- Disguise reminders for the two faces ship in the encrypted pack, not the APK. The scan
  still finds no telltale words, no permissions, backups off, and exactly one exported
  launcher per face (now four).

## 1.1.0 (2026-10-04): switchable disguises

- Settings > Disguise: choose how the app looks on the home screen — the original
  disguise or a notepad. The icon and name change at once (via launcher activity-aliases); only one shows
  at a time. The reader, code and contents are unchanged.
- Notes disguise: a plain, working notepad. To open the reader, start a note, hold Done,
  type your code, then hold Done again; a normal tap of Done saves the note. Its own notes
  are ordinary and stored in the clear, so the disguise looks lived-in.
- The reader's exit button is now a neutral "hide", not the original disguise's icon.
- Disguise reminders ship in the encrypted pack; the APK scan still finds no telltale words,
  no permissions, backups off, and only launcher faces exported.

## 1.0.0 (2026-10-04): first public release

- Same app as 0.5.0, after an independent review. Plain-language install steps for people
  who have only used the Play Store (`docs/INSTALL.md`). Source published under GPLv3.

## 0.5.0 (2026-10-03): user guide (Phase 6)

- `docs/USER_GUIDE.md`: setup, opening and closing, reading, notes, backups, erasing, and
  plainly what the app does and does not protect against. Also inside the app (encrypted)
  under Settings > How this app works.
- `docs/THREAT_MODEL.md`: design, leak checklist status, and ten known weaknesses for the
  independent review.

## 0.4.0 (beta 0.4, 2026-10-03): backup, wipe, hardening

- Settings > Backup: export notes, highlights and saved items to a file locked with a
  passphrase (Argon2id + AES-256-GCM, no header or magic bytes, chosen through the system
  file picker), and import one. Import adds what is missing and never replaces anything.
- Settings > Security: change code (re-wraps the key only), and an opt-in "Erase after 10
  wrong codes" with a warning. Only hold-= attempts count; a right code resets the count.
  The count and setting are sealed with the device key, and the file is rewritten on right
  and wrong codes alike so a failure makes no extra disk activity.
- The app stays open behind the system file picker for up to 2 minutes, then locks.
- Setup and code-screen wording moved into the encrypted pack.
- Release builds exit quietly on a crash instead of logging a stack trace.
- Leftover font file from a killed process is deleted at start.
- GitHub Actions: unit tests, release build and the APK scan on every push.

Known limits: other UI words (Saved, Notes, Highlight…) are still readable in the APK;
the bundled text is obfuscation only; Argon2 is not yet calibrated on a slow phone.

## 0.3.2 (2026-10-03)

- Saved tab has two sections, Verses and Notes. Notes lists every verse note and chapter
  note on its own (nothing to save), with the verse text, Edit, and Open in reader.

## 0.3.1 (2026-10-03)

- Selecting verses: each tap adds or removes one verse, so you can pick John 3:16 and 18
  without 17. Long-press a verse to add every verse from the last one tapped through it.
- Highlights, notes and Saved items work on any set of verses ("John 3:16, 18"); Saved
  text shows "…" where verses are skipped. Notes and Saved items from 0.3.0 carry over.

## 0.3.0 (beta 0.3, 2026-10-03): highlights, notes, Saved

- Tap a verse to select it; tap another to extend to a range. A bar appears with five
  highlight colors (and remove), Note, Save and Done.
- Highlights are stored per verse, so they show in every translation.
- Verse and range notes: a small dot after the verse; tap it to read, edit or delete.
- Chapter notes at the end of each chapter, oldest first, each with an optional verse tag.
- Saved tab: save a verse, range or whole chapter. Filter, sort (newest, oldest, book
  order), label, any number of notes per item, open in the reader, and view in the
  translation it was saved from.
- Recent passages at the top of the book picker.
- All of it is one encrypted item in the vault, written on every change and dropped from
  memory on lock. Note fields ask the keyboard not to learn and block copy.

Still to come (0.4): encrypted export/import, wipe options, release hardening.

## 0.2.0 (beta 0.2, 2026-10-03): reader

- The placeholder is replaced by the reader: BSB (default), WEB and KJV, all 66 books,
  offline. Section headings, Psalm titles and poetry indentation; small verse numbers.
- Book and chapter picker, swipe or Previous/Next between chapters, translation switch
  (keeps your place), last position remembered.
- Search: type a reference ("John 3:16", "1 cor 13:4-7", "ps 23") to jump, or words to
  search the current translation. The index is built in memory after unlock and dropped
  on lock.
- Settings: Paper, Sepia, Dark and Black themes, text size, line spacing, "About these
  texts" (sources and licenses), Erase everything. Literata typeface.
- Text, book names, reader words, the font and license texts ship as encrypted packs
  under opaque names (build-time key: obfuscation only). The font is
  decrypted to the app cache while open and deleted on lock.
- `tools/scan_apk.sh` checks the release APK: no telltale words, no permissions, backups
  off, only the launcher exported. Passes. Release APK: 5.9 MB (packs are 4.7 MB of it).
- Panic: the button at top left locks with one tap; double-tap anywhere in the
  top-left corner still works and no longer blocks the controls under it.

Still to come: recent passages (Q28) with Saved in 0.3; setup-screen wording still readable
in the APK; failed-attempt counter; Argon2 calibration on a slow phone.

## 0.1.1 (2026-10-03)

- Corner double-tap lock now works on a real phone: the target was mostly hidden under
  the status bar. It now reaches about 1 cm below the status bar, and two taps up to
  half a second apart count.

## 0.1.0 (beta 0.1, 2026-10-03): first disguise and locked space core

- First disguise, a working arithmetic keypad: normal precedence, BigDecimal arithmetic (0.1 + 0.2 = 0.3),
  percent, plus/minus, chained operations, repeated `=`, live preview, divide-by-zero
  message. Turn the phone sideways for scientific keys (trig in degrees or radians,
  logs, powers, roots, factorial, π, e, parentheses).
- First run: hold `=` for 1.5 s to choose a code (6+ digits, entered twice).
- Open: hold `=` until the display shows 0, type the code, hold `=` again. A wrong
  code just shows 0. Any operator, `.` or short `=` silently cancels; so does waiting
  15 s or leaving the app.
- Key hierarchy from the brief: Argon2id (64 MiB, t=3, p=1) of the code, wrapped
  again by an Android Keystore key (StrongBox when available). The code is never stored.
- Placeholder inside: an encrypted scratch pad, "Back to the disguise", double-tap the
  top-left corner, 60 s idle lock, and lock on background or screen off. Keys are
  zeroed on lock. Screenshots and the recents thumbnail are blocked while open.
- "Erase everything" deletes the wrapped key, the Keystore key, and the data.
- No permissions, backups disabled, nothing exported but the launcher, release logging
  stripped, R8 enabled. APK about 0.9 MB.

Known gaps (planned for later betas): UI text for the setup and placeholder screens is
still readable inside the APK (moves into the encrypted pack in Phase 2/5); no
failed-attempt counter or wipe-after-N yet (Phase 5); Argon2 not yet calibrated on a
slow phone (about 1.5 s on the emulator).
