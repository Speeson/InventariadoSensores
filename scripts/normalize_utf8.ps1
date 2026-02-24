param(
    [string]$Root = "."
)

$extensions = @(
    ".kt", ".kts", ".java", ".xml", ".gradle", ".properties", ".yml", ".yaml", ".md", ".py", ".sql", ".sh"
)

$utf8NoBom = New-Object System.Text.UTF8Encoding($false)

Get-ChildItem -Path $Root -Recurse -File |
    Where-Object {
        $ext = $_.Extension.ToLowerInvariant()
        $extensions -contains $ext -and $_.FullName -notmatch "\\.git\\|\\build\\|\\.gradle\\"
    } |
    ForEach-Object {
        try {
            $content = [System.IO.File]::ReadAllText($_.FullName)
            [System.IO.File]::WriteAllText($_.FullName, $content, $utf8NoBom)
        } catch {
            Write-Warning "No se pudo normalizar: $($_.FullName)"
        }
    }

Write-Output "Normalizacion completada (UTF-8 sin BOM)."
