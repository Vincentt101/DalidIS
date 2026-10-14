# Payment Announcer (Android)

Reads incoming payments out loud. When PayPal, Wise, GCash, Maya, GoTyme, MariBank or any
other app you pick posts a notification like **"Maria Santos sent you $350.00"**, the phone
says:

> "You have received $350 from Maria Santos."

Peso amounts are said as pesos. For example, *"You have received PHP 500.00 of GCash from
JUAN DELA CRUZ…"* is read as:

> "You have received 500 pesos from Juan Dela Cruz."

If the text doesn't match a known payment format, it reads the notification as-is
(e.g. *"PayPal: Payment received. Your balance was updated"*).

- Kotlin, min SDK 26 (Android 8.0), target SDK 35
- No internet permission. Nothing leaves the phone.

## Install on your phone (no computer needed)

GitHub builds the app automatically (`.github/workflows/payment-announcer-apk.yml`) every
time this folder changes, and posts the installable file on the repo's **Releases** page.

1. On your phone, open **https://github.com/Vincentt101/DalidIS/releases/latest**
2. Under **Assets**, tap **PaymentAnnouncer.apk** to download it.
3. Open the downloaded file. When Android asks, allow your browser or Files app to
   **Install unknown apps**, then tap **Install**.
   - If Play Protect warns *"Unsafe app blocked"* or *"App scan"*, tap **More details →
     Install anyway**. It warns about every app that doesn't come from the Play Store.
