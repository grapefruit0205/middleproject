# Phase 04 Result: AWS Network and Terraform Baseline

## Outcome

Implemented the Phase 4 AWS network baseline from the empty baseline. The configuration defines the two-AZ `ap-northeast-2` VPC, public/WEB/WAS/DB subnet tiers, IGW and route tables, environment-specific NAT profiles, the least-privilege ALB-to-application security-group chain, and the SSM instance profile. The partial S3 backend remains `backend "s3" {}`; the example init settings are fake and non-secret.

Regional HA uses the AWS provider-supported `aws_nat_gateway` form with `availability_mode = "regional"` and only `vpc_id`. The provider v6.59.0 schema evidence shows no `subnet_id`, EIP/allocation, or availability-zone-address argument for Regional automatic placement. Development retains one conventional zonal NAT in `public_a`; local creates no NAT. DB route tables have no default route.

## Implementation files

The implementation files present after the empty baseline are:

- `infra/terraform/main.tf`
- `infra/terraform/variables.tf`
- `infra/terraform/versions.tf`
- `infra/terraform/outputs.tf`
- `infra/terraform/.terraform.lock.hcl`
- `infra/terraform/.gitignore`
- `infra/terraform/backend.hcl.example`
- `infra/terraform/probe-invalid-vpc.tfvars`
- `infra/terraform/probe-duplicate-az.tfvars`
- `infra/terraform/probe-non-seoul-az.tfvars`
- `infra/terraform/probe-valid-alternate.tfvars`
- `docs/phases/phase-04/result.md`

`.gitignore` continues to ignore `backend.hcl` and does not ignore `backend.hcl.example`. The tracked `.terraform.lock.hcl` and existing `.terraform/` directory were retained; `.terraform/` was not manually removed.

## Verification commands and actual results

| Command | Exit code | Summary |
|---|---:|---|
| `terraform fmt -recursive` | 1 | Failed because the installed Terraform 1.15.8 does not support the `-recursive` option. |
| `terraform fmt` | 0 | PASS; Terraform files were formatted. |
| `terraform init -backend=false -input=false` | 0 | PASS; initialized/reused the AWS provider without contacting the configured backend. |
| `terraform validate` before NAT correction | 1 | Failed because the initial Regional NAT arguments were invalid for AWS provider v6.59.0. |
| `terraform validate` after final correction | 0 | PASS. |
| `terraform providers schema -json` in the configured working directory | 1 | Failed because the partial S3 backend required reinitialization. |
| `terraform init -backend=false -input=false -reconfigure` in the temporary backendless schema copy | 0 | PASS; initialized the AWS provider for schema inspection. |
| `terraform providers schema -json` in the temporary backendless schema copy | 0 | PASS; provided the v6.59.0 schema evidence used for the final Regional NAT correction. |
| Read-only Python static security scan | 1 | Failed only on the intended Public ALB HTTPS/443 ingress from `0.0.0.0/0`; no unintended public SSH/22 ingress, DB default route, credentials, or secrets were found. |
| `where.exe tfsec` | 1 | Unavailable on the execution environment. |
| `where.exe trivy` | 1 | Unavailable on the execution environment. |
| `where.exe checkov` | 1 | Unavailable on the execution environment. |
| `terraform apply` | not run | Explicitly not run, as required. |

## Provider schema evidence

For AWS provider v6.59.0, `aws_nat_gateway` supports `availability_mode = "regional"`; Regional automatic placement uses `vpc_id` only. The schema does not provide `subnet_id`, EIP/allocation, or availability-zone-address arguments for this Regional form. The earlier validation failure also rejected the attempted `connectivity_type = "regional"` value, allowing only `private` or `public`.

## Review findings addressed

- All eight subnet CIDRs are now derived from `var.vpc_cidr` with `cidrsubnet(var.vpc_cidr, 8, ...)`; `vpc_cidr` is validated as a valid IPv4 `/16`, the supported prefix for this layout.
- `availability_zones` validation now requires exactly two distinct values matching `ap-northeast-2` AZ naming.
- Deterministic probe inputs are retained in `infra/terraform/probe-invalid-vpc.tfvars`, `infra/terraform/probe-duplicate-az.tfvars`, `infra/terraform/probe-non-seoul-az.tfvars`, and `infra/terraform/probe-valid-alternate.tfvars`.

## Verification commands and actual results (this revision)

| Command | Exit code | Summary |
|---|---:|---|
| `terraform fmt` | 0 | PASS; formatted the Terraform configuration and probe variable files. |
| `terraform fmt -check` | 0 | PASS. |
| `terraform init -backend=false -input=false -reconfigure` | 0 | PASS; AWS provider v6.59.0 initialized in the working directory without contacting the configured backend. |
| `terraform validate -no-color` | 0 | PASS. |
| `terraform plan -refresh=false -lock=false -input=false -var-file probe-invalid-vpc.tfvars -no-color` in an isolated backendless scratch copy | 1 | PASS for rejection probe; rejected the unsupported `/24` `vpc_cidr` with the variable validation message. |
| `terraform plan -refresh=false -lock=false -input=false -var-file probe-duplicate-az.tfvars -no-color` in an isolated backendless scratch copy | 1 | PASS for rejection probe; rejected duplicate `ap-northeast-2a` entries with the AZ validation message. |
| `terraform plan -refresh=false -lock=false -input=false -var-file probe-non-seoul-az.tfvars -no-color` in an isolated backendless scratch copy | 1 | PASS for rejection probe; rejected `us-east-1a` with the Seoul-region AZ validation message. |
| `terraform plan -refresh=false -lock=false -input=false -var-file probe-valid-alternate.tfvars -no-color` in an isolated backendless scratch copy | 0 | PASS; accepted `10.30.0.0/16` and planned derived `10.30.0.0/24` through `10.30.31.0/24` subnet ranges. |
| `terraform apply` | not run | Explicitly not run, as required. |

The deterministic validation probes now prove all reviewed invalid inputs are rejected, and the alternate `/16` probe proves subnet derivation follows `var.vpc_cidr`. No `terraform apply` ran, no Terraform state or configured backend was written, and no AWS resource mutation was attempted. Provider initialization or planning may still perform read-only AWS credential or account validation calls.

## Handoff and scope

No protected architecture documents, ADRs, briefs, or prompts were changed. No credentials, secrets, state files, or live AWS resources were created. No commit, push, merge, rebase, reset, or history rewrite was performed.
