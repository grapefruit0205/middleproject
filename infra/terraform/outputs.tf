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
