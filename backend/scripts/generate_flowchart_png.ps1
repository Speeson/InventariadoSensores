Add-Type -AssemblyName System.Drawing
$ErrorActionPreference = "Stop"

$outPath = "backend/context/DIAGRAMA_FLUJO_APP.png"

$width = 2200
$height = 1400
$bmp = New-Object System.Drawing.Bitmap($width, $height, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.Clear([System.Drawing.Color]::Transparent)

$fontNode = New-Object System.Drawing.Font("Segoe UI", 16, [System.Drawing.FontStyle]::Regular)
$fontTitle = New-Object System.Drawing.Font("Segoe UI", 24, [System.Drawing.FontStyle]::Bold)
$fontLabel = New-Object System.Drawing.Font("Segoe UI", 14, [System.Drawing.FontStyle]::Bold)

$brushNode = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(228, 20, 36, 68))
$brushDecision = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(228, 34, 55, 96))
$brushText = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 255, 255, 255))
$brushEdgeLabel = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 210, 232, 255))
$penNode = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(245, 124, 189, 255), 3)
$penEdge = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(245, 181, 221, 255), 3)

$sf = New-Object System.Drawing.StringFormat
$sf.Alignment = [System.Drawing.StringAlignment]::Center
$sf.LineAlignment = [System.Drawing.StringAlignment]::Center

function New-Node {
    param(
        [string]$id,
        [int]$x,
        [int]$y,
        [int]$w,
        [int]$h,
        [string]$text,
        [string]$type = "process"
    )
    return @{
        id = $id
        x = $x
        y = $y
        w = $w
        h = $h
        text = $text
        type = $type
    }
}

function Draw-RoundedRect {
    param($graphics, $pen, $brush, [int]$x, [int]$y, [int]$w, [int]$h, [int]$r)
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $path.AddArc($x, $y, $r, $r, 180, 90)
    $path.AddArc($x + $w - $r, $y, $r, $r, 270, 90)
    $path.AddArc($x + $w - $r, $y + $h - $r, $r, $r, 0, 90)
    $path.AddArc($x, $y + $h - $r, $r, $r, 90, 90)
    $path.CloseFigure()
    $graphics.FillPath($brush, $path)
    $graphics.DrawPath($pen, $path)
    $path.Dispose()
}

function Draw-Node {
    param($graphics, $node)
    $x = [int]($node.x - $node.w / 2)
    $y = [int]($node.y - $node.h / 2)

    if ($node.type -eq "decision") {
        $pts = [System.Drawing.PointF[]]@(
            ([System.Drawing.PointF]::new([float]$node.x, [float]$y)),
            ([System.Drawing.PointF]::new([float]($x + $node.w), [float]$node.y)),
            ([System.Drawing.PointF]::new([float]$node.x, [float]($y + $node.h))),
            ([System.Drawing.PointF]::new([float]$x, [float]$node.y))
        )
        $graphics.FillPolygon($script:brushDecision, $pts)
        $graphics.DrawPolygon($script:penNode, $pts)
    } else {
        Draw-RoundedRect $graphics $script:penNode $script:brushNode $x $y $node.w $node.h 24
    }

    $rect = New-Object System.Drawing.RectangleF([float]$x, [float]$y, [float]$node.w, [float]$node.h)
    $graphics.DrawString($node.text, $script:fontNode, $script:brushText, $rect, $script:sf)
}

function Get-Anchor {
    param($node, [string]$side)
    $x = [float]$node.x
    $y = [float]$node.y
    $hw = [float]($node.w / 2)
    $hh = [float]($node.h / 2)
    switch ($side) {
        "top"    { return [System.Drawing.PointF]::new([float]$x, [float]($y - $hh)) }
        "bottom" { return [System.Drawing.PointF]::new([float]$x, [float]($y + $hh)) }
        "left"   { return [System.Drawing.PointF]::new([float]($x - $hw), [float]$y) }
        "right"  { return [System.Drawing.PointF]::new([float]($x + $hw), [float]$y) }
        default  { return [System.Drawing.PointF]::new([float]$x, [float]$y) }
    }
}

