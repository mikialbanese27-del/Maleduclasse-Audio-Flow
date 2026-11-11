# Maleduclasse Audio Flow — Android 1.3.2 / PC Mobile First v0.4

L'app Android è il punto di partenza per creare **progetti, copioni, cast e take**. Il PC importa queste informazioni, assembla la timeline e gestisce suoni e mix.

## Funzioni Android

- Progetti: nuovo, apri, rinomina, duplica, archivia, elimina localmente.
- Sceneggiatura: editor con sintassi `@Personaggio`, `/scena`, tag, controllo battute, importazione DOCX e dettatura.
- Cast: doppiatori, personaggi, assegnazioni globali e per episodio.
- Doppiaggio: filtro doppiatore, registrazione offline, più take, import audio, ascolto, preferita/provvisoria/eliminazione.
- Ascolta il contesto: due pulsanti ai lati del comando centrale riproducono le battute immediatamente precedente e successiva nell'ordine del copione completo, anche di altri personaggi. Riproducono la take preferita (oppure l'ultima disponibile), restano disabilitati senza audio e non interrompono la registrazione.
- Sincronizzazione bidirezionale LAN: importa da PC oppure crea su telefono e invia al PC con ID stabili. Il PC crea la bozza dopo le take.
- Protezione da conflitti sulle revisioni; le take precedenti sono archiviate quando il testo cambia.

## APK

Android 1.3.2 (versionCode 16), compatibilità Android 8.0+.

GitHub Actions → **Build Android APK** produce un pacchetto **debug** di prova e un pacchetto **release unsigned**. Nessuno dei due artefatti CI deve essere considerato la versione definitiva: la release Android viene firmata separatamente con una chiave privata conservata fuori dal repository. La chiave di firma **non va caricata su GitHub**.

La release firmata usa lo stesso application ID `it.maleduclasse.audioflow.deck131`; il passaggio da debug a release può richiedere la disinstallazione della build precedente a causa delle diverse firme. Prima di farlo, sincronizzare e salvare progetti e take: la disinstallazione cancella i dati locali dell'app.

Una release firmata localmente non è stata collaudata fisicamente su smartphone né verificata end-to-end con uno specifico pacchetto Windows.

## Windows

Usare il pacchetto Windows **v0.4 Mobile First**, con `mobile_first_api.py`. Avviare `AVVIA_PC_ANDROID.bat`. Smartphone e PC sulla **stessa Wi-Fi privata**.

L'app deve connettersi a `http://IP_DEL_PC:18766` con il codice mostrato dal PC. Il servizio usa HTTP LAN, non cifrato: non aprire la porta verso Internet e non usarlo su reti pubbliche.

Progetti, testo e take restano disponibili offline sul telefono. In caso di conflitto di revisione, la sincronizzazione non sovrascrive il lavoro del PC.
