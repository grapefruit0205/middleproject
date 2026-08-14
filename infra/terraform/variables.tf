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
  default = "default"
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
