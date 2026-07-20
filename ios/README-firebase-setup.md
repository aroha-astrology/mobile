# iOS Firebase Setup

The Firebase Authentication plugin needs `GoogleService-Info.plist` in the Xcode project.
This is already committed at `ios/App/App/GoogleService-Info.plist` and wired into
`App.xcodeproj`'s Resources build phase for the `com.aroha.astrology` iOS app — no
manual Xcode step needed for a normal build.

## If you ever regenerate the platform (`cap add ios`)

`cap add ios` recreates `ios/` from the Capacitor template and won't know about
`GoogleService-Info.plist`. To re-add it:

1. Copy `GoogleService-Info.plist` (from Firebase Console, `com.aroha.astrology` iOS app)
   into `ios/App/App/`.
2. Open `ios/App/App.xcworkspace` in Xcode (macOS only) and drag the file into the
   `App` target group, checking "Copy items if needed" and the `App` target — this
   registers it in the Resources build phase. (Or edit `project.pbxproj` directly:
   add a `PBXFileReference` + `PBXBuildFile` entry and list it in the `App` group and
   the `Resources` build phase.)
3. Run `pod install` in `ios/App/` (macOS only — see [../docs/ios-cocoapods-conversion.md](../docs/ios-cocoapods-conversion.md)).
4. Build and run on a simulator or physical device, or push to trigger the Codemagic
   `ios-unsigned-build` workflow (see `../codemagic.yaml`), which runs `pod install`
   and an unsigned simulator build on every run.
