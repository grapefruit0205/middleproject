variable "aws_region" {
  description = "AWS region for the Seoul deployment."
  type        = string
  default     = "ap-northeast-2"

  validation {
    condition     = var.aws_region == "ap-northeast-2"
    error_message = "This baseline is intentionally scoped to ap-northeast-2."
  }
}

variable "environment" {
  description = "AWS application deployment profile: development (single-zonal NAT) or ha (regional NAT)."
  type        = string
  default     = "development"

  validation {
    condition     = contains(["development", "ha"], var.environment)
    error_message = "environment must be development or ha when application tiers are present; local has no supported tier bootstrap path."
  }
}

variable "name" {
  description = "Short application name used in resource names and tags."
  type        = string
  default     = "reminder-platform"
}

variable "vpc_cidr" {
  description = "CIDR range for the two-AZ VPC."
  type        = string
  default     = "10.20.0.0/16"

  validation {
    condition     = can(cidrnetmask(var.vpc_cidr)) && cidrnetmask(var.vpc_cidr) == "255.255.0.0"
    error_message = "vpc_cidr must be a valid IPv4 network with the supported /16 prefix."
  }
}

variable "availability_zones" {
  description = "Exactly two AZs for the baseline."
  type        = list(string)
  default     = ["ap-northeast-2a", "ap-northeast-2c"]

  validation {
    condition = (
      length(var.availability_zones) == 2 &&
      length(distinct(var.availability_zones)) == 2 &&
      alltrue([for az in var.availability_zones : can(regex("^ap-northeast-2[a-z]$", az))])
    )
    error_message = "availability_zones must contain exactly two distinct ap-northeast-2 AZ names."
  }
}

variable "cost_center" {
  description = "Cost allocation tag value."
  type        = string
  default     = "platform"
}

variable "owner" {
  description = "Owning team tag value."
  type        = string
  default     = "application-team"
}

variable "certificate_arn" {
  description = "ACM certificate ARN for the public ALB HTTPS listener."
  type        = string
  sensitive   = true
}

variable "artifact_bucket_name" {
  description = "Private S3 bucket containing the frontend archive and ROOT.war."
  type        = string
}

variable "artifact_force_destroy" {
  description = "Allow Terraform to delete non-empty artifact buckets during approved ephemeral teardown."
  type        = bool
  default     = false
}

variable "alb_access_log_force_destroy" {
  description = "Allow Terraform to delete non-empty ALB access-log buckets during approved ephemeral teardown."
  type        = bool
  default     = false
}

variable "frontend_artifact_path" {
  description = "Local path to the frontend archive published by Terraform."
  type        = string
}

variable "backend_artifact_path" {
  description = "Local path to the backend ROOT.war published by Terraform."
  type        = string
}

variable "frontend_artifact_key" {
  description = "S3 key for the built frontend archive."
  type        = string
  default     = "frontend/frontend.zip"
}

variable "backend_artifact_key" {
  description = "S3 key for ROOT.war."
  type        = string
  default     = "backend/ROOT.war"
}

variable "instance_type" {
  type    = string
  default = "t3.small"
}

variable "db_instance_class" {
  type    = string
  default = "db.t4g.micro"
}

variable "db_allocated_storage" {
  type    = number
  default = 20
}

variable "db_name" {
  type    = string
  default = "reminder"
}

variable "db_username" {
  type    = string
  default = "reminder_app"
}

variable "postgres_engine_version" {
  type    = string
  default = "16.14"
}

variable "skip_final_snapshot" {
  type    = bool
  default = false
}

variable "deletion_protection" {
  type    = bool
  default = true
}

variable "tomcat_version" {
  type    = string
  default = "10.1.57"
}

variable "scheduler_group" {
  type    = string
  default = "reminder-platform"

  validation {
    condition     = trimspace(var.scheduler_group) != "" && var.scheduler_group != "default"
    error_message = "scheduler_group must be a project-scoped non-default EventBridge Scheduler group."
  }
}

variable "scheduler_aws_enabled" {
  type    = bool
  default = true
}

variable "delivery_sqs_enabled" {
  type    = bool
  default = true
}

variable "notification_email_enabled" {
  type    = bool
  default = false

  validation {
    condition     = !var.notification_email_enabled || (trimspace(var.notification_email_from) != "" && trimspace(var.notification_email_to) != "" && trimspace(var.notification_email_identity_arn) != "")
    error_message = "Enabled email delivery requires nonblank from, to, and identity ARN settings."
  }
}

