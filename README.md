# Vanta Launcher

A minimal Android home launcher. Kotlin + Jetpack Compose.

The package id is still `com.maria.homelauncher`, from before the name changed. Changing it
would orphan every existing install and its settings, and it is not visible anywhere in the
app, so it stays.

## What works

- Replaces the home screen (HOME intent filter, wallpaper shows through)
- Clock + date
- Dock of up to 5 favourite apps, persisted
- Swipe up anywhere -> app drawer
- Alphabetical app grid with live search
- Long-press an app -> dock, hide, app info, uninstall
- **Hidden apps** -- kept out of the grid, only surface when you type in search
- **Wallpaper rotation** -- pick individual images *or a whole folder* from anywhere on
  the device, rotate on a timer, home / lock / both, optional shuffle, "Change now". A
  watched folder is re-read every time, so pictures added to it later join on their own
- **Animated background** -- a GIF or a video played behind the launcher, set to repeat or
  to play once and hold on its last frame
- **Drawer folders** -- group apps inside the drawer, rename them, ungroup them. A search
  flattens them so an app is never hidden behind one
- **Single wallpaper** -- set one image immediately without turning rotation on
- **Free placement** -- the home screen is a grid of cells and anything can go in any one
  of them, gaps included. Long-press an icon to pick it up and drop it where you want.
  Nothing reflows to close a gap. With a drawer the home screen starts empty and fills
  with what you put there ("Add to home screen" in an app's long-press menu); on one
  surface every installed app is laid out for you.
- **App folders** -- drop one icon squarely on another to make one. Folders open as an
  overlay with an editable name, and a long-press inside moves an app back out.
- **Edit mode** -- hold bare wallpaper. The cell lattice appears, widgets grow a drag
  sheet, a resize corner and a remove badge, and a bar offers Wallpaper, Widgets, Style
  and Done, with a stepper for adding and removing pages.
- **Pages you keep** -- add one and leave it empty if all you want is a look at the
  wallpaper with nothing in front of it. Pages are kept until you remove them.
- **The clock is an item** -- it sits in grid cells like everything else, so it can be
  dragged anywhere, resized, or taken off the home screen entirely.
- **Drawer browse style** -- Scroll (one long vertical list) or Pages (swipe left/right,
  with page dots). Set in settings.
- **Search bar position** -- Top or Bottom, centred either way. At the bottom it lifts above
  the keyboard (`imePadding`).
- **Settings button side** -- Left (default) or Right. The opposite side reserves the same
  48dp footprint so the search pill stays centred rather than drifting.
- **Drop guide** -- dragging on the home surface outlines every slot so the landing cell is
  obvious; the target cell gets a thicker, brighter ring.
- **First-run intro** -- six screens on first launch: what it is, how to set it as the
  default home app (with a button straight to that system screen), how to get around, and
  live toggles for icon shape, columns, labels and single-surface mode. Skippable, and
  replayable from Settings.
- Settings is a **list of pages**, not one long scroll: Home layout, Grid, Icons, Colours,
  Text, Drawer, Wallpaper, Hidden apps, Intro and reset. Back steps up to the list before
  it leaves settings. (in single-surface mode it is the
  "Settings" link at the bottom of the swipe-up search panel instead)
- **Icon packs** -- any third-party icon theme installed on the device. The pack list is
  read from the packs themselves; no hardcoded names.
- **Per-app overrides** -- long-press any app to rename it or give it a picture of your
  own as its icon.
- **Gestures** -- swipe down, double tap, swipe left and swipe right on the home screen,
  each set to notifications, quick settings, lock, recents, search, an app, or nothing.
- **Notification dots** -- an accent dot on apps with something waiting.
- **Widgets** -- live on the same grid as the icons, in both home modes. The catalogue is
  grouped by the app each widget came from and collapsed until opened; hold a row and drag
  it straight onto the spot you want, or tap it to let it find its own. Drag a placed
  widget to move it and pull its bottom corner to resize it in whole cells.
- **Theming** -- every piece of UI reads from one `Style` model, persisted and live-applied:
  - Layout: 3-6 columns, icon size, labels on/off, label size
  - Icon shape: circle / squircle / rounded / square / original
    - Rows per page, page dots, folder tile shade and corners
    - Dock: how many apps, background on or off, corner radius
    - Clock: 12 or 24 hour, show or hide the clock and the date
    - Accent taken from the wallpaper, once or on every rotation
    - Icon opacity, so the wallpaper shows through the icons themselves
  - Icon colour: original, or one-colour themed icons with a full picker (hue,
    saturation, brightness and alpha bars, live swatch, hex and RGBA entry)
  - A **live preview** at the top of settings with Home / Drawer tabs, showing the real
    icons and your first wallpaper image, in the shape of the phone it is running on. In
    Pages mode the preview is genuinely swipeable, with its own page dots.
  - Colour: accent and background through the same full picker, panel style
    (blur / dim / solid), opacity and blur strength
  - Text: system font family, clock / date / search sizes
  - Reset to defaults
- Back button does nothing on home, closes the drawer or settings when open
- Home press while already home closes the drawer
- Auto-refreshes when apps are installed or removed

### The home grid

`Layout.kt` holds a list of `Placed`: an item plus the page, column, row and cell span it
occupies. Apps, folders and widgets are all `Item`s in that one list, which is what lets a
single drag implementation move any of them.

- A `page` of -1 means **unplaced**. The grid gives those the first free spot that fits and
  writes the coordinates back, so a newly installed app, an app added from the drawer and a
  layout saved before free placement existed all land somewhere sensible without the store
  needing to know how wide the grid is.
- `syncLayout()` reconciles against what is installed: drops uninstalled apps, prunes empty
  folders, collapses a one-app folder back into a plain app, and passes widgets straight
  through. `addNew` is on only for one-surface mode; with a drawer the home screen holds
  what was put there and nothing else.
- **Nothing reflows.** A gap is a gap. Dropping on a taken cell is refused and the outline
  turns red rather than shoving the occupant aside.
- **The clock is `Item.Clock`,** one per layout and keyed as such. It used to be a fixed
  header that owned the top of the screen whether that was wanted or not. It ignores
  touches while the surface is idle, so a long press on it reaches the wallpaper
  underneath and turns on edit mode, which is where it can be moved. Its content still
  comes from the clock settings; where it sits comes from the grid.
- **An empty page is something to ask for, not a mistake to tidy away.** The page count is
  stored (`Prefs.homePages`) and the grid shows whichever is larger: that, or enough to
  hold the furthest item. Dropping something on the spare page at the end raises the count
  on its own. An earlier version compacted empty pages away on leaving edit mode, which
  made a deliberately blank page impossible to keep.
- **Drag does not live-move.** The dragged tile floats under the finger and the target
  cells are outlined; the move commits on release, which is what keeps "drop onto" from
  being ambiguous.
- Folder vs move is decided by position: the pointer must land inside `FOLDER_HIT` (0.38) of
  the target cell's half-extent to mean "make a folder". Outer region means "move here".
- Dwelling within 6% of a page edge for 550ms flips the page mid-drag.
- A long-press that releases without moving more than 14dp opens the item menu instead of
  being treated as a drag.
- Hidden apps are excluded from the layout entirely and reachable only through search.
- Widgets are dragged from edit mode only. Outside it their own views own every touch,
  which is the entire point of a widget.

### Drawer folders

Kept in a store of their own (`DrawerFolders.kt`), not in the home layout. The home screen
holds items at coordinates; the drawer is a sorted list of everything installed, and a
folder there is a grouping of that list rather than a thing sitting in a cell. One store
would have meant one of the two pretending to be the other.

- Membership is `package/activity` strings, the same key everything else uses, so an entry
  survives a rename, an icon pack change or a reinstall, and simply stops being listed if
  the app goes away.
- `drawerCells()` returns folders and loose apps together, sorted by name the way a drawer
  always is. A folder made by hand stays a folder even with one app in it, because someone
  put it there on purpose; only an empty one disappears.
- **A search flattens them.** Looking for an app by name and being told it is inside
  something is a worse answer than just showing it.

### Colour picker

Four gradient bars rather than a wheel, plus hex and RGBA fields, all editing the same
value. Two things keep it cheap:

- **Dragging never commits.** Only local HSV state moves while your finger is down;
  `onCommit` fires on release and on a valid typed value. Icon tint is baked into every
  icon at render time, so committing per frame would re-render ~20 bitmaps per frame.
  One gesture now costs one re-render.
- The bars are plain `Box`es with a `Brush` and two `pointerInput` detectors. No canvas
  loop, no bitmap, no library.

The Symbol / Icon background chips drive **one** picker rather than showing two, so the
page stays short. Colours does the same with Accent / Background. There are no preset
swatches left anywhere.

**Gotcha worth remembering:** `pointerInput(Unit)` keeps the lambdas it was composed with.
The first version closed over a `remember`ed colour value, so releasing a bar committed a
stale colour (display said `#FFCA0000`, prefs held `0xFFFF0000`). Callbacks now go through
`rememberUpdatedState`, and `commit()` recomputes from the state values instead of reading
a captured snapshot.

### Icon colour and preview

- One-colour icons use the app's own **monochrome layer** (`AdaptiveIconDrawable.getMonochrome()`,
  API 33+) when it ships one. Most Google apps do and look like proper themed icons.
