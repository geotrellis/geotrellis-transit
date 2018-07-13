provider "aws" {
  region  = "us-east-1"
  version = "~> 1.27.0"
}

provider "template" {
  version = "~> 1.0.0"
}

provider "terraform" {
  version = "~> 1.0.0"
}

terraform {
  backend "s3" {
    region  = "us-east-1"
    encrypt = "true"
  }
}
