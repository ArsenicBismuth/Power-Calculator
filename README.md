
# Power Calculator

![Final](docs/img/ss01.png)

Simple but solid application for interfacing a power calculator device.
Basic functionality with:
- Bluetooth devices list.
- Expandable settings.
- Tidy debug mode.
- Template-able layouts.
- Solid without any ugly bugs.

[Apk] located in `release\app-debug.apk`.

## How it works
The basic workflow of this app:
	1. Select one from the list of paired Bluetooth devices.
	2. Press the pink button on the app to send a start sign in the form of `!` character.
	3. App waits for string with format `#1111/2222~` where `1111` and `2222` should be the same number with any number of digits.
	4. The app does some basic checks and updates the display.


## Testing
Testing can be done using the Arduino dummy program located in `arduino\` or just directly using PC.

To test using PC:
	1. Pair the smartphone to the PC.
	2. Search "bluetooth" in control panel.
	3. Select "Change Bluetooth settings" > COM Ports.
	4. Add > select Incoming > OK.
	5. Install and use any terminal program such as TeraTerm.
	6. Establish connection to the one of the COM.
	7. Type anything to send a character to the smartphone.

![Settings](docs/img/ss02.png)
![Debug](docs/img/ss03.png)
    

   [Apk]: <https://github.com/ArsenicBismuth/Power-Calculator/blob/master/release/app-debug.apk>