- Apps without one fall back to an **alpha silhouette** of the foreground layer. That works
  everywhere but flattens interior detail -- on this emulator Clock reduces to a bare
  chevron and TMoble to a filled blob. That is the fallback working as intended, not a bug.
- Tint is baked at render time like shape, so changing it re-renders every icon. Colours are
  swatches rather than a slider specifically to keep those changes discrete.
- `HorizontalPager` defaults to `verticalAlignment = CenterVertically`, so a page with
  fewer rows floats to the middle. Both the preview pager and any short page need
  `Alignment.Top` to line up with a full page.
- Recents are centred with a fixed tile width plus `Arrangement.Center`; `weight(1f)` would
  stretch them across the full width instead of grouping them.
- The preview is sized to the device's own aspect ratio rather than filling the settings
  column. A full-width box 330dp tall read as a small tablet: spacing looked roomier than it
  is, and a page of icons did not fall where it really falls.
- The preview reuses the already-rendered `AppEntry` icons, so it cannot drift from the real
  surface -- whatever it shows is literally the same bitmaps. Wallpaper is decoded from the
  first chosen rotation image with `inSampleSize` so a full-size photo is never loaded whole.

### Widgets

Hosting widgets is `AppWidgetHost` plus `AppWidgetManager`, both wrapped in
`LauncherWidgets`. Three things are worth knowing.

