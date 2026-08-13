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
  description = "Instance profile for SSM-managed WEB/WAS instances."
}

output "nat_profile" {
  value       = var.environment
  description = "Selected NAT profile: local, development/single-zonal, or HA/Regional NAT."
}
