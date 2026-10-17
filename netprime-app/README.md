# NetPrime app (Android phone + Google TV)

Opens https://netprime.bz/ full screen.

- Shows up on the Google TV home screen (banner + TV launcher entry).
- Blocks ads: requests to ~73k known ad/tracking servers are refused (StevenBlack/hosts list),
  pop-up windows are blocked, and links that leave netprime.bz are ignored.
- On TV, Netflix-style navigation: arrows move between items (selected one grows with a white
  border, rows keep it near the left, each row remembers your place); OK opens
  the highlighted item. Hold OK to switch to pointer mode (a dot you move around) for spots
  the highlight can't reach; hold OK again to switch back. In fullscreen video, left/right
  skip 10 seconds. Play/Pause on the remote toggles the video.
- Fullscreen video supported; Back leaves fullscreen, then goes back a page, then exits.

Build: `./build.sh` (needs Java 17+, Python 3 + Pillow, internet). Output: `../apk/NetPrime.apk`.
Remote navigation lives in `assets/tvnav.js`.
To allow another web address, add it to `ALLOWED_SITES` in `MainActivity.java`.
