# Material Files (fork)

An open source Material Design file manager for Android 6.0+ (with QoL improvements)

## Preview

<p>
<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/1.webp" width="22%" />
<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/2.webp" width="22%" />
<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/3.webp" width="22%" />
<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/4.webp" width="22%" />
</p>

## Changes

- (everything [upstream](https://github.com/zhanghai/MaterialFiles/tree/fc1250038496ebf4d4c139f62d16f0071f2c995a)), additionally:
- [Sora Editor](https://github.com/Citrinae-Lime/MaterialFiles.Sora-Editor) (from [Citrinae-Lime](https://github.com/Citrinae-Lime)), with:
  - Search and replace, syntaxes, themes, fonts

Then...

- [Telephoto](https://github.com/saket/telephoto) for image viewing instead of PhotoView
- Folder size calculation
- Modify archives (rename, delete, paste into, edit, etc)
- Additional archive formats: zstd, gzip, tar
- Compression levels
- Termux in 'Open in Terminal'¹ (root-only paths supported)
- Migration from Groovy to Kotlin DSL and some things to Jetpack Compose

Full credits to [Hai Zhang](https://github.com/zhanghai) for the original app and [Citrinae-Lime](https://github.com/Citrinae-Lime) for the Sora Editor modifications

## Additional info

¹ For the "Open in Terminal" function to properly work in Termux, you need to first edit the `~/.termux/termux.properties` file from within termux, and set `allow-external-apps = true` ([info](https://wiki.termux.com/wiki/Terminal_Settings), [more info](https://github.com/termux/termux-app/wiki/RUN_COMMAND-Intent#allow-external-apps-property-mandatory)), then grant the permission from Material Files' "App Info" in settings, usually the flow is `Settings > Apps > Material Files > Permissions > Additonal permissions > Run commands in termux environment` (might be slightly different depending on your device/manufacturer)

## License

    Copyright (C) 2018 Hai Zhang

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>.
