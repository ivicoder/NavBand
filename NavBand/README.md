# NavBand

NavBand V1

Applicazione Android per intercettare le notifiche di
Google Maps e Waze e preparare informazioni di navigazione
per Xiaomi Smart Band 8.

## Funzioni

- Google Maps
- Waze
- NotificationListenerService
- direzione
- distanza
- rotatorie
- riconoscimento uscita
- vibrazione rotatorie configurabile
- Mi Fitness
- Notify for Xiaomi
- immagini quando disponibili
- interfaccia Jetpack Compose

## Compilazione

Il progetto viene compilato tramite GitHub Actions.

Aprire:

Actions -> Build NavBand APK

e scaricare l'artifact:

NavBand-debug

## Nota

La trasmissione alla Smart Band viene effettuata attraverso
il sistema di notifiche dell'app ponte selezionata.

La gestione specifica dei pattern di vibrazione di Notify
verrà perfezionata dopo il primo test reale sulla Band 8.
