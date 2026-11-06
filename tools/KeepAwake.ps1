Add-Type -TypeDefinition 'using System; using System.Runtime.InteropServices; public static class EdgeFlowAwake { [DllImport("kernel32.dll")] public static extern uint SetThreadExecutionState(uint flags); }'
$expires = (Get-Date).AddHours(6)
try {
 [EdgeFlowAwake]::SetThreadExecutionState([uint32]2147483651) | Out-Null
 while ((Get-Date) -lt $expires) { Start-Sleep -Seconds 30 }
} finally { [EdgeFlowAwake]::SetThreadExecutionState([uint32]2147483648) | Out-Null }