- **Adding is never one call.** A third-party launcher does not hold `BIND_APPWIDGET`
  (it is privileged), so binding an id to a provider goes through the system consent
  dialog, and the widget may then want to run its own configure activity. Both return
  through `onActivityResult`, which is why `MainActivity` still overrides that deprecated
  method rather than using the Activity Result API: `startAppWidgetConfigureActivityForResult`
  is the only call that can launch a configure activity a third-party host is not allowed
  to start directly. Cancelling at either step frees the allocated id again.
- **`startListening()` lives in `onStart`.** Without it a placed widget renders as an
  empty box after a cold start. Verified by force-stopping with a clock widget placed.
- **Size is announced, not imposed.** The frame gets the cells you dragged it to; the
  provider is told about it through `updateAppWidgetOptions`, fired from a keyed
  `LaunchedEffect` because that call broadcasts to the widget and firing it per
  recomposition would be rude. Ids the system has forgotten are dropped on sight, so an
  uninstalled widget cannot leave a dead frame behind.
- **The catalogue stays composed while a widget is dragged out of it.** The gesture belongs
  to the row it started on, so taking that row out of the composition would cancel the drag
  halfway across the screen. It is made invisible instead.
- Where a widget sits is part of the home layout, not a list of its own. `LauncherWidgets`
  hands out ids and reports a bound, configured one through `onPlaced`; the layout decides
  where it lands. The spot a drag chose is held until that callback arrives, because the
  consent dialog and the widget's own setup screen both happen in between.

### Icon opacity

`Style.iconAlpha` is a plain `Modifier.alpha` at every icon draw site, deliberately **not**
baked into the bitmap the way shape and tint are. Baking would mean re-rendering every
icon on each drag of the slider; an alpha modifier is free and makes the slider live.

### Icon packs

Packs are ordinary apps that ship artwork plus an `appfilter.xml`. There is no library
and no formal spec, only the ADW convention every pack still follows, so `IconPack.kt`
parses what packs actually ship rather than what a document says.

- Packs are found by the intent filters they declare (`org.adw.launcher.THEMES` and
  friends), so the list is whatever is installed, never a hardcoded set.
- `appfilter.xml` lives in `res/xml`, in `assets`, or occasionally in `res/raw`. All
  three are tried before giving up.
- A pack icon is drawn exactly as the pack made it. **The shape setting does not apply to
  it**, because the pack is the shape; masking it again would clip its artwork.
- Apps the pack has no icon for are dressed in its `iconback`, `iconmask` and `iconupon`
  at its `scale`, so they still match. The mask is applied `DST_OUT`: it is opaque where
  the icon should be cut away, which is the convention packs are drawn to.
- One-colour icons beat a pack, since the two want opposite things. A replacement icon
  the user picked beats both.

### Gestures and the notification shade

The shade itself cannot be restyled. It belongs to SystemUI, and no launcher of any kind
changes how it looks without a custom ROM. What a launcher can do is open it, and read
what is in it.

- Opening the shade, quick settings, recents, and locking the screen are **global
  actions**, reserved to accessibility services. There is no permission to request
  instead. `LauncherAccess` exists only to call `performGlobalAction`: it listens for no
  events and cannot read window content (`canRetrieveWindowContent="false"`).
- When a gesture needs that service and it is off, the action says so and drops the user
  on the screen where they can turn it on, rather than failing silently.
- Up and down share one drag detector. Two separate ones fought over the same gesture.
- Sideways swipes are only wired up in the drawer layout. On one surface they already
  belong to the home pages, and the settings page says so instead of offering a choice
  that would not work.

### Notification dots

`NotificationWatch` is a `NotificationListenerService`. It keeps a set of package names
and nothing else: no titles, no text, nothing stored, nothing sent. Ongoing notifications
are filtered out, so a download or a music player does not leave a permanent dot. The set
reaches the UI through `LocalBadges`, a non-static composition local because it changes
often.

Notification access is a permission only the user can grant, so both this page and the
gestures page poll their permission once a second while open. Reading it once showed a
stale answer when the user came back from the system settings screen.

### Per-app overrides

Names live in prefs. A replacement icon is copied into the launcher own files as a
square PNG rather than referenced by URI, so the picked image can be deleted or its
permission revoked without leaving a hole on the home screen. Both are keyed by
"package/activity", and an override survives an icon pack change.

### Accent from the wallpaper

`WallpaperColour` samples a hard-downscaled copy of the image, averages only the pixels
that are actually colourful, then forces the result to a readable saturation and
brightness. Deliberately not androidx.palette: that is a library and a clustering pass
for what is one average over a thumbnail.

With "Follow my wallpaper" on, the rotation worker writes the new accent as it goes.
That write happens outside the running UI, so `MainActivity.onStart` re-reads the style
and picks it up.

### Settings structure

`Settings.kt` is a tiny router: a `Page` enum, one `when` over it, and a root list built
from the enum itself, so adding a page means adding one entry and one branch. Widgets is one
page with two states rather than two pages, so it owns a `BackHandler` of its own to step
from the picker back to the list. Each visual
page gets the live preview at the top; Wallpaper, Hidden apps and Intro do not, since
nothing on them changes how the launcher looks.

The control bodies live in `Appearance.kt` as independent composables (`GridSection`,
`IconsSection`, `ColoursSection`, `TextSection`, `DrawerSection`) sharing one set of
widgets, so none of them knows it is on a page of its own.