function Resolve-Point {
    param($spec, $nodes)
    if ($spec.ContainsKey("node")) {
        return Get-Anchor $nodes[$spec.node] $spec.side
    }
    return [System.Drawing.PointF]::new([float]$spec.x, [float]$spec.y)
}

function Draw-Arrow {
    param($graphics, $pen, [System.Drawing.PointF[]]$points)

    if ($points.Length -lt 2) { return }
    $graphics.DrawLines($pen, $points)

    $p1 = $points[$points.Length - 2]
    $p2 = $points[$points.Length - 1]
    $ang = [Math]::Atan2(($p2.Y - $p1.Y), ($p2.X - $p1.X))
    $len = 14
    $a1 = $ang + [Math]::PI * 0.86
    $a2 = $ang - [Math]::PI * 0.86
    $h1 = [System.Drawing.PointF]::new([float]($p2.X + $len * [Math]::Cos($a1)), [float]($p2.Y + $len * [Math]::Sin($a1)))
    $h2 = [System.Drawing.PointF]::new([float]($p2.X + $len * [Math]::Cos($a2)), [float]($p2.Y + $len * [Math]::Sin($a2)))
    $tri = [System.Drawing.PointF[]]@($p2, $h1, $h2)
    $graphics.FillPolygon($pen.Brush, $tri)
}

function Draw-Edge {
    param($graphics, $nodes, $edge)

    $pts = [System.Collections.Generic.List[System.Drawing.PointF]]::new()
    foreach ($p in $edge.path) {
        $pts.Add((Resolve-Point $p $nodes))
    }
    Draw-Arrow $graphics $script:penEdge $pts.ToArray()

    if ($edge.ContainsKey("label")) {
        $labelPos = Resolve-Point $edge.labelPos $nodes
        $graphics.DrawString($edge.label, $script:fontLabel, $script:brushEdgeLabel, $labelPos)
    }
}

$nodes = @{}
$nodes["A"] = New-Node "A" 700 120 240 92 "Inicio app"
$nodes["B"] = New-Node "B" 700 260 280 120 "Sesion JWT`nvalida?" "decision"
$nodes["C"] = New-Node "C" 360 420 240 92 "Login / Registro"
$nodes["D"] = New-Node "D" 360 560 260 92 "Backend emite JWT"
$nodes["E"] = New-Node "E" 700 560 220 92 "Home"
$nodes["F"] = New-Node "F" 700 720 280 120 "Hay conexion?" "decision"
$nodes["H"] = New-Node "H" 360 900 250 96 "Modo offline"
$nodes["N"] = New-Node "N" 360 1040 270 96 "Room cache`nlecturas"
$nodes["P"] = New-Node "P" 360 1180 280 96 "Guardar`npendiente local"
$nodes["Q"] = New-Node "Q" 700 1180 320 96 "WorkManager`nreintento sync"
$nodes["G"] = New-Node "G" 1040 900 250 96 "Modo online"
$nodes["S"] = New-Node "S" 1040 1040 330 96 "Operaciones:`nescanear / CSV / etiqueta"
$nodes["I"] = New-Node "I" 1040 1180 320 96 "Llamadas API REST"
$nodes["J"] = New-Node "J" 1400 1080 250 90 "PostgreSQL"
$nodes["K"] = New-Node "K" 1400 1230 280 90 "Redis + Celery"
$nodes["AA"] = New-Node "AA" 1720 1230 300 92 "Procesa eventos`ny actualiza stock"
$nodes["AB"] = New-Node "AB" 1720 1080 280 90 "WS / FCM / email"
$nodes["L"] = New-Node "L" 1040 1320 320 90 "UI actualizada`n+ cache Room"
$nodes["AE"] = New-Node "AE" 1720 900 300 92 "Prometheus + Grafana"

