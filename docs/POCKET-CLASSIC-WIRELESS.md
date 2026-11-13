# Pocket Classic wireless ADB

Updated: 2026-11-13. Device access was configured and verified without changing apps or game data.

## Verified device and connection

- Device: Retroid Pocket Classic (`parrot`), Android 14 / API 34.
- Wi-Fi IPv4 at verification: `192.168.12.233` on `wlan0`.
- ADB TCP listener was already active on port `5555`; `adb_wifi_enabled=0`, so Android's Wireless debugging/pairing mode was not enabled. No pairing code was used or recorded.
- Connected endpoint: `192.168.12.233:5555`. After disconnect/reconnect, `adb shell getprop ro.product.device` returned `parrot` and Android version returned `14`.
- The device has `com.ast.openmw` version `0.51.0-12` (versionCode 5112). This was inspected read-only.

ADB still requires authorization from the host. Port 5555 is reachable on the device's current Wi-Fi network; use this only on a trusted network. The observed Wi-Fi address may change. Reboot persistence has not been tested, so do not assume the TCP listener or address will survive reboot.

## Reconnect

Check the endpoint and connect directly:

```powershell
adb devices -l
adb connect 192.168.12.233:5555
adb -s 192.168.12.233:5555 shell getprop ro.product.model
```

Expected model: `Retroid_Pocket_Classic`. Always qualify device commands with `-s 192.168.12.233:5555` after connecting; this host also has another Android device attached. Do not use an unqualified `adb shell`.

If the address changes while the Classic is USB-connected, get the current address using the Classic's USB transport (transport 6 was its observed ID; transport IDs can change):

```powershell
adb devices -l
adb -t 6 shell ip -o -4 addr show wlan0
```

Use the returned `wlan0` address in `adb connect <address>:5555`, then address it with `adb -s <address>:5555 ...`. Confirm the model before any operation. If transport 6 no longer identifies the Classic, rediscover it with `adb devices -l`; never guess or use an unqualified command.

If the listener is no longer active, connect the Classic over USB, rediscover its current transport ID, and only after confirming it is the Pocket Classic run:

```powershell
adb -t <classic-transport-id> tcpip 5555
```

Then reconnect with its current Wi-Fi address as above. `adb tcpip` was not needed during this setup because port 5555 was already listening. After a reboot or ROM update, verify the listener; if it is off, repeat the USB activation step. Persistence is unverified.

To close the host's network connection:

```powershell
adb disconnect 192.168.12.233:5555
```

## Storage inspection boundary

The installed OpenMW package is the separate `com.ast.openmw` app, version `0.51.0-12`, with package data directory `/data/user/0/com.ast.openmw`. No app, UI, profile, configuration, save, or storage contents were changed as part of wireless setup.
