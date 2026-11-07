# Maleduclasse Audio Flow — Android v0.4 Mobile First

L'app Android è il punto di partenza per creare **progetti, copioni, cast e take**. Il PC importa queste informazioni, assembla la timeline e gestisce suoni e mix.

## Funzioni Android

- Progetti: nuovo, apri, rinomina, duplica, archivia, elimina localmente.
- Sceneggiatura: editor con sintassi `@Personaggio`, `/scena`, tag, controllo battute, importazione DOCX e dettatura.
- Cast: doppiatori, personaggi, assegnazioni globali e per episodio.
- Doppiaggio: filtro doppiatore, registrazione offline, più take, import audio, ascolto, preferita/provvisoria/eliminazione.
- Sincronizzazione bidirezionale LAN: importa da PC oppure crea su telefono e invia al PC con ID stabili. Il PC crea la bozza dopo le take.
- Protezione da conflitti sulle revisioni; le take precedenti sono archiviate quando il testo cambia.

## APK

GitHub Actions → **Build Maleduclasse Android APK** → artefatto `Maleduclasse_Audio_Flow_Android_debug_APK`.
Compatibilità: Android 8.0+.
Questa è una build **debug da collaudare su dispositivo reale**, non una release definitiva firmata per Google Play.

## Windows

Usare il pacchetto Windows **v0.4 Mobile First**, con `mobile_first_api.py`. Avviare `AVVIA_PC_ANDROID.bat`. Smartphone e PC sulla **stessa Wi-Fi privata**.

L'app deve connettersi a `http://IP_DEL_PC:18766` con il codice mostrato dal PC. Il servizio usa HTTP LAN, non cifrato: non aprire la porta verso Internet e non usarlo su reti pubbliche.

Progetti, testo e take restano disponibili offline sul telefono. In caso di conflitto di revisione, la sincronizzazione non sovrascrive il lavoro del PC.
