variable "environment" {
  default = "Production"
}

variable "remote_state_bucket" {
  type        = "string"
  description = "Core infrastructure config bucket"
}

variable "aws_account_id" {
  default     = "896538046175"
  description = "Geotrellis Transit account ID"
}

variable "aws_region" {
  default = "us-east-1"
}

variable "image_version" {
  type        = "string"
  description = "Geotrellis Transit Image version"
}

variable "cdn_price_class" {
  default = "PriceClass_200"
}

variable "ssl_certificate_arn" {}

variable "transit_ecs_desired_count" {
  default = "1"
}

variable "transit_ecs_min_count" {
  default = "1"
}

variable "transit_ecs_max_count" {
  default = "2"
}

variable "transit_ecs_deployment_min_percent" {
  default = "100"
}

variable "transit_ecs_deployment_max_percent" {
  default = "200"
}
