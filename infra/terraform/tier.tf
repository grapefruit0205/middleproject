data "aws_ssm_parameter" "al2023" {
  name = "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64"
}

resource "aws_s3_bucket" "artifacts" {
  bucket        = var.artifact_bucket_name
  force_destroy = var.artifact_force_destroy
  tags          = { Name = "${var.name}-${var.environment}-artifacts" }
}

resource "aws_s3_object" "frontend_artifact" {
  bucket       = aws_s3_bucket.artifacts.id
  key          = var.frontend_artifact_key
  source       = var.frontend_artifact_path
  source_hash  = filemd5(var.frontend_artifact_path)
  content_type = "application/zip"

  depends_on = [
    aws_s3_bucket_ownership_controls.artifacts,
    aws_s3_bucket_public_access_block.artifacts,
    aws_s3_bucket_server_side_encryption_configuration.artifacts,
    aws_s3_bucket_versioning.artifacts,
  ]
}

resource "aws_s3_object" "backend_artifact" {
  bucket       = aws_s3_bucket.artifacts.id
  key          = var.backend_artifact_key
  source       = var.backend_artifact_path
  source_hash  = filemd5(var.backend_artifact_path)
  content_type = "application/java-archive"

  depends_on = [
    aws_s3_bucket_ownership_controls.artifacts,
    aws_s3_bucket_public_access_block.artifacts,
    aws_s3_bucket_server_side_encryption_configuration.artifacts,
    aws_s3_bucket_versioning.artifacts,
  ]
}

resource "aws_s3_bucket_ownership_controls" "artifacts" {
  bucket = aws_s3_bucket.artifacts.id
  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

resource "aws_s3_bucket_public_access_block" "artifacts" {
  bucket                  = aws_s3_bucket.artifacts.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_server_side_encryption_configuration" "artifacts" {
  bucket = aws_s3_bucket.artifacts.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_versioning" "artifacts" {
  bucket = aws_s3_bucket.artifacts.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_iam_role" "web" {
  name               = "${var.name}-${var.environment}-web"
  assume_role_policy = jsonencode({ Version = "2012-10-17", Statement = [{ Effect = "Allow", Principal = { Service = "ec2.amazonaws.com" }, Action = "sts:AssumeRole" }] })
}

resource "aws_sqs_queue" "reminder_dlq" {
  name                      = "${var.name}-${var.environment}-reminder-dlq"
  message_retention_seconds = 1209600
  sqs_managed_sse_enabled   = true
}

resource "aws_sqs_queue" "reminder" {
  name                       = "${var.name}-${var.environment}-reminder"
  visibility_timeout_seconds = 60
  sqs_managed_sse_enabled    = true
  redrive_policy             = jsonencode({ deadLetterTargetArn = aws_sqs_queue.reminder_dlq.arn, maxReceiveCount = 5 })
}

resource "aws_scheduler_schedule_group" "this" {
  name = var.scheduler_group
}

resource "aws_iam_role" "scheduler" {
  name               = "${var.name}-${var.environment}-scheduler"
  description        = "EventBridge Scheduler execution role for ${var.scheduler_group}"
  assume_role_policy = jsonencode({ Version = "2012-10-17", Statement = [{ Effect = "Allow", Principal = { Service = "scheduler.amazonaws.com" }, Action = "sts:AssumeRole" }] })
}

resource "aws_iam_role_policy" "scheduler" {
  name   = "${var.name}-${var.environment}-scheduler-sqs"
  role   = aws_iam_role.scheduler.id
  policy = jsonencode({ Version = "2012-10-17", Statement = [{ Effect = "Allow", Action = ["sqs:SendMessage"], Resource = aws_sqs_queue.reminder.arn }] })
}

resource "aws_iam_role" "was" {
  name               = "${var.name}-${var.environment}-was"
  assume_role_policy = jsonencode({ Version = "2012-10-17", Statement = [{ Effect = "Allow", Principal = { Service = "ec2.amazonaws.com" }, Action = "sts:AssumeRole" }] })
}

resource "aws_iam_role_policy" "web" {
  name = "${var.name}-${var.environment}-web-access"
  role = aws_iam_role.web.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = concat(
      [{ Effect = "Allow", Action = ["s3:GetObject"], Resource = "${aws_s3_bucket.artifacts.arn}/${var.frontend_artifact_key}" }],
      var.tunnel_client_enabled ? [{
        Effect   = "Allow"
        Action   = ["secretsmanager:GetSecretValue"]
        Resource = var.tunnel_runtime_api_key_secret_arn
      }] : []
    )
  })
}

