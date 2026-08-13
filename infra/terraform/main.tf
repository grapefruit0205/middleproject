locals {
  az_a = var.availability_zones[0]
  az_c = var.availability_zones[1]

  name_slug         = substr(replace(replace(lower("${var.name}-${var.environment}"), "_", "-"), " ", "-"), 0, 20)
  public_alb_name   = substr("${local.name_slug}-public", 0, 32)
  internal_alb_name = substr("${local.name_slug}-internal", 0, 32)
  web_tg_name       = substr("${local.name_slug}-web-tg", 0, 32)
  was_tg_name       = substr("${local.name_slug}-was-tg", 0, 32)
  web_lt_prefix     = substr("${local.name_slug}-web-", 0, 37)
  was_lt_prefix     = substr("${local.name_slug}-was-", 0, 37)

  cost_tags = {
    Project     = var.name
    Environment = var.environment
    CostCenter  = var.cost_center
    Owner       = var.owner
    ManagedBy   = "terraform"
  }

  nat_enabled = var.environment != "local"
  nat_ha      = var.environment == "ha"
}

resource "aws_vpc" "this" {
  cidr_block           = var.vpc_cidr
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = { Name = "${var.name}-${var.environment}-vpc" }
}

resource "aws_internet_gateway" "this" {
  vpc_id = aws_vpc.this.id
  tags   = { Name = "${var.name}-${var.environment}-igw" }
}

locals {
  subnets = {
    public_a = { cidr = cidrsubnet(var.vpc_cidr, 8, 0), az = local.az_a, tier = "public" }
    public_c = { cidr = cidrsubnet(var.vpc_cidr, 8, 1), az = local.az_c, tier = "public" }
    web_a    = { cidr = cidrsubnet(var.vpc_cidr, 8, 10), az = local.az_a, tier = "web" }
    web_c    = { cidr = cidrsubnet(var.vpc_cidr, 8, 11), az = local.az_c, tier = "web" }
    was_a    = { cidr = cidrsubnet(var.vpc_cidr, 8, 20), az = local.az_a, tier = "was" }
    was_c    = { cidr = cidrsubnet(var.vpc_cidr, 8, 21), az = local.az_c, tier = "was" }
    db_a     = { cidr = cidrsubnet(var.vpc_cidr, 8, 30), az = local.az_a, tier = "db" }
    db_c     = { cidr = cidrsubnet(var.vpc_cidr, 8, 31), az = local.az_c, tier = "db" }
  }
}

resource "aws_subnet" "this" {
  for_each = local.subnets

  vpc_id                  = aws_vpc.this.id
  cidr_block              = each.value.cidr
  availability_zone       = each.value.az
  map_public_ip_on_launch = each.value.tier == "public"
  tags                    = { Name = "${var.name}-${var.environment}-${each.key}", Tier = each.value.tier }
}

resource "aws_eip" "nat" {
  count  = var.environment == "development" ? 1 : 0
  domain = "vpc"
  tags   = { Name = "${var.name}-${var.environment}-nat-a" }
}

resource "aws_nat_gateway" "zonal" {
  count = var.environment == "development" ? 1 : 0

  allocation_id = aws_eip.nat[0].id
  subnet_id     = aws_subnet.this["public_a"].id
  depends_on    = [aws_internet_gateway.this]
  tags          = { Name = "${var.name}-${var.environment}-nat-a" }
}

resource "aws_nat_gateway" "regional" {
  count = var.environment == "ha" ? 1 : 0

  availability_mode = "regional"
  vpc_id            = aws_vpc.this.id
  depends_on        = [aws_internet_gateway.this]
  tags              = { Name = "${var.name}-${var.environment}-nat-regional" }
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.this.id
  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.this.id
  }
  tags = { Name = "${var.name}-${var.environment}-public-rt" }
}

resource "aws_route_table_association" "public" {
  for_each       = { public_a = "public_a", public_c = "public_c" }
  route_table_id = aws_route_table.public.id
  subnet_id      = aws_subnet.this[each.value].id
}

resource "aws_route_table" "private" {
  for_each = { a = local.az_a, c = local.az_c }
  vpc_id   = aws_vpc.this.id
  dynamic "route" {
    for_each = local.nat_enabled ? [1] : []
    content {
      cidr_block     = "0.0.0.0/0"
      nat_gateway_id = local.nat_ha ? aws_nat_gateway.regional[0].id : aws_nat_gateway.zonal[0].id
    }
  }
  tags = { Name = "${var.name}-${var.environment}-private-${each.key}-rt" }
}

resource "aws_route_table" "db" {
  for_each = { a = local.az_a, c = local.az_c }
  vpc_id   = aws_vpc.this.id
  tags     = { Name = "${var.name}-${var.environment}-db-${each.key}-rt", RoutePolicy = "local-only" }
}

resource "aws_route_table_association" "private" {
  for_each       = { web_a = "web_a", web_c = "web_c", was_a = "was_a", was_c = "was_c" }
  route_table_id = aws_route_table.private[substr(each.key, -1, 1)].id
  subnet_id      = aws_subnet.this[each.value].id
}

resource "aws_route_table_association" "db" {
  for_each       = { db_a = "db_a", db_c = "db_c" }
  route_table_id = aws_route_table.db[substr(each.key, -1, 1)].id
  subnet_id      = aws_subnet.this[each.value].id
}

resource "aws_security_group" "public_alb" {
  name   = "${var.name}-${var.environment}-public-alb"
  vpc_id = aws_vpc.this.id
  ingress {
    description = "HTTPS from internet"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }
  egress {
    description = "ALB responses"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_security_group" "web" {
  name   = "${var.name}-${var.environment}-web"
  vpc_id = aws_vpc.this.id
  ingress {
    description     = "HTTP from public ALB"
    from_port       = 80
    to_port         = 80
    protocol        = "tcp"
    security_groups = [aws_security_group.public_alb.id]
  }
  egress {
    description = "Outbound via NAT or local profile"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_security_group" "internal_alb" {
  name   = "${var.name}-${var.environment}-internal-alb"
  vpc_id = aws_vpc.this.id
  ingress {
    description     = "HTTP from WEB"
    from_port       = 80
    to_port         = 80
    protocol        = "tcp"
    security_groups = [aws_security_group.web.id]
  }
  egress {
    description = "Internal ALB responses"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_security_group" "was" {
  name   = "${var.name}-${var.environment}-was"
  vpc_id = aws_vpc.this.id
  ingress {
    description     = "Tomcat from internal ALB"
    from_port       = 8080
    to_port         = 8080
    protocol        = "tcp"
    security_groups = [aws_security_group.internal_alb.id]
  }
  egress {
    description = "Outbound via NAT or local profile"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_security_group" "rds" {
  name   = "${var.name}-${var.environment}-rds"
  vpc_id = aws_vpc.this.id
  ingress {
    description     = "PostgreSQL from WAS"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.was.id]
  }
  egress {
    description = "RDS responses"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_iam_role" "ssm" {
  name               = "${var.name}-${var.environment}-ssm"
  assume_role_policy = jsonencode({ Version = "2012-10-17", Statement = [{ Effect = "Allow", Principal = { Service = "ec2.amazonaws.com" }, Action = "sts:AssumeRole" }] })
  tags               = { Name = "${var.name}-${var.environment}-ssm-role" }
}

resource "aws_iam_role_policy_attachment" "ssm" {
  role       = aws_iam_role.ssm.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_instance_profile" "ssm" {
  name = "${var.name}-${var.environment}-ssm"
  role = aws_iam_role.ssm.name
  tags = { Name = "${var.name}-${var.environment}-ssm-profile" }
}
