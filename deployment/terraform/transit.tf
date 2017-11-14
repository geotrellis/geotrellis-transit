#
# ECS Resources
#

# Template for container definition, allows us to inject environment
data "template_file" "ecs_transit_task" {
  template = "${file("${path.module}/task-definitions/transit.json")}"

  vars {
    transit_image       = "${var.aws_account_id}.dkr.ecr.${var.aws_region}.amazonaws.com/gt-transit:${var.image_version}"
    transit_environment = "${var.environment}"
    transit_region      = "${var.aws_region}"
  }
}

# Allows resource sharing among multiple containers
resource "aws_ecs_task_definition" "transit" {
  family                = "${var.environment}Transit"
  container_definitions = "${data.template_file.ecs_transit_task.rendered}"
}

resource "aws_cloudwatch_log_group" "transit" {
  name              = "log${var.environment}Transit"
  retention_in_days = "30"

  tags {
    Environment = "${var.environment}"
  }
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
  health_check_path              = "/api/travelshed/wms?service=WMS&request=GetMap&version=1.1.1&layers=&styles=&format=image/jpeg&transparent=false&height=256&width=256&latitude=39.96238554917605&longitude=-75.16399383544922&time=43019&duration=3600&modes=walking&schedule=weekday&direction=departing&breaks=600,900,1200,1800,2400,3000,3600,4500,5400,7200&palette=0xF68481,0xFDB383,0xFEE085,0xDCF288,0xB6F2AE,0x98FEE6,0x83D9FD,0x81A8FC,0x8083F7,0x7F81BD&srs=EPSG:3857&bbox=-8384836.254770693,4862617.991389772,-8375052.315150191,4872401.931010273"

  project     = "Geotrellis Transit"
  environment = "${var.environment}"
}