resource "aws_iam_role_policy" "was" {
  name = "${var.name}-${var.environment}-was-access"
  role = aws_iam_role.was.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = concat([
      { Effect = "Allow", Action = ["s3:GetObject"], Resource = "${aws_s3_bucket.artifacts.arn}/${var.backend_artifact_key}" },
      { Effect = "Allow", Action = ["secretsmanager:GetSecretValue"], Resource = aws_db_instance.this.master_user_secret[0].secret_arn },
      { Effect = "Allow", Action = ["sqs:ReceiveMessage", "sqs:DeleteMessage", "sqs:ChangeMessageVisibility", "sqs:GetQueueAttributes"], Resource = aws_sqs_queue.reminder.arn },
      { Effect = "Allow", Action = ["scheduler:CreateSchedule", "scheduler:UpdateSchedule", "scheduler:DeleteSchedule"], Resource = "arn:aws:scheduler:${var.aws_region}:*:schedule/${var.scheduler_group}/reminder-*" },
      { Effect = "Allow", Action = ["iam:PassRole"], Resource = aws_iam_role.scheduler.arn, Condition = { StringEquals = { "iam:PassedToService" = "scheduler.amazonaws.com" } } }
      ],
      var.notification_email_enabled ? [{ Effect = "Allow", Action = ["ses:SendEmail"], Resource = var.notification_email_identity_arn }] : [],
      var.notification_push_enabled ? [{
        Effect   = "Allow"
        Action   = ["secretsmanager:GetSecretValue"]
        Resource = var.notification_push_service_account_secret_arn
      }] : [],
      var.public_transport_enabled ? [{
        Effect   = "Allow"
        Action   = ["secretsmanager:GetSecretValue"]
        Resource = var.public_transport_secrets_arn
      }] : []
    )
  })
}

resource "aws_iam_role_policy_attachment" "web_ssm" {
  role       = aws_iam_role.web.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_role_policy_attachment" "was_ssm" {
  role       = aws_iam_role.was.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_instance_profile" "web" {
  name = "${var.name}-${var.environment}-web"
  role = aws_iam_role.web.name
}

resource "aws_iam_instance_profile" "was" {
  name = "${var.name}-${var.environment}-was"
  role = aws_iam_role.was.name
}

resource "aws_lb" "public" {
  name                       = local.public_alb_name
  internal                   = false
  load_balancer_type         = "application"
  drop_invalid_header_fields = true
  subnets                    = [aws_subnet.this["public_a"].id, aws_subnet.this["public_c"].id]
  security_groups            = [aws_security_group.public_alb.id]

  access_logs {
    bucket  = aws_s3_bucket.alb_access_logs.id
    prefix  = "alb"
    enabled = true
  }
}

resource "aws_lb_target_group" "web" {
  name     = local.web_tg_name
  port     = 80
  protocol = "HTTP"
  vpc_id   = aws_vpc.this.id
  health_check {
    path    = "/healthz"
    matcher = "200"
  }
}

resource "aws_lb_listener" "public" {
  load_balancer_arn = aws_lb.public.arn
  port              = 443
  protocol          = "HTTPS"
  ssl_policy        = "ELBSecurityPolicy-TLS13-1-2-2021-06"
  certificate_arn   = var.certificate_arn
  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.web.arn
  }
}

resource "aws_lb" "internal" {
  name                       = local.internal_alb_name
  internal                   = true
  load_balancer_type         = "application"
  drop_invalid_header_fields = true
  subnets                    = [aws_subnet.this["was_a"].id, aws_subnet.this["was_c"].id]
  security_groups            = [aws_security_group.internal_alb.id]

  access_logs {
    bucket  = aws_s3_bucket.alb_access_logs.id
    prefix  = "alb"
    enabled = true
  }
}

resource "aws_lb_target_group" "was" {
  name     = local.was_tg_name
  port     = 8080
  protocol = "HTTP"
  vpc_id   = aws_vpc.this.id
  health_check {
    path    = "/actuator/health/readiness"
    matcher = "200"
  }
}