A `BackHandler` inside `SettingsScreen` is enabled only off the root, so Back pops to the
list first and only then leaves settings.

### Intro

`Prefs.onboarded` gates it; absent or false means it shows. It renders above everything in
`LauncherRoot` and swallows Back so you cannot land behind it. The toggles on its pages
write to the same `Style`/`Prefs` as the real settings, so choices made during the intro
are live immediately, not replayed at the end.

"Open home app settings" fires `Settings.ACTION_HOME_SETTINGS`, falling back to the top
level of Settings if that action does not resolve on a given device.

Pressing Start hands over to `ui/WelcomeFade.kt` rather than cutting to the grid. The
cover starts opaque in the same `bgColor` the intro pages were drawn on, so there is no
seam at the swap; "Welcome" fades up in the accent, holds, then the cover dissolves over
1.8s to reveal the wallpaper. A tap ends it early. Skip does not get the ceremony, since
skipping means the opposite of wanting to be shown things.

Two things keep it smooth, and both were measured with `dumpsys gfxinfo` rather than
guessed at. The cover is a translucent rect drawn in `drawBehind`, not `Modifier.alpha`
on the box: alpha on a full-screen node makes the renderer allocate and blend an
offscreen buffer for the whole screen every frame, which on the emulator took 56% of
frames over budget. And both animated values are read inside the draw and layer lambdas,
so a frame costs a redraw rather than a recomposition. After: 3%.

Replay intro in settings flips the state directly instead of only writing `onboarded`.
The launcher is the home screen and so almost never starts cold, which is why the old
"shows on next launch" never seemed to happen.

### Fonts

Six providers, behind one id. A stored font is a string like `g:Inter`, `s:Geist Mono`,
`h:Satoshi`, `d:/system/fonts/Roboto-Regular.ttf`, `f:/data/.../fonts/1730-My.ttf` or
`b:Serif`, and `Fonts.family` turns it into a `FontFamily`. A bare string with no tag is read as a Google
family, which is how ids written before there were providers keep working without a
migration pass over every per-app override. The two old `Style` fields fold into the single
id once, in `migrateFont`.

Every piece of text in the launcher already went through one `Style.fontFamily` extension,
so all of this landed behind that property and no call site changed.

- **Your files** are .ttf or .otf files copied into `filesDir/fonts`, because a content URI
  borrowed from a file manager will not outlive a reboot. Each is turned into a typeface
  once before being kept, so a file that is not a font is refused at the picker rather than
  leaving a hole at the next cold start.
- **On this phone** is `/system/fonts`, which is world readable. Around two hundred files on
  a stock image, most of them one alphabet each. They need no fetch, no network and no Play
  services. The file list is folded to one row per family: the plain file beats `-Regular`,
  which beats `-VF`, which beats `-Bold` and the rest, and `prettify` turns
  `NotoSansBengaliUI-VF.ttf` into "Noto Sans Bengali UI".
- **Built in** is the five generic families the platform always has.
- **Google Fonts** come two ways. When Play services is there, the downloadable-fonts
  provider hands back a typeface it caches once for every app on the phone, and the
  launcher stores nothing. When it is not, `FontWeb` fetches the file straight from the
  CDN and keeps it, so the font works offline from then on. Neither route needs an account
  or an API key. Around 1950 families.
- **Fontsource** is an open mirror with its own catalogue and its files on jsDelivr. About
  2100 families, of which roughly 1980 are the Google ones again and about 120 are not in
  Google at all. Its id and default subset build a file path directly, so no request is
  needed to find a download.
- **Fontshare** is 100 families from Indian Type Foundry, none of them in Google. Its file
  URLs are opaque hashes, so each download costs a stylesheet request first. It also files
  a font under several categories at once and hands them over as one string, so the group
  chips split on the comma and such a font appears under each.

`searchAll` is the cross-provider search. It walks the providers in preference order,
groups by lowercased name and keeps the first, so the local copy wins over a download of
the same family: the phone really does ship Roboto, Dancing Script and others that are also
in the Google catalogue, and nearly all of Fontsource is Google again under another name.
Google is ordered ahead of the two mirrors because on a phone with Play services it is the
one route that stores nothing. The row then says where else that name was found, so the
choice is visible rather than silently made. To force a particular provider's copy, switch to that
provider and pick it there.

The Play services provider has to be told which signing certificates count as the real
Play services.
The documented way is to paste a block of base64 into resources, which androidx does not
ship and which is only a copy of what is already on the phone. `Fonts.init` asks the
package manager for them instead. It must use the deprecated `GET_SIGNATURES` to do it:
that is exactly what the androidx font code asks for when it checks back, and the lists
are compared whole, so the newer signing-certificate history can hand back a rotated
certificate the check has never seen. Getting this wrong fails silently, with no fetch
attempted and no log line. The trade is that a phone whose Play services had been replaced
would not be caught; the package name is pinned and the real one is a system app.

The family list comes from `https://fonts.google.com/metadata/fonts`, the same JSON
fonts.google.com itself loads. It is around 1950 families with their categories, needs no
key, and is cached to a file and refreshed weekly. The shorter list in `Fonts.kt` is the
fallback for a phone that has never had a connection, and supplies the curated "Popular"
group that stays at the front of the tabs; the rest of the tabs are Google's own categories
once the fetch has landed. Any name typed by hand is passed straight through either way.

