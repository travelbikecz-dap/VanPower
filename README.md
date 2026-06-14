# VanPower — Delta 3 + Android Auto

Proyecto Android (Kotlin) para controlar una **estación de energía portátil** (Delta 3) desde **Android Auto** vía BLE.

## Estado actual

- Protocolo BLE Delta 3 V3 portado desde [ecoflow2nut-pibridge](https://github.com/alanstrok/ecoflow2nut-pibridge)
- Handshake ECDH (encrypt_type 7) + AES-128
- Telemetría: batería, solar, entrada/salida, estado AC/USB/DC
- Controles: toggles AC 220V, USB, DC 12V
- UI Android Auto IoT (dashboard + controles)

## Requisitos

- Android Studio Ladybug o superior
- JDK 17
- Teléfono Android 8+ (API 26)
- Ecoflow Delta 3 (`EF-D3`)

## Desarrollo (uso personal)

1. Abre el proyecto en Android Studio y deja que sincronice Gradle.
2. Instala la APK en debug (`Run` → tu móvil).
3. Configura MAC, serial y user_id en la app.
4. Prueba Android Auto con el **Desktop Head Unit (DHU)**:
   ```powershell
   .\dhu\launch-dhu.ps1
   ```
   El script usa resolución **800×480** (ventana compacta, no pantalla completa). Tras cambiar resolución, **reinicia el servidor de unidad principal** en Android Auto (móvil).

`HostValidator.ALLOW_ALL_HOSTS_VALIDATOR` está activo para desarrollo. **Cámbialo antes de publicar en Play Store.**

## Próximo paso

Probar con tu Delta 3 real (ver abajo). Si el handshake falla, revisar `adb logcat -s Delta3BleClient`.

## Publicación futura (opcional)

- Categoría Play Store: **IoT**
- Cumplir [Car App Quality](https://developer.android.com/docs/quality-guidelines/car-app-quality)
- `HostValidator` de producción
- Política de privacidad (no se usa cloud Ecoflow para telemetría)
