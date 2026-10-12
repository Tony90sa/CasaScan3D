# CasaScan 3D — Android + COLMAP

Prototipo per acquisire fotografie sovrapposte con Android e ricostruire una nuvola di punti 3D sparsa sul PC, senza ARCore.

## Android
Apri la cartella `android-app` in Android Studio, sincronizza Gradle e compila. Avvia l'app, premi **Nuova scansione**, acquisisci foto e usa **Esporta ZIP per PC**.

## PC
Installa Python 3.10+ e COLMAP. Poi:

```bash
python pc-reconstruction/reconstruct.py scansione.zip --output output
```

Apri `output/sparse.ply` in MeshLab o CloudCompare.

## Limiti
Questa versione genera una nuvola di punti sparsa, non una mesh texturizzata. Non produce misure metriche certificate. Servono foto nitide con sovrapposizione 70–80%, luce uniforme e collegamenti tra stanze. Non cambiare zoom o obiettivo durante la sessione.

## IA e rimozione oggetti
Funzione proposta per una versione successiva: maschere degli oggetti da escludere dal calcolo 3D e inpainting facoltativo delle foto. Non confondere l'inpainting con il recupero della geometria nascosta.

## Privacy
Le foto restano sul telefono fino all'esportazione volontaria dello ZIP; la ricostruzione è locale sul PC.
