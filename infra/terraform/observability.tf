data "aws_caller_identity" "current" {}

resource "aws_cloudwatch_log_group" "apache_access" {
  name              = "/middleproject/${var.environment}/web/apache-access"
  retention_in_days = 14
}

resource "aws_cloudwatch_log_group" "apache_error" {
  name              = "/middleproject/${var.environment}/web/apache-error"
  retention_in_days = 14
}

resource "aws_cloudwatch_log_group" "tomcat_access" {
  name              = "/middleproject/${var.environment}/was/tomcat-access"
  retention_in_days = 14
}

resource "aws_cloudwatch_log_group" "application" {
  name              = "/middleproject/${var.environment}/was/application"
  retention_in_days = 14
}

resource "aws_cloudwatch_log_group" "ssm_session" {
  name              = "/middleproject/${var.environment}/ssm/session"
  retention_in_days = 30
}

resource "aws_s3_bucket" "alb_access_logs" {
  bucket        = "${local.name_slug}-${data.aws_caller_identity.current.account_id}-alb-logs"
  force_destroy = var.alb_access_log_force_destroy

  tags = { Name = "${var.name}-${var.environment}-alb-access-logs" }
}

resource "aws_s3_bucket_ownership_controls" "alb_access_logs" {
  bucket = aws_s3_bucket.alb_access_logs.id

  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

resource "aws_s3_bucket_public_access_block" "alb_access_logs" {
  bucket                  = aws_s3_bucket.alb_access_logs.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_server_side_encryption_configuration" "alb_access_logs" {
  bucket = aws_s3_bucket.alb_access_logs.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_versioning" "alb_access_logs" {
  bucket = aws_s3_bucket.alb_access_logs.id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "alb_access_logs" {
  bucket = aws_s3_bucket.alb_access_logs.id

  rule {
    id     = "expire-alb-access-logs"
    status = "Enabled"

    filter {}

    abort_incomplete_multipart_upload {
      days_after_initiation = 7
    }

    expiration {
      days = 30
    }

    noncurrent_version_expiration {
      noncurrent_days = 30
    }
  }

  depends_on = [aws_s3_bucket_versioning.alb_access_logs]
}

resource "aws_s3_bucket_policy" "alb_access_logs" {
  bucket = aws_s3_bucket.alb_access_logs.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid    = "AllowElasticLoadBalancingLogDelivery"
      Effect = "Allow"
      Principal = {
        Service = "logdelivery.elasticloadbalancing.amazonaws.com"
      }
      Action   = "s3:PutObject"
      Resource = "${aws_s3_bucket.alb_access_logs.arn}/alb/AWSLogs/${data.aws_caller_identity.current.account_id}/*"
      Condition = {
        StringEquals = {
          "aws:SourceAccount" = data.aws_caller_identity.current.account_id
        }
      }
    }]
  })

  depends_on = [
    aws_s3_bucket_ownership_controls.alb_access_logs,
    aws_s3_bucket_public_access_block.alb_access_logs,
  ]
}

resource "aws_cloudwatch_metric_alarm" "public_unhealthy_hosts" {
  alarm_name          = "${var.name}-${var.environment}-public-unhealthy-hosts"
  namespace           = "AWS/ApplicationELB"
  metric_name         = "UnHealthyHostCount"
  statistic           = "Maximum"
  period              = 60
  evaluation_periods  = 2
  threshold           = 1
  comparison_operator = "GreaterThanOrEqualToThreshold"
  treat_missing_data  = "notBreaching"
  dimensions = {
    LoadBalancer = aws_lb.public.arn_suffix
    TargetGroup  = aws_lb_target_group.web.arn_suffix
  }
}

resource "aws_cloudwatch_metric_alarm" "internal_unhealthy_hosts" {
  alarm_name          = "${var.name}-${var.environment}-internal-unhealthy-hosts"
  namespace           = "AWS/ApplicationELB"
  metric_name         = "UnHealthyHostCount"
  statistic           = "Maximum"
  period              = 60
  evaluation_periods  = 2
  threshold           = 1
  comparison_operator = "GreaterThanOrEqualToThreshold"
  treat_missing_data  = "notBreaching"
  dimensions = {
    LoadBalancer = aws_lb.internal.arn_suffix
    TargetGroup  = aws_lb_target_group.was.arn_suffix
  }
}