4. Follow [First-run setup on the phone](#first-run-setup-on-the-phone) below.

If the Releases page is empty, open the repo's **Actions** tab, choose
**Build Payment Announcer APK**, and tap **Run workflow**. Wait about 5 minutes and check
Releases again.

**Updating to a new build:** without a fixed signing key, each build is signed with a
different temporary key, so Android refuses to install it over the old one
(*"App not installed"* or *"package conflicts"*). Uninstall the old version first. Your watched
apps and the access settings will need to be set up again. To avoid this, add a fixed key
(see [Fixed signing key for GitHub builds](#fixed-signing-key-for-github-builds)).

## How it works

| File | Role |
|---|---|
| `PaymentNotificationListener.kt` | `NotificationListenerService`. Filters by watched package, skips group summaries, ongoing notifications and repeats within 30 s, then parses and speaks. |
| `PaymentParser.kt` | Regex rules for the amount and sender (pure Kotlin, unit tested). |
| `Speaker.kt` | `TextToSpeech` wrapper. Queues text until the engine is ready and ducks music while speaking. |
| `SettingsActivity.kt` | Settings screen: notification access, battery optimization, on/off switch, **Test voice**, watched-app list, and the last captured notification. |
| `Prefs.kt` | Stores settings in SharedPreferences. |

### Formats it recognizes

The phrasing doesn't need to match exactly or be in the same case.

- `John Doe sent you $350.00` / `John Doe has sent you $350.00 USD`
- `John Doe paid you $30`
- `John Doe sent $20 to you`
- `You received $350.00 from John Doe` / `You've received €200 from …` / `You got $5 from …`
- `Payment of $99.99 received from Jane Smith. Ref 12345`
- `$350.00 received from John Doe` / `£5.00 has been credited from Tom to your account`
- GCash: `You have received PHP 500.00 of GCash from JUAN DELA CRUZ w/ MSISDN 0917…`
- Maya / banks: `You've received ₱250.50 from …`, `You have received PHP 1,000.00 via InstaPay from …`,
  `Php3,000.00 from … thru PESONet`

Amounts: currency symbols `$ € £ ¥ ₹ ₱ ₩ ₦ ₺ ₽`, ISO codes before or after the number
(`PHP 1,500.00`, `Php500`, `40.00 CAD`), the `P100.00` peso shorthand, and thousands
separators. `.00` is dropped so it says "$350", not "$350 point zero zero". Philippine peso
amounts are spoken as "1,500 pesos and 50 centavos".

Sender names: trailing clauses such as `on 14 Oct`, `via InstaPay`, `w/ MSISDN 0917…`, a
mobile number, or `to your account` are cut off. Asterisks in masked names (`JU** DE** C.`)
are removed. ALL-CAPS names are read in normal case ("Juan Dela Cruz"), so the voice doesn't
spell them out letter by letter.

**Adding your bank's wording:** grant access, receive a payment, then look at the
*Last captured notification* section on the settings screen to see the exact title and
text. Add a `Rule(...)` to `PaymentParser.rules` and a test case to
`app/src/test/.../PaymentParserTest.kt`.

## Build

You need **JDK 17 or newer** and the **Android SDK** (platform 35). The easiest way to get
both is to install [Android Studio](https://developer.android.com/studio).

### Option A: Android Studio

1. **File → Open** and select the `payment-announcer` folder.
2. Wait for Gradle sync to finish, then **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.
3. The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

### Option B: command line

```bash
cd payment-announcer
# Point Gradle at your SDK if ANDROID_HOME isn't set:
echo "sdk.dir=$HOME/Android/Sdk" > local.properties     # macOS: $HOME/Library/Android/sdk

./gradlew testDebugUnitTest      # parser unit tests
./gradlew assembleDebug          # -> app/build/outputs/apk/debug/app-debug.apk
```

On Windows, use `gradlew.bat` instead of `./gradlew`.

The debug APK is signed with the debug key, which is fine for sideloading on your own phone.

### Optional: signed release build

```bash
keytool -genkeypair -v -keystore release.jks -alias announcer \
        -keyalg RSA -keysize 2048 -validity 10000
cat > keystore.properties <<EOF
storeFile=release.jks
storePassword=YOUR_PASSWORD
keyAlias=announcer
keyPassword=YOUR_PASSWORD
EOF
./gradlew assembleRelease        # -> app/build/outputs/apk/release/app-release.apk
```

`keystore.properties` and `*.jks` are git-ignored. Keep the keystore safe: updates must be
signed with the same key.

### Fixed signing key for GitHub builds

Once these four repository secrets exist (**Settings → Secrets and variables → Actions → New
repository secret**), the workflow builds a signed release APK, and future builds install
over the previous one:

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | output of `base64 -w0 release.jks` (macOS: `base64 -i release.jks`) |
| `KEYSTORE_PASSWORD` | the store password |
| `KEY_ALIAS` | `announcer` (or whatever alias you used) |
| `KEY_PASSWORD` | the key password |

## Install (sideload)

**Over USB with adb:**

1. On the phone, go to **Settings → About phone** and tap **Build number** 7 times. Then go to
   **Settings → System → Developer options** and turn on **USB debugging**.
2. Connect the phone and run:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

**Without a computer:** copy the APK to the phone (Drive, email, USB), tap it in the Files
app, and allow *Install unknown apps* for that app when asked.

## First-run setup on the phone

1. Open **Payment Announcer**.
2. Tap **Open Notification Access settings** and turn on *Payment Announcer*.
   - **Android 13+ and the toggle is greyed out ("Restricted setting")?** This is normal for
     sideloaded apps. Tap **Open App info** in the app, then **⋮** (top right) →
     **Allow restricted settings**, confirm with your PIN, and go back and turn the toggle on.
3. When asked *Keep announcing in the background?*, tap **Allow**. You can also use the
   **Disable battery optimization** button later.
4. Under **Apps to watch**, these are pre-filled:

   | App | Package |
   |---|---|
   | PayPal | `com.paypal.android.p2pmobile` |
   | Wise | `com.transferwise.android` |
   | GCash | `com.globe.gcash.android` |
   | Maya | `com.paymaya` |
   | GoTyme Bank | `ph.com.gotyme` |
   | MariBank PH | `ph.seabank.seabank` |

   Apps you don't have are marked *(not installed)*. Remove them with ✕ if you like. If an app
   you *do* have shows as not installed, its package name is different on your phone: remove
   it, then tap **Add app** and pick it from the list of installed apps. Use **Add app** for
   any other bank too (BDO, BPI, UnionBank, etc.).
5. Tap **Test voice**. You should hear *"You have received $350 from Test Sender."*
   If you hear nothing, raise the **media** volume and check
   **Settings → Accessibility → Text-to-speech output** (install *Speech Services by Google*
   if no engine is listed).

The voice uses your phone's default language. To change the voice or speed, go to
**Settings → Accessibility → Text-to-speech output**.

## Keeping it running in the background

The system keeps a notification listener bound as long as notification access is granted,
so the app doesn't need a foreground service or a persistent notification. It also starts
again automatically after a reboot. On top of that, the app:

- asks the system to rebind the listener whenever the settings screen opens, and whenever
  the listener gets disconnected
- prompts once to be excluded from battery optimization

Some phone makers add their own app killers on top of standard Android. If announcements
stop after a while, also check these settings:

| Brand | Setting |
|---|---|
| Samsung | Settings → Battery → Background usage limits → remove the app from *Sleeping apps* / *Deep sleeping apps*. App info → Battery → **Unrestricted**. |
| Xiaomi / Redmi / POCO | App info → **Autostart** on; Battery saver → **No restrictions**. |
| OPPO / realme / OnePlus | App info → Battery → **Allow background activity**; turn off *Optimize battery use*. |
| Huawei / Honor | Battery → App launch → turn off *Manage automatically* and enable all three toggles. |
| Vivo | Settings → Battery → **High background power consumption** → allow. |

See [dontkillmyapp.com](https://dontkillmyapp.com) for other models. Avoid using **Force stop**
on the app: a force-stopped app loses its listener until you open it again.

## Troubleshooting

- **Nothing is announced:** check that the status line says *Notification access granted*,
  the *Announce payments* switch is on, and the app is in the watched list. Then send a test
  payment and look at *Last captured notification*. If that section is empty, the
  notification never reached the app.
- **The bank's notifications are hidden on the lock screen or silenced:** the listener still
  receives them, but if the bank app's notifications are *blocked*, there is nothing to read.
- **It reads the raw text instead of "You have received…":** the wording didn't match. Add a
  rule (see *Adding your bank's wording* above).
- **The same payment is announced twice:** repeats of identical text from the same app within
  30 s are skipped. If your bank posts two *different* notifications per payment, remove one
  of them in the bank app's notification settings, or tighten the rules.