Fetching one Google file is two requests. `https://fonts.googleapis.com/css2?family=<Family>`,
asked by something whose user agent does not claim to understand woff2, answers with CSS
containing a direct `.ttf` URL, and that URL is the download. Fontshare works the same way
but refuses that ancient user agent outright, so it gets a current one: the two providers
need opposite lies about the browser. Fontsource needs no stylesheet at all, since the path
is predictable from the catalogue.

Every download lands under a `.part` name and is renamed only once it is complete, so a
dropped connection cannot leave something that looks like a font but is not one. Android
cannot read woff2 or woff, which is the whole reason for the user agent games, and it is
why Bunny Fonts is not here: it is reachable and free, but serves nothing else.

The picker shows a pageful at a time, each row drawn in the font it offers. That cap is
deliberate: a row on a web tab is a real download, so a whole catalogue on one screen would
ask for two thousand of them before anyone had chosen anything.

Those previews are cached per provider under `filesDir/net/<tag>`, and the cache is capped
at 48 files each, oldest evicted. Without the cap a few minutes of scrolling quietly keeps
every font it drew, and some faces are well over a megabyte. The font actually in use lives
in the same cache, so `FontWeb.touch` restamps it whenever it is resolved, which the home
screen does constantly: that keeps it at the front of the queue and out of reach of a scroll
through some other provider. Anything saved with "Keep" is in the user's own folder, where
pruning never looks.

"Keep" on any web row downloads that family into the user's own font folder. It only
saves: picking it is still a tap on the row, since wanting a copy and wanting to use it are
not the same thing. Once saved it shows up under Your files, and the cross-provider search
folds it together with the catalogue entry it came from.

The button has four words, which it needed: "Keep", "Saving", then "Kept" if the file is
now in the user's folder or "Retry" if the download failed. It used to have two, so a
finished save flashed "Saving" and dropped back to "Keep", which reads as though nothing
happened, and a real failure looked exactly the same. "Kept" is derived from the file list
rather than from having just pressed the button, so it is still right on a later visit.

Switching provider clears the search box. A name typed against one provider usually matches
nothing in the next, and leaving it there shows an empty list with no visible reason.

One app can have its own font. It is stored in `Overrides` beside the rename and the
replacement icon, carried on `AppEntry.font`, and resolved by `Style.familyFor`, which every
place that draws an app name goes through so they cannot drift apart. Empty means follow
the global font, which is what all of them do until told otherwise.

Two different things are called a provider here, and only one of them is pluralisable.
Downloadable fonts are a general Android mechanism, but Play services is the only such
*provider app* installed on an ordinary phone, so probing for more would find nothing.
A *catalogue* on the web needs nothing installed, which is why there are three of those and
why the Google tab works on a phone with no Play services at all. Adding a fourth means a
list endpoint and a rule for turning a family name into a font file URL, both in `FontWeb`,
plus an entry in `FontSource`; the picker, the search and the dedupe follow from `web = true`
and need no changes.

### Themes

`Presets.kt` holds the whole-look presets and `ui/ThemesPage.kt` shows them. A `Preset` is
a flat list of look values plus `applyTo`, which copies them onto the current `Style` and
leaves everything else alone, so columns, dock, gestures, widgets and hidden apps survive a
theme change. Each is a nod rather than a reproduction: what a launcher can actually borrow
from an old interface is the palette, the icon shape, how much glass there is and the
typeface, and the preset sets exactly those.

Light themes needed one change first. Every piece of text in the launcher said
`Color.White`, which quietly ruled them out, so `Style.ink` now carries the text colour and
`LocalInk` delivers it. The swap was mechanical in all but one spot: a composition local
cannot be read inside a `drawBehind` or `Canvas` lambda, so the home screen's drop-target
grid reads it once outside and closes over the value.

The light presets assume a light wallpaper. App names on the home screen sit on the user's
own picture rather than on the theme, so the ink has nothing to do with what is behind them
there; the page says so rather than pretending otherwise.

### Theming notes

- `Style` lives in `Style.kt` and reaches the UI through `LocalStyle`. Anything that draws
  should read from it rather than hardcoding.
- **Icon shape is baked at render time, not clipped at draw time.** Adaptive icons are
  drawn from their raw `background`/`foreground` layers so our mask is the only one applied
  -- clipping the already-system-masked bitmap would leave gaps inside a square. Changing
  shape triggers a full `repo.reload()`.
- Older non-adaptive icons are masked as-is, so a square legacy icon can lose its corners.
- **Blur is real `FLAG_BLUR_BEHIND` + `blurBehindRadius`** (Android 12+). Verified set on
  the window via `dumpsys window`, but the emulator's GPU does not render it despite
  `ro.surface_flinger.supports_background_blur=1`. Untested on real hardware. Where blur
  does not render it degrades to the same result as Dim, which is intentional.
- Fonts are Android's built-in families (default / sans / serif / mono / cursive). No
  typefaces are bundled, so no APK bloat and no network dependency. Bundling real custom
  fonts is a separate decision.
- A translucent panel sits in our own window, so the home layer is faded out whenever the
  drawer or settings is open -- otherwise the clock and dock ghost through the panel.

### Wallpaper rotation notes

- Images are picked through the system document picker and stored as content URIs with
  a **persistable** read grant, so they survive reboots.