resource "aws_lb_listener" "internal" {
  load_balancer_arn = aws_lb.internal.arn
  port              = 80
  protocol          = "HTTP"
  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.was.arn
  }
}

resource "aws_launch_template" "web" {
  depends_on = [
    aws_s3_object.frontend_artifact,
    aws_route_table_association.private,
    aws_nat_gateway.zonal,
    aws_nat_gateway.regional,
    aws_iam_role_policy.web,
    aws_iam_role_policy.web_observability,
    aws_iam_role_policy_attachment.web_ssm,
  ]
  name_prefix   = local.web_lt_prefix
  image_id      = data.aws_ssm_parameter.al2023.value
  instance_type = var.instance_type
  iam_instance_profile {
    name = aws_iam_instance_profile.web.name
  }
  network_interfaces {
    associate_public_ip_address = false
    security_groups             = [aws_security_group.web.id]
  }
  block_device_mappings {
    device_name = "/dev/xvda"
    ebs {
      encrypted   = true
      volume_type = "gp3"
    }
  }
  user_data = base64encode(templatefile("${path.module}/templates/web.sh.tftpl", {
    bucket                            = aws_s3_bucket.artifacts.id
    artifact_key                      = var.frontend_artifact_key
    internal_alb_dns                  = aws_lb.internal.dns_name
    apache_access_log_group           = aws_cloudwatch_log_group.apache_access.name
    apache_error_log_group            = aws_cloudwatch_log_group.apache_error.name
    environment                       = var.environment
    aws_region                        = var.aws_region
    tunnel_client_enabled             = var.tunnel_client_enabled
    tunnel_id                         = var.tunnel_id
    tunnel_runtime_api_key_secret_arn = var.tunnel_runtime_api_key_secret_arn
    tunnel_client_download_url        = var.tunnel_client_download_url
    tunnel_client_sha256              = lower(var.tunnel_client_sha256)
    tunnel_client_installer           = file("${path.module}/templates/install-tunnel-client.sh")
    tunnel_loopback_port              = var.tunnel_loopback_port
  }))
  metadata_options { http_tokens = "required" }
  tag_specifications {
    resource_type = "instance"
    tags          = { Name = "${var.name}-${var.environment}-web" }
  }
}

resource "aws_autoscaling_group" "web" {
  depends_on = [
    aws_s3_object.frontend_artifact,
    aws_route_table_association.private,
    aws_nat_gateway.zonal,
    aws_nat_gateway.regional,
    aws_iam_role_policy.web,
    aws_iam_role_policy.web_observability,
    aws_iam_role_policy_attachment.web_ssm,
  ]
  name                      = "${var.name}-${var.environment}-web"
  min_size                  = 2
  max_size                  = 2
  desired_capacity          = 2
  vpc_zone_identifier       = [aws_subnet.this["web_a"].id, aws_subnet.this["web_c"].id]
  target_group_arns         = [aws_lb_target_group.web.arn]
  health_check_type         = "ELB"
  health_check_grace_period = 300
  launch_template {
    id      = aws_launch_template.web.id
    version = aws_launch_template.web.latest_version
  }
  tag {
    key                 = "Name"
    value               = "${var.name}-${var.environment}-web"
    propagate_at_launch = true
  }
  instance_refresh {
    strategy = "Rolling"
    preferences {
      instance_warmup        = 300
      min_healthy_percentage = 50
    }
  }
}

