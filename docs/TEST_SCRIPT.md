# Test script

How to check a build before release. The automated part runs on every push (CI); the manual
part is done on a phone or emulator, on a fresh install unless a step says otherwise.

## Automated

`./gradlew testDirectDebugUnitTest testPlayDebugUnitTest` runs:

- `KeyVaultTest`: RFC 9106 Argon2id vector, right and wrong code, tamper, lost device key,
  wipe zeroes keys, no code, key or plaintext in stored files, wipe after 10 wrong codes only
  when on, change code, backup round trip, wrong passphrase.
- `ReaderTest`: decrypts every book of all three translations, verse counts, clean text,
  reference parsing, search.
- `AnnotationsTest`: highlights, notes, Saved, recent passages, backup merge.
- `StrongsTest`: KJV word tags, lexicon, concordance, number parsing, lexicon search.
- `DictionaryTest`: lookups, base forms ("ran" -> run), suggestions.
- `DisguiseTest`: only the app's own face and the Dictionary exist, default first.

Then `./gradlew assembleRelease` and `tools/scan_apk.sh` on each APK in
`app/build/outputs/apk/{direct,play}/release/`: no telltale words, no permissions, backups
off, nothing exported but the two launcher faces.

## First run and code

Direct (`app-direct-*.apk`):

1. Fresh install: the launcher shows only "Dictionary"; App info shows "Dictionary" with the
   dictionary icon. Opening it shows the Welcome message (Set up now / Later); Search is a
   filled button.
2. **Set up now** opens "Choose a code"; so does holding Search after **Later**. The setup
   hint describes the Search way in. After the code is set the reader opens, with no
   disguise question. The Welcome message doesn't come back.

Play (`app-play-*.apk`):

3. Fresh install: the launcher and App info show "Kanaiic Reader"; it opens to "Choose a code".
4. Enter a code twice: the reader opens and asks "Disguise the app now?". **Later**: it stays
   Kanaiic Reader. Settings starts with "How to open the app", and each option under Disguise
   shows its own way in.
5. On another fresh install, **Disguise now**: the steps screen, then **Switch to Dictionary**:
   the launcher shows only "Dictionary"; holding Search, typing the code and holding Search
   again opens the reader. The question doesn't come back.

## Locking

1. Each of these locks back to the front screen: Home, screen off, Back with nowhere left to
   go back to, double-tap the top-left corner, 60 s without touching.
2. Recents while open shows a blank card; a screenshot while open is black (release build).
3. Kanaiic Reader face: a wrong code says so; the right one opens.
4. Dictionary face: a quick tap of Search looks the word up; letting go before 3 s also
   just searches. Hold Search 3 s: the number pad appears and the keyboard hides. A wrong code
   and OK closes the pad with nothing else; Back or a tap outside closes it too; the right code
   opens. Home while the pad is open, then back to the app: the pad is gone.

## Reading

1. Tap the title, pick Psalms 23: poetry lines indent. Swipe for next and previous chapter.
2. Switch to KJV and WEB from the chip at top right: same passage.
3. Search "jn 3:16", tap "Go to John 3:16": the reader opens on it with the verse tinted.
   Search "still waters": matches highlighted; tap Psalm 23:2: the verse is tinted and "still"
   and "waters" stand out. Swipe to the next chapter and back: the marks are gone.
4. Back arrow (top left): from that verse it returns to the results, query kept, same scroll,
   keyboard down. Read > Saved > Settings, then the arrow twice: Saved, then Read. Pick another
   chapter in the book picker, then the arrow: the previous chapter. With nowhere to go back
   to, the arrow is faded and does nothing; system Back there locks.
5. Settings: each theme, text size and spacing change the text; About lists the sources.
6. Lock and open again: same translation, place, theme and size.

## Highlights, notes, Saved

1. Tap John 3:16, then 18: both underlined, the bar shows John 3:16, 18. Long-press 21:
   16–21 selected.
2. Pick a colour: highlighted, also after switching translation.
3. Note on a verse: a dot appears; tap it to edit. End of chapter: note on the chapter.
4. Save a verse and a chapter; Saved lists them; add a label and a note; filter and sort.
5. Lock and open again: everything is still there.

## Strong's

1. Select John 3:16, tap **Strong's**: each KJV word with its Greek word, transliteration,
   number and renderings.
2. Tap "loved": the G25 entry, linked numbers, renderings with counts, 109 verses.
3. Search tab (there is no separate Strong's tab): "love" lists G26, H157, G25 under STRONG'S
   with "Show all", then the verses; "agape" and "G26" find G26; Back from an entry returns to
   the results with the query kept. "H3068" offers the entry (5519 verses; scrolls smoothly);
   tapping one of its verses opens it tinted, and the arrow returns to the entry.

## Dictionary disguise

1. "ran" opens run ("a form of run"); suggestions while typing; synonyms open their entries;
   Thesaurus shows synonyms, Related and Opposite; About shows the WordNet notice.
2. Notes tab: an ordinary notepad; holding Done just saves.
3. PDF tab: open a long PDF (make one with Ghostscript and `adb push` it to Downloads): name,
   page counter, sharp pages; fling to the end and back: `dumpsys meminfo` native heap stays
   bounded. Pinch zooms to 4x and re-renders sharper; one finger pans. Switch tabs and back:
   same page. A password-protected PDF and a non-PDF give clear messages (a dialog if a PDF is
   already open). Nothing in `shared_prefs` or `files` names the PDF.
   (Emulator pinch: `adb root`, then `sendevent` on `/dev/input/event2` with ABS_MT_PRESSURE
   set; release every slot afterwards, or reboot if input sticks.)

## Backups and security

1. Settings > Export: passphrase twice, save the file. It opens as random bytes.
2. Import it: "Nothing new". Wrong passphrase: error, nothing changes.
3. Change code: the old code no longer opens, the new one does.
4. "Erase after 10 wrong codes" on, 10 wrong codes: everything is erased and setup starts again.
5. Set up again and import the backup: notes, highlights and saved items come back.

## Updates

1. Install the previous release, set a code, switch to the Dictionary, then install the new
   build over it. Within a second, before opening, the launcher lists only `.FaceDictionary`
   (`adb shell cmd package query-activities --brief -a android.intent.action.MAIN -c
   android.intent.category.LAUNCHER`); the old code opens the reader with everything there.
2. Same on the Kanaiic Reader face (direct 1.7 set up and left as Kanaiic Reader): it stays
   Kanaiic Reader after the update, even though direct now starts as the Dictionary.
