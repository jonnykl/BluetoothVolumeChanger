# BluetoothVolumeChanger
Xposed module to change bluetooth media volume even when no bluetooth device is connected.

# Usage
After installation of the app and reboot you can send a Intent to set the bluetooth media volume. It expects an extra integer between 0 and 15.

The name of the action is `com.gmail.klamroth.jonathan.bluetoothvolumechanger.ACTION_CHANGE_BT_VOLUME`. The name of the volume extra is `com.gmail.klamroth.jonathan.bluetoothvolumechanger.EXTRA_VOLUME`.

To test it execute following command (you can replace 5 with a volume of your choice between 0 and 15):
```
am broadcast -a com.gmail.klamroth.jonathan.bluetoothvolumechanger.ACTION_CHANGE_BT_VOLUME --ei com.gmail.klamroth.jonathan.bluetoothvolumechanger.EXTRA_VOLUME 5
```

To get the current bluetooth media volume execute the following command:
```
settings get system volume_music_bt_a2dp
```

# License
This module is licensed under the terms of the MIT license.
