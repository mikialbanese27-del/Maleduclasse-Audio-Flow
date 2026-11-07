# Maleduclasse Audio Flow — Android Sync

App Android nativa di registrazione battute, con sincronizzazione LAN verso Maleduclasse Audio Flow per Windows.

## Compilazione APK
Il workflow [Build Android APK](.github/workflows/android-apk.yml) crea una build debug installabile su Android 8.0+.
Apri **Actions → Build Android APK → run**, poi scarica l'artefatto **Maleduclasse-Android-APK**.

## Collegamento al PC
Sul computer usa il pacchetto Windows v0.3.0 SYNC e avvia `AVVIA_PC_ANDROID.bat`. Telefono e PC devono essere sulla stessa Wi-Fi privata. Imposta sull'app l'indirizzo del PC (`http://192.168.x.x:18766`) e il codice di abbinamento mostrato sul PC. L'endpoint di sincronizzazione impiega HTTP in LAN: **non aprire la porta 18766 verso Internet**.

## Stato
Questa è una build **di prova**. La compilazione automatica e il collaudo su dispositivi Android effettivi sono due controlli distinti. Le registrazioni sono conservate sul telefono finché il trasferimento non è confermato dal PC.
