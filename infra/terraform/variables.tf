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
  description = "Deployment profile: local, development (single-zonal NAT), or ha (regional NAT)."
  type        = string
  default     = "development"

  validation {
    condition     = contains(["local", "development", "ha"], var.environment)
    error_message = "environment must be local, development, or ha."
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
