# Muestra qué versión corre en cada ambiente.
foreach ($e in @(@{n="uat";p=8081}, @{n="prod";p=8082})) {
    try { $i = Invoke-RestMethod "http://localhost:$($e.p)/api/info"; Write-Host ("{0,-5} {1} v{2} ({3})" -f $e.n, $i.env, $i.version, $i.commit) }
    catch { Write-Host ("{0,-5} (no desplegado)" -f $e.n) }
}
