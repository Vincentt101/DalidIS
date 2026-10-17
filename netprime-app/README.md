# NetPrime app (Android phone + Google TV)

Opens https://netprime.bz/ full screen.

- Shows up on the Google TV home screen (banner + TV launcher entry).
- Blocks ads: requests to ~73k known ad/tracking servers are refused (StevenBlack/hosts list),
  pop-up windows are blocked, and links that leave netprime.bz are ignored.
- On TV, the remote's arrow keys move an on-screen pointer; OK clicks. Push against the
  top/bottom edge to scroll. Play/Pause on the remote toggles the video.
- Fullscreen video supported; Back leaves fullscreen, then goes back a page, then exits.

Build: `./build.sh` (needs Java 17+, Python 3 + Pillow, internet). Output: `../apk/NetPrime.apk`.
To allow another web address, add it to `ALLOWED_SITES` in `MainActivity.java`.