- "Add folder" uses `OpenDocumentTree` and enumerates children with `DocumentsContract`
  (no `documentfile` dependency). The tree grant covers the child documents, so those URIs
  keep working too. It reads images **directly inside** the folder, not sub-folders.
- **The folder is stored, not its contents.** `Wallpaper.sources()` expands the saved tree
  URIs on every change, so a picture dropped into a watched folder is in the rotation
  without the folder being picked again. The settings page says how many images that comes
  to right now, which is read off the disk rather than from anything saved.
- Android refuses a tree grant on some well-known folders, Downloads among them. That is
  the system's rule, not ours: it answers "Can't use this folder" in its own picker.
- **A live wallpaper is drawn over the static one.** Setting a picture underneath one
  succeeds and changes nothing you can see, which looks exactly like the phone ignoring
  the app. Phones that ship with a moving wallpaper, Samsung among them, land here. So
  `wallpaperInfo != null` means clearing first.
- The image is decoded to a bitmap and handed over with `setBitmap` rather than as a
  stream. Some skins quietly refuse a stream they cannot crop themselves; a bitmap gives
  them nothing to refuse. `setStream` stays as the fallback when decoding fails.

### Animated background

A moving background cannot be the system wallpaper. `WallpaperManager` takes a bitmap, so
the alternatives are a live wallpaper service or a layer the launcher draws itself.
`ui/LiveBackground.kt` is the second, which keeps it inside our own window and means it
costs nothing the moment another app is in front.

- GIF and animated WebP go through `ImageDecoder` to an `AnimatedImageDrawable`. That
  class is API 28 and this app starts at 26, so below that the file still shows as its
  first frame rather than nothing.
- `repeatCount` counts repeats *after* the first run, so 0 is "play once" and leaves the
  last frame up. That is the other half of the Repeat switch.
- Video uses `MediaPlayer` on a **TextureView**, not a SurfaceView: a SurfaceView punches
  a hole through the window and this one is deliberately translucent. A non-looping player
  stops at the end and the texture keeps the last frame, which gets the same behaviour for
  free. Sound is muted; a background with audio is nobody's idea of a background.
- A TextureView stretches its content to its own shape, so `cropToFill` scales the larger
  way and lets the overflow fall off the edges, the way a wallpaper does.
- Both halves stop on `ON_PAUSE` and start again on `ON_RESUME`.
- "Choose a wallpaper" applies one image right away via `Wallpaper.setOne`, independent of
  the rotation list, using the same Apply-to target.
- Rotation runs on WorkManager. **15 minutes is Android's floor** for periodic background
  work -- anything shorter needs a foreground service.
- Lock screen uses `WallpaperManager.FLAG_LOCK`. Some OEM skins block this; stock Android
  and the emulator are fine.
- A dead URI (file deleted, permission revoked) is skipped and the rotation moves to the
  next image rather than stalling.

## Status

Working and verified on the emulator: both home modes, drawer scroll and pages, search,
hidden apps, drag and folders, wallpaper rotation from files and folders, single wallpaper,
the colour picker, the live preview, the first-run intro, widgets (add, configure, resize,
reorder, remove, and surviving a cold start), icon opacity, icon packs (both themed apps
and dressed ones), renaming an app, swipe-down-for-notifications, swipe-to-lock, and
notification dots. No outstanding crashes.

One thing could not be driven from adb: **double tap**. Each `adb shell input tap` spawns
a JVM, which is far slower than the 300ms double-tap window, so the two taps never pair.
The detector is the stock Compose one and the action it fires is the same code path that
swipe-to-lock proved, but the pairing of the two taps is untested. Try it by hand.

## The loop

The emulator must be running. Then, from this folder:

```
.\dev.ps1 boot      start the emulator (once per session)
.\dev.ps1 run       build + install + set as home + press home
.\dev.ps1 restart   force-stop and relaunch, fastest way to see a change
.\dev.ps1 log       tail logcat for just this app
.\dev.ps1 reset     hand the home app back to the stock launcher
.\dev.ps1 wipe      clear saved dock favourites
```

Android Studio is **not** needed. It is installed, but only useful as a code editor.

## Toolchain as actually installed

| piece | value |
|---|---|
| Android Studio | 2026.2.1 (Rabbit 1) |
| Gradle | 8.9 (wrapper committed) |
| AGP / Kotlin | 8.7.3 / 2.0.21 |
| JDK for Gradle | Temurin 21, `C:/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot` |
| compileSdk / target | 35 / 35, minSdk 26 |
| build-tools | 35.0.0 (AGP 8.7.3 requires it; 36.0.0 alone is not enough) |
| AVD | `Medium_Phone_API_37.0`, API 37, google_apis_playstore, x86_64 |
| acceleration | WHPX (VBS/Hyper-V active; HAXM is discontinued and incompatible) |

### Gotchas that cost time

- **Studio bundles JDK 25, Gradle 8.9 does not support it.** Temurin 21 is installed and
  `gradle.properties` sets `org.gradle.java.home`. That is not enough on its own --
  `gradlew.bat` needs a JVM *before* it reads that file, so `dev.ps1` also sets `JAVA_HOME`.
- **`sdkmanager` and `avdmanager` are deprecated** in this release, replaced by a single
  `android` CLI (`cmdline-tools/latest/bin/android.exe`). Package paths changed from
  `build-tools;35.0.0` to `build-tools/35.0.0`.
