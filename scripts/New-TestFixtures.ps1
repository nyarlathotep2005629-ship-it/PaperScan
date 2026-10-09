param(
    [string]$OutputDirectory = (Join-Path $PSScriptRoot '..\app\src\androidTest\assets\fixtures')
)

Add-Type -AssemblyName System.Drawing
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null

function New-Fixture {
    param(
        [string]$Name,
        [System.Drawing.Color]$Background,
        [bool]$HasPaper = $true,
        [bool]$DarkPaper = $false,
        [bool]$ThinLines = $false,
        [bool]$ColoredMarks = $false,
        [bool]$Shadow = $false
    )
    $bitmap = [System.Drawing.Bitmap]::new(1000, 800)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    try {
        $graphics.Clear($Background)
        if ($HasPaper) {
            $points = [System.Drawing.Point[]]@(
                [System.Drawing.Point]::new(145, 105), [System.Drawing.Point]::new(855, 78),
                [System.Drawing.Point]::new(802, 704), [System.Drawing.Point]::new(182, 670)
            )
            if ($Shadow) {
                $shadowBrush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(70, 0, 0, 0))
                $shadowPoints = $points | ForEach-Object { [System.Drawing.Point]::new($_.X + 20, $_.Y + 25) }
                $graphics.FillPolygon($shadowBrush, [System.Drawing.Point[]]$shadowPoints)
                $shadowBrush.Dispose()
            }
            $paperColour = if ($DarkPaper) { [System.Drawing.Color]::FromArgb(215, 210, 193) } else { [System.Drawing.Color]::FromArgb(248, 246, 237) }
            $paper = [System.Drawing.SolidBrush]::new($paperColour)
            $graphics.FillPolygon($paper, $points)
            $paper.Dispose()
            $lineWidth = if ($ThinLines) { 2.0 } else { 6.0 }
            $ink = [System.Drawing.Pen]::new([System.Drawing.Color]::FromArgb(40, 40, 40), $lineWidth)
            for ($i = 0; $i -lt 10; $i++) { $graphics.DrawLine($ink, 250, 190 + $i * 42, 735, 180 + $i * 42) }
            $graphics.DrawRectangle($ink, 300, 260, 240, 150)
            $graphics.DrawEllipse($ink, 580, 260, 90, 90)
            $ink.Dispose()
            if ($ColoredMarks) {
                $red = [System.Drawing.Pen]::new([System.Drawing.Color]::Crimson, 8)
                $blue = [System.Drawing.Pen]::new([System.Drawing.Color]::RoyalBlue, 8)
                $graphics.DrawLine($red, 280, 570, 670, 545)
                $graphics.DrawLine($blue, 280, 600, 670, 575)
                $red.Dispose(); $blue.Dispose()
            }
        }
        $font = [System.Drawing.Font]::new('Arial', 28, [System.Drawing.FontStyle]::Bold)
        $brush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(80, 80, 80))
        $graphics.DrawString($Name, $font, $brush, 28, 24)
        $font.Dispose(); $brush.Dispose()
        $bitmap.Save((Join-Path $OutputDirectory "$Name.png"), [System.Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $graphics.Dispose(); $bitmap.Dispose()
    }
}

New-Fixture 'overhead_a4' ([System.Drawing.Color]::FromArgb(113, 84, 58))
New-Fixture 'perspective_a4' ([System.Drawing.Color]::FromArgb(99, 74, 52))
New-Fixture 'white_table' ([System.Drawing.Color]::FromArgb(220, 220, 214))
New-Fixture 'wood_table' ([System.Drawing.Color]::FromArgb(128, 88, 52))
New-Fixture 'dark_document' ([System.Drawing.Color]::FromArgb(35, 35, 35)) -DarkPaper $true
New-Fixture 'shadow' ([System.Drawing.Color]::FromArgb(96, 76, 58)) -Shadow $true
New-Fixture 'image_edge' ([System.Drawing.Color]::FromArgb(108, 82, 60))
New-Fixture 'colored_document' ([System.Drawing.Color]::FromArgb(104, 82, 60)) -ColoredMarks $true
New-Fixture 'circuit_lines' ([System.Drawing.Color]::FromArgb(92, 75, 62)) -ThinLines $true
New-Fixture 'math_thin_lines' ([System.Drawing.Color]::FromArgb(88, 76, 64)) -ThinLines $true
New-Fixture 'no_paper' ([System.Drawing.Color]::FromArgb(90, 110, 100)) -HasPaper:$false
