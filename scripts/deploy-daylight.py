#!/usr/bin/env python3
"""Explicit, manual deployment of allowlisted public Daylight assets to Amplify.

Uses the caller's AWS CLI login; does not store credentials or deploy backend data.
Run only when deployment to the named account has been authorized.
"""
import argparse
import io
import json
from pathlib import Path
import subprocess
import urllib.request
import zipfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--profile', required=True)
    parser.add_argument('--account', required=True)
    parser.add_argument('--region', default='ap-northeast-2')
    parser.add_argument('--static-preview', action='store_true',
                        help='Deploy the browser-local UI preview, not the API-connected application.')
    args = parser.parse_args()
    source = Path(__file__).resolve().parents[1] / 'daylight'
    if not args.static_preview and "'/api/deadlines'" in (source / 'team-calendar.js').read_text():
        raise SystemExit('Daylight now requires its same-origin API. Static-only Amplify deployment is disabled; use an authenticated WEB/API deployment.')
    base = ['aws', '--profile', args.profile, '--region', args.region, '--output', 'json', '--no-cli-pager']

    def aws(*command):
        return json.loads(subprocess.check_output(base + list(command), text=True))

    identity = aws('sts', 'get-caller-identity')
    if identity['Account'] != args.account:
        raise SystemExit('AWS account mismatch; nothing deployed.')
    archive = io.BytesIO()
    with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED) as bundle:
        if args.static_preview:
            preview = source / 'preview'
            html = (preview / 'index.html').read_text().replace('../styles.css', './styles.css').replace('../script.js', './script.js')
            bundle.writestr('index.html', html)
            for name in ('styles.css', 'script.js'):
                bundle.write(source / name, name)
            for name in ('team-calendar.js', 'calendar-views.js'):
                bundle.write(preview / name, name)
        else:
            for name in ('index.html', 'styles.css', 'script.js', 'team-calendar.js'):
                bundle.write(source / name, name)
    name = 'daylight-team-calendar'
    apps = [app for app in aws('amplify', 'list-apps')['apps'] if app['name'] == name]
    if len(apps) > 1:
        raise SystemExit('Multiple matching apps; resolve the target manually.')
    if apps:
        app = apps[0]
        if app.get('repository'):
            raise SystemExit('Existing app is Git-connected; refusing manual overwrite.')
    else:
        app = aws('amplify', 'create-app', '--name', name, '--platform', 'WEB',
                  '--description', 'Daylight static team calendar preview; browser-local data only')['app']
    app_id = app['appId']
    # Print only non-secret resource IDs. Presigned upload URLs never reach logs.
    print(json.dumps({'appId': app_id, 'region': args.region}), flush=True)
    branches = aws('amplify', 'list-branches', '--app-id', app_id)['branches']
    if not any(b['branchName'] == 'main' for b in branches):
        aws('amplify', 'create-branch', '--app-id', app_id, '--branch-name', 'main',
            '--stage', 'PRODUCTION', '--no-enable-auto-build')
    deployment = aws('amplify', 'create-deployment', '--app-id', app_id, '--branch-name', 'main')
    request = urllib.request.Request(deployment['zipUploadUrl'], data=archive.getvalue(),
                                     method='PUT', headers={'Content-Type': 'application/zip'})
    with urllib.request.urlopen(request, timeout=60) as response:
        if response.status not in (200, 201):
            raise SystemExit('Deployment upload failed.')
    result = aws('amplify', 'start-deployment', '--app-id', app_id, '--branch-name', 'main',
                 '--job-id', deployment['jobId'])
    print(json.dumps({'appId': app_id, 'jobId': deployment['jobId'],
                      'status': result['jobSummary']['status'],
                      'url': f"https://main.{app['defaultDomain']}/"}), flush=True)


if __name__ == '__main__':
    main()
