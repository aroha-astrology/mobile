# iOS Firebase Setup

The Firebase Authentication plugin needs `GoogleService-Info.plist` to be added to the Xcode project.

## Steps (macOS only)

1. Download `GoogleService-Info.plist` from the Firebase Console for the `com.arohaastrology.app` iOS app.
2. Open `ios/App/App.xcworkspace` in Xcode.
3. Drag `GoogleService-Info.plist` into the `App` target group. Check "Copy items if needed" and select the `App` target.
4. Run `pod install` in `ios/App/`.
5. Build and run on a simulator or physical device.