resource "aws_launch_template" "was" {
  depends_on = [
    aws_s3_object.backend_artifact,
    aws_route_table_association.private,
    aws_nat_gateway.zonal,
    aws_nat_gateway.regional,
    aws_iam_role_policy.was,
    aws_iam_role_policy.was_observability,
    aws_iam_role_policy_attachment.was_ssm,
  ]
  name_prefix   = local.was_lt_prefix
  image_id      = data.aws_ssm_parameter.al2023.value
  instance_type = var.instance_type
  iam_instance_profile {
    name = aws_iam_instance_profile.was.name
  }
  network_interfaces {
    associate_public_ip_address = false
    security_groups             = [aws_security_group.was.id]
  }
  block_device_mappings {
    device_name = "/dev/xvda"
    ebs {
      encrypted   = true
      volume_type = "gp3"
    }
  }
  user_data = base64encode(templatefile("${path.module}/templates/was.sh.tftpl", {
    bucket                                       = aws_s3_bucket.artifacts.id
    artifact_key                                 = var.backend_artifact_key
    backend_artifact_hash                        = filemd5(var.backend_artifact_path)
    tomcat_version                               = var.tomcat_version
    db_secret_arn                                = aws_db_instance.this.master_user_secret[0].secret_arn
    db_host                                      = aws_db_instance.this.address
    db_name                                      = var.db_name
    db_username                                  = var.db_username
    scheduler_aws_enabled                        = var.scheduler_aws_enabled
    scheduler_group                              = var.scheduler_group
    scheduler_role_arn                           = aws_iam_role.scheduler.arn
    scheduler_queue_arn                          = aws_sqs_queue.reminder.arn
    scheduler_timezone                           = var.scheduler_timezone
    delivery_sqs_enabled                         = var.delivery_sqs_enabled
    delivery_queue_url                           = aws_sqs_queue.reminder.url
    notification_email_enabled                   = var.notification_email_enabled
    notification_email_from                      = var.notification_email_from
    notification_email_to                        = var.notification_email_to
    trip_demo_owner_id                           = var.trip_demo_owner_id
    notification_push_enabled                    = var.notification_push_enabled
    notification_push_project_id                 = var.notification_push_project_id
    notification_push_service_account_secret_arn = var.notification_push_service_account_secret_arn
    public_transport_enabled                     = var.public_transport_enabled
    public_transport_secrets_arn                 = var.public_transport_secrets_arn
    public_transport_seoul_realtime_enabled      = var.public_transport_seoul_realtime_enabled
    tomcat_access_log_group                      = aws_cloudwatch_log_group.tomcat_access.name
    application_log_group                        = aws_cloudwatch_log_group.application.name
    environment                                  = var.environment
  }))
  metadata_options { http_tokens = "required" }
  tag_specifications {
    resource_type = "instance"
    tags          = { Name = "${var.name}-${var.environment}-was" }
  }
}

resource "aws_autoscaling_group" "was" {
  depends_on = [
    aws_s3_object.backend_artifact,
    aws_route_table_association.private,
    aws_nat_gateway.zonal,
    aws_nat_gateway.regional,
    aws_iam_role_policy.was,
    aws_iam_role_policy.was_observability,
    aws_iam_role_policy_attachment.was_ssm,
  ]
  name                      = "${var.name}-${var.environment}-was"
  min_size                  = 2
  max_size                  = 2
  desired_capacity          = 2
  vpc_zone_identifier       = [aws_subnet.this["was_a"].id, aws_subnet.this["was_c"].id]
  target_group_arns         = [aws_lb_target_group.was.arn]
  health_check_type         = "ELB"
  health_check_grace_period = 300
  launch_template {
    id      = aws_launch_template.was.id
    version = aws_launch_template.was.latest_version
  }
  tag {
    key                 = "Name"
    value               = "${var.name}-${var.environment}-was"
    propagate_at_launch = true
  }
  instance_refresh {
    strategy = "Rolling"
    preferences {
      instance_warmup        = 300
      min_healthy_percentage = 50
    }
  }
}

resource "aws_db_subnet_group" "this" {
  name       = "${var.name}-${var.environment}-db"
  subnet_ids = [aws_subnet.this["db_a"].id, aws_subnet.this["db_c"].id]
}

resource "aws_db_instance" "this" {
  identifier                  = "${var.name}-${var.environment}"
  engine                      = "postgres"
  engine_version              = var.postgres_engine_version
  instance_class              = var.db_instance_class
  allocated_storage           = var.db_allocated_storage
  storage_type                = "gp3"
  storage_encrypted           = true
  multi_az                    = true
  db_name                     = var.db_name
  username                    = var.db_username
  manage_master_user_password = true
  auto_minor_version_upgrade  = true
  copy_tags_to_snapshot       = true
  db_subnet_group_name        = aws_db_subnet_group.this.name
  vpc_security_group_ids      = [aws_security_group.rds.id]
  skip_final_snapshot         = var.skip_final_snapshot
  backup_retention_period     = 7
  backup_window               = "18:00-18:30"
  deletion_protection         = var.deletion_protection
}