- **`$ErrorActionPreference = 'Stop'` breaks the script.** Windows PowerShell turns native
  stderr into an ErrorRecord, and both gradlew and adb write harmless warnings there.
  `dev.ps1` uses `'Continue'` and checks exit codes explicitly.
- **Do not capture screenshots with `adb exec-out screencap -p > file.png` in PowerShell.**
  Redirection adds a BOM and corrupts the binary. Use `adb shell screencap -p /sdcard/s.png`
  then `adb pull`.
- Running an API 35 build on an API 37 device is normal and supported.

### Readability and gestures

- Anything drawn straight onto the wallpaper (clock, date, hint, app names) carries a soft
  black `Shadow`. Without it white text disappears entirely on a light wallpaper, which is
  not hypothetical: the stock emulator image is near-white.
- The drawer closes on **pull-down past the top of the list**, via a `NestedScrollConnection`
  watching unconsumed downward scroll while `canScrollBackward` is false. The earlier
  plain drag only worked on the margins, because the grid consumed vertical drags first.

### Things that bite on the emulator

- **The app grid eats swipe-down-to-close.** Closing by swipe works on the search bar and
  margins only. Back button and launching an app also close it. Proper fix is a
  nested-scroll connection; not done yet.
- **Gesture nav vs swipe-up** is handled -- drags starting in the system gesture inset are
  ignored. Start swipes from mid-screen.
- **Cold boot is slow, snapshots are fast.** Leave the AVD running between builds.

### Backup

Everything the launcher knows lives in five preference stores, `launcher`, `style`,
`overrides`, `layout` and `widgets`, plus two folders of files: the fonts you added and the
icons you replaced. `Backup.kt` writes all of it to one JSON file through the system file
picker, and reads it back.

Values carry their type (`{"t":"l","v":4290759167}`), because SharedPreferences is typed and
asking for a key as the wrong type throws. Putting an `Int` back where a `Long` was would
break the next read, not the write, which is the kind of bug that surfaces a week later.
Font and icon files travel as base64 in the same file, so a backup is the whole setup rather
than a list of settings pointing at things that are gone.

Two things do not survive a move to another phone, and the import says so instead of failing
quietly:

- **Widgets** are host ids issued by the phone they were placed on. The import skips that
  store when the device string does not match, which leaves whatever that phone already had
  rather than wiping it.
- **Icon packs** are other apps. The choice comes back; it needs that pack installed.

A font id like `f:/data/user/0/.../fonts/Satoshi.ttf` names a path, and the path is not the
same on the new phone. The file travels with the backup, so the id is repointed at wherever
it landed. Anything else is left alone.

A restore rewrites every store underneath a running screen, half of which is already held in
composable state, so it finishes by starting the activity again. That takes the settings
screen and its status line with it, which is why the result is a toast: it is the one message
that has to outlive the restart. Toasts get two lines and silently cut the rest, so the text
is short and the long explanation stays on the page.

### Undo for themes

A preset overwrites a dozen colour fields at once, and nobody remembers what they were. This
was not hypothetical: it happened, and the colours were recovered by sampling pixels out of
old screenshots.

`StyleStore.snapshot` copies the whole `style` store into a second one before a preset is
applied, by iterating the entries rather than naming fields, so a setting added later is
covered without anyone remembering to add it here. One snapshot, not a history: the colours
before the last theme are worth keeping, every look anyone ever tried is not. Using the undo
clears it, so it cannot be pressed twice and land somewhere nobody asked for.

## Building a shareable APK

```
gradlew assembleDebug      # app/build/outputs/apk/debug/app-debug.apk
gradlew assembleRelease    # app/build/outputs/apk/release/app-release.apk
```

The release build is signed with a key in `keystore.jks`, whose passwords are in
`keystore.properties`. Both are gitignored, and both matter: **a new key cannot install over
an app signed with the old one**, so losing them means uninstalling before upgrading, which
takes the whole setup with it. Keep a copy somewhere other than this folder. Export a backup
too, and the loss is survivable either way.

Signed with v2 and v3, not v1, which is only needed below API 24 and this app starts at 26.
A clone without the key still builds a release: it comes out unsigned rather than failing.

`isMinifyEnabled` is off on purpose. Icon pack support looks resources up by name through
`getIdentifier`, which a shrinker cannot see and would strip.

Debug and release are signed with different keys, so a phone cannot have both. Installing one
over the other means uninstalling first, and that erases everything.

### Updates

The launcher watches its own GitHub releases. A WorkManager job runs every three hours on
any connection, and a throttled check also runs whenever the launcher starts, which is
what actually makes a release turn up the same hour it lands: a periodic job runs when the
system feels like it, and the home screen is opened dozens of times a day. It reads the
latest release, compares its tag
against the installed `versionName`, and downloads the attached APK in the background.
Unmetered because this ends in a seven megabyte download nobody agreed to pay for.

**It cannot install silently.** Android allows that only for an app that owns the device or
is part of the system image, and this is neither, so the last step is always a tap on
Android's own installer dialog. Everything before it is automatic, which was the tedious
part. `REQUEST_INSTALL_PACKAGES` only buys the right to ask; the user still has to allow
installs from this app once, on a system screen that is not a runtime prompt.

No notification permission is requested. Android 13 made posting one a prompt, and asking on
first run to say "there might be an update one day" is a poor trade when this app *is* the
home screen. A ready update says so in a pill above the dock, and "Later" sets that version
aside rather than the row forever.

