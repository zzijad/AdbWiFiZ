# AdbWiFiZ

Android aplikacija koja automatski uključuje **Wireless debugging** čim se telefon poveže na Wi-Fi,
i ponovo ga uključi ako ga sistem (MIUI) ugasi. Pravljena kao dodatak za ScrcpyZ.

## Podešavanje (jednom)

1. Instaliraj APK (GitHub → Releases).
2. Xiaomi: Developer options → uključi **USB debugging** i **USB debugging (Security settings)**.
3. Poveži telefon kablom i na računaru pokreni:
   ```
   adb shell pm grant com.zzijad.adbwifiz android.permission.WRITE_SECURE_SETTINGS
   ```
4. Otvori AdbWiFiZ → „Baterija: bez ograničenja“ i „Autostart“ → uključi.
5. Na svakoj Wi-Fi mreži (kuća, škola) jednom ručno uključi Wireless debugging i potvrdi
   **„Uvek dozvoli na ovoj mreži“**.
6. U skorašnjim aplikacijama zaključaj AdbWiFiZ (katanac).

Posle toga aplikacija radi sama, i posle restarta telefona.

Napomena: `app/adbwifiz.jks` je ključ za potpisivanje i nalazi se u repozitorijumu, da bi svaka
nova verzija mogla da se instalira preko stare.
