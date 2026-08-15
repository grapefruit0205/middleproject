$terraformRoot = Split-Path -Parent $PSScriptRoot

Describe 'Phase 11 Linux bootstrap behavior' {
    BeforeAll {
        $bash = 'C:\Program Files\Git\bin\bash.exe'
        if (-not (Test-Path $bash)) {
            throw 'Git Bash is required for the bootstrap behavior tests.'
        }

        $webBootstrap = Get-Content (Join-Path $terraformRoot 'templates/web.sh.tftpl') -Raw
        $wasBootstrap = Get-Content (Join-Path $terraformRoot 'templates/was.sh.tftpl') -Raw
    }

    It 'renders the Apache configuration under nounset without expanding the capture replacement as a shell argument' {
        $match = [regex]::Match(
            $webBootstrap,
            '(?ms)^cat >/etc/httpd/conf\.d/reminder\.conf <<EOF\r?\n.*?^EOF\r?$'
        )
        $match.Success | Should Be $true

        $outputPath = Join-Path $TestDrive 'reminder.conf'
        $outputPathForBash = $outputPath.Replace('\', '/')
        $fragment = $match.Value.Replace(
            'cat >/etc/httpd/conf.d/reminder.conf',
            "cat >'$outputPathForBash'"
        )
        $fragment = [regex]::Replace($fragment, '\$\{[^}]+\}', 'fixture.internal')

        $scriptPath = Join-Path $TestDrive 'render-apache.sh'
        [System.IO.File]::WriteAllText($scriptPath, $fragment, [System.Text.UTF8Encoding]::new($false))
        $scriptPathForBash = $scriptPath.Replace('\', '/')

        & $bash -u $scriptPathForBash 2>&1 | Out-Null
        $LASTEXITCODE | Should Be 0
        (Get-Content $outputPath -Raw) | Should Match 'alb_trace_root=\$1'
    }

    It 'inserts the Tomcat access valve without corrupting a multiline Host element' {
        $match = [regex]::Match(
            $wasBootstrap,
            '(?m)^sed -i .+ /opt/tomcat/conf/server\.xml\r?$'
        )
        $match.Success | Should Be $true

        $serverXmlPath = Join-Path $TestDrive 'server.xml'
        $serverXml = @'
<?xml version="1.0" encoding="UTF-8"?>
<Server>
  <Service>
    <Engine>
      <Host name="localhost" appBase="webapps"
            unpackWARs="true" autoDeploy="true">
        <Context path="" />
      </Host>
    </Engine>
  </Service>
</Server>
'@
        [System.IO.File]::WriteAllText($serverXmlPath, $serverXml, [System.Text.UTF8Encoding]::new($false))
        $serverXmlPathForBash = $serverXmlPath.Replace('\', '/')
        $sedCommand = $match.Value.Replace('/opt/tomcat/conf/server.xml', "'$serverXmlPathForBash'")
        $scriptPath = Join-Path $TestDrive 'insert-valve.sh'
        [System.IO.File]::WriteAllText($scriptPath, $sedCommand, [System.Text.UTF8Encoding]::new($false))
        $scriptPathForBash = $scriptPath.Replace('\', '/')

        & $bash $scriptPathForBash 2>&1 | Out-Null
        $LASTEXITCODE | Should Be 0
        { [xml](Get-Content $serverXmlPath -Raw) | Out-Null } | Should Not Throw
        $document = [xml](Get-Content $serverXmlPath -Raw)
        $document.Server.Service.Engine.Host.Valve.className | Should Be 'org.apache.catalina.valves.AccessLogValve'
    }

    It 'defers the database secret lookup until the Tomcat service starts' {
        $match = [regex]::Match(
            $wasBootstrap,
            '(?ms)^cat >/etc/systemd/system/tomcat\.service <<EOF\r?\n.*?^EOF\r?$'
        )
        $match.Success | Should Be $true

        $unitPath = Join-Path $TestDrive 'tomcat.service'
        $unitPathForBash = $unitPath.Replace('\', '/')
        $fragment = $match.Value.Replace(
            'cat >/etc/systemd/system/tomcat.service',
            "cat >'$unitPathForBash'"
        )
        $fragment = [regex]::Replace($fragment, '\$\{[^}]+\}', 'fixture.internal')

        $scriptPath = Join-Path $TestDrive 'render-tomcat-unit.sh'
        [System.IO.File]::WriteAllText($scriptPath, $fragment, [System.Text.UTF8Encoding]::new($false))
        $scriptPathForBash = $scriptPath.Replace('\', '/')

        & $bash -u -c "unset DB_SECRET_ARN; export INSTANCE_ID=fixture-instance; source '$scriptPathForBash'" 2>&1 | Out-Null
        $LASTEXITCODE | Should Be 0
        $unit = Get-Content $unitPath -Raw
        $unit | Should Match ([regex]::Escape('$(aws secretsmanager get-secret-value'))
        $unit | Should Match ([regex]::Escape('$DB_SECRET_ARN'))
    }
}