variable "notification_email_from" {
  type    = string
  default = ""
}

variable "notification_email_to" {
  type    = string
  default = ""
}

variable "notification_email_identity_arn" {
  type    = string
  default = ""

  validation {
    condition     = var.notification_email_identity_arn == "" || can(regex("^arn:aws:ses:ap-northeast-2:[0-9]{12}:identity/.+$", var.notification_email_identity_arn))
    error_message = "notification_email_identity_arn must be a Seoul SES identity ARN."
  }
}

variable "scheduler_timezone" {
  type    = string
  default = "Asia/Seoul"
}

variable "trip_demo_owner_id" {
  description = "Fixed single owner used by the private Phase 12-18 demonstration."
  type        = string
  default     = "demo-owner"

  validation {
    condition     = trimspace(var.trip_demo_owner_id) != "" && length(var.trip_demo_owner_id) <= 200
    error_message = "trip_demo_owner_id must be nonblank and at most 200 characters."
  }
}

variable "notification_push_enabled" {
  description = "Enable server-side Firebase Cloud Messaging delivery on the WAS tier."
  type        = bool
  default     = false
}

variable "notification_push_project_id" {
  description = "Firebase project ID used by the Admin SDK."
  type        = string
  default     = ""

  validation {
    condition     = !var.notification_push_enabled || can(regex("^[a-z][a-z0-9-]{4,28}[a-z0-9]$", var.notification_push_project_id))
    error_message = "Enabled push delivery requires a valid Firebase project ID."
  }
}

variable "notification_push_service_account_secret_arn" {
  description = "Seoul Secrets Manager ARN containing Firebase service-account JSON."
  type        = string
  default     = ""

  validation {
    condition     = !var.notification_push_enabled || can(regex("^arn:aws:secretsmanager:ap-northeast-2:[0-9]{12}:secret:[A-Za-z0-9/_+=.@-]+$", var.notification_push_service_account_secret_arn))
    error_message = "Enabled push delivery requires an ap-northeast-2 Secrets Manager ARN."
  }
}

variable "tunnel_client_enabled" {
  description = "Install and run OpenAI tunnel-client on the private WEB tier."
  type        = bool
  default     = false
}

variable "tunnel_id" {
  description = "OpenAI-hosted Secure MCP Tunnel identifier; not a credential."
  type        = string
  default     = ""

  validation {
    condition     = !var.tunnel_client_enabled || can(regex("^tunnel_[0-9A-Za-z_-]+$", var.tunnel_id))
    error_message = "Enabled tunnel-client requires a nonblank tunnel_id beginning with tunnel_."
  }
}

variable "tunnel_runtime_api_key_secret_arn" {
  description = "Seoul Secrets Manager ARN containing only the tunnel-client runtime API key."
  type        = string
  default     = ""

  validation {
    condition     = !var.tunnel_client_enabled || can(regex("^arn:aws:secretsmanager:ap-northeast-2:[0-9]{12}:secret:[A-Za-z0-9/_+=.@-]+$", var.tunnel_runtime_api_key_secret_arn))
    error_message = "Enabled tunnel-client requires an ap-northeast-2 Secrets Manager ARN."
  }
}

variable "tunnel_client_download_url" {
  description = "Official openai/tunnel-client GitHub release asset URL. Pin integrity with tunnel_client_sha256."
  type        = string
  default     = ""

  validation {
    condition     = !var.tunnel_client_enabled || can(regex("^https://github[.]com/openai/tunnel-client/releases/(latest/download|download/[^/]+)/[^/?#]+$", var.tunnel_client_download_url))
    error_message = "Enabled tunnel-client requires an official openai/tunnel-client releases asset URL."
  }
}

variable "tunnel_client_sha256" {
  description = "Expected SHA-256 for the downloaded tunnel-client release asset."
  type        = string
  default     = ""

  validation {
    condition     = !var.tunnel_client_enabled || (length(var.tunnel_client_sha256) == 64 && can(regex("^[0-9A-Fa-f]+$", var.tunnel_client_sha256)))
    error_message = "Enabled tunnel-client requires a 64-character hexadecimal SHA-256."
  }
}

variable "tunnel_loopback_port" {
  description = "Loopback-only Apache listener used by tunnel-client to reach the MCP endpoint."
  type        = number
  default     = 8090

  validation {
    condition     = var.tunnel_loopback_port >= 1024 && var.tunnel_loopback_port <= 65535
    error_message = "tunnel_loopback_port must be between 1024 and 65535."
  }
}
