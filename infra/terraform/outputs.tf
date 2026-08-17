output "vpc_id" {
  value       = aws_vpc.this.id
  description = "Baseline VPC ID."
}

output "subnet_ids" {
  value       = { for name, subnet in aws_subnet.this : name => subnet.id }
  description = "Public, WEB, WAS, and DB subnet IDs by AZ."
}

output "security_group_ids" {
  value = {
    public_alb   = aws_security_group.public_alb.id
    web          = aws_security_group.web.id
    internal_alb = aws_security_group.internal_alb.id
    was          = aws_security_group.was.id
    rds          = aws_security_group.rds.id
  }
}

output "ssm_instance_profile_name" {
  value       = aws_iam_instance_profile.ssm.name
  description = "Legacy shared SSM profile retained for compatibility; tier launch templates use scoped profiles."
}

output "web_instance_profile_name" {
  value       = aws_iam_instance_profile.web.name
  description = "WEB instance profile with SSM and frontend artifact read access only."
}

output "was_instance_profile_name" {
  value       = aws_iam_instance_profile.was.name
  description = "WAS instance profile with SSM, backend artifact, and database secret access."
}

output "nat_profile" {
  value       = var.environment
  description = "Selected AWS application NAT profile: development/single-zonal or HA/Regional NAT."
}

output "public_alb_dns_name" {
  value       = aws_lb.public.dns_name
  description = "Public HTTPS entry point used for Phase 05 acceptance checks."
}

output "internal_alb_dns_name" {
  value       = aws_lb.internal.dns_name
  description = "Private ALB endpoint used by the Apache reverse proxy."
}

output "rds_endpoint" {
  value       = aws_db_instance.this.endpoint
  description = "Private PostgreSQL endpoint used by the WAS tier."
}

output "artifact_bucket_name" {
  value       = aws_s3_bucket.artifacts.id
  description = "Private bucket containing the deployed frontend and WAR artifacts."
}

output "reminder_queue_url" {
  value       = aws_sqs_queue.reminder.url
  description = "Reminder delivery SQS queue URL."
}

output "reminder_queue_arn" {
  value       = aws_sqs_queue.reminder.arn
  description = "Reminder delivery SQS queue ARN."
}

output "scheduler_role_arn" {
  value       = aws_iam_role.scheduler.arn
  description = "EventBridge Scheduler execution role ARN."
}

output "scheduler_group" {
  value       = var.scheduler_group
  description = "EventBridge Scheduler group name."
}

output "observability_log_groups" {
  value = {
    apache_access = aws_cloudwatch_log_group.apache_access.name
    apache_error  = aws_cloudwatch_log_group.apache_error.name
    tomcat_access = aws_cloudwatch_log_group.tomcat_access.name
    application   = aws_cloudwatch_log_group.application.name
    ssm_session   = aws_cloudwatch_log_group.ssm_session.name
  }
  description = "CloudWatch log groups for Phase 10 operational evidence."
}

output "alb_access_log_bucket_name" {
  value       = aws_s3_bucket.alb_access_logs.id
  description = "Private encrypted bucket receiving both ALB access logs."
}

output "session_logging_document_name" {
  value       = aws_ssm_document.session_logging.name
  description = "Project-scoped SSM Session Manager document with CloudWatch recording."
}

output "secure_mcp_tunnel" {
  value = {
    enabled              = var.tunnel_client_enabled
    tunnel_id            = var.tunnel_client_enabled ? var.tunnel_id : null
    private_mcp_endpoint = var.tunnel_client_enabled ? "http://127.0.0.1:${var.tunnel_loopback_port}/api/mcp" : null
  }
  description = "Non-sensitive Secure MCP Tunnel handoff data. The endpoint is loopback-only on WEB instances."
}

output "push_delivery" {
  value = {
    enabled             = var.notification_push_enabled
    firebase_project_id = var.notification_push_enabled ? var.notification_push_project_id : null
  }
  description = "Non-sensitive Firebase push-delivery handoff data."
}

output "public_transport" {
  value = {
    enabled                     = var.public_transport_enabled
    seoul_realtime_http_enabled = var.public_transport_seoul_realtime_enabled
  }
  description = "Non-sensitive Phase 18 public-transport feature flags."
}