$edges = @(
    @{ path = @(@{node="A";side="bottom"}, @{node="B";side="top"}) },
    @{ path = @(@{node="B";side="left"}, @{x=520;y=260}, @{x=520;y=420}, @{node="C";side="top"}); label="No"; labelPos=@{x=500;y=320} },
    @{ path = @(@{node="B";side="bottom"}, @{node="E";side="top"}); label="Si"; labelPos=@{x=730;y=400} },
    @{ path = @(@{node="C";side="bottom"}, @{node="D";side="top"}) },
    @{ path = @(@{node="D";side="right"}, @{x=520;y=560}, @{node="E";side="left"}) },
    @{ path = @(@{node="E";side="bottom"}, @{node="F";side="top"}) },

    @{ path = @(@{node="F";side="left"}, @{x=520;y=720}, @{x=520;y=900}, @{node="H";side="top"}); label="No"; labelPos=@{x=500;y=810} },
    @{ path = @(@{node="F";side="right"}, @{x=880;y=720}, @{x=880;y=900}, @{node="G";side="top"}); label="Si"; labelPos=@{x=900;y=810} },

    @{ path = @(@{node="H";side="bottom"}, @{node="N";side="top"}) },
    @{ path = @(@{node="N";side="bottom"}, @{node="P";side="top"}) },
    @{ path = @(@{node="P";side="right"}, @{x=540;y=1180}, @{node="Q";side="left"}) },
    @{ path = @(@{node="Q";side="top"}, @{x=700;y=1110}, @{x=880;y=1110}, @{node="I";side="left"}) },

    @{ path = @(@{node="G";side="bottom"}, @{node="S";side="top"}) },
    @{ path = @(@{node="S";side="bottom"}, @{node="I";side="top"}) },

    @{ path = @(@{node="I";side="right"}, @{x=1220;y=1180}, @{node="J";side="bottom"}) },
    @{ path = @(@{node="I";side="right"}, @{node="K";side="left"}) },
    @{ path = @(@{node="K";side="right"}, @{node="AA";side="left"}) },
    @{ path = @(@{node="AA";side="top"}, @{node="AB";side="bottom"}) },
    @{ path = @(@{node="I";side="right"}, @{x=1540;y=1180}, @{x=1540;y=900}, @{node="AE";side="left"}) },

    @{ path = @(@{node="J";side="left"}, @{x=1220;y=1080}, @{x=1220;y=1320}, @{node="L";side="right"}) },
    @{ path = @(@{node="AB";side="left"}, @{x=1540;y=1080}, @{x=1540;y=1320}, @{node="L";side="right"}) },
    @{ path = @(@{node="I";side="bottom"}, @{node="L";side="top"}) },
    @{ path = @(@{node="L";side="left"}, @{x=700;y=1320}, @{x=700;y=620}, @{node="E";side="bottom"}) }
)

# Title and lane hints
$g.DrawString("IoTrack - Diagrama de flujo", $fontTitle, $brushText, (New-Object System.Drawing.PointF(760, 26)))
$g.DrawString("Android", $fontLabel, $brushEdgeLabel, (New-Object System.Drawing.PointF(160, 40)))
$g.DrawString("Backend", $fontLabel, $brushEdgeLabel, (New-Object System.Drawing.PointF(1320, 40)))
$g.DrawString("Observabilidad", $fontLabel, $brushEdgeLabel, (New-Object System.Drawing.PointF(1780, 40)))

foreach ($n in $nodes.Values) {
    Draw-Node $g $n
}

foreach ($e in $edges) {
    Draw-Edge $g $nodes $e
}

$bmp.Save($outPath, [System.Drawing.Imaging.ImageFormat]::Png)

$fontNode.Dispose()
$fontTitle.Dispose()
$fontLabel.Dispose()
$brushNode.Dispose()
$brushDecision.Dispose()
$brushText.Dispose()
$brushEdgeLabel.Dispose()
$penNode.Dispose()
$penEdge.Dispose()
$g.Dispose()
$bmp.Dispose()

Write-Output $outPath
