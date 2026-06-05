# Builds a single shareable PDF "Design Pack" from the architecture doc + ADRs.
# Markdown -> HTML via marked.js; Mermaid diagrams via mermaid.js; HTML -> PDF via headless Chrome.
$ErrorActionPreference = 'Stop'
$root = 'C:\sandbox\competitive-analysis'
$outDir = Join-Path $root 'docs\dist'
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

$docs = @(
  'docs/architecture_v2_prudent.md',
  'docs/adr/README.md',
  'docs/adr/0001-llm-orchestrates-never-calculates.md',
  'docs/adr/0002-reconcile-dont-average.md',
  'docs/adr/0003-modular-monolith-java25-postgres.md',
  'docs/adr/0004-langchain4j-native-defer-mcp-adk.md'
)

$parts = New-Object System.Collections.Generic.List[string]
foreach ($rel in $docs) {
  $full = Join-Path $root ($rel -replace '/','\')
  $content = Get-Content -Raw -Encoding UTF8 $full
  $b64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($content))
  $parts.Add('"' + $b64 + '"')
}
$arr = '[' + ($parts -join ',') + ']'

$template = @'
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<title>Design Pack</title>
<script src="https://cdn.jsdelivr.net/npm/marked@12/marked.min.js"></script>
<script src="https://cdn.jsdelivr.net/npm/mermaid@11/dist/mermaid.min.js"></script>
<style>
  :root { --ink:#1a2230; --muted:#5b6676; --accent:#5b3fd6; --line:#e2e6ee; }
  * { box-sizing: border-box; }
  body { font-family: -apple-system, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; color: var(--ink); line-height: 1.55; margin: 0; }
  .page { padding: 0 6mm; }
  .cover { height: 250mm; display:flex; flex-direction:column; justify-content:center; page-break-after: always; padding: 0 12mm; }
  .cover h1 { font-size: 30pt; margin: 0 0 8px; }
  .cover .sub { font-size: 13pt; color: var(--muted); }
  .cover .meta { margin-top: 28px; font-size: 10.5pt; color: var(--muted); }
  .cover .rule { height:4px; width:80px; background:var(--accent); margin:18px 0; border-radius:2px; }
  section.doc { page-break-before: always; }
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
  <div class="cover">
    <div class="rule"></div>
    <h1>Real Estate CxO Decision-Support Agent</h1>
    <div class="sub">Architecture, Design &amp; Decision Records &mdash; Design Pack</div>
    <div class="meta">
      Antigravity &middot; Prudent Architecture v2.2<br>
      Generated __DATE__ &middot; Confidential &mdash; internal team distribution
    </div>
  </div>
  <div id="content" class="page"></div>
<script>
  const DOCS = __DATA__;
  function decodeB64Utf8(b64){
    const bin = atob(b64);
    const bytes = Uint8Array.from(bin, function(c){ return c.charCodeAt(0); });
    return new TextDecoder('utf-8').decode(bytes);
  }
  const content = document.getElementById('content');
  DOCS.forEach(function(b64){
    const md = decodeB64Utf8(b64);
    const sec = document.createElement('section');
    sec.className = 'doc';
    sec.innerHTML = marked.parse(md);
    sec.querySelectorAll('pre > code.language-mermaid').forEach(function(el){
      const div = document.createElement('div');
      div.className = 'mermaid';
      div.textContent = el.textContent;
      el.parentElement.replaceWith(div);
    });
    content.appendChild(sec);
  });
  mermaid.initialize({ startOnLoad:false, theme:'neutral', securityLevel:'loose', flowchart:{useMaxWidth:true} });
  mermaid.run({ querySelector: '.mermaid' })
    .then(function(){ document.title = 'READY'; })
    .catch(function(){ document.title = 'READY'; });
</script>
</body>
</html>
'@

$html = $template.Replace('__DATA__', $arr).Replace('__DATE__', 'June 2026')
$htmlPath = Join-Path $outDir 'comp-analysis-design-pack.html'
[IO.File]::WriteAllText($htmlPath, $html, [Text.UTF8Encoding]::new($false))
Write-Output ("HTML written: " + $htmlPath + " (" + $html.Length + " chars)")

$chrome = 'C:\Program Files\Google\Chrome\Application\chrome.exe'
$pdfPath = Join-Path $outDir 'comp-analysis-design-pack.pdf'
if (Test-Path $pdfPath) { Remove-Item $pdfPath -Force }
$fileUrl = 'file:///' + ($htmlPath -replace '\\','/')
$profile = Join-Path $env:TEMP 'chrome-pdf-profile'

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
if (-not $p.WaitForExit(120000)) { try { $p.Kill() } catch {}; Write-Output 'Chrome timed out.' }

if (Test-Path $pdfPath) {
  $kb = [math]::Round((Get-Item $pdfPath).Length / 1KB, 1)
  Write-Output ("PDF OK: " + $pdfPath + " (" + $kb + " KB)")
} else {
  Write-Output "PDF NOT produced with --headless=new; retrying with legacy --headless..."
  $chromeArgs[0] = '--headless'
  $p2 = Start-Process -FilePath $chrome -ArgumentList $chromeArgs -PassThru -WindowStyle Hidden
  if (-not $p2.WaitForExit(120000)) { try { $p2.Kill() } catch {} }
  if (Test-Path $pdfPath) {
    $kb = [math]::Round((Get-Item $pdfPath).Length / 1KB, 1)
    Write-Output ("PDF OK (legacy): " + $pdfPath + " (" + $kb + " KB)")
  } else {
    Write-Output "PDF still NOT produced."
  }
}
