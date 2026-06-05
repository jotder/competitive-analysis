# Builds ONE PDF per document (architecture doc + each ADR) into docs/dist/.
# Markdown -> HTML via marked.js; Mermaid via mermaid.js; HTML -> PDF via headless Chrome.
$ErrorActionPreference = 'Stop'
$root = 'C:\sandbox\competitive-analysis'
$outDir = Join-Path $root 'docs\dist'
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

# src markdown (relative)  ->  output pdf base name
$docs = @(
  @{ src = 'docs/architecture_v2_prudent.md';                       out = 'architecture_v2_prudent' },
  @{ src = 'docs/adr/README.md';                                    out = 'adr-index' },
  @{ src = 'docs/adr/0001-llm-orchestrates-never-calculates.md';    out = 'adr-0001-llm-orchestrates-never-calculates' },
  @{ src = 'docs/adr/0002-reconcile-dont-average.md';               out = 'adr-0002-reconcile-dont-average' },
  @{ src = 'docs/adr/0003-modular-monolith-java25-postgres.md';     out = 'adr-0003-modular-monolith-java25-postgres' },
  @{ src = 'docs/adr/0004-langchain4j-native-defer-mcp-adk.md';     out = 'adr-0004-langchain4j-native-defer-mcp-adk' }
)

$template = @'
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<title>__TITLE__</title>
<script src="https://cdn.jsdelivr.net/npm/marked@12/marked.min.js"></script>
<script src="https://cdn.jsdelivr.net/npm/mermaid@11/dist/mermaid.min.js"></script>
<style>
  :root { --ink:#1a2230; --muted:#5b6676; --accent:#5b3fd6; --line:#e2e6ee; }
  * { box-sizing: border-box; }
  body { font-family: -apple-system, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; color: var(--ink); line-height: 1.55; margin: 0; }
  #content { padding: 0 4mm; }
  h1 { font-size: 19pt; border-bottom: 2px solid var(--accent); padding-bottom: 4px; margin-top: 0; }
  h2 { font-size: 14pt; margin-top: 22px; border-bottom:1px solid var(--line); padding-bottom:3px; }
  h3 { font-size: 12pt; margin-top: 16px; }
  h4 { font-size: 11pt; color: var(--muted); }
  p, li { font-size: 10.5pt; }
  code { font-family: "Cascadia Code", Consolas, monospace; background:#f4f5f8; padding:1px 4px; border-radius:3px; font-size: 9.5pt; }
  pre { background:#f4f5f8; padding:10px 12px; border-radius:6px; overflow:auto; page-break-inside: avoid; }
  pre code { background:none; padding:0; font-size: 9pt; white-space: pre-wrap; word-break: break-word; }
  table { border-collapse: collapse; width: 100%; margin: 12px 0; font-size: 9.3pt; page-break-inside: avoid; }
  th, td { border: 1px solid var(--line); padding: 6px 8px; text-align: left; vertical-align: top; }
  th { background: #f0f1f6; }
  blockquote { border-left: 4px solid var(--accent); margin: 12px 0; padding: 4px 14px; background:#faf9ff; }
  .mermaid { page-break-inside: avoid; margin: 14px 0; text-align:center; }
  .mermaid svg { max-width: 100%; height: auto; }
  a { color: var(--accent); text-decoration: none; }
  hr { border:none; border-top:1px solid var(--line); margin: 20px 0; }
  @page { size: A4; margin: 14mm 10mm; }
</style>
</head>
<body>
  <div id="content"></div>
<script>
  const B64 = "__DOC__";
  function decodeB64Utf8(b64){
    const bin = atob(b64);
    const bytes = Uint8Array.from(bin, function(c){ return c.charCodeAt(0); });
    return new TextDecoder('utf-8').decode(bytes);
  }
  const content = document.getElementById('content');
  content.innerHTML = marked.parse(decodeB64Utf8(B64));
  content.querySelectorAll('pre > code.language-mermaid').forEach(function(el){
    const div = document.createElement('div');
    div.className = 'mermaid';
    div.textContent = el.textContent;
    el.parentElement.replaceWith(div);
  });
  mermaid.initialize({ startOnLoad:false, theme:'neutral', securityLevel:'loose', flowchart:{useMaxWidth:true} });
  mermaid.run({ querySelector: '.mermaid' })
    .then(function(){ document.title = 'READY'; })
    .catch(function(){ document.title = 'READY'; });
</script>
</body>
</html>
'@

$chrome = 'C:\Program Files\Google\Chrome\Application\chrome.exe'
$profile = Join-Path $env:TEMP 'chrome-pdf-profile'

function Convert-DocToPdf($htmlPath, $pdfPath) {
  if (Test-Path $pdfPath) { Remove-Item $pdfPath -Force }
  $fileUrl = 'file:///' + ($htmlPath -replace '\\','/')
  $chromeArgs = @(
    '--headless=new','--disable-gpu','--no-sandbox',
    ('--user-data-dir=' + $profile),
    '--no-pdf-header-footer',
    '--virtual-time-budget=30000',
    '--run-all-compositor-stages-before-draw',
    ('--print-to-pdf=' + $pdfPath),
    $fileUrl
  )
  $p = Start-Process -FilePath $chrome -ArgumentList $chromeArgs -PassThru -WindowStyle Hidden
  if (-not $p.WaitForExit(120000)) { try { $p.Kill() } catch {} }
}

$results = New-Object System.Collections.Generic.List[string]
foreach ($d in $docs) {
  $full = Join-Path $root ($d.src -replace '/','\')
  $content = Get-Content -Raw -Encoding UTF8 $full
  $b64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($content))

  $html = $template.Replace('__DOC__', $b64).Replace('__TITLE__', $d.out)
  $htmlPath = Join-Path $outDir ($d.out + '.html')
  [IO.File]::WriteAllText($htmlPath, $html, [Text.UTF8Encoding]::new($false))

  $pdfPath = Join-Path $outDir ($d.out + '.pdf')
  Convert-DocToPdf $htmlPath $pdfPath
  Remove-Item $htmlPath -Force   # keep only the PDFs

  if (Test-Path $pdfPath) {
    $kb = [math]::Round((Get-Item $pdfPath).Length / 1KB, 1)
    $results.Add(("OK   " + $d.out + ".pdf  (" + $kb + " KB)"))
  } else {
    $results.Add(("FAIL " + $d.out + ".pdf"))
  }
}

# Remove the old combined artifacts so the folder holds only per-document PDFs.
foreach ($stale in 'comp-analysis-design-pack.pdf','comp-analysis-design-pack.html') {
  $sp = Join-Path $outDir $stale
  if (Test-Path $sp) { Remove-Item $sp -Force }
}

Write-Output "=== Per-document PDFs (docs/dist) ==="
$results | ForEach-Object { Write-Output $_ }
