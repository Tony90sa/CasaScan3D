# Ricostruzione su PC

Installa COLMAP da https://colmap.github.io/ e Python 3.10+. Su Windows potrebbe essere necessario indicare il percorso dell'eseguibile con `--colmap`.

```powershell
python reconstruct.py "C:\Users\Nome\Downloads\CasaScan.zip" --output "C:\CasaScanOutput"
```

Output: `sparse.ply`, nuvola di punti sparsa, non mesh. La scala non è metrica.
