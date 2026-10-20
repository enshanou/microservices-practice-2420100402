param([string]$BaseUrl = 'http://localhost:8080')
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Net.Http
$client = New-Object System.Net.Http.HttpClient
$client.Timeout = [TimeSpan]::FromSeconds(20)
$script:passed = 0
function Check-Api([string]$Method, [string]$Path, [int]$Expected, $Body = $null) {
    $request = New-Object System.Net.Http.HttpRequestMessage
    $request.Method = New-Object System.Net.Http.HttpMethod($Method)
    $request.RequestUri = [Uri]($BaseUrl.TrimEnd('/') + $Path)
    if ($null -ne $Body) {
        $request.Content = New-Object System.Net.Http.StringContent(($Body | ConvertTo-Json -Compress), [Text.Encoding]::UTF8, 'application/json')
    }
    $response = $client.SendAsync($request).GetAwaiter().GetResult()
    try {
        $text = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        if ([int]$response.StatusCode -ne $Expected) { throw "$Method $Path expected $Expected, received $([int]$response.StatusCode): $text" }
        $script:passed++
        Write-Host "PASS $Method $Path -> $Expected"
        if ($text.StartsWith('{') -or $text.StartsWith('[')) { return ($text | ConvertFrom-Json) }
        return $text
    } finally { $response.Dispose(); $request.Dispose() }
}
try {
    $null = Check-Api GET '/actuator/health' 200
    $null = Check-Api GET '/api/hello' 200
    $course = Check-Api POST '/api/courses' 201 @{ownerId='oes';title=('API verification ' + [Guid]::NewGuid().ToString('N').Substring(0,8));sourceReference='imports/demo.srt'}
    $null = Check-Api GET '/api/courses' 200
    $null = Check-Api GET "/api/courses/$($course.id)" 200
    $task = Check-Api POST "/api/courses/$($course.id)/tasks" 201
    $null = Check-Api GET "/api/tasks/$($task.id)" 200
    $null = Check-Api GET "/api/courses/$($course.id)/tasks" 200
    $null = Check-Api POST "/api/courses/$($course.id)/tasks" 409
    $null = Check-Api PATCH "/api/tasks/$($task.id)/status" 409 @{status='SUCCEEDED'}
    $null = Check-Api PATCH "/api/tasks/$($task.id)/status" 200 @{status='RUNNING'}
    $null = Check-Api PATCH "/api/tasks/$($task.id)/status" 400 @{status='FAILED';failureReason=' '}
    $null = Check-Api PATCH "/api/tasks/$($task.id)/status" 200 @{status='FAILED';failureReason='Demo: missing subtitle input'}
    $retry = Check-Api POST "/api/courses/$($course.id)/tasks" 201
    $null = Check-Api PATCH "/api/tasks/$($retry.id)/status" 200 @{status='RUNNING'}
    $null = Check-Api PATCH "/api/tasks/$($retry.id)/status" 200 @{status='SUCCEEDED'}
    $history = @(Check-Api GET "/api/courses/$($course.id)/tasks" 200)
    if ($history.Count -ne 2) { throw 'Expected both processing attempts to remain in history.' }
    $null = Check-Api POST '/api/courses' 400 @{ownerId='oes';title='';sourceReference='demo.srt'}
    $null = Check-Api GET '/api/courses/00000000-0000-0000-0000-000000000000' 404
    Write-Host "SUCCESS: $script:passed HTTP checks passed; task history contains 2 records."
    Write-Host "Verification course: $($course.id). Demo records are retained in MySQL."
} finally { $client.Dispose() }