Three things have to line up, and `dev.ps1 release` does all three so none of them depends
on anyone remembering:

- **The tag is the version.** `v0.3` means versionName `0.3`. `Updater.newer` compares them
  as dotted numbers, so 0.10 is correctly newer than 0.9 where a string compare says the
  opposite.
- **The APK is attached to the release**, since that is what gets downloaded.
- **It is signed with the same key as the installed copy.** Android refuses the install
  otherwise, with a message that does not explain why. A debug build cannot update a release
  build, or the other way round. This is the one that will waste an afternoon.

The download is size-checked against what the release says before it is ever offered, because
a truncated APK installs as nothing but an error. It lands in the cache, under a name carrying
its version, and any other file in that folder is cleared first so an abandoned attempt is
never mistaken for the real thing.

The `FileProvider` is scoped to that one cache folder, so it cannot be talked into handing out
anything else the app stores.

## Running it on another phone

minSdk is 26, targetSdk 35, and lint reports no `NewApi` findings, so nothing calls an API
that an Android 8 phone would not have.

What needs doing by hand on a new device:

- Set it as the home app. Android asks the first time home is pressed.
- Notification dots need the notification listener permission, and gestures need the
  accessibility service. Both are per-device and cannot be granted by a backup.
- Widgets have to be placed again. See above.

Samsung and other skins ship their own fonts in `/system/fonts`, so the "On this phone" tab
lists a different set there. That is the tab working, not failing.

Two locale bugs were fixed while checking this, and both only appear on a phone set to a
language with its own digits. `String.format` with the default locale would render the hex
colour field in digits its own parser then rejects, leaving the field uneditable. Both
formatting sites are pinned to one locale now.

Panels pad for `displayCutoutPadding` as well as the system bars, which matters on a
punch-hole screen in landscape, where the cutout sits outside the status bar.

## Layout

```
app/src/main/java/com/maria/homelauncher/
  MainActivity.kt       activity, HOME wiring, onNewIntent, window blur, style state
  AppRepo.kt            installed apps, icon rendering (shape mask + mono tint), intents
  Layout.kt             placed items, folders, JSON store, install sync, packing
  DrawerFolders.kt      folders inside the drawer, and the rows it shows
  Prefs.kt              dock, hidden apps, wallpapers, flags
  Style.kt              the whole look as one data class, plus LocalStyle
  Wallpaper.kt          rotation worker, folder enumeration, apply
  Widgets.kt            widget host, the two-step add flow, cell spans
  IconPack.kt           third-party icon themes: discovery, appfilter, masking
  Overrides.kt          per-app name and icon replacements
  Backup.kt             the whole setup to one file, and back
  Updater.kt            release checking, download, and the install hand-off
  Gestures.kt           accessibility service and the gesture actions
  Fonts.kt              the six font providers, behind one id
  FontWeb.kt            Google, Fontsource and Fontshare over plain HTTP
  Presets.kt            whole-look themes and how they apply
  NotificationWatch.kt  notification listener, package names only
  WallpaperColour.kt    accent sampled out of the wallpaper
  ui/Launcher.kt        root, home screen, drawer, paged grid, item sheet
  ui/HomeGrid.kt        the free-placement grid: drag, drop guide, folders, widget tray
  ui/Overlays.kt        folder overlay, search-only panel
  ui/Settings.kt        page router, root list, wallpaper and hidden pages
  ui/Appearance.kt      the per-page control bodies and shared widgets
  ui/ColorPicker.kt     hue/sat/bright/alpha bars, hex and RGBA entry
  ui/Preview.kt         live mock of home and drawer
  ui/LiveBackground.kt  the GIF and video layer behind everything
  ui/Onboarding.kt      six-screen first-run intro
  ui/WelcomeFade.kt     the fade from the last intro page into home
  ui/FontPage.kt        the font picker: provider dots, search, keep
  ui/ThemesPage.kt      the preset list, each row in its own colours
  ui/BackupPage.kt      save and load, and what does not travel
  ui/UpdatePage.kt      version, auto-check toggle, check and install
  ui/UpdateBanner.kt    the one-line offer on the home screen
  ui/WidgetViews.kt     settings page, catalogue grouped by app, drag-out hooks
  ui/GesturesPage.kt    one row per gesture, with its chooser
  ui/BadgesPage.kt      notification dots and their permission
  ui/AppEdit.kt         rename and change-icon dialog
  ui/Theme.kt           Material scheme from Style
```

## Not done yet

- **A notification panel of our own.** The listener already sees what is waiting, so the
  launcher could draw that list in its own style instead of only opening the system one.
- **A quick settings tile**, so Home appears as a toggle in the real QS panel.
- **Icon pack shape normalising.** A pack icon is used exactly as drawn. Packs with
  inconsistent artwork will look inconsistent.
- **Widget placement on the grid.** Widgets stack vertically above the apps; they cannot
  be dragged into a particular cell or resized by their corners the way the stock launcher
  does. Height is a slider in settings.
- **Folder re-scan.** Picking a wallpaper folder snapshots it; images added later are not
  picked up until you pick the folder again.
- **System-wide recents.** Removed on request. If it ever comes back, `UsageStatsManager`
  would make it see launches from outside this launcher, at the cost of a permission.
- A dead `recents` key may still sit in `launcher.xml` from the removed feature. Nothing
  reads it.
