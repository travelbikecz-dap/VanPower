# VanPower — Delta 3 + Android Auto

Proyecto Android (Kotlin) para controlar una **estación de energía portátil** (Delta 3) desde **Android Auto** vía BLE.

Repositorio: [github.com/travelbikecz-dap/VanPower](https://github.com/travelbikecz-dap/VanPower)

## Estado actual

- Protocolo BLE Delta 3 V3 portado desde [ecoflow2nut-pibridge](https://github.com/alanstrok/ecoflow2nut-pibridge)
- Handshake ECDH (encrypt_type 7) + AES-128
- Telemetría: batería, solar, red AC, entrada/salida, tiempos de carga/autonomía, AC/USB/DC
- Controles Android Auto: suspender/restaurar salidas + toggles AC 220V, USB, DC 12V (con memoria al reactivar)
- Refresco automático del panel en el coche cuando llega telemetría BLE
- `HostValidator` de producción en release (Android Auto); debug permite DHU

## Pantalla Android Auto (6 filas)

1. **Batería** — % + suspender/activar todas las salidas (restaura el estado anterior)
2. **Energía** — entrada/salida total + solar/red
3. **Tiempo** — carga estimada y autonomía
4. **AC 220V** · **USB** · **DC 12V**

Sin BLE conectado (DHU): modo **Simulación** con datos de ejemplo.

## Requisitos

- Android Studio Ladybug o superior
- JDK 17
- Teléfono Android 8+ (API 26)
- Estación Delta 3 (`EF-D3`) para uso real

## Desarrollo

1. Abre el proyecto en Android Studio y sincroniza Gradle.
2. Instala la APK debug (`Run` → tu móvil).
3. Configura MAC, serial y user_id en VanPower (móvil).
4. Prueba Android Auto con el **Desktop Head Unit (DHU)**:

```powershell
.\dhu\launch-dhu.ps1
```

Resolución DHU: **1280×720**, dpi 160 (`dhu/car-display.ini`).

**Móvil:** Android Auto → modo desarrollador → **Iniciar servidor de unidad principal**.

En **debug**, `HostValidator` acepta el DHU. En **release**, solo el host oficial de Android Auto.

## Play Store / coche real

La APK de desarrollo **no aparece** en el coche sin Play Store. Pasos:

1. Cuenta Google Play verificada
2. Subir AAB firmado a **prueba interna**
3. Instalar desde Play en el móvil
4. Política de privacidad: [docs/privacy.html](docs/privacy.html)  
   (activar GitHub Pages en el repo → Settings → Pages → carpeta `/docs`)

URL sugerida en Play Console:  
`https://travelbikecz-dap.github.io/VanPower/privacy.html`

## Build release

```powershell
.\gradlew.bat bundleRelease
```

Salida: `app/build/outputs/bundle/release/app-release.aab`  
Requiere `keystore.properties` (ver `keystore.properties.example`).

## Subir a GitHub

```powershell
.\scripts\publish-github.ps1
```

(Requiere `gh auth login` o `-Token`.)

## Depuración BLE

```powershell
adb logcat -s Delta3BleClient
```

## Créditos protocolo

Basado en trabajo de [ha-ef-ble](https://github.com/bostick/ha-ef-ble) y [ecoflow2nut-pibridge](https://github.com/alanstrok/ecoflow2nut-pibridge).