resource "aws_cloudwatch_metric_alarm" "public_target_5xx" {
  alarm_name          = "${var.name}-${var.environment}-public-target-5xx"
  namespace           = "AWS/ApplicationELB"
  metric_name         = "HTTPCode_Target_5XX_Count"
  statistic           = "Sum"
  period              = 300
  evaluation_periods  = 1
  threshold           = 5
  comparison_operator = "GreaterThanOrEqualToThreshold"
  treat_missing_data  = "notBreaching"
  dimensions = {
    LoadBalancer = aws_lb.public.arn_suffix
    TargetGroup  = aws_lb_target_group.web.arn_suffix
  }
}

resource "aws_cloudwatch_metric_alarm" "internal_target_5xx" {
  alarm_name          = "${var.name}-${var.environment}-internal-target-5xx"
  namespace           = "AWS/ApplicationELB"
  metric_name         = "HTTPCode_Target_5XX_Count"
  statistic           = "Sum"
  period              = 300
  evaluation_periods  = 1
  threshold           = 5
  comparison_operator = "GreaterThanOrEqualToThreshold"
  treat_missing_data  = "notBreaching"
  dimensions = {
    LoadBalancer = aws_lb.internal.arn_suffix
    TargetGroup  = aws_lb_target_group.was.arn_suffix
  }
}

resource "aws_cloudwatch_metric_alarm" "scheduler_target_error" {
  alarm_name          = "${var.name}-${var.environment}-scheduler-target-error"
  namespace           = "AWS/Scheduler"
  metric_name         = "TargetErrorCount"
  statistic           = "Sum"
  period              = 300
  evaluation_periods  = 1
  threshold           = 1
  comparison_operator = "GreaterThanOrEqualToThreshold"
  treat_missing_data  = "notBreaching"
  dimensions = {
    ScheduleGroup = aws_scheduler_schedule_group.this.name
  }
}

resource "aws_cloudwatch_metric_alarm" "scheduler_invocation_dropped" {
  alarm_name          = "${var.name}-${var.environment}-scheduler-invocation-dropped"
  namespace           = "AWS/Scheduler"
  metric_name         = "InvocationDroppedCount"
  statistic           = "Sum"
  period              = 300
  evaluation_periods  = 1
  threshold           = 1
  comparison_operator = "GreaterThanOrEqualToThreshold"
  treat_missing_data  = "notBreaching"
  dimensions = {
    ScheduleGroup = aws_scheduler_schedule_group.this.name
  }
}

resource "aws_cloudwatch_metric_alarm" "reminder_queue_age" {
  alarm_name          = "${var.name}-${var.environment}-reminder-queue-age"
  namespace           = "AWS/SQS"
  metric_name         = "ApproximateAgeOfOldestMessage"
  statistic           = "Maximum"
  period              = 60
  evaluation_periods  = 2
  threshold           = 300
  comparison_operator = "GreaterThanOrEqualToThreshold"
  treat_missing_data  = "notBreaching"
  dimensions = {
    QueueName = aws_sqs_queue.reminder.name
  }
}

resource "aws_cloudwatch_metric_alarm" "reminder_queue_visible" {
  alarm_name          = "${var.name}-${var.environment}-reminder-queue-visible"
  namespace           = "AWS/SQS"
  metric_name         = "ApproximateNumberOfMessagesVisible"
  statistic           = "Maximum"
  period              = 60
  evaluation_periods  = 2
  threshold           = 10
  comparison_operator = "GreaterThanOrEqualToThreshold"
  treat_missing_data  = "notBreaching"
  dimensions = {
    QueueName = aws_sqs_queue.reminder.name
  }
}

resource "aws_cloudwatch_metric_alarm" "reminder_dlq" {
  alarm_name          = "${var.name}-${var.environment}-reminder-dlq-nonempty"
  namespace           = "AWS/SQS"
  metric_name         = "ApproximateNumberOfMessagesVisible"
  statistic           = "Maximum"
  period              = 60
  evaluation_periods  = 1
  threshold           = 1
  comparison_operator = "GreaterThanOrEqualToThreshold"
  treat_missing_data  = "notBreaching"
  dimensions = {
    QueueName = aws_sqs_queue.reminder_dlq.name
  }
}

resource "aws_cloudwatch_metric_alarm" "delivery_terminal_failures" {
  alarm_name          = "${var.name}-${var.environment}-delivery-terminal-failures"
  namespace           = "MiddleProject/Reminder/${var.environment}"
  metric_name         = "reminder.delivery.terminal.failures"
  statistic           = "Sum"
  period              = 300
  evaluation_periods  = 1
  threshold           = 1
  comparison_operator = "GreaterThanOrEqualToThreshold"
  treat_missing_data  = "notBreaching"
}
