#
# ECS Resources
#

# Template for container definition, allows us to inject environment
data "template_file" "ecs_transit_task" {
  template = "${file("${path.module}/task-definitions/transit.json")}"

  vars {
    transit_image = "${var.aws_account_id}.dkr.ecr.${var.aws_region}.amazonaws.com/gt-transit:${var.image_version}"
  }
}

# Allows resource sharing among multiple containers
resource "aws_ecs_task_definition" "transit" {
  family                = "${var.environment}Transit"
  container_definitions = "${data.template_file.ecs_transit_task.rendered}"
}

module "transit_ecs_service" {
  source = "github.com/azavea/terraform-aws-ecs-web-service?ref=0.2.0"

  name                = "Transit"
  vpc_id              = "${data.terraform_remote_state.core.vpc_id}"
  public_subnet_ids   = ["${data.terraform_remote_state.core.public_subnet_ids}"]
  access_log_bucket   = "${data.terraform_remote_state.core.logs_bucket_id}"
  access_log_prefix   = "ALB/Transit"
  port                = "9999"
  ssl_certificate_arn = "${var.ssl_certificate_arn}"

  cluster_name                   = "${data.terraform_remote_state.core.container_instance_name}"
  task_definition_id             = "${aws_ecs_task_definition.transit.family}:${aws_ecs_task_definition.transit.revision}"
  desired_count                  = "${var.transit_ecs_desired_count}"
  min_count                      = "${var.transit_ecs_min_count}"
  max_count                      = "${var.transit_ecs_max_count}"
  deployment_min_healthy_percent = "${var.transit_ecs_deployment_min_percent}"
  deployment_max_percent         = "${var.transit_ecs_deployment_max_percent}"
  container_name                 = "gt-transit"
  container_port                 = "9999"
  ecs_service_role_name          = "${data.terraform_remote_state.core.ecs_service_role_name}"
  ecs_autoscale_role_arn         = "${data.terraform_remote_state.core.ecs_autoscale_role_arn}"

  project     = "Geotrellis Transit"
  environment = "${var.environment}"
}
