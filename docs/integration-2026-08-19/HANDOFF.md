# HANDOFF — Cloud Onboarding Integration

**Handoff date**: 2026-08-19 | **Author**: Elmo (@toypark1234) | **Session ID**: integration-2026-08-19

**Purpose**: Anyone can pick this up in 15 minutes, continue testing, and finish merge to `main`.

> 📖 For the full retrospective (root cause analyses, timeline, screenshots), read [`README.md`](README.md) in this folder. This document is only *actionable* items.

---

## 1. Current state in one paragraph

Eugene's 4 open PRs ([#4](../../pull/4)/[#5](../../pull/5)/[#6](../../pull/6)/[#7](../../pull/7)) have been merged into a scratch branch [`dev/cloud-onboarding-integrated`](../../tree/dev/cloud-onboarding-integrated), built into a Pixel 7a APK via CI, and manually tested through the point where the app receives a device-flow `user_code` from `stg.woowtech.io`. **The onboarding flow past the "open browser to authorize" step is NOT yet tested** — that requires a real stg PaaS account credential which the author didn't have available. Everything up to that point works. Three environment issues were worked around on this branch; those workarounds must be replaced with permanent fixes before merging any of Eugene's PRs to `main`.

---

## 2. Who owns what (fill in names before starting)

| Item | Owner | Status |
|---|---|---|
| Finish browser-auth + provision + NameYourDevice testing on Pixel 7a | **TBD** (needs stg PaaS credentials) | ⬜ not started |
| Decide permanent fix for reckon (rename tag vs add stage) | **Eugene** | ⬜ decision pending |
| Decide permanent fix for stg CF Access (Service Token vs WARP vs flavor split) | **Eugene + platform team** | ⬜ decision pending |
| Decide `dev-build.yml` releases-branch policy | **TBD** | ⬜ decision pending |
| Merge Eugene stack to `main` (in order #4→#5→#6→#7) | **Eugene** (after 3 decisions above) | ⬜ blocked |
| Delete integration branch after merge | Whoever merges last PR | ⬜ blocked |

---

## 3. Assets you'll need (all URLs)

### Repository
- **Branch (this work)**: https://github.com/WOOWTECH/woow_ha_app_cloud_vesion/tree/dev/cloud-onboarding-integrated
- **Retrospective report**: [`docs/integration-2026-08-19/README.md`](README.md)
- **Eugene's PRs**: [#4](../../pull/4) → [#5](../../pull/5) → [#6](../../pull/6) → [#7](../../pull/7) (merge order forced by stacking)
- **Base tag**: [`v2026.8.3-cloud-alpha1`](../../releases/tag/v2026.8.3-cloud-alpha1) on commit `fa397e1`
- **Slim HA image PR** (backend counterpart, already merged): [WOOWTECH/cloud-version-woowtech-ha#1](https://github.com/WOOWTECH/cloud-version-woowtech-ha/pull/1)

### APK for testing
- **Download page**: https://github.com/WOOWTECH/woow_ha_app_cloud_vesion/actions/runs/32219930928
- **Filename**: `woowtech-home-dev-cloud-onboarding-integrated-debug.apk` (47.7 MB, expires 2026-11-17)
- **Package**: `com.woowtech.homecloud.debug`
- **VersionName**: `2027.0.0-beta.1+integrated-full`
- **Flavor**: `fullDebug` (Google Play Services required; MIUI / non-GMS devices need a `minimalDebug` build instead — request a rebuild if you need that)

### Backend
- **PaaS stg (debug builds)**: `https://stg.woowtech.io`
- **PaaS prod (release builds)**: `https://paas.woowtech.io`
- **OAuth Device Flow endpoint**: `POST /oauth2/device_authorization`
- **Client ID**: `woow-ha-app`
- **Scopes**: `ha:provision workspace:read smarthome:read`

### Cloudflare Access
- **Policy name**: `prod-ip-bypass`
- **Policy ID**: `324f5955-2c08-420b-b4a8-b882fa82320d`
- **Account ID**: `9c27f623ee596e0b67be56263bcb1974`
- **Currently allowlisted IPs**: 12 (see `README.md` §5 for the full list)
- **How to add your test device**: see §5 below

---

## 4. Reproduce the current state (target: 15 min)

### 4.1 Get the APK
1. Open https://github.com/WOOWTECH/woow_ha_app_cloud_vesion/actions/runs/32219930928
2. Bottom of page → **Artifacts** → click `debug-apk` → download zip
3. Unzip → `woowtech-home-dev-cloud-onboarding-integrated-debug.apk`

### 4.2 Install on your Android test device
```bash
# From your laptop (needs adb):
adb install -t woowtech-home-dev-cloud-onboarding-integrated-debug.apk

# If prior install exists with different signature:
adb uninstall com.woowtech.homecloud.debug
adb install -t woowtech-home-dev-cloud-onboarding-integrated-debug.apk
```

**⚠️ Cloudflare Access will block your device unless your public IP is on the allowlist.** See §5.

### 4.3 Add your device's public IP to CF Access (one-time per WiFi/network)
1. Open Chrome on the phone → https://api.ipify.org → note the IP
2. Add that IP as `<ip>/32` to policy `prod-ip-bypass` — either via Cloudflare Zero Trust dashboard, or ask someone with Cloudflare API access (@Elmo did it via MCP with `PUT /accounts/{acct}/access/policies/324f5955-...`)
3. Wait ~10 seconds for CF edge propagation

### 4.4 Run the app
1. Open **woowtech Home Cloud** (icon may say `雲端版`)
2. On "選擇連線方式" tap **使用雲端服務**
3. Expected: screen shows "雲端登入", a 8-char user code (e.g. `LGMN-CRLK`), and "等待瀏覽器授權中..." — **this is where the last confirmed test stopped**
4. If instead you see "伺服器回應格式錯誤" → CF Access is still blocking (§5 didn't take effect)

---

## 5. Continue testing from here

### 5.1 What's left to validate

- [ ] Tap "前往驗證" → opens Chrome to `https://stg.woowtech.io/device?user_code=XXXX-XXXX` → **need stg PaaS account credentials to log in**
- [ ] After browser auth completes → app should auto-jump to **CloudProvision** screen ("開通中...")
- [ ] Wait for free HA slim to provision (up to 10 min per PR #1 spec)
- [ ] On success → **NameYourDevice** screen appears → enter a device name → **Save**
- [ ] After Save → app should register with the freshly-provisioned slim instance → land on the main HA dashboard
- [ ] Verify the mobile_app registration works (this is what [cloud-version-woowtech-ha PR #1](https://github.com/WOOWTECH/cloud-version-woowtech-ha/pull/1) fixed on the slim image side)

### 5.2 Regression tests worth doing while you're there

- **Back-key trap fix (PR #6)**: On CloudProvision error screen (canRetry=false), pressing back should land on CloudChooser — NOT bounce you back to CloudProvision via CloudSignIn. If it bounces, cc0b85e regressed.
- **401 auto-refresh (PR #6)**: Toggle Airplane Mode for ~20s during CloudProvision polling → should show "重新連線中..." and recover, NOT crash to Error screen.
- **Transient-error retry (PR #1)**: Kill WiFi for 5s during device-flow polling → should show reconnecting, NOT terminal Error.

### 5.3 Recording test evidence

Please save screenshots per step to `docs/integration-2026-08-19/` with names like `03-cloud-provision-success.png`, `04-name-your-device.png`, etc. Push to the branch. Existing files there: `01-cloud-chooser.png`, `02-cloud-signin-user-code.png`.

---

## 6. Merge path to `main` (blocked until below)

### 6.1 Before touching Eugene's PRs, fix these three (permanent)

**A. Reckon can't parse the base tag.** Pick one:
- **Preferred**: Rename `v2026.8.3-cloud-alpha1` → `v2026.8.3-beta.1` (semver-compliant), update the [existing Release](../../releases/tag/v2026.8.3-cloud-alpha1) tag_name accordingly.
- Alternative: `settings.gradle.kts` line 42 → `stages("beta", "final")` → `stages("beta", "cloud-alpha", "final")`; **verify** reckon parses `2026.8.3-cloud-alpha.1` (note the dot — the current tag lacks it; may need tag rename anyway).

**B. `dev-build.yml` releases-branch push.** Pick one:
- Create empty `releases` branch on this repo → restore the removed 12 lines from the workflow.
- Migrate to `softprops/action-gh-release` (proper GitHub Releases).
- Keep this branch's fix (artifact-only) permanently. Reviewers download from run pages, artifacts expire after 90 days.

**C. stg Cloudflare Access.** Pick one:
- Add Cloudflare Access **Service Token** to CI + app → app sends `CF-Access-Client-Id` / `CF-Access-Client-Secret` headers. All test devices work automatically, no per-device IP allowlist.
- Enforce Cloudflare **WARP** on all test devices → CF Access uses WARP identity.
- Split debug into `debug-stg` + `debug-prod` flavors → dev-build.yml produces `debug-prod` by default (public tester friendly), `debug-stg` is opt-in for internal team.

### 6.2 Merge sequence (only after A/B/C done)

1. Rebase [#4](../../pull/4) on latest `main`, wait CI green, merge (squash preferred so the 4 permanent-fix commits show up as one clean piece per PR).
2. Rebase [#5](../../pull/5) on new `main`, wait CI green, merge.
3. Same for [#6](../../pull/6), then [#7](../../pull/7).
4. **Do NOT cherry-pick from `dev/cloud-onboarding-integrated`** — the 3 workaround commits (`ad16dae`, `476d340`, `d8114e3`) are branch-specific and don't belong on `main`.

### 6.3 After the merge

- `git push origin --delete dev/cloud-onboarding-integrated` — this scratch branch is done
- The `docs/integration-2026-08-19/` folder can either move to main via a separate small PR, or stay branch-only (retrospective doesn't need to be permanent)

---

## 7. Rollback procedures (if something breaks in the wild)

### 7.1 If a merged Eugene PR causes prod issues

- Revert on `main` via `git revert -m 1 <merge-commit>` (in reverse merge order: revert #7 first, then #6, etc.)
- Publish a new alpha release from the pre-#4 commit if urgent

### 7.2 If someone accidentally pushes to `dev/cloud-onboarding-integrated`

- Nothing on prod depends on this branch — safe to force-reset to `893eae5` (current HEAD as of handoff)
- Or delete the branch entirely and recreate from `main`+PRs if needed

### 7.3 If CF Access allowlist bloat becomes a problem

- The `prod-ip-bypass` policy is a stopgap for dev testing — clean up test-device IPs monthly, or switch to Service Token model (§6.1 C)

---

## 8. Known gotchas / open questions

- **APK is `fullDebug` = requires Google Play Services.** Non-GMS devices (MIUI EU, Amazon Fire, Huawei) can't run this build; they need `minimalDebug`. `dev-build.yml` currently builds only Full — add `assembleMinimalDebug` if minimal-flavor testing is needed.
- **Debug keystore is per-CI-run.** If you install this APK, then rebuild the branch, then try to install the new APK → `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. Always `adb uninstall` before installing a new debug build.
- **CF Access session is 24h.** The IP allowlist bypass has a 24h session duration. If you leave a session open for >24h then retry, you may see a fresh CF Access challenge — restart the app / clear browser session on stg to reset.
- **`v2026.8.3-cloud-alpha1` Release APK is minimalDebug (68MB); this branch's APK is fullDebug (47.7MB).** Different flavors, NOT a code regression. See [`README.md`](README.md) §6 for the aapt breakdown.
- **`dev-build.yml` will auto-trigger on any push to `dev/**`.** If you're just adding docs, either cancel the CI run (`gh api -X POST repos/.../actions/runs/{id}/cancel`) or accept the ~10min build.

---

## 9. Contacts

- **Elmo (@toypark1234)** — integration branch owner, CF Access policy admin access, tested up through step §5.1 checkbox 0
- **Eugene (@eugenechen0514)** — PR author for #4/#5/#6/#7, familiar with the WoowPaas layer
- **Shell** — mentioned in [cloud-version-woowtech-ha PR #1](https://github.com/WOOWTECH/cloud-version-woowtech-ha/pull/1) as the e2e onboarding validator on the slim-image side
- **paas-platform team** — owns stg / prod PaaS endpoints, decision on Service Token vs WARP vs current IP allowlist model

---

## 10. Quick reference commands

```bash
# Adb wireless connect (pair once, then connect on each device toggle)
adb pair <phone-ip>:<pairing-port>       # enter pairing code from phone
adb connect <phone-ip>:<connect-port>    # the OTHER port shown on the main Wireless Debug screen

# Install / reinstall
adb uninstall com.woowtech.homecloud.debug
adb install -t <apk-file>

# Launch app (bypasses LeakCanary's launcher)
adb shell am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER \
  com.woowtech.homecloud.debug/io.homeassistant.companion.android.launch.LaunchActivity

# Watch logs for cloud/paas activity
adb logcat -T 1 | grep -iE "woowpaas|cloud|oauth|serialization|homecloud"

# Screenshot to your laptop
adb exec-out screencap -p > screen.png

# Trigger a fresh CI build (workflow_dispatch)
gh workflow run dev-build.yml --repo WOOWTECH/woow_ha_app_cloud_vesion \
  --ref dev/cloud-onboarding-integrated

# Check CF Access policy state
gh api https://api.cloudflare.com/client/v4/accounts/9c27f623ee596e0b67be56263bcb1974/access/policies/324f5955-2c08-420b-b4a8-b882fa82320d
```

---

*Last updated: 2026-08-19. When you make changes, update the checkboxes in §2 and §5.1, and add your name + date at the bottom of this line.*
