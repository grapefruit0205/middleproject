locals {
  instance_log_group_arns = [
    aws_cloudwatch_log_group.apache_access.arn,
    "${aws_cloudwatch_log_group.apache_access.arn}:*",
    aws_cloudwatch_log_group.apache_error.arn,
    "${aws_cloudwatch_log_group.apache_error.arn}:*",
    aws_cloudwatch_log_group.tomcat_access.arn,
    "${aws_cloudwatch_log_group.tomcat_access.arn}:*",
    aws_cloudwatch_log_group.application.arn,
    "${aws_cloudwatch_log_group.application.arn}:*",
  ]
}

resource "aws_iam_role_policy" "web_observability" {
  name = "${var.name}-${var.environment}-web-observability"
  role = aws_iam_role.web.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = ["logs:CreateLogStream", "logs:DescribeLogStreams", "logs:PutLogEvents"]
        Resource = [aws_cloudwatch_log_group.apache_access.arn, "${aws_cloudwatch_log_group.apache_access.arn}:*", aws_cloudwatch_log_group.apache_error.arn, "${aws_cloudwatch_log_group.apache_error.arn}:*"]
      },
      {
        Effect   = "Allow"
        Action   = ["cloudwatch:PutMetricData"]
        Resource = "*"
        Condition = {
          ForAnyValue:StringEquals = {
            "cloudwatch:namespace" = ["MiddleProject/Host/${var.environment}", "MiddleProject/Reminder/${var.environment}"]
          }
        }
      }
    ]
  })
}

resource "aws_iam_role_policy" "was_observability" {
  name = "${var.name}-${var.environment}-was-observability"
  role = aws_iam_role.was.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = ["logs:CreateLogStream", "logs:DescribeLogStreams", "logs:PutLogEvents"]
        Resource = [aws_cloudwatch_log_group.tomcat_access.arn, "${aws_cloudwatch_log_group.tomcat_access.arn}:*", aws_cloudwatch_log_group.application.arn, "${aws_cloudwatch_log_group.application.arn}:*"]
      },
      {
        Effect   = "Allow"
        Action   = ["cloudwatch:PutMetricData"]
        Resource = "*"
        Condition = {
          ForAnyValue:StringEquals = {
            "cloudwatch:namespace" = ["MiddleProject/Host/${var.environment}", "MiddleProject/Reminder/${var.environment}"]
          }
        }
      }
    ]
  })
}

resource "aws_ssm_document" "session_logging" {
  name            = "${var.name}-${var.environment}-session-logging"
  document_type   = "Session"
  document_format = "JSON"

  content = jsonencode({
    schemaVersion = "1.0"
    description   = "Project shell sessions recorded in CloudWatch Logs"
    sessionType   = "Standard_Stream"
    inputs = {
      cloudWatchLogGroupName      = aws_cloudwatch_log_group.ssm_session.name
      cloudWatchStreamingEnabled  = true
      cloudWatchEncryptionEnabled = false
      s3EncryptionEnabled         = false
      runAsEnabled                = false
    }
  })
}
